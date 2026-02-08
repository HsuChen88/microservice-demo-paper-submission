package com.papersubmission.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaperEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(PaperEventProducer.class);
    private static final String TOPIC = "paper-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaperEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendPaperCreatedEvent(PaperCreatedEvent event, String paperId) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, paperId, eventJson)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            logger.error("Failed to send paper created event for paper ID: {}", paperId, ex);
                        } else {
                            logger.info("Successfully sent paper created event for paper ID: {} to topic: {}", 
                                    paperId, TOPIC);
                        }
                    });
        } catch (Exception e) {
            logger.error("Error serializing paper created event for paper ID: {}", paperId, e);
        }
    }
}
