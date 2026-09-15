package com.ashaai.backend.controller;

import com.ashaai.backend.entity.*;
import com.ashaai.backend.repository.*;
import com.ashaai.backend.service.AiIntegrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AiController {

    private static final Logger logger = LoggerFactory.getLogger(AiController.class);
    private final AiIntegrationService aiIntegrationService;
    private final HouseholdMemberRepository householdMemberRepository;
    private final HouseholdRepository householdRepository;
    private final VaccinationRepository vaccinationRepository;
    private final PendingReviewRepository pendingReviewRepository;
    private final SurveyTemplateRepository surveyTemplateRepository;
    private final AshaHeadRepository ashaHeadRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiController(
            AiIntegrationService aiIntegrationService,
            HouseholdMemberRepository householdMemberRepository,
            HouseholdRepository householdRepository,
            VaccinationRepository vaccinationRepository,
            PendingReviewRepository pendingReviewRepository,
            SurveyTemplateRepository surveyTemplateRepository,
            AshaHeadRepository ashaHeadRepository) {
        this.aiIntegrationService = aiIntegrationService;
        this.householdMemberRepository = householdMemberRepository;
        this.householdRepository = householdRepository;
        this.vaccinationRepository = vaccinationRepository;
        this.pendingReviewRepository = pendingReviewRepository;
        this.surveyTemplateRepository = surveyTemplateRepository;
        this.ashaHeadRepository = ashaHeadRepository;
    }

    // Voice Dictation (Section 2.4)
    @PostMapping(value = {"/api/ai/voice/transcribe", "/api/voice/transcribe"}, consumes = "multipart/form-data")
    public ResponseEntity<String> processVoice(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "module_type", required = false) String moduleType,
            @RequestParam(value = "form_fields", required = false) String formFields) {

        String resolvedFormFields = formFields;
        if ((resolvedFormFields == null || resolvedFormFields.isBlank()) && moduleType != null && !moduleType.isBlank()) {
            java.util.Optional<SurveyTemplate> templateOpt;
            try {
                // If moduleType is a UUID, it's a dynamic survey ID
                UUID id = UUID.fromString(moduleType);
                templateOpt = surveyTemplateRepository.findById(id);
            } catch (IllegalArgumentException e) {
                // Otherwise it's a built-in module key
                templateOpt = surveyTemplateRepository.findByModuleKey(moduleType);
            }
            if (templateOpt.isPresent()) {
                resolvedFormFields = templateOpt.get().getFields();
            }
        }

        return ResponseEntity.ok(aiIntegrationService.extractVoiceData(audio, moduleType, resolvedFormFields));
    }

    // Malnutrition Photo Grading (Section 2.3)
    @PostMapping(value = {"/api/ai/vision/muac", "/api/vision/muac-grade"}, consumes = "multipart/form-data")
    public ResponseEntity<String> processMuacPhoto(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "age", required = false) String age,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "height", required = false) String height,
            @RequestParam(value = "weight", required = false) String weight) {
        MultipartFile file = image != null ? image : photo;
        if (file == null) {
            return ResponseEntity.badRequest().body("{\"error\":\"IMAGE_REQUIRED\",\"message\":\"Image or photo file must be provided\"}");
        }
        return ResponseEntity.ok(aiIntegrationService.gradeMuacPhoto(file, age, gender, height, weight));
    }

    // Register OCR Extraction (Section 2.6)
    @PostMapping(value = {"/api/ai/vision/register-ocr", "/api/register/extract"}, consumes = "multipart/form-data")
    public ResponseEntity<String> processRegisterOcr(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "register_type", required = false) String registerType) {
        MultipartFile file = image != null ? image : photo;
        if (file == null) {
            return ResponseEntity.badRequest().body("{\"error\":\"IMAGE_REQUIRED\",\"message\":\"Image or photo file must be provided\"}");
        }
        return ResponseEntity.ok(aiIntegrationService.extractRegisterOcr(file, registerType));
    }

    // Register Batch Import to Postgres (Single Write Path)
    @PostMapping(value = {"/api/register/import-batch", "/api/ai/register/import-batch"})
    public ResponseEntity<Map<String, Object>> importBatch(@RequestBody Map<String, Object> payload) {
        String targetCollection = (String) payload.getOrDefault("targetCollection", "household_members");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");

        int savedCount = 0;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                try {
                    if ("household_members".equalsIgnoreCase(targetCollection)) {
                        HouseholdMember member = new HouseholdMember();
                        member.setName((String) row.getOrDefault("name", "Unknown"));
                        member.setGender((String) row.getOrDefault("gender", "Female"));
                        member.setRelationshipToHead((String) row.getOrDefault("relation", "Member"));
                        member.setTemporaryId("TMP-OCR-" + System.currentTimeMillis() + "-" + savedCount);
                        member.setIdentityStatus("temporary");

                        List<Household> households = householdRepository.findAll();
                        if (!households.isEmpty()) {
                            member.setHousehold(households.get(0));
                            householdMemberRepository.save(member);
                            savedCount++;
                        }
                    } else if ("vaccinations".equalsIgnoreCase(targetCollection)) {
                        Vaccination v = new Vaccination();
                        v.setVaccineName((String) row.getOrDefault("vaccine_name", (String) row.getOrDefault("vaccineName", "BCG")));
                        v.setBatch((String) row.getOrDefault("batch", "OCR-BATCH"));
                        vaccinationRepository.save(v);
                        savedCount++;
                    } else {
                        savedCount++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to persist OCR row: {}", e.getMessage());
                }
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("status", "success");
        res.put("saved_count", savedCount);
        res.put("target_collection", targetCollection);
        return ResponseEntity.ok(res);
    }

    // Ask AshaAI Chatbot (Section 2.7)
    @PostMapping(value = {"/api/chat", "/api/ai/chat"})
    public ResponseEntity<String> chatWithAi(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(aiIntegrationService.chatWithAi(payload));
    }

    // Multilingual Survey Translation (Section 2.13)
    @PostMapping(value = {"/api/admin/translate", "/api/ai/translate"})
    public ResponseEntity<String> translateSurveyText(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(aiIntegrationService.translateSurveyText(payload));
    }

    // Smart Validation Layer 2 Cross-field Validation (Section 2.9)
    @PostMapping(value = {"/api/validation/cross-field", "/api/ai/validation/cross-field"})
    public ResponseEntity<String> validateCrossField(@RequestBody Map<String, Object> payload) {
        String resultJson = aiIntegrationService.validateCrossField(payload);
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            if (root.has("has_conflict") && root.get("has_conflict").asBoolean()) {
                String entityType = (String) payload.getOrDefault("entity_type", "record");
                StringBuilder reason = new StringBuilder("Cross-field conflict: ");
                if (root.has("conflicts") && root.get("conflicts").isArray()) {
                    for (JsonNode c : root.get("conflicts")) {
                        reason.append(c.asText()).append("; ");
                    }
                }

                PendingReview review = new PendingReview();
                review.setTableName(entityType);
                review.setRecordId(UUID.randomUUID());
                review.setReason(reason.toString());
                review.setStatus("PENDING_CONFIRMATION");
                pendingReviewRepository.save(review);
                logger.info("event=cross_field_conflict_flagged table={} reason={}", entityType, reason);
            }
        } catch (Exception e) {
            logger.warn("Could not parse cross-field validation output: {}", e.getMessage());
        }

        return ResponseEntity.ok(resultJson);
    }

    // Multilingual Survey Publishing (Section 2.13)
    @PostMapping(value = {"/api/admin/supervisor/surveys/publish", "/api/surveys/publish"})
    public ResponseEntity<Map<String, Object>> publishSurvey(@RequestBody Map<String, Object> payload) {
        String titleEn = (String) payload.getOrDefault("title", "Health Survey");
        String titleMr = (String) payload.getOrDefault("title_mr", titleEn);
        String titleHi = (String) payload.getOrDefault("title_hi", titleEn);
        Object fieldsObj = payload.get("fields");

        SurveyTemplate template = new SurveyTemplate();
        template.setNameEn(titleEn);
        template.setNameMr(titleMr);
        template.setNameHi(titleHi);
        template.setIsPublished(true);

        try {
            template.setFields(objectMapper.writeValueAsString(fieldsObj));
        } catch (Exception e) {
            template.setFields("[]");
        }

        List<AshaHead> heads = ashaHeadRepository.findAll();
        if (!heads.isEmpty()) {
            template.setCreatedBy(heads.get(0));
            SurveyTemplate saved = surveyTemplateRepository.save(template);
            Map<String, Object> res = new HashMap<>();
            res.put("status", "success");
            res.put("id", saved.getId().toString());
            return ResponseEntity.ok(res);
        } else {
            Map<String, Object> res = new HashMap<>();
            res.put("status", "error");
            res.put("message", "No supervisor account found");
            return ResponseEntity.badRequest().body(res);
        }
    }

    // Conversational Agent (Section 2.15)
    @PostMapping(value = {"/api/agent/classify-intent", "/api/ai/agent/classify-intent"})
    public ResponseEntity<String> classifyAgentIntent(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(aiIntegrationService.classifyAgentIntent(payload));
    }

    @PostMapping(value = {"/api/agent/turn", "/api/ai/agent/turn"})
    public ResponseEntity<String> handleAgentTurn(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(aiIntegrationService.handleAgentTurn(payload));
    }

    @PostMapping(value = {"/api/agent/tools/execute", "/api/ai/agent/tools/execute"})
    public ResponseEntity<String> executeAgentTool(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(aiIntegrationService.executeAgentTool(payload));
    }

    @org.springframework.web.bind.annotation.GetMapping(value = {"/api/agent/session/{sessionId}/draft", "/api/ai/agent/session/{sessionId}/draft"})
    public ResponseEntity<String> getAgentSessionDraft(@org.springframework.web.bind.annotation.PathVariable String sessionId) {
        return ResponseEntity.ok(aiIntegrationService.getAgentSessionDraft(sessionId));
    }
}
