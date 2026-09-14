from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from typing import Optional
from services.gemini_service import gemini_service
from models.schemas import RegisterOcrResponse

router = APIRouter(prefix="/api/ai/register", tags=["register"])

@router.post("/ocr-import", response_model=RegisterOcrResponse)
@router.post("/extract", response_model=RegisterOcrResponse)
async def import_register(
    image: Optional[UploadFile] = File(None),
    photo: Optional[UploadFile] = File(None),
    register_type: Optional[str] = Form(None),
):
    """
    Receives a photo of a handwritten register and extracts tabular data via Gemini Vision (Section 2.6).
    Flags any uncertain or <0.8 confidence fields with needs_review = True.
    """
    upload = image or photo
    if not upload:
        raise HTTPException(status_code=400, detail="Image or photo file must be provided")

    try:
        image_bytes = await upload.read()
        if not image_bytes:
            raise HTTPException(status_code=400, detail="Empty image payload")

        result = gemini_service.extract_register_ocr(image_bytes, register_type=register_type)
        return result
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))
