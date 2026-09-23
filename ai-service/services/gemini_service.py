"""Service wrapper for AI extraction features.

All calls route strictly through provider_client's call_multimodal and call_text
per docs/RULES.md and docs/ARCHITECTURE.md. Direct SDK usage is prohibited.
"""

from typing import Optional, Dict, Any
import json
import logging
import re
import time
import os
import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision
import onnxruntime as ort
from huggingface_hub import hf_hub_download
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
from services.provider_client import call_multimodal, call_text, call_translation, call_critical_consensus, call_report_generation_consensus

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
    date_of_birth: Optional[str] = Field(None, description="Extracted date of birth in ISO YYYY-MM-DD format (e.g. 2006-07-25)")
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

_MONTHS_MAP = {
    'january': 1, 'jan': 1, 'february': 2, 'feb': 2, 'march': 3, 'mar': 3,
    'april': 4, 'apr': 4, 'may': 5, 'june': 6, 'jun': 6, 'july': 7, 'jul': 7,
    'august': 8, 'aug': 8, 'september': 9, 'sep': 9, 'sept': 9, 'october': 10,
    'oct': 10, 'november': 11, 'nov': 11, 'december': 12, 'dec': 12,
    # Marathi months
    'जानेवारी': 1, 'फेब्रुवारी': 2, 'मार्च': 3, 'एप्रिल': 4,
    'मे': 5, 'जून': 6, 'जुलै': 7, 'ऑगस्ट': 8,
    'सप्टेंबर': 9, 'ऑक्टोबर': 10, 'नोव्हेंबर': 11, 'डिसेंबर': 12,
    # Hindi months
    'जनवरी': 1, 'फरवरी': 2, 'अप्रैल': 4, 'मई': 5,
    'जुलाई': 7, 'अगस्त': 8, 'सितंबर': 9, 'अक्टूबर': 10,
    'नवंबर': 11, 'दिसंबर': 12,
}

_DEVANAGARI_DIGITS_TRANS = str.maketrans('०१२३४५६७८९', '0123456789')

def _parse_date_to_iso(val: Any) -> str:
    if not val:
        return ""
    s = str(val).translate(_DEVANAGARI_DIGITS_TRANS).strip()
    if re.match(r'^\d{4}-\d{2}-\d{2}$', s):
        return s
    
    # 25 July 2006 or 25th July 2006 or 25 जुलै 2006
    m = re.match(r'^(\d{1,2})(?:st|nd|rd|th)?\s+([a-zA-Z\u0900-\u097F]+)\s+(\d{4})$', s)
    if m:
        day, mon_str, year = int(m.group(1)), m.group(2).lower(), int(m.group(3))
        mon = _MONTHS_MAP.get(mon_str)
        if mon and 1 <= day <= 31 and 1900 <= year <= 2100:
            return f"{year:04d}-{mon:02d}-{day:02d}"
            
    # July 25, 2006
    m = re.match(r'^([a-zA-Z\u0900-\u097F]+)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,)?\s+(\d{4})$', s)
    if m:
        mon_str, day, year = m.group(1).lower(), int(m.group(2)), int(m.group(3))
        mon = _MONTHS_MAP.get(mon_str)
        if mon and 1 <= day <= 31 and 1900 <= year <= 2100:
            return f"{year:04d}-{mon:02d}-{day:02d}"
            
    # DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY
    m = re.match(r'^(\d{1,2})[/\-\.](\d{1,2})[/\-\.](\d{4})$', s)
    if m:
        day, mon, year = int(m.group(1)), int(m.group(2)), int(m.group(3))
        if 1 <= mon <= 12 and 1 <= day <= 31 and 1900 <= year <= 2100:
            return f"{year:04d}-{mon:02d}-{day:02d}"
            
    # YYYY/MM/DD or YYYY.MM.DD
    m = re.match(r'^(\d{4})[/\-\.](\d{1,2})[/\-\.](\d{1,2})$', s)
    if m:
        year, mon, day = int(m.group(1)), int(m.group(2)), int(m.group(3))
        if 1 <= mon <= 12 and 1 <= day <= 31:
            return f"{year:04d}-{mon:02d}-{day:02d}"

    return s

