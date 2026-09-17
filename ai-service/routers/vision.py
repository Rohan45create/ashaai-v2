from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Query
from typing import Literal, Optional
from services.gemini_service import gemini_service
from models.schemas import MuacGradingResponse
from services.provider_client import translate_report

router = APIRouter(prefix="/api/ai/vision", tags=["vision"])

@router.post("/muac-grading", response_model=MuacGradingResponse)
@router.post("/muac-grade", response_model=MuacGradingResponse)
async def grade_muac(
    image: Optional[UploadFile] = File(None),
    photo: Optional[UploadFile] = File(None),
    age: Optional[str] = Form(None),
    gender: Optional[str] = Form(None),
    height: Optional[str] = Form(None),
    weight: Optional[str] = Form(None)
):
    """
    Receives a photo of a child or MUAC tape and uses Gemini Vision to grade it
    against 115mm (SAM) / 125mm (MAM) / above (Normal) thresholds.
    """
    upload = image or photo
    if not upload:
        raise HTTPException(status_code=400, detail="Image or photo file must be provided")

    try:
        image_bytes = await upload.read()
        if not image_bytes:
            raise HTTPException(status_code=400, detail="Empty image payload")
        result = gemini_service.grade_muac_photo(
            image_data=image_bytes, 
            age=age, 
            gender=gender, 
            height=height, 
            weight=weight
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/translate-report", response_model=MuacGradingResponse)
async def translate_malnutrition_report(
    report: MuacGradingResponse,
    lang: Literal["hi", "mr"] = Query(..., description="Target language: hi=Hindi, mr=Marathi"),
):
    """Translate the text fields of a confirmed malnutrition report to Hindi or Marathi.

    This endpoint is compute-only — nothing is read from or written to the database.
    The English report remains the single source of truth; translation is generated
    on demand for display purposes only.

    Provider: Single explicit Groq call via translate_report() in provider_client.
    Not routed through call_text() or call_translation() — see provider_client.py
    docstring for rationale.
    """
    try:
        translated = translate_report(report=report, target_lang=lang)
        return translated
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Translation unavailable: {e}")
