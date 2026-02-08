package com.papersubmission.dto;

import jakarta.validation.constraints.NotBlank;

public record PaperRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Author is required")
        String author,

        String abstractText,

        String journal
) {
}
