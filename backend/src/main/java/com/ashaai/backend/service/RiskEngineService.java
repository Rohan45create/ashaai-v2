package com.ashaai.backend.service;

import com.ashaai.backend.entity.Child;
import com.ashaai.backend.repository.ChildRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Implements Predictive Risk Engine and Genetic Risk Augmentation.
 * Adheres strictly to docs/AI_FEATURES_WIRING_PROMPT_FINAL.md Section 2.1,
 * docs/ARCHITECTURE.md, and docs/RULES.md.
 *
 * Deterministic Java formula only — no external or AI calls.
 */
@Service
public class RiskEngineService {

    private static final Logger log = LoggerFactory.getLogger(RiskEngineService.class);

    private final ChildRepository childRepository;

    public RiskEngineService(ChildRepository childRepository) {
        this.childRepository = childRepository;
    }

    /**
     * Input risk factors for deterministic evaluation.
     */
    public record RiskFactors(
        String malnutritionGrade,
        Integer daysSinceLastVisit,
        Double motherHaemoglobin,
        Integer breastfeedingCessationMonths,
        Integer vaccinationGapDays,
        boolean seasonalAgriculturalStress,
        boolean hasSiblingWithSamMam,
        String siblingIdentifier
    ) {}

    /**
     * Result of risk calculation.
     */
    public record RiskEvaluationResult(
        int baseScore,
        int finalScore,
        String riskLevel,
        String primaryDriver,
        boolean geneticAugmentationApplied,
        String triggeringSibling
    ) {}

    /**
     * Exact weighted scoring formula per Section 2.1 with Genetic Risk Augmentation.
     */
    public RiskEvaluationResult calculateRisk(RiskFactors factors) {
        int score = 0;
        int maxDriverPoints = -1;
        String primaryDriver = "Normal assessment — no acute risk drivers";

        // 1. Malnutrition Grade (MAM)
        if ("MAM".equalsIgnoreCase(factors.malnutritionGrade())) {
            score += 25;
            if (25 > maxDriverPoints) {
                maxDriverPoints = 25;
                primaryDriver = "Moderate Acute Malnutrition (MAM) detected";
            }
        }

        // 2. Days since last visit
        if (factors.daysSinceLastVisit() != null) {
            int days = factors.daysSinceLastVisit();
            int points = 0;
            if (days > 30) {
                points = 20;
            } else if (days > 21) {
                points = 15;
            } else if (days > 14) {
                points = 10;
            } else if (days > 7) {
                points = 5;
            }
            if (points > 0) {
                score += points;
                if (points > maxDriverPoints) {
                    maxDriverPoints = points;
                    primaryDriver = "Overdue visit — " + days + " days since last contact";
                }
            }
        }

        // 3. Mother haemoglobin
        if (factors.motherHaemoglobin() != null) {
            double hb = factors.motherHaemoglobin();
            int points = 0;
            if (hb < 8.0) {
                points = 15;
            } else if (hb < 10.0) {
                points = 8;
            }
            if (points > 0) {
                score += points;
                if (points > maxDriverPoints) {
                    maxDriverPoints = points;
                    primaryDriver = "Low maternal haemoglobin — " + hb + " g/dL";
                }
            }
        }

        // 4. Breastfeeding cessation
        if (factors.breastfeedingCessationMonths() != null && factors.breastfeedingCessationMonths() < 6) {
            score += 10;
            if (10 > maxDriverPoints) {
                maxDriverPoints = 10;
                primaryDriver = "Early breastfeeding cessation (< 6 months)";
            }
        }

        // 5. Vaccination gap days
        if (factors.vaccinationGapDays() != null) {
            int gap = factors.vaccinationGapDays();
            int points = 0;
            if (gap > 30) {
                points = 15;
            } else if (gap > 14) {
                points = 8;
            }
            if (points > 0) {
                score += points;
                if (points > maxDriverPoints) {
                    maxDriverPoints = points;
                    primaryDriver = "Vaccination overdue by " + gap + " days";
                }
            }
        }

        // 6. Seasonal agricultural stress
        if (factors.seasonalAgriculturalStress()) {
            score += 10;
            if (10 > maxDriverPoints) {
                maxDriverPoints = 10;
                primaryDriver = "Seasonal agricultural stress period";
            }
        }

        // Base formula cap at 100
        score = Math.min(score, 100);
        int baseScore = score;

        // SAM Rule: Force CRITICAL risk_level and score = max(score, 80)
        String riskLevel;
        if ("SAM".equalsIgnoreCase(factors.malnutritionGrade())) {
            score = Math.max(score, 80);
            riskLevel = "CRITICAL";
            primaryDriver = "Severe Acute Malnutrition (SAM) detected";
        } else {
            riskLevel = getRiskLevelBand(score);
        }

        // 7. Genetic Risk Augmentation (+15, separate deterministic step)
        boolean geneticApplied = false;
        String triggeringSibling = null;
        if (factors.hasSiblingWithSamMam()) {
            score = Math.min(score + 15, 100);
            geneticApplied = true;
            triggeringSibling = factors.siblingIdentifier() != null ? factors.siblingIdentifier() : "UNKNOWN_SIBLING";

            // Re-evaluate band if augmented score crosses boundary (unless already CRITICAL from SAM)
            if (!"CRITICAL".equals(riskLevel)) {
                riskLevel = getRiskLevelBand(score);
            }

            // Log which sibling triggered it per RULES.md (IDs and event types only)
            log.warn("event=genetic_risk_augmentation_applied score_bump=+15 final_score={} triggering_sibling={}",
                    score, triggeringSibling);
        }

        return new RiskEvaluationResult(
            baseScore,
            score,
            riskLevel,
            primaryDriver,
            geneticApplied,
            triggeringSibling
        );
    }

