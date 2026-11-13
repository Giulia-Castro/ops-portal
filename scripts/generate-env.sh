#!/usr/bin/env bash
# Creates .env with random secrets for a local or CI stack. Refuses to overwrite.
set -euo pipefail

if [[ -f .env ]]; then
  echo ".env already exists; remove it first if you want new secrets." >&2
  exit 1
fi

PY="$(command -v python3 || command -v python)"
"$PY" - <<'EOF'
import secrets

names = [
    "POSTGRES_PASSWORD", "REPORT_ENGINE_API_KEY", "N8N_ENCRYPTION_KEY",
    "TRIAGE_DB_PASSWORD", "TRIAGE_API_KEY",
    "PORTAL_DB_PASSWORD", "PORTAL_VIEWER_PASSWORD", "PORTAL_OPERATOR_PASSWORD",
]
with open(".env", "w", newline="\n") as env:
    for name in names:
        env.write(f"{name}={secrets.token_urlsafe(24)}\n")
    env.write("TRIAGE_AGENT=keyword\nTRIAGE_MANUAL_MINUTES=2\nANTHROPIC_API_KEY=\n")
EOF

echo ".env created. Portal passwords are in PORTAL_VIEWER_PASSWORD / PORTAL_OPERATOR_PASSWORD."
