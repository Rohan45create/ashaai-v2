package com.ashaai.backend.controller;

import com.ashaai.backend.entity.DiseaseCase;
import com.ashaai.backend.repository.DiseaseCaseRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diseaseCases")
public class DiseaseCaseController {

    private final DiseaseCaseRepository diseaseCaseRepository;

    public DiseaseCaseController(DiseaseCaseRepository diseaseCaseRepository) {
        this.diseaseCaseRepository = diseaseCaseRepository;
    }

    @GetMapping
    public ResponseEntity<List<DiseaseCase>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(diseaseCaseRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<DiseaseCase> create(@RequestBody DiseaseCase entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(diseaseCaseRepository.save(entity));
    }
}
