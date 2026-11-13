#!/usr/bin/env bash
# End-to-end scenario on the full stack (used by CI):
#  1. waits for the three projects, 2. seeds them, 3. runs a report from the portal as operator,
#  4. checks the dashboard and the business rules, 5. exports the data for the static demo.
set -euo pipefail
cd "$(dirname "$0")/.."
PY="$(command -v python3 || command -v python)"

wait_for() {
  local name="$1" url="$2" expected="$3" code=""
  echo "Waiting for $name..."
  for _ in $(seq 1 90); do
    code=$(curl -s -o /dev/null -w "%{http_code}" -X "${4:-GET}" "$url" || true)
    [[ "$code" == "$expected" ]] && return 0
    sleep 5
  done
  echo "$name not ready (last status: $code)" >&2
  exit 1
}

wait_for "triage API" http://localhost:8001/health/ready 200
wait_for "report engine" http://localhost:8000/health/ready 200
wait_for "n8n webhook" http://localhost:5678/webhook/daily-report 403 POST
wait_for "portal" http://localhost:8080/api/auth/me 401

bash scripts/seed.sh

source scripts/portal-api.sh
portal_login operator

echo "Running the 2026-11-06 report from the portal..."
run=$(portal_post /api/v1/report-runs '{"reportDate":"2026-11-06"}')
echo "  $run" | head -c 300; echo
[[ "$(echo "$run" | tail -1)" == "201" ]]
echo "$run" | head -1 | grep -q '"outcome":"DELIVERED"'

again=$(portal_post /api/v1/report-runs '{"reportDate":"2026-11-06"}')
[[ "$(echo "$again" | tail -1)" == "409" ]]
echo "$again" | head -1 | grep -q 'REPORT_ALREADY_DELIVERED'

DASHBOARD_JSON=$(portal_get /api/v1/dashboard)
RUNS_JSON=$(portal_get /api/v1/report-runs)
export DASHBOARD_JSON RUNS_JSON
"$PY" - <<'EOF'
import json
import os

dashboard = json.loads(os.environ["DASHBOARD_JSON"])
runs = json.loads(os.environ["RUNS_JSON"])
triage, reports = dashboard["triage"], dashboard["reports"]
print("triage:", triage["data"]["total"], "tickets, accuracy", triage["data"]["categoryAccuracy"])
print("reports:", reports["data"]["delivered"], "delivered;", "health:", dashboard["health"])
print("impact:", dashboard["impact"])

assert triage["available"] and reports["available"], dashboard
assert triage["data"]["total"] == 48
assert triage["data"]["feedbackCount"] == 48
assert reports["data"]["delivered"] == 8
assert dashboard["health"]["red"] == 1 and dashboard["health"]["yellow"] == 1
assert dashboard["impact"]["complete"] is True
assert runs[0]["outcome"] == "DELIVERED" and runs[0]["requestedBy"] == "operator"
print("E2E OK")
EOF

bash scripts/export-demo.sh
