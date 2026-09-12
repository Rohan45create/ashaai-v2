from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any

class VoiceExtractionResponse(BaseModel):
    transcript: str = Field(default="", description="Transcribed audio text")
    fields: Dict[str, Any] = Field(default_factory=dict, description="Extracted key-value form fields")
    fields_detected: int = Field(default=0, description="Total count of detected fields")
    name: Optional[str] = Field(None, description="Legacy field for family member")
    gender: Optional[str] = Field(None, description="Legacy field for gender")
    age: Optional[int] = Field(None, description="Legacy field for age")
    is_pregnant: Optional[bool] = Field(False, description="Legacy field for pregnancy")
    relationship: Optional[str] = Field(None, description="Legacy field for relationship")

class MuacGradingResponse(BaseModel):
    grade: str = Field(..., description="RED for SAM, YELLOW for MAM, NORMAL for Normal")
    malnutrition_grade: str = Field(..., description="SAM, MAM, or Normal")
    severity_label: str = Field(..., description="Human-readable severity label, e.g. 'Severe (SAM)'")
    muac_mm: Optional[float] = Field(None, description="Estimated MUAC measurement in millimeters")
    confidence: float = Field(..., description="Confidence percentage between 0 and 100")
    visible_signs: List[str] = Field(default_factory=list, description="Observed clinical signs such as wasting, swelling, edema")
    recommendation: Optional[str] = Field(None, description="Clinical recommended action")
    needs_nrc_referral: bool = Field(default=False, description="True if Severe Acute Malnutrition (SAM)")
    explanation: Optional[str] = Field(None, description="Clinical reasoning explanation")

class RegisterRow(BaseModel):
    fields: Dict[str, Any] = Field(default_factory=dict, description="Extracted column name to value mapping")
    confidence: float = Field(default=1.0, description="Confidence score for this row between 0.0 and 1.0")
    needs_review: bool = Field(default=False, description="True if any field has confidence < 0.8 or is uncertain")
    name: Optional[str] = Field(None, description="Legacy convenience field")
    age: Optional[int] = Field(None, description="Legacy convenience field")
    gender: Optional[str] = Field(None, description="Legacy convenience field")
    notes: Optional[str] = Field(None, description="Legacy convenience field")

class RegisterOcrResponse(BaseModel):
    register_type: str = Field(default="family_survey", description="Detected or requested register type")
    target_collection: str = Field(default="household_members", description="Target collection/table in system")
    total_rows_found: int = Field(default=0, description="Total number of rows extracted")
    rows: List[RegisterRow] = Field(default_factory=list, description="Extracted rows from register")
    confidence: float = Field(default=1.0, description="Overall confidence score between 0.0 and 1.0")

class AmbientSuggestion(BaseModel):
    field: str = Field(..., description="Target form field ID")
    value: Any = Field(..., description="Suggested value for the field")
    label: Optional[str] = Field(None, description="Human readable label")
    confidence: float = Field(default=0.8, description="Confidence score")

class AmbientResponse(BaseModel):
    type: str = Field(default="suggestions", description="Message type")
    suggestions: List[AmbientSuggestion] = Field(default_factory=list, description="List of suggestion chips")

# ── Text Reasoning Models (Sections 2.7, 2.9, 2.13) ──────────────────────────

class ChatMessage(BaseModel):
    role: str = Field(..., description="Role: user or ai/assistant")
    content: str = Field(..., description="Message text content")

class ChatRequest(BaseModel):
    message: str = Field(..., description="User query message")
    language: Optional[str] = Field(default="en", description="Target language: en, mr, or hi")
    conversation_history: List[ChatMessage] = Field(default_factory=list, description="Previous messages")

class ChatResponse(BaseModel):
    response: str = Field(..., description="Chatbot clinical response")
    source: Optional[str] = Field(default="static_health_knowledge", description="Source of information")

class CrossFieldValidationRequest(BaseModel):
    entity_type: str = Field(..., description="Entity name, e.g. pregnancy, child, visit")
    record: Dict[str, Any] = Field(..., description="Key-value fields of the saved record")

class CrossFieldValidationResponse(BaseModel):
    has_conflict: bool = Field(default=False, description="True if cross-field conflict detected")
    conflicts: List[str] = Field(default_factory=list, description="List of detected logical conflict descriptions")
    severity: str = Field(default="LOW", description="LOW, MEDIUM, HIGH, or CRITICAL")
    suggested_action: Optional[str] = Field(None, description="Suggested action for review")

class TranslationRequest(BaseModel):
    text: str = Field(..., description="Text to translate")
    source_lang: Optional[str] = Field(default=None, description="en, mr, or hi if known")

class TranslationResponse(BaseModel):
    en: str = Field(default="", description="English translation")
    mr: str = Field(default="", description="Marathi translation")
    hi: str = Field(default="", description="Hindi translation")
