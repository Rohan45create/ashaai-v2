package com.ashaai.backend.controller;

import com.ashaai.backend.dto.WorkerPhoneCheckRequest;
import com.ashaai.backend.entity.Asha;
import com.ashaai.backend.repository.AshaRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import com.ashaai.backend.security.PhoneCheckRateLimiter;
import com.ashaai.backend.util.PhoneUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AshaRepository ashaRepository;
    private final PhoneCheckRateLimiter phoneCheckRateLimiter;

    public AuthController(AshaRepository ashaRepository, PhoneCheckRateLimiter phoneCheckRateLimiter) {
        this.ashaRepository = ashaRepository;
        this.phoneCheckRateLimiter = phoneCheckRateLimiter;
    }

    /**
     * Public pre-check endpoint for ASHA OTP login.
     * Prevents OTP issuance and SMS costs for unregistered or unauthorized numbers.
     * Rate-limited per IP and per normalized phone to mitigate user enumeration.
     */
    @PostMapping("/auth/check-worker-phone")
    public ResponseEntity<Map<String, Object>> checkWorkerPhone(
            @Valid @RequestBody WorkerPhoneCheckRequest req,
            HttpServletRequest request) {

        String rawPhone = req != null ? req.getPhone() : null;
        String normalized = PhoneUtils.normalizeIndianPhone(rawPhone);

        if (normalized == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "INVALID_PHONE",
                "message", "Invalid phone number format. Please provide a valid 10-digit Indian mobile number."
            ));
        }

        String clientIp = getClientIp(request);
        String masked = PhoneUtils.maskPhone(normalized);

        // Rate limit: 5 req/min per IP, 3 req/min per phone
        if (!phoneCheckRateLimiter.tryAcquireIp(clientIp) || !phoneCheckRateLimiter.tryAcquirePhone(normalized)) {
            logger.warn("worker_phone_check_rate_limit: ip={}, phone={}", clientIp, masked);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "RATE_LIMIT_EXCEEDED",
                "message", "Too many requests. Please wait a moment."
            ));
        }

        String tenDigits = PhoneUtils.extractTenDigits(normalized);
        String twelveDigits = PhoneUtils.extractTwelveDigits(normalized);

        Optional<Asha> ashaOpt = ashaRepository.findByAnyPhoneFormat(normalized, tenDigits, twelveDigits);

        if (ashaOpt.isEmpty()) {
            logger.info("worker_phone_check_not_found: phone={}", masked);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "WORKER_NOT_FOUND",
                "message", "No ASHA worker is registered with this number. Contact your supervisor."
            ));
        }

        Asha asha = ashaOpt.get();

        if (!asha.isActive()) {
            logger.warn("worker_phone_check_inactive: asha_id={}, phone={}", asha.getId(), masked);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "WORKER_INACTIVE",
                "message", "This ASHA worker account is deactivated. Contact your supervisor."
            ));
        }

        if (asha.getAuthUserId() == null) {
            logger.warn("worker_phone_check_not_linked: asha_id={}, phone={}", asha.getId(), masked);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "WORKER_NOT_LINKED",
                "message", "Your account is not set up for login yet. Contact your supervisor."
            ));
        }

        logger.info("worker_phone_check_ok: asha_id={}, phone={}", asha.getId(), masked);
        return ResponseEntity.ok(Map.of("registered", true));
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    /**
     * Resolves the authenticated caller's identity from the SecurityContext.
     *
     * IMPORTANT: We inject Authentication (the full token), NOT @AuthenticationPrincipal.
     * SupabaseJwtFilter stores the Asha/AshaHead ENTITY as the token's principal argument,
     * so getPrincipal() returns the entity — not the AshaAuthenticationToken itself.
     * Checking `authentication instanceof AshaAuthenticationToken` targets the wrapper,
     * which is always correct regardless of what principal holds internally.
     *
     * Using @AuthenticationPrincipal Object principal would instead check whether the
     * entity (the principal field inside the token) is an AshaAuthenticationToken —
     * which is structurally always false, causing a permanent 403.
     */
    @PostMapping({"/admin/auth/resolve-identity", "/ashas/auth/resolve-identity"})
    public ResponseEntity<Map<String, String>> resolveIdentity(Authentication authentication) {
        if (!(authentication instanceof AshaAuthenticationToken token)) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "IDENTITY_RESOLUTION_FAILED",
                "message", "No matching identity for this token"
            ));
        }

        if (token.getAshaId() != null) {
            return ResponseEntity.ok(Map.of(
                "doc_id", token.getAshaId().toString(),
                "role", "asha_worker"
            ));
        } else if (token.getAshaHeadId() != null) {
            return ResponseEntity.ok(Map.of(
                "doc_id", token.getAshaHeadId().toString(),
                "role", "asha_head"
            ));
        }

        // JWT valid, filter ran, but neither ashaId nor ashaHeadId populated — fail loudly per RULES.md
        return ResponseEntity.status(403).body(Map.of(
            "error", "IDENTITY_RESOLUTION_FAILED",
            "message", "Token resolved but no ASHA or head profile found"
        ));
    }
}
