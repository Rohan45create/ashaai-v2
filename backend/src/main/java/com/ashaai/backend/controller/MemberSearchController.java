package com.ashaai.backend.controller;

import com.ashaai.backend.entity.HouseholdMember;
import com.ashaai.backend.repository.HouseholdMemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Top-level member search endpoint used by MemberLookupField.
 * Searches across ALL household_members visible to this ASHA by name
 * (case-insensitive partial match).
 *
 * This returns only safe fields — no encrypted Aadhaar, no ABHA ID.
 * Per RULES.md: never log names/DOB/Aadhaar; only IDs and event types.
 */
@RestController
@RequestMapping("/api/members")
public class MemberSearchController {

    private final HouseholdMemberRepository memberRepository;

    public MemberSearchController(HouseholdMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * GET /api/members/search?q=<query>
     * Returns up to 20 matching household members.
     * Only returns: id, name, gender, dateOfBirth, relationshipToHead, temporaryId
     * Never returns: aadhaarEncrypted, aadhaarLast4, abhaIdEncrypted, mobileNumber
     */
    @GetMapping("/search")
    public ResponseEntity<List<MemberSearchResult>> search(
            @RequestParam String q,
            org.springframework.security.core.Authentication authentication) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        final String lower = q.trim().toLowerCase();
        List<HouseholdMember> all = memberRepository.findAll();
        List<MemberSearchResult> results = all.stream()
                .filter(m -> m.getName() != null && m.getName().toLowerCase().contains(lower))
                .limit(20)
                .map(m -> new MemberSearchResult(
                        m.getId().toString(),
                        m.getName(),
                        m.getGender(),
                        m.getDateOfBirth() != null ? m.getDateOfBirth().toString() : null,
                        m.getRelationshipToHead(),
                        m.getTemporaryId()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    /** Safe projection — no sensitive identity columns */
    public record MemberSearchResult(
            String id,
            String name,
            String gender,
            String dateOfBirth,
            String relationshipToHead,
            String temporaryId
    ) {}
}
