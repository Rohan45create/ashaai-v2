package com.ashaai.backend.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhoneUtils {

    // Indian mobile numbers: 10 digits starting with 6, 7, 8, or 9
    private static final Pattern INDIAN_MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");

    private PhoneUtils() {}

    /**
     * Normalizes an Indian phone number to E.164 format (+91XXXXXXXXXX).
     * Accepts:
     *  - 10 digits: "9876543210" -> "+919876543210"
     *  - 12 digits starting with 91: "919876543210" -> "+919876543210"
     *  - E.164 with +91: "+919876543210" -> "+919876543210"
     *  - With common punctuation/whitespace: "+91 98765-43210" -> "+919876543210"
     *
     * Returns null if phone is null or does not represent a valid 10-digit Indian mobile.
     */
    public static String normalizeIndianPhone(String phone) {
        if (phone == null) {
            return null;
        }

        String trimmed = phone.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Check if starts with a plus
        boolean hasPlus = trimmed.startsWith("+");

        // Strip everything that isn't a digit
        String digits = trimmed.replaceAll("\\D", "");

        if (digits.length() == 10) {
            // Must be a valid Indian mobile number starting with 6-9
            if (INDIAN_MOBILE_PATTERN.matcher(digits).matches()) {
                return "+91" + digits;
            }
            return null;
        }

        if (digits.length() == 12 && digits.startsWith("91")) {
            String tenDigits = digits.substring(2);
            if (INDIAN_MOBILE_PATTERN.matcher(tenDigits).matches()) {
                return "+91" + tenDigits;
            }
            return null;
        }

        return null;
    }

    /**
     * Extracts the 10-digit mobile number from a normalized E.164 (+91XXXXXXXXXX) number.
     */
    public static String extractTenDigits(String normalizedE164) {
        if (normalizedE164 != null && normalizedE164.startsWith("+91") && normalizedE164.length() == 13) {
            return normalizedE164.substring(3);
        }
        return normalizedE164;
    }

    /**
     * Extracts the 12-digit number without plus (91XXXXXXXXXX).
     */
    public static String extractTwelveDigits(String normalizedE164) {
        if (normalizedE164 != null && normalizedE164.startsWith("+")) {
            return normalizedE164.substring(1);
        }
        return normalizedE164;
    }

    /**
     * Masks the phone number so that only the last 4 digits are visible.
     * Example: "+919876543211" -> "+91******3211"
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "[EMPTY]";
        }
        if (phone.length() <= 4) {
            return "****";
        }
        String lastFour = phone.substring(phone.length() - 4);
        String prefix = phone.startsWith("+91") ? "+91" : "";
        return prefix + "******" + lastFour;
    }
}
