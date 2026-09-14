"""Unit tests for text reasoning AI features:
1. Ask AshaAI chatbot (Section 2.7 - static health knowledge and 24h cache)
2. Smart Validation Layer 2 (Section 2.9 - cross-field conflict detection)
3. Multilingual Survey Builder translation (Section 2.13)
"""

import unittest
from unittest.mock import patch
from models.schemas import (
    ChatResponse,
    CrossFieldValidationResponse,
    TranslationResponse,
)
from services.gemini_service import gemini_service, _chat_cache


class TestTextFeatures(unittest.TestCase):

    @patch("services.gemini_service.call_text")
    def test_chatbot_static_knowledge_and_caching(self, mock_text):
        """Chatbot must answer clinical query and cache response for 24h."""
        mock_text.return_value = ChatResponse(
            response="For SAM where MUAC < 115mm, immediately refer the child to the nearest NRC.",
            source="static_health_knowledge",
        )

        # First call hits provider
        res1 = gemini_service.ask_asha_ai("What is the protocol for SAM?", language="en")
        self.assertIn("NRC", res1.response)
        mock_text.assert_called_once()

        # Second identical call within 24h must be served from cache without calling provider again
        mock_text.reset_mock()
        res2 = gemini_service.ask_asha_ai("What is the protocol for SAM?", language="en")
        self.assertEqual(res2.response, res1.response)
        self.assertEqual(res2.source, "cache_24h")
        mock_text.assert_not_called()

    @patch("services.gemini_service.call_critical_consensus")
    def test_cross_field_pregnant_mother_underage(self, mock_consensus):
        """Cross-field check must flag pregnant woman recorded age < 14."""
        mock_consensus.return_value = {
            "result": CrossFieldValidationResponse(
                has_conflict=True,
                conflicts=["Pregnant mother recorded with implausible age 11 (<14 years)."],
                severity="HIGH",
                suggested_action="Supervisor review required",
            ),
            "confidence": "high",
            "flag_for_review": False
        }

        res = gemini_service.validate_cross_field(
            entity_type="pregnancy",
            record={"mother_name": "Anita", "age": 11, "is_pregnant": True},
        )
        self.assertTrue(res.has_conflict)
        self.assertEqual(res.severity, "HIGH")
        self.assertTrue(any("11" in c for c in res.conflicts))

    @patch("services.gemini_service.call_critical_consensus")
    def test_cross_field_anc_chronology_conflict(self, mock_consensus):
        """Cross-field check must flag ANC2 date recorded before ANC1 date."""
        mock_consensus.return_value = {
            "result": CrossFieldValidationResponse(
                has_conflict=False, conflicts=[], severity="LOW"
            ),
            "confidence": "high",
            "flag_for_review": False
        }

        # Deterministic check ensures it gets flagged even if mock returns clean
        res = gemini_service.validate_cross_field(
            entity_type="pregnancy",
            record={
                "mother_name": "Sunita",
                "age": 24,
                "anc1_date": "2026-05-15",
                "anc2_date": "2026-04-10",  # prior to ANC1!
            },
        )
        self.assertTrue(res.has_conflict)
        self.assertEqual(res.severity, "HIGH")
        self.assertTrue(any("ANC2 date" in c for c in res.conflicts))

    @patch("services.gemini_service.call_translation")
    def test_multilingual_survey_translation(self, mock_translation):
        """Survey builder translation must return en, mr, and hi translations."""
        mock_translation.return_value = TranslationResponse(
            en="Child weight in kilograms",
            mr="किलोग्रॅममध्ये बालकाचे वजन",
            hi="किलोग्राम में बच्चे का वजन",
        )

        res = gemini_service.translate_survey_text("Child weight in kilograms", source_lang="en")
        self.assertEqual(res.en, "Child weight in kilograms")
        self.assertEqual(res.mr, "किलोग्रॅममध्ये बालकाचे वजन")
        self.assertEqual(res.hi, "किलोग्राम में बच्चे का वजन")


if __name__ == "__main__":
    unittest.main()
