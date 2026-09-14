import unittest
from services.session_store import session_manager
from services.supervisor_tools import (
    gather_requirement,
    draft_survey_fields,
    preview_in_language,
    SUPERVISOR_TOOLS,
    SUPERVISOR_TOOL_SCHEMAS
)


class TestSupervisorTools(unittest.TestCase):

    def setUp(self):
        session_manager.clear_all()

    def test_gather_requirement(self):
        sess_id = "sess-sup-1"
        session_manager.create_session(sess_id, role="SUPERVISOR")

        res = gather_requirement(
            topic="Dengue Surveillance",
            target_population="All Households",
            indicators=["Fever onset", "Platelet count", "Hospitalization"],
            session_id=sess_id
        )

        self.assertEqual(res["status"], "REQUIREMENTS_RECORDED")
        self.assertEqual(res["requirements"]["topic"], "Dengue Surveillance")

        # Confirm stored in session
        sess = session_manager.get_session(sess_id)
        self.assertIn("requirements", sess["supervisor_draft"])
        self.assertEqual(sess["supervisor_draft"]["requirements"]["topic"], "Dengue Surveillance")

    def test_draft_survey_fields_is_published_false(self):
        sess_id = "sess-sup-2"
        session_manager.create_session(sess_id, role="SUPERVISOR")

        res = draft_survey_fields(
            survey_title="Postpartum Followup",
            description="Tracking mother and newborn post-delivery",
            session_id=sess_id
        )

        self.assertEqual(res["status"], "DRAFT_CREATED")
        draft = res["survey_draft"]
        self.assertEqual(draft["title_en"], "Postpartum Followup")
        self.assertTrue(len(draft["fields"]) >= 4)

        # STRICT HARNESS CHECK: is_published MUST BE FALSE
        self.assertFalse(draft["is_published"])
        self.assertEqual(draft["draft_status"], "READY_FOR_SUPERVISOR_REVIEW")

        # Check session update
        sess = session_manager.get_session(sess_id)
        self.assertEqual(sess["status"], "READY_FOR_REVIEW")
        self.assertFalse(sess["supervisor_draft"]["survey"]["is_published"])

    def test_preview_in_language(self):
        sess_id = "sess-sup-3"
        session_manager.create_session(sess_id, role="SUPERVISOR")

        # Draft survey
        draft_survey_fields(
            survey_title="Hypertension Screening",
            session_id=sess_id
        )

        # Preview in Marathi
        preview_mr = preview_in_language("MR", session_id=sess_id)
        self.assertEqual(preview_mr["language"], "MR")
        self.assertTrue(len(preview_mr["fields"]) > 0)

        # Preview in Hindi
        preview_hi = preview_in_language("HI", session_id=sess_id)
        self.assertEqual(preview_hi["language"], "HI")

    def test_autonomy_harness_no_publish_tool(self):
        """CRITICAL AUTONOMY POLICY AUDIT:

        Confirm no publish() or publish_survey() tool exists in SUPERVISOR_TOOLS.
        Supervisor must review in SurveyBuilder.jsx and click Publish themselves.
        """
        tool_names = set(SUPERVISOR_TOOLS.keys())
        schema_names = {s["function"]["name"] for s in SUPERVISOR_TOOL_SCHEMAS}

        expected_tools = {
            "gather_requirement",
            "draft_survey_fields",
            "preview_in_language"
        }

        self.assertEqual(tool_names, expected_tools)
        self.assertEqual(schema_names, expected_tools)

        forbidden_tools = ["publish", "publish_survey", "commit_survey", "activate_survey"]
        for forbidden in forbidden_tools:
            self.assertNotIn(forbidden, tool_names)
            self.assertNotIn(forbidden, schema_names)


if __name__ == "__main__":
    unittest.main()
