"""Service wrapper for AI extraction features.

All calls route strictly through provider_client's call_multimodal and call_text
per docs/RULES.md and docs/ARCHITECTURE.md. Direct SDK usage is prohibited.
"""

from typing import Optional, Dict, Any
import json
import logging
import time
from pydantic import BaseModel, Field
from models.schemas import (
    VoiceExtractionResponse,
    MuacGradingResponse,
    RegisterOcrResponse,
    RegisterRow,
    AmbientResponse,
    AmbientSuggestion,
    ChatMessage,
    ChatResponse,
    CrossFieldValidationResponse,
    TranslationResponse,
)
from services.provider_client import call_multimodal, call_text

logger = logging.getLogger(__name__)

# 24-hour cache for Ask AshaAI chatbot (Section 2.7)
_chat_cache: Dict[str, tuple[float, str]] = {}
CACHE_TTL_SECONDS = 86400

STATIC_HEALTH_KNOWLEDGE = """
Official MoHFW & NVHCP Public Health Protocol Knowledge Base:
1. Malnutrition & Child Health:
- Severe Acute Malnutrition (SAM): MUAC < 115 mm (Red tape) OR bilateral pitting edema. Requires immediate referral to Nutrition Rehabilitation Centre (NRC).
- Moderate Acute Malnutrition (MAM): MUAC 115 - 124 mm (Yellow tape). Requires Take-Home Ration (THR) and 14-day weight follow-up.
- Normal: MUAC >= 125 mm (Green tape). Regular monitoring and balanced diet.
2. Immunization Schedule (NVHCP):
- Birth: BCG, OPV-0, Hepatitis B birth dose.
- 6 weeks: Pentavalent-1, OPV-1, Rotavirus-1, fIPV-1, PCV-1.
- 10 weeks: Pentavalent-2, OPV-2, Rotavirus-2.
- 14 weeks: Pentavalent-3, OPV-3, Rotavirus-3, fIPV-2, PCV-2.
- 9 months: MR-1, JE-1, Vitamin A dose 1, PCV booster.
- 16-24 months: MR-2, DPT booster-1, OPV booster, JE-2, Vitamin A dose 2.
- 5-6 years: DPT booster-2.
3. Antenatal Care (ANC) Protocols (PMSMA):
- Minimum 4 ANC checkups (ANC1 <12 wks, ANC2 14-26 wks, ANC3 28-34 wks, ANC4 36 wks to term).
- IFA tablets (180 days) + Calcium + 2 doses of Td/TT vaccine.
- Danger signs in pregnancy: Vaginal bleeding, severe headache with blurred vision, high fever, convulsions, high BP (>140/90), reduced fetal movements.
4. Infant Danger Signs:
- Fast breathing (>60 breaths/min), severe chest indrawing, fever (>38C) or hypothermia (<36.5C), refusal to breastfeed, lethargy, umbilical cord pus or redness.
Note on Live Search: Live search grounding is currently unsupported/offline on Groq; responses are derived from validated National Health Mission clinical guidelines.
"""

class GeminiExtractedField(BaseModel):
    key: str = Field(description="The column or field name")
    value: str = Field(description="The extracted value")

class GeminiVoiceExtractionResponse(BaseModel):
    transcript: str = Field(default="", description="Transcribed audio text")
    fields: list[GeminiExtractedField] = Field(default_factory=list, description="Extracted key-value form fields")
    fields_detected: int = Field(default=0, description="Total count of detected fields")
    name: Optional[str] = Field(None, description="Legacy field for family member")
    gender: Optional[str] = Field(None, description="Legacy field for gender")
    age: Optional[int] = Field(None, description="Legacy field for age")
    is_pregnant: Optional[bool] = Field(False, description="Legacy field for pregnancy")
    relationship: Optional[str] = Field(None, description="Legacy field for relationship")

