#!/usr/bin/env python3
"""Small HTTP worker for the CostGreedy scheduling demo."""
import hashlib
import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

WORKER_ID = os.environ.get("WORKER_ID", "worker-1")
PORT = int(os.environ.get("PORT", "8001"))
BIND = os.environ.get("BIND", "127.0.0.1")


class Handler(BaseHTTPRequestHandler):
    def _send_json(self, status, payload):
        raw = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path == "/health":
            self._send_json(200, {"worker": WORKER_ID, "status": "healthy"})
        else:
            self._send_json(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/run":
            self._send_json(404, {"error": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length) or b"{}")
            task_id = str(payload.get("task_id", "task-unknown"))
            iterations = max(1, min(int(payload.get("iterations", 100_000)), 5_000_000))
            started = time.perf_counter()
            digest = hashlib.sha256()
            for i in range(iterations):
                digest.update(f"{task_id}:{i}".encode("utf-8"))
            elapsed = time.perf_counter() - started
            self._send_json(200, {
                "task_id": task_id,
                "worker": WORKER_ID,
                "status": "completed",
                "iterations": iterations,
                "execution_seconds": round(elapsed, 6),
                "result_sha256": digest.hexdigest()
            })
        except Exception as exc:
            self._send_json(400, {"error": str(exc)})

    def log_message(self, fmt, *args):
        print("[%s] %s - %s" % (WORKER_ID, self.address_string(), fmt % args))


if __name__ == "__main__":
    server = ThreadingHTTPServer((BIND, PORT), Handler)
    print(f"{WORKER_ID} listening on {BIND}:{PORT}")
    server.serve_forever()
