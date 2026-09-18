package com.ashaai.backend.controller;

import com.ashaai.backend.entity.Asha;
import com.ashaai.backend.entity.Child;
import com.ashaai.backend.entity.HouseholdMember;
import com.ashaai.backend.entity.Referral;
import com.ashaai.backend.repository.AshaRepository;
import com.ashaai.backend.repository.ChildRepository;
import com.ashaai.backend.repository.HouseholdMemberRepository;
import com.ashaai.backend.repository.ReferralRepository;
import com.ashaai.backend.security.AshaAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/referrals")
public class ReferralController {

    private final ReferralRepository referralRepository;
    private final ChildRepository childRepository;
    private final AshaRepository ashaRepository;
    private final HouseholdMemberRepository householdMemberRepository;

    public ReferralController(ReferralRepository referralRepository,
                              ChildRepository childRepository,
                              AshaRepository ashaRepository,
                              HouseholdMemberRepository householdMemberRepository) {
        this.referralRepository = referralRepository;
        this.childRepository = childRepository;
        this.ashaRepository = ashaRepository;
        this.householdMemberRepository = householdMemberRepository;
    }

    @GetMapping
    public ResponseEntity<List<Referral>> getAll(Authentication authentication) {
        AshaAuthenticationToken auth = (AshaAuthenticationToken) authentication;
        if (auth.getAshaHeadId() != null) {
            return ResponseEntity.ok(referralRepository.findByAsha_Head_Id(auth.getAshaHeadId()));
        } else if (auth.getAshaId() != null) {
            return ResponseEntity.ok(referralRepository.findByAsha_Id(auth.getAshaId()));
        }
        return ResponseEntity.ok(referralRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Referral> getById(@PathVariable UUID id) {
        return referralRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Referral entity, Authentication authentication) {
        AshaAuthenticationToken auth = (AshaAuthenticationToken) authentication;

        // Resolve child if provided
        Child child = null;
        if (entity.getChild() != null && entity.getChild().getId() != null) {
            child = childRepository.findById(entity.getChild().getId()).orElse(null);
            entity.setChild(child);
        }

        // Resolve household member
        HouseholdMember member = entity.getHouseholdMember();
        if (member != null && member.getId() != null) {
            member = householdMemberRepository.findById(member.getId()).orElse(member);
            entity.setHouseholdMember(member);
        } else if (child != null && child.getHouseholdMember() != null) {
            entity.setHouseholdMember(child.getHouseholdMember());
        }

        // Resolve Asha
        Asha asha = entity.getAsha();
        if (asha == null || asha.getId() == null) {
            if (auth.getAshaId() != null) {
                asha = ashaRepository.findById(auth.getAshaId()).orElse(null);
            } else if (child != null && child.getAsha() != null) {
                asha = child.getAsha();
            }
        } else {
            asha = ashaRepository.findById(asha.getId()).orElse(asha);
        }
        entity.setAsha(asha);

        if (entity.getAsha() == null) {
            return ResponseEntity.badRequest().body("asha_id is required");
        }
        if (entity.getChild() == null && entity.getHouseholdMember() == null) {
            return ResponseEntity.badRequest().body("Either child_id or household_member_id is required");
        }

        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("pending");
        } else {
            entity.setStatus(entity.getStatus().toLowerCase());
        }
        if (entity.getReferredDate() == null) {
            entity.setReferredDate(OffsetDateTime.now());
        }

        Referral saved = referralRepository.save(entity);

        // Keep children.nrc_referral_status in sync
        if (child != null) {
            child.setNrcReferralStatus(saved.getStatus());
            childRepository.save(child);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Referral updateData, Authentication authentication) {
        Referral referral = referralRepository.findById(id).orElse(null);
        if (referral == null) {
            return ResponseEntity.notFound().build();
        }

        if (updateData.getStatus() != null && !updateData.getStatus().isBlank()) {
            String newStatus = updateData.getStatus().toLowerCase();
            referral.setStatus(newStatus);
            if ("admitted".equals(newStatus) && referral.getAdmittedDate() == null) {
                referral.setAdmittedDate(LocalDate.now());
            } else if ("discharged".equals(newStatus) && referral.getDischargedDate() == null) {
                referral.setDischargedDate(LocalDate.now());
            }
        }

        if (updateData.getNrcName() != null) {
            referral.setNrcName(updateData.getNrcName());
        }
        if (updateData.getAdmittedDate() != null) {
            referral.setAdmittedDate(updateData.getAdmittedDate());
        }
        if (updateData.getDischargedDate() != null) {
            referral.setDischargedDate(updateData.getDischargedDate());
        }
        if (updateData.getFollowUpDueDate() != null) {
            referral.setFollowUpDueDate(updateData.getFollowUpDueDate());
        }

        Referral saved = referralRepository.save(referral);

        // Keep children.nrc_referral_status in sync
        Child child = referral.getChild();
        if (child != null) {
            child.setNrcReferralStatus(saved.getStatus());
            childRepository.save(child);
        }

        return ResponseEntity.ok(saved);
    }
}
