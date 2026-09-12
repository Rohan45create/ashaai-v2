package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Ngo;
import com.ashaai.backend.repository.NgoRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ngos")
public class NgoController {

    private final NgoRepository ngoRepository;

    public NgoController(NgoRepository ngoRepository) {
        this.ngoRepository = ngoRepository;
    }

    @GetMapping
    public ResponseEntity<List<Ngo>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(ngoRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<Ngo> create(@RequestBody Ngo entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(ngoRepository.save(entity));
    }
}
