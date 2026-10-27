#!/usr/bin/env python3
"""Create deployment credentials without overwriting an existing environment."""
import base64
import os
from pathlib import Path
import secrets
import sys


def main():
    root = Path(__file__).resolve().parents[1]
    target = root / ".env.deploy"
    replacements = {
        "DEPLOY_DB_PASSWORD": secrets.token_hex(32),
        "DEPLOY_POSTGRES_PASSWORD": secrets.token_hex(32),
        "DEPLOY_JWT_SECRET": base64.b64encode(secrets.token_bytes(48)).decode("ascii"),
        "DEPLOY_INTERNAL_ACCESS_CODE": secrets.token_hex(24),
    }
    template = (root / ".env.deploy.example").read_text(encoding="utf-8")
    lines = []
    for line in template.splitlines():
        name = line.partition("=")[0]
        lines.append(name + "=" + replacements[name] if name in replacements else line)
    try:
        descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        print(".env.deploy already exists; keeping its credentials.", file=sys.stderr)
        return 1
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
        output.write("\n".join(lines) + "\n")
    print("Created .env.deploy. Keep it private and reuse it across restarts.")
    print("The analyst/operations signup code is DEPLOY_INTERNAL_ACCESS_CODE in that file.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
