#!/usr/bin/env bash
# Seeds the stack with the sample data of projects 1 and 2:
#  - P1: triages the 48 labeled tickets of its eval dataset and records the expected labels as
#        human feedback, so the portal shows accuracy measured in "production".
#  - P2: runs the n8n report workflow for 2026-10-30..2026-11-05 (11-06 and 11-07 are left
#        for the on-demand run from the portal).
set -euo pipefail

P1_DIR="${P1_DIR:-../ticket-triage-agent}"
P2_DIR="${P2_DIR:-../ops-report-automation}"
TRIAGE_URL="${TRIAGE_URL:-http://localhost:8001}"
env_value() { grep "^$1=" .env | cut -d= -f2-; }
export TRIAGE_API_KEY="${TRIAGE_API_KEY:-$(env_value TRIAGE_API_KEY)}"
export REPORT_ENGINE_API_KEY="${REPORT_ENGINE_API_KEY:-$(env_value REPORT_ENGINE_API_KEY)}"
PY="$(command -v python3 || command -v python)"

echo "Seeding project 1 (ticket triage)..."
"$PY" - "$P1_DIR/evals/dataset.jsonl" "$TRIAGE_URL" <<'EOF'
import json
import os
import sys
import urllib.request

dataset, base = sys.argv[1], sys.argv[2]
key = os.environ["TRIAGE_API_KEY"]


def call(method, path, body):
    request = urllib.request.Request(
        base + path,
        data=json.dumps(body).encode(),
        method=method,
        headers={"X-API-Key": key, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


hits = 0
with open(dataset, encoding="utf-8") as handle:
    tickets = [json.loads(line) for line in handle if line.strip()]
for ticket in tickets:
    triage = call("POST", "/v1/triages", {"subject": ticket["subject"], "body": ticket["body"]})
    hits += triage["category"] == ticket["expected_category"]
    call("PATCH", f"/v1/triages/{triage['id']}/feedback", {
        "category": ticket["expected_category"],
        "severity": ticket["expected_severity"],
        "version": triage["version"],
    })
print(f"  {len(tickets)} tickets triaged, {hits} with the right category, feedback recorded.")
EOF

echo "Seeding project 2 (daily reports through n8n)..."
(cd "$P2_DIR" && bash scripts/backfill.sh 2026-10-30 2026-11-05)
