import unittest
from services.session_store import session_manager
from services.export_report_tools import (
    get_records,
    export_pdf,
    EXPORT_REPORT_TOOLS,
    EXPORT_REPORT_TOOL_SCHEMAS
)


class TestExportReportTools(unittest.TestCase):

    def setUp(self):
        session_manager.clear_all()

    def test_get_records_read_only(self):
        sess_id = "sess-export-1"
        session_manager.create_session(sess_id, intent="EXPORT_REPORT")

        # Query all submitted records
        records = get_records(date_range="last_month", session_id=sess_id)
        self.assertTrue(len(records) >= 4)
        self.assertEqual(records[0]["category"], "immunization")

        # Query with category filter
        anc_records = get_records(filters={"category": "anc"}, session_id=sess_id)
        self.assertTrue(len(anc_records) >= 2)
        for r in anc_records:
            self.assertEqual(r["category"], "anc")

        # Verify session export_payload was updated
        sess = session_manager.get_session(sess_id)
        self.assertIsNotNone(sess["export_payload"])
        self.assertEqual(len(sess["export_payload"]["records"]), len(anc_records))

    def test_export_pdf_skips_review_gate(self):
        sess_id = "sess-export-2"
        session_manager.create_session(sess_id, intent="EXPORT_REPORT")

        # First retrieve records
        records = get_records(filters={"category": "immunization"}, session_id=sess_id)
        
        # Then trigger PDF export
        result = export_pdf(records=records, title="Immunization Due Report", session_id=sess_id)

        self.assertEqual(result["status"], "EXPORT_READY")
        self.assertEqual(result["title"], "Immunization Due Report")
        self.assertIn("headers", result)
        self.assertIn("rows", result)
        self.assertTrue(len(result["rows"]) > 0)
        self.assertTrue(result["filename"].endswith(".pdf"))

        # Tier 1 Autonomy check: Must explicitly state review gate is NOT required
        self.assertFalse(result["review_gate_required"])

        # Check session status transition to COMPLETED
        sess = session_manager.get_session(sess_id)
        self.assertEqual(sess["status"], "COMPLETED")

    def test_export_report_tool_contracts(self):
        tool_names = set(EXPORT_REPORT_TOOLS.keys())
        schema_names = {s["function"]["name"] for s in EXPORT_REPORT_TOOL_SCHEMAS}

        expected_tools = {"get_records", "export_pdf"}
        self.assertEqual(tool_names, expected_tools)
        self.assertEqual(schema_names, expected_tools)

        # Confirm no submit or draft tools in export toolset
        self.assertNotIn("submit", tool_names)
        self.assertNotIn("stage_field_value", tool_names)


if __name__ == "__main__":
    unittest.main()
