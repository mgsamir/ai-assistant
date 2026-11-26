"""
Lab webhook server — Android app talks to this server on the user's own machine.
Set LAB_SECRET to a long random value. Set LAB_BIND=0.0.0.0 only when LAN access
is needed and the host firewall restricts access to trusted devices.
"""
from __future__ import annotations
import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from . import runner, router, memory

SECRET = os.environ.get("LAB_SECRET", "")
PORT = int(os.environ.get("LAB_PORT", "8765"))
# Local-only default. Explicitly opt in to LAN exposure when required.
BIND = os.environ.get("LAB_BIND", "127.0.0.1")
MAX_BODY = 64 * 1024


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, obj: dict):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        if len(SECRET) < 32:
            self._send(503, {"ok": False, "error": "Server par kam-se-kam 32 characters ka random LAB_SECRET set karo."})
            return
        supplied = self.headers.get("X-Lab-Secret", "")
        if not hmac.compare_digest(supplied.encode("utf-8"), SECRET.encode("utf-8")):
            self._send(401, {"ok": False, "error": "Galat ya missing secret."})
            return
        try:
            length = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            self._send(400, {"ok": False, "error": "Invalid Content-Length."})
            return
        if length < 0 or length > MAX_BODY:
            self._send(413, {"ok": False, "error": "Request body too large or missing."})
            return
        try:
            body = json.loads(self.rfile.read(length) or b"{}")
            if not isinstance(body, dict):
                raise ValueError("JSON object required")
        except (json.JSONDecodeError, UnicodeDecodeError, ValueError):
            self._send(400, {"ok": False, "error": "Body valid JSON object nahi hai."})
            return

        action = body.get("action", "")
        try:
            if action == "run_command":
                result = runner.run_linux_command(body.get("cmd", ""))
            elif action == "check_web":
                result = runner.check_web_status(body.get("url", ""))
            elif action == "scan_local":
                result = runner.scan_local_vulnerability()
            elif action == "route":
                result = router.classify(body.get("task", ""))
            elif action == "memory_context":
                result = {"ok": True, "context": memory.context_for_ai()}
            elif action == "add_fact":
                result = {"ok": True, "facts": memory.add_fact(body.get("text", ""))}
            else:
                self._send(400, {"ok": False, "error": "Unknown action."})
                return
            self._send(200, result)
        except Exception as exc:  # noqa: BLE001
            memory.add_log("error", f"{action}: {exc}")
            self._send(500, {"ok": False, "error": "Internal server error."})


def main():
    if len(SECRET) < 32:
        raise SystemExit("Set LAB_SECRET to a random value of at least 32 characters before starting.")
    if not 1 <= PORT <= 65535:
        raise SystemExit("LAB_PORT must be between 1 and 65535.")
    httpd = ThreadingHTTPServer((BIND, PORT), Handler)
    print(f"Lab server {BIND}:{PORT} par chal raha hai. LAN exposure ke liye LAB_BIND=0.0.0.0 explicitly set karo.")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
