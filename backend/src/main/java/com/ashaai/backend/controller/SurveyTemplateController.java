package com.ashaai.backend.controller;

import com.ashaai.backend.entity.SurveyTemplate;
import com.ashaai.backend.repository.SurveyTemplateRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/surveyTemplates")
public class SurveyTemplateController {

    private final SurveyTemplateRepository surveyTemplateRepository;

    public SurveyTemplateController(SurveyTemplateRepository surveyTemplateRepository) {
        this.surveyTemplateRepository = surveyTemplateRepository;
    }

    @GetMapping
    public ResponseEntity<List<SurveyTemplate>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(surveyTemplateRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SurveyTemplate> getById(@PathVariable java.util.UUID id) {
        return surveyTemplateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    
    @PostMapping
    public ResponseEntity<SurveyTemplate> create(@RequestBody SurveyTemplate entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(surveyTemplateRepository.save(entity));
    }
}
