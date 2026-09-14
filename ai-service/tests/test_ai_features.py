"""Unit tests for multimodal AI features:
1. Malnutrition photo grading (Section 2.3 - 115mm/125mm thresholds)
2. Voice dictation (Section 2.4 - multimodal single-call)
3. Register OCR import (Section 2.6 - <0.8 confidence review flag)
4. Ambient AI suggestions (Section 2.5 - non-blocking suggestion chips)
"""

import unittest
from unittest.mock import patch
from models.schemas import (
    MuacGradingResponse,
    VoiceExtractionResponse,
    RegisterOcrResponse,
    RegisterRow,
    AmbientResponse,
    AmbientSuggestion,
)
from services.gemini_service import (
    gemini_service,
    GeminiVoiceExtractionResponse,
    GeminiExtractedField,
    GeminiRegisterOcrResponse,
    GeminiRegisterRow,
)


class TestAIFeatures(unittest.TestCase):

    @patch("services.gemini_service.call_multimodal")
    def test_muac_grading_sam_threshold(self, mock_multimodal):
        """MUAC < 115mm must strictly yield SAM, grade RED, and needs_nrc_referral=True."""
        mock_multimodal.return_value = MuacGradingResponse(
            grade="RED",
            malnutrition_grade="SAM",
            severity_label="Severe (SAM)",
            muac_mm=112.0,
            confidence=92.0,
            visible_signs=["Bilateral pitting edema", "Severe wasting"],
            recommendation="Immediate referral to NRC",
            needs_nrc_referral=True,
            explanation="MUAC is 112mm, well below 115mm threshold",
        )

        res = gemini_service.grade_muac_photo(b"dummy_image_data")
        self.assertEqual(res.grade, "RED")
        self.assertEqual(res.malnutrition_grade, "SAM")
        self.assertTrue(res.needs_nrc_referral)
        self.assertLess(res.muac_mm, 115.0)

    @patch("services.gemini_service.call_multimodal")
    def test_muac_grading_mam_threshold(self, mock_multimodal):
        """MUAC between 115mm and 124mm must yield MAM, grade YELLOW, needs_nrc_referral=False."""
        mock_multimodal.return_value = MuacGradingResponse(
            grade="YELLOW",
            malnutrition_grade="MAM",
            severity_label="Moderate (MAM)",
            muac_mm=121.0,
            confidence=88.0,
            visible_signs=["Mild muscle loss"],
            recommendation="THR supplementation",
            needs_nrc_referral=False,
            explanation="MUAC is 121mm, in MAM range",
        )

        res = gemini_service.grade_muac_photo(b"dummy_image_data")
        self.assertEqual(res.grade, "YELLOW")
        self.assertEqual(res.malnutrition_grade, "MAM")
        self.assertFalse(res.needs_nrc_referral)
        self.assertTrue(115.0 <= res.muac_mm < 125.0)

    @patch("services.gemini_service.call_multimodal")
    def test_muac_grading_normal_threshold(self, mock_multimodal):
        """MUAC >= 125mm must yield Normal, grade NORMAL, needs_nrc_referral=False."""
        mock_multimodal.return_value = MuacGradingResponse(
            grade="NORMAL",
            malnutrition_grade="Normal",
            severity_label="Normal",
            muac_mm=135.0,
            confidence=95.0,
            visible_signs=[],
            recommendation="Normal growth",
            needs_nrc_referral=False,
            explanation="MUAC is 135mm, in green zone",
        )

        res = gemini_service.grade_muac_photo(b"dummy_image_data")
        self.assertEqual(res.grade, "NORMAL")
        self.assertEqual(res.malnutrition_grade, "Normal")
        self.assertFalse(res.needs_nrc_referral)
        self.assertGreaterEqual(res.muac_mm, 125.0)

    @patch("services.gemini_service.call_multimodal")
    def test_voice_dictation_static_module(self, mock_multimodal):
        """Voice dictation for static module returns transcript and structured fields in one call."""
        mock_multimodal.return_value = GeminiVoiceExtractionResponse(
            transcript="बाळाचे नाव आरव पाटील, वजन आठ किलो, उंची सत्तर सेमी",
            fields=[
                GeminiExtractedField(key="child_name", value="आरव पाटील"),
                GeminiExtractedField(key="weight_kg", value="8.0"),
                GeminiExtractedField(key="height_cm", value="70.0"),
            ],
            fields_detected=3,
        )

        res = gemini_service.extract_voice_multimodal(
            audio_bytes=b"dummy_audio",
            mime_type="audio/webm",
        )
        self.assertIn("आरव", res.transcript)
        self.assertEqual(float(res.fields["weight_kg"]), 8.0)
        self.assertEqual(res.fields_detected, 3)

    @patch("services.gemini_service.call_multimodal")
    def test_voice_dictation_dynamic_survey(self, mock_multimodal):
        """Voice dictation with dynamic form_fields injects custom fields into prompt."""
        custom_fields = '[{"id":"custom_bp","label":"Blood Pressure","type":"text"}]'
        mock_multimodal.return_value = GeminiVoiceExtractionResponse(
            transcript="BP is 120 by 80",
            fields=[GeminiExtractedField(key="custom_bp", value="120/80")],
            fields_detected=1,
        )

        res = gemini_service.extract_voice_multimodal(
            audio_bytes=b"dummy_audio",
            form_fields=custom_fields,
        )
        self.assertEqual(res.fields["custom_bp"], "120/80")

    @patch("services.gemini_service.call_multimodal")
    def test_register_ocr_flags_low_confidence(self, mock_multimodal):
        """Register OCR must set needs_review=True if confidence < 0.8."""
        mock_multimodal.return_value = GeminiRegisterOcrResponse(
            register_type="family_survey",
            target_collection="household_members",
            total_rows_found=2,
            rows=[
                GeminiRegisterRow(
                    fields=[GeminiExtractedField(key="name", value="Suresh")],
                    confidence=0.92,
                    needs_review=False
                ),
                GeminiRegisterRow(
                    fields=[GeminiExtractedField(key="name", value="Ramesh")],
                    confidence=0.65,
                    needs_review=False
                ),
            ],
            confidence=0.8,
        )

        res = gemini_service.extract_register_ocr(b"dummy_register_img")
        self.assertEqual(len(res.rows), 2)
        self.assertFalse(res.rows[0].needs_review)
        self.assertTrue(res.rows[1].needs_review)  # 0.65 < 0.8 must be flagged

    @patch("services.gemini_service.call_text")
    def test_ambient_suggestions(self, mock_text):
        """Ambient AI must return suggestion chips without auto-fill."""
        mock_text.return_value = AmbientResponse(
            type="suggestions",
            suggestions=[
                AmbientSuggestion(field="weight_kg", value=8.5, label="Heard weight 8.5 kg", confidence=0.88)
            ]
        )

        res = gemini_service.extract_ambient_suggestions("Doctor said the weight is eight point five kg")
        self.assertEqual(res.type, "suggestions")
        self.assertEqual(len(res.suggestions), 1)
        self.assertEqual(res.suggestions[0].field, "weight_kg")
        self.assertEqual(res.suggestions[0].value, 8.5)


if __name__ == "__main__":
    unittest.main()