    /**
     * Map numeric score to risk band.
     * Bands: 0-25 LOW, 26-50 MEDIUM, 51-75 HIGH, 76-100 CRITICAL.
     */
    public static String getRiskLevelBand(int score) {
        if (score <= 25) return "LOW";
        if (score <= 50) return "MEDIUM";
        if (score <= 75) return "HIGH";
        return "CRITICAL";
    }

    /**
     * Evaluates and updates a single child entity in the database.
     */
    public RiskEvaluationResult evaluateChild(Child child, LocalDate asOfDate, boolean seasonalStress) {
        int daysSinceVisit = 0;
        if (child.getLastVisitDate() != null) {
            daysSinceVisit = (int) ChronoUnit.DAYS.between(child.getLastVisitDate(), asOfDate);
        } else {
            daysSinceVisit = 31; // No recorded visit -> penalize as >30 days
        }

        // Check sibling SAM/MAM history
        boolean hasSiblingSamMam = Boolean.TRUE.equals(child.getSiblingMalnutritionHistory());
        String triggeringSiblingId = null;

        if (!hasSiblingSamMam && child.getHouseholdMember() != null && child.getHouseholdMember().getHousehold() != null) {
            UUID householdId = child.getHouseholdMember().getHousehold().getId();
            List<Child> siblings = childRepository.findByHouseholdMember_Household_Id(householdId);
            for (Child sibling : siblings) {
                if (!sibling.getId().equals(child.getId())) {
                    if ("SAM".equalsIgnoreCase(sibling.getMalnutritionGrade()) || "MAM".equalsIgnoreCase(sibling.getMalnutritionGrade())) {
                        hasSiblingSamMam = true;
                        triggeringSiblingId = sibling.getId().toString();
                        break;
                    }
                }
            }
        }

        RiskFactors factors = new RiskFactors(
            child.getMalnutritionGrade(),
            daysSinceVisit,
            null, // Mother Hb is optional or loaded from pregnancy if available
            child.getBreastfeedingCessationMonths(),
            null, // vaccination gap days evaluated separately if linked
            seasonalStress,
            hasSiblingSamMam,
            triggeringSiblingId
        );

        RiskEvaluationResult result = calculateRisk(factors);

        child.setRiskScore(result.finalScore());
        child.setRiskLevel(result.riskLevel());
        child.setRiskPrimaryDriver(result.primaryDriver());
        child.setRiskUpdatedAt(OffsetDateTime.now());
        childRepository.save(child);

        return result;
    }

    /**
     * Run nightly scoring across all children in the system.
     */
    public void runNightlyRiskScoring() {
        LocalDate today = LocalDate.now();
        List<Child> children = childRepository.findAll();
        for (Child child : children) {
            evaluateChild(child, today, false);
        }
        log.info("event=nightly_risk_scoring_completed count={}", children.size());
    }
}
