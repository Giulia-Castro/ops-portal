#!/usr/bin/env bash
# Helpers to call the portal like the browser does: session cookie + CSRF cookie/header.
# Source it, then: portal_login operator; portal_get /api/v1/dashboard; portal_post path json
set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8080}"
COOKIES="$(mktemp)"
trap 'rm -f "$COOKIES"' EXIT

_csrf_header() {
  curl -s -c "$COOKIES" -b "$COOKIES" "$PORTAL_URL/api/auth/csrf" \
    | sed -E 's/.*"headerName":"([^"]+)".*/\1/'
}

_csrf_token() {
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$COOKIES" | tail -1
}

portal_login() {
  local user="$1" password_var
  password_var="PORTAL_$(echo "$user" | tr '[:lower:]' '[:upper:]')_PASSWORD"
  local password="${!password_var:-$(grep "^$password_var=" .env | cut -d= -f2-)}"
  local header
  header="$(_csrf_header)"
  curl -s -f -c "$COOKIES" -b "$COOKIES" -H "$header: $(_csrf_token)" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$user\",\"password\":\"$password\"}" \
    "$PORTAL_URL/api/auth/login" > /dev/null
}

portal_get() {
  curl -s -f -b "$COOKIES" -H "Accept-Language: pt-BR" "$PORTAL_URL$1"
}

# Prints the body and, on the last line, the HTTP status.
portal_post() {
  local header
  header="$(_csrf_header)"
  curl -s -b "$COOKIES" -c "$COOKIES" -H "$header: $(_csrf_token)" \
    -H "Content-Type: application/json" -H "Accept-Language: pt-BR" \
    -w "\n%{http_code}" -d "$2" "$PORTAL_URL$1"
}
