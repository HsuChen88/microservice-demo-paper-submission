package com.papersubmission.dto;

import com.papersubmission.model.Paper;

import java.time.LocalDateTime;
import java.util.UUID;

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
