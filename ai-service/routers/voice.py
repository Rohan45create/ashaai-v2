from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from typing import Optional
from services.gemini_service import gemini_service
from models.schemas import VoiceExtractionResponse

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

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
