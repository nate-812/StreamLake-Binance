#!/usr/bin/env bash
set -euo pipefail

API_HEALTH_URL=${API_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}
AI_HEALTH_URL=${AI_HEALTH_URL:-http://127.0.0.1:8000/health}

health_status() {
    local url="$1"
    local body
    if ! body="$(curl -fsS --max-time 3 "$url" 2>/dev/null)"; then
        printf 'DOWN'
        return
    fi
    case "$body" in
        *'"status":"UP"'*|*'"status": "UP"'*) printf 'UP' ;;
        *'"status":"ok"'*|*'"status": "ok"'*) printf 'UP' ;;
        *) printf 'UNKNOWN' ;;
    esac
}

API_STATUS="$(health_status "$API_HEALTH_URL")"
AI_STATUS="$(health_status "$AI_HEALTH_URL")"

printf '{\n'
printf '  "api": "%s",\n' "$API_STATUS"
printf '  "ai": "%s"\n' "$AI_STATUS"
printf '}\n'
