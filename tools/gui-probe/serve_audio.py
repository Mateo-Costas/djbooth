"""A tiny HTTP server for one audio file, with Range support (the stdlib one has none, and the
player seeks by asking for byte ranges). Bound to localhost only; stops after IDLE seconds."""
import http.server
import re
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "audio"
IDLE = 900  # seconds with no request before it shuts itself down
last = [time.time()]


class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _serve(self, head):
        last[0] = time.time()
        name = self.path.split("?")[0].lstrip("/")
        f = ROOT / name
        if not f.is_file() or f.parent != ROOT:
            self.send_error(404)
            return
        data = f.read_bytes()
        total = len(data)
        start, end = 0, total - 1
        m = re.match(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
        status = 200
        if m:
            if m.group(1):
                start = int(m.group(1))
            if m.group(2):
                end = min(int(m.group(2)), total - 1)
            if not m.group(1) and m.group(2):
                start = max(0, total - int(m.group(2)))
                end = total - 1
            status = 206
        self.send_response(status)
        self.send_header("Content-Type", "audio/wav")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        if status == 206:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, total))
        self.end_headers()
        if not head:
            try:
                self.wfile.write(data[start:end + 1])
            except (BrokenPipeError, ConnectionResetError):
                pass

    def do_GET(self):
        self._serve(False)

    def do_HEAD(self):
        self._serve(True)


srv = http.server.ThreadingHTTPServer(("127.0.0.1", 8765), H)


def watchdog():
    while time.time() - last[0] < IDLE:
        time.sleep(5)
    srv.shutdown()


threading.Thread(target=watchdog, daemon=True).start()
print("serving", ROOT, "on 127.0.0.1:8765", flush=True)
srv.serve_forever()
