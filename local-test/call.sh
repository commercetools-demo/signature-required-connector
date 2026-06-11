#!/usr/bin/env bash
# Send a sample API Extension payload to a locally running service.
# Usage: ./local-test/call.sh <payload.json> <extension-secret> [url]
set -euo pipefail

PAYLOAD="${1:?path to a JSON payload required}"
SECRET="${2:?extension secret required (matches EXTENSION_AUTH_SECRET)}"
URL="${3:-http://localhost:8080/service}"

curl -sS -X POST "$URL" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${SECRET}" \
  -H "X-Correlation-ID: local-$(date +%s)" \
  --data-binary "@${PAYLOAD}" | { command -v jq >/dev/null && jq . || cat; }
echo
