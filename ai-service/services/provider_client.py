"""Single reusable client wrapper for AI inference routing in AshaAI.

Provider routing rules per docs/ARCHITECTURE.md and docs/RULES.md:
- call_multimodal:
    Tries near.ai first ONLY IF NEAR_AI_API_KEY is set in environment.
    If not set, or if near.ai raises an unavailable/credits exhausted/connection error,
    falls back to Gemini.
- call_text:
    Tries near.ai first ONLY IF NEAR_AI_API_KEY is set in environment.
    If not set, or if near.ai fails,
    falls back to Groq.

Every feature-specific AI service must route through these two functions.
Direct calls to Gemini, Groq, or near.ai from feature files are strictly prohibited.
"""

from __future__ import annotations

import base64
import os
from typing import Any, Optional, Type, Union

import httpx
import structlog
from google import genai
from google.genai import types
from groq import Groq
from pydantic import BaseModel

logger = structlog.get_logger(__name__)


class AIProviderError(Exception):
    """Base exception for AI provider invocation failures."""
    pass


class NearAIUnavailableError(AIProviderError):
    """Raised when near.ai is unavailable, credits are exhausted, or rate-limited."""
    pass


# Configuration defaults
NEAR_AI_DEFAULT_BASE_URL = "https://cloud-api.near.ai/v1"
NEAR_AI_DEFAULT_TEXT_MODEL = "meta-llama/Llama-3.3-70B-Instruct"
NEAR_AI_DEFAULT_MULTIMODAL_MODEL = "meta-llama/Llama-3.2-11B-Vision-Instruct"
GROQ_DEFAULT_MODEL = "openai/gpt-oss-120b"
GEMINI_DEFAULT_MODEL = "gemini-3.6-flash"          # text / call_text path
GEMINI_MULTIMODAL_DEFAULT_MODEL = "gemini-3.6-flash"  # image+audio / call_multimodal path


def _extract_bytes(audio_or_image: Union[bytes, str]) -> bytes:
    """Normalize audio/image input to raw bytes."""
    if isinstance(audio_or_image, bytes):
        return audio_or_image
    if isinstance(audio_or_image, str):
        if os.path.exists(audio_or_image):
            with open(audio_or_image, "rb") as f:
                return f.read()
        try:
            return base64.b64decode(audio_or_image)
        except Exception:
            return audio_or_image.encode("utf-8")
    raise ValueError(f"Unsupported media type: {type(audio_or_image)}")


def _clean_json_text(text: str) -> str:
    """Strip markdown formatting from model responses to ensure valid JSON."""
    text = text.strip()
    if text.startswith("```json"):
        text = text[len("```json"):].strip()
    elif text.startswith("```"):
        text = text[len("```"):].strip()
    if text.endswith("```"):
        text = text[:-len("```")].strip()
    return text


