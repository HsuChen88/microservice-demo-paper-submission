# DTO（Data Transfer Object）模組 — 技術文件

## 目錄

- [模組概述](#模組概述)
- [為什麼實作此模組](#為什麼實作此模組)
- [目錄結構](#目錄結構)
- [各類別詳細說明](#各類別詳細說明)
- [輸入輸出規格](#輸入輸出規格)
- [資料流向](#資料流向)
- [驗證機制](#驗證機制)
- [與其他模組的關係](#與其他模組的關係)
- [效能分析](#效能分析)
- [設計決策與考量](#設計決策與考量)

---

## 模組概述

`dto` 套件包含 Paper Submission Service 的資料傳輸物件（Data Transfer Object），負責定義 REST API 的輸入請求格式與輸出回應格式。所有 DTO 均採用 Java Record 實作，確保不可變性（immutability）與簡潔性。

| 屬性 | 值 |
|---|---|
| 套件路徑 | `com.papersubmission.dto` |
| 類別數量 | 2 |
| 實作方式 | Java Record（Java 16+） |
| 驗證框架 | Jakarta Bean Validation（`spring-boot-starter-validation`） |

---

## 為什麼實作此模組

1. **關注點分離**：DTO 將 API 層的資料結構與 JPA 實體（`Paper`）解耦。API 的欄位變更不會直接影響資料庫 schema，反之亦然。

2. **安全性**：避免直接暴露 JPA 實體給外部 Client，防止敏感欄位洩漏或 mass assignment 攻擊。例如 `PaperRequest` 不包含 `id`、`status`、`createdAt`、`updatedAt` 等伺服器管控的欄位。

3. **輸入驗證**：`PaperRequest` 透過 `@NotBlank` 註解在 Controller 層自動進行 Bean Validation，在進入商業邏輯前即攔截不合法的輸入。

4. **序列化控制**：`PaperResponse` 定義了精確的輸出欄位與格式，確保 API 回應結構一致且可預測。

5. **不可變性**：Java Record 天生不可變（immutable），消除了共享可變狀態（shared mutable state）造成的併發安全問題。

---

## 目錄結構

```
dto/
├── PaperRequest.java     # 建立論文的請求 DTO
└── PaperResponse.java    # 論文查詢的回應 DTO
```

---

## 各類別詳細說明

### `PaperRequest.java` — 請求 DTO

以 Java Record 定義的不可變請求物件，用於 `POST /api/v1/submissions` 建立論文時的 Request Body。

```java
public record PaperRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Author is required")
        String author,

        String abstractText,

        String journal
) {}
```

| 欄位 | 型別 | 必填 | 驗證規則 | 說明 |
|---|---|---|---|---|
| `title` | `String` | 是 | `@NotBlank` — 不可為 null、空字串或純空白 | 論文標題 |
| `author` | `String` | 是 | `@NotBlank` — 不可為 null、空字串或純空白 | 作者姓名 |
| `abstractText` | `String` | 否 | 無 | 論文摘要 |
| `journal` | `String` | 否 | 無 | 期刊名稱 |

**使用位置**：

- `SubmissionController.createPaper(@Valid @RequestBody PaperRequest request)`
- `SubmissionService.createPaper(PaperRequest request)`

---

### `PaperResponse.java` — 回應 DTO

以 Java Record 定義的不可變回應物件，用於所有論文查詢 API 的回傳結果。

```java
public record PaperResponse(
        UUID id,
        String title,
        String author,
        String abstractText,
        String journal,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaperResponse fromEntity(Paper paper) {
        return new PaperResponse(
                paper.getId(),
                paper.getTitle(),
                paper.getAuthor(),
                paper.getAbstractText(),
                paper.getJournal(),
                paper.getStatus(),
                paper.getCreatedAt(),
                paper.getUpdatedAt()
        );
    }
}
```

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | `UUID` | 論文唯一識別碼（伺服器自動生成） |
| `title` | `String` | 論文標題 |
| `author` | `String` | 作者姓名 |
| `abstractText` | `String` | 論文摘要 |
| `journal` | `String` | 期刊名稱 |
| `status` | `String` | 論文狀態（如 `SUBMITTED`） |
| `createdAt` | `LocalDateTime` | 建立時間 |
| `updatedAt` | `LocalDateTime` | 最後更新時間 |

**`fromEntity(Paper)` 靜態工廠方法**：

將 JPA 實體 `Paper` 轉換為 `PaperResponse`，集中管理 Entity → DTO 的對映邏輯。

**使用位置**：

- `SubmissionService.createPaper()` — 建立後回傳
- `SubmissionService.getPaperById()` — 單一查詢回傳
- `SubmissionService.getAllPapers()` — 全部查詢回傳（stream map）
- `SubmissionController` — 所有端點的 `ResponseEntity<PaperResponse>` 回傳
- `InternalPaperController.getPaperById()` — 內部 API 回傳

---

## 輸入輸出規格

### 輸入：`PaperRequest` JSON 範例

**合法請求（完整欄位）：**

```json
{
  "title": "A Study on Microservices Architecture",
  "author": "John Doe",
  "abstractText": "This paper explores the benefits and challenges...",
  "journal": "IEEE Software"
}
```

**合法請求（最小必填欄位）：**

```json
{
  "title": "A Study on Microservices",
  "author": "John Doe"
}
```

**不合法請求（觸發驗證錯誤）：**

```json
{
  "title": "",
  "author": null
}
```

→ Spring Boot 回傳 400 Bad Request，包含驗證錯誤訊息 `"Title is required"` / `"Author is required"`。

---

### 輸出：`PaperResponse` JSON 範例

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "A Study on Microservices Architecture",
  "author": "John Doe",
  "abstractText": "This paper explores the benefits and challenges...",
  "journal": "IEEE Software",
  "status": "SUBMITTED",
  "createdAt": "2026-02-11T10:30:00",
  "updatedAt": "2026-02-11T10:30:00"
}
```

**序列化行為**：

- `UUID` → JSON String（`"550e8400-..."`)
- `LocalDateTime` → ISO-8601 String（`"2026-02-11T10:30:00"`），由 Jackson 自動處理
- `null` 欄位 → JSON `null`（如 `abstractText` 未提供時）

---

## 資料流向

### 寫入流程（POST）

```
Client JSON Request
        │
        ▼
  Jackson Deserialize
        │
        ▼
  PaperRequest (Record)
        │  @Valid Bean Validation
        ▼
  SubmissionService.createPaper()
        │  手動欄位對映
        ▼
  Paper (JPA Entity)
        │  JPA save()
        ▼
  PostgreSQL
        │  savedPaper
        ▼
  PaperResponse.fromEntity(savedPaper)
        │
        ▼
  Jackson Serialize → JSON Response
```

### 讀取流程（GET）

```
  PostgreSQL
        │  JPA findById / findAll
        ▼
  Paper (JPA Entity)
        │
        ▼
  PaperResponse.fromEntity(paper)
        │
        ▼
  Jackson Serialize → JSON Response
```

---

## 驗證機制

### Bean Validation 流程

```
HTTP Request Body (JSON)
        │
        ▼
  Jackson → PaperRequest
        │
        ▼
  @Valid 觸發 Bean Validation
        │
  ┌─────┴─────┐
  │ 通過       │ 失敗
  ▼           ▼
Controller   MethodArgumentNotValidException
  method      → Spring Boot 自動回傳 400 Bad Request
```

### 使用的驗證註解

| 註解 | 套件 | 目標欄位 | 行為 |
|---|---|---|---|
| `@NotBlank` | `jakarta.validation.constraints` | `title`, `author` | 拒絕 null、空字串 `""`、純空白 `"   "` |
| `@Valid` | `jakarta.validation` | Controller 方法參數 | 觸發巢狀物件的 Bean Validation |

---

## 與其他模組的關係

```
                    ┌─────────────────┐
                    │   Controller    │
                    │  (接收/回傳)     │
                    └────┬──────┬─────┘
                         │      │
              PaperRequest    PaperResponse
                         │      ▲
                         ▼      │
                    ┌────────────────┐
                    │    Service     │
                    │  (商業邏輯)     │
                    └────┬──────┬───┘
                         │      │
                    手動對映   fromEntity()
                         │      │
                         ▼      ▲
                    ┌────────────────┐
                    │  Model (Paper) │
                    │  (JPA Entity)  │
                    └────────────────┘
```

| 模組 | 與 DTO 的關係 |
|---|---|
| `controller/` | 接收 `PaperRequest`，回傳 `ResponseEntity<PaperResponse>` |
| `service/` | 消費 `PaperRequest` 建立實體，使用 `PaperResponse.fromEntity()` 生成回應 |
| `model/Paper` | `PaperResponse.fromEntity()` 的輸入源，`PaperRequest` 的欄位對映目標 |
| `kafka/PaperCreatedEvent` | 與 `PaperResponse` 類似但用途不同：事件只攜帶必要欄位，且 `createdAt` 為 ISO String |

---

## 效能分析

### 記憶體效率

- **Java Record**：編譯時自動生成 `equals()`、`hashCode()`、`toString()`，無需手動實作或引入 Lombok
- **不可變性**：Record 的所有欄位為 `final`，無 setter，可安全在多執行緒間共享
- **輕量級**：無 proxy、無反射增強，相較 JavaBean 模式減少 ~30% 記憶體開銷

### 序列化/反序列化效能

| 操作 | 使用工具 | 延遲 |
|---|---|---|
| JSON → `PaperRequest` | Jackson Databind | ~0.01–0.1ms |
| `Paper` → `PaperResponse` | `fromEntity()` 靜態方法 | ~0.001ms（純欄位複製） |
| `PaperResponse` → JSON | Jackson Databind | ~0.01–0.1ms |

### Entity-DTO 轉換策略

| 策略 | 本專案採用 | 說明 |
|---|---|---|
| 手動對映 | ✅ `fromEntity()` 靜態工廠 | 簡單直觀，零外部依賴 |
| MapStruct | ❌ | 適合大量 DTO 對映，目前規模不需要 |
| ModelMapper | ❌ | 反射式對映，效能較差 |

---

## 設計決策與考量

### 為什麼用 Java Record 而非傳統 POJO？

| 面向 | Java Record | POJO + Lombok |
|---|---|---|
| 不可變性 | 內建，所有欄位 final | 需手動確保或使用 `@Value` |
| 樣板程式碼 | 零 — 自動生成 equals/hashCode/toString | 需 `@Data` 或 `@Getter`/`@Setter` |
| 外部依賴 | 無 | 需要 Lombok 依賴與 IDE 插件 |
| 序列化 | Jackson 原生支援 Record | Jackson 原生支援 |
| 適用場景 | DTO、Event、Value Object | 可變實體、Builder 模式 |

### 為什麼 PaperRequest 不包含 id、status、timestamps？

- **`id`**：由伺服器端 JPA `@GeneratedValue(strategy = GenerationType.UUID)` 自動生成，Client 不應指定
- **`status`**：預設為 `"SUBMITTED"`，由 Service 層管控狀態流轉邏輯
- **`createdAt` / `updatedAt`**：由 `@PrePersist` / `@PreUpdate` lifecycle callback 自動管理

### 為什麼 PaperResponse 使用 fromEntity() 而非 Constructor？

- **語義清晰**：`fromEntity(paper)` 明確表達「從實體轉換」的意圖
- **集中管理**：對映邏輯集中在 `PaperResponse` 內部，修改欄位時只需改一處
- **未來擴展**：可輕易增加多個工廠方法如 `fromDTO()`、`fromEvent()` 等

### 已知限制與改善方向

| 項目 | 現況 | 建議改善 |
|---|---|---|
| 錯誤回應格式 | Spring Boot 預設 400 錯誤格式 | 自訂 `@ControllerAdvice` 統一錯誤回應 DTO |
| 欄位長度驗證 | 僅 `@NotBlank` | 加入 `@Size(max=500)` 與 DB 欄位長度一致 |
| 分頁回應 | 無 | 新增 `PagedResponse<T>` 包含 `content`, `page`, `size`, `totalElements` |
| API 版本 | 僅 v1 | 若 DTO 結構變更，可新增 `v2` 版本 DTO 保持向後相容 |
| OpenAPI 文件 | 無 | 加入 `@Schema` 註解生成 Swagger 文件 |
