package com.ashaai.backend.controller;

import com.ashaai.backend.entity.AshaHead;
import com.ashaai.backend.repository.AshaHeadRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ashaHeads")
public class AshaHeadController {

    private final AshaHeadRepository ashaHeadRepository;

    public AshaHeadController(AshaHeadRepository ashaHeadRepository) {
        this.ashaHeadRepository = ashaHeadRepository;
    }

    @GetMapping
    public ResponseEntity<List<AshaHead>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(ashaHeadRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<AshaHead> create(@RequestBody AshaHead entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(ashaHeadRepository.save(entity));
    }
}
