package com.ashaai.backend.controller;

import com.ashaai.backend.entity.MedicalOfficer;
import com.ashaai.backend.repository.MedicalOfficerRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicalOfficers")
public class MedicalOfficerController {

    private final MedicalOfficerRepository medicalOfficerRepository;

    public MedicalOfficerController(MedicalOfficerRepository medicalOfficerRepository) {
        this.medicalOfficerRepository = medicalOfficerRepository;
    }

    @GetMapping
    public ResponseEntity<List<MedicalOfficer>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(medicalOfficerRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<MedicalOfficer> create(@RequestBody MedicalOfficer entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(medicalOfficerRepository.save(entity));
    }
}
