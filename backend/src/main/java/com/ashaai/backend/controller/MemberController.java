package com.ashaai.backend.controller;

import com.ashaai.backend.entity.HouseholdMember;
import com.ashaai.backend.repository.HouseholdMemberRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import com.ashaai.backend.service.LinkageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/households/{householdId}/members")
public class MemberController {

    private final HouseholdMemberRepository memberRepository;
    private final LinkageService linkageService;

    public MemberController(HouseholdMemberRepository memberRepository, LinkageService linkageService) {
        this.memberRepository = memberRepository;
        this.linkageService = linkageService;
    }

    @GetMapping
    public ResponseEntity<List<HouseholdMember>> getMembers(@PathVariable UUID householdId, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        List<HouseholdMember> all = memberRepository.findAll();
        List<HouseholdMember> householdMembers = all.stream()
                .filter(m -> m.getHousehold() != null && m.getHousehold().getId().equals(householdId))
                .collect(Collectors.toList());
        return ResponseEntity.ok(householdMembers);
    }
    
    @PostMapping
    public ResponseEntity<HouseholdMember> createMember(@PathVariable UUID householdId, @RequestBody HouseholdMember member, org.springframework.security.core.Authentication authentication) { com.ashaai.backend.security.AshaAuthenticationToken auth = (com.ashaai.backend.security.AshaAuthenticationToken) authentication;
        // If Aadhaar is not provided, generate temporary ID
        if (member.getAadhaarLast4() == null && member.getTemporaryId() == null) {
            String tempId = linkageService.generateTemporaryId("DEFAULT"); // Replace with actual district logic
            member.setTemporaryId(tempId);
            member.setIdentityStatus("temporary");
        } else if (member.getAadhaarLast4() != null) {
            member.setIdentityStatus("aadhaar_confirmed");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(memberRepository.save(member));
    }
}
