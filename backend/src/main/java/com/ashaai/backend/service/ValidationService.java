package com.ashaai.backend.service;

import com.ashaai.backend.entity.EditHistory;
import com.ashaai.backend.repository.EditHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Smart Validation Layer 1 (instant field rules & Verhoeff checksum).
 * Adheres strictly to docs/AI_FEATURES_WIRING_PROMPT_FINAL.md Section 2.8,
 * docs/ARCHITECTURE.md, and docs/RULES.md.
 *
 * Direct algorithm implementation — no external API or cloud calls.
 * "Always warns, never blocks -- override requires a documented reason, written to edit_history."
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final EditHistoryRepository editHistoryRepository;

    public ValidationService(EditHistoryRepository editHistoryRepository) {
        this.editHistoryRepository = editHistoryRepository;
    }

    public record ValidationWarning(
        String field,
        String value,
        String message
    ) {}

    public record ValidationResult(
        boolean isValid,
        List<ValidationWarning> warnings
    ) {}

    // ==========================================
    // VERHOEFF CHECKSUM ALGORITHM (Direct Implementation)
    // ==========================================

    private static final int[][] D_TABLE = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
        {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
        {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
        {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
        {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
        {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
        {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
        {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
        {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    private static final int[][] P_TABLE = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
        {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
        {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
        {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
        {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
        {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
        {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    private static final int[] INV_TABLE = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};

    /**
     * Validates an input number using the Verhoeff checksum algorithm.
     */
    public static boolean validateVerhoeff(String numStr) {
        if (numStr == null || numStr.trim().isEmpty()) {
            return false;
        }

        String cleaned = numStr.replaceAll("\\s+", "");
        int c = 0;
        int len = cleaned.length();

        for (int i = 0; i < len; i++) {
            char ch = cleaned.charAt(len - 1 - i);
            if (!Character.isDigit(ch)) {
                return false;
            }
            int digit = ch - '0';
            c = D_TABLE[c][P_TABLE[i % 8][digit]];
        }

        return c == 0;
    }

    /**
     * Calculates the Verhoeff check digit for a given numerical string.
     */
    public static int generateVerhoeffCheckDigit(String numStr) {
        if (numStr == null || numStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Number string cannot be empty.");
        }

        String cleaned = numStr.replaceAll("\\s+", "");
        int c = 0;
        int len = cleaned.length();

        for (int i = 0; i < len; i++) {
            char ch = cleaned.charAt(len - 1 - i);
            if (!Character.isDigit(ch)) {
                throw new IllegalArgumentException("Invalid digit: " + ch);
            }
            int digit = ch - '0';
            c = D_TABLE[c][P_TABLE[(i + 1) % 8][digit]];
        }

        return INV_TABLE[c];
    }

    /**
     * Verifies that Aadhaar is strictly 12 digits and satisfies the Verhoeff checksum.
     */
    public static boolean validateAadhaar(String aadhaar) {
        if (aadhaar == null) return false;
        String cleaned = aadhaar.replaceAll("\\s+", "");
        if (cleaned.length() != 12) return false;
        return validateVerhoeff(cleaned);
    }

    // ==========================================
    // FIELD VALIDATION RULES (Section 2.8)
    // ==========================================

    /**
     * Validate Date of Birth (must not be in the future).
     */
    public static boolean validateDob(LocalDate dob, LocalDate asOfDate) {
        if (dob == null) return false;
        LocalDate reference = asOfDate != null ? asOfDate : LocalDate.now();
        return !dob.isAfter(reference);
    }

    /**
     * Validate Age (0-120 years).
     */
    public static boolean validateAge(Integer age) {
        if (age == null) return false;
        return age >= 0 && age <= 120;
    }

    /**
     * Validate Weight (0.5kg - 300.0kg).
     */
    public static boolean validateWeight(BigDecimal weightKg) {
        if (weightKg == null) return false;
        double w = weightKg.doubleValue();
        return w >= 0.5 && w <= 300.0;
    }

    /**
     * Validate Haemoglobin (1.0 - 25.0 g/dL).
     */
    public static boolean validateHaemoglobin(BigDecimal hb) {
        if (hb == null) return false;
        double val = hb.doubleValue();
        return val >= 1.0 && val <= 25.0;
    }

    /**
     * Evaluates all Layer 1 rules on a composite record.
     * Generates warnings if any rule fails.
     */
    public ValidationResult validateLayer1(
        LocalDate dob,
        Integer age,
        String aadhaar,
        BigDecimal weightKg,
        BigDecimal haemoglobinGdl,
        LocalDate asOfDate
    ) {
        List<ValidationWarning> warnings = new ArrayList<>();

        if (dob != null && !validateDob(dob, asOfDate)) {
            warnings.add(new ValidationWarning(
                "dateOfBirth",
                dob.toString(),
                "Date of birth cannot be in the future."
            ));
        }

        if (age != null && !validateAge(age)) {
            warnings.add(new ValidationWarning(
                "age",
                String.valueOf(age),
                "Age must be between 0 and 120 years."
            ));
        }

        if (aadhaar != null && !validateAadhaar(aadhaar)) {
            warnings.add(new ValidationWarning(
                "aadhaar",
                "[MASKED]",
                "Aadhaar number fails 12-digit format or Verhoeff checksum validation."
            ));
        }

        if (weightKg != null && !validateWeight(weightKg)) {
            warnings.add(new ValidationWarning(
                "currentWeightKg",
                weightKg.toString(),
                "Weight must be between 0.5 kg and 300.0 kg."
            ));
        }

        if (haemoglobinGdl != null && !validateHaemoglobin(haemoglobinGdl)) {
            warnings.add(new ValidationWarning(
                "haemoglobinGdl",
                haemoglobinGdl.toString(),
                "Haemoglobin must be between 1.0 g/dL and 25.0 g/dL."
            ));
        }

        return new ValidationResult(warnings.isEmpty(), warnings);
    }

    /**
     * Records an override to edit_history per Section 2.8:
     * "Always warns, never blocks -- override requires a documented reason, written to edit_history."
     */
    public EditHistory recordValidationOverride(
        UUID recordId,
        String tableName,
        String fieldName,
        String oldValue,
        String newValue,
        String documentedReason,
        UUID editedBy
    ) {
        if (documentedReason == null || documentedReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Override requires a documented reason.");
        }

        EditHistory history = new EditHistory();
        history.setRecordId(recordId);
        history.setTableName(tableName);
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setReason(documentedReason.trim());
        history.setEditedBy(editedBy);

        EditHistory saved = editHistoryRepository != null ? editHistoryRepository.save(history) : history;
        log.warn("event=validation_warning_overridden table={} record_id={} field={}",
                tableName, recordId, fieldName);
        return saved;
    }
}
