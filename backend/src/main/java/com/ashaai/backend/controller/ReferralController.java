package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Referral;
import com.ashaai.backend.repository.ReferralRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/referrals")
public class ReferralController {

    private final ReferralRepository referralRepository;

    public ReferralController(ReferralRepository referralRepository) {
        this.referralRepository = referralRepository;
    }

    @GetMapping
    public ResponseEntity<List<Referral>> getAll(org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.ok(referralRepository.findAll());
    }
    
    @PostMapping
    public ResponseEntity<Referral> create(@RequestBody Referral entity, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        return ResponseEntity.status(HttpStatus.CREATED).body(referralRepository.save(entity));
    }
}
