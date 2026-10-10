package com.ashaai.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneUtilsTest {

    @Test
    @DisplayName("Normalizes 10-digit Indian mobile number starting with 6-9")
    void testNormalizeTenDigits() {
        assertEquals("+919876543211", PhoneUtils.normalizeIndianPhone("9876543211"));
        assertEquals("+918123456789", PhoneUtils.normalizeIndianPhone("8123456789"));
        assertEquals("+917000000000", PhoneUtils.normalizeIndianPhone("7000000000"));
        assertEquals("+916999999999", PhoneUtils.normalizeIndianPhone("6999999999"));
    }

    @Test
    @DisplayName("Normalizes 12-digit number starting with 91")
    void testNormalizeTwelveDigits() {
        assertEquals("+919876543211", PhoneUtils.normalizeIndianPhone("919876543211"));
    }

    @Test
    @DisplayName("Normalizes E.164 format with +91")
    void testNormalizeE164() {
        assertEquals("+919876543211", PhoneUtils.normalizeIndianPhone("+919876543211"));
    }

    @Test
    @DisplayName("Strips whitespace, hyphens, and brackets")
    void testNormalizeWithPunctuation() {
        assertEquals("+919876543211", PhoneUtils.normalizeIndianPhone("+91 98765-43211"));
        assertEquals("+919876543211", PhoneUtils.normalizeIndianPhone("(+91) 98765 43211"));
        assertEquals("+919876543211", PhoneUtils.normalizeIndianPhone(" 9876543211 "));
    }

    @Test
    @DisplayName("Rejects numbers starting with 0-5 or wrong length")
    void testRejectInvalidPhones() {
        assertNull(PhoneUtils.normalizeIndianPhone("5123456789"));
        assertNull(PhoneUtils.normalizeIndianPhone("12345"));
        assertNull(PhoneUtils.normalizeIndianPhone("98765432110")); // 11 digits
        assertNull(PhoneUtils.normalizeIndianPhone("abcdefghij"));
        assertNull(PhoneUtils.normalizeIndianPhone(""));
        assertNull(PhoneUtils.normalizeIndianPhone(null));
        assertNull(PhoneUtils.normalizeIndianPhone("+14155552671")); // non-India country code
    }

    @Test
    @DisplayName("Masks phone number showing only last 4 digits")
    void testMaskPhone() {
        assertEquals("+91******3211", PhoneUtils.maskPhone("+919876543211"));
        assertEquals("******3211", PhoneUtils.maskPhone("9876543211"));
        assertEquals("****", PhoneUtils.maskPhone("123"));
        assertEquals("[EMPTY]", PhoneUtils.maskPhone(null));
    }

    @Test
    @DisplayName("Extracts 10-digit and 12-digit representations")
    void testExtractDigits() {
        assertEquals("9876543211", PhoneUtils.extractTenDigits("+919876543211"));
        assertEquals("919876543211", PhoneUtils.extractTwelveDigits("+919876543211"));
    }
}
