"""FILL_SURVEY toolset for Conversational Agent.

Enforces Tier 2 Autonomy per docs/ARCHITECTURE.md and docs/AI_AUTONOMY_POLICY.md:
- Toolset: search_survey, open_survey, search_household_member, stage_field_value, get_draft_summary.
- Critical architectural guarantee: There is NO submit() or publish() tool in this toolset.
- stage_field_value writes ONLY to the session-held draft in session_manager.
- Zero writes to household_members, survey_submissions, or any database table.
- Human reviews and taps Submit on the existing review screen.
"""

from typing import Any, Dict, List, Optional
from services.session_store import session_manager


# Standard registered survey catalog
SURVEY_CATALOG = [
    {
        "id": "anc_registration",
        "title": "Maternal Health & ANC Registration",
        "category": "Maternal",
        "fields": [
            {"key": "beneficiary_name", "label": "Beneficiary Name", "type": "text", "required": True},
            {"key": "age", "label": "Age", "type": "number", "required": True},
            {"key": "lmp_date", "label": "Last Menstrual Period (LMP)", "type": "date", "required": True},
            {"key": "edd_date", "label": "Expected Due Date (EDD)", "type": "date", "required": False},
            {"key": "gravida", "label": "Gravida (Total Pregnancies)", "type": "number", "required": True},
            {"key": "systolic_bp", "label": "Systolic BP", "type": "number", "required": True},
            {"key": "diastolic_bp", "label": "Diastolic BP", "type": "number", "required": True},
            {"key": "hemoglobin", "label": "Hemoglobin (g/dL)", "type": "number", "required": True},
            {"key": "high_risk_flag", "label": "High Risk Identified", "type": "boolean", "required": False}
        ]
    },
    {
        "id": "child_growth",
        "title": "Child Growth & Malnutrition Monitoring",
        "category": "Child",
        "fields": [
            {"key": "child_name", "label": "Child Name", "type": "text", "required": True},
            {"key": "age_months", "label": "Age (Months)", "type": "number", "required": True},
            {"key": "gender", "label": "Gender", "type": "select", "options": ["Male", "Female"], "required": True},
            {"key": "weight_kg", "label": "Weight (kg)", "type": "number", "required": True},
            {"key": "height_cm", "label": "Height (cm)", "type": "number", "required": True},
            {"key": "muac_mm", "label": "MUAC (mm)", "type": "number", "required": True},
            {"key": "malnutrition_grade", "label": "Malnutrition Grade", "type": "text", "required": False}
        ]
    },
    {
        "id": "vaccination",
        "title": "Child & Infant Immunization Record",
        "category": "Immunization",
        "fields": [
            {"key": "child_name", "label": "Child Name", "type": "text", "required": True},
            {"key": "vaccine_name", "label": "Vaccine Dose", "type": "text", "required": True},
            {"key": "dose_number", "label": "Dose Number", "type": "number", "required": True},
            {"key": "administered_date", "label": "Date Administered", "type": "date", "required": True},
            {"key": "next_due_date", "label": "Next Due Date", "type": "date", "required": False}
        ]
    },
    {
        "id": "family_survey",
        "title": "Annual Family & Household Survey",
        "category": "Household",
        "fields": [
            {"key": "head_name", "label": "Head of Household", "type": "text", "required": True},
            {"key": "total_members", "label": "Total Members", "type": "number", "required": True},
            {"key": "sanitation_type", "label": "Toilet Facility", "type": "text", "required": True},
            {"key": "water_source", "label": "Drinking Water Source", "type": "text", "required": True},
            {"key": "bpl_card", "label": "BPL / Ration Card Status", "type": "boolean", "required": False}
        ]
    },
    {
        "id": "village_health",
        "title": "Village Health & Sanitation Assessment",
        "category": "Community",
        "fields": [
            {"key": "village_name", "label": "Village Name", "type": "text", "required": True},
            {"key": "clean_water_points", "label": "Clean Water Points", "type": "number", "required": True},
            {"key": "waste_disposal_status", "label": "Waste Disposal Quality", "type": "text", "required": True}
        ]
    }
]

# Sample beneficiary database index for fuzzy search (PII-minimized)
SAMPLE_MEMBERS_INDEX = [
    {"id": "mem-001", "name": "Sunita Devi", "gender": "Female", "age": 24, "village": "Rampur", "temp_id": "TMP-7821"},
    {"id": "mem-002", "name": "Pooja Sharma", "gender": "Female", "age": 28, "village": "Rampur", "temp_id": "TMP-4109"},
    {"id": "mem-003", "name": "Aarav Kumar", "gender": "Male", "age": 3, "village": "Shivaji Nagar", "temp_id": "TMP-9012"},
    {"id": "mem-004", "name": "Ananya Patil", "gender": "Female", "age": 1, "village": "Shivaji Nagar", "temp_id": "TMP-3341"},
    {"id": "mem-005", "name": "Meena Kumari", "gender": "Female", "age": 31, "village": "Kalyan", "temp_id": "TMP-5590"}
]


