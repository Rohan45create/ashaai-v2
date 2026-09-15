package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Pregnancy;
import com.ashaai.backend.repository.PregnancyRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import com.ashaai.backend.service.AncFlaggingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pregnancies")
public class PregnancyController {

    private final PregnancyRepository pregnancyRepository;
    private final AncFlaggingService ancFlaggingService;

    public PregnancyController(PregnancyRepository pregnancyRepository, AncFlaggingService ancFlaggingService) {
        this.pregnancyRepository = pregnancyRepository;
        this.ancFlaggingService = ancFlaggingService;
    }

    @GetMapping
    public ResponseEntity<List<Pregnancy>> getPregnancies(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        List<Pregnancy> all = pregnancyRepository.findAll();
        List<Pregnancy> ashasPregnancies = all.stream()
                .filter(p -> p.getAsha() != null && p.getAsha().getId().equals(auth.getAshaId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ashasPregnancies);
    }

    @PostMapping
    public ResponseEntity<Pregnancy> createPregnancy(@RequestBody Pregnancy pregnancy, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        ancFlaggingService.evaluateHighRisk(pregnancy);
        return ResponseEntity.status(HttpStatus.CREATED).body(pregnancyRepository.save(pregnancy));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Pregnancy> updatePregnancy(@PathVariable UUID id, @RequestBody Pregnancy pregnancyDetails) {
        return pregnancyRepository.findById(id).map(existing -> {
            existing.setStatus(pregnancyDetails.getStatus());
            existing.setLmp(pregnancyDetails.getLmp());
            existing.setEdd(pregnancyDetails.getEdd());
            existing.setBloodGroup(pregnancyDetails.getBloodGroup());
            existing.setHaemoglobinGdl(pregnancyDetails.getHaemoglobinGdl());
            existing.setAnc1Date(pregnancyDetails.getAnc1Date());
            existing.setAnc2Date(pregnancyDetails.getAnc2Date());
            existing.setAnc3Date(pregnancyDetails.getAnc3Date());
            existing.setAnc4Date(pregnancyDetails.getAnc4Date());
            existing.setLastAncDate(pregnancyDetails.getLastAncDate());
            existing.setDeliveryInstitution(pregnancyDetails.getDeliveryInstitution());
            existing.setJsyBenefit(pregnancyDetails.getJsyBenefit());

            ancFlaggingService.evaluateHighRisk(existing);
            return ResponseEntity.ok(pregnancyRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/pregnancies/{id}/visit-history
     * Returns ANC visit timeline for a pregnancy record.
     * Used by PendingReview panel to display "Past Visits" accordion.
     */
    @GetMapping("/{id}/visit-history")
    public ResponseEntity<VisitHistoryResponse> getVisitHistory(@PathVariable UUID id) {
        return pregnancyRepository.findById(id).map(p -> {
            VisitHistoryResponse resp = new VisitHistoryResponse(
                p.getId().toString(),
                p.getMotherMember() != null ? p.getMotherMember().getName() : null,
                p.getLmp() != null ? p.getLmp().toString() : null,
                p.getEdd() != null ? p.getEdd().toString() : null,
                p.getAnc1Date() != null ? p.getAnc1Date().toString() : null,
                p.getAnc2Date() != null ? p.getAnc2Date().toString() : null,
                p.getAnc3Date() != null ? p.getAnc3Date().toString() : null,
                p.getAnc4Date() != null ? p.getAnc4Date().toString() : null,
                p.getLastAncDate() != null ? p.getLastAncDate().toString() : null,
                Boolean.TRUE.equals(p.getHighRiskFlag()),
                p.getHighRiskReasons(),
                p.getStatus()
            );
            return ResponseEntity.ok(resp);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Safe projection for visit history — no sensitive identity data */
    public record VisitHistoryResponse(
        String pregnancyId,
        String motherName,
        String lmp,
        String edd,
        String anc1Date,
        String anc2Date,
        String anc3Date,
        String anc4Date,
        String lastAncDate,
        boolean highRisk,
        java.util.List<String> highRiskReasons,
        String status
    ) {}
}
