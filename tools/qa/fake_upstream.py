#!/usr/bin/env python3
"""Fake aircraft upstream for local QA.

Serves the `?all&jv2` envelope, `{"ac": [...], "total": n, "now": <epoch ms>, "msg": "No error"}`,
from tools/qa/fixture_aircraft.json.

Usage:
    python3 tools/qa/fake_upstream.py [--port 18080] [--fixture tools/qa/fixture_aircraft.json]

Observation ages are re-emitted as given on every request, so the aircraft always look
freshly seen no matter how long the fake has been running.
"""

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DEFAULT_PORT = 18080
DEFAULT_FIXTURE = "tools/qa/fixture_aircraft.json"


class UpstreamHandler(BaseHTTPRequestHandler):
    aircraft = []

    def do_GET(self):
        if "all" not in self.path:
            self.send_error(404, "only ?all&jv2 is served")
            return

        payload = {
            "ac": self.aircraft,
            "total": len(self.aircraft),
            "now": int(time.time() * 1000),
            "msg": "No error",
        }
        body = json.dumps(payload).encode("utf-8")

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("%s %s" % (self.address_string(), fmt % args))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--fixture", default=DEFAULT_FIXTURE)
    parser.add_argument("--host", default="127.0.0.1")
    args = parser.parse_args()

    with open(args.fixture) as fixture:
        UpstreamHandler.aircraft = json.load(fixture)

    print("Serving %d aircraft on http://%s:%d/?all&jv2" % (
        len(UpstreamHandler.aircraft), args.host, args.port,
    ))
    ThreadingHTTPServer((args.host, args.port), UpstreamHandler).serve_forever()


if __name__ == "__main__":
    main()
