package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Child;
import com.ashaai.backend.repository.ChildRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/children")
public class ChildController {

    private final ChildRepository childRepository;

    public ChildController(ChildRepository childRepository) {
        this.childRepository = childRepository;
    }

    @GetMapping
    public ResponseEntity<List<Child>> getChildren(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        List<Child> all = childRepository.findAll();
        List<Child> ashasChildren = all.stream()
                .filter(c -> c.getAsha() != null && c.getAsha().getId().equals(auth.getAshaId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ashasChildren);
    }
    
    @PostMapping
    public ResponseEntity<Child> createChild(@RequestBody Child child, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(childRepository.save(child));
    }
}
