"""
Lab webhook server -- Android app (Assistant.kt) isi se baat karta hai.

Sirf apne LAN/lab machine par chalao: `python3 -m core.server`
Env vars:
  LAB_SECRET   - zaroori. Phone app ke Settings mein bhi yahi daalna hai.
  LAB_PORT     - default 8765
  LAB_BIND     - default 0.0.0.0 (apne LAN tak; internet par expose mat karna)

Har request ka header "X-Lab-Secret" LAB_SECRET se match karna chahiye, warna 401.
Jo bhi command/url aaye, wo hamesha security.py ke guardrails se guzarta hai --
ye server khud koi allow/deny decide nahi karta, runner.py/security.py karte hain.
"""
from __future__ import annotations
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from . import runner, router, memory

SECRET = os.environ.get("LAB_SECRET", "")
PORT = int(os.environ.get("LAB_PORT", "8765"))
BIND = os.environ.get("LAB_BIND", "0.0.0.0")


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, obj: dict):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):  # quiet, add_log me jaata hai jo zaroori hai
        pass

    def do_POST(self):
        if not SECRET:
            self._send(500, {"ok": False, "error": "Server par LAB_SECRET set nahi hai."})
            return
        if self.headers.get("X-Lab-Secret") != SECRET:
            self._send(401, {"ok": False, "error": "Galat ya missing secret."})
            return
        length = int(self.headers.get("Content-Length", "0"))
        try:
            body = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            self._send(400, {"ok": False, "error": "Body valid JSON nahi hai."})
            return

        action = body.get("action", "")
        try:
            if action == "run_command":
                self._send(200, runner.run_linux_command(body.get("cmd", "")))
            elif action == "check_web":
                self._send(200, runner.check_web_status(body.get("url", "")))
            elif action == "scan_local":
                self._send(200, runner.scan_local_vulnerability())
            elif action == "route":
                self._send(200, router.classify(body.get("task", "")))
            elif action == "memory_context":
                self._send(200, {"ok": True, "context": memory.context_for_ai()})
            elif action == "add_fact":
                self._send(200, {"ok": True, "facts": memory.add_fact(body.get("text", ""))})
            else:
                self._send(400, {"ok": False, "error": f"Unknown action: {action}"})
        except Exception as e:  # noqa: BLE001
            memory.add_log("error", f"{action}: {e}")
            self._send(500, {"ok": False, "error": str(e)})


def main():
    if not SECRET:
        print("WARNING: LAB_SECRET set nahi hai -- server chalega lekin har request 500 dega. export LAB_SECRET=... karke phir chalao.")
    httpd = ThreadingHTTPServer((BIND, PORT), Handler)
    print(f"Lab server {BIND}:{PORT} par chal raha hai (sirf apne LAN ke liye, internet par mat kholna).")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
