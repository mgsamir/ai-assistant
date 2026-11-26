"""
Vision — Guardrails & Security Module.

Default-deny checks for the local lab runner. Network URLs are restricted to
publicly-routable destinations, including DNS-resolved addresses, to reduce SSRF
risk. Redirect targets must be checked independently by the caller.
"""
from __future__ import annotations

import ipaddress
import re
import shlex
import socket
import urllib.parse

# Commands with only read/inspection behavior. Network and service-control
# programs are intentionally excluded: their option grammars are too broad to
# safely validate with a generic shell-command allowlist.
ALLOWED_BINARIES = {
    "ls", "pwd", "whoami", "id", "uptime", "uname", "date", "df", "du", "free",
    "ps", "top", "htop", "who", "w", "last", "ss", "netstat",
    "cat", "head", "tail", "grep", "find", "stat", "file", "wc",
}

# Shell syntax is never accepted; commands are invoked with shell=False too.
SHELL_META = set(";&|`$()< >\n\r")


def is_safe(cmd: str) -> tuple[bool, str]:
    """Return (allowed, reason) for a single read-only command."""
    if not isinstance(cmd, str) or not cmd.strip():
        return False, "Khaali command."
    if any(ch in cmd for ch in SHELL_META):
        return False, "Shell metacharacters allowed nahi hain — ek simple command bhejo."
    try:
        tokens = shlex.split(cmd)
    except ValueError as exc:
        return False, f"Command parse nahi hui: {exc}"
    if not tokens:
        return False, "Khaali command."
    binary = tokens[0].rsplit("/", 1)[-1]
    if binary not in ALLOWED_BINARIES:
        return False, f'"{binary}" allowlist me nahi hai. Allowed: {", ".join(sorted(ALLOWED_BINARIES))}'
    # Block options that make otherwise observational tools mutate data or
    # execute another program. Shell metacharacter checks alone are not enough.
    args = tokens[1:]
    if binary == "find" and any(a in {"-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprintf"} for a in args):
        return False, "find ke destructive/executable options allowed nahi hain."
    if binary in {"head", "tail", "cat", "grep", "wc", "stat", "file", "du", "df", "ls", "find"} and any(a == "--help" for a in args):
        return True, ""
    return True, ""


def is_safe_url(url: str) -> tuple[bool, str]:
    """Allow only HTTP(S) URLs whose host resolves exclusively to public IPs."""
    if not isinstance(url, str) or not url or len(url) > 2048 or any(ord(c) < 0x20 for c in url):
        return False, "URL khaali, bahut lamba, ya invalid hai."
    try:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme.lower() not in {"http", "https"} or not parsed.hostname:
            return False, "Sirf valid http:// ya https:// URL allowed hai."
        if parsed.username is not None or parsed.password is not None:
            return False, "URL mein username/password allowed nahi hai."
        host = parsed.hostname.rstrip(".").lower()
        if not host or host == "localhost" or host.endswith(".localhost") or host.endswith(".local"):
            return False, "Private/internal network address allowed nahi hai."
        try:
            addresses = {ipaddress.ip_address(host)}
        except ValueError:
            try:
                records = socket.getaddrinfo(host, parsed.port or (443 if parsed.scheme.lower() == "https" else 80), type=socket.SOCK_STREAM)
                addresses = {ipaddress.ip_address(record[4][0].split("%", 1)[0]) for record in records}
            except (OSError, ValueError):
                return False, "URL host resolve nahi hua, isliye request block ki."
        if not addresses or any(not address.is_global for address in addresses):
            return False, "Private/internal network address check_web_status se allowed nahi."
    except (ValueError, UnicodeError):
        return False, "URL valid nahi hai."
    return True, ""
