"""Conversational Agent Multi-Turn Turn Coordinator.

Coordinates the full Conversational Agent loop per docs/ARCHITECTURE.md:
- VAD / STT turn intake
- Intent classification (FILL_SURVEY vs EXPORT_REPORT)
- Path A (FILL_SURVEY, Tier 2): stages draft only -> review screen -> human submit
- Path B (EXPORT_REPORT, Tier 1): read-only query -> direct jsPDF -> no review gate
- Returns spoken reply text for on-device TTS playback
"""

import re
from typing import Any, Dict, Optional
from services.session_store import session_manager
from services.intent_classifier import classify_intent, IntentType
from services.fill_survey_tools import (
    search_survey,
    open_survey,
    search_household_member,
    stage_field_value,
    get_draft_summary
)
from services.export_report_tools import get_records, export_pdf
from services.provider_client import call_text


def _extract_numbers_and_fields(text: str) -> Dict[str, Any]:
    """Helper to extract common clinical values from spoken turn."""
    extracted = {}
    lower = text.lower()

    # Blood Pressure pattern: e.g. "120 over 80", "120/80", "120 आणि 80"
    bp_match = re.search(r"(\d{2,3})\s*(?:over|\/|by|\s+)\s*(\d{2,3})", lower)
    if bp_match and ("pressure" in lower or "bp" in lower or int(bp_match.group(1)) > 80):
        extracted["systolic_bp"] = int(bp_match.group(1))
        extracted["diastolic_bp"] = int(bp_match.group(2))

    # Hemoglobin pattern: e.g. "hemoglobin is 11.5", "hb 10.2"
    hb_match = re.search(r"(?:hemoglobin|hb|रक्त)\s*(?:is|was|झाले)?\s*(\d{1,2}(?:\.\d)?)", lower)
    if hb_match:
        extracted["hemoglobin"] = float(hb_match.group(1))

    # Weight pattern: e.g. "weight is 12 kg", "14 kilo"
    wt_match = re.search(r"(?:weight|वजन)\s*(?:is)?\s*(\d{1,2}(?:\.\d)?)\s*(?:kg|kilo|किलो)?", lower)
    if wt_match:
        extracted["weight_kg"] = float(wt_match.group(1))

    # MUAC pattern: e.g. "muac 125", "muac is 118 mm"
    muac_match = re.search(r"muac\s*(?:is)?\s*(\d{2,3})", lower)
    if muac_match:
        extracted["muac_mm"] = int(muac_match.group(1))

    # Age pattern: e.g. "age 24", "वय २४"
    age_match = re.search(r"(?:age|वय|उम्र)\s*(?:is)?\s*(\d{1,2})", lower)
    if age_match:
        extracted["age"] = int(age_match.group(1))

    return extracted