def search_survey(query: str) -> List[Dict[str, Any]]:
    """Read-only search across survey schemas.

    Never creates or modifies any survey.
    """
    q = (query or "").lower().strip()
    matches = []
    for s in SURVEY_CATALOG:
        if (
            q in s["id"].lower()
            or q in s["title"].lower()
            or q in s["category"].lower()
            or any(q in f["key"].lower() or q in f["label"].lower() for f in s["fields"])
        ):
            matches.append({
                "id": s["id"],
                "title": s["title"],
                "category": s["category"],
                "field_count": len(s["fields"])
            })
    # If no exact match, return all available surveys so user can choose
    if not matches:
        return [
            {"id": s["id"], "title": s["title"], "category": s["category"], "field_count": len(s["fields"])}
            for s in SURVEY_CATALOG
        ]
    return matches


def open_survey(survey_id: str, session_id: str) -> Dict[str, Any]:
    """Load survey field schema into session (read-only schema lookup).

    Attaches active survey metadata to the session draft.
    """
    sid = survey_id.lower().strip()
    survey = next((s for s in SURVEY_CATALOG if s["id"] == sid or sid in s["id"]), None)
    if not survey:
        raise ValueError(f"Survey template '{survey_id}' not found")

    session = session_manager.get_session(session_id)
    if not session:
        session = session_manager.create_session(session_id)

    session_manager.update_session(session_id, {
        "active_survey_id": survey["id"],
        "active_survey_title": survey["title"]
    })

    return {
        "survey_id": survey["id"],
        "title": survey["title"],
        "category": survey["category"],
        "fields": survey["fields"]
    }


def search_household_member(
    name: Optional[str] = None,
    dob: Optional[str] = None,
    gender: Optional[str] = None,
    address: Optional[str] = None
) -> List[Dict[str, Any]]:
    """Read-only fuzzy match lookup for linking beneficiaries.

    Enforces Harness Rule 3 (PII Minimization):
    Never includes raw Aadhaar number or phone number in LLM tool context.
    """
    results = []
    target_name = (name or "").lower().strip()
    target_addr = (address or "").lower().strip()
    target_gender = (gender or "").lower().strip()

    for mem in SAMPLE_MEMBERS_INDEX:
        score = 0
        if target_name and target_name in mem["name"].lower():
            score += 3
        if target_addr and target_addr in mem["village"].lower():
            score += 1
        if target_gender and target_gender == mem["gender"].lower():
            score += 1

        if score > 0 or not target_name:
            results.append({
                "member_id": mem["id"],
                "name": mem["name"],
                "age": mem["age"],
                "gender": mem["gender"],
                "village": mem["village"],
                "temporary_id": mem["temp_id"],
                "match_score": score
            })

    results.sort(key=lambda x: x["match_score"], reverse=True)
    return results[:5]


def stage_field_value(field: str, value: Any, session_id: str) -> Dict[str, Any]:
    """Stage a field value strictly into the session-held draft.

    CRITICAL ARCHITECTURAL GUARANTEE:
    - Writes ONLY to session_manager's session draft.
    - Zero code paths to write to household_members, survey_submissions, or any database table.
    - Human reviews on the existing review screen and taps Submit.
    """
    if not session_id:
        raise ValueError("session_id is required to stage field values")

    session = session_manager.get_session(session_id)
    if not session:
        session = session_manager.create_session(session_id)

    staged = session_manager.stage_field(session_id, field, value)
    return {
        "status": "STAGED",
        "field": field,
        "value": value,
        "staged_count": len(staged),
        "all_staged_fields": staged
    }


def get_draft_summary(session_id: str) -> Dict[str, Any]:
    """Read back what is staged in the current session draft (read-only)."""
    return session_manager.get_draft_summary(session_id)


# Explicit tool executor mapping - NOTE: No submit() or publish() exists here!
FILL_SURVEY_TOOLS = {
    "search_survey": search_survey,
    "open_survey": open_survey,
    "search_household_member": search_household_member,
    "stage_field_value": stage_field_value,
    "get_draft_summary": get_draft_summary
}


# Tool calling schema declarations for LLM function calling
FILL_SURVEY_TOOL_SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "search_survey",
            "description": "Search for an existing health survey or module by title or topic (read-only).",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "Survey title or keyword, e.g. 'ANC', 'maternal', 'vaccination', 'child growth'"}
                },
                "required": ["query"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "open_survey",
            "description": "Open a survey to inspect its required fields and activate it for the session draft (read-only schema).",
            "parameters": {
                "type": "object",
                "properties": {
                    "survey_id": {"type": "string", "description": "The ID or slug of the survey to open (e.g. 'anc_registration', 'child_growth', 'vaccination')"}
                },
                "required": ["survey_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "search_household_member",
            "description": "Search beneficiary / household member records for linking (read-only, PII-minimized).",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "Beneficiary name"},
                    "dob": {"type": "string", "description": "Date of birth or approximate age"},
                    "gender": {"type": "string", "description": "Gender (Female, Male, Other)"},
                    "address": {"type": "string", "description": "Village or hamlet name"}
                }
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "stage_field_value",
            "description": "Stage a response field value into the session draft ONLY. NEVER writes to the real database.",
            "parameters": {
                "type": "object",
                "properties": {
                    "field": {"type": "string", "description": "The exact field key from the survey schema"},
                    "value": {"description": "The value to stage (string, number, or boolean)"}
                },
                "required": ["field", "value"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_draft_summary",
            "description": "Read back the currently staged draft values in this session (read-only).",
            "parameters": {
                "type": "object",
                "properties": {}
            }
        }
    }
]
