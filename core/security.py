"""
Vision — Guardrails & Security Module.

Default-deny checks for the local lab runner. Network URLs are restricted to
publicly-routable destinations, including DNS-resolved addresses, to reduce SSRF
risk. File-reading commands are limited to approved roots and sensitive files
are denied even when they are inside an approved root.
"""
from __future__ import annotations

import ipaddress
import os
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

PROJECT_ROOT = os.path.realpath(os.path.join(os.path.dirname(__file__), os.pardir))
ALLOWED_PATHS = ("/var/log", "/tmp", PROJECT_ROOT)
PATH_COMMANDS = {"cat", "head", "tail", "grep", "ls", "du", "stat", "file", "wc", "find"}

# Shell syntax is never accepted; commands are invoked with shell=False too.
SHELL_META = set(";&|`$()< >\n\r") - {" "}

# Options which consume a following argument. Paths supplied to file-reading
# options (-f for grep, for example) are also checked separately below.
_VALUE_OPTIONS = {
    "head": {"-n", "--lines", "-c", "--bytes"},
    "tail": {"-n", "--lines", "-c", "--bytes"},
    "grep": {"-e", "--regexp", "-f", "--file", "-m", "--max-count", "-A", "--after-context",
             "-B", "--before-context", "-C", "--context", "-d", "--directories", "-D", "--devices",
             "-w", "--width", "-x", "--line-buffered"},
    "ls": {"-I", "--ignore", "-T", "--tabsize", "--block-size"},
    "du": {"-t", "--threshold", "--exclude", "--block-size"},
    "stat": {"-c", "--format", "--printf"},
    "file": {"-e", "-F", "-m", "-f"},
    "wc": {"--files0-from"},
}


def _within_allowed_path(path: str) -> bool:
    """Check a path after resolving symlinks and user-home shorthand."""
    expanded = os.path.expanduser(path)
    resolved = os.path.realpath(expanded)
    for root in ALLOWED_PATHS:
        try:
            if os.path.commonpath((resolved, os.path.realpath(root))) == os.path.realpath(root):
                return True
        except (ValueError, OSError):
            continue
    return False


def _sensitive_path(path: str) -> bool:
    """Reject common credentials and private-key material, regardless of root."""
    expanded = os.path.expanduser(path)
    resolved = os.path.realpath(expanded)
    for candidate in (expanded, resolved):
        parts = [part.lower() for part in candidate.replace("\\", "/").split("/")]
        if any(part == ".ssh" for part in parts):
            return True
        for name in parts:
            if name == ".env" or name.startswith(".env."):
                return True
            if name in {"id_rsa", "id_ed25519", "id_ecdsa", "id_dsa", "authorized_keys", "private.key"}:
                return True
            if name.endswith((".pem", ".p12", ".pfx", ".keytab")) or name.endswith(".keystore"):
                return True
    return False


def _path_error(path: str) -> str | None:
    if path == "-":
        return None  # stdin/stdout operand; it does not name a filesystem path.
    if _sensitive_path(path):
        return f"Sensitive file path blocked: {path}"
    if not _within_allowed_path(path):
        return f"File path allowed nahi hai (sirf /var/log, /tmp, aur project folder): {path}"
    return None


def _path_operands(binary: str, args: list[str]) -> tuple[list[str], list[str]]:
    """Return filesystem operands and paths passed to grep's -f/--file."""
    if binary == "find":
        roots = []
        for arg in args:
            if arg == "--":
                continue
            if arg.startswith("-") or arg in {"!", "(", ")"}:
                break
            roots.append(arg)
        return (roots or ["."], [])

    operands: list[str] = []
    option_paths: list[str] = []
    positional_pattern_consumed = binary != "grep"
    i = 0
    end_options = False
    value_options = _VALUE_OPTIONS.get(binary, set())
    while i < len(args):
        arg = args[i]
        if end_options:
            operands.append(arg)
            i += 1
            continue
        if arg == "--":
            end_options = True
            i += 1
            continue
        if arg in value_options:
            if i + 1 >= len(args):
                break
            value = args[i + 1]
            if binary == "grep" and arg in {"-f", "--file"}:
                option_paths.append(value)
            i += 2
            continue
        # GNU options that attach their value (for example -n10 or --format=%n)
        if arg.startswith("--") and "=" in arg:
            option, value = arg.split("=", 1)
            if binary == "grep" and option in {"--file"}:
                option_paths.append(value)
            i += 1
            continue
        if arg.startswith("-") and arg != "-":
            # Short-option bundles and options with an attached value are flags,
            # except grep -ePATTERN and -fPATH which take meaningful operands.
            if binary == "grep" and len(arg) > 2 and arg[:2] in {"-e", "-f"}:
                if arg.startswith("-f"):
                    option_paths.append(arg[2:])
                i += 1
                continue
            i += 1
            continue
        if binary == "grep" and not positional_pattern_consumed:
            positional_pattern_consumed = True
        else:
            operands.append(arg)
        i += 1
    return operands, option_paths


def _check_paths(binary: str, args: list[str]) -> tuple[bool, str]:
    if binary not in PATH_COMMANDS:
        return True, ""
    operands, option_paths = _path_operands(binary, args)
    # These commands default to the current directory when no paths are given.
    if not operands and binary in {"ls", "du", "find"}:
        operands = ["."]
    for path in operands + option_paths:
        error = _path_error(path)
        if error:
            return False, error
    return True, ""


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
    args = tokens[1:]
    if binary == "find" and any(a in {"-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprintf"} for a in args):
        return False, "find ke destructive/executable options allowed nahi hain."
    ok, reason = _check_paths(binary, args)
    if not ok:
        return False, reason
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
