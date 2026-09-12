package com.ashaai.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implements Vaccine Due-Date Engine with NVHCP schedule and pure date arithmetic.
 * Adheres strictly to docs/AI_FEATURES_WIRING_PROMPT_FINAL.md Section 2.11,
 * docs/ARCHITECTURE.md, and docs/RULES.md.
 *
 * Deterministic date arithmetic only — no external or AI calls.
 */
@Service
public class VaccineEngineService {

    private static final Logger log = LoggerFactory.getLogger(VaccineEngineService.class);

    public record VaccineScheduleDefinition(
        String vaccineCode,
        String vaccineName,
        int offsetDays,
        String milestone
    ) {}

    public enum VaccineStatus {
        GIVEN,
        DUE_SOON,   // Within 3 days of due date
        OVERDUE,    // Past due date and not given
        UPCOMING
    }

    public record VaccineEvaluation(
        String vaccineCode,
        String vaccineName,
        String milestone,
        LocalDate dueDate,
        LocalDate givenDate,
        VaccineStatus status,
        long daysDifference, // positive: days overdue or days until due
        boolean triggerNotification,
        boolean surfaceOnPriorityList
    ) {}

    /**
     * Fixed NVHCP (National Viral Hepatitis / UIP) Schedule Lookup Table.
     * Hardcoded per Section 2.11.
     */
    private static final List<VaccineScheduleDefinition> NVHCP_SCHEDULE;

    static {
        List<VaccineScheduleDefinition> list = new ArrayList<>();
        // At Birth
        list.add(new VaccineScheduleDefinition("BCG", "Bacillus Calmette-Guérin", 0, "At Birth"));
        list.add(new VaccineScheduleDefinition("HEP_B_0", "Hepatitis B - Birth Dose", 0, "At Birth"));
        list.add(new VaccineScheduleDefinition("OPV_0", "Oral Polio Vaccine - 0 Dose", 0, "At Birth"));

        // 6 Weeks (42 days)
        list.add(new VaccineScheduleDefinition("OPV_1", "Oral Polio Vaccine - 1", 42, "6 Weeks"));
        list.add(new VaccineScheduleDefinition("PENTAVALENT_1", "Pentavalent - 1", 42, "6 Weeks"));
        list.add(new VaccineScheduleDefinition("ROTA_1", "Rotavirus Vaccine - 1", 42, "6 Weeks"));
        list.add(new VaccineScheduleDefinition("IPV_1", "Inactivated Polio Vaccine - 1", 42, "6 Weeks"));

        // 10 Weeks (70 days)
        list.add(new VaccineScheduleDefinition("OPV_2", "Oral Polio Vaccine - 2", 70, "10 Weeks"));
        list.add(new VaccineScheduleDefinition("PENTAVALENT_2", "Pentavalent - 2", 70, "10 Weeks"));
        list.add(new VaccineScheduleDefinition("ROTA_2", "Rotavirus Vaccine - 2", 70, "10 Weeks"));

        // 14 Weeks (98 days)
        list.add(new VaccineScheduleDefinition("OPV_3", "Oral Polio Vaccine - 3", 98, "14 Weeks"));
        list.add(new VaccineScheduleDefinition("PENTAVALENT_3", "Pentavalent - 3", 98, "14 Weeks"));
        list.add(new VaccineScheduleDefinition("ROTA_3", "Rotavirus Vaccine - 3", 98, "14 Weeks"));
        list.add(new VaccineScheduleDefinition("IPV_2", "Inactivated Polio Vaccine - 2", 98, "14 Weeks"));

        // 9 Months (270 days)
        list.add(new VaccineScheduleDefinition("MR_1", "Measles-Rubella - 1", 270, "9 Months"));
        list.add(new VaccineScheduleDefinition("JE_1", "Japanese Encephalitis - 1", 270, "9 Months"));
        list.add(new VaccineScheduleDefinition("VIT_A_1", "Vitamin A - 1st Dose", 270, "9 Months"));

        // 16-24 Months (480 days / ~16 months)
        list.add(new VaccineScheduleDefinition("MR_2", "Measles-Rubella - 2", 480, "16-24 Months"));
        list.add(new VaccineScheduleDefinition("DPT_BOOSTER_1", "DPT Booster - 1", 480, "16-24 Months"));
        list.add(new VaccineScheduleDefinition("OPV_BOOSTER", "Oral Polio Vaccine Booster", 480, "16-24 Months"));

        // 5-6 Years (1825 days / 5 years)
        list.add(new VaccineScheduleDefinition("DPT_BOOSTER_2", "DPT Booster - 2", 1825, "5-6 Years"));

        NVHCP_SCHEDULE = Collections.unmodifiableList(list);
    }

