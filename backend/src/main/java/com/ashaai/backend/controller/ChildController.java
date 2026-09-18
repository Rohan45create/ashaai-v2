package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Child;
import com.ashaai.backend.repository.ChildRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/children")
public class ChildController {

    private final ChildRepository childRepository;

    public ChildController(ChildRepository childRepository) {
        this.childRepository = childRepository;
    }

    @GetMapping
    public ResponseEntity<List<Child>> getChildren(Authentication authentication) {
        AshaAuthenticationToken auth = (AshaAuthenticationToken) authentication;
        List<Child> all = childRepository.findAll();
        if (auth.getAshaId() != null) {
            return ResponseEntity.ok(all.stream()
                    .filter(c -> c.getAsha() != null && c.getAsha().getId().equals(auth.getAshaId()))
                    .collect(Collectors.toList()));
        } else if (auth.getAshaHeadId() != null) {
            return ResponseEntity.ok(all.stream()
                    .filter(c -> c.getAsha() != null && c.getAsha().getHead() != null && c.getAsha().getHead().getId().equals(auth.getAshaHeadId()))
                    .collect(Collectors.toList()));
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Child> getChildById(@PathVariable UUID id, Authentication authentication) {
        return childRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Child> createChild(@RequestBody Child child, Authentication authentication) {
        AshaAuthenticationToken auth = (AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(childRepository.save(child));
    }
}
