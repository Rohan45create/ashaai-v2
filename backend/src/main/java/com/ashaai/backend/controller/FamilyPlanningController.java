package com.ashaai.backend.controller;

import com.ashaai.backend.entity.FamilyPlanning;
import com.ashaai.backend.repository.FamilyPlanningRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/familyPlannings")
public class FamilyPlanningController {

    private final FamilyPlanningRepository familyPlanningRepository;

    public FamilyPlanningController(FamilyPlanningRepository familyPlanningRepository) {
        this.familyPlanningRepository = familyPlanningRepository;
    }

    @GetMapping
    public ResponseEntity<List<FamilyPlanning>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(familyPlanningRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<FamilyPlanning> create(@RequestBody FamilyPlanning entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(familyPlanningRepository.save(entity));
    }
}
