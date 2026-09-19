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
    
    @GetMapping("/{id}")
    public ResponseEntity<java.util.Map<String, Object>> getById(@PathVariable java.util.UUID id, org.springframework.security.core.Authentication authentication) {
        return ashaRepository.findById(id)
            .map(asha -> {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", asha.getId().toString());
                map.put("name", asha.getName());
                map.put("phone", asha.getPhone());
                map.put("village", asha.getVillage());
                map.put("district", asha.getDistrict() != null ? asha.getDistrict() : "Beed");
                map.put("phc", (asha.getVillage() != null ? asha.getVillage() : "Shirur") + " PHC");
                if (asha.getHead() != null) {
                    map.put("headId", asha.getHead().getId().toString());
                    map.put("headName", asha.getHead().getName());
                }
                return ResponseEntity.ok(map);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Asha> create(@RequestBody Asha entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(ashaRepository.save(entity));
    }
}
