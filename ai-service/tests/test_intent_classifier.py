import unittest
from unittest.mock import patch
from services.intent_classifier import (
    IntentType,
    classify_intent,
    classify_intent_heuristic
)


class TestIntentClassifier(unittest.TestCase):

    def test_heuristic_fill_survey_english(self):
        result = classify_intent_heuristic("I want to fill the maternal health survey")
        self.assertEqual(result.intent, IntentType.FILL_SURVEY)
        self.assertGreater(result.confidence, 0.7)
        self.assertEqual(result.detected_survey_name, "Maternal Health (ANC)")

    def test_heuristic_fill_survey_hindi(self):
        result = classify_intent_heuristic("मुझे नया सर्वेक्षण भरना है")
        self.assertEqual(result.intent, IntentType.FILL_SURVEY)

    def test_heuristic_fill_survey_marathi(self):
        result = classify_intent_heuristic("नवीन बाल पोषण सर्वेक्षण भरणे सुरू करा")
        self.assertEqual(result.intent, IntentType.FILL_SURVEY)
        self.assertEqual(result.detected_survey_name, "Child Growth & Nutrition")

    def test_heuristic_export_report_english(self):
        result = classify_intent_heuristic("Export last month's vaccination report as PDF")
        self.assertEqual(result.intent, IntentType.EXPORT_REPORT)
        self.assertGreater(result.confidence, 0.7)
        self.assertEqual(result.detected_report_type, "vaccination_due")
        self.assertEqual(result.detected_date_range, "last_month")

    def test_heuristic_export_report_hindi(self):
        result = classify_intent_heuristic("मासिक रिपोर्ट पीडीएफ डाउनलोड करो")
        self.assertEqual(result.intent, IntentType.EXPORT_REPORT)
        self.assertEqual(result.detected_report_type, "monthly_summary")

    def test_heuristic_export_report_marathi(self):
        result = classify_intent_heuristic("मागील महिन्याचा अहवाल पीडीएफ द्या")
        self.assertEqual(result.intent, IntentType.EXPORT_REPORT)

    def test_heuristic_unknown_chitchat(self):
        result = classify_intent_heuristic("Hello good morning sister")
        self.assertEqual(result.intent, IntentType.UNKNOWN)

    @patch("services.intent_classifier.call_text")
    def test_llm_classification_fill_survey(self, mock_call_text):
        mock_call_text.return_value = """```json
{
  "intent": "FILL_SURVEY",
  "confidence": 0.95,
  "detected_survey_name": "ANC Follow-up",
  "detected_member_name": "Sunita Devi",
  "reasoning": "User explicitly asked to enter ANC data for Sunita"
}
```"""
        result = classify_intent("Sunita Devi ki ANC survey bharni hai")
        self.assertEqual(result.intent, IntentType.FILL_SURVEY)
        self.assertEqual(result.confidence, 0.95)
        self.assertEqual(result.detected_survey_name, "ANC Follow-up")
        self.assertEqual(result.detected_member_name, "Sunita Devi")

    @patch("services.intent_classifier.call_text")
    def test_llm_classification_export_report(self, mock_call_text):
        mock_call_text.return_value = """{
  "intent": "EXPORT_REPORT",
  "confidence": 0.92,
  "detected_report_type": "immunization_summary",
  "detected_date_range": "2026-08",
  "reasoning": "User requested export of immunization records"
}"""
        result = classify_intent("Download immunization report for August")
        self.assertEqual(result.intent, IntentType.EXPORT_REPORT)
        self.assertEqual(result.confidence, 0.92)
        self.assertEqual(result.detected_report_type, "immunization_summary")

    @patch("services.intent_classifier.call_text")
    def test_llm_failure_falls_back_to_heuristic(self, mock_call_text):
        mock_call_text.side_effect = Exception("API connection timed out")
        # When LLM fails, must smoothly fallback to heuristic without raising error
        result = classify_intent("Export immunization report PDF")
        self.assertEqual(result.intent, IntentType.EXPORT_REPORT)
        self.assertIn("Heuristic fallback", result.reasoning)


if __name__ == "__main__":
    unittest.main()
