package com.ashaai.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;

@Service
public class AiIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(AiIntegrationService.class);
    private final RestClient restClient;

    public AiIntegrationService(
            RestClient.Builder restClientBuilder,
            @Value("${app.ai-service.url}") String aiServiceUrl) {
        
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
                
        this.restClient = restClientBuilder
                .baseUrl(aiServiceUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    public String extractVoiceData(MultipartFile audioFile, String moduleType, String formFields) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("audio", new org.springframework.core.io.ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return audioFile.getOriginalFilename() != null ? audioFile.getOriginalFilename() : "audio.webm";
                }
            });
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read audio file", e);
        }
        
        if (formFields != null && !formFields.isBlank()) {
            builder.part("form_fields", formFields);
        }

        return restClient.post()
                .uri("/api/ai/voice/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(String.class);
    }

    public String gradeMuacPhoto(MultipartFile imageFile, String age, String gender, String height, String weight) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("image", new org.springframework.core.io.ByteArrayResource(imageFile.getBytes()) {
                @Override
                public String getFilename() {
                    return imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename() : "image.jpg";
                }
            });
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read image file", e);
        }

        if (age != null && !age.isBlank()) builder.part("age", age);
        if (gender != null && !gender.isBlank()) builder.part("gender", gender);
        if (height != null && !height.isBlank()) builder.part("height", height);
        if (weight != null && !weight.isBlank()) builder.part("weight", weight);

        return restClient.post()
                .uri("/api/ai/vision/muac-grade")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(String.class);
    }

    public String extractRegisterOcr(MultipartFile imageFile, String registerType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("image", new org.springframework.core.io.ByteArrayResource(imageFile.getBytes()) {
                @Override
                public String getFilename() {
                    return imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename() : "image.jpg";
                }
            });
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read image file", e);
        }
        if (registerType != null && !registerType.isBlank()) {
            builder.part("register_type", registerType);
        }

        return restClient.post()
                .uri("/api/ai/register/extract")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(String.class);
    }

    public String chatWithAi(Map<String, Object> payload) {
        return restClient.post()
                .uri("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    public String validateCrossField(Map<String, Object> payload) {
        return restClient.post()
                .uri("/api/ai/validation/cross-field")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    public String translateSurveyText(Map<String, Object> payload) {
        return restClient.post()
                .uri("/api/ai/translate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    // Conversational Agent (Section 2.15)
    public String classifyAgentIntent(Map<String, Object> payload) {
        return restClient.post()
                .uri("/api/ai/agent/classify-intent")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    public String handleAgentTurn(Map<String, Object> payload) {
        return restClient.post()
                .uri("/api/ai/agent/turn")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    public String executeAgentTool(Map<String, Object> payload) {
        return restClient.post()
                .uri("/api/ai/agent/tools/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    public String getAgentSessionDraft(String sessionId) {
        return restClient.get()
                .uri("/api/ai/agent/session/{sessionId}/draft", sessionId)
                .retrieve()
                .body(String.class);
    }
}
