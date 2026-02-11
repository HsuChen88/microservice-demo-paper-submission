# Paper Submission Service — 技術文件

## 目錄

- [服務概述](#服務概述)
- [為什麼實作此微服務](#為什麼實作此微服務)
- [架構設計](#架構設計)
- [目錄結構](#目錄結構)
- [各模組詳細說明](#各模組詳細說明)
- [API 規格](#api-規格)
- [資料模型](#資料模型)
- [Kafka 事件機制](#kafka-事件機制)
- [Resilience4j 容錯機制](#resilience4j-容錯機制)
- [組態設定](#組態設定)
- [存取方式](#存取方式)
- [效能分析](#效能分析)
- [建置與部署](#建置與部署)

---

## 服務概述

**Paper Submission Service** 是論文館藏微服務架構中的核心服務，負責論文投稿的建立、查詢與生命週期管理。採用 Spring Boot 3.4.1 搭配 Java 17 建構，透過 RESTful API 提供外部與內部存取介面，並以 Kafka 實現事件驅動的跨服務通訊。

| 屬性 | 值 |
|---|---|
| 框架 | Spring Boot 3.4.1 |
| 語言 | Java 17 |
| 資料庫 | PostgreSQL |
| 訊息佇列 | Apache Kafka |
| 容錯機制 | Resilience4j Circuit Breaker |
| 監控指標 | Micrometer + Prometheus |
| 服務埠號 | 8081 |

---

## 為什麼實作此微服務

在論文館藏系統的微服務拆分策略中，Paper Submission Service 承擔以下核心職責：

1. **單一職責原則**：將「論文投稿」這個領域邊界（Bounded Context）獨立為一個服務，與 Catalog Service（館藏索引）解耦，各自擁有獨立的資料庫與部署生命週期。

2. **事件驅動架構**：論文建立後透過 Kafka 發送 `PaperCreatedEvent`，下游的 Catalog Service 訂閱該事件並自動建立索引，實現最終一致性（Eventual Consistency），避免同步耦合的效能瓶頸。

3. **獨立擴展性**：投稿流量與查詢流量特性不同，拆分後可依據各自負載獨立水平擴展（HPA），在 Production 環境支援 3–10 個副本的彈性伸縮。

4. **故障隔離**：透過 Resilience4j Circuit Breaker，當 Kafka 或下游服務異常時，不會影響論文的建立與查詢功能，確保核心流程可用性。

---

## 架構設計

```
┌─────────────────────────────────────────────────────────┐
│                   Paper Submission Service               │
│                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌────────────┐ │
│  │  Controller   │───▶│   Service    │───▶│ Repository │ │
│  │  (REST API)   │    │  (Business)  │    │   (JPA)    │ │
│  └──────────────┘    └──────┬───────┘    └─────┬──────┘ │
│                             │                   │        │
│                             ▼                   ▼        │
│                    ┌────────────────┐    ┌────────────┐  │
│                    │  Kafka Producer│    │ PostgreSQL  │  │
│                    │  (Event Pub)   │    │  (papers)   │  │
│                    └────────┬───────┘    └────────────┘  │
│                             │                            │
└─────────────────────────────┼────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │   Kafka Topic    │
                    │  "paper-events"  │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ Catalog Service  │
                    │  (Consumer)      │
                    └──────────────────┘
```

**分層架構**：

| 層級 | 職責 | 對應類別 |
|---|---|---|
| Controller | 接收 HTTP 請求、參數驗證、路由分發 | `SubmissionController`, `InternalPaperController` |
| Service | 商業邏輯、交易管理、事件發布 | `SubmissionService` |
| Repository | 資料持久化、CRUD 操作 | `PaperRepository` |
| Kafka | 非同步事件發布 | `PaperEventProducer`, `PaperCreatedEvent` |
| DTO | 請求/回應資料傳輸物件 | `PaperRequest`, `PaperResponse` |
| Model | JPA 實體對映 | `Paper` |

---

## 目錄結構

```
com/papersubmission/
├── PaperSubmissionApplication.java    # Spring Boot 啟動入口
├── controller/
│   ├── SubmissionController.java      # 公開 REST API (外部存取)
│   └── InternalPaperController.java   # 內部 REST API (服務間呼叫)
├── dto/
│   ├── PaperRequest.java              # 建立論文請求 DTO (Java Record)
│   └── PaperResponse.java             # 論文回應 DTO (Java Record)
├── kafka/
│   ├── PaperCreatedEvent.java         # Kafka 事件 DTO (Java Record)
│   └── PaperEventProducer.java        # Kafka 事件發布元件
├── model/
│   └── Paper.java                     # JPA 實體類別
├── repository/
│   └── PaperRepository.java           # Spring Data JPA Repository
└── service/
    └── SubmissionService.java         # 核心商業邏輯服務
```

---

## 各模組詳細說明

### `PaperSubmissionApplication.java`

Spring Boot 應用程式入口，使用 `@SpringBootApplication` 啟用自動組態、元件掃描及 Spring Boot 預設設定。

### `controller/SubmissionController.java`

對外公開的 REST 控制器，掛載於 `/api/v1/submissions`。

- **`POST /api/v1/submissions`** — 建立新論文投稿
  - 接收 `@Valid @RequestBody PaperRequest`，經由 Bean Validation 驗證
  - 回傳 `PaperResponse` 包含完整論文資訊
- **`GET /api/v1/submissions/{id}`** — 依 UUID 查詢單一論文
- **`GET /api/v1/submissions`** — 查詢全部論文清單

### `controller/InternalPaperController.java`

內部服務間呼叫的控制器，掛載於 `/api/internal/papers`。

- **`GET /api/internal/papers/{id}`** — 供 Catalog Service 等內部服務查詢論文詳細資訊
- 不經過外部 Gateway 路由，僅限 Kubernetes 叢集內部透過 ClusterIP 存取

### `dto/PaperRequest.java`

以 Java Record 實作的不可變請求 DTO：

```java
public record PaperRequest(
    @NotBlank String title,      // 必填，論文標題
    @NotBlank String author,     // 必填，作者
    String abstractText,          // 選填，摘要
    String journal                // 選填，期刊名稱
) {}
```

### `dto/PaperResponse.java`

以 Java Record 實作的不可變回應 DTO，提供 `fromEntity(Paper)` 靜態工廠方法將 JPA 實體轉為回應物件：

```java
public record PaperResponse(
    UUID id, String title, String author,
    String abstractText, String journal, String status,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static PaperResponse fromEntity(Paper paper) { ... }
}
```

### `service/SubmissionService.java`

核心商業邏輯層：

- **`createPaper(PaperRequest)`**：
  1. 將 `PaperRequest` 轉換為 `Paper` 實體
  2. 透過 `PaperRepository.save()` 持久化至 PostgreSQL（`@Transactional`）
  3. 建構 `PaperCreatedEvent` 並透過 `PaperEventProducer` 非同步發送至 Kafka
  4. Kafka 發送採用 fire-and-forget + try-catch，失敗僅記錄 log，不影響主流程
  5. 回傳 `PaperResponse`
- **`getPaperById(UUID)`**：查詢論文，找不到時拋出 `RuntimeException`
- **`getAllPapers()`**：回傳所有論文的 `List<PaperResponse>`

### `model/Paper.java`

JPA 實體，對映至 `papers` 資料表：

| 欄位 | 型別 | 約束 | 說明 |
|---|---|---|---|
| `id` | `UUID` | PK, auto-generated | 主鍵，由 JPA 自動生成 |
| `title` | `VARCHAR(500)` | NOT NULL | 論文標題 |
| `author` | `VARCHAR(300)` | NOT NULL | 作者 |
| `abstractText` | `TEXT` | nullable | 摘要 |
| `journal` | `VARCHAR(300)` | nullable | 期刊名稱 |
| `status` | `VARCHAR(50)` | default "SUBMITTED" | 論文狀態 |
| `createdAt` | `TIMESTAMP` | NOT NULL, auto | 建立時間 |
| `updatedAt` | `TIMESTAMP` | NOT NULL, auto | 更新時間 |

使用 `@PrePersist` / `@PreUpdate` lifecycle callback 自動管理時間戳記。

### `repository/PaperRepository.java`

繼承 `JpaRepository<Paper, UUID>`，自動獲得標準 CRUD 方法（`save`, `findById`, `findAll`, `deleteById` 等），無需手動實作。

### `kafka/PaperEventProducer.java`

Kafka 事件發布器：

- 注入 `KafkaTemplate<String, String>` 與 `ObjectMapper`
- 將 `PaperCreatedEvent` 序列化為 JSON，發送至 `paper-events` topic
- 使用 `paperId` 作為 Kafka message key，確保同一論文的事件落在相同 partition
- 非同步發送並註冊 callback 記錄成功/失敗 log

### `kafka/PaperCreatedEvent.java`

以 Java Record 實作的事件 DTO，提供 `from(...)` 靜態工廠方法：

```java
public record PaperCreatedEvent(
    String id, String title, String author,
    String abstractText, String status, String createdAt
) {
    public static PaperCreatedEvent from(...) { ... }
}
```

`createdAt` 以 ISO-8601 格式序列化，確保跨服務時間戳記解析一致性。

---

## API 規格

### 公開 API（External）

#### `POST /api/v1/submissions` — 建立論文

**Request Body:**

```json
{
  "title": "A Study on Microservices",
  "author": "John Doe",
  "abstractText": "This paper explores...",
  "journal": "IEEE Software"
}
```

| 欄位 | 型別 | 必填 | 驗證規則 |
|---|---|---|---|
| `title` | String | 是 | `@NotBlank`，不可為空白 |
| `author` | String | 是 | `@NotBlank`，不可為空白 |
| `abstractText` | String | 否 | — |
| `journal` | String | 否 | — |

**Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "A Study on Microservices",
  "author": "John Doe",
  "abstractText": "This paper explores...",
  "journal": "IEEE Software",
  "status": "SUBMITTED",
  "createdAt": "2026-02-11T10:30:00",
  "updatedAt": "2026-02-11T10:30:00"
}
```

**副作用：** 建立成功後，非同步發送 `PaperCreatedEvent` 至 Kafka topic `paper-events`。

---

#### `GET /api/v1/submissions/{id}` — 查詢單一論文

**Path Parameter:**

| 參數 | 型別 | 說明 |
|---|---|---|
| `id` | UUID | 論文唯一識別碼 |

**Response (200 OK):** 同上述 `PaperResponse` 結構。

**Error (500):** 當 `id` 不存在時拋出 `RuntimeException`。

---

#### `GET /api/v1/submissions` — 查詢所有論文

**Response (200 OK):**

```json
[
  { "id": "...", "title": "...", ... },
  { "id": "...", "title": "...", ... }
]
```

---

### 內部 API（Internal，服務間呼叫）

#### `GET /api/internal/papers/{id}` — 內部查詢論文

與公開 API 的 `GET /{id}` 功能相同，但路徑不同，用於 Kubernetes 叢集內部服務直接呼叫（不經過 Istio Gateway）。

---

### Actuator Endpoints（運維監控）

| Endpoint | 說明 |
|---|---|
| `GET /actuator/health` | 健康檢查（含 DB、Kafka 狀態） |
| `GET /actuator/prometheus` | Prometheus 格式的 metrics |
| `GET /actuator/info` | 應用程式資訊 |

---

## 資料模型

### `papers` 資料表 Schema

```sql
CREATE TABLE IF NOT EXISTS papers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(500) NOT NULL,
    author        VARCHAR(300) NOT NULL,
    abstract_text TEXT,
    journal       VARCHAR(300),
    status        VARCHAR(50)  DEFAULT 'SUBMITTED',
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```

**備註：** JPA 透過 `spring.jpa.hibernate.ddl-auto: update` 自動管理 schema 更新，`schema.sql` 作為初始化參考。

---

## Kafka 事件機制

### Topic: `paper-events`

| 屬性 | 值 |
|---|---|
| Topic 名稱 | `paper-events` |
| Message Key | `paperId`（UUID 字串） |
| Message Value | JSON 格式的 `PaperCreatedEvent` |
| 發送模式 | 非同步 fire-and-forget |
| 錯誤處理 | try-catch + SLF4J 日誌，不中斷主流程 |

### 事件 Payload 範例

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "A Study on Microservices",
  "author": "John Doe",
  "abstractText": "This paper explores...",
  "status": "SUBMITTED",
  "createdAt": "2026-02-11T10:30:00"
}
```

### 事件流程

```
createPaper() ──▶ DB save ──▶ Build PaperCreatedEvent
                                        │
                                        ▼
                              PaperEventProducer.send()
                                        │
                              ┌─────────┴─────────┐
                              │  KafkaTemplate     │
                              │  .send("paper-events", │
                              │    key=paperId,    │
                              │    value=JSON)     │
                              └─────────┬─────────┘
                                        │
                              ┌─────────▼─────────┐
                              │  Kafka Broker      │
                              │  Topic: paper-events│
                              └─────────┬─────────┘
                                        │
                              ┌─────────▼─────────┐
                              │ Catalog Service    │
                              │ (Consumer Group)   │
                              └───────────────────┘
```

---

## Resilience4j 容錯機制

### Circuit Breaker 組態

```yaml
resilience4j:
  circuitbreaker:
    instances:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10            # 滑動窗口大小
        minimumNumberOfCalls: 5          # 最少呼叫次數才開始統計
        failureRateThreshold: 50         # 失敗率門檻 (%)
        waitDurationInOpenState: 10s     # Open 狀態等待時間
        permittedNumberOfCallsInHalfOpenState: 3  # Half-Open 時允許的呼叫數
```

### 狀態轉換

```
  CLOSED ──(失敗率 ≥ 50%)──▶ OPEN ──(等待 10s)──▶ HALF_OPEN
    ▲                                                  │
    └──────────(3 次呼叫成功率達標)──────────────────────┘
                                                       │
                          OPEN ◀──(仍未達標)────────────┘
```

- **CLOSED**：正常運作，統計最近 10 次呼叫的失敗率
- **OPEN**：當失敗率 ≥ 50%，斷路器跳脫，所有請求快速失敗（fail-fast），等待 10 秒
- **HALF_OPEN**：允許 3 次試探性呼叫，若成功率達標則回到 CLOSED，否則重回 OPEN

---

## 組態設定

### 環境變數

| 變數 | 預設值 | 說明 |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL 主機位址 |
| `DB_PORT` | `5432` | PostgreSQL 埠號 |
| `DB_NAME` | `submission_db` | 資料庫名稱 |
| `DB_USER` | `postgres` | 資料庫使用者 |
| `DB_PASS` | `postgres` | 資料庫密碼 |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka Bootstrap Servers |

### 相依服務

| 服務 | 用途 | 必要性 |
|---|---|---|
| PostgreSQL | 論文資料持久化 | **必要** — 無法啟動 |
| Kafka | 事件發布（非同步通知 Catalog Service） | **非必要** — 發送失敗僅記錄 log |

---

## 存取方式

### 1. Docker Compose（本地開發）

```bash
cd infra
docker compose up -d
# 服務可透過 http://localhost:8081 存取
curl http://localhost:8081/api/v1/submissions
```

### 2. Kubernetes + Istio（叢集部署）

**外部存取**（經由 Istio Gateway）：

```bash
# 透過 Istio IngressGateway
curl http://<ISTIO_GATEWAY_IP>/api/v1/submissions
```

Istio VirtualService 路由規則將 `/api/v1/submissions` 與 `/api/internal` 導向 `submission-service:8081`。

**叢集內部存取**（Service DNS）：

```bash
# 在叢集內的其他 Pod 中
curl http://submission-service.paper-demo.svc.cluster.local:8081/api/internal/papers/{id}
```

### 3. Helm 部署指令

```bash
# Dev 環境
helm upgrade --install submission infra/helm-charts/paper-submission \
  -f infra/helm-charts/paper-submission/values-dev.yaml \
  -n paper-demo

# Production 環境
helm upgrade --install submission infra/helm-charts/paper-submission \
  -f infra/helm-charts/paper-submission/values-prod.yaml \
  -n paper-demo
```

### 4. 一鍵部署腳本

```bash
./scripts/deploy-to-k8s.sh dev                           # 本地 dev 部署
./scripts/deploy-to-k8s.sh prod ghcr.io/user latest      # 遠端 registry 部署
```

---

## 效能分析

### 資源配置（依環境）

| 環境 | Replicas | CPU Request | CPU Limit | Memory Request | Memory Limit | HPA |
|---|---|---|---|---|---|---|
| Dev | 1 | 50m | 500m | 256Mi | 512Mi | 停用 |
| QA | 2 | 100m | 500m | 256Mi | 512Mi | 停用 |
| Prod | 3–10 | 200m | 1000m | 512Mi | 1Gi | 啟用 |

### HPA 自動擴展策略（Production）

| 指標 | 目標使用率 | 最小副本 | 最大副本 |
|---|---|---|---|
| CPU | 70% | 3 | 10 |
| Memory | 75% | 3 | 10 |

### 效能特性分析

#### 寫入路徑（POST /api/v1/submissions）

```
HTTP Request → Bean Validation → JPA save (DB write) → Kafka send (async) → HTTP Response
                                        │                      │
                              同步（blocking）           非同步（non-blocking）
                              ~5–20ms                    ~1–5ms（不阻塞回應）
```

- **延遲瓶頸**：主要在 PostgreSQL 寫入（~5–20ms），Kafka 發送為非同步不影響回應時間
- **吞吐量**：單一實例約 200–500 TPS（取決於 DB 連線池與硬體配置）
- **資料一致性**：DB 寫入為強一致性（`@Transactional`），Kafka 事件為最終一致性

#### 讀取路徑（GET /api/v1/submissions/{id}）

```
HTTP Request → JPA findById (DB read) → Entity → DTO → HTTP Response
                      │
              ~1–10ms（主鍵查詢）
```

- **延遲**：UUID 主鍵查詢效能穩定，約 1–10ms
- **吞吐量**：單一實例約 500–1000 QPS

#### 讀取路徑（GET /api/v1/submissions）

- **注意事項**：`findAll()` 無分頁機制，當資料量大時可能造成效能問題
- **改善建議**：加入 Spring Data Pageable 支援分頁查詢

### 可觀測性

| 面向 | 工具 | Endpoint |
|---|---|---|
| Metrics | Micrometer + Prometheus | `/actuator/prometheus` |
| Health Check | Spring Boot Actuator | `/actuator/health` |
| Circuit Breaker 狀態 | Resilience4j Health Indicator | `/actuator/health`（含 CB 狀態） |

Helm Chart 已配置 Prometheus scrape annotations：

```yaml
prometheus.io/scrape: "true"
prometheus.io/port: "8081"
prometheus.io/path: "/actuator/prometheus"
```

### 可靠性設計

| 機制 | 實作 | 目的 |
|---|---|---|
| Startup Probe | `/actuator/health`, initialDelay=30s | 避免在 JVM 啟動期間被殺掉 |
| Liveness Probe | `/actuator/health`, period=10s | 偵測 deadlock/hang 並重啟 |
| Readiness Probe | `/actuator/health`, period=5s | 確保流量只導向就緒的 Pod |
| PDB | minAvailable=2 (prod) | 滾動更新時保證最低可用副本 |
| Circuit Breaker | Resilience4j | Kafka 或下游故障時快速失敗 |

### 已知限制與改善方向

| 項目 | 現況 | 建議改善 |
|---|---|---|
| 分頁查詢 | `findAll()` 無分頁 | 加入 `Pageable` 參數支援 `page`, `size` |
| 錯誤處理 | 查無資料拋出 `RuntimeException` | 改用自訂 Exception + `@ControllerAdvice` 全域錯誤處理 |
| 快取 | 無 | 對高頻讀取加入 Redis/Caffeine 快取 |
| 認證授權 | 無 | 整合 Spring Security + OAuth2/JWT |
| 資料庫索引 | 僅主鍵索引 | 依查詢模式加入 `title`, `author` 複合索引 |
| Kafka 重試 | fire-and-forget | 加入 Kafka Producer retry 與 Dead Letter Topic |
| API 文件 | 無 | 整合 SpringDoc OpenAPI (Swagger UI) |

---

## 建置與部署

### 本地建置

```bash
cd paper-submission
./mvnw clean package -DskipTests
java -jar target/paper-submission-1.0.0.jar
```

### Docker 建置

```bash
docker build -t paper-submission:latest ./paper-submission
```

Multi-stage Dockerfile：
- **Build Stage**: `maven:3.9-eclipse-temurin-17` — 編譯 JAR
- **Runtime Stage**: `eclipse-temurin:17-jre` — 僅包含 JRE，減小映像體積

### CI/CD 建置與推送

```bash
./scripts/build-and-push.sh ghcr.io/<username> latest
```

### 技術棧依賴版本

| 依賴 | 版本 |
|---|---|
| Spring Boot | 3.4.1 |
| Java | 17 |
| Resilience4j | 2.2.0 |
| PostgreSQL Driver | (managed by Spring Boot BOM) |
| Spring Kafka | (managed by Spring Boot BOM) |
| Micrometer Prometheus | (managed by Spring Boot BOM) |
