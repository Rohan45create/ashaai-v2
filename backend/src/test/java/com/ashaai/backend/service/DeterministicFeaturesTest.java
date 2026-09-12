package com.ashaai.backend.service;

import com.ashaai.backend.entity.Asha;
import com.ashaai.backend.entity.AshaHead;
import com.ashaai.backend.entity.Notification;
import com.ashaai.backend.entity.PendingReview;
import com.ashaai.backend.entity.Pregnancy;
import com.ashaai.backend.repository.ChildRepository;
import com.ashaai.backend.repository.EditHistoryRepository;
import com.ashaai.backend.repository.NotificationRepository;
import com.ashaai.backend.repository.PendingReviewRepository;
import com.ashaai.backend.repository.PregnancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying deterministic output for all 5 core health intelligence features:
 * 1. Predictive Risk Engine (Section 2.1)
 * 2. Genetic Risk Augmentation (Section 2.1)
 * 3. ANC High-Risk Flag & Supervisor Gate (Section 2.10)
 * 4. Vaccine Due-Date Engine (Section 2.11)
 * 5. Smart Validation Layer 1 & Verhoeff Checksum (Section 2.8)
 */
class DeterministicFeaturesTest {

    private RiskEngineService riskEngineService;
    private AncFlaggingService ancFlaggingService;
    private VaccineEngineService vaccineEngineService;
    private ValidationService validationService;

    private ChildRepository childRepository;
    private NotificationRepository notificationRepository;
    private PendingReviewRepository pendingReviewRepository;
    private PregnancyRepository pregnancyRepository;
    private EditHistoryRepository editHistoryRepository;

