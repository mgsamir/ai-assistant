"""
Ultron — Mode Router (the Brain).

Task dekh kar decide karta hai: fast/cheap model chahiye ya powerful/careful model.
Khud koi API call nahi karta -- sirf bataata hai kaunsa provider+model chuno
(wahi providers jo app/Providers.kt mein already hain: gemini, groq, openrouter).
"""
from __future__ import annotations
import re

HEAVY_HINTS = re.compile(
    r"(?i)\b(security audit|vulnerability|debug|exploit|penetration|pentest|cve|"
    r"architecture|design|refactor|deep|detailed analysis|compare|research|"
    r"complex|step by step|plan)\b"
)


def classify(task: str) -> dict:
    """
    Returns: {"tier": "fast"|"powerful", "provider": str, "model": str, "reason": str}
    """
    is_heavy = bool(HEAVY_HINTS.search(task)) or len(task) > 600
    if is_heavy:
        return {
            "tier": "powerful",
            "provider": "gemini",
            "model": "gemini-2.0-pro-exp",
            "reason": "Security/debug/architecture jaisa complex kaam -- powerful model chuna.",
        }
    return {
        "tier": "fast",
        "provider": "groq",
        "model": "llama-3.3-70b-versatile",
        "reason": "Seedha/chhota sawaal -- fast model se jawab theek hai.",
    }
