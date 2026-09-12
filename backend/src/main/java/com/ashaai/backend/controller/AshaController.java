package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Asha;
import com.ashaai.backend.repository.AshaRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ashas")
public class AshaController {

    private final AshaRepository ashaRepository;

    public AshaController(AshaRepository ashaRepository) {
        this.ashaRepository = ashaRepository;
    }

    @GetMapping
    public ResponseEntity<List<Asha>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(ashaRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<Asha> create(@RequestBody Asha entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(ashaRepository.save(entity));
    }
}