    public List<VaccineScheduleDefinition> getScheduleDefinitions() {
        return NVHCP_SCHEDULE;
    }

    /**
     * Evaluates a child's complete immunization record against the NVHCP schedule.
     *
     * @param dateOfBirth Child's birth date
     * @param givenVaccines Map of vaccineCode -> date given (nullable or empty if none given)
     * @param asOfDate Current evaluation date
     * @return List of evaluation status per vaccine in chronological order
     */
    public List<VaccineEvaluation> evaluateSchedule(
        LocalDate dateOfBirth,
        Map<String, LocalDate> givenVaccines,
        LocalDate asOfDate
    ) {
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("Child date of birth cannot be null.");
        }
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        List<VaccineEvaluation> evaluations = new ArrayList<>();

        for (VaccineScheduleDefinition def : NVHCP_SCHEDULE) {
            LocalDate dueDate = dateOfBirth.plusDays(def.offsetDays());
            LocalDate givenDate = givenVaccines != null ? givenVaccines.get(def.vaccineCode()) : null;

            VaccineStatus status;
            long daysDiff = 0;
            boolean triggerNotification = false;
            boolean surfaceOnPriorityList = false;

            if (givenDate != null) {
                status = VaccineStatus.GIVEN;
                daysDiff = ChronoUnit.DAYS.between(dueDate, givenDate);
            } else {
                if (asOfDate.isAfter(dueDate)) {
                    // Past due and not given -> appears on priority list as overdue with day count
                    status = VaccineStatus.OVERDUE;
                    daysDiff = ChronoUnit.DAYS.between(dueDate, asOfDate);
                    surfaceOnPriorityList = true;
                } else {
                    long daysUntilDue = ChronoUnit.DAYS.between(asOfDate, dueDate);
                    daysDiff = daysUntilDue;
                    if (daysUntilDue <= 3) {
                        // Within 3 days of due -> notification
                        status = VaccineStatus.DUE_SOON;
                        triggerNotification = true;
                    } else {
                        status = VaccineStatus.UPCOMING;
                    }
                }
            }

            evaluations.add(new VaccineEvaluation(
                def.vaccineCode(),
                def.vaccineName(),
                def.milestone(),
                dueDate,
                givenDate,
                status,
                daysDiff,
                triggerNotification,
                surfaceOnPriorityList
            ));
        }

        return evaluations;
    }

    /**
     * Calculates the maximum overdue gap (in days) across all pending vaccines.
     * Directly consumed by the Predictive Risk Engine (Section 2.1).
     */
    public int calculateMaxVaccinationGapDays(
        LocalDate dateOfBirth,
        Map<String, LocalDate> givenVaccines,
        LocalDate asOfDate
    ) {
        List<VaccineEvaluation> evaluations = evaluateSchedule(dateOfBirth, givenVaccines, asOfDate);
        long maxGap = 0;
        for (VaccineEvaluation eval : evaluations) {
            if (eval.status() == VaccineStatus.OVERDUE && eval.daysDifference() > maxGap) {
                maxGap = eval.daysDifference();
            }
        }
        return (int) maxGap;
    }
}
