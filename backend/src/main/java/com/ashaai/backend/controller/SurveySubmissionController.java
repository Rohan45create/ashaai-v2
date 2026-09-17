package com.ashaai.backend.controller;

import com.ashaai.backend.dto.SurveySubmissionDto;
import com.ashaai.backend.entity.SurveySubmission;
import com.ashaai.backend.repository.SurveySubmissionRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import com.ashaai.backend.service.SurveyProcessorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = {"/api/surveySubmissions", "/api/survey_submissions"})
public class SurveySubmissionController {

    private final SurveySubmissionRepository surveySubmissionRepository;
    private final SurveyProcessorService surveyProcessorService;

    public SurveySubmissionController(
            SurveySubmissionRepository surveySubmissionRepository,
            SurveyProcessorService surveyProcessorService
    ) {
        this.surveySubmissionRepository = surveySubmissionRepository;
        this.surveyProcessorService = surveyProcessorService;
    }

    @GetMapping
    public ResponseEntity<List<SurveySubmission>> getAll(org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(surveySubmissionRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SurveySubmission> getById(@PathVariable UUID id) {
        return surveySubmissionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SurveySubmission> create(
            @RequestBody SurveySubmissionDto dto,
            org.springframework.security.core.Authentication authentication
    ) {
        UUID ashaId = null;
        if (authentication instanceof AshaAuthenticationToken auth) {
            ashaId = auth.getAshaId();
        }
        SurveySubmission saved = surveyProcessorService.processSurveySubmission(dto, ashaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