def _extract_dob_from_text(raw: str) -> Optional[str]:
    """Scans text or transcript for spoken date of birth in English, Marathi, or Hindi."""
    if not raw:
        return None
    text = str(raw).translate(_DEVANAGARI_DIGITS_TRANS)
    patterns = [
        r'(?:date\s+of\s+birth|birth\s*date|dob|born\s+on|born|जन्मतारीख|जन्म\s*तारीख)\s*(?:is|:|-)?\s*(\d{1,2}(?:st|nd|rd|th)?\s+[a-zA-Z\u0900-\u097F]+(?:,)?\s+\d{4})',
        r'(?:date\s+of\s+birth|birth\s*date|dob|born\s+on|born|जन्मतारीख|जन्म\s*तारीख)\s*(?:is|:|-)?\s*([a-zA-Z\u0900-\u097F]+\s+\d{1,2}(?:st|nd|rd|th)?(?:,)?\s+\d{4})',
        r'(?:date\s+of\s+birth|birth\s*date|dob|born\s+on|born|जन्मतारीख|जन्म\s*तारीख)\s*(?:is|:|-)?\s*(\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{4})',
        r'(\d{1,2}(?:st|nd|rd|th)?\s+(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec|जुलै|जुलाई|जानेवारी|फेब्रुवारी|मार्च|एप्रिल|मे|जून|ऑगस्ट|सप्टेंबर|ऑक्टोबर|नोव्हेंबर|डिसेंबर|जनवरी|फरवरी|मई|अगस्त|सितंबर|अक्टूबर|नवंबर|दिसंबर)\s*,?\s*\d{4})',
        r'((?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec|जुलै|जुलाई|जानेवारी|फेब्रुवारी|मार्च|एप्रिल|मे|जून|ऑगस्ट|सप्टेंबर|ऑक्टोबर|नोव्हेंबर|डिसेंबर|जनवरी|फरवरी|मई|अगस्त|सितंबर|अक्टूबर|नवंबर|दिसंबर)\s+\d{1,2}(?:st|nd|rd|th)?\s*,?\s*\d{4})',
        r'(\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{4})'
    ]
    for pat in patterns:
        m = re.search(pat, text, re.IGNORECASE)
        if m:
            iso = _parse_date_to_iso(m.group(1))
            if re.match(r'^\d{4}-\d{2}-\d{2}$', iso):
                return iso
    return None

