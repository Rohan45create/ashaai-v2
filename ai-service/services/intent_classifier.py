"""Intent classifier for AshaAI Conversational Agent.

Classifies opening utterances per docs/ARCHITECTURE.md:
- FILL_SURVEY (Path A: Tier 2 survey filling with session-held draft)
- EXPORT_REPORT (Path B: Tier 1 read-only PDF export)

Uses call_text() (near.ai primary -> Groq fallback) with deterministic
fast-path/fallback heuristics for Indian multilingual phrasing (EN, HI, MR).
"""

import json
import re
from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field
from services.provider_client import call_text


class IntentType(str, Enum):
    FILL_SURVEY = "FILL_SURVEY"
    EXPORT_REPORT = "EXPORT_REPORT"
    UNKNOWN = "UNKNOWN"


class IntentClassificationResult(BaseModel):
    intent: IntentType = Field(
        default=IntentType.UNKNOWN,
        description="Classified intent: FILL_SURVEY, EXPORT_REPORT, or UNKNOWN"
    )
    confidence: float = Field(
        default=0.0,
        ge=0.0,
        le=1.0,
        description="Confidence score between 0.0 and 1.0"
    )
    detected_survey_name: Optional[str] = Field(
        default=None,
        description="Identified survey topic, e.g. 'maternal', 'anc', 'child', 'immunization'"
    )
    detected_report_type: Optional[str] = Field(
        default=None,
        description="Identified report type, e.g. 'monthly', 'vaccination', 'anc_summary'"
    )
    detected_date_range: Optional[str] = Field(
        default=None,
        description="Extracted date range, e.g. 'last month', 'this week'"
    )
    detected_member_name: Optional[str] = Field(
        default=None,
        description="Extracted beneficiary or household member name"
    )
    reasoning: Optional[str] = Field(
        default=None,
        description="Brief justification for the classification"
    )


EXPORT_ACTION_KEYWORDS = {
    "export", "download", "pdf", "report", "reports", "print", "ahawal", "ahwal",
    "अहवाल", "रिपोर्ट", "डाउनलोड", "एक्सपोर्ट", "प्रिंट"
}

FILL_ACTION_KEYWORDS = {
    "survey", "surveys", "fill", "form", "forms", "record", "enter", "entry",
    "register", "registration", "bharna", "bhara", "bharnaycha", "kholna", "shuru",
    "सर्वेक्षण", "नोंदणी", "फॉर्म", "भरा", "भरणे", "सुरू"
}


def classify_intent_heuristic(utterance: str) -> IntentClassificationResult:
    """Deterministic heuristic for intent classification.

    Acts as instant fast-path or safety fallback if LLM providers are unavailable.
    """
    text = utterance.lower().strip()
    words = set(re.findall(r"[\w\u0900-\u097F]+", text))

    export_matches = words.intersection(EXPORT_ACTION_KEYWORDS)
    fill_matches = words.intersection(FILL_ACTION_KEYWORDS)

    export_score = len(export_matches)
    fill_score = len(fill_matches)

    # Contextual check: e.g. "generate report", "download pdf"
    if any(phrase in text for phrase in ["generate report", "download report", "monthly summary", "pdf dya"]):
        export_score += 2
    if any(phrase in text for phrase in ["fill survey", "new survey", "start survey", "survey bharna"]):
        fill_score += 2

    # Extract detected survey hints
    survey_name = None
    if any(k in text for k in ["anc", "maternal", "माता", "गर्भवती"]):
        survey_name = "Maternal Health (ANC)"
    elif any(k in text for k in ["child", "बाल", "पोषण"]):
        survey_name = "Child Growth & Nutrition"
    elif any(k in text for k in ["immunization", "vaccin", "लसीकरण", "लस"]):
        survey_name = "Immunization Tracker"

    # Extract detected report hints
    report_type = None
    if any(k in text for k in ["monthly", "मासिक"]):
        report_type = "monthly_summary"
    elif any(k in text for k in ["immunization", "vaccin", "लसीकरण", "लस"]):
        report_type = "vaccination_due"
    elif any(k in text for k in ["anc", "maternal"]):
        report_type = "anc_high_risk"

    # Extract date range hints
    date_range = None
    if any(k in text for k in ["last month", "गेल्या महिन्यात", "पिछले महीने"]):
        date_range = "last_month"
    elif any(k in text for k in ["this month", "या महिन्यात", "इस महीने"]):
        date_range = "this_month"

    if export_score > fill_score:
        return IntentClassificationResult(
            intent=IntentType.EXPORT_REPORT,
            confidence=0.85,
            detected_report_type=report_type,
            detected_date_range=date_range,
            reasoning=f"Matched export keywords: {list(export_matches)}"
        )
    elif fill_score > export_score:
        return IntentClassificationResult(
            intent=IntentType.FILL_SURVEY,
            confidence=0.85,
            detected_survey_name=survey_name,
            reasoning=f"Matched survey filling keywords: {list(fill_matches)}"
        )

    # In case of exact tie where both export and fill keywords appear, export action wins if 'pdf' or 'report'
    if export_score > 0 and ("pdf" in words or "report" in words or "reports" in words):
        return IntentClassificationResult(
            intent=IntentType.EXPORT_REPORT,
            confidence=0.80,
            detected_report_type=report_type,
            detected_date_range=date_range,
            reasoning=f"Resolved tie in favor of report export: {list(export_matches)}"
        )

    return IntentClassificationResult(
        intent=IntentType.UNKNOWN,
        confidence=0.3,
        reasoning="Ambiguous or conversational utterance"
    )