def _call_near_ai_text(
    prompt: str,
    system_prompt: Optional[str] = None,
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute text inference using near.ai OpenAI-compatible endpoint."""
    api_key = os.getenv("NEAR_AI_API_KEY")
    if not api_key:
        raise NearAIUnavailableError("NEAR_AI_API_KEY is not set.")

    base_url = os.getenv("NEAR_AI_BASE_URL", NEAR_AI_DEFAULT_BASE_URL)
    model = os.getenv("NEAR_AI_MODEL", NEAR_AI_DEFAULT_TEXT_MODEL)
    timeout = float(kwargs.get("timeout", 30.0))

    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})

    payload = {
        "model": model,
        "messages": messages,
        "temperature": kwargs.get("temperature", 0.1),
    }

    try:
        with httpx.Client(timeout=httpx.Timeout(timeout, connect=5.0)) as client:
            resp = client.post(
                f"{base_url.rstrip('/')}/chat/completions",
                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )

        # 402: Credits exhausted, 429: Rate limit, 5xx: Service unavailable
        if resp.status_code in (402, 429, 500, 502, 503, 504) or resp.is_error:
            raise NearAIUnavailableError(
                f"near.ai text error: status={resp.status_code}, response={resp.text}"
            )

        data = resp.json()
        raw_text = data["choices"][0]["message"]["content"]
    except NearAIUnavailableError:
        raise
    except Exception as e:
        raise NearAIUnavailableError(f"near.ai request failed: {e}") from e

    if response_schema:
        return response_schema.model_validate_json(_clean_json_text(raw_text))
    return raw_text


def _call_near_ai_multimodal(
    prompt: str,
    audio_or_image: Union[bytes, str],
    mime_type: str = "image/jpeg",
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute multimodal inference using near.ai OpenAI-compatible endpoint."""
    api_key = os.getenv("NEAR_AI_API_KEY")
    if not api_key:
        raise NearAIUnavailableError("NEAR_AI_API_KEY is not set.")

    base_url = os.getenv("NEAR_AI_BASE_URL", NEAR_AI_DEFAULT_BASE_URL)
    model = os.getenv("NEAR_AI_MULTIMODAL_MODEL", NEAR_AI_DEFAULT_MULTIMODAL_MODEL)
    timeout = float(kwargs.get("timeout", 30.0))

    media_bytes = _extract_bytes(audio_or_image)
    b64_data = base64.b64encode(media_bytes).decode("utf-8")

    if not mime_type.startswith("image/"):
        raise NearAIUnavailableError(
            f"near.ai multimodal model {model} does not support mime_type {mime_type}. Falling back."
        )

    content: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
    content.append({
        "type": "image_url",
        "image_url": {"url": f"data:{mime_type};base64,{b64_data}"},
    })

    payload = {
        "model": model,
        "messages": [{"role": "user", "content": content}],
        "temperature": kwargs.get("temperature", 0.1),
    }

    try:
        with httpx.Client(timeout=httpx.Timeout(timeout, connect=5.0)) as client:
            resp = client.post(
                f"{base_url.rstrip('/')}/chat/completions",
                headers={
                    "Authorization": f"Bearer {api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
            )

        if resp.status_code in (402, 429, 500, 502, 503, 504) or resp.is_error:
            raise NearAIUnavailableError(
                f"near.ai multimodal error: status={resp.status_code}, response={resp.text}"
            )

        data = resp.json()
        raw_text = data["choices"][0]["message"]["content"]
    except NearAIUnavailableError:
        raise
    except Exception as e:
        raise NearAIUnavailableError(f"near.ai multimodal request failed: {e}") from e

    if response_schema:
        return response_schema.model_validate_json(_clean_json_text(raw_text))
    return raw_text


def _call_groq_text(
    prompt: str,
    system_prompt: Optional[str] = None,
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute text inference using Groq (designated fallback for text reasoning)."""
    api_key = os.getenv("GROQ_API_KEY")
    if not api_key:
        logger.warning("groq_api_key_absent_checking_stub_fallback")
        # In testing/stub environments without GROQ key, avoid crash if stub handler requested
        if kwargs.get("allow_stub", False):
            if response_schema:
                return response_schema.model_validate({})
            return "Groq stub response"
        raise ValueError("GROQ_API_KEY is not set in environment.")

    model = os.getenv("GROQ_MODEL", GROQ_DEFAULT_MODEL)
    client = Groq(api_key=api_key)

    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})

    create_kwargs: dict[str, Any] = {
        "model": model,
        "messages": messages,
        "temperature": kwargs.get("temperature", 0.1),
    }
    if response_schema:
        create_kwargs["response_format"] = {"type": "json_object"}

    completion = client.chat.completions.create(**create_kwargs)
    raw_text = completion.choices[0].message.content or ""

    if response_schema:
        return response_schema.model_validate_json(_clean_json_text(raw_text))
    return raw_text


def _call_gemini_text(
    prompt: str,
    system_prompt: Optional[str] = None,
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute text inference using Gemini."""
    import time as _time
    from google.api_core.exceptions import ServiceUnavailable

    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set in environment.")

    client = genai.Client(api_key=api_key)
    model = os.getenv("GEMINI_MODEL", GEMINI_DEFAULT_MODEL)

    contents = []
    if system_prompt:
        contents.append(types.Content(role="user", parts=[types.Part.from_text(text=system_prompt)]))
        contents.append(types.Content(role="model", parts=[types.Part.from_text(text="Understood.")]))
    contents.append(types.Content(role="user", parts=[types.Part.from_text(text=prompt)]))

    config_kwargs: dict[str, Any] = {}
    if response_schema:
        config_kwargs["response_mime_type"] = "application/json"
        config_kwargs["response_schema"] = response_schema

    config = types.GenerateContentConfig(**config_kwargs) if config_kwargs else None

    last_exc: Exception
    for attempt in range(2):
        try:
            response = client.models.generate_content(
                model=model,
                contents=contents,
                config=config,
            )
            raw_text = response.text or ""
            if response_schema:
                return response_schema.model_validate_json(_clean_json_text(raw_text))
            return raw_text
        except Exception as exc:
            last_exc = exc
            err_str = str(exc)
            if "503" in err_str or "UNAVAILABLE" in err_str:
                if attempt == 0:
                    logger.warning("gemini_text_503_retrying", model=model, attempt=attempt + 1)
                    _time.sleep(2)
                    continue
            raise
    raise last_exc


def _call_gemini_multimodal(
    prompt: str,
    audio_or_image: Union[bytes, str],
    mime_type: str = "image/jpeg",
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute multimodal inference using Gemini (designated fallback for multimodal/STT).

    Uses GEMINI_MULTIMODAL_DEFAULT_MODEL (gemini-3.5-flash) which is confirmed stable.
    Retries once on 503 UNAVAILABLE before surfacing the error.
    """
    import time as _time
    from google.api_core.exceptions import ServiceUnavailable

    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set in environment.")

    media_bytes = _extract_bytes(audio_or_image)
    client = genai.Client(api_key=api_key)
    # Use dedicated multimodal model env-var; fall back to GEMINI_MULTIMODAL_DEFAULT_MODEL
    model = os.getenv("GEMINI_MULTIMODAL_MODEL", GEMINI_MULTIMODAL_DEFAULT_MODEL)

    # ── Correct single-turn structure: image + text as parts inside one Content ──
    # Passing [Part, str] at the top level of contents creates two separate
    # conversation turns in the google-genai SDK, which is semantically wrong.
    contents = types.Content(
        role="user",
        parts=[
            types.Part.from_bytes(data=media_bytes, mime_type=mime_type),
            types.Part.from_text(text=prompt),
        ],
    )

    config_kwargs: dict[str, Any] = {}
    if response_schema:
        config_kwargs["response_mime_type"] = "application/json"
        config_kwargs["response_schema"] = response_schema

    config = types.GenerateContentConfig(**config_kwargs) if config_kwargs else None

    # ── 503-retry: one retry after 2 s backoff ──
    last_exc: Exception
    for attempt in range(2):
        try:
            response = client.models.generate_content(
                model=model,
                contents=contents,
                config=config,
            )
            raw_text = response.text or ""
            if response_schema:
                return response_schema.model_validate_json(_clean_json_text(raw_text))
            return raw_text
        except Exception as exc:
            last_exc = exc
            err_str = str(exc)
            if "503" in err_str or "UNAVAILABLE" in err_str:
                if attempt == 0:
                    logger.warning(
                        "gemini_multimodal_503_retrying",
                        model=model,
                        attempt=attempt + 1,
                    )
                    _time.sleep(2)
                    continue
            raise  # non-503 errors surface immediately
    raise last_exc  # 503 persisted after retry


def call_multimodal(
    prompt: str,
    audio_or_image: Union[bytes, str],
    mime_type: str = "image/jpeg",
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute multimodal AI call with near.ai primary and Gemini fallback.

    Tries near.ai first ONLY IF NEAR_AI_API_KEY is set in environment.
    If not set, or if near.ai raises an 'unavailable/credits exhausted' error,
    falls back to Gemini.
    """
    near_ai_key = os.getenv("NEAR_AI_API_KEY", "").strip()

    if near_ai_key:
        try:
            logger.info("routing_multimodal_to_near_ai", provider="near.ai")
            return _call_near_ai_multimodal(
                prompt=prompt,
                audio_or_image=audio_or_image,
                mime_type=mime_type,
                response_schema=response_schema,
                **kwargs,
            )
        except (NearAIUnavailableError, Exception) as exc:
            logger.warning(
                "near_ai_multimodal_failed_falling_back_to_gemini",
                error=str(exc),
                fallback="gemini",
            )

    else:
        logger.info(
            "near_ai_key_absent_skipping_directly_to_gemini",
            fallback="gemini",
        )

    # Designated fallback: Gemini
    return _call_gemini_multimodal(
        prompt=prompt,
        audio_or_image=audio_or_image,
        mime_type=mime_type,
        response_schema=response_schema,
        **kwargs,
    )


def call_text(
    prompt: str,
    system_prompt: Optional[str] = None,
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute text reasoning AI call with near.ai primary and Groq fallback.

    Tries near.ai first ONLY IF NEAR_AI_API_KEY is set in environment.
    If not set, or if near.ai fails, falls back to Groq.
    """
    near_ai_key = os.getenv("NEAR_AI_API_KEY", "").strip()

    if near_ai_key:
        try:
            logger.info("routing_text_to_near_ai", provider="near.ai")
            return _call_near_ai_text(
                prompt=prompt,
                system_prompt=system_prompt,
                response_schema=response_schema,
                **kwargs,
            )
        except (NearAIUnavailableError, Exception) as exc:
            logger.warning(
                "near_ai_text_failed_falling_back_to_gemini",
                error=str(exc),
                fallback="gemini",
            )

    else:
        logger.info(
            "near_ai_key_absent_skipping_directly_to_gemini",
            fallback="gemini",
        )

    # Designated fallback: Gemini
    return _call_gemini_text(
        prompt=prompt,
        system_prompt=system_prompt,
        response_schema=response_schema,
        **kwargs,
    )
