package com.ashaai.backend.controller;

import com.ashaai.backend.dto.SurveySubmissionDto;
import com.ashaai.backend.entity.SurveySubmission;
import com.ashaai.backend.repository.SurveySubmissionRepository;
import com.ashaai.backend.service.SurveyProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SurveySubmissionControllerTest {

    private MockMvc mockMvc;
    private SurveySubmissionRepository submissionRepository;
    private SurveyProcessorService processorService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        submissionRepository = mock(SurveySubmissionRepository.class);
        processorService = mock(SurveyProcessorService.class);
        objectMapper = new ObjectMapper();

        SurveySubmissionController controller = new SurveySubmissionController(submissionRepository, processorService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/surveySubmissions delegates to SurveyProcessorService and returns CREATED")
    void testCreateSubmission() throws Exception {
        UUID submissionId = UUID.randomUUID();
        SurveySubmission saved = new SurveySubmission();
        saved.setId(submissionId);
        saved.setData("{\"referred_to_nrc\": true, \"malnutritionGrade\": \"RED\"}");

        when(processorService.processSurveySubmission(any(SurveySubmissionDto.class), any())).thenReturn(saved);

        Map<String, Object> data = new HashMap<>();
        data.put("weight_kg", 5.4);
        data.put("height_cm", 72.0);
        data.put("muac_cm", 11.0);
        data.put("malnutritionGrade", "RED");
        data.put("referred_to_nrc", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("moduleKey", "child_growth");
        payload.put("data", data);
        payload.put("referredToNrc", true);

        mockMvc.perform(post("/api/surveySubmissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(submissionId.toString()));

        verify(processorService, times(1)).processSurveySubmission(any(SurveySubmissionDto.class), any());
    }

    @Test
    @DisplayName("GET /api/surveySubmissions returns all submissions")
    void testGetAllSubmissions() throws Exception {
        SurveySubmission s = new SurveySubmission();
        s.setId(UUID.randomUUID());
        when(submissionRepository.findAll()).thenReturn(List.of(s));

        mockMvc.perform(get("/api/surveySubmissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(submissionRepository, times(1)).findAll();
    }
}
