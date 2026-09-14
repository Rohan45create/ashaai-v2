"""Supervisor variant toolset for Conversational Agent.

Implements Tier 2 Autonomy per docs/ARCHITECTURE.md lines 259-263 and
docs/AI_AUTONOMY_POLICY.md Section "Voice-to-Survey-Builder (admin side)":
- Tools: gather_requirement, draft_survey_fields, preview_in_language.
- CRITICAL ARCHITECTURAL GUARANTEE: NO publish() tool exists in this toolset.
- Populates draft state only (is_published = False).
- Zero writes to survey_templates database table.
- Supervisor reviews and explicitly clicks Publish in the existing Survey Builder UI.
"""

import time
from typing import Any, Dict, List, Optional
from services.session_store import session_manager


def gather_requirement(
    topic: str,
    target_population: Optional[str] = None,
    indicators: Optional[List[str]] = None,
    session_id: Optional[str] = None
) -> Dict[str, Any]:
    """Gather and refine survey requirements from spoken supervisor dialogue (read-only)."""
    requirements = {
        "topic": topic,
        "target_population": target_population or "General Beneficiaries",
        "indicators": indicators or [],
        "captured_at": time.time()
    }

    if session_id:
        session = session_manager.get_session(session_id)
        if not session:
            session = session_manager.create_session(session_id, role="SUPERVISOR")
        
        draft = session.setdefault("supervisor_draft", {})
        draft["requirements"] = requirements
        session_manager.update_session(session_id, {"supervisor_draft": draft})

    return {
        "status": "REQUIREMENTS_RECORDED",
        "requirements": requirements
    }


def draft_survey_fields(
    survey_title: str,
    description: Optional[str] = None,
    proposed_fields: Optional[List[Dict[str, Any]]] = None,
    session_id: Optional[str] = None
) -> Dict[str, Any]:
    """Draft survey questions and field types for the Survey Builder UI.

    ARCHITECTURAL GUARANTEES:
    - Writes ONLY to session draft state.
    - Zero write path to survey_templates table.
    - is_published is strictly False.
    - Feeds directly into frontend SurveyBuilder.jsx state.
    """
    fields = []
    input_fields = proposed_fields or [
        {"key": "beneficiary_name", "label_en": "Beneficiary Name", "label_mr": "लाभार्थी नाव", "label_hi": "लाभार्थी का नाम", "type": "text", "required": True},
        {"key": "age", "label_en": "Age", "label_mr": "वय", "label_hi": "उम्र", "type": "number", "required": True},
        {"key": "symptoms", "label_en": "Symptoms Observed", "label_mr": "लक्षणे", "label_hi": "लक्षण", "type": "text", "required": True},
        {"key": "referral_needed", "label_en": "Referral Required?", "label_mr": "संदर्भ आवश्यक आहे का?", "label_hi": "रेफरल की आवश्यकता है?", "type": "boolean", "required": False}
    ]

    for idx, f in enumerate(input_fields, start=1):
        fields.append({
            "id": int(time.time() * 1000) + idx,
            "type": f.get("type", "text"),
            "label_en": f.get("label_en", f.get("label", "Question")),
            "label_mr": f.get("label_mr", "नवीन प्रश्न"),
            "label_hi": f.get("label_hi", "नया सवाल"),
            "required": bool(f.get("required", False)),
            "options_en": f.get("options_en", ""),
            "options_mr": f.get("options_mr", ""),
            "options_hi": f.get("options_hi", "")
        })

    survey_draft = {
        "title_en": survey_title,
        "title_mr": f"{survey_title} (मराठी मसुदा)",
        "title_hi": f"{survey_title} (हिंदी प्रारूप)",
        "description": description or "",
        "fields": fields,
        "is_published": False,  # Strict Tier 2 policy requirement
        "draft_status": "READY_FOR_SUPERVISOR_REVIEW",
        "updated_at": time.time()
    }

    if session_id:
        session = session_manager.get_session(session_id)
        if not session:
            session = session_manager.create_session(session_id, role="SUPERVISOR")

        session_draft = session.setdefault("supervisor_draft", {})
        session_draft["survey"] = survey_draft
        session_manager.update_session(session_id, {
            "supervisor_draft": session_draft,
            "status": "READY_FOR_REVIEW"
        })

    return {
        "status": "DRAFT_CREATED",
        "survey_draft": survey_draft,
        "instructions": "Draft is loaded into Survey Builder. Supervisor must click Publish in the UI."
    }


def preview_in_language(
    language: str,
    session_id: Optional[str] = None
) -> Dict[str, Any]:
    """Preview survey fields in the specified language (EN, MR, HI).

    Allows spoken or visual verification before human supervisor publishes.
    """
    lang = language.upper().strip()
    if lang not in ["EN", "MR", "HI"]:
        lang = "EN"

    fields_preview = []
    survey_title = "Untitled Survey"

    if session_id:
        session = session_manager.get_session(session_id)
        if session and session.get("supervisor_draft", {}).get("survey"):
            survey = session["supervisor_draft"]["survey"]
            survey_title = survey.get(f"title_{lang.lower()}", survey.get("title_en", "Untitled"))
            for f in survey.get("fields", []):
                fields_preview.append({
                    "id": f.get("id"),
                    "label": f.get(f"label_{lang.lower()}", f.get("label_en")),
                    "type": f.get("type"),
                    "required": f.get("required")
                })

    return {
        "language": lang,
        "title": survey_title,
        "fields": fields_preview,
        "count": len(fields_preview)
    }


# Supervisor toolset mapping - NOTE: NO publish() tool!
SUPERVISOR_TOOLS = {
    "gather_requirement": gather_requirement,
    "draft_survey_fields": draft_survey_fields,
    "preview_in_language": preview_in_language
}

# Schemas for LLM function calling
SUPERVISOR_TOOL_SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "gather_requirement",
            "description": "Gather survey requirements, scope, target population, and key metrics from supervisor voice input.",
            "parameters": {
                "type": "object",
                "properties": {
                    "topic": {"type": "string", "description": "Core survey topic, e.g. 'Dengue Outbreak Surveillance'"},
                    "target_population": {"type": "string", "description": "Target population, e.g. 'Children under 5'"},
                    "indicators": {"type": "array", "items": {"type": "string"}, "description": "Key indicators to capture"}
                },
                "required": ["topic"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "draft_survey_fields",
            "description": "Generate drafted survey questions and field types for the Survey Builder UI (draft only, is_published=False).",
            "parameters": {
                "type": "object",
                "properties": {
                    "survey_title": {"type": "string", "description": "Title of the proposed survey"},
                    "description": {"type": "string", "description": "Brief description of the survey purpose"},
                    "proposed_fields": {"type": "array", "items": {"type": "object"}, "description": "List of drafted field objects"}
                },
                "required": ["survey_title"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "preview_in_language",
            "description": "Preview the drafted survey in a specific language (EN, MR, or HI).",
            "parameters": {
                "type": "object",
                "properties": {
                    "language": {"type": "string", "description": "Language code: 'EN', 'MR', or 'HI'"}
                },
                "required": ["language"]
            }
        }
    }
]
