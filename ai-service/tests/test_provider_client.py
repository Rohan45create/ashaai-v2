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


    @patch("services.provider_client._call_gemini_multimodal")
    @patch("services.provider_client._call_hf_indic_whisper")
    def test_call_multimodal_audio_uses_hf_stt_when_successful(
        self, mock_hf_stt, mock_gemini
    ):
        """Confirm call_multimodal audio uses HF STT when successful without falling back to Gemini."""
        os.environ.pop("NEAR_AI_API_KEY", None)
        os.environ["HF_TOKEN"] = "mock_hf_token"

        mock_hf_stt.return_value = "marathi_transcript_from_whisper"

        result = call_multimodal("Transcribe", b"audio_wav_data", mime_type="audio/wav")

        mock_hf_stt.assert_called_once()
        mock_gemini.assert_not_called()
        self.assertEqual(result, "marathi_transcript_from_whisper")

    @patch("services.provider_client._call_gemini_multimodal")
    @patch("services.provider_client._call_hf_indic_whisper")
    def test_call_multimodal_audio_falls_back_to_gemini_when_hf_stt_fails(
        self, mock_hf_stt, mock_gemini
    ):
        """Confirm call_multimodal audio falls back to Gemini when HF STT fails."""
        from services.provider_client import HFInferenceError

        os.environ.pop("NEAR_AI_API_KEY", None)
        os.environ["HF_TOKEN"] = "mock_hf_token"

        mock_hf_stt.side_effect = HFInferenceError("Endpoint unavailable", status_code=503, short_reason="unavailable")
        mock_gemini.return_value = "gemini_audio_transcript"

        result = call_multimodal("Transcribe", b"audio_wav_data", mime_type="audio/wav")

        mock_hf_stt.assert_called_once()
        mock_gemini.assert_called_once()
        self.assertEqual(result, "gemini_audio_transcript")

    @patch("services.provider_client._call_groq_text")
    @patch("services.provider_client._call_hf_sarvam_text")
    def test_call_text_uses_sarvam_when_successful(
        self, mock_sarvam, mock_groq
    ):
        """Confirm call_text uses Sarvam-30B when successful and does not fall through to Groq."""
        os.environ.pop("NEAR_AI_API_KEY", None)
        os.environ["HF_TOKEN"] = "mock_hf_token"

        mock_sarvam.return_value = "sarvam_text_output"

        result = call_text("Clinical reasoning task")

        mock_sarvam.assert_called_once()
        mock_groq.assert_not_called()
        self.assertEqual(result, "sarvam_text_output")

    @patch("services.provider_client._call_groq_text")
    @patch("services.provider_client._call_hf_sarvam_text")
    def test_call_text_falls_back_to_groq_when_sarvam_fails(
        self, mock_sarvam, mock_groq
    ):
        """Confirm call_text falls back to Groq when Sarvam fails."""
        from services.provider_client import HFInferenceError

        os.environ.pop("NEAR_AI_API_KEY", None)
        os.environ["HF_TOKEN"] = "mock_hf_token"

        mock_sarvam.side_effect = HFInferenceError("Model not supported", status_code=400, short_reason="model_not_supported")
        mock_groq.return_value = "groq_text_output"

        result = call_text("Clinical reasoning task")

        mock_sarvam.assert_called_once()
        mock_groq.assert_called_once()
        self.assertEqual(result, "groq_text_output")

    @patch("services.provider_client._call_groq_text")
    @patch("services.provider_client._call_hf_sarvam_text")
    @patch("services.provider_client._call_hf_indictrans2")
    def test_call_translation_uses_indictrans2_when_successful(
        self, mock_indictrans, mock_sarvam, mock_groq
    ):
        """Confirm call_translation uses IndicTrans2 when successful without falling through."""
        from services.provider_client import call_translation

        os.environ.pop("NEAR_AI_API_KEY", None)
        os.environ["HF_TOKEN"] = "mock_hf_token"

        mock_indictrans.return_value = "marathi_translation_indictrans"

        result = call_translation("Translate hello to Marathi")

        mock_indictrans.assert_called_once()
        mock_sarvam.assert_not_called()
        mock_groq.assert_not_called()
        self.assertEqual(result, "marathi_translation_indictrans")

    @patch("services.provider_client._call_groq_text")
    @patch("services.provider_client._call_hf_sarvam_text")
    @patch("services.provider_client._call_hf_indictrans2")
    def test_call_translation_falls_back_through_sarvam_to_groq(
        self, mock_indictrans, mock_sarvam, mock_groq
    ):
        """Confirm translation falls back: IndicTrans2 (fail) -> Sarvam (fail) -> Groq (success)."""
        from services.provider_client import call_translation, HFInferenceError

        os.environ.pop("NEAR_AI_API_KEY", None)
        os.environ["HF_TOKEN"] = "mock_hf_token"

        mock_indictrans.side_effect = HFInferenceError("404 repo not found", status_code=404, short_reason="repo_not_found")
        mock_sarvam.side_effect = HFInferenceError("400 not supported", status_code=400, short_reason="model_not_supported")
        mock_groq.return_value = "groq_marathi_translation"

        result = call_translation("Translate hello to Marathi")

        mock_indictrans.assert_called_once()
        mock_sarvam.assert_called_once()
        mock_groq.assert_called_once()
        self.assertEqual(result, "groq_marathi_translation")

    def test_provider_chain_tracker_formatting(self):
        """Verify ProviderChainTracker formats chain output strings cleanly."""
        from services.provider_client import ProviderChainTracker

        tracker = ProviderChainTracker("stt")
        tracker.record_skip("near_ai", "no_key")
        tracker.record_failure("indic_whisper", model="openai/whisper-large-v3", status_code=404, exc_type="HFInferenceError", reason="model_not_deployed")
        tracker.record_success("gemini", "gemini-3.5-flash-lite")

        chain_str = tracker.log_chain()
        self.assertEqual(
            chain_str,
            "stt: near_ai=skipped(no_key) -> indic_whisper=FAILED(404 model_not_deployed) -> gemini=OK"
        )


if __name__ == "__main__":
    unittest.main()

