import os
import time
import unittest
from unittest.mock import MagicMock
from services.session_store import (
    InMemorySessionStore,
    RedisSessionStore,
    SessionManager
)


class TestSessionStore(unittest.TestCase):

    def test_in_memory_session_store(self):
        store = InMemorySessionStore()
        session_id = "test-session-123"
        payload = {"role": "ASHA", "intent": "FILL_SURVEY", "staged_fields": {"weight": 14.5}}

        # Save and retrieve
        self.assertTrue(store.save_session(session_id, payload))
        retrieved = store.get_session(session_id)
        self.assertEqual(retrieved, payload)

        # Deep copy test: modifying returned dict does not mutate store directly
        retrieved["staged_fields"]["weight"] = 99.9
        fresh = store.get_session(session_id)
        self.assertEqual(fresh["staged_fields"]["weight"], 14.5)

        # Expiry test
        store.save_session("quick-expire", {"foo": "bar"}, ttl_seconds=0.05)
        time.sleep(0.08)
        self.assertIsNone(store.get_session("quick-expire"))

        # Delete test
        self.assertTrue(store.delete_session(session_id))
        self.assertIsNone(store.get_session(session_id))

    def test_session_manager_fallback_without_redis(self):
        # Without REDIS_URL, must use InMemorySessionStore
        manager = SessionManager(redis_url=None)
        self.assertFalse(manager.is_using_redis())
        self.assertIsInstance(manager.store, InMemorySessionStore)

        # Create session
        session = manager.create_session("sess-asha-1", role="ASHA")
        self.assertEqual(session["role"], "ASHA")
        self.assertEqual(session["status"], "ACTIVE")
        self.assertEqual(session["staged_fields"], {})

        # Stage field value (session draft only)
        manager.stage_field("sess-asha-1", "systolic_bp", 120)
        manager.stage_field("sess-asha-1", "diastolic_bp", 80)

        summary = manager.get_draft_summary("sess-asha-1")
        self.assertEqual(summary["staged_count"], 2)
        self.assertEqual(summary["staged_fields"], {"systolic_bp": 120, "diastolic_bp": 80})

        # Append dialogue turn
        manager.append_history("sess-asha-1", role="user", content="Blood pressure is 120 over 80")
        updated = manager.get_session("sess-asha-1")
        self.assertEqual(len(updated["history"]), 1)
        self.assertEqual(updated["history"][0]["content"], "Blood pressure is 120 over 80")

    def test_session_manager_fallback_with_unreachable_redis(self):
        # Invalid REDIS_URL should not crash, must log warning and fall back to InMemory
        manager = SessionManager(redis_url="redis://non_existent_redis_host:6379/0")
        self.assertFalse(manager.is_using_redis())
        self.assertIsInstance(manager.store, InMemorySessionStore)

        # Should still work seamlessly
        manager.create_session("fallback-sess", role="ASHA")
        self.assertIsNotNone(manager.get_session("fallback-sess"))

    def test_redis_session_store_mock(self):
        # Verify Redis store logic using a mocked redis client
        mock_redis = MagicMock()
        mock_redis.get.return_value = '{"session_id": "redis-1", "role": "ASHA"}'
        mock_redis.set.return_value = True
        mock_redis.delete.return_value = 1

        redis_store = RedisSessionStore(mock_redis)
        retrieved = redis_store.get_session("redis-1")
        self.assertEqual(retrieved["session_id"], "redis-1")
        self.assertEqual(retrieved["role"], "ASHA")

        saved = redis_store.save_session("redis-1", {"role": "SUPERVISOR"})
        self.assertTrue(saved)
        mock_redis.set.assert_called_once()


if __name__ == "__main__":
    unittest.main()
