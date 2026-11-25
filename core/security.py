"""
Vision — Guardrails & Security Module.

Ek hi kaam: koi bhi command/action chalne se PEHLE check karo ki wo surakshit hai ya nahi.
Design: default-deny. Sirf ALLOWED list me jo hai wo chalega, baaki sab block.
- Sirf localhost/127.0.0.1 par network-targeting tools chal sakte hain (koi third-party IP/host scan nahi).
- Destructive commands (rm -rf, mkfs, dd, shutdown, iptables flush, sudo, ...) hamesha block.
- Shell metacharacters (; | & ` $() > <) block — taaki koi command allowlist ko bypass na kar sake
  (jaise "ls; rm -rf /" allowlist me "ls" dikhega lekin asal me kuch aur bhi karega).
"""
from __future__ import annotations
import re
import shlex
import urllib.parse

# Jo binaries chalne ki ijaazat hai (sirf inspection/monitoring, kuch bhi destructive nahi)
ALLOWED_BINARIES = {
    "ls", "pwd", "whoami", "id", "uptime", "uname", "date", "df", "du", "free",
    "ps", "top", "htop", "who", "w", "last",
    "ss", "netstat", "ip", "ifconfig", "route",
    "systemctl", "journalctl", "service",
    "cat", "head", "tail", "grep", "find", "stat", "file", "wc",
    "ping", "curl", "wget", "nmap", "traceroute", "dig", "nslookup", "host",
}

# Binaries jo kisi host/IP ko target karte hain — inka target sirf localhost/loopback ho sakta hai
NETWORK_TOOLS = {"ping", "curl", "wget", "nmap", "traceroute", "dig", "nslookup", "host", "nc", "netcat"}
LOOPBACK_HOSTS = {"127.0.0.1", "localhost", "::1", "0.0.0.0"}

# Ye binaries/patterns kabhi bhi nahi chalenge, allowlist me ho ya na ho
DENY_PATTERNS = [
    r"\brm\b.*-rf", r"\bmkfs\b", r"\bdd\b", r"\bshutdown\b", r"\breboot\b",
    r"\bsudo\b", r"\bsu\b\s", r"\bchmod\b.*-R.*/", r"\bchown\b.*-R.*/",
    r"\biptables\b.*-F", r"\bmount\b", r"\bumount\b", r":(){ :|:& };:",
    r"\bkillall\b", r"\bpkill\b", r"\bfdisk\b", r"\bparted\b", r"\bmkswap\b",
    r"\bcrontab\b", r"\buseradd\b", r"\buserdel\b", r"\bpasswd\b",
    r"\bnmap\b.*(-sS|-O|--script)",  # nmap ke aggressive/exploit scripts kabhi nahi
]

SHELL_META = set(";&|`$()<>\n")


def _extract_targets(tokens: list[str]) -> list[str]:
    """Command ke arguments me se jo IP/host/URL-jaisa dikhe wo nikaal lo (host ke roop mein)."""
    targets = []
    ip_re = re.compile(r"^\d{1,3}(\.\d{1,3}){3}$")
    host_re = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9\-\.]*\.[a-zA-Z]{2,}$")
    for raw in tokens[1:]:
        if raw.startswith("-"):
            continue
        t = raw
        if "://" in t:  # curl/wget jaise tools ke liye URL se host nikaalo
            t = urllib.parse.urlparse(t).hostname or t
        t = t.split(":")[0].split("/")[0]  # host:port ya host/path se sirf host
        if ip_re.match(t) or host_re.match(t) or t in LOOPBACK_HOSTS:
            targets.append(t)
    return targets


def is_safe(cmd: str) -> tuple[bool, str]:
    """(True, "") agar command chalayi ja sakti hai, warna (False, wajah)."""
    if not cmd or not cmd.strip():
        return False, "Khaali command."

    if any(ch in cmd for ch in SHELL_META):
        return False, "Shell metacharacters (; | & ` $() > <) allowed nahi hain — ek simple command bhejo."

    for pat in DENY_PATTERNS:
        if re.search(pat, cmd):
            return False, f"Ye command hamesha block hai (destructive/dangerous pattern: {pat})."

    try:
        tokens = shlex.split(cmd)
    except ValueError as e:
        return False, f"Command parse nahi hui: {e}"
    if not tokens:
        return False, "Khaali command."

    binary = tokens[0].split("/")[-1]  # /usr/bin/ls -> ls
    if binary not in ALLOWED_BINARIES:
        return False, f"\"{binary}\" allowlist me nahi hai. Allowed: {', '.join(sorted(ALLOWED_BINARIES))}"

    if binary in NETWORK_TOOLS:
        targets = _extract_targets(tokens)
        for t in targets:
            if t not in LOOPBACK_HOSTS:
                return False, f"\"{binary}\" sirf localhost par chal sakta hai, \"{t}\" par nahi (third-party host scan allowed nahi)."

    return True, ""


def is_safe_url(url: str) -> tuple[bool, str]:
    """check_web_status jaise HTTP check ke liye — koi internal/private network na ho."""
    m = re.match(r"^https?://([^/:]+)", url)
    if not m:
        return False, "Sirf http:// ya https:// URL allowed hai."
    host = m.group(1)
    private_re = re.compile(r"^(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.|169\.254\.|localhost$|0\.0\.0\.0$)")
    if private_re.match(host):
        return False, "Private/internal network address check_web_status se allowed nahi (scanning jaisa lagega)."
    return True, ""
