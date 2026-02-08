package com.papersubmission.controller;

import com.papersubmission.dto.PaperRequest;
import com.papersubmission.dto.PaperResponse;
import com.papersubmission.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<PaperResponse> createPaper(@Valid @RequestBody PaperRequest request) {
        PaperResponse response = submissionService.createPaper(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaperResponse> getPaperById(@PathVariable UUID id) {
        PaperResponse response = submissionService.getPaperById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<PaperResponse>> getAllPapers() {
        List<PaperResponse> papers = submissionService.getAllPapers();
        return ResponseEntity.ok(papers);
    }
}
