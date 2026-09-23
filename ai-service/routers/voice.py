import structlog
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from typing import Optional
from services.gemini_service import gemini_service
from models.schemas import VoiceExtractionResponse

logger = structlog.get_logger(__name__)

router = APIRouter(prefix="/api/ai/voice", tags=["voice"])

@router.post("/structuring", response_model=VoiceExtractionResponse)
@router.post("/transcribe", response_model=VoiceExtractionResponse)
async def process_voice(
    audio: UploadFile = File(...),
    module_type: Optional[str] = Form(None),
    form_fields: Optional[str] = Form(None),
):
    """
    Receives an audio file, transcribes it and extracts structured form fields
    in a single multimodal call per Section 2.4.
    Supports dynamic schema injection via form_fields.
    Audio is discarded from memory immediately after inference.

    Per RULES.md error-handling conventions: AI provider failures return a
    structured 503 with a user-facing message so the frontend can display
    "Voice transcription unavailable, please enter manually" rather than a
    generic 500.
    """
    try:
        audio_bytes = await audio.read()
        if not audio_bytes:
            raise HTTPException(status_code=400, detail="Empty audio payload")

        mime_type = audio.content_type or "audio/webm"
        # 1-call multimodal transcription and structured extraction
        structured_data = gemini_service.extract_voice_multimodal(
            audio_bytes=audio_bytes,
            mime_type=mime_type,
            form_fields=form_fields,
        )

        # Explicitly discard audio from memory
        del audio_bytes

        return structured_data

    except HTTPException:
        raise  # pass through 400 errors unchanged
    except Exception as e:
        err_str = str(e)
        logger.warning("voice_transcription_provider_error", error=err_str[:300])
        # Surface Gemini quota/outage errors as 503 with a user-facing message
        # (RULES.md: "Voice transcription unavailable, please enter manually")
        is_service_error = any(
            code in err_str
            for code in ("503", "UNAVAILABLE", "429", "RESOURCE_EXHAUSTED", "quota", "high demand")
        )
        if is_service_error:
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "VOICE_SERVICE_UNAVAILABLE",
                    "message": "Voice transcription is temporarily unavailable due to high demand. Please enter the details manually or try again in a few moments.",
                },
            )
        raise HTTPException(status_code=500, detail=str(e))

