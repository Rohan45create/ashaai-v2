package com.ashaai.backend.controller;

import com.ashaai.backend.entity.SurveySubmission;
import com.ashaai.backend.repository.SurveySubmissionRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/surveySubmissions")
public class SurveySubmissionController {

    private final SurveySubmissionRepository surveySubmissionRepository;

    public SurveySubmissionController(SurveySubmissionRepository surveySubmissionRepository) {
        this.surveySubmissionRepository = surveySubmissionRepository;
    }

    @GetMapping
    public ResponseEntity<List<SurveySubmission>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(surveySubmissionRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<SurveySubmission> create(@RequestBody SurveySubmission entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(surveySubmissionRepository.save(entity));
    }
}
