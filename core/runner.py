"""
Bastion & Nimrod — Adaptive Task Runner (the Executor).

Har function pehle security.is_safe*() se guzarta hai. Koi bhi function yahan
kisi third-party host ko scan/exploit nahi karta — scan_local_vulnerability()
hamesha sirf 127.0.0.1 dekhta hai, koi host parameter leta hi nahi.
"""
from __future__ import annotations
import shlex
import socket
import subprocess
import urllib.request
import urllib.error

from . import security, memory

# Localhost par aksar khule rehne wale common/well-known ports (sirf inspection ke liye)
COMMON_PORTS = [21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 587, 993, 995,
                3000, 3306, 5432, 5900, 6379, 8000, 8080, 8443, 9000, 27017]


def run_linux_command(cmd: str, timeout: int = 15) -> dict:
    """Allowlist/guardrails pass karne par hi command chalati hai (shell=False)."""
    ok, reason = security.is_safe(cmd)
    if not ok:
        memory.add_log("command", f"BLOCKED: {cmd} -> {reason}")
        return {"ok": False, "error": reason}
    try:
        tokens = shlex.split(cmd)
        p = subprocess.run(tokens, shell=False, capture_output=True, text=True, timeout=timeout)
        out = (p.stdout or "") + (("\n" + p.stderr) if p.stderr else "")
        memory.add_log("command", f"{cmd} -> exit {p.returncode}")
        return {"ok": True, "exit_code": p.returncode, "output": out[:8000]}
    except subprocess.TimeoutExpired:
        memory.add_log("command", f"{cmd} -> TIMEOUT")
        return {"ok": False, "error": f"Command {timeout}s mein poori nahi hui (timeout)."}
    except FileNotFoundError:
        return {"ok": False, "error": "Command/binary nahi mila."}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "error": str(e)}


def check_web_status(url: str, timeout: int = 8) -> dict:
    """Kisi (public) website ka status code + chand headers dekho — scan nahi, sirf ek normal GET/HEAD."""
    ok, reason = security.is_safe_url(url)
    if not ok:
        return {"ok": False, "error": reason}
    try:
        req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "ai-assistant-lab/1.0"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            headers = {k: v for k, v in list(r.headers.items())[:10]}
            result = {"ok": True, "status": r.status, "headers": headers}
    except urllib.error.HTTPError as e:
        result = {"ok": True, "status": e.code, "headers": {}}
    except Exception as e:  # noqa: BLE001
        result = {"ok": False, "error": str(e)}
    memory.add_log("web_check", f"{url} -> {result.get('status', result.get('error'))}")
    return result


def scan_local_vulnerability() -> dict:
    """
    SIRF apne hi system (127.0.0.1) ke common ports check karta hai -- koi host
    parameter accept nahi karta, taaki ye kabhi kisi aur system ko scan na kar sake.
    Exploitation nahi karta, sirf "ye port khula hai" jaisi basic jaankari deta hai.
    """
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
    note = "Ye sirf ye batata hai kaunsa port sun raha hai -- agar koi service bina zaroorat ke bahar se reachable ho (jaise 0.0.0.0 par bind), use firewall/bind-address se localhost tak seemit karo."
    memory.add_log("scan", f"Open ports on 127.0.0.1: {open_ports}")
    return {"ok": True, "host": "127.0.0.1", "open_ports": open_ports, "note": note}
