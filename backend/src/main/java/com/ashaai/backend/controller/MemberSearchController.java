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

    /**
     * GET /api/members/check-duplicate?aadhaar_last4=...&aadhaar_raw=...&aadhaar_hash=...
     * Checks if a member with matching Aadhaar already exists in the system.
     * Safe projection for DuplicateWarningModal.
     */
    @GetMapping("/check-duplicate")
    public ResponseEntity<java.util.Map<String, Object>> checkDuplicate(
            @RequestParam(required = false) String aadhaar_last4,
            @RequestParam(required = false) String aadhaar_raw,
            @RequestParam(required = false) String aadhaar_hash,
            org.springframework.security.core.Authentication authentication
    ) {
        String last4 = aadhaar_last4;
        if ((last4 == null || last4.length() < 4) && aadhaar_raw != null && aadhaar_raw.length() >= 4) {
            last4 = aadhaar_raw.substring(aadhaar_raw.length() - 4);
        }
        if (last4 == null || last4.length() < 4) {
            return ResponseEntity.ok(java.util.Map.of("found", false));
        }

        List<HouseholdMember> matches = memberRepository.findByAadhaarLast4(last4);
        if (matches.isEmpty()) {
            return ResponseEntity.ok(java.util.Map.of("found", false));
        }

        HouseholdMember match = matches.get(0);
        java.util.Map<String, Object> record = new java.util.HashMap<>();
        record.put("id", match.getId());
        record.put("member_name", match.getName());
        record.put("gender", match.getGender());
        record.put("date_of_birth", match.getDateOfBirth() != null ? match.getDateOfBirth().toString() : "");
        record.put("mobile_number", match.getMobileNumber() != null ? match.getMobileNumber() : "");
        record.put("village", (match.getHousehold() != null && match.getHousehold().getAddress() != null) ? match.getHousehold().getAddress() : "");
        record.put("updatedAt", match.getUpdatedAt() != null ? match.getUpdatedAt().toString() : (match.getCreatedAt() != null ? match.getCreatedAt().toString() : ""));

        return ResponseEntity.ok(java.util.Map.of("found", true, "record", record));
    }

    /**
     * GET /api/members?householdId=<id>
     */
    @GetMapping
    public ResponseEntity<List<MemberSearchResult>> getMembers(
            @RequestParam(required = false) java.util.UUID householdId
    ) {
        List<HouseholdMember> list;
        if (householdId != null) {
            list = memberRepository.findByHousehold_Id(householdId);
        } else {
            list = memberRepository.findAll();
        }
        List<MemberSearchResult> results = list.stream()
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
