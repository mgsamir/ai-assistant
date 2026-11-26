"""
Miss Minutes — Thread-safe JSON memory and log manager.
"""
from __future__ import annotations
import json
import os
import tempfile
import threading
import time
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / "data"
MEMORY_FILE = DATA_DIR / "memory.json"
LOGS_FILE = DATA_DIR / "logs.json"
MAX_LOGS = 500
_LOCK = threading.RLock()


def _load(path: Path, default: Any) -> Any:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value
    except (json.JSONDecodeError, OSError):
        return default


def _save(path: Path, data: Any) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=DATA_DIR)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as tmp:
            json.dump(data, tmp, indent=2, ensure_ascii=False)
            tmp.flush()
            os.fsync(tmp.fileno())
        os.replace(tmp_name, path)
    finally:
        try:
            os.unlink(tmp_name)
        except FileNotFoundError:
            pass


def load_facts() -> list[str]:
    with _LOCK:
        value = _load(MEMORY_FILE, [])
        return value if isinstance(value, list) and all(isinstance(item, str) for item in value) else []


def add_fact(text: str) -> list[str]:
    text = text.strip() if isinstance(text, str) else ""
    with _LOCK:
        facts = load_facts()
        if text and text not in facts:
            facts.append(text[:2000])
            _save(MEMORY_FILE, facts)
        return facts


def remove_fact(index: int) -> list[str]:
    with _LOCK:
        facts = load_facts()
        if 0 <= index < len(facts):
            facts.pop(index)
            _save(MEMORY_FILE, facts)
        return facts


def load_logs() -> list[dict]:
    with _LOCK:
        value = _load(LOGS_FILE, [])
        return value if isinstance(value, list) and all(isinstance(item, dict) for item in value) else []


def add_log(kind: str, detail: str) -> None:
    """Append a bounded activity record without losing concurrent updates."""
    with _LOCK:
        logs = load_logs()
        logs.append({"time": time.strftime("%Y-%m-%d %H:%M:%S"), "kind": str(kind)[:80], "detail": str(detail)[:2000]})
        _save(LOGS_FILE, logs[-MAX_LOGS:])


def recent_logs(n: int = 10, kind: str | None = None) -> list[dict]:
    with _LOCK:
        logs = load_logs()
        if kind:
            logs = [entry for entry in logs if entry.get("kind") == kind]
        return logs[-max(0, n):] if n else []


def context_for_ai(n_logs: int = 5) -> str:
    """Return a short context string for the assistant."""
    with _LOCK:
        facts = load_facts()
        logs = recent_logs(n_logs)
        parts = []
        if facts:
            parts.append("Saved facts:\n" + "\n".join(f"- {fact}" for fact in facts))
        if logs:
            parts.append("Recent activity:\n" + "\n".join(
                f"- [{entry.get('time', '?')}] {entry.get('kind', '?')}: {str(entry.get('detail', ''))[:200]}"
                for entry in logs
            ))
        return "\n\n".join(parts)