class AIService:
    """Delegates domain-specific AI processing to the unified provider client."""

    def grade_muac_photo(self, image_data: bytes, age: Optional[str] = None, gender: Optional[str] = None, height: Optional[str] = None, weight: Optional[str] = None) -> MuacGradingResponse:
        """Grade MUAC tape or child photo using the 3-model pipeline (Section 2.3).
        
        Applies WHO/NVHCP thresholds:
        - < 115mm  -> SAM (RED, needs NRC referral)
        - 115-124mm -> MAM (YELLOW)
        - >= 125mm -> Normal (NORMAL)
        """
        
        # --- Model 1: MediaPipe Tasks API & CV2 ---
        np_arr = np.frombuffer(image_data, np.uint8)
        img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        
        calculated_muac = None
        if img is not None:
            # 1a. Locate .task model bundle — downloaded once alongside service startup
            _TASK_MODEL_PATH = os.path.join(
                os.path.dirname(os.path.dirname(__file__)),  # ai-service root
                "pose_landmarker_lite.task"
            )
            arm_width_px = None
            
            if os.path.exists(_TASK_MODEL_PATH):
                try:
                    # Tasks API: IMAGE running mode for single-frame detection
                    base_options = mp_python.BaseOptions(model_asset_path=_TASK_MODEL_PATH)
                    options = mp_vision.PoseLandmarkerOptions(
                        base_options=base_options,
                        running_mode=mp_vision.RunningMode.IMAGE,
                    )
                    img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
                    mp_image = mp.Image(
                        image_format=mp.ImageFormat.SRGB,
                        data=img_rgb
                    )
                    with mp_vision.PoseLandmarker.create_from_options(options) as landmarker:
                        detection_result = landmarker.detect(mp_image)
                    
                    # PoseLandmark indices: LEFT_ELBOW=13, LEFT_WRIST=15
                    # We use the elbow–wrist segment length as a proxy for arm circumference diameter
                    PoseLandmark = mp_vision.PoseLandmark
                    h_px, w_px = img.shape[:2]
                    if detection_result.pose_landmarks:
                        lms = detection_result.pose_landmarks[0]  # first person
                        # Use left elbow (13) and left wrist (15)
                        elbow = lms[PoseLandmark.LEFT_ELBOW]
                        wrist = lms[PoseLandmark.LEFT_WRIST]
                        # Pixel distance between elbow and wrist
                        ex, ey = elbow.x * w_px, elbow.y * h_px
                        wx, wy = wrist.x * w_px, wrist.y * h_px
                        forearm_px = ((ex - wx) ** 2 + (ey - wy) ** 2) ** 0.5
                        # MUAC measurement point is mid-upper arm — use 1/3 of forearm as proxy diameter
                        arm_width_px = forearm_px / 3.0
                        logger.info(f"MediaPipe PoseLandmarker: arm_width_px={arm_width_px:.1f}")
                except Exception as e:
                    logger.warning(f"MediaPipe Tasks API pose detection failed: {e}")
                    arm_width_px = None
            else:
                logger.warning(f"pose_landmarker_lite.task not found at {_TASK_MODEL_PATH} — skipping MediaPipe step")

            # 1b. Find reference coin using Hough Circle Transform
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            gray = cv2.medianBlur(gray, 5)
            circles = cv2.HoughCircles(gray, cv2.HOUGH_GRADIENT, 1, 20, param1=50, param2=30, minRadius=10, maxRadius=100)
            
            coin_diameter_px = None
            if circles is not None:
                circles = np.uint16(np.around(circles))
                coin_radius_px = circles[0, 0, 2]
                coin_diameter_px = coin_radius_px * 2.0
            
            # 1c. Compute real-world MUAC from ratio
            if arm_width_px and coin_diameter_px:
                coin_real_mm = 25.0  # Indian 1 Rupee coin diameter
                ratio = coin_real_mm / coin_diameter_px
                calculated_muac = arm_width_px * ratio * np.pi
                logger.info(f"Computed MUAC: {calculated_muac:.1f} mm")

        # --- Weight Regressor ---
        calc_weight = weight
        if not calc_weight and calculated_muac is not None and height and age:
            try:
                # Mock regressor: weight proportional to height and MUAC
                h_val = float(height)
                m_val = float(calculated_muac)
                calc_weight = str(round((h_val * 0.1) + (m_val * 0.05), 1))
            except Exception as e:
                logger.warning(f"Weight regressor failed: {e}")
                calc_weight = None

        # --- Model 2: ONNX Classifier (YOLOv8-style, input [1,3,640,640]) ---
        # repo_id and filename are env-var-driven so the correct private repo
        # can be set without touching code. HF_TOKEN (or HUGGINGFACE_API_KEY)
        # must be a READ-scoped token for the target repo.
        model2_flag = "UNKNOWN"
        try:
            _onnx_repo = os.environ.get(
                "ONNX_MALNUTRITION_REPO",
                "rohandev1/ashaai-malnutrition-model",  # fallback — override via .env
            )
            _onnx_filename = os.environ.get(
                "ONNX_MALNUTRITION_FILE",
                "malnutrition_classifier.onnx",
            )
            _hf_tok = os.environ.get("HF_TOKEN") or os.environ.get("HUGGINGFACE_API_KEY", "")
            model_path = hf_hub_download(
                repo_id=_onnx_repo,
                filename=_onnx_filename,
                token=_hf_tok or None,
            )
            session = ort.InferenceSession(model_path)

            # --- Real inference: preprocess image to [1, 3, 640, 640] float32 ---
            # Model output: [1, 11, 8400] where channels 4-10 are 7 class scores
            # We map the highest-confidence detection class to a grade string.
            # Class order assumed from training: Normal, MAM, SAM, ...
            _ONNX_CLASS_LABELS = ["Normal", "MAM", "SAM", "Edema", "Wasting",
                                   "Stunting", "Underweight"]
            _GRADE_MAP = {"SAM": "SAM", "MAM": "MAM", "Normal": "Normal"}

            if img is not None:
                img_resized = cv2.resize(img, (640, 640))
                img_rgb_onnx = cv2.cvtColor(img_resized, cv2.COLOR_BGR2RGB)
                input_tensor = (img_rgb_onnx.astype(np.float32) / 255.0).transpose(2, 0, 1)
                input_tensor = np.expand_dims(input_tensor, axis=0)  # [1, 3, 640, 640]
                raw_output = session.run(None, {session.get_inputs()[0].name: input_tensor})[0]
                # raw_output shape: [1, 11, 8400] — take class channels (4:11)
                class_scores = raw_output[0, 4:, :]    # [7, 8400]
                # Max confidence across all detections for each class
                max_scores = class_scores.max(axis=1)  # [7]
                best_class_idx = int(np.argmax(max_scores))
                best_score = float(max_scores[best_class_idx])
                raw_label = _ONNX_CLASS_LABELS[best_class_idx] if best_class_idx < len(_ONNX_CLASS_LABELS) else "Unknown"
                model2_flag = _GRADE_MAP.get(raw_label, raw_label)
                logger.info(
                    f"ONNX Model 2: class={raw_label}, score={best_score:.3f}, "
                    f"flag={model2_flag}, repo={_onnx_repo}"
                )
            else:
                model2_flag = "ONNX_SKIPPED"
                logger.warning("ONNX Model 2: skipped because img decode failed")
        except Exception as e:
            logger.warning(f"ONNX Model 2 failed or repo missing: {e}")
            model2_flag = "ONNX_SKIPPED"

        # --- Model 3: Gemini + Groq consensus (text-only, no image re-sent) ---
        # Only the plain-text measurements + ONNX flag travel to the LLMs here.
        # Sending the image again would waste latency and quotas; the visual work
        # was already done by Model 1 (MediaPipe/CV2) and Model 2 (ONNX).
        prompt = (
            "You are an expert pediatric clinical assistant for Indian community health (ASHA). "
            "A photo of a child has already been processed by two upstream models. "
            "You are receiving ONLY the structured numerical outputs -- NOT the image itself.\n"
            f"Structured pipeline measurements:\n"
            f"- Height: {height or 'Not provided'}\n"
            f"- Weight: {calc_weight or 'Not provided'}\n"
            f"- Age: {age or 'Not provided'}\n"
            f"- Gender: {gender or 'Not provided'}\n"
            f"- Computed MUAC (MediaPipe/CV2 Model 1): {round(calculated_muac, 1) if calculated_muac else 'Could not compute automatically'} mm\n"
            f"- Visual Malnutrition Flag (ONNX Model 2): {model2_flag}\n\n"
            "Generate a definitive malnutrition assessment report. Assess:\n"
            "1. Final MUAC in millimeters (muac_mm). Prefer the computed value if available.\n"
            "2. Malnutrition Grade using WHO/NVHCP thresholds:\n"
            "   - Under 115 mm: grade='RED', malnutrition_grade='SAM', severity_label='Severe (SAM)', needs_nrc_referral=True\n"
            "   - 115-124 mm: grade='YELLOW', malnutrition_grade='MAM', severity_label='Moderate (MAM)', needs_nrc_referral=False\n"
            "   - 125 mm or higher: grade='NORMAL', malnutrition_grade='Normal', severity_label='Normal', needs_nrc_referral=False\n"
            "3. Clinical signs inferred from measurements: wasting, edema risk, growth faltering.\n"
            "4. Plain-language explanation combining measurements and the ONNX visual flag. "
            "Include confidence (0-100) and a recommended clinical action."
        )
        consensus = call_report_generation_consensus(
            prompt=prompt,
            response_schema=MuacGradingResponse,
        )
        result = consensus["result"]
        if isinstance(result, dict):
            result = MuacGradingResponse.model_validate(result)
        result.consensus_source = consensus.get("source")
        logger.info(
            "muac_model3_consensus_complete: source=%s, grade=%s",
            result.consensus_source,
            result.grade,
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

        KEY INVARIANT: Every entry in the returned `fields` dict is keyed by the
        stable field_id (e.g. "child_name", "age_months"), NEVER by a translated
        label string.  This is enforced at three layers:
          1. The AI prompt explicitly lists each field as "USE KEY: <id>" so the
             model knows the exact key it must emit for every field.
          2. After inference, a label→id normalisation pass remaps any key that
             matches a known label back to its stable id (handles model drift).
          3. The legacy top-level scalar fields (gender, name, age, etc.) are
             also merged into fields under their own field_ids when a field list
             is present, so they are never silently dropped on the frontend.
        """
        # Build label→id index and schema description for the prompt.
        # The index maps every variation of a field's label to its stable id so
        # we can normalise AI output even if the model ignores the KEY instruction.
        label_to_id: Dict[str, str] = {}  # label_lower → field_id
        schema_desc = ""

        if form_fields:
            try:
                parsed_fields = json.loads(form_fields)
                # Build human-readable field list that emphasises the KEY the AI must use
                field_lines = []
                for f in parsed_fields:
                    fid = str(f.get("id", "")).strip()
                    flabel = str(f.get("label", fid)).strip()
                    ftype = str(f.get("type", "text")).strip()
                    if not fid:
                        continue
                    field_lines.append(
                        f'  - USE KEY: "{fid}"  |  Field label: "{flabel}"  |  Type: {ftype}'
                    )
                    # Index every token of the label (handles bilingual labels like
                    # "Child Name / बालकाचे नाव") plus the raw label itself
                    label_to_id[flabel.lower()] = fid
                    for part in flabel.split("/"):
                        part_clean = part.strip().lower()
                        if part_clean:
                            label_to_id[part_clean] = fid
                    # Also index the id itself so pass-through values survive the map
                    label_to_id[fid.lower()] = fid

                # Auto-alias common synonyms for target field IDs so model variations map cleanly
                known_ids = set(label_to_id.values())
                name_targets = [x for x in ("member_name", "child_name", "mother_name", "patientName", "elderlyName", "deceasedName", "baby_name") if x in known_ids]
                if name_targets:
                    label_to_id.setdefault("name", name_targets[0])
                    label_to_id.setdefault("full name", name_targets[0])
                    label_to_id.setdefault("person name", name_targets[0])
                dob_targets = [x for x in ("date_of_birth", "birth_date", "dob", "dateOfDeath", "lmp_date") if x in known_ids]
                if dob_targets:
                    label_to_id.setdefault("date of birth", dob_targets[0])
                    label_to_id.setdefault("dob", dob_targets[0])
                    label_to_id.setdefault("birth date", dob_targets[0])
                    label_to_id.setdefault("date", dob_targets[0])
                gender_targets = [x for x in ("gender", "baby_gender") if x in known_ids]
                if gender_targets:
                    label_to_id.setdefault("sex", gender_targets[0])
                    label_to_id.setdefault("gender", gender_targets[0])
                rel_targets = [x for x in ("relationship_to_head", "relationship") if x in known_ids]
                if rel_targets:
                    label_to_id.setdefault("relation", rel_targets[0])
                    label_to_id.setdefault("relationship", rel_targets[0])
                phone_targets = [x for x in ("mobile_number", "phone_number") if x in known_ids]
                if phone_targets:
                    label_to_id.setdefault("mobile", phone_targets[0])
                    label_to_id.setdefault("phone", phone_targets[0])

                schema_desc = (
                    "Target Form Fields — you MUST use the exact KEY string shown below "
                    "(the part after 'USE KEY:') as the key in your `fields` output. "
                    "Do NOT use the field label or any translation of it as the key.\n"
                    + "\n".join(field_lines)
                )
            except Exception:
                schema_desc = f"Target Form Fields: {form_fields}"
        else:
            schema_desc = (
                "Extract any recognizable health and identity fields "
                "(name, age, gender, date_of_birth, symptoms, measurements, etc.). "
                "Use concise snake_case English identifiers as keys "
                "(e.g. member_name, child_name, date_of_birth, age, gender, weight_kg)."
            )

        prompt = (
            "You are an expert clinical dictation assistant for Indian ASHA healthcare workers.\n"
            "Listen to this spoken audio (spoken in Marathi, Hindi, or Indian English).\n"
            "CRITICAL REQUIREMENT: ALL returned text (including the transcript and all field values) "
            "MUST BE TRANSLATED TO ENGLISH.\n"
            "1. Transcribe the spoken words and translate the full transcription into English, "
            "storing the English version in `transcript`.\n"
            "2. Extract values for the fields specified below into `fields` (key-value pairs). "
            "IMPORTANT: use the exact KEY string specified for each field — never the label.\n"
            f"{schema_desc}\n"
            "3. Count the number of non-null extracted fields into `fields_detected`.\n"
            "4. DATES: Any extracted date MUST strictly be formatted in ISO 'YYYY-MM-DD' (e.g. '2006-07-25'). Never output dates as text or unpadded numbers.\n"
            "Do not invent values. If a field was not mentioned in the audio, omit it."
        )

        gemini_result = call_multimodal(
            prompt=prompt,
            audio_or_image=audio_bytes,
            mime_type=mime_type,
            response_schema=GeminiVoiceExtractionResponse,
        )

        # ── Normalise keys to stable field_ids ────────────────────────────────
        # The AI may still return a label-string key despite the prompt instruction
        # (model drift, multilingual confusion). Remap any key that matches a
        # known label back to the stable id. Unknown keys pass through as-is.
        raw_fields: Dict[str, str] = {f.key: f.value for f in gemini_result.fields}
        fields_dict: Dict[str, str] = {}
        for raw_key, raw_value in raw_fields.items():
            normalized_id = label_to_id.get(raw_key.lower(), raw_key)
            # Normalize date fields to strict ISO YYYY-MM-DD
            if any(d_kw in normalized_id.lower() for d_kw in ("date", "dob", "edd", "lmp")):
                val = _parse_date_to_iso(raw_value)
            else:
                val = raw_value
            fields_dict[normalized_id] = val

        # ── Merge legacy top-level scalar fields into fields_dict ─────────────
        # The Pydantic schema has top-level gender/name/age/relationship fields
        # from a legacy design. Ensure these also land in fields_dict under the
        # matching field_id so the frontend merges them into form state cleanly.
        parsed_gemini_dob = _parse_date_to_iso(gemini_result.date_of_birth) if gemini_result.date_of_birth else None

        legacy_candidates = {
            "gender": gemini_result.gender,
            "name": gemini_result.name,
            # age comes back as int from the legacy field — stringify for consistency
            "age": str(gemini_result.age) if gemini_result.age is not None else None,
            "date_of_birth": parsed_gemini_dob,
            "relationship": gemini_result.relationship,
            "is_pregnant": str(gemini_result.is_pregnant) if gemini_result.is_pregnant else None,
        }
        for legacy_key, legacy_val in legacy_candidates.items():
            if legacy_val is not None and legacy_val != '':
                if label_to_id:
                    stable_id = label_to_id.get(legacy_key, legacy_key)
                    if stable_id in label_to_id.values() and stable_id not in fields_dict:
                        fields_dict[stable_id] = legacy_val
                else:
                    if legacy_key not in fields_dict:
                        fields_dict[legacy_key] = legacy_val

        # ── Fallback: regex extraction from transcript if date_of_birth is missing ───
        dob_keys = [k for k in fields_dict if any(d in k.lower() for d in ("date_of_birth", "dob", "birth_date", "birthdate"))]
        if not dob_keys and gemini_result.transcript:
            fallback_dob = _extract_dob_from_text(gemini_result.transcript)
            if fallback_dob:
                target_dob_key = "date_of_birth"
                if label_to_id:
                    target_dob_key = label_to_id.get("date_of_birth", label_to_id.get("dob", "date_of_birth"))
                fields_dict[target_dob_key] = fallback_dob
                parsed_gemini_dob = fallback_dob

        final_dob = parsed_gemini_dob or fields_dict.get("date_of_birth") or fields_dict.get("dob") or fields_dict.get("birth_date")

        result = VoiceExtractionResponse(
            transcript=gemini_result.transcript,
            fields=fields_dict,
            fields_detected=gemini_result.fields_detected,
            name=gemini_result.name,
            gender=gemini_result.gender,
            age=gemini_result.age,
            date_of_birth=final_dob,
            is_pregnant=gemini_result.is_pregnant,
            relationship=gemini_result.relationship
        )

        if not result.fields_detected and result.fields:
            result.fields_detected = len(result.fields)

        logger.info(
            "voice_extract_complete: fields_detected=%d keys=%s",
            result.fields_detected,
            list(result.fields.keys()),
        )
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
        """Cross-field consistency check using call_critical_consensus() per Section 2.9.
        
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

        consensus_out = call_critical_consensus(
            prompt=prompt,
            task_type="classification",
            response_schema=CrossFieldValidationResponse,
        )
        
        if consensus_out["flag_for_review"]:
            op1 = consensus_out["result"]["opinion_1"]
            op2 = consensus_out["result"]["opinion_2"]
            conflict_msg = f"AI Disagreement: Opinion 1: {op1.model_dump_json() if hasattr(op1, 'model_dump_json') else op1} | Opinion 2: {op2.model_dump_json() if hasattr(op2, 'model_dump_json') else op2}"
            result = CrossFieldValidationResponse(
                has_conflict=True,
                conflicts=[conflict_msg],
                severity="HIGH",
                suggested_action="Mandatory supervisor review required due to disputed AI consensus."
            )
        else:
            result = consensus_out["result"]

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
        
        Uses call_translation() (IndicTrans2 primary, Groq fallback).
        """
        prompt = (
            "You are a professional medical translator for public health surveys in India.\n"
            "Translate this health survey question or field label into all three languages: "
            "English, Marathi (मराठी), and Hindi (हिंदी).\n"
            f"Input Text: '{text}'\n"
            f"Source language hint: {source_lang or 'auto'}\n"
            "Return JSON with keys 'en', 'mr', and 'hi'. Use culturally clear, accurate health terms."
        )

        return call_translation(
            prompt=prompt,
            response_schema=TranslationResponse,
        )


# Backward-compatible singleton instance
gemini_service = AIService()

