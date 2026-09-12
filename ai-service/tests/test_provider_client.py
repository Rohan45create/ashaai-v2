"""Unit tests for provider_client fallback behavior per docs/ARCHITECTURE.md and docs/RULES.md.

Tests confirm:
1. call_multimodal skips near.ai directly to Gemini when NEAR_AI_API_KEY is absent.
2. call_text skips near.ai directly to Groq when NEAR_AI_API_KEY is absent.
3. Both functions fall back to their designated provider when near.ai raises an error.
4. Both functions use near.ai when NEAR_AI_API_KEY is present and healthy.
"""

import os
import unittest
from unittest.mock import patch, MagicMock

from services.provider_client import (
    call_multimodal,
    call_text,
    NearAIUnavailableError,
)


class TestProviderClientFallbacks(unittest.TestCase):
    """Verifies that provider_client implements the exact designated fallback chains."""

    def setUp(self):
        # Save original environment
        self.original_env = dict(os.environ)

    def tearDown(self):
        # Restore original environment
        os.environ.clear()
        os.environ.update(self.original_env)

    @patch("services.provider_client._call_gemini_multimodal")
    @patch("services.provider_client._call_near_ai_multimodal")
    def test_call_multimodal_skips_to_gemini_when_near_ai_key_absent(
        self, mock_near_ai, mock_gemini
    ):
        """Confirm call_multimodal skips near.ai and calls Gemini fallback when NEAR_AI_API_KEY is absent."""
        # Ensure NEAR_AI_API_KEY is absent
        os.environ.pop("NEAR_AI_API_KEY", None)

        mock_gemini.return_value = "gemini_multimodal_success"

        prompt = "Assess malnutrition from this MUAC tape image"
        image_bytes = b"fake_jpeg_payload"
        result = call_multimodal(prompt, image_bytes, mime_type="image/jpeg")

        # near.ai must NOT be called at all
        mock_near_ai.assert_not_called()

        # Gemini MUST be called as the designated fallback
        mock_gemini.assert_called_once_with(
            prompt=prompt,
            audio_or_image=image_bytes,
            mime_type="image/jpeg",
            response_schema=None,
        )

        self.assertEqual(result, "gemini_multimodal_success")

    @patch("services.provider_client._call_groq_text")
    @patch("services.provider_client._call_near_ai_text")
    def test_call_text_skips_to_groq_when_near_ai_key_absent(
        self, mock_near_ai, mock_groq
    ):
        """Confirm call_text skips near.ai and calls Groq fallback when NEAR_AI_API_KEY is absent."""
        # Ensure NEAR_AI_API_KEY is absent
        os.environ.pop("NEAR_AI_API_KEY", None)

        mock_groq.return_value = "groq_text_reasoning_success"

        prompt = "Cross-validate child age against reported vaccination schedule"
        result = call_text(prompt)

        # near.ai must NOT be called at all
        mock_near_ai.assert_not_called()

        # Groq MUST be called as the designated fallback
        mock_groq.assert_called_once_with(
            prompt=prompt,
            system_prompt=None,
            response_schema=None,
        )

        self.assertEqual(result, "groq_text_reasoning_success")

    @patch("services.provider_client._call_gemini_multimodal")
    @patch("services.provider_client._call_near_ai_multimodal")
    def test_call_multimodal_falls_back_to_gemini_on_near_ai_error(
        self, mock_near_ai, mock_gemini
    ):
        """Confirm call_multimodal falls back to Gemini if near.ai raises unavailable/credits error."""
        os.environ["NEAR_AI_API_KEY"] = "near_ai_dummy_key"

        mock_near_ai.side_effect = NearAIUnavailableError("Credits exhausted (HTTP 402)")
        mock_gemini.return_value = "gemini_fallback_response"

        result = call_multimodal("Transcribe audio", b"audio_bytes", mime_type="audio/wav")

        mock_near_ai.assert_called_once()
        mock_gemini.assert_called_once()
        self.assertEqual(result, "gemini_fallback_response")

    @patch("services.provider_client._call_groq_text")
    @patch("services.provider_client._call_near_ai_text")
    def test_call_text_falls_back_to_groq_on_near_ai_error(
        self, mock_near_ai, mock_groq
    ):
        """Confirm call_text falls back to Groq if near.ai raises an error."""
        os.environ["NEAR_AI_API_KEY"] = "near_ai_dummy_key"

        mock_near_ai.side_effect = NearAIUnavailableError("Service unavailable (HTTP 503)")
        mock_groq.return_value = "groq_fallback_response"

        result = call_text("Translate text to Marathi")

        mock_near_ai.assert_called_once()
        mock_groq.assert_called_once()
        self.assertEqual(result, "groq_fallback_response")

    @patch("services.provider_client._call_gemini_multimodal")
    @patch("services.provider_client._call_near_ai_multimodal")
    def test_call_multimodal_uses_near_ai_when_healthy(
        self, mock_near_ai, mock_gemini
    ):
        """Confirm call_multimodal uses near.ai and does NOT trigger Gemini when near.ai succeeds."""
        os.environ["NEAR_AI_API_KEY"] = "near_ai_dummy_key"
        mock_near_ai.return_value = "near_ai_multimodal_success"

        result = call_multimodal("Evaluate photo", b"photo_bytes")

        mock_near_ai.assert_called_once()
        mock_gemini.assert_not_called()
        self.assertEqual(result, "near_ai_multimodal_success")

    @patch("services.provider_client._call_groq_text")
    @patch("services.provider_client._call_near_ai_text")
    def test_call_text_uses_near_ai_when_healthy(
        self, mock_near_ai, mock_groq
    ):
        """Confirm call_text uses near.ai and does NOT trigger Groq when near.ai succeeds."""
        os.environ["NEAR_AI_API_KEY"] = "near_ai_dummy_key"
        mock_near_ai.return_value = "near_ai_text_success"

        result = call_text("Evaluate question")

        mock_near_ai.assert_called_once()
        mock_groq.assert_not_called()
        self.assertEqual(result, "near_ai_text_success")


if __name__ == "__main__":
    unittest.main()
