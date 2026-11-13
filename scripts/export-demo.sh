#!/usr/bin/env bash
# Exports what the portal API returns into frontend/public/demo/, the data of the static demo.
# Only aggregated metrics and run audit rows (user names "operator"/"viewer"); no personal data.
set -euo pipefail
cd "$(dirname "$0")/.."

source scripts/portal-api.sh
portal_login viewer

mkdir -p frontend/public/demo
portal_get /api/v1/dashboard > frontend/public/demo/dashboard.json
portal_get /api/v1/report-runs > frontend/public/demo/report-runs.json
echo "Demo data exported to frontend/public/demo/."