def process_agent_turn(
    session_id: str,
    utterance: str,
    role: str = "ASHA"
) -> Dict[str, Any]:
    """Process a single conversational turn.

    Coordinates intent classification, tool executions, draft staging,
    and spoken response formulation for on-device TTS.
    """
    session = session_manager.get_session(session_id)
    if not session:
        session = session_manager.create_session(session_id, role=role)

    text = utterance.strip()
    session_manager.append_history(session_id, role="user", content=text)

    # 1. Intent Classification (on first turn or if not yet established)
    intent = session.get("intent")
    if not intent or intent == IntentType.UNKNOWN.value:
        classification = classify_intent(text)
        intent = classification.intent.value
        session_manager.update_session(session_id, {
            "intent": intent,
            "detected_survey_name": classification.detected_survey_name,
            "detected_report_type": classification.detected_report_type
        })
        session = session_manager.get_session(session_id)

    # 2. Routing: Path B (EXPORT_REPORT, Tier 1: direct export, no review gate)
    if intent == IntentType.EXPORT_REPORT.value:
        report_type = session.get("detected_report_type") or "all"
        records = get_records(
            date_range="last_month",
            filters={"category": report_type if report_type != "all" else None},
            session_id=session_id
        )
        export_result = export_pdf(
            records=records,
            title=f"{report_type.replace('_', ' ').title()} Activity Report",
            session_id=session_id
        )
        
        reply_text = (
            f"I have prepared your {report_type.replace('_', ' ')} report with {export_result['record_count']} records. "
            "The PDF is downloading directly to your device."
        )
        session_manager.append_history(session_id, role="assistant", content=reply_text)

        return {
            "session_id": session_id,
            "intent": intent,
            "status": "COMPLETED",
            "reply_text": reply_text,
            "review_gate_required": False,  # Tier 1: No review gate
            "export_payload": export_result,
            "staged_draft": None
        }

    # 3. Routing: Path A (FILL_SURVEY, Tier 2: draft staging only -> review screen)
    active_survey_id = session.get("active_survey_id")
    
    # 3a. Resolve survey if not yet open
    if not active_survey_id:
        survey_hint = session.get("detected_survey_name") or text
        matches = search_survey(survey_hint)
        target_survey = matches[0]["id"] if matches else "anc_registration"
        schema = open_survey(target_survey, session_id)
        active_survey_id = schema["survey_id"]
        session = session_manager.get_session(session_id)

    # 3b. Resolve household member if mentioned and not yet linked
    if not session.get("selected_member_id"):
        for word in text.split():
            if len(word) >= 3 and word.isalpha():
                members = search_household_member(name=word)
                if members:
                    m = members[0]
                    session_manager.update_session(session_id, {
                        "selected_member_id": m["member_id"],
                        "selected_member_name": m["name"]
                    })
                    break
        session = session_manager.get_session(session_id)

    # 3c. Extract clinical/form fields and stage strictly to session draft
    extracted_fields = _extract_numbers_and_fields(text)
    for k, v in extracted_fields.items():
        stage_field_value(k, v, session_id)

    # Refresh session state
    draft = get_draft_summary(session_id)

    # 3d. Check for completion signal
    is_done_signal = any(w in text.lower() for w in [
        "done", "submit", "complete", "finish", "review",
        "झाले", "पूर्ण", "हो गया", "सबमिट"
    ])

    if is_done_signal or draft["staged_count"] >= 3:
        # Handoff to existing review screen
        session_manager.update_session(session_id, {"status": "READY_FOR_REVIEW"})
        
        reply_text = (
            f"All values recorded in your draft for {draft.get('active_survey_title', 'survey')}. "
            f"Staged {draft['staged_count']} fields. "
            "Please review the details on your screen and tap Submit when ready."
        )
        session_manager.append_history(session_id, role="assistant", content=reply_text)

        return {
            "session_id": session_id,
            "intent": IntentType.FILL_SURVEY.value,
            "status": "READY_FOR_REVIEW",
            "reply_text": reply_text,
            "review_gate_required": True,  # Tier 2: Human MUST submit on review screen
            "staged_draft": draft,
            "export_payload": None
        }

    # 3e. Formulate intermediate multi-turn prompt
    member_name = session.get("selected_member_name")
    member_phrase = f" for {member_name}" if member_name else ""
    staged_keys = list(draft["staged_fields"].keys())
    
    if "systolic_bp" in staged_keys and "hemoglobin" not in staged_keys:
        next_prompt = "What is the hemoglobin level?"
    elif not staged_keys:
        next_prompt = "Please state the blood pressure readings or other vital measurements."
    else:
        next_prompt = "Any other observations or is the record complete?"

    reply_text = f"Recorded{member_phrase}: {', '.join(f'{k}: {v}' for k, v in draft['staged_fields'].items())}. {next_prompt}"
    session_manager.append_history(session_id, role="assistant", content=reply_text)

    return {
        "session_id": session_id,
        "intent": IntentType.FILL_SURVEY.value,
        "status": "ACTIVE",
        "reply_text": reply_text,
        "review_gate_required": True,
        "staged_draft": draft,
        "export_payload": None
    }
