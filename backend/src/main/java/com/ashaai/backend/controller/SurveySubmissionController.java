package com.ashaai.backend.controller;

import com.ashaai.backend.dto.SurveySubmissionDto;
import com.ashaai.backend.dto.SurveySubmissionResponseDto;
import com.ashaai.backend.entity.SurveySubmission;
import com.ashaai.backend.repository.SurveySubmissionRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import com.ashaai.backend.service.SurveyProcessorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = {"/api/surveySubmissions", "/api/survey_submissions", "/api/submissions"})
public class SurveySubmissionController {

    private final SurveySubmissionRepository surveySubmissionRepository;
    private final SurveyProcessorService surveyProcessorService;
    private final ObjectMapper objectMapper;

    public SurveySubmissionController(
            SurveySubmissionRepository surveySubmissionRepository,
            SurveyProcessorService surveyProcessorService
    ) {
        this(surveySubmissionRepository, surveyProcessorService, new ObjectMapper());
    }

    @Autowired
    public SurveySubmissionController(
            SurveySubmissionRepository surveySubmissionRepository,
            SurveyProcessorService surveyProcessorService,
            ObjectMapper objectMapper
    ) {
        this.surveySubmissionRepository = surveySubmissionRepository;
        this.surveyProcessorService = surveyProcessorService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<SurveySubmissionResponseDto>> getAll(
            @RequestParam(required = false) UUID ashaId,
            org.springframework.security.core.Authentication authentication
    ) {
        UUID effectiveAshaId = ashaId;
        if (effectiveAshaId == null && authentication instanceof AshaAuthenticationToken auth) {
            effectiveAshaId = auth.getAshaId();
        }

        List<SurveySubmission> submissions;
        if (effectiveAshaId != null) {
            submissions = surveySubmissionRepository.findByAsha_Id(effectiveAshaId);
        } else {
            submissions = surveySubmissionRepository.findAll();
        }

        List<SurveySubmissionResponseDto> dtos = submissions.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<SurveySubmissionResponseDto> getById(@PathVariable UUID id) {
        return surveySubmissionRepository.findById(id)
                .map(this::toResponseDto)
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

    private SurveySubmissionResponseDto toResponseDto(SurveySubmission sub) {
        SurveySubmissionResponseDto dto = new SurveySubmissionResponseDto();
        dto.setId(sub.getId());
        dto.setSubmittedAt(sub.getSubmittedAt());

        if (sub.getAsha() != null) {
            dto.setAshaId(sub.getAsha().getId());
        }

        if (sub.getTemplate() != null) {
            dto.setTemplateId(sub.getTemplate().getId());
            dto.setModuleType(sub.getTemplate().getModuleKey());
        }

        if (sub.getHousehold() != null) {
            dto.setHouseholdId(sub.getHousehold().getId());
            if (sub.getHousehold().getHouseNumber() != null) {
                dto.setFamilyName("House " + sub.getHousehold().getHouseNumber());
            } else if (sub.getHousehold().getAddress() != null) {
                dto.setFamilyName(sub.getHousehold().getAddress());
            }
        }

        // Parse data JSON string to map/object to extract fallback moduleKey and familyName
        if (sub.getData() != null && !sub.getData().isBlank()) {
            try {
                Map<String, Object> dataMap = objectMapper.readValue(sub.getData(), new TypeReference<>() {});
                dto.setData(dataMap);

                if (dto.getModuleType() == null) {
                    Object mk = dataMap.get("moduleKey");
                    if (mk == null) mk = dataMap.get("module_key");
                    if (mk == null) mk = dataMap.get("moduleType");
                    if (mk != null) dto.setModuleType(mk.toString());
                }

                if (dto.getFamilyName() == null) {
                    Object fn = dataMap.get("familyHeadName");
                    if (fn == null) fn = dataMap.get("headOfHousehold");
                    if (fn == null) fn = dataMap.get("mother_name");
                    if (fn == null) fn = dataMap.get("child_name");
                    if (fn != null) dto.setFamilyName(fn.toString());
                }

                if (dto.getHouseholdId() == null) {
                    Object hid = dataMap.get("householdId");
                    if (hid == null) hid = dataMap.get("household_id");
                    if (hid != null) {
                        try {
                            dto.setHouseholdId(UUID.fromString(hid.toString()));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                // If not JSON object, store as raw string
                dto.setData(sub.getData());
            }
        }

        return dto;
    }
}
