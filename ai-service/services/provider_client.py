"""Single reusable client wrapper for AI inference routing in AshaAI.

Provider routing rules per docs/ARCHITECTURE.md and docs/RULES.md:
- call_multimodal:
    Tries near.ai first ONLY IF NEAR_AI_API_KEY is set in environment.
    For audio (STT), intercepts and tries Indic-Whisper before Gemini fallback.
    If near.ai raises an unavailable/credits exhausted/connection error, falls back to Gemini.
- call_text:
    Tries near.ai first ONLY IF NEAR_AI_API_KEY is set in environment.
    If not set, or if near.ai fails, falls back to Sarvam-30B, then Groq.
- call_translation:
    Tries IndicTrans2 first. If unavailable, falls back to Groq -> Gemini.
- call_critical_consensus:
    Calls 2 providers in parallel. Evaluates consensus for critical tasks.

Every feature-specific AI service must route through these functions.
Direct calls to Gemini, Groq, Sarvam, Indic models, or near.ai from feature files are strictly prohibited.
"""

from __future__ import annotations

import base64
import json
import os
import concurrent.futures
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

HF_SARVAM_DEFAULT_MODEL = "sarvamai/sarvam-30b"
HF_INDICTRANS_DEFAULT_MODEL = "ai4bharat/indictrans2-en-indic"
HF_INDICWHISPER_DEFAULT_MODEL = "ai4bharat/indicwhisper"


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


