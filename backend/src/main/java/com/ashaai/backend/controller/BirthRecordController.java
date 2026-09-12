package com.ashaai.backend.controller;

import com.ashaai.backend.entity.BirthRecord;
import com.ashaai.backend.repository.BirthRecordRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/birthRecords")
public class BirthRecordController {

    private final BirthRecordRepository birthRecordRepository;

    public BirthRecordController(BirthRecordRepository birthRecordRepository) {
        this.birthRecordRepository = birthRecordRepository;
    }

    @GetMapping
    public ResponseEntity<List<BirthRecord>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(birthRecordRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<BirthRecord> create(@RequestBody BirthRecord entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(birthRecordRepository.save(entity));
    }
}
