package com.papersubmission.service;

import com.papersubmission.dto.PaperRequest;
import com.papersubmission.dto.PaperResponse;
import com.papersubmission.kafka.PaperCreatedEvent;
import com.papersubmission.kafka.PaperEventProducer;
import com.papersubmission.model.Paper;
import com.papersubmission.repository.PaperRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionService.class);

    private final PaperRepository paperRepository;
    private final PaperEventProducer paperEventProducer;

    public SubmissionService(PaperRepository paperRepository, PaperEventProducer paperEventProducer) {
        this.paperRepository = paperRepository;
        this.paperEventProducer = paperEventProducer;
    }

    @Transactional
    public PaperResponse createPaper(PaperRequest request) {
        Paper paper = new Paper();
        paper.setTitle(request.title());
        paper.setAuthor(request.author());
        paper.setAbstractText(request.abstractText());
        paper.setJournal(request.journal());
        paper.setStatus("SUBMITTED");

        Paper savedPaper = paperRepository.save(paper);
        logger.info("Paper created with ID: {}", savedPaper.getId());

        // Send Kafka event (fire-and-forget with logging)
        try {
            PaperCreatedEvent event = PaperCreatedEvent.from(
                    savedPaper.getId().toString(),
                    savedPaper.getTitle(),
                    savedPaper.getAuthor(),
                    savedPaper.getAbstractText(),
                    savedPaper.getStatus(),
                    savedPaper.getCreatedAt()
            );
            paperEventProducer.sendPaperCreatedEvent(event, savedPaper.getId().toString());
        } catch (Exception e) {
            logger.error("Failed to send paper created event for paper ID: {}", savedPaper.getId(), e);
            // Don't throw exception - event sending failure should not fail the API call
        }

        return PaperResponse.fromEntity(savedPaper);
    }

    public PaperResponse getPaperById(UUID id) {
        Paper paper = paperRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paper not found with ID: " + id));
        return PaperResponse.fromEntity(paper);
    }

    public List<PaperResponse> getAllPapers() {
        return paperRepository.findAll().stream()
                .map(PaperResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
