package com.ashaai.backend.service;

import com.ashaai.backend.entity.AshaHead;
import com.ashaai.backend.entity.Notification;
import com.ashaai.backend.entity.PendingReview;
import com.ashaai.backend.entity.Pregnancy;
import com.ashaai.backend.repository.NotificationRepository;
import com.ashaai.backend.repository.PendingReviewRepository;
import com.ashaai.backend.repository.PregnancyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements ANC High-Risk Flagging and the supervisor-confirmation gate
 * before priority-list propagation.
 *
 * Adheres strictly to docs/AI_FEATURES_WIRING_PROMPT_FINAL.md Section 2.10,
 * docs/MEMORY.md, docs/ARCHITECTURE.md, and docs/RULES.md.
 */
@Service
public class AncFlaggingService {

    private static final Logger log = LoggerFactory.getLogger(AncFlaggingService.class);

    private final NotificationRepository notificationRepository;
    private final PendingReviewRepository pendingReviewRepository;
    private final PregnancyRepository pregnancyRepository;

    public AncFlaggingService(
        NotificationRepository notificationRepository,
        PendingReviewRepository pendingReviewRepository,
        PregnancyRepository pregnancyRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.pendingReviewRepository = pendingReviewRepository;
        this.pregnancyRepository = pregnancyRepository;
    }

    /**
     * Clinical factors for ANC high-risk evaluation.
     */
    public record AncClinicalInputs(
        Integer motherAge,
        Double haemoglobinGdl,
        Integer systolicBp,
        Integer diastolicBp,
        boolean previousCSection,
        boolean previousStillbirth,
        String bloodGroup
    ) {}

    /**
     * Evaluation outcome.
     */
    public record AncFlagResult(
        boolean isHighRisk,
        List<String> reasons,
        boolean notificationSent,
        boolean pendingReviewCreated
    ) {}

    /**
     * Deterministic clinical rules per Section 2.10:
     * Triggers:
     * - mother's age < 18 or > 35
     * - haemoglobin < 8.0 g/dL
     * - BP > 140/90
     * - previous C-section
     * - previous stillbirth
     * - OR two moderate factors together
     */
    public AncFlagResult evaluateClinicalTriggers(AncClinicalInputs inputs) {
        List<String> majorReasons = new ArrayList<>();
        List<String> moderateReasons = new ArrayList<>();

        // 1. Mother Age (<18 or >35)
        if (inputs.motherAge() != null) {
            int age = inputs.motherAge();
            if (age < 18 || age > 35) {
                majorReasons.add("High-risk maternal age (<18 or >35): " + age + " years");
            } else if ((age >= 18 && age <= 19) || (age >= 30 && age <= 35)) {
                moderateReasons.add("Borderline maternal age: " + age + " years");
            }
        }

        // 2. Haemoglobin (<8 severe, 8.0-10.9 moderate)
        if (inputs.haemoglobinGdl() != null) {
            double hb = inputs.haemoglobinGdl();
            if (hb < 8.0) {
                majorReasons.add("Severe maternal anaemia (Hb < 8.0 g/dL): " + hb + " g/dL");
            } else if (hb < 11.0) {
                moderateReasons.add("Moderate maternal anaemia (Hb 8.0–10.9 g/dL): " + hb + " g/dL");
            }
        }

        // 3. Blood Pressure (>140/90 severe, borderline 130-140 / 85-90)
        if (inputs.systolicBp() != null || inputs.diastolicBp() != null) {
            int sys = inputs.systolicBp() != null ? inputs.systolicBp() : 120;
            int dia = inputs.diastolicBp() != null ? inputs.diastolicBp() : 80;
            if (sys > 140 || dia > 90) {
                majorReasons.add("Gestational hypertension (BP > 140/90): " + sys + "/" + dia + " mmHg");
            } else if ((sys >= 130 && sys <= 140) || (dia >= 85 && dia <= 90)) {
                moderateReasons.add("Borderline elevated blood pressure: " + sys + "/" + dia + " mmHg");
            }
        }

        // 4. Previous C-section
        if (inputs.previousCSection()) {
            majorReasons.add("Previous Caesarean section on record");
        }

        // 5. Previous stillbirth
        if (inputs.previousStillbirth()) {
            majorReasons.add("History of previous stillbirth or bad obstetric history");
        }

        // 6. Rh-Negative blood group (moderate factor)
        if (inputs.bloodGroup() != null && inputs.bloodGroup().contains("-")) {
            moderateReasons.add("Rh-negative maternal blood group: " + inputs.bloodGroup());
        }

        List<String> combinedReasons = new ArrayList<>(majorReasons);
        boolean isHighRisk = false;

        if (!majorReasons.isEmpty()) {
            isHighRisk = true;
        } else if (moderateReasons.size() >= 2) {
            isHighRisk = true;
            combinedReasons.addAll(moderateReasons);
        }

        return new AncFlagResult(isHighRisk, combinedReasons, false, false);
    }

