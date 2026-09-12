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
}
