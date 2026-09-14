import unittest
from services.session_store import session_manager
from services.fill_survey_tools import (
    search_survey,
    open_survey,
    search_household_member,
    stage_field_value,
    get_draft_summary,
    FILL_SURVEY_TOOLS,
    FILL_SURVEY_TOOL_SCHEMAS
)


class TestFillSurveyTools(unittest.TestCase):

    def setUp(self):
        session_manager.clear_all()

    def test_search_survey_readonly(self):
        results = search_survey("maternal")
        self.assertTrue(len(results) > 0)
        self.assertEqual(results[0]["id"], "anc_registration")
        self.assertIn("Maternal", results[0]["title"])

        child_results = search_survey("malnutrition")
        self.assertTrue(len(child_results) > 0)
        self.assertEqual(child_results[0]["id"], "child_growth")

    def test_open_survey(self):
        sess_id = "sess-open-1"
        session_manager.create_session(sess_id, role="ASHA")
        
        schema = open_survey("anc_registration", sess_id)
        self.assertEqual(schema["survey_id"], "anc_registration")
        self.assertTrue(len(schema["fields"]) >= 5)

        # Check that session metadata was updated
        sess = session_manager.get_session(sess_id)
        self.assertEqual(sess["active_survey_id"], "anc_registration")
        self.assertEqual(sess["active_survey_title"], "Maternal Health & ANC Registration")

    def test_search_household_member_pii_minimization(self):
        matches = search_household_member(name="Sunita")
        self.assertTrue(len(matches) > 0)
        match = matches[0]
        self.assertEqual(match["name"], "Sunita Devi")
        self.assertIn("temporary_id", match)
        
        # PII Minimization rule: full 12-digit Aadhaar must never be present
        self.assertNotIn("aadhaar", match)
        self.assertNotIn("aadhaar_number", match)

    def test_stage_field_value_writes_to_draft_only(self):
        sess_id = "sess-stage-1"
        session_manager.create_session(sess_id, role="ASHA")

        res1 = stage_field_value("systolic_bp", 125, sess_id)
        self.assertEqual(res1["status"], "STAGED")
        self.assertEqual(res1["field"], "systolic_bp")
        self.assertEqual(res1["value"], 125)

        res2 = stage_field_value("diastolic_bp", 82, sess_id)
        self.assertEqual(res2["staged_count"], 2)

        # Confirm session draft has exactly these staged values
        draft = get_draft_summary(sess_id)
        self.assertEqual(draft["staged_count"], 2)
        self.assertEqual(draft["staged_fields"]["systolic_bp"], 125)
        self.assertEqual(draft["staged_fields"]["diastolic_bp"], 82)

    def test_autonomy_harness_no_submit_or_publish_tool(self):
        """CRITICAL AUTONOMY POLICY AUDIT:

        Confirm no submit(), publish(), or real-table write tool exists in FILL_SURVEY_TOOLS.
        """
        tool_names = set(FILL_SURVEY_TOOLS.keys())
        schema_names = {s["function"]["name"] for s in FILL_SURVEY_TOOL_SCHEMAS}

        # Expected tool names per docs/ARCHITECTURE.md
        expected_tools = {
            "search_survey",
            "open_survey",
            "search_household_member",
            "stage_field_value",
            "get_draft_summary"
        }

        self.assertEqual(tool_names, expected_tools)
        self.assertEqual(schema_names, expected_tools)

        # Explicit forbidden tool assertions
        forbidden_tools = ["submit", "publish", "commit", "save_to_database", "insert_record"]
        for forbidden in forbidden_tools:
            self.assertNotIn(forbidden, tool_names)
            self.assertNotIn(forbidden, schema_names)


if __name__ == "__main__":
    unittest.main()