    /**
     * Evaluates pregnancy entity on save.
     * When high-risk is detected:
     * 1. Sets high_risk_flag = true and records reasons on pregnancy.
     * 2. Fires immediate internal notification to supervisor (Tier 1 visibility).
     * 3. Inserts into pending_reviews with status='PENDING_CONFIRMATION'.
     *
     * Notice: It does NOT auto-propagate to the top of ASHA priority list until supervisor confirms!
     */
    @Transactional
    public AncFlagResult evaluateHighRisk(Pregnancy pregnancy) {
        Integer motherAge = null;
        if (pregnancy.getMotherMember() != null && pregnancy.getMotherMember().getDateOfBirth() != null) {
            motherAge = Period.between(pregnancy.getMotherMember().getDateOfBirth(), LocalDate.now()).getYears();
        }

        Double hb = pregnancy.getHaemoglobinGdl() != null ? pregnancy.getHaemoglobinGdl().doubleValue() : null;
        String bloodGroup = pregnancy.getBloodGroup();

        AncClinicalInputs inputs = new AncClinicalInputs(
            motherAge,
            hb,
            null, // BP tracked if recorded in visit/checkup
            null,
            false,
            false,
            bloodGroup
        );

        AncFlagResult flagResult = evaluateClinicalTriggers(inputs);

        boolean notificationSent = false;
        boolean pendingReviewCreated = false;

        if (flagResult.isHighRisk()) {
            pregnancy.setHighRiskFlag(true);
            pregnancy.setHighRiskReasons(flagResult.reasons());

            // 1. Immediate internal notification to supervisor
            AshaHead supervisor = null;
            if (pregnancy.getAsha() != null && pregnancy.getAsha().getHead() != null) {
                supervisor = pregnancy.getAsha().getHead();
            }

            if (supervisor != null && notificationRepository != null) {
                Notification notification = new Notification();
                notification.setRecipientHead(supervisor);
                notification.setTitle("High-Risk ANC Detected");
                notification.setBody("High-risk pregnancy detected for mother in village " +
                        (pregnancy.getAsha().getVillage() != null ? pregnancy.getAsha().getVillage() : "N/A") +
                        ". Triggers: " + String.join("; ", flagResult.reasons()));
                notification.setIsRead(false);
                notificationRepository.save(notification);
                notificationSent = true;
                log.info("event=high_risk_anc_notification_sent supervisor_id={}", supervisor.getId());
            }

            // 2. Lands in pending_reviews for supervisor confirmation gate
            if (pendingReviewRepository != null) {
                PendingReview pendingReview = new PendingReview();
                pendingReview.setTableName("pregnancies");
                pendingReview.setRecordId(pregnancy.getId() != null ? pregnancy.getId() : UUID.randomUUID());
                pendingReview.setReason(String.join("; ", flagResult.reasons()));
                pendingReview.setStatus("PENDING_CONFIRMATION");
                if (pregnancy.getAsha() != null && pregnancy.getAsha().getId() != null) {
                    pendingReview.setFlaggedBy(pregnancy.getAsha().getId());
                }
                pendingReviewRepository.save(pendingReview);
                pendingReviewCreated = true;
                log.info("event=high_risk_anc_pending_review_created record_id={}", pendingReview.getRecordId());
            }
        } else {
            pregnancy.setHighRiskFlag(false);
            pregnancy.setHighRiskReasons(null);
        }

        if (pregnancy.getId() != null && pregnancyRepository != null) {
            pregnancyRepository.save(pregnancy);
        }

        return new AncFlagResult(flagResult.isHighRisk(), flagResult.reasons(), notificationSent, pendingReviewCreated);
    }

    /**
     * Supervisor Confirmation Gate.
     * Only when the supervisor explicitly confirms does this propagate to the priority list.
     *
     * @param pendingReviewId The ID of the pending review entry
     * @param supervisorId ID of the supervisor taking the action
     * @param approve true to confirm high-risk propagation, false to reject
     * @return true if confirmed and approved for priority propagation, false if rejected or not found
     */
    @Transactional
    public boolean confirmHighRiskBySupervisor(UUID pendingReviewId, UUID supervisorId, boolean approve) {
        if (pendingReviewRepository == null) return false;

        Optional<PendingReview> reviewOpt = pendingReviewRepository.findById(pendingReviewId);
        if (reviewOpt.isEmpty()) {
            return false;
        }

        PendingReview review = reviewOpt.get();
        if (approve) {
            review.setStatus("CONFIRMED");
            pendingReviewRepository.save(review);
            log.info("event=supervisor_confirmed_high_risk_anc review_id={} supervisor_id={}", pendingReviewId, supervisorId);
            return true;
        } else {
            review.setStatus("REJECTED");
            pendingReviewRepository.save(review);

            // Revert high risk flag on pregnancy entity if supervisor rejects
            if (review.getRecordId() != null && pregnancyRepository != null) {
                pregnancyRepository.findById(review.getRecordId()).ifPresent(p -> {
                    p.setHighRiskFlag(false);
                    p.setHighRiskReasons(null);
                    pregnancyRepository.save(p);
                });
            }
            log.info("event=supervisor_rejected_high_risk_anc review_id={} supervisor_id={}", pendingReviewId, supervisorId);
            return false;
        }
    }
}
