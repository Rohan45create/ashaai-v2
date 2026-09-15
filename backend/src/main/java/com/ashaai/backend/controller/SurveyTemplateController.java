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
import java.util.Map;
import java.util.UUID;

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

    /**
     * PATCH /api/surveyTemplates/{id}
     * Accepts a partial update map. Supported keys:
     *   - isActive (Boolean)
     *   - icon (String)
     *   - isPublished (Boolean)
     *   - nameEn, nameMr, nameHi (String)
     *   - fields (String JSON)
     */
    @PatchMapping("/{id}")
    public ResponseEntity<SurveyTemplate> patch(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> updates,
            org.springframework.security.core.Authentication authentication) {
        return surveyTemplateRepository.findById(id).map(t -> {
            if (updates.containsKey("isActive")) {
                t.setIsActive(Boolean.parseBoolean(updates.get("isActive").toString()));
            }
            if (updates.containsKey("icon")) {
                t.setIcon((String) updates.get("icon"));
            }
            if (updates.containsKey("isPublished")) {
                t.setIsPublished(Boolean.parseBoolean(updates.get("isPublished").toString()));
            }
            if (updates.containsKey("nameEn")) t.setNameEn((String) updates.get("nameEn"));
            if (updates.containsKey("nameMr")) t.setNameMr((String) updates.get("nameMr"));
            if (updates.containsKey("nameHi")) t.setNameHi((String) updates.get("nameHi"));
            if (updates.containsKey("fields")) t.setFields((String) updates.get("fields"));
            return ResponseEntity.ok(surveyTemplateRepository.save(t));
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
