from fastapi import APIRouter, HTTPException
from services.gemini_service import gemini_service
from models.schemas import (
    ChatRequest,
    ChatResponse,
    CrossFieldValidationRequest,
    CrossFieldValidationResponse,
    TranslationRequest,
    TranslationResponse,
)

router = APIRouter(prefix="/api/ai", tags=["text-reasoning"])

@router.post("/chat", response_model=ChatResponse)
async def chat_endpoint(payload: ChatRequest):
    """
    Ask AshaAI chatbot endpoint (Section 2.7).
    Answers clinical queries using static Indian health protocol guidelines.
    Cached for 24 hours. Uses call_text() (near.ai primary, Groq fallback).
    """
    try:
        response = gemini_service.ask_asha_ai(
            message=payload.message,
            language=payload.language or "en",
            conversation_history=payload.conversation_history,
        )
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/validation/cross-field", response_model=CrossFieldValidationResponse)
async def cross_field_validation_endpoint(payload: CrossFieldValidationRequest):
    """
    Smart Validation Layer 2 cross-field audit endpoint (Section 2.9).
    Audits saved record for clinical and chronological anomalies using call_text().
    """
    try:
        response = gemini_service.validate_cross_field(
            entity_type=payload.entity_type,
            record=payload.record,
        )
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/translate", response_model=TranslationResponse)
async def translate_endpoint(payload: TranslationRequest):
    """
    Multilingual Survey Builder translation endpoint (Section 2.13).
    Translates survey question text into English, Marathi, and Hindi using call_text().
    """
    try:
        response = gemini_service.translate_survey_text(
            text=payload.text,
            source_lang=payload.source_lang,
        )
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
