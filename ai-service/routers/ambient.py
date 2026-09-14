import json
import logging
from typing import Optional
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from services.gemini_service import gemini_service

router = APIRouter(tags=["ambient"])
logger = logging.getLogger(__name__)

@router.websocket("/ws/ambient")
async def websocket_ambient_endpoint(
    websocket: WebSocket,
    token: Optional[str] = None,
    module: Optional[str] = "family_survey",
):
    """
    WebSocket endpoint for real-time Ambient AI suggestions (Section 2.5).
    Receives recognized text chunks + current form state from browser SpeechRecognition.
    Yields non-blocking suggestion chips without auto-filling form data.
    """
    await websocket.accept()
    logger.info("Ambient WebSocket connected for module=%s", module)

    try:
        while True:
            data = await websocket.receive_text()
            try:
                message = json.loads(data)
                text = message.get("text", "")
                form_state = message.get("form_state", {})

                if not text.strip():
                    continue

                response = gemini_service.extract_ambient_suggestions(
                    transcript=text,
                    module=module,
                    current_state=form_state,
                )

                await websocket.send_json(response.model_dump())

            except json.JSONDecodeError:
                await websocket.send_json({"type": "error", "message": "Invalid JSON format"})
            except Exception as e:
                logger.warning("Ambient processing error: %s", e)
                await websocket.send_json({"type": "suggestions", "suggestions": []})

    except WebSocketDisconnect:
        logger.info("Ambient WebSocket disconnected")
    except Exception as e:
        logger.error("Ambient WebSocket unexpected error: %s", e)
