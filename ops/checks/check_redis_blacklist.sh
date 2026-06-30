#!/usr/bin/env bash
set -euo pipefail

REDIS_HOST=${REDIS_HOST:-192.168.1.10}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_USERNAME=${REDIS_USERNAME:-}
REDIS_PATTERN=${REDIS_PATTERN:-risk:blacklist:*}

if ! command -v redis-cli >/dev/null 2>&1; then
    echo "ERROR: redis-cli not found" >&2
    exit 2
fi

ARGS=(-h "$REDIS_HOST" -p "$REDIS_PORT")
if [ -n "$REDIS_USERNAME" ]; then
    ARGS+=(--user "$REDIS_USERNAME")
fi

echo "=== Redis Blacklist Keys ==="
if [ -n "${REDIS_PASSWORD:-}" ]; then
    REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli "${ARGS[@]}" --scan --pattern "$REDIS_PATTERN"
else
    redis-cli "${ARGS[@]}" --scan --pattern "$REDIS_PATTERN"
fi
