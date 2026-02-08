package com.papersubmission.controller;

import com.papersubmission.dto.PaperResponse;
import com.papersubmission.service.SubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/papers")
public class InternalPaperController {

    private final SubmissionService submissionService;

    public InternalPaperController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaperResponse> getPaperById(@PathVariable UUID id) {
        PaperResponse response = submissionService.getPaperById(id);
        return ResponseEntity.ok(response);
    }
}
