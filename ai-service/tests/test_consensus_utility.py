import pytest
from unittest.mock import patch, MagicMock
from services.provider_client import call_critical_consensus, AIProviderError
from models.schemas import CrossFieldValidationResponse

def test_consensus_agreement():
    """Confirm agreement returns high confidence."""
    with patch('services.provider_client._call_gemini_text') as mock_gemini, \
         patch('services.provider_client._call_hf_sarvam_text') as mock_sarvam:
        
        # Both return "Normal"
        mock_gemini.return_value = "Normal"
        mock_sarvam.return_value = "Normal"
        
        res = call_critical_consensus(prompt="Test prompt", task_type="classification")
        assert res["confidence"] == "high"
        assert res["flag_for_review"] is False
        assert res["result"] == "Normal"

def test_consensus_disagreement():
    """Confirm disagreement returns disputed and both results."""
    with patch('services.provider_client._call_gemini_text') as mock_gemini, \
         patch('services.provider_client._call_hf_sarvam_text') as mock_sarvam:
        
        # Disagree
        mock_gemini.return_value = "SAM"
        mock_sarvam.return_value = "MAM"
        
        res = call_critical_consensus(prompt="Test prompt", task_type="classification")
        assert res["confidence"] == "disputed"
        assert res["flag_for_review"] is True
        assert res["result"]["opinion_1"] == "SAM"
        assert res["result"]["opinion_2"] == "MAM"

def test_consensus_single_source_fallback():
    """Confirm fallback to single source if one provider fails."""
    with patch('services.provider_client._call_gemini_text') as mock_gemini, \
         patch('services.provider_client._call_hf_sarvam_text') as mock_sarvam:
        
        mock_gemini.return_value = "Normal"
        mock_sarvam.side_effect = Exception("Sarvam timeout")
        
        res = call_critical_consensus(prompt="Test prompt", task_type="classification")
        assert res["confidence"] == "single_source"
        assert res["flag_for_review"] is False
        assert res["result"] == "Normal"
        
def test_consensus_all_fail():
    """Confirm AIProviderError if all providers fail."""
    with patch('services.provider_client._call_gemini_text') as mock_gemini, \
         patch('services.provider_client._call_hf_sarvam_text') as mock_sarvam:
        
        mock_gemini.side_effect = Exception("Gemini timeout")
        mock_sarvam.side_effect = Exception("Sarvam timeout")
        
        with pytest.raises(AIProviderError):
            call_critical_consensus(prompt="Test prompt", task_type="classification")
