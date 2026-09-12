package com.ashaai.backend.service;

import com.ashaai.backend.dto.PriorityItemDto;
import com.ashaai.backend.entity.*;
import com.ashaai.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Implements the 3-list merge algorithm per Section 2.2:
 * 1. Current risk scores, high to low
 * 2. Overdue ANC visits / vaccines / NRC follow-ups, sorted by days overdue descending
 * 3. Manual supervisor flags
 *
 * Deduplicate by person.
 * Final sort: CRITICAL first, then HIGH, then overdue-by-most-days, then MEDIUM.
 * Pure Postgres queries via Spring Data JPA — no external/AI calls, no Redis.
 */
@Service
public class PriorityListService {

    private static final Logger log = LoggerFactory.getLogger(PriorityListService.class);

    private final ChildRepository childRepository;
    private final PregnancyRepository pregnancyRepository;
    private final VaccinationRepository vaccinationRepository;
    private final ReferralRepository referralRepository;
    private final PendingReviewRepository pendingReviewRepository;
    private final RiskEngineService riskEngineService;

    public PriorityListService(
        ChildRepository childRepository,
        PregnancyRepository pregnancyRepository,
        VaccinationRepository vaccinationRepository,
        ReferralRepository referralRepository,
        PendingReviewRepository pendingReviewRepository,
        RiskEngineService riskEngineService
    ) {
        this.childRepository = childRepository;
        this.pregnancyRepository = pregnancyRepository;
        this.vaccinationRepository = vaccinationRepository;
        this.referralRepository = referralRepository;
        this.pendingReviewRepository = pendingReviewRepository;
        this.riskEngineService = riskEngineService;
    }

