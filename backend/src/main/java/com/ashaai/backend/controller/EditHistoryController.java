package com.ashaai.backend.controller;

import com.ashaai.backend.entity.EditHistory;
import com.ashaai.backend.repository.EditHistoryRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/edithistories")
public class EditHistoryController {

    private final EditHistoryRepository editHistoryRepository;

    public EditHistoryController(EditHistoryRepository editHistoryRepository) {
        this.editHistoryRepository = editHistoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<EditHistory>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(editHistoryRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<EditHistory> create(@RequestBody EditHistory entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(editHistoryRepository.save(entity));
    }
}
