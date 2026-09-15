from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from typing import Optional
from services.gemini_service import gemini_service
from models.schemas import MuacGradingResponse

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