    /**
     * Executes the 3-list merge and returns the ranked priority list for an ASHA worker.
     */
    @Transactional(readOnly = true)
    public List<PriorityItemDto> getMergedPriorityList(UUID ashaId) {
        LocalDate now = LocalDate.now();

        // ─────────────────────────────────────────────────────────────────
        // LIST 1: Current risk scores (Children)
        // ─────────────────────────────────────────────────────────────────
        List<PriorityItemDto> list1 = new ArrayList<>();
        List<Child> children = childRepository.findByAshaId(ashaId);
        for (Child child : children) {
            int score = child.getRiskScore() != null ? child.getRiskScore() : 0;
            String level = child.getRiskLevel() != null ? child.getRiskLevel() : RiskEngineService.getRiskLevelBand(score);

            // Filter for children requiring attention (CRITICAL, HIGH, MEDIUM or score >= 26)
            if (score >= 26 || Arrays.asList("CRITICAL", "HIGH", "MEDIUM").contains(level)) {
                int daysSinceVisit = 0;
                if (child.getLastVisitDate() != null) {
                    daysSinceVisit = Math.max(0, (int) ChronoUnit.DAYS.between(child.getLastVisitDate(), now));
                }

                String personId = child.getHouseholdMember() != null
                    ? child.getHouseholdMember().getId().toString()
                    : child.getId().toString();

                String village = "";
                String householdId = "";
                if (child.getHouseholdMember() != null && child.getHouseholdMember().getHousehold() != null) {
                    Household hh = child.getHouseholdMember().getHousehold();
                    village = hh.getAddress() != null ? hh.getAddress() : "";
                    householdId = hh.getId() != null ? hh.getId().toString() : "";
                }

                list1.add(new PriorityItemDto(
                    child.getId().toString(),
                    child.getHouseholdMember() != null ? child.getHouseholdMember().getName() : "Child",
                    "child",
                    level,
                    score,
                    child.getAgeMonths() != null ? child.getAgeMonths() : 0,
                    child.getRiskPrimaryDriver() != null ? child.getRiskPrimaryDriver() : "Elevated nutritional risk",
                    child.getRiskRecommendedAction() != null ? child.getRiskRecommendedAction() : "Conduct growth monitoring and nutritional counseling",
                    daysSinceVisit,
                    village,
                    householdId,
                    personId
                ));
            }
        }
        list1.sort((a, b) -> Integer.compare(b.riskScore(), a.riskScore()));

        // ─────────────────────────────────────────────────────────────────
        // LIST 2: Overdue ANC visits / vaccines / NRC follow-ups
        // ─────────────────────────────────────────────────────────────────
        List<PriorityItemDto> list2 = new ArrayList<>();

        // 2a. Overdue ANC Visits
        List<Pregnancy> pregnancies = pregnancyRepository.findActiveOrDraftPregnancies(ashaId);
        for (Pregnancy pregnancy : pregnancies) {
            long daysOverdue = computeAncDaysOverdue(pregnancy, now);
            if (daysOverdue > 0) {
                String personId = pregnancy.getMotherMember() != null
                    ? pregnancy.getMotherMember().getId().toString()
                    : pregnancy.getId().toString();

                String village = "";
                String householdId = "";
                if (pregnancy.getHousehold() != null) {
                    village = pregnancy.getHousehold().getAddress() != null ? pregnancy.getHousehold().getAddress() : "";
                    householdId = pregnancy.getHousehold().getId() != null ? pregnancy.getHousehold().getId().toString() : "";
                }

                String level = daysOverdue > 30 ? "HIGH" : "MEDIUM";
                int score = daysOverdue > 30 ? 65 : 45;

                list2.add(new PriorityItemDto(
                    pregnancy.getId().toString(),
                    pregnancy.getMotherMember() != null ? pregnancy.getMotherMember().getName() : "Mother",
                    "pregnancy",
                    level,
                    score,
                    0,
                    "Overdue ANC Visit — " + daysOverdue + " days overdue",
                    "Schedule immediate ANC checkup and iron-folic acid supplementation",
                    (int) daysOverdue,
                    village,
                    householdId,
                    personId
                ));
            }
        }

        // 2b. Overdue Vaccines
        List<Vaccination> vaccinations = vaccinationRepository.findByChild_Asha_Id(ashaId);
        Map<String, Long> childMaxVaccineGap = new HashMap<>();
        Map<String, String> childMaxVaccineName = new HashMap<>();
        Map<String, Child> childMap = new HashMap<>();

        for (Vaccination v : vaccinations) {
            if (v.getGivenDate() == null && v.getDueDate() != null && now.isAfter(v.getDueDate())) {
                long gap = ChronoUnit.DAYS.between(v.getDueDate(), now);
                Child c = v.getChild();
                if (c != null) {
                    String cId = c.getId().toString();
                    childMap.put(cId, c);
                    if (gap > childMaxVaccineGap.getOrDefault(cId, 0L)) {
                        childMaxVaccineGap.put(cId, gap);
                        childMaxVaccineName.put(cId, v.getVaccineName() != null ? v.getVaccineName() : "Scheduled Vaccine");
                    }
                }
            }
        }

        for (Map.Entry<String, Long> entry : childMaxVaccineGap.entrySet()) {
            String childId = entry.getKey();
            long gap = entry.getValue();
            Child c = childMap.get(childId);
            String vName = childMaxVaccineName.get(childId);

            String personId = c.getHouseholdMember() != null ? c.getHouseholdMember().getId().toString() : childId;
            String village = "";
            String householdId = "";
            if (c.getHouseholdMember() != null && c.getHouseholdMember().getHousehold() != null) {
                village = c.getHouseholdMember().getHousehold().getAddress() != null ? c.getHouseholdMember().getHousehold().getAddress() : "";
                householdId = c.getHouseholdMember().getHousehold().getId() != null ? c.getHouseholdMember().getHousehold().getId().toString() : "";
            }

            String level = gap > 30 ? "HIGH" : "MEDIUM";
            int score = gap > 30 ? 60 : 35;

            list2.add(new PriorityItemDto(
                childId,
                c.getHouseholdMember() != null ? c.getHouseholdMember().getName() : "Child",
                "child",
                level,
                score,
                c.getAgeMonths() != null ? c.getAgeMonths() : 0,
                "Overdue Vaccine (" + vName + ") — " + gap + " days overdue",
                "Administer pending " + vName + " dose at primary health center",
                (int) gap,
                village,
                householdId,
                personId
            ));
        }

        // 2c. Overdue NRC Follow-ups
        List<Referral> referrals = referralRepository.findByChild_Asha_Id(ashaId);
        for (Referral ref : referrals) {
            if (ref.getFollowUpDueDate() != null && now.isAfter(ref.getFollowUpDueDate()) && !"Completed".equalsIgnoreCase(ref.getStatus())) {
                long overdue = ChronoUnit.DAYS.between(ref.getFollowUpDueDate(), now);
                Child c = ref.getChild();
                String personId = (c != null && c.getHouseholdMember() != null)
                    ? c.getHouseholdMember().getId().toString()
                    : (ref.getHouseholdMember() != null ? ref.getHouseholdMember().getId().toString() : ref.getId().toString());

                String village = "";
                String householdId = "";
                String name = "Referred Child";
                int ageMonths = 0;
                String childId = ref.getId().toString();

                if (c != null) {
                    childId = c.getId().toString();
                    if (c.getHouseholdMember() != null) {
                        name = c.getHouseholdMember().getName();
                        if (c.getHouseholdMember().getHousehold() != null) {
                            village = c.getHouseholdMember().getHousehold().getAddress() != null ? c.getHouseholdMember().getHousehold().getAddress() : "";
                            householdId = c.getHouseholdMember().getHousehold().getId() != null ? c.getHouseholdMember().getHousehold().getId().toString() : "";
                        }
                    }
                    if (c.getAgeMonths() != null) ageMonths = c.getAgeMonths();
                }

                list2.add(new PriorityItemDto(
                    childId,
                    name,
                    "child",
                    "CRITICAL",
                    88,
                    ageMonths,
                    "Overdue NRC Follow-up — " + overdue + " days overdue",
                    "Conduct mandatory NRC discharge follow-up visit immediately",
                    (int) overdue,
                    village,
                    householdId,
                    personId
                ));
            }
        }
        list2.sort((a, b) -> Integer.compare(b.daysOverdue(), a.daysOverdue()));

        // ─────────────────────────────────────────────────────────────────
        // LIST 3: Manual supervisor flags (Confirmed in PendingReview)
        // ─────────────────────────────────────────────────────────────────
        List<PriorityItemDto> list3 = new ArrayList<>();
        List<PendingReview> confirmedReviews = pendingReviewRepository.findAll();
        Set<UUID> confirmedPregnancyIds = new HashSet<>();

        for (PendingReview pr : confirmedReviews) {
            if ("pregnancies".equalsIgnoreCase(pr.getTableName()) && "CONFIRMED".equalsIgnoreCase(pr.getStatus())) {
                confirmedPregnancyIds.add(pr.getRecordId());
            }
        }

        for (Pregnancy p : pregnancies) {
            if (confirmedPregnancyIds.contains(p.getId()) || Boolean.TRUE.equals(p.getHighRiskFlag())) {
                String personId = p.getMotherMember() != null ? p.getMotherMember().getId().toString() : p.getId().toString();
                String village = p.getHousehold() != null && p.getHousehold().getAddress() != null ? p.getHousehold().getAddress() : "";
                String householdId = p.getHousehold() != null && p.getHousehold().getId() != null ? p.getHousehold().getId().toString() : "";

                String reasonStr = (p.getHighRiskReasons() != null && !p.getHighRiskReasons().isEmpty())
                    ? String.join("; ", p.getHighRiskReasons())
                    : "Supervisor flagged high risk ANC";

                list3.add(new PriorityItemDto(
                    p.getId().toString(),
                    p.getMotherMember() != null ? p.getMotherMember().getName() : "Mother",
                    "pregnancy",
                    "CRITICAL",
                    90,
                    0,
                    "Supervisor Confirmed High-Risk ANC: " + reasonStr,
                    "High-risk pregnancy protocol — priority home checkup and doctor visit",
                    0,
                    village,
                    householdId,
                    personId
                ));
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // MERGE & DEDUPLICATE BY PERSON
        // ─────────────────────────────────────────────────────────────────
        Map<String, PriorityItemDto> mergedByPerson = new LinkedHashMap<>();

        // Add from all three lists, merging when person is already present
        List<PriorityItemDto> allItems = new ArrayList<>();
        allItems.addAll(list1);
        allItems.addAll(list2);
        allItems.addAll(list3);

        for (PriorityItemDto item : allItems) {
            String key = item.personId();
            if (!mergedByPerson.containsKey(key)) {
                mergedByPerson.put(key, item);
            } else {
                PriorityItemDto existing = mergedByPerson.get(key);
                mergedByPerson.put(key, mergePersonItems(existing, item));
            }
        }

        List<PriorityItemDto> mergedList = new ArrayList<>(mergedByPerson.values());

        // ─────────────────────────────────────────────────────────────────
        // FINAL SORT: CRITICAL first, then HIGH, then overdue-by-most-days, then MEDIUM
        // ─────────────────────────────────────────────────────────────────
        mergedList.sort((a, b) -> {
            int rankA = getSeverityRank(a.riskLevel());
            int rankB = getSeverityRank(b.riskLevel());

            // 1. CRITICAL first
            if (rankA == 4 || rankB == 4) {
                if (rankA != rankB) return Integer.compare(rankB, rankA);
                // Both CRITICAL: most overdue first, then highest risk score
                int daysCmp = Integer.compare(b.daysOverdue(), a.daysOverdue());
                if (daysCmp != 0) return daysCmp;
                return Integer.compare(b.riskScore(), a.riskScore());
            }

            // 2. then HIGH
            if (rankA == 3 || rankB == 3) {
                if (rankA != rankB) return Integer.compare(rankB, rankA);
                // Both HIGH: most overdue first, then highest risk score
                int daysCmp = Integer.compare(b.daysOverdue(), a.daysOverdue());
                if (daysCmp != 0) return daysCmp;
                return Integer.compare(b.riskScore(), a.riskScore());
            }

            // 3. then overdue-by-most-days
            int daysA = a.daysOverdue();
            int daysB = b.daysOverdue();
            if (daysA > 0 || daysB > 0) {
                if (daysA != daysB) return Integer.compare(daysB, daysA);
            }

            // 4. then MEDIUM (and other lower bands) by risk_score descending
            if (rankA != rankB) return Integer.compare(rankB, rankA);
            return Integer.compare(b.riskScore(), a.riskScore());
        });

        log.info("event=priority_list_generated asha_id={} total_items={}", ashaId, mergedList.size());
        return mergedList;
    }

    /**
     * Compute overdue days for a pregnancy based on standard ANC milestones.
     */
    private long computeAncDaysOverdue(Pregnancy p, LocalDate now) {
        if (p.getLmp() == null) return 0;
        LocalDate lmp = p.getLmp();

        // Check each ANC milestone
        if (p.getAnc1Date() == null && now.isAfter(lmp.plusDays(84))) {
            return ChronoUnit.DAYS.between(lmp.plusDays(84), now);
        } else if (p.getAnc2Date() == null && now.isAfter(lmp.plusDays(182))) {
            return ChronoUnit.DAYS.between(lmp.plusDays(182), now);
        } else if (p.getAnc3Date() == null && now.isAfter(lmp.plusDays(238))) {
            return ChronoUnit.DAYS.between(lmp.plusDays(238), now);
        } else if (p.getAnc4Date() == null && now.isAfter(lmp.plusDays(252))) {
            return ChronoUnit.DAYS.between(lmp.plusDays(252), now);
        } else if (p.getLastAncDate() != null) {
            long daysSinceLast = ChronoUnit.DAYS.between(p.getLastAncDate(), now);
            if (daysSinceLast > 35) {
                return daysSinceLast - 30;
            }
        }
        return 0;
    }

    /**
     * Merge two items belonging to the same person, preserving the highest severity.
     */
    public static PriorityItemDto mergePersonItems(PriorityItemDto existing, PriorityItemDto incoming) {
        int rankExisting = getSeverityRank(existing.riskLevel());
        int rankIncoming = getSeverityRank(incoming.riskLevel());

        String higherLevel = rankIncoming > rankExisting ? incoming.riskLevel() : existing.riskLevel();
        int higherScore = Math.max(existing.riskScore(), incoming.riskScore());
        int maxDaysOverdue = Math.max(existing.daysOverdue(), incoming.daysOverdue());

        String primaryDriver = rankIncoming > rankExisting ? incoming.primaryDriver() : existing.primaryDriver();
        String recommendedAction = rankIncoming > rankExisting ? incoming.recommendedAction() : existing.recommendedAction();

        return new PriorityItemDto(
            existing.id(),
            existing.name(),
            existing.type(),
            higherLevel,
            higherScore,
            existing.ageMonths(),
            primaryDriver,
            recommendedAction,
            maxDaysOverdue,
            existing.village(),
            existing.householdId(),
            existing.personId()
        );
    }

    /**
     * Severity ranking: CRITICAL (4) > HIGH (3) > MEDIUM (2) > LOW (1).
     */
    public static int getSeverityRank(String riskLevel) {
        if ("CRITICAL".equalsIgnoreCase(riskLevel)) return 4;
        if ("HIGH".equalsIgnoreCase(riskLevel)) return 3;
        if ("MEDIUM".equalsIgnoreCase(riskLevel)) return 2;
        return 1;
    }
}
