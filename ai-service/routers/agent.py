"""FastAPI router for Conversational Agent.

Exposes endpoints for intent classification, turn processing, and session draft inspection.
"""

from typing import Optional
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from services.intent_classifier import classify_intent, IntentClassificationResult
from services.session_store import session_manager

router = APIRouter(prefix="/api/ai/agent", tags=["conversational-agent"])


class ClassifyIntentRequest(BaseModel):
    utterance: str = Field(..., min_length=1, description="Spoken turn or text transcription")
    session_id: Optional[str] = Field(None, description="Optional active session ID to bind intent to")
    use_llm: bool = Field(True, description="Whether to use LLM or deterministic heuristic")


@router.post("/classify-intent", response_model=IntentClassificationResult)
async def classify_agent_intent(req: ClassifyIntentRequest):
    """Classify opening conversational utterance into FILL_SURVEY or EXPORT_REPORT."""
    try:
        result = classify_intent(req.utterance, use_llm=req.use_llm)
        
        # If session_id is provided, record intent in session store
        if req.session_id:
            session = session_manager.get_session(req.session_id)
            if not session:
                session = session_manager.create_session(req.session_id)
            session_manager.update_session(req.session_id, {
                "intent": result.intent.value,
                "detected_survey_name": result.detected_survey_name,
                "detected_report_type": result.detected_report_type
            })
            session_manager.append_history(
                req.session_id,
                role="user",
                content=req.utterance
            )

        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


class AgentTurnRequest(BaseModel):
    session_id: str = Field(..., description="Active session ID")
    utterance: str = Field(..., min_length=1, description="Spoken turn transcription")
    role: str = Field("ASHA", description="User role ('ASHA' or 'SUPERVISOR')")


@router.post("/turn")
async def handle_agent_turn(req: AgentTurnRequest):
    """Process a conversational turn in a continuous hands-free session.

    Dispatches intent classification, tool invocation, draft staging,
    and returns spoken reply text for on-device TTS.
    """
    from services.conversational_agent import process_agent_turn
    try:
        response = process_agent_turn(
            session_id=req.session_id,
            utterance=req.utterance,
            role=req.role
        )
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


class ExecuteToolRequest(BaseModel):
    session_id: str = Field(..., description="Active session ID")
    tool_name: str = Field(..., description="Name of tool to execute")
    arguments: dict = Field(default_factory=dict, description="Arguments for the tool")


@router.post("/tools/execute")
async def execute_agent_tool(req: ExecuteToolRequest):
    """Execute a tool for the active conversational agent session.

    Enforces that tools can only write to session-held drafts and never directly
    commit to database tables.
    """
    from services.fill_survey_tools import FILL_SURVEY_TOOLS
    from services.export_report_tools import EXPORT_REPORT_TOOLS
    from services.supervisor_tools import SUPERVISOR_TOOLS

    all_tools = {**FILL_SURVEY_TOOLS, **EXPORT_REPORT_TOOLS, **SUPERVISOR_TOOLS}

    if req.tool_name not in all_tools:
        raise HTTPException(
            status_code=400,
            detail=f"Unknown or unauthorized tool '{req.tool_name}'. No submit or direct DB write tools exist."
        )

    tool_fn = all_tools[req.tool_name]
    try:
        # Pass session_id if required by tool
        args = dict(req.arguments)
        session_id_tools = [
            "open_survey", "stage_field_value", "get_draft_summary",
            "get_records", "export_pdf",
            "gather_requirement", "draft_survey_fields", "preview_in_language"
        ]
        if req.tool_name in session_id_tools:
            args["session_id"] = req.session_id

        result = tool_fn(**args)
        return {"status": "SUCCESS", "tool": req.tool_name, "result": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/session/{session_id}/draft")
async def get_session_draft(session_id: str):
    """Retrieve the current session draft for the existing review screen."""
    session = session_manager.get_session(session_id)
    if not session:
        raise HTTPException(status_code=404, detail=f"Session '{session_id}' not found")
    return session_manager.get_draft_summary(session_id)

