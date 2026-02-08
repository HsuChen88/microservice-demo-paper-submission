package com.papersubmission.kafka;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record PaperCreatedEvent(
        String id,
        String title,
        String author,
        String abstractText,
        String status,
        String createdAt
) {
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static PaperCreatedEvent from(String id, String title, String author, 
                                         String abstractText, String status, LocalDateTime createdAt) {
        return new PaperCreatedEvent(
                id,
                title,
                author,
                abstractText,
                status,
                createdAt.format(ISO_FORMATTER)
        );
    }
}