def _call_hf_sarvam_text(
    prompt: str,
    system_prompt: Optional[str] = None,
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute text inference using Sarvam-30B on Hugging Face Inference API."""
    api_key = os.getenv("HUGGINGFACE_API_KEY")
    if not api_key:
        raise AIProviderError("HUGGINGFACE_API_KEY is not set.")
    
    model = os.getenv("HF_SARVAM_MODEL", HF_SARVAM_DEFAULT_MODEL)
    
    # Simple formatting for models via Inference API text generation
    full_prompt = prompt
    if system_prompt:
        full_prompt = f"System: {system_prompt}\n\nUser: {prompt}"
        
    payload = {
        "inputs": full_prompt, 
        "parameters": {"temperature": kwargs.get("temperature", 0.1), "return_full_text": False, "max_new_tokens": 1024}
    }
    try:
        with httpx.Client(timeout=30.0) as client:
            resp = client.post(
                f"https://api-inference.huggingface.co/models/{model}",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json=payload
            )
        resp.raise_for_status()
        data = resp.json()
        if isinstance(data, list) and len(data) > 0 and "generated_text" in data[0]:
            raw_text = data[0]["generated_text"]
        else:
            raw_text = str(data)
    except Exception as e:
        raise AIProviderError(f"HF Sarvam-30B request failed: {e}") from e

    if response_schema:
        return response_schema.model_validate_json(_clean_json_text(raw_text))
    return raw_text


def _call_hf_indictrans2(
    prompt: str,
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute translation inference using IndicTrans2 on Hugging Face Inference API."""
    api_key = os.getenv("HUGGINGFACE_API_KEY")
    if not api_key:
        raise AIProviderError("HUGGINGFACE_API_KEY is not set.")
    
    model = os.getenv("HF_INDICTRANS_MODEL", HF_INDICTRANS_DEFAULT_MODEL)
    payload = {"inputs": prompt}
    try:
        with httpx.Client(timeout=30.0) as client:
            resp = client.post(
                f"https://api-inference.huggingface.co/models/{model}",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json=payload
            )
        resp.raise_for_status()
        data = resp.json()
        if isinstance(data, list) and len(data) > 0 and "translation_text" in data[0]:
            raw_text = data[0]["translation_text"]
        elif isinstance(data, list) and len(data) > 0 and "generated_text" in data[0]:
            raw_text = data[0]["generated_text"]
        else:
            raw_text = str(data)
    except Exception as e:
        raise AIProviderError(f"HF IndicTrans2 request failed: {e}") from e

    if response_schema:
        return response_schema.model_validate_json(_clean_json_text(raw_text))
    return raw_text


def _call_hf_indic_whisper(
    audio_or_image: Union[bytes, str],
    **kwargs: Any,
) -> str:
    """Execute speech-to-text using Indic-Whisper on Hugging Face Inference API."""
    api_key = os.getenv("HUGGINGFACE_API_KEY")
    if not api_key:
        raise AIProviderError("HUGGINGFACE_API_KEY is not set.")
    
    model = os.getenv("HF_INDICWHISPER_MODEL", HF_INDICWHISPER_DEFAULT_MODEL)
    media_bytes = _extract_bytes(audio_or_image)
    try:
        with httpx.Client(timeout=60.0) as client:
            resp = client.post(
                f"https://api-inference.huggingface.co/models/{model}",
                headers={"Authorization": f"Bearer {api_key}"},
                content=media_bytes
            )
        resp.raise_for_status()
        data = resp.json()
        if isinstance(data, dict) and "text" in data:
            return data["text"]
        return str(data)
    except Exception as e:
        raise AIProviderError(f"HF Indic-Whisper request failed: {e}") from e


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
        # Groq requires the word 'json' to be present if response_format is used
        # We also need to provide the schema so it knows which keys to output
        schema_str = response_schema.model_json_schema()
        if "json" not in prompt.lower() and (not system_prompt or "json" not in system_prompt.lower()):
            messages.append({"role": "user", "content": f"You must respond in valid JSON format matching this schema: {schema_str}"})
        else:
            messages.append({"role": "user", "content": f"Ensure your JSON response matches this schema: {schema_str}"})

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
    """Execute multimodal inference using Gemini (designated fallback for multimodal/STT)."""
    import time as _time

    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set in environment.")

    media_bytes = _extract_bytes(audio_or_image)
    client = genai.Client(api_key=api_key)
    model = os.getenv("GEMINI_MULTIMODAL_MODEL", GEMINI_MULTIMODAL_DEFAULT_MODEL)

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
                    logger.warning("gemini_multimodal_503_retrying", model=model, attempt=attempt + 1)
                    _time.sleep(2)
                    continue
            raise
    raise last_exc


def call_multimodal(
    prompt: str,
    audio_or_image: Union[bytes, str],
    mime_type: str = "image/jpeg",
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute multimodal AI call with near.ai primary and Gemini fallback.
    For audio, tries Indic-Whisper for STT before Gemini.
    """
    is_audio = mime_type.startswith("audio/")

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
                "near_ai_multimodal_failed_falling_back",
                error=str(exc)
            )

    hf_key = os.getenv("HUGGINGFACE_API_KEY", "").strip()
    if is_audio and hf_key:
        try:
            logger.info("routing_audio_to_indicwhisper", provider="indicwhisper")
            transcript = _call_hf_indic_whisper(audio_or_image=audio_or_image, **kwargs)
            # Feed transcript into text model to fulfill reasoning schema if requested
            if response_schema:
                text_prompt = f"Transcript from audio: {transcript}\n\nTask: {prompt}"
                logger.info("indicwhisper_success_routing_transcript_to_text", provider="indicwhisper")
                return call_text(prompt=text_prompt, response_schema=response_schema, **kwargs)
            else:
                return transcript
        except Exception as exc:
            logger.warning("indicwhisper_failed_falling_back_to_gemini", error=str(exc))

    logger.info("routing_multimodal_to_gemini_fallback", provider="gemini")
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
    """Execute text reasoning AI call with near.ai primary, Sarvam-30B, then Groq fallback."""
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
            logger.warning("near_ai_text_failed_falling_back", error=str(exc))

    hf_key = os.getenv("HUGGINGFACE_API_KEY", "").strip()
    if hf_key:
        try:
            logger.info("routing_text_to_sarvam", provider="sarvam")
            return _call_hf_sarvam_text(
                prompt=prompt,
                system_prompt=system_prompt,
                response_schema=response_schema,
                **kwargs,
            )
        except Exception as exc:
            logger.warning("sarvam_failed_falling_back", error=str(exc))

    groq_key = os.getenv("GROQ_API_KEY", "").strip()
    if groq_key:
        try:
            logger.info("routing_text_to_groq", provider="groq")
            return _call_groq_text(
                prompt=prompt,
                system_prompt=system_prompt,
                response_schema=response_schema,
                **kwargs,
            )
        except Exception as exc:
            logger.warning("groq_failed_falling_back", error=str(exc))

    logger.info("routing_text_to_gemini_fallback", provider="gemini")
    return _call_gemini_text(
        prompt=prompt,
        system_prompt=system_prompt,
        response_schema=response_schema,
        **kwargs,
    )


def call_translation(
    prompt: str,
    response_schema: Optional[Type[BaseModel]] = None,
    **kwargs: Any,
) -> Any:
    """Execute translation call with IndicTrans2 primary, Groq/Gemini fallback."""
    hf_key = os.getenv("HUGGINGFACE_API_KEY", "").strip()
    if hf_key:
        try:
            logger.info("routing_translation_to_indictrans2", provider="indictrans2")
            return _call_hf_indictrans2(prompt=prompt, response_schema=response_schema, **kwargs)
        except Exception as exc:
            logger.warning("indictrans2_failed_falling_back", error=str(exc))
    
    groq_key = os.getenv("GROQ_API_KEY", "").strip()
    if groq_key:
        try:
            logger.info("routing_translation_to_groq", provider="groq")
            return _call_groq_text(prompt=prompt, response_schema=response_schema, **kwargs)
        except Exception as exc:
            logger.warning("groq_failed_falling_back", error=str(exc))
            
    logger.info("routing_translation_to_gemini_fallback", provider="gemini")
    return _call_gemini_text(prompt=prompt, response_schema=response_schema, **kwargs)


def call_critical_consensus(
    prompt: str, 
    media: Optional[Union[bytes, str]] = None, 
    task_type: str = "classification", 
    **kwargs: Any
) -> dict:
    """
    Calls exactly 2 providers IN PARALLEL for critical tasks.
    Text-only: Gemini + Sarvam-30B.
    Vision: Gemini + near.ai.
    """
    results = {}
    
    def fetch_gemini():
        if media:
            mime_type = kwargs.get("mime_type", "image/jpeg")
            return _call_gemini_multimodal(prompt=prompt, audio_or_image=media, mime_type=mime_type, **kwargs)
        else:
            return _call_gemini_text(prompt=prompt, **kwargs)
            
    def fetch_other():
        if media:
            mime_type = kwargs.get("mime_type", "image/jpeg")
            return _call_near_ai_multimodal(prompt=prompt, audio_or_image=media, mime_type=mime_type, **kwargs)
        else:
            return _call_hf_sarvam_text(prompt=prompt, **kwargs)
            
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        f_gem = executor.submit(fetch_gemini)
        f_oth = executor.submit(fetch_other)
        
        try:
            results["gemini"] = f_gem.result()
        except Exception as e:
            logger.warning("consensus_gemini_failed", error=str(e))
            results["gemini"] = None
            
        try:
            results["other"] = f_oth.result()
        except Exception as e:
            logger.warning("consensus_other_failed", error=str(e))
            results["other"] = None
            
    res_gem = results["gemini"]
    res_oth = results["other"]
    
    if res_gem and res_oth:
        if task_type == "classification":
            match = False
            if hasattr(res_gem, "model_dump_json") and hasattr(res_oth, "model_dump_json"):
                match = res_gem.model_dump_json() == res_oth.model_dump_json()
            else:
                match = str(res_gem).strip().lower() == str(res_oth).strip().lower()
                
            if match:
                return {"result": res_gem, "confidence": "high", "flag_for_review": False}
            else:
                return {
                    "result": {"opinion_1": res_gem, "opinion_2": res_oth}, 
                    "confidence": "disputed", 
                    "flag_for_review": True
                }
    elif res_gem:
        return {"result": res_gem, "confidence": "single_source", "flag_for_review": False}
    elif res_oth:
        return {"result": res_oth, "confidence": "single_source", "flag_for_review": False}
    else:
        raise AIProviderError("Both providers failed in critical consensus.")


def call_report_generation_consensus(
    prompt: str,
    response_schema=None,
    **kwargs,
):
    """
    Parallel Gemini + Groq for text-only report generation (e.g. malnutrition Model 3).

    The image is NEVER sent here -- only plain-text measurements + classification flags
    are passed as the prompt. This makes both provider calls cheap and parallelisable.

    Behaviour:
      - BOTH providers are called simultaneously (ThreadPoolExecutor, 2 workers).
      - If BOTH succeed: a second Groq call synthesises a single merged report,
        labelled source = "consensus_merged".
      - If only ONE succeeds: that result is returned as-is,
        labelled source = "single_source".
      - If BOTH fail: raises AIProviderError.

    Total provider calls: up to 3 (Gemini draft, Groq draft, Groq merge).
    Groq does double-duty for the merge step; no third provider is introduced.
    """
    import concurrent.futures as _cf

    gemini_res = None
    groq_res = None
    draft_kwargs = dict(kwargs)
    if response_schema:
        draft_kwargs["response_schema"] = response_schema

    # Step 1: Parallel drafts (text-only, no image)
    def _gemini_draft():
        return _call_gemini_text(prompt=prompt, **draft_kwargs)

    def _groq_draft():
        return _call_groq_text(prompt=prompt, **draft_kwargs)

    with _cf.ThreadPoolExecutor(max_workers=2) as ex:
        fg = ex.submit(_gemini_draft)
        fq = ex.submit(_groq_draft)
        try:
            gemini_res = fg.result()
            logger.info("report_consensus_gemini_draft_ok")
        except Exception as exc:
            logger.warning("report_consensus_gemini_draft_failed", error=str(exc))
        try:
            groq_res = fq.result()
            logger.info("report_consensus_groq_draft_ok")
        except Exception as exc:
            logger.warning("report_consensus_groq_draft_failed", error=str(exc))

    def _to_text(obj: Any) -> str:
        if hasattr(obj, "model_dump_json"):
            return obj.model_dump_json(indent=2)
        if isinstance(obj, dict):
            return json.dumps(obj, indent=2)
        return str(obj)

    def _to_schema_result(val: Any) -> Any:
        if response_schema is None or val is None:
            return val
        if isinstance(val, response_schema):
            return val
        if isinstance(val, dict):
            return response_schema.model_validate(val)
        if isinstance(val, str):
            return response_schema.model_validate_json(_clean_json_text(val))
        return val

    # Step 2: Merge or single-source
    if gemini_res is not None and groq_res is not None:
        gemini_text = _to_text(gemini_res)
        groq_text = _to_text(groq_res)
        schema_hint = (
            "\n\nRespond in valid JSON matching this schema: " + json.dumps(response_schema.model_json_schema())
            if response_schema else ""
        )
        merge_prompt = (
            "You are a senior pediatric clinical editor. "
            "Two AI systems independently generated malnutrition assessment reports from the same structured measurements. "
            "Synthesise ONE definitive, coherent report combining the strongest and most clinically accurate elements of both. "
            "Do NOT output both reports separately or reference which source each part came from. "
            "The result must read as a single, fluent clinical assessment.\n\n"
            "=== REPORT A (Gemini) ===\n" + gemini_text + "\n\n"
            "=== REPORT B (Groq/Llama) ===\n" + groq_text + "\n\n"
            "Output the merged report:" + schema_hint
        )
        try:
            merged = _call_groq_text(prompt=merge_prompt, response_schema=response_schema)
            logger.info("report_consensus_merge_ok")
            return {"result": _to_schema_result(merged), "source": "consensus_merged"}
        except Exception as exc:
            logger.warning("report_consensus_merge_failed_using_gemini", error=str(exc))
            return {"result": _to_schema_result(gemini_res), "source": "single_source"}
    elif gemini_res is not None:
        logger.info("report_consensus_single_source_gemini")
        return {"result": _to_schema_result(gemini_res), "source": "single_source"}
    elif groq_res is not None:
        logger.info("report_consensus_single_source_groq")
        return {"result": _to_schema_result(groq_res), "source": "single_source"}
    else:
        raise AIProviderError("Both Gemini and Groq failed in report_generation consensus.")
