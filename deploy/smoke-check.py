#!/usr/bin/env python3
"""Read-only HTTP checks for a running deployment; creates no accounts or trades."""
import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url", nargs="?", default="http://localhost:8082")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme not in ("http", "https") or not parsed.netloc or parsed.path:
        parser.error("Use an HTTP(S) origin, for example https://trading.example.com")
    # Do not send a localhost smoke check through a configured HTTP proxy.
    local = parsed.hostname in ("localhost", "127.0.0.1", "::1")
    opener = urllib.request.build_opener(
        *([urllib.request.ProxyHandler({})] if local else [])
    )

    def fetch(path, expected=200):
        request = urllib.request.Request(base + path, headers={"User-Agent": "LEAP-deployment-check"})
        try:
            with opener.open(request, timeout=15) as response:
                status, content, headers = response.status, response.read(), response.headers
        except urllib.error.HTTPError as error:
            status, content, headers = error.code, error.read(), error.headers
        if status != expected:
            raise RuntimeError(f"{path}: expected HTTP {expected}, received {status}")
        return content, headers

    for path in ("/", "/dashboard", "/analyst", "/operations"):
        body, headers = fetch(path)
        if b"<app-root" not in body or "text/html" not in headers.get("Content-Type", ""):
            raise RuntimeError(f"{path}: Angular entry point missing")
        print(f"PASS {path}: frontend and SPA fallback")
    body, _ = fetch("/api/health")
    if body.decode("utf-8") != "LEAP Backend is running!":
        raise RuntimeError("/api/health was not handled by the trading API")
    print("PASS /api/health: API proxy")
    body, _ = fetch("/api/auth/account-currencies")
    if [item["code"] for item in json.loads(body)] != ["USD", "GBP", "INR"]:
        raise RuntimeError("Unexpected signup currencies")
    print("PASS signup currencies: USD, GBP, INR")
    fetch("/api/accounts/me", 401)
    fetch("/api/market/instruments", 401)
    fetch("/actuator/health", 404)
    fetch("/missing-chunk.js", 404)
    print("PASS API authentication, private management routes and missing-asset handling")
    print("Deployment smoke checks passed.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, urllib.error.URLError, TimeoutError, ValueError, KeyError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
