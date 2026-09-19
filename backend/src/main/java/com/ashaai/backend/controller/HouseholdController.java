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
    public ResponseEntity<List<Household>> getHouseholds(
            @RequestParam(required = false) UUID ashaId,
            org.springframework.security.core.Authentication authentication) {
        UUID effectiveAshaId = ashaId;
        if (effectiveAshaId == null && authentication instanceof AshaAuthenticationToken auth) {
            effectiveAshaId = auth.getAshaId();
        }
        if (effectiveAshaId != null) {
            return ResponseEntity.ok(householdRepository.findByAsha_Id(effectiveAshaId));
        }
        return ResponseEntity.ok(householdRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<Household> createHousehold(
            @RequestBody Household household,
            org.springframework.security.core.Authentication authentication) {
        if (household.getCreatedAt() == null) {
            household.setCreatedAt(java.time.OffsetDateTime.now());
        }
        if (household.getUpdatedAt() == null) {
            household.setUpdatedAt(java.time.OffsetDateTime.now());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(householdRepository.save(household));
    }
}