CLASSIFIER_SYSTEM_PROMPT = """You are AshaAI's conversational agent intent classifier.
The user is an ASHA health worker speaking in English, Hindi, or Marathi.
Classify the user's opening utterance into one of:
1. FILL_SURVEY: User wants to fill, record, enter, or start a health survey or household visit.
2. EXPORT_REPORT: User wants to generate, download, export, or print an existing report or PDF.
3. UNKNOWN: Ambiguous, chitchat, or unrelated intent.

Return valid JSON with:
{
  "intent": "FILL_SURVEY" | "EXPORT_REPORT" | "UNKNOWN",
  "confidence": 0.0 - 1.0,
  "detected_survey_name": string or null,
  "detected_report_type": string or null,
  "detected_date_range": string or null,
  "detected_member_name": string or null,
  "reasoning": string
}
"""


def classify_intent(utterance: str, use_llm: bool = True) -> IntentClassificationResult:
    """Classify the user's opening utterance into FILL_SURVEY, EXPORT_REPORT, or UNKNOWN.

    Follows the architecture rule: calls call_text() (near.ai primary -> Groq fallback).
    If LLM call fails or use_llm is False, falls back to deterministic heuristic.
    """
    if not utterance or not utterance.strip():
        return IntentClassificationResult(
            intent=IntentType.UNKNOWN,
            confidence=0.0,
            reasoning="Empty utterance"
        )

    if not use_llm:
        return classify_intent_heuristic(utterance)

    prompt = f"User opening utterance: \"{utterance.strip()}\""

    try:
        raw_response = call_text(
            prompt=prompt,
            system_prompt=CLASSIFIER_SYSTEM_PROMPT,
        )

        # Parse LLM JSON output
        if isinstance(raw_response, str):
            # Extract JSON block if wrapped in markdown
            clean = raw_response.strip()
            if "```json" in clean:
                clean = clean.split("```json")[1].split("```")[0].strip()
            elif "```" in clean:
                clean = clean.split("```")[1].split("```")[0].strip()

            parsed = json.loads(clean)
            intent_str = parsed.get("intent", "").upper()
            if intent_str not in [IntentType.FILL_SURVEY.value, IntentType.EXPORT_REPORT.value]:
                intent_val = IntentType.UNKNOWN
            else:
                intent_val = IntentType(intent_str)

            return IntentClassificationResult(
                intent=intent_val,
                confidence=float(parsed.get("confidence", 0.8)),
                detected_survey_name=parsed.get("detected_survey_name"),
                detected_report_type=parsed.get("detected_report_type"),
                detected_date_range=parsed.get("detected_date_range"),
                detected_member_name=parsed.get("detected_member_name"),
                reasoning=parsed.get("reasoning", "Classified via LLM")
            )
        elif isinstance(raw_response, dict):
            return IntentClassificationResult(**raw_response)

    except Exception as e:
        # Graceful fallback to heuristic if LLM fails or is unconfigured
        res = classify_intent_heuristic(utterance)
        res.reasoning = f"Heuristic fallback after LLM error: {str(e)}"
        return res

    return classify_intent_heuristic(utterance)
