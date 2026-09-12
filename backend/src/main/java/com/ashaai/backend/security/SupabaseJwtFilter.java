package com.ashaai.backend.security;

import com.ashaai.backend.entity.Asha;
import com.ashaai.backend.entity.AshaHead;
import com.ashaai.backend.repository.AshaHeadRepository;
import com.ashaai.backend.repository.AshaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
public class SupabaseJwtFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final AshaRepository ashaRepository;
    private final AshaHeadRepository ashaHeadRepository;

    public SupabaseJwtFilter(JwtDecoder jwtDecoder, 
                             @org.springframework.context.annotation.Lazy AshaRepository ashaRepository, 
                             @org.springframework.context.annotation.Lazy AshaHeadRepository ashaHeadRepository) {
        this.jwtDecoder = jwtDecoder;
        this.ashaRepository = ashaRepository;
        this.ashaHeadRepository = ashaHeadRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        
        if (request.getRequestURI().contains("/api/vision/muac-grade") || 
            request.getRequestURI().contains("/api/register/extract") || 
            request.getRequestURI().contains("/api/voice/transcribe")) {
            System.out.println("[DEBUG-BACKEND-FILTER] Path: " + request.getRequestURI());
            System.out.println("[DEBUG-BACKEND-FILTER] Authorization Header: " + (header != null ? header.substring(0, Math.min(20, header.length())) + "..." : "null"));
        }

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Jwt jwt = jwtDecoder.decode(token);
            String sub = jwt.getSubject();
            if (sub != null) {
                UUID authUserId = UUID.fromString(sub);

                Optional<Asha> ashaOpt = ashaRepository.findByAuthUserId(authUserId);
                if (ashaOpt.isPresent()) {
                    Asha asha = ashaOpt.get();
                    AshaAuthenticationToken auth = new AshaAuthenticationToken(
                            asha, asha.getId(), asha.getHead() != null ? asha.getHead().getId() : null, authUserId, Collections.emptyList()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    filterChain.doFilter(request, response);
                    return;
                }

                Optional<AshaHead> headOpt = ashaHeadRepository.findByAuthUserId(authUserId);
                if (headOpt.isPresent()) {
                    AshaHead head = headOpt.get();
                    AshaAuthenticationToken auth = new AshaAuthenticationToken(
                            head, null, head.getId(), authUserId, Collections.emptyList()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Strict 403 fallback — JWT valid but no matching ashas/asha_heads row
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Identity resolution failed for auth user.");
                return;
            }
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid JWT or malformed UUID
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
