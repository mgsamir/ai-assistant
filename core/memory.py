"""
Miss Minutes — Memory & Log Manager.

Pichli baatein, error logs aur scan results ko data/memory.json aur data/logs.json
mein save karta hai (isi repo ke andar, taaki phone ke GitHub tools aur PC dashboard
dono isi data ko dekh/badal sakein — alag-alag storage nahi).
"""
from __future__ import annotations
import json
import os
import time
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / "data"
MEMORY_FILE = DATA_DIR / "memory.json"
LOGS_FILE = DATA_DIR / "logs.json"
MAX_LOGS = 500


def _load(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return default


def _save(path: Path, data: Any) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    os.replace(tmp, path)  # atomic — crash ke beech corrupt file na bache


def load_facts() -> list[str]:
    return _load(MEMORY_FILE, [])


def add_fact(text: str) -> list[str]:
    facts = load_facts()
    text = text.strip()
    if text and text not in facts:
        facts.append(text)
        _save(MEMORY_FILE, facts)
    return facts


def remove_fact(index: int) -> list[str]:
    facts = load_facts()
    if 0 <= index < len(facts):
        facts.pop(index)
        _save(MEMORY_FILE, facts)
    return facts


def load_logs() -> list[dict]:
    return _load(LOGS_FILE, [])


def add_log(kind: str, detail: str) -> None:
    """kind: 'command' | 'scan' | 'error' | 'web_check' | 'task'"""
    logs = load_logs()
    logs.append({"time": time.strftime("%Y-%m-%d %H:%M:%S"), "kind": kind, "detail": detail[:2000]})
    logs = logs[-MAX_LOGS:]
    _save(LOGS_FILE, logs)


def recent_logs(n: int = 10, kind: str | None = None) -> list[dict]:
    logs = load_logs()
    if kind:
        logs = [l for l in logs if l.get("kind") == kind]
    return logs[-n:]


def context_for_ai(n_logs: int = 5) -> str:
    """Naya task shuru hone se pehle AI ko dene ke liye chhota context."""
    facts = load_facts()
    logs = recent_logs(n_logs)
    parts = []
    if facts:
        parts.append("Saved facts:\n" + "\n".join(f"- {f}" for f in facts))
    if logs:
        parts.append("Recent activity:\n" + "\n".join(f"- [{l['time']}] {l['kind']}: {l['detail'][:200]}" for l in logs))
    return "\n\n".join(parts)
