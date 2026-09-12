package com.ashaai.backend.service;

import com.ashaai.backend.dto.PriorityItemDto;
import com.ashaai.backend.entity.*;
import com.ashaai.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriorityListServiceTest {

    private ChildRepository childRepository;
    private PregnancyRepository pregnancyRepository;
    private VaccinationRepository vaccinationRepository;
    private ReferralRepository referralRepository;
    private PendingReviewRepository pendingReviewRepository;
    private RiskEngineService riskEngineService;

    private PriorityListService priorityListService;

    @BeforeEach
    void setUp() {
        childRepository = mock(ChildRepository.class);
        pregnancyRepository = mock(PregnancyRepository.class);
        vaccinationRepository = mock(VaccinationRepository.class);
        referralRepository = mock(ReferralRepository.class);
        pendingReviewRepository = mock(PendingReviewRepository.class);
        riskEngineService = mock(RiskEngineService.class);

        priorityListService = new PriorityListService(
            childRepository,
            pregnancyRepository,
            vaccinationRepository,
            referralRepository,
            pendingReviewRepository,
            riskEngineService
        );
    }

    @Test
    @DisplayName("Priority List Merge: 3 lists merged, deduplicated by person, and sorted CRITICAL -> HIGH -> overdue-by-most-days -> MEDIUM")
    void test3ListMergeAndFinalSort() {
        UUID ashaId = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        // ── Person A: Child with MEDIUM Risk Score (List 1) ──
        UUID personAId = UUID.randomUUID();
        HouseholdMember memberA = new HouseholdMember();
        memberA.setId(personAId);
        memberA.setName("Child Aarav");

        Child childA = new Child();
        childA.setId(UUID.randomUUID());
        childA.setHouseholdMember(memberA);
        childA.setRiskScore(40);
        childA.setRiskLevel("MEDIUM");
        childA.setRiskPrimaryDriver("Moderate Acute Malnutrition (MAM) detected");
        childA.setLastVisitDate(now.minusDays(10));

        // ── Person B: Mother with Overdue ANC Visit (List 2) — overdue by 45 days (HIGH) ──
        UUID personBId = UUID.randomUUID();
        HouseholdMember memberB = new HouseholdMember();
        memberB.setId(personBId);
        memberB.setName("Mother Sunita");

        Pregnancy pregnancyB = new Pregnancy();
        pregnancyB.setId(UUID.randomUUID());
        pregnancyB.setMotherMember(memberB);
        pregnancyB.setLmp(now.minusDays(84 + 45)); // ANC1 due 84 days after LMP -> 45 days overdue

        // ── Person C: Mother with Confirmed Supervisor Flag (List 3) — CRITICAL ──
        UUID personCId = UUID.randomUUID();
        HouseholdMember memberC = new HouseholdMember();
        memberC.setId(personCId);
        memberC.setName("Mother Meena");

        Pregnancy pregnancyC = new Pregnancy();
        pregnancyC.setId(UUID.randomUUID());
        pregnancyC.setMotherMember(memberC);
        pregnancyC.setHighRiskFlag(true);
        pregnancyC.setHighRiskReasons(List.of("Severe anaemia (<8.0 g/dL)", "Previous C-Section"));

        PendingReview reviewC = new PendingReview();
        reviewC.setTableName("pregnancies");
        reviewC.setRecordId(pregnancyC.getId());
        reviewC.setStatus("CONFIRMED");

        // ── Person D: Child with Overdue NRC Follow-up (List 2) — CRITICAL (overdue by 12 days) ──
        UUID personDId = UUID.randomUUID();
        HouseholdMember memberD = new HouseholdMember();
        memberD.setId(personDId);
        memberD.setName("Child Rohan");

        Child childD = new Child();
        childD.setId(UUID.randomUUID());
        childD.setHouseholdMember(memberD);

        Referral referralD = new Referral();
        referralD.setId(UUID.randomUUID());
        referralD.setChild(childD);
        referralD.setFollowUpDueDate(now.minusDays(12));
        referralD.setStatus("Pending");

        // ── Person E: Child appearing in BOTH List 1 (HIGH score 70) AND List 2 (Overdue Vaccine 20 days) ──
        UUID personEId = UUID.randomUUID();
        HouseholdMember memberE = new HouseholdMember();
        memberE.setId(personEId);
        memberE.setName("Child Ananya");

        Child childE = new Child();
        childE.setId(UUID.randomUUID());
        childE.setHouseholdMember(memberE);
        childE.setRiskScore(70);
        childE.setRiskLevel("HIGH");
        childE.setRiskPrimaryDriver("High Nutritional Risk");

        Vaccination vaccineE = new Vaccination();
        vaccineE.setId(UUID.randomUUID());
        vaccineE.setChild(childE);
        vaccineE.setVaccineName("OPV-1");
        vaccineE.setDueDate(now.minusDays(20)); // Overdue by 20 days

        // Mock Repositories
        when(childRepository.findByAshaId(ashaId)).thenReturn(List.of(childA, childE));
        when(pregnancyRepository.findActiveOrDraftPregnancies(ashaId)).thenReturn(List.of(pregnancyB, pregnancyC));
        when(vaccinationRepository.findByChild_Asha_Id(ashaId)).thenReturn(List.of(vaccineE));
        when(referralRepository.findByChild_Asha_Id(ashaId)).thenReturn(List.of(referralD));
        when(pendingReviewRepository.findAll()).thenReturn(List.of(reviewC));

        // Execute Merge Algorithm
        List<PriorityItemDto> merged = priorityListService.getMergedPriorityList(ashaId);

        // 1. Deduplication Verification
        // Exactly 5 distinct people should exist (A, B, C, D, E)
        assertEquals(5, merged.size(), "Each person must appear exactly once");

        // Person E must be merged into 1 item with HIGH level (rank preserved over vaccine's MEDIUM)
        PriorityItemDto itemE = merged.stream()
            .filter(i -> i.personId().equals(personEId.toString())).findFirst().orElseThrow();
        assertEquals("HIGH", itemE.riskLevel());
        assertEquals(70, itemE.riskScore());
        assertEquals(20, itemE.daysOverdue()); // Overdue vaccine days merged in

        // 2. Final Sort Verification
        // Rank ordering per Section 2.2:
        // CRITICAL first: Person D (NRC follow-up overdue 12 days) & Person C (Supervisor Confirmed High-Risk)
        // then HIGH: Person E (score 70) & Person B (ANC overdue 45 days)
        // then overdue-by-most-days
        // then MEDIUM: Person A (score 40)

        assertEquals("CRITICAL", merged.get(0).riskLevel());
        assertEquals("CRITICAL", merged.get(1).riskLevel());

        assertEquals("HIGH", merged.get(2).riskLevel());
        assertEquals("HIGH", merged.get(3).riskLevel());

        assertEquals("MEDIUM", merged.get(4).riskLevel());
        assertEquals(personAId.toString(), merged.get(4).personId());
    }
}
