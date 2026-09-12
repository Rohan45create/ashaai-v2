package com.ashaai.backend.controller;

import com.ashaai.backend.entity.NcdRecord;
import com.ashaai.backend.repository.NcdRecordRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ncdRecords")
public class NcdRecordController {

    private final NcdRecordRepository ncdRecordRepository;

    public NcdRecordController(NcdRecordRepository ncdRecordRepository) {
        this.ncdRecordRepository = ncdRecordRepository;
    }

    @GetMapping
    public ResponseEntity<List<NcdRecord>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(ncdRecordRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<NcdRecord> create(@RequestBody NcdRecord entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(ncdRecordRepository.save(entity));
    }
}