class GeminiRegisterRow(BaseModel):
    fields: list[GeminiExtractedField] = Field(default_factory=list, description="Extracted column name to value mapping")
    confidence: float = Field(default=1.0, description="Confidence score for this row between 0.0 and 1.0")
    needs_review: bool = Field(default=False, description="True if any field has confidence < 0.8 or is uncertain")
    name: Optional[str] = Field(None, description="Legacy convenience field")
    age: Optional[int] = Field(None, description="Legacy convenience field")
    gender: Optional[str] = Field(None, description="Legacy convenience field")
    notes: Optional[str] = Field(None, description="Legacy convenience field")

class GeminiRegisterOcrResponse(BaseModel):
    register_type: str = Field(default="family_survey", description="Detected or requested register type")
    target_collection: str = Field(default="household_members", description="Target collection/table in system")
    total_rows_found: int = Field(default=0, description="Total number of rows extracted")
    rows: list[GeminiRegisterRow] = Field(default_factory=list, description="Extracted rows from register")
    confidence: float = Field(default=1.0, description="Overall confidence score between 0.0 and 1.0")

class AIService:
    """Delegates domain-specific AI processing to the unified provider client."""

    def grade_muac_photo(self, image_data: bytes) -> MuacGradingResponse:
        """Grade MUAC tape or child photo using call_multimodal (Section 2.3).
        
        Applies WHO/NVHCP thresholds:
        - < 115mm  -> SAM (RED, needs NRC referral)
        - 115-124mm -> MAM (YELLOW)
        - >= 125mm -> Normal (NORMAL)
        """
        prompt = (
            "You are an expert pediatric clinical assistant for Indian community health (ASHA). "
            "Examine this image of a child or MUAC tape.\n"
            "Assess:\n"
            "1. Estimated Mid-Upper Arm Circumference (MUAC) in millimeters (muac_mm).\n"
            "2. Malnutrition Grade based strictly on WHO / Indian NVHCP thresholds:\n"
            "   - Under 115 mm (or Red tape zone): grade = 'RED', malnutrition_grade = 'SAM', "
            "severity_label = 'Severe (SAM)', needs_nrc_referral = True\n"
            "   - 115 mm to 124 mm (or Yellow tape zone): grade = 'YELLOW', malnutrition_grade = 'MAM', "
            "severity_label = 'Moderate (MAM)', needs_nrc_referral = False\n"
            "   - 125 mm or higher (or Green tape zone): grade = 'NORMAL', malnutrition_grade = 'Normal', "
            "severity_label = 'Normal', needs_nrc_referral = False\n"
            "3. Assess observed clinical signs: visible severe wasting, bilateral pitting edema, rib prominence, loose skin folds.\n"
            "4. Recommend clinical action and provide clear explanation with confidence percentage (0-100)."
        )
        result = call_multimodal(
            prompt=prompt,
            audio_or_image=image_data,
            mime_type="image/jpeg",
            response_schema=MuacGradingResponse,
        )
        # Enforce deterministic threshold guarantees post-inference
        return self._enforce_muac_thresholds(result)

    def _enforce_muac_thresholds(self, response: MuacGradingResponse) -> MuacGradingResponse:
        """Guarantees exact 115mm / 125mm threshold categorization."""
        if response.muac_mm is not None:
            if response.muac_mm < 115.0:
                response.grade = "RED"
                response.malnutrition_grade = "SAM"
                response.severity_label = "Severe (SAM)"
                response.needs_nrc_referral = True
                if not response.recommendation:
                    response.recommendation = "Immediate referral to Nutrition Rehabilitation Centre (NRC)."
            elif response.muac_mm < 125.0:
                response.grade = "YELLOW"
                response.malnutrition_grade = "MAM"
                response.severity_label = "Moderate (MAM)"
                response.needs_nrc_referral = False
                if not response.recommendation:
                    response.recommendation = "Provide Take-Home Ration (THR) and weekly follow-up."
            else:
                response.grade = "NORMAL"
                response.malnutrition_grade = "Normal"
                response.severity_label = "Normal"
                response.needs_nrc_referral = False
                if not response.recommendation:
                    response.recommendation = "Maintain regular diet and monitoring as per schedule."
        return response

    def extract_voice_multimodal(
        self,
        audio_bytes: bytes,
        mime_type: str = "audio/webm",
        form_fields: Optional[str] = None,
    ) -> VoiceExtractionResponse:
        """Single-call multimodal voice dictation per Section 2.4.
        
        Transcribes Marathi/Hindi/English audio and extracts structured fields matching
        the dynamic schema injection.
        Audio is processed in memory and never persisted.
        """
        # Determine schema source
        schema_desc = ""
        if form_fields:
            try:
                parsed_fields = json.loads(form_fields)
                schema_desc = f"Target Form Fields (Dynamic): {json.dumps(parsed_fields, ensure_ascii=False)}"
            except Exception:
                schema_desc = f"Target Form Fields: {form_fields}"
        else:
            schema_desc = "Extract any recognizable health and identity fields (name, age, gender, symptoms, measurements, etc.)."

        prompt = (
            "You are an expert clinical dictation assistant for Indian ASHA healthcare workers.\n"
            "Listen to this spoken audio (spoken in Marathi, Hindi, or Indian English).\n"
            "CRITICAL REQUIREMENT: ALL returned text (including the transcript and all field values) MUST BE TRANSLATED TO ENGLISH.\n"
            "1. Transcribe the spoken words and translate the full transcription into English, storing the English version in `transcript`.\n"
            "2. Extract values for the fields specified below into `fields` (key-value dictionary). Translate all field values to English.\n"
            f"{schema_desc}\n"
            "3. Count the number of non-null extracted fields into `fields_detected`.\n"
            "Do not invent values. If a field was not mentioned in the audio, omit it."
        )

        gemini_result = call_multimodal(
            prompt=prompt,
            audio_or_image=audio_bytes,
            mime_type=mime_type,
            response_schema=GeminiVoiceExtractionResponse,
        )
        
        # Convert Gemini specific schema back to public schema
        fields_dict = {f.key: f.value for f in gemini_result.fields}
        
        result = VoiceExtractionResponse(
            transcript=gemini_result.transcript,
            fields=fields_dict,
            fields_detected=gemini_result.fields_detected,
            name=gemini_result.name,
            gender=gemini_result.gender,
            age=gemini_result.age,
            is_pregnant=gemini_result.is_pregnant,
            relationship=gemini_result.relationship
        )
        
        if not result.fields_detected and result.fields:
            result.fields_detected = len(result.fields)
        return result

    def extract_register_ocr(
        self,
        image_data: bytes,
        register_type: Optional[str] = "family_survey",
    ) -> RegisterOcrResponse:
        """Extract tabular data from paper register photo per Section 2.6.
        
        Identifies register header in Marathi/English, parses rows, and flags
        fields with confidence < 0.8 for human review.
        """
        prompt = (
            "You are an expert OCR assistant for handwritten Indian health registers "
            "(ASHA registers in Marathi and English).\n"
            "Examine this photograph of a physical register page:\n"
            "CRITICAL REQUIREMENT: ALL returned text (names, conditions, etc.) MUST BE TRANSLATED TO ENGLISH.\n"
            "1. Identify the register type from the header / columns (family_survey, vaccination, anc, child_growth, etc.).\n"
            "2. Extract every table row into key-value fields (e.g. name, age, gender, date, relation, etc.). "
            "Translate all names, relations, and text values into English.\n"
            "3. For each row, assign confidence (0.0 to 1.0). If any text is blurry or confidence < 0.8, "
            "set needs_review = True.\n"
            f"Requested register type hint: {register_type or 'auto-detect'}."
        )
        gemini_result = call_multimodal(
            prompt=prompt,
            audio_or_image=image_data,
            mime_type="image/jpeg",
            response_schema=GeminiRegisterOcrResponse,
        )
        
        public_rows = []
        for r in gemini_result.rows:
            # Map GeminiExtractedField to dictionary
            row_dict = {f.key: f.value for f in r.fields}
            
            needs_rev = r.needs_review
            # Ensure needs_review flag is set on any row with confidence < 0.8
            if r.confidence < 0.8:
                needs_rev = True
                
            public_rows.append(RegisterRow(
                fields=row_dict,
                confidence=r.confidence,
                needs_review=needs_rev,
                name=r.name,
                age=r.age,
                gender=r.gender,
                notes=r.notes
            ))
            
        result = RegisterOcrResponse(
            register_type=gemini_result.register_type,
            target_collection=gemini_result.target_collection,
            total_rows_found=len(public_rows),
            rows=public_rows,
            confidence=gemini_result.confidence
        )
        return result

    def extract_ambient_suggestions(
        self,
        transcript: str,
        module: Optional[str] = "family_survey",
        current_state: Optional[Dict[str, Any]] = None,
    ) -> AmbientResponse:
        """Extract non-blocking suggestion chips from ambient conversation per Section 2.5."""
        prompt = (
            f"Given the ongoing conversation transcript during an ASHA health visit: '{transcript}',\n"
            f"and form module '{module}', extract suggested form field updates as suggestion chips.\n"
            "Never auto-fill; only suggest high-confidence findings (e.g. weight, symptoms, pregnancy, dates)."
        )
        return call_text(
            prompt=prompt,
            response_schema=AmbientResponse,
        )

    def ask_asha_ai(
        self,
        message: str,
        language: str = "en",
        conversation_history: Optional[list[ChatMessage]] = None,
    ) -> ChatResponse:
        """Ask AshaAI chatbot answering from static health knowledge block (Section 2.7).
        
        Cached for 24 hours per question. Search grounding is honestly noted as offline.
        Uses call_text() (near.ai primary, Groq fallback).
        """
        norm_key = f"{language.lower()}:{message.strip().lower()}"
        now = time.time()
        if norm_key in _chat_cache:
            ts, cached_resp = _chat_cache[norm_key]
            if now - ts < CACHE_TTL_SECONDS:
                logger.info("Serving chatbot response from 24h cache for key=%s", norm_key)
                return ChatResponse(response=cached_resp, source="cache_24h")

        history_text = ""
        if conversation_history:
            history_text = "Previous conversation:\n" + "\n".join(
                f"{m.role}: {m.content}" for m in conversation_history[-5:]
            )

        lang_instruction = {
            "mr": "Respond in simple, clear, colloquial Marathi (मराठी) suitable for an ASHA worker in Maharashtra.",
            "hi": "Respond in simple, clear Hindi (हिंदी) suitable for an ASHA community health worker.",
            "en": "Respond in clear Indian English with empathetic, direct clinical guidance.",
        }.get(language.lower(), "Respond in clear English.")

        prompt = (
            "You are AshaAI, an empathetic and clinically authoritative AI health assistant for ASHA workers.\n"
            f"{STATIC_HEALTH_KNOWLEDGE}\n"
            f"{history_text}\n"
            f"ASHA Worker Query: '{message}'\n"
            f"Language requirement: {lang_instruction}\n"
            "Guidelines:\n"
            "1. Base your answer on the official guidelines above (malnutrition, immunization, ANC, newborn danger signs).\n"
            "2. Be concise, practical, and step-by-step.\n"
            "3. If referring to hospital or NRC, emphasize immediate referral.\n"
            "4. Never hallucinate medication dosages.\n"
            "5. SCOPE RESTRICTION (CRITICAL): You ONLY answer questions related to: ASHA worker duties, "
            "the 12 health modules in this app (Family Survey, ANC, Child Growth, Vaccination, etc.), "
            "Indian public health protocols (NVHCP, MoHFW, IMNCI), or how to use AshaAI. "
            "If a question is clearly off-topic (politics, entertainment, coding, recipes, general knowledge, "
            "anything unrelated to community health work), respond ONLY with: "
            "'I can only help with ASHA health worker duties and health protocols. "
            "Please ask me about malnutrition, ANC, vaccinations, or how to use this app.' "
            "Do NOT attempt to answer off-topic questions even if you could."
        )

        result = call_text(
            prompt=prompt,
            response_schema=ChatResponse,
        )
        # Store in 24-hour cache
        _chat_cache[norm_key] = (now, result.response)
        return result

    def validate_cross_field(
        self,
        entity_type: str,
        record: Dict[str, Any],
    ) -> CrossFieldValidationResponse:
        """Cross-field consistency check using call_text() per Section 2.9.
        
        Flags logical conflicts into pending_reviews without blocking ASHA save.
        """
        prompt = (
            "You are an expert clinical data quality auditor for Indian ASHA community health records.\n"
            f"Audit this record of entity '{entity_type}':\n"
            f"Record Fields: {json.dumps(record, default=str, ensure_ascii=False)}\n\n"
            "Check for cross-field contradictions and clinical impossibilities:\n"
            "1. Pregnant woman recorded age < 14 or > 55.\n"
            "2. Chronological date contradictions (e.g. ANC2 date before ANC1 date, or future visit dates).\n"
            "3. Breastfeeding cessation age older than the child's current age.\n"
            "4. Severe weight drop (>30% loss) in short period without acute disease notes.\n"
            "5. Systolic BP <= Diastolic BP.\n\n"
            "If any conflicts exist, return has_conflict = true, list of conflict descriptions, "
            "severity ('HIGH' for dangerous errors, 'MEDIUM' for anomalies), and suggested_action."
        )

        result = call_text(
            prompt=prompt,
            response_schema=CrossFieldValidationResponse,
        )
        return self._enforce_deterministic_cross_field(entity_type, record, result)

    def _enforce_deterministic_cross_field(
        self,
        entity_type: str,
        record: Dict[str, Any],
        resp: CrossFieldValidationResponse,
    ) -> CrossFieldValidationResponse:
        """Deterministic safety net for known Section 2.9 examples."""
        conflicts = list(resp.conflicts)

        # Rule 1: Pregnant woman recorded age < 14
        age = record.get("age") or record.get("mother_age")
        is_pregnant = record.get("is_pregnant") or record.get("isPregnant") or (entity_type == "pregnancy")
        if age is not None:
            try:
                age_val = float(age)
                if is_pregnant and age_val < 14:
                    conflicts.append(f"Pregnant mother recorded with implausible age {int(age_val)} (<14 years).")
            except Exception:
                pass

        # Rule 2: Chronological ANC order (ANC2 before ANC1)
        anc1 = record.get("anc1_date") or record.get("anc1Date")
        anc2 = record.get("anc2_date") or record.get("anc2Date")
        if anc1 and anc2:
            try:
                if str(anc2) < str(anc1):
                    conflicts.append(f"ANC2 date ({anc2}) is recorded before ANC1 date ({anc1}).")
            except Exception:
                pass

        # Rule 3: Breastfeeding cessation age older than child age
        bf_cessation = record.get("breastfeeding_cessation_months")
        child_age = record.get("age_months")
        if bf_cessation is not None and child_age is not None:
            try:
                if float(bf_cessation) > float(child_age):
                    conflicts.append(
                        f"Breastfeeding cessation age ({bf_cessation}m) exceeds child's current age ({child_age}m)."
                    )
            except Exception:
                pass

        if conflicts:
            resp.has_conflict = True
            resp.conflicts = list(dict.fromkeys(conflicts))
            resp.severity = "HIGH"
            if not resp.suggested_action:
                resp.suggested_action = "Supervisor review required — verify dates and age with ASHA worker."

        return resp

    def translate_survey_text(
        self,
        text: str,
        source_lang: Optional[str] = None,
    ) -> TranslationResponse:
        """Translate survey field label across English, Marathi, and Hindi (Section 2.13).
        
        Uses call_text() (near.ai primary, Groq fallback).
        """
        prompt = (
            "You are a professional medical translator for public health surveys in India.\n"
            "Translate this health survey question or field label into all three languages: "
            "English, Marathi (मराठी), and Hindi (हिंदी).\n"
            f"Input Text: '{text}'\n"
            f"Source language hint: {source_lang or 'auto'}\n"
            "Return JSON with keys 'en', 'mr', and 'hi'. Use culturally clear, accurate health terms."
        )

        return call_text(
            prompt=prompt,
            response_schema=TranslationResponse,
        )


# Backward-compatible singleton instance
gemini_service = AIService()

