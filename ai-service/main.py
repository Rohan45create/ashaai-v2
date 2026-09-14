"""Internal FastAPI service for AshaAI's AI capabilities.

Routes are proxied by Spring Boot and internal channels;
the frontend communicates through Spring Boot REST APIs.
"""

from dotenv import load_dotenv
load_dotenv()

from fastapi import FastAPI
from routers import voice, vision, register, ambient, text_features, agent

app = FastAPI(title="AshaAI AI Service", version="0.1.0")

app.include_router(voice.router)
app.include_router(vision.router)
app.include_router(register.router)
app.include_router(ambient.router)
app.include_router(text_features.router)
app.include_router(agent.router)

from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
import logging

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc):
    body = await request.body()
    logging.error(f"Validation Error. Body: {body}. Error: {exc.errors()}")
    return JSONResponse(status_code=422, content={"detail": exc.errors()})

from google.genai.errors import APIError, ClientError

@app.exception_handler(APIError)
@app.exception_handler(ClientError)
async def genai_exception_handler(request, exc):
    logging.error(f"GenAI API Error: {exc}")
    status_code = getattr(exc, 'code', 503)
    if status_code not in [400, 401, 403, 404, 429, 500, 503]:
        status_code = 503
    return JSONResponse(
        status_code=status_code,
        content={"detail": {"message": "AI Provider is currently unavailable due to high demand. Please try again later.", "code": status_code}}
    )

@app.get("/health", tags=["health"])
def health() -> dict[str, str]:
    """Return process health without exposing configuration or secrets."""
    return {"status": "UP"}
