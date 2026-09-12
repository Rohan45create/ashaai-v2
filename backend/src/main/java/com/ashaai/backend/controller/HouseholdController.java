package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Household;
import com.ashaai.backend.repository.HouseholdRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/households")
public class HouseholdController {

    private final HouseholdRepository householdRepository;

    public HouseholdController(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    @GetMapping
    public ResponseEntity<List<Household>> getHouseholds(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        // Find households for the current Asha
        List<Household> all = householdRepository.findAll();
        List<Household> ashasHouseholds = all.stream()
                .filter(h -> h.getAsha() != null && h.getAsha().getId().equals(auth.getAshaId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ashasHouseholds);
    }
    
    @PostMapping
    public ResponseEntity<Household> createHousehold(@RequestBody Household household, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        // TODO: Map from DTO properly
        // For phase 2 testing, accept entity directly for now
        return ResponseEntity.status(HttpStatus.CREATED).body(householdRepository.save(household));
    }
}
