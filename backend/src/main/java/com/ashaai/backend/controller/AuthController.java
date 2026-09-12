package com.ashaai.backend.controller;

import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

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
