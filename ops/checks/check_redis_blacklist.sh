#!/usr/bin/env bash
set -euo pipefail

REDIS_HOST=${REDIS_HOST:-"192.168.1.10"}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_PASSWORD=${REDIS_PASSWORD:-""}

AUTH_ARG=""
if [ -n "$REDIS_PASSWORD" ]; then
    AUTH_ARG="-a $REDIS_PASSWORD"
fi

echo "=== Redis Blacklist Keys ==="
# 使用 SCAN 避免 KEYS 导致的阻塞
redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" $AUTH_ARG --scan --pattern "risk:blacklist:*"
