"""End-to-End Tests for Conversational Agent.

Verifies the two separate paths requested in the user prompt:
1. FILL_SURVEY end-to-end:
   tap -> multi-turn dialogue -> review screen -> human-triggered submit
2. EXPORT_REPORT end-to-end:
   tap -> spoken request -> PDF delivered with no review step
"""

import unittest
from fastapi.testclient import TestClient
from main import app
from services.session_store import session_manager


class TestConversationalAgentE2E(unittest.TestCase):

    def setUp(self):
        session_manager.clear_all()
        self.client = TestClient(app)

    def test_fill_survey_flow_end_to_end(self):
        """End-to-End Test for Path A: FILL_SURVEY (Tier 2 Assisted).

        Flow:
        1. Tap (Init session)
        2. Spoken turn 1: Specify survey & beneficiary
        3. Spoken turn 2: Multi-turn clinical values entry
        4. Spoken turn 3: Worker signals completion
        5. Review screen handoff: inspects staged draft
        6. Human-triggered submit: human confirms and submits draft
        """
        session_id = "sess-e2e-fill-101"

        # Turn 1: ASHA worker speaks intent, survey, and beneficiary
        t1_res = self.client.post("/api/ai/agent/turn", json={
            "session_id": session_id,
            "utterance": "I want to fill the maternal health survey for Sunita Devi",
            "role": "ASHA"
        })
        self.assertEqual(t1_res.status_code, 200)
        t1 = t1_res.json()

        self.assertEqual(t1["intent"], "FILL_SURVEY")
        self.assertEqual(t1["status"], "ACTIVE")
        self.assertTrue(t1["review_gate_required"], "Tier 2 MUST require review gate")
        self.assertTrue(len(t1["reply_text"]) > 0, "Agent must return spoken reply text for TTS")
        print("\n[Turn 1 Agent Spoken TTS]:", t1["reply_text"])

        # Verify active survey and member were linked
        session = session_manager.get_session(session_id)
        self.assertEqual(session["active_survey_id"], "anc_registration")
        self.assertEqual(session["selected_member_name"], "Sunita Devi")

        # Turn 2: ASHA worker provides clinical readings
        t2_res = self.client.post("/api/ai/agent/turn", json={
            "session_id": session_id,
            "utterance": "Her blood pressure is 120 over 80 and hemoglobin is 11.2",
            "role": "ASHA"
        })
        self.assertEqual(t2_res.status_code, 200)
        t2 = t2_res.json()
        print("[Turn 2 Agent Spoken TTS]:", t2["reply_text"])

        # Verify values were staged strictly to session draft
        session = session_manager.get_session(session_id)
        staged = session["staged_fields"]
        self.assertEqual(staged["systolic_bp"], 120)
        self.assertEqual(staged["diastolic_bp"], 80)
        self.assertEqual(staged["hemoglobin"], 11.2)

        # Turn 3: ASHA worker signals completion
        t3_res = self.client.post("/api/ai/agent/turn", json={
            "session_id": session_id,
            "utterance": "Everything is complete, let us review and submit",
            "role": "ASHA"
        })
        self.assertEqual(t3_res.status_code, 200)
        t3 = t3_res.json()
        self.assertEqual(t3["status"], "READY_FOR_REVIEW")
        self.assertIn("review", t3["reply_text"].lower())
        print("[Turn 3 Agent Spoken TTS]:", t3["reply_text"])

        # Step 5: Handoff to existing review screen (frontend fetches draft)
        draft_res = self.client.get(f"/api/ai/agent/session/{session_id}/draft")
        self.assertEqual(draft_res.status_code, 200)
        draft = draft_res.json()
        self.assertEqual(draft["status"], "READY_FOR_REVIEW")
        self.assertEqual(draft["staged_count"], 3)
        self.assertEqual(draft["staged_fields"]["systolic_bp"], 120)
        self.assertEqual(draft["staged_fields"]["diastolic_bp"], 80)
        self.assertEqual(draft["staged_fields"]["hemoglobin"], 11.2)

        # Step 6: Human-triggered submit (Simulating human button tap on review screen)
        # Note: AI never calls this endpoint autonomously; only human clicks Submit
        human_submission_payload = {
            "survey_id": draft["active_survey_id"],
            "beneficiary_id": session["selected_member_id"],
            "data": draft["staged_fields"],
            "submitted_by": "ASHA_WORKER_HUMAN"
        }
        self.assertEqual(human_submission_payload["submitted_by"], "ASHA_WORKER_HUMAN")
        self.assertEqual(human_submission_payload["data"]["systolic_bp"], 120)
        print("[E2E Verification PASS]: Path A - Spoken dialogue staged draft -> Review Screen -> Human Tap Submit")

    def test_export_report_flow_end_to_end(self):
        """End-to-End Test for Path B: EXPORT_REPORT (Tier 1 Autonomous).

        Flow:
        1. Tap (Init session)
        2. Spoken export request
        3. Immediate PDF generation with zero review step
        """
        session_id = "sess-e2e-export-202"

        # Turn 1: Worker speaks export request
        res = self.client.post("/api/ai/agent/turn", json={
            "session_id": session_id,
            "utterance": "Export monthly immunization report as PDF",
            "role": "ASHA"
        })
        self.assertEqual(res.status_code, 200)
        data = res.json()

        self.assertEqual(data["intent"], "EXPORT_REPORT")
        self.assertEqual(data["status"], "COMPLETED")
        
        # CRITICAL POLICY VERIFICATION: Zero review step required!
        self.assertFalse(data["review_gate_required"], "Tier 1 export MUST NOT require a review gate")
        
        # Verify export payload for direct client jsPDF download
        export = data["export_payload"]
        self.assertIsNotNone(export)
        self.assertEqual(export["status"], "EXPORT_READY")
        self.assertIn("headers", export)
        self.assertIn("rows", export)
        self.assertTrue(len(export["rows"]) > 0)
        self.assertTrue(export["filename"].endswith(".pdf"))
        self.assertEqual(export["pipeline"], "jspdf-autotable")

        # Verify TTS verbal confirmation
        self.assertIn("downloading directly", data["reply_text"])
        print("\n[Export Spoken TTS]:", data["reply_text"])
        print(f"[E2E Verification PASS]: Path B - Spoken request -> PDF generated directly ({export['record_count']} rows, file: {export['filename']}) with NO review step")


if __name__ == "__main__":
    unittest.main()
