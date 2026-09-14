"""Session store for Conversational Agent.

Implements the rule from docs/ARCHITECTURE.md and docs/RULES.md:
- Uses Redis IF REDIS_URL is present and reachable.
- Otherwise gracefully falls back to an in-memory session store.
- Holds session state: accumulating draft, intent, role, and conversation history.
"""

import json
import logging
import os
import threading
import time
from typing import Any, Dict, List, Optional

logger = logging.getLogger("session_store")


class BaseSessionStore:
    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        raise NotImplementedError

    def save_session(self, session_id: str, data: Dict[str, Any], ttl_seconds: int = 86400) -> bool:
        raise NotImplementedError

    def delete_session(self, session_id: str) -> bool:
        raise NotImplementedError

    def clear_all(self) -> None:
        raise NotImplementedError


class InMemorySessionStore(BaseSessionStore):
    """Thread-safe in-memory session store."""

    def __init__(self):
        self._lock = threading.Lock()
        self._sessions: Dict[str, Dict[str, Any]] = {}
        self._expires_at: Dict[str, float] = {}

    def _is_expired(self, session_id: str) -> bool:
        expiry = self._expires_at.get(session_id)
        if expiry and time.time() > expiry:
            self._sessions.pop(session_id, None)
            self._expires_at.pop(session_id, None)
            return True
        return False

    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            if self._is_expired(session_id):
                return None
            data = self._sessions.get(session_id)
            if data is None:
                return None
            return json.loads(json.dumps(data))  # Deep copy

    def save_session(self, session_id: str, data: Dict[str, Any], ttl_seconds: int = 86400) -> bool:
        with self._lock:
            self._sessions[session_id] = json.loads(json.dumps(data))
            self._expires_at[session_id] = time.time() + ttl_seconds
            return True

    def delete_session(self, session_id: str) -> bool:
        with self._lock:
            existed = session_id in self._sessions
            self._sessions.pop(session_id, None)
            self._expires_at.pop(session_id, None)
            return existed

    def clear_all(self) -> None:
        with self._lock:
            self._sessions.clear()
            self._expires_at.clear()


class RedisSessionStore(BaseSessionStore):
    """Redis-backed session store when REDIS_URL is provided."""

    def __init__(self, redis_client):
        self.client = redis_client
        self.prefix = "asha_agent:session:"

    def _key(self, session_id: str) -> str:
        return f"{self.prefix}{session_id}"

    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        try:
            val = self.client.get(self._key(session_id))
            if not val:
                return None
            return json.loads(val)
        except Exception as e:
            logger.error("Redis get_session failed: %s", e)
            return None

    def save_session(self, session_id: str, data: Dict[str, Any], ttl_seconds: int = 86400) -> bool:
        try:
            val = json.dumps(data)
            self.client.set(self._key(session_id), val, ex=ttl_seconds)
            return True
        except Exception as e:
            logger.error("Redis save_session failed: %s", e)
            return False

    def delete_session(self, session_id: str) -> bool:
        try:
            return bool(self.client.delete(self._key(session_id)))
        except Exception as e:
            logger.error("Redis delete_session failed: %s", e)
            return False

    def clear_all(self) -> None:
        try:
            keys = self.client.keys(f"{self.prefix}*")
            if keys:
                self.client.delete(*keys)
        except Exception as e:
            logger.error("Redis clear_all failed: %s", e)


class SessionManager:
    """Unified session manager supporting Redis and in-memory fallback."""

    def __init__(self, redis_url: Optional[str] = None):
        self.store = self._init_store(redis_url)

    def _init_store(self, redis_url: Optional[str] = None) -> BaseSessionStore:
        url = redis_url or os.environ.get("REDIS_URL")
        if url:
            try:
                import redis
                client = redis.from_url(url, decode_responses=True)
                client.ping()
                logger.info("Connected to Redis session store at %s", url)
                return RedisSessionStore(client)
            except Exception as e:
                logger.warning(
                    "REDIS_URL provided but Redis connection failed (%s); falling back to InMemorySessionStore.",
                    e
                )
        return InMemorySessionStore()

    def is_using_redis(self) -> bool:
        return isinstance(self.store, RedisSessionStore)

    def create_session(
        self,
        session_id: str,
        role: str = "ASHA",
        intent: Optional[str] = None
    ) -> Dict[str, Any]:
        """Create a new conversational agent session."""
        now = time.time()
        session_data = {
            "session_id": session_id,
            "role": role,  # 'ASHA' or 'SUPERVISOR'
            "intent": intent,  # 'FILL_SURVEY', 'EXPORT_REPORT', or None (pending classifier)
            "status": "ACTIVE",  # 'ACTIVE', 'READY_FOR_REVIEW', 'COMPLETED'
            "active_survey_id": None,
            "active_survey_title": None,
            "selected_member_id": None,
            "selected_member_name": None,
            "staged_fields": {},  # Staged draft only - NEVER writes to real DB
            "history": [],  # Multi-turn dialogue history
            "export_payload": None,
            "created_at": now,
            "updated_at": now
        }
        self.store.save_session(session_id, session_data)
        return session_data

    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        return self.store.get_session(session_id)

    def update_session(self, session_id: str, updates: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        session = self.get_session(session_id)
        if not session:
            return None
        session.update(updates)
        session["updated_at"] = time.time()
        self.store.save_session(session_id, session)
        return session

    def append_history(
        self,
        session_id: str,
        role: str,
        content: str,
        tool_calls: Optional[List[Dict[str, Any]]] = None,
        tool_results: Optional[List[Dict[str, Any]]] = None
    ) -> Optional[Dict[str, Any]]:
        """Append a conversational turn to dialogue history."""
        session = self.get_session(session_id)
        if not session:
            return None

        turn = {
            "role": role,  # 'user', 'assistant', 'system', 'tool'
            "content": content,
            "timestamp": time.time()
        }
        if tool_calls:
            turn["tool_calls"] = tool_calls
        if tool_results:
            turn["tool_results"] = tool_results

        session.setdefault("history", []).append(turn)
        session["updated_at"] = time.time()
        self.store.save_session(session_id, session)
        return session

    def stage_field(self, session_id: str, field_name: str, value: Any) -> Dict[str, Any]:
        """Stage a value into the session-held draft ONLY.

        Never writes to household_members, survey_submissions, or any real table.
        """
        session = self.get_session(session_id)
        if not session:
            raise ValueError(f"Session {session_id} not found")

        staged = session.setdefault("staged_fields", {})
        staged[field_name] = value
        session["updated_at"] = time.time()
        self.store.save_session(session_id, session)
        return staged

    def get_draft_summary(self, session_id: str) -> Dict[str, Any]:
        """Retrieve current session draft summary for review or agent read-back."""
        session = self.get_session(session_id)
        if not session:
            raise ValueError(f"Session {session_id} not found")

        return {
            "session_id": session_id,
            "intent": session.get("intent"),
            "active_survey_id": session.get("active_survey_id"),
            "active_survey_title": session.get("active_survey_title"),
            "selected_member_id": session.get("selected_member_id"),
            "selected_member_name": session.get("selected_member_name"),
            "staged_fields": session.get("staged_fields", {}),
            "staged_count": len(session.get("staged_fields", {})),
            "status": session.get("status", "ACTIVE")
        }

    def delete_session(self, session_id: str) -> bool:
        return self.store.delete_session(session_id)

    def clear_all(self) -> None:
        self.store.clear_all()


# Global default session manager instance
session_manager = SessionManager()
