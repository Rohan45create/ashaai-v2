package com.ashaai.backend.controller;

import com.ashaai.backend.entity.DeathRecord;
import com.ashaai.backend.repository.DeathRecordRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deathRecords")
public class DeathRecordController {

    private final DeathRecordRepository deathRecordRepository;

    public DeathRecordController(DeathRecordRepository deathRecordRepository) {
        this.deathRecordRepository = deathRecordRepository;
    }

    @GetMapping
    public ResponseEntity<List<DeathRecord>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(deathRecordRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<DeathRecord> create(@RequestBody DeathRecord entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(deathRecordRepository.save(entity));
    }
}
