"""
Bastion & Nimrod — Adaptive Task Runner (the Executor).
"""
from __future__ import annotations
import shlex
import socket
import subprocess
import urllib.request
import urllib.error

from . import security, memory

COMMON_PORTS = [21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 587, 993, 995,
                3000, 3306, 5432, 5900, 6379, 8000, 8080, 8443, 9000, 27017]


def run_linux_command(cmd: str, timeout: int = 15) -> dict:
    """Run an allow-listed, read-only command without a shell."""
    ok, reason = security.is_safe(cmd)
    if not ok:
        memory.add_log("command", f"BLOCKED: {cmd} -> {reason}")
        return {"ok": False, "error": reason}
    try:
        tokens = shlex.split(cmd)
        timeout = max(1, min(int(timeout), 30))
        p = subprocess.run(tokens, shell=False, capture_output=True, text=True, timeout=timeout)
        out = (p.stdout or "") + (("\n" + p.stderr) if p.stderr else "")
        memory.add_log("command", f"{cmd} -> exit {p.returncode}")
        result = {"ok": p.returncode == 0, "exit_code": p.returncode, "output": out[:8000]}
        if p.returncode != 0:
            result["error"] = f"Command exit code {p.returncode}."
        return result
    except subprocess.TimeoutExpired:
        memory.add_log("command", f"{cmd} -> TIMEOUT")
        return {"ok": False, "error": f"Command {timeout}s mein poori nahi hui (timeout)."}
    except FileNotFoundError:
        return {"ok": False, "error": "Command/binary nahi mila."}
    except (OSError, ValueError, TypeError) as e:
        return {"ok": False, "error": str(e)}


class _SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Revalidate every redirect to avoid redirect-based SSRF bypasses."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        ok, reason = security.is_safe_url(newurl)
        if not ok:
            raise urllib.error.HTTPError(newurl, code, reason, headers, fp)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def check_web_status(url: str, timeout: int = 8) -> dict:
    """Check one public website, validating its host and every redirect target."""
    ok, reason = security.is_safe_url(url)
    if not ok:
        return {"ok": False, "error": reason}
    try:
        req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "ai-assistant-lab/1.0"})
        opener = urllib.request.build_opener(_SafeRedirectHandler())
        with opener.open(req, timeout=max(1, min(int(timeout), 20))) as r:
            headers = {k: v for k, v in list(r.headers.items())[:10]}
            result = {"ok": True, "status": r.status, "headers": headers}
    except urllib.error.HTTPError as e:
        result = {"ok": e.code < 400, "status": e.code, "headers": {}}
    except Exception as e:  # noqa: BLE001
        result = {"ok": False, "error": str(e)}
    memory.add_log("web_check", f"{url[:500]} -> {result.get('status', result.get('error'))}")
    return result


def scan_local_vulnerability() -> dict:
    """Inspect common TCP ports on this machine only; no host parameter exists."""
    open_ports = []
    for port in COMMON_PORTS:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(0.4)
        try:
            if s.connect_ex(("127.0.0.1", port)) == 0:
                open_ports.append(port)
        except OSError:
            pass
        finally:
            s.close()
    note = "Ye sirf ye batata hai kaunsa port sun raha hai."
    memory.add_log("scan", f"Open ports on 127.0.0.1: {open_ports}")
    return {"ok": True, "host": "127.0.0.1", "open_ports": open_ports, "note": note}
