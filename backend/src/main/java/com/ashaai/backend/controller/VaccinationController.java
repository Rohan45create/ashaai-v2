package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Vaccination;
import com.ashaai.backend.repository.VaccinationRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vaccinations")
public class VaccinationController {

    private final VaccinationRepository vaccinationRepository;

    public VaccinationController(VaccinationRepository vaccinationRepository) {
        this.vaccinationRepository = vaccinationRepository;
    }

    @GetMapping
    public ResponseEntity<List<Vaccination>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(vaccinationRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<Vaccination> create(@RequestBody Vaccination entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(vaccinationRepository.save(entity));
    }
}
