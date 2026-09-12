package com.ashaai.backend.controller;

import com.ashaai.backend.entity.PendingReview;
import com.ashaai.backend.repository.PendingReviewRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import com.ashaai.backend.service.AncFlaggingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/pendingReviews")
public class PendingReviewController {

    private final PendingReviewRepository pendingReviewRepository;
    private final AncFlaggingService ancFlaggingService;

    public PendingReviewController(
            PendingReviewRepository pendingReviewRepository,
            AncFlaggingService ancFlaggingService
    ) {
        this.pendingReviewRepository = pendingReviewRepository;
        this.ancFlaggingService = ancFlaggingService;
    }

    @GetMapping
    public ResponseEntity<List<PendingReview>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(pendingReviewRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<PendingReview> create(@RequestBody PendingReview entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(pendingReviewRepository.save(entity));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveReview(
            @PathVariable UUID id,
            org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        Optional<PendingReview> reviewOpt = pendingReviewRepository.findById(id);
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PendingReview review = reviewOpt.get();
        UUID supervisorId = (auth != null && auth.getAshaHeadId() != null)
                ? auth.getAshaHeadId()
                : UUID.randomUUID();

        if ("pregnancies".equalsIgnoreCase(review.getTableName())) {
            ancFlaggingService.confirmHighRiskBySupervisor(id, supervisorId, true);
        } else {
            review.setStatus("CONFIRMED");
            pendingReviewRepository.save(review);
        }
        return ResponseEntity.ok(review);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectReview(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body,
            org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        Optional<PendingReview> reviewOpt = pendingReviewRepository.findById(id);
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PendingReview review = reviewOpt.get();
        UUID supervisorId = (auth != null && auth.getAshaHeadId() != null)
                ? auth.getAshaHeadId()
                : UUID.randomUUID();

        if ("pregnancies".equalsIgnoreCase(review.getTableName())) {
            ancFlaggingService.confirmHighRiskBySupervisor(id, supervisorId, false);
        } else {
            review.setStatus("REJECTED");
            if (body != null && body.containsKey("reason")) {
                review.setReason(
                        (review.getReason() != null ? review.getReason() + " | Rejection reason: " : "")
                        + body.get("reason")
                );
            }
            pendingReviewRepository.save(review);
        }
        return ResponseEntity.ok(review);
    }
}