    @BeforeEach
    void setUp() {
        childRepository = mock(ChildRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        pendingReviewRepository = mock(PendingReviewRepository.class);
        pregnancyRepository = mock(PregnancyRepository.class);
        editHistoryRepository = mock(EditHistoryRepository.class);

        riskEngineService = new RiskEngineService(childRepository);
        ancFlaggingService = new AncFlaggingService(notificationRepository, pendingReviewRepository, pregnancyRepository);
        vaccineEngineService = new VaccineEngineService();
        validationService = new ValidationService(editHistoryRepository);
    }

    // =========================================================================
    // FEATURE 1: PREDICTIVE RISK ENGINE
    // =========================================================================

    @Test
    @DisplayName("Feature 1: Predictive Risk Engine - Normal child with no risk factors produces score 0 (LOW)")
    void testRiskEngine_NormalChild() {
        RiskEngineService.RiskFactors factors = new RiskEngineService.RiskFactors(
            "Normal",
            5,    // <= 7 days -> 0 pts
            12.5, // >= 10.0 -> 0 pts
            12,   // >= 6 months -> 0 pts
            0,    // 0 days overdue -> 0 pts
            false,
            false,
            null
        );

        RiskEngineService.RiskEvaluationResult result = riskEngineService.calculateRisk(factors);

        assertEquals(0, result.baseScore());
        assertEquals(0, result.finalScore());
        assertEquals("LOW", result.riskLevel());
        assertEquals("Normal assessment — no acute risk drivers", result.primaryDriver());
        assertFalse(result.geneticAugmentationApplied());
    }

    @Test
    @DisplayName("Feature 1: Predictive Risk Engine - Exact weights accumulation matching Section 2.1 formula")
    void testRiskEngine_ExactFormulaWeights() {
        // MAM (+25)
        // daysSinceVisit = 25 (>21 and <=30 -> +15)
        // motherHb = 7.5 (<8.0 -> +15)
        // breastfeedingCessation = 4 (<6 -> +10)
        // vaccinationGap = 20 (>14 and <=30 -> +8)
        // seasonalStress = true (+10)
        // Expected total = 25 + 15 + 15 + 10 + 8 + 10 = 83
        RiskEngineService.RiskFactors factors = new RiskEngineService.RiskFactors(
            "MAM",
            25,
            7.5,
            4,
            20,
            true,
            false,
            null
        );

        RiskEngineService.RiskEvaluationResult result = riskEngineService.calculateRisk(factors);

        assertEquals(83, result.baseScore());
        assertEquals(83, result.finalScore());
        assertEquals("CRITICAL", result.riskLevel()); // 76-100 is CRITICAL
        assertEquals("Moderate Acute Malnutrition (MAM) detected", result.primaryDriver()); // Highest driver was MAM (25 pts)
    }

    @Test
    @DisplayName("Feature 1: Predictive Risk Engine - SAM forces CRITICAL and score = max(score, 80)")
    void testRiskEngine_SamForcesCriticalAndMinimum80() {
        // SAM with 0 other risk factors
        RiskEngineService.RiskFactors factors = new RiskEngineService.RiskFactors(
            "SAM",
            3,
            12.0,
            10,
            0,
            false,
            false,
            null
        );

        RiskEngineService.RiskEvaluationResult result = riskEngineService.calculateRisk(factors);

        assertEquals(0, result.baseScore()); // Base formula had 0
        assertEquals(80, result.finalScore()); // SAM forces max(0, 80) = 80
        assertEquals("CRITICAL", result.riskLevel());
        assertEquals("Severe Acute Malnutrition (SAM) detected", result.primaryDriver());
    }

    @Test
    @DisplayName("Feature 1: Predictive Risk Engine - Visit delay thresholds step up predictably")
    void testRiskEngine_VisitDelayThresholds() {
        int[] visitDays = {5, 10, 18, 25, 35};
        int[] expectedPoints = {0, 5, 10, 15, 20};

        for (int i = 0; i < visitDays.length; i++) {
            RiskEngineService.RiskFactors factors = new RiskEngineService.RiskFactors(
                "Normal", visitDays[i], 12.0, 10, 0, false, false, null
            );
            RiskEngineService.RiskEvaluationResult result = riskEngineService.calculateRisk(factors);
            assertEquals(expectedPoints[i], result.baseScore(), "Failed at days: " + visitDays[i]);
        }
    }

    // =========================================================================
    // FEATURE 2: GENETIC RISK AUGMENTATION
    // =========================================================================

    @Test
    @DisplayName("Feature 2: Genetic Risk Augmentation - Adds exactly +15 as separate step and logs sibling")
    void testGeneticRiskAugmentation_Adds15AndPromotesBand() {
        // Base score: daysSinceVisit=32 (+20) -> baseScore=20 ("LOW")
        // Sibling with SAM exists -> +15 -> finalScore=35 ("MEDIUM")
        String siblingUuid = UUID.randomUUID().toString();
        RiskEngineService.RiskFactors factors = new RiskEngineService.RiskFactors(
            "Normal",
            32,
            12.0,
            12,
            0,
            false,
            true, // Sibling has SAM/MAM
            siblingUuid
        );

        RiskEngineService.RiskEvaluationResult result = riskEngineService.calculateRisk(factors);

        assertEquals(20, result.baseScore());
        assertEquals(35, result.finalScore());
        assertEquals("MEDIUM", result.riskLevel()); // Promoted from LOW (20) to MEDIUM (35)
        assertTrue(result.geneticAugmentationApplied());
        assertEquals(siblingUuid, result.triggeringSibling());
    }

    @Test
    @DisplayName("Feature 2: Genetic Risk Augmentation - High score caps strictly at 100")
    void testGeneticRiskAugmentation_CapsAt100() {
        // Base score = 92
        // +15 = 107 -> must cap at 100
        RiskEngineService.RiskFactors factors = new RiskEngineService.RiskFactors(
            "MAM", // 25
            35,    // 20
            7.0,   // 15
            3,     // 10
            35,    // 15
            true,  // 10 -> sum = 95
            true,  // Sibling has MAM
            "SIB-101"
        );

        RiskEngineService.RiskEvaluationResult result = riskEngineService.calculateRisk(factors);

        assertEquals(95, result.baseScore());
        assertEquals(100, result.finalScore()); // Capped at 100
        assertEquals("CRITICAL", result.riskLevel());
        assertTrue(result.geneticAugmentationApplied());
    }

    // =========================================================================
    // FEATURE 3: ANC HIGH-RISK FLAG & SUPERVISOR GATE
    // =========================================================================

    @Test
    @DisplayName("Feature 3: ANC High-Risk Flag - Mother age < 18 or > 35 triggers immediate high-risk")
    void testAncFlagging_MaternalAgeTrigger() {
        AncFlaggingService.AncClinicalInputs teenagePregnancy = new AncFlaggingService.AncClinicalInputs(
            16, 12.0, 115, 75, false, false, "O+"
        );
        AncFlaggingService.AncFlagResult teenResult = ancFlaggingService.evaluateClinicalTriggers(teenagePregnancy);
        assertTrue(teenResult.isHighRisk());
        assertTrue(teenResult.reasons().get(0).contains("High-risk maternal age (<18 or >35): 16"));

        AncFlaggingService.AncClinicalInputs advancedAge = new AncFlaggingService.AncClinicalInputs(
            38, 12.0, 115, 75, false, false, "O+"
        );
        AncFlaggingService.AncFlagResult ageResult = ancFlaggingService.evaluateClinicalTriggers(advancedAge);
        assertTrue(ageResult.isHighRisk());
        assertTrue(ageResult.reasons().get(0).contains("High-risk maternal age (<18 or >35): 38"));
    }

    @Test
    @DisplayName("Feature 3: ANC High-Risk Flag - Severe Anaemia (Hb < 8.0) and Hypertension (BP > 140/90) trigger")
    void testAncFlagging_HbAndBpTriggers() {
        AncFlaggingService.AncClinicalInputs severeAnaemia = new AncFlaggingService.AncClinicalInputs(
            25, 7.2, 120, 80, false, false, "B+"
        );
        AncFlaggingService.AncFlagResult hbResult = ancFlaggingService.evaluateClinicalTriggers(severeAnaemia);
        assertTrue(hbResult.isHighRisk());
        assertTrue(hbResult.reasons().get(0).contains("Severe maternal anaemia (Hb < 8.0 g/dL): 7.2"));

        AncFlaggingService.AncClinicalInputs hypertension = new AncFlaggingService.AncClinicalInputs(
            25, 12.0, 145, 95, false, false, "A+"
        );
        AncFlaggingService.AncFlagResult bpResult = ancFlaggingService.evaluateClinicalTriggers(hypertension);
        assertTrue(bpResult.isHighRisk());
        assertTrue(bpResult.reasons().get(0).contains("Gestational hypertension (BP > 140/90): 145/95"));
    }

    @Test
    @DisplayName("Feature 3: ANC High-Risk Flag - Two moderate factors together trigger high risk")
    void testAncFlagging_TwoModerateFactorsTrigger() {
        // Moderate anaemia (Hb 9.5) + Rh-Negative ("A-") -> 2 moderate factors
        AncFlaggingService.AncClinicalInputs moderateFactors = new AncFlaggingService.AncClinicalInputs(
            25, 9.5, 120, 80, false, false, "A-"
        );
        AncFlaggingService.AncFlagResult result = ancFlaggingService.evaluateClinicalTriggers(moderateFactors);
        assertTrue(result.isHighRisk());
        assertEquals(2, result.reasons().size());
    }

    @Test
    @DisplayName("Feature 3: ANC High-Risk Flag - Supervisor Confirmation Gate (pending_reviews and approval)")
    void testAncFlagging_SupervisorConfirmationGate() {
        AshaHead head = new AshaHead();
        head.setName("Supervisor Sunita");

        Asha asha = new Asha();
        asha.setName("Worker Lata");
        asha.setVillage("Shirur");
        asha.setHead(head);

        Pregnancy pregnancy = new Pregnancy();
        pregnancy.setAsha(asha);
        pregnancy.setHaemoglobinGdl(new BigDecimal("7.1")); // Trigger severe anaemia

        // Evaluate high-risk on pregnancy save
        AncFlaggingService.AncFlagResult result = ancFlaggingService.evaluateHighRisk(pregnancy);

        assertTrue(result.isHighRisk());
        assertTrue(pregnancy.getHighRiskFlag());

        // Verify supervisor notification created
        verify(notificationRepository, times(1)).save(any(Notification.class));

        // Verify pending review created in pending_reviews table
        ArgumentCaptor<PendingReview> reviewCaptor = ArgumentCaptor.forClass(PendingReview.class);
        verify(pendingReviewRepository, times(1)).save(reviewCaptor.capture());
        PendingReview capturedReview = reviewCaptor.getValue();
        assertEquals("pregnancies", capturedReview.getTableName());
        assertEquals("PENDING_CONFIRMATION", capturedReview.getStatus());

        // Test Supervisor Confirmation: Approve
        UUID reviewId = UUID.randomUUID();
        when(pendingReviewRepository.findById(reviewId)).thenReturn(Optional.of(capturedReview));

        boolean approved = ancFlaggingService.confirmHighRiskBySupervisor(reviewId, UUID.randomUUID(), true);
        assertTrue(approved);
        assertEquals("CONFIRMED", capturedReview.getStatus());

        // Test Supervisor Confirmation: Reject
        boolean rejected = ancFlaggingService.confirmHighRiskBySupervisor(reviewId, UUID.randomUUID(), false);
        assertFalse(rejected);
        assertEquals("REJECTED", capturedReview.getStatus());
    }

    // =========================================================================
    // FEATURE 4: VACCINE DUE-DATE ENGINE
    // =========================================================================

    @Test
    @DisplayName("Feature 4: Vaccine Due-Date Engine - NVHCP schedule count and date arithmetic")
    void testVaccineEngine_NvhcpScheduleArithmetic() {
        LocalDate dob = LocalDate.of(2026, 1, 1);
        List<VaccineEngineService.VaccineScheduleDefinition> defs = vaccineEngineService.getScheduleDefinitions();
        assertTrue(defs.size() >= 18, "NVHCP schedule must contain comprehensive UIP vaccines");

        // BCG: due at birth (2026-01-01)
        // OPV-1: due at 6 weeks (42 days -> 2026-02-12)
        // MR-1: due at 9 months (270 days -> 2026-09-28)
        Map<String, LocalDate> given = new HashMap<>();
        given.put("BCG", LocalDate.of(2026, 1, 2));

        LocalDate asOfDate = LocalDate.of(2026, 2, 20); // 8 days after OPV-1 due date
        List<VaccineEngineService.VaccineEvaluation> evals = vaccineEngineService.evaluateSchedule(dob, given, asOfDate);

        // BCG: GIVEN
        VaccineEngineService.VaccineEvaluation bcg = evals.stream()
            .filter(e -> e.vaccineCode().equals("BCG")).findFirst().orElseThrow();
        assertEquals(VaccineEngineService.VaccineStatus.GIVEN, bcg.status());

        // OPV-1: OVERDUE by 8 days
        VaccineEngineService.VaccineEvaluation opv1 = evals.stream()
            .filter(e -> e.vaccineCode().equals("OPV_1")).findFirst().orElseThrow();
        assertEquals(VaccineEngineService.VaccineStatus.OVERDUE, opv1.status());
        assertEquals(8, opv1.daysDifference());
        assertTrue(opv1.surfaceOnPriorityList());
    }

    @Test
    @DisplayName("Feature 4: Vaccine Due-Date Engine - Within 3 days of due triggers DUE_SOON notification")
    void testVaccineEngine_DueSoonNotificationWindow() {
        LocalDate dob = LocalDate.of(2026, 1, 1);
        // OPV-1 due date is 2026-02-12 (42 days from birth)
        // Evaluate on 2026-02-10 (2 days before due date)
        LocalDate asOfDate = LocalDate.of(2026, 2, 10);

        List<VaccineEngineService.VaccineEvaluation> evals = vaccineEngineService.evaluateSchedule(dob, Map.of(), asOfDate);
        VaccineEngineService.VaccineEvaluation opv1 = evals.stream()
            .filter(e -> e.vaccineCode().equals("OPV_1")).findFirst().orElseThrow();

        assertEquals(VaccineEngineService.VaccineStatus.DUE_SOON, opv1.status());
        assertTrue(opv1.triggerNotification());
        assertEquals(2, opv1.daysDifference()); // 2 days until due
    }

    @Test
    @DisplayName("Feature 4: Vaccine Due-Date Engine - Correctly computes max vaccination gap days")
    void testVaccineEngine_MaxVaccinationGapDays() {
        LocalDate dob = LocalDate.of(2026, 1, 1);
        // Birth vaccines (BCG, HEP_B_0, OPV_0) given at birth
        Map<String, LocalDate> givenBirthDoses = Map.of(
            "BCG", dob,
            "HEP_B_0", dob,
            "OPV_0", dob
        );
        // OPV-1 due 2026-02-12 (day 42). As of 2026-03-01 -> exactly 17 days overdue
        LocalDate asOfDate = LocalDate.of(2026, 3, 1);

        int maxGapWithBirthGiven = vaccineEngineService.calculateMaxVaccinationGapDays(dob, givenBirthDoses, asOfDate);
        assertEquals(17, maxGapWithBirthGiven);

        // If birth doses were never given, max gap from birth (2026-01-01 to 2026-03-01) is 59 days
        int maxGapNoVaccines = vaccineEngineService.calculateMaxVaccinationGapDays(dob, Map.of(), asOfDate);
        assertEquals(59, maxGapNoVaccines);
    }

    // =========================================================================
    // FEATURE 5: SMART VALIDATION LAYER 1 & VERHOEFF CHECKSUM
    // =========================================================================

    @Test
    @DisplayName("Feature 5: Verhoeff Checksum - Direct implementation validates numbers and detects alterations")
    void testSmartValidation_VerhoeffAlgorithm() {
        // Generate check digit for known number
        String baseNum = "236";
        int checkDigit = ValidationService.generateVerhoeffCheckDigit(baseNum);
        assertEquals(3, checkDigit); // 236 + 3 = 2363 satisfies Verhoeff

        assertTrue(ValidationService.validateVerhoeff("2363"));
        assertFalse(ValidationService.validateVerhoeff("2364")); // Single digit substitution fails
        assertFalse(ValidationService.validateVerhoeff("2633")); // Adjacent transposition fails

        // 12-digit Aadhaar generation and verification
        String aadhaar11 = "98765432109";
        int aadhaarCheckDigit = ValidationService.generateVerhoeffCheckDigit(aadhaar11);
        String validAadhaar = aadhaar11 + aadhaarCheckDigit;

        assertEquals(12, validAadhaar.length());
        assertTrue(ValidationService.validateAadhaar(validAadhaar));

        // Invalid lengths or characters
        assertFalse(ValidationService.validateAadhaar("98765432109")); // 11 digits
        assertFalse(ValidationService.validateAadhaar(validAadhaar + "1")); // 13 digits
        assertFalse(ValidationService.validateAadhaar("98765432109A")); // Non-digit
    }

    @Test
    @DisplayName("Feature 5: Smart Validation Layer 1 - Field boundaries for DOB, Age, Weight, and Hb")
    void testSmartValidation_FieldBoundaries() {
        LocalDate today = LocalDate.of(2026, 5, 1);

        // DOB
        assertTrue(ValidationService.validateDob(LocalDate.of(2026, 4, 30), today));
        assertFalse(ValidationService.validateDob(LocalDate.of(2026, 5, 2), today)); // Future DOB

        // Age (0-120)
        assertTrue(ValidationService.validateAge(0));
        assertTrue(ValidationService.validateAge(120));
        assertFalse(ValidationService.validateAge(-1));
        assertFalse(ValidationService.validateAge(121));

        // Weight (0.5 - 300.0 kg)
        assertTrue(ValidationService.validateWeight(new BigDecimal("0.5")));
        assertTrue(ValidationService.validateWeight(new BigDecimal("300.0")));
        assertFalse(ValidationService.validateWeight(new BigDecimal("0.4")));
        assertFalse(ValidationService.validateWeight(new BigDecimal("300.1")));

        // Haemoglobin (1.0 - 25.0 g/dL)
        assertTrue(ValidationService.validateHaemoglobin(new BigDecimal("1.0")));
        assertTrue(ValidationService.validateHaemoglobin(new BigDecimal("25.0")));
        assertFalse(ValidationService.validateHaemoglobin(new BigDecimal("0.9")));
        assertFalse(ValidationService.validateHaemoglobin(new BigDecimal("25.1")));
    }

    @Test
    @DisplayName("Feature 5: Smart Validation Layer 1 - Warnings generated and documented override recorded")
    void testSmartValidation_WarningsAndOverride() {
        ValidationService.ValidationResult result = validationService.validateLayer1(
            LocalDate.of(2030, 1, 1), // Future DOB -> warning
            135,                     // Age > 120 -> warning
            "123456789012",           // Invalid Verhoeff Aadhaar -> warning
            new BigDecimal("0.2"),    // Weight < 0.5kg -> warning
            new BigDecimal("28.5"),   // Hb > 25.0 -> warning
            LocalDate.of(2026, 1, 1)
        );

        assertFalse(result.isValid());
        assertEquals(5, result.warnings().size());

        // Override requires documented reason and writes to edit_history
        UUID recordId = UUID.randomUUID();
        UUID ashaId = UUID.randomUUID();

        validationService.recordValidationOverride(
            recordId,
            "children",
            "currentWeightKg",
            "0.2",
            "0.2",
            "Verified premature neonate birth weight under special care",
            ashaId
        );

        verify(editHistoryRepository, times(1)).save(any());
    }
}
