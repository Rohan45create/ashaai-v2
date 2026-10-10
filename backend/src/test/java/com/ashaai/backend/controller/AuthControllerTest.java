package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Asha;
import com.ashaai.backend.repository.AshaRepository;
import com.ashaai.backend.security.PhoneCheckRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AshaRepository ashaRepository;
    private PhoneCheckRateLimiter rateLimiter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ashaRepository = mock(AshaRepository.class);
        rateLimiter = new PhoneCheckRateLimiter();
        rateLimiter.resetForTesting();
        objectMapper = new ObjectMapper();

        AuthController controller = new AuthController(ashaRepository, rateLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("200 OK: Valid registered, active, and linked ASHA worker")
    void testCheckWorkerPhone_Success() throws Exception {
        Asha asha = new Asha();
        asha.setId(UUID.randomUUID());
        asha.setName("Lata Patil");
        asha.setPhone("+919876543211");
        asha.setAuthUserId(UUID.randomUUID());
        asha.setActive(true);

        when(ashaRepository.findByAnyPhoneFormat("+919876543211", "9876543211", "919876543211"))
                .thenReturn(Optional.of(asha));

        mockMvc.perform(post("/api/auth/check-worker-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "9876543211"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true));
    }

    @Test
    @DisplayName("404 Not Found: Unknown phone number returns WORKER_NOT_FOUND")
    void testCheckWorkerPhone_NotFound() throws Exception {
        when(ashaRepository.findByAnyPhoneFormat(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        MvcResult result = mockMvc.perform(post("/api/auth/check-worker-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "9876543211"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WORKER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No ASHA worker is registered with this number. Contact your supervisor."))
                .andReturn();

        // Ensure no internal stack trace leaked
        String responseContent = result.getResponse().getContentAsString();
        assertFalse(responseContent.contains("Exception"));
        assertFalse(responseContent.contains("com.ashaai"));
    }

    @Test
    @DisplayName("403 Forbidden: Inactive/deactivated worker returns WORKER_INACTIVE")
    void testCheckWorkerPhone_Inactive() throws Exception {
        Asha asha = new Asha();
        asha.setId(UUID.randomUUID());
        asha.setName("Deactivated Worker");
        asha.setPhone("+919876543211");
        asha.setAuthUserId(UUID.randomUUID());
        asha.setActive(false);

        when(ashaRepository.findByAnyPhoneFormat("+919876543211", "9876543211", "919876543211"))
                .thenReturn(Optional.of(asha));

        mockMvc.perform(post("/api/auth/check-worker-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "+919876543211"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("WORKER_INACTIVE"))
                .andExpect(jsonPath("$.message").value("This ASHA worker account is deactivated. Contact your supervisor."));
    }

    @Test
    @DisplayName("409 Conflict: Worker found but auth_user_id is null returns WORKER_NOT_LINKED")
    void testCheckWorkerPhone_Unlinked() throws Exception {
        Asha asha = new Asha();
        asha.setId(UUID.randomUUID());
        asha.setName("Unlinked Worker");
        asha.setPhone("+919876543211");
        asha.setAuthUserId(null); // Not linked yet
        asha.setActive(true);

        when(ashaRepository.findByAnyPhoneFormat("+919876543211", "9876543211", "919876543211"))
                .thenReturn(Optional.of(asha));

        mockMvc.perform(post("/api/auth/check-worker-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "+919876543211"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("WORKER_NOT_LINKED"))
                .andExpect(jsonPath("$.message").value("Your account is not set up for login yet. Contact your supervisor."));
    }

    @Test
    @DisplayName("400 Bad Request: Malformed or invalid phone returns INVALID_PHONE")
    void testCheckWorkerPhone_InvalidPhone() throws Exception {
        mockMvc.perform(post("/api/auth/check-worker-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "12345"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PHONE"));

        mockMvc.perform(post("/api/auth/check-worker-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "abcdefghij"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PHONE"));
    }

    @Test
    @DisplayName("429 Too Many Requests: Rate limiting triggers when phone attempts exceed limit")
    void testCheckWorkerPhone_RateLimiting() throws Exception {
        when(ashaRepository.findByAnyPhoneFormat(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Max 3 requests per phone per minute
        for (int i = 0; i < PhoneCheckRateLimiter.PHONE_LIMIT; i++) {
            mockMvc.perform(post("/api/auth/check-worker-phone")
                            .header("X-Forwarded-For", "192.168.1." + i) // distinct IPs to test phone limit
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("phone", "9876543211"))))
                    .andExpect(status().isNotFound());
        }

        // 4th request on same phone must trigger 429
        mockMvc.perform(post("/api/auth/check-worker-phone")
                        .header("X-Forwarded-For", "192.168.1.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "9876543211"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }
}
