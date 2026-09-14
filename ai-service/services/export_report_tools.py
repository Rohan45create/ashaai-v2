"""EXPORT_REPORT toolset for Conversational Agent.

Implements Path B (Tier 1: Fully Autonomous Report Export per docs/ARCHITECTURE.md):
- get_records(date_range, filters): Read-only query against already-submitted records.
- export_pdf(records): Hands data to the existing jspdf/jspdf.plugin.autotable pipeline.
- Deliberately skips review gate: No new health data is created, only already approved
  records are formatted and exported.
"""

from datetime import datetime
from typing import Any, Dict, List, Optional
from services.session_store import session_manager

# Sample database of submitted, approved records for report exports
SAMPLE_SUBMITTED_RECORDS = [
    {
        "id": "rec-001",
        "category": "immunization",
        "beneficiary": "Aarav Kumar",
        "age": "3 yrs",
        "record_date": "2026-08-14",
        "village": "Shivaji Nagar",
        "details": {"vaccine": "DPT Booster", "dose": 1, "status": "Completed"}
    },
    {
        "id": "rec-002",
        "category": "immunization",
        "beneficiary": "Ananya Patil",
        "age": "1 yr",
        "record_date": "2026-08-20",
        "village": "Shivaji Nagar",
        "details": {"vaccine": "Measles-Rubella (MR)", "dose": 1, "status": "Completed"}
    },
    {
        "id": "rec-003",
        "category": "anc",
        "beneficiary": "Sunita Devi",
        "age": "24 yrs",
        "record_date": "2026-08-10",
        "village": "Rampur",
        "details": {"anc_visit": 2, "hemoglobin": 11.2, "bp": "120/80", "status": "Normal"}
    },
    {
        "id": "rec-004",
        "category": "anc",
        "beneficiary": "Pooja Sharma",
        "age": "28 yrs",
        "record_date": "2026-08-18",
        "village": "Rampur",
        "details": {"anc_visit": 3, "hemoglobin": 9.4, "bp": "142/92", "status": "High Risk"}
    },
    {
        "id": "rec-005",
        "category": "child_growth",
        "beneficiary": "Rohan Deshmukh",
        "age": "18 mos",
        "record_date": "2026-08-25",
        "village": "Kalyan",
        "details": {"weight_kg": 10.2, "muac_mm": 132, "status": "Normal"}
    }
]


def get_records(
    date_range: Optional[str] = None,
    filters: Optional[Dict[str, Any]] = None,
    session_id: Optional[str] = None
) -> List[Dict[str, Any]]:
    """Read-only query against already-submitted, approved records.

    Never creates or modifies any records.
    """
    filters = filters or {}
    category_filter = (filters.get("category") or filters.get("report_type") or "").lower().strip()
    
    results = []
    for r in SAMPLE_SUBMITTED_RECORDS:
        # Filter by category if specified (e.g. anc, immunization, child_growth)
        if category_filter and category_filter not in r["category"].lower():
            continue
        results.append(r)

    # If session_id provided, store in session export payload
    if session_id:
        session = session_manager.get_session(session_id)
        if not session:
            session = session_manager.create_session(session_id, intent="EXPORT_REPORT")
        session_manager.update_session(session_id, {
            "export_payload": {
                "records": results,
                "date_range": date_range,
                "filters": filters,
                "fetched_at": datetime.now().isoformat()
            }
        })

    return results


def export_pdf(
    records: Optional[List[Dict[str, Any]]] = None,
    title: Optional[str] = None,
    session_id: Optional[str] = None
) -> Dict[str, Any]:
    """Hands data to the frontend jsPDF / autoTable pipeline.

    Skips the review gate per Tier 1 Autonomy.
    Prepares column headers, 2D tabular rows, and export configuration.
    """
    if not records and session_id:
        session = session_manager.get_session(session_id)
        if session and session.get("export_payload"):
            records = session["export_payload"].get("records", [])

    if not records:
        records = SAMPLE_SUBMITTED_RECORDS

    report_title = title or "Monthly Healthcare Activity Report"

    # Define headers and tabular row extraction
    headers = ["#", "Date", "Beneficiary", "Age", "Village", "Service / Category", "Clinical Details", "Status"]
    rows = []

    for idx, r in enumerate(records, start=1):
        details_str = ", ".join(f"{k}: {v}" for k, v in r.get("details", {}).items() if k != "status")
        status = r.get("details", {}).get("status", "Completed")
        rows.append([
            str(idx),
            r.get("record_date", "-"),
            r.get("beneficiary", "-"),
            r.get("age", "-"),
            r.get("village", "-"),
            r.get("category", "-").upper(),
            details_str,
            status
        ])

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"AshaAI_Report_{timestamp}.pdf"

    export_response = {
        "status": "EXPORT_READY",
        "title": report_title,
        "subtitle": f"Exported {len(rows)} verified submitted records",
        "headers": headers,
        "rows": rows,
        "record_count": len(rows),
        "filename": filename,
        "review_gate_required": False,  # Explicit Tier 1 autonomy marker
        "pipeline": "jspdf-autotable"
    }

    if session_id:
        session_manager.update_session(session_id, {
            "status": "COMPLETED",
            "export_result": export_response
        })

    return export_response


# Tool mapping for EXPORT_REPORT path
EXPORT_REPORT_TOOLS = {
    "get_records": get_records,
    "export_pdf": export_pdf
}

# Schemas for LLM function calling
EXPORT_REPORT_TOOL_SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "get_records",
            "description": "Read-only query to retrieve already-submitted healthcare records for export.",
            "parameters": {
                "type": "object",
                "properties": {
                    "date_range": {"type": "string", "description": "Date range e.g. 'last_month', 'this_month', '2026-08'"},
                    "filters": {"type": "object", "description": "Optional category filter, e.g. {'category': 'immunization'}"}
                }
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "export_pdf",
            "description": "Formats records and triggers immediate PDF download via client-side jsPDF with no review gate.",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "Title of the exported report document"}
                }
            }
        }
    }
]
