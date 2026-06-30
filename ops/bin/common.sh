#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [ -d /opt/StreamLake-Binance ]; then
    DEFAULT_STREAMLAKE_ROOT=/opt/StreamLake-Binance
else
    DEFAULT_STREAMLAKE_ROOT="$REPO_ROOT"
fi

STREAMLAKE_ROOT=${STREAMLAKE_ROOT:-$DEFAULT_STREAMLAKE_ROOT}
STREAMLAKE_RUN_DIR=${STREAMLAKE_RUN_DIR:-/tmp/streamlake-ops}
STREAMLAKE_LOG_DIR=${STREAMLAKE_LOG_DIR:-/var/log/streamlake-ops}
FLINK_HOME=${FLINK_HOME:-/opt/flink}
KAFKA_HOME=${KAFKA_HOME:-/opt/kafka}
FLINK_BIN=${FLINK_BIN:-$FLINK_HOME/bin/flink}
KAFKA_CONFIG=${KAFKA_CONFIG:-$KAFKA_HOME/config/kraft/server.properties}
SECRET_FILE=${STREAMLAKE_SECRET_FILE:-/root/.streamlake-secrets}

if mkdir -p "$STREAMLAKE_LOG_DIR" 2>/dev/null && touch "$STREAMLAKE_LOG_DIR/actions.log" 2>/dev/null; then
    LOG_FILE="$STREAMLAKE_LOG_DIR/actions.log"
else
    STREAMLAKE_LOG_DIR=/tmp/streamlake-ops
    mkdir -p "$STREAMLAKE_LOG_DIR"
    LOG_FILE="$STREAMLAKE_LOG_DIR/actions.log"
    touch "$LOG_FILE"
fi
mkdir -p "$STREAMLAKE_RUN_DIR"

log_action() {
    local action="$1"
    local status="$2"
    local msg="$3"
    printf '%s | %s | %s | %s | %s\n' \
        "$(date '+%Y-%m-%d %H:%M:%S')" "$(whoami)" "$action" "$status" "$msg" >> "$LOG_FILE"
}

info() {
    printf '\033[1;34m[INFO]\033[0m %s\n' "$*"
}

warn() {
    printf '\033[1;33m[WARN]\033[0m %s\n' "$*"
}

error() {
    printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2
}

fail_action() {
    local action="$1"
    local msg="$2"
    error "$msg"
    log_action "$action" "FAILED" "$msg"
    exit 1
}

load_secrets() {
    if [ -f "$SECRET_FILE" ]; then
        info "Loading secrets from $SECRET_FILE"
        set -a
        # shellcheck disable=SC1090
        . "$SECRET_FILE"
        set +a
    elif [ -f "$STREAMLAKE_ROOT/.env" ]; then
        info "Loading secrets from $STREAMLAKE_ROOT/.env"
        set -a
        # shellcheck disable=SC1090
        . "$STREAMLAKE_ROOT/.env"
        set +a
    else
        warn "No secret file found; assuming required environment variables are already exported."
    fi
}

check_dry_run() {
    if [ "${DRY_RUN:-0}" = "1" ]; then
        info "DRY_RUN=1, would execute: $*"
        log_action "$1" "DRY_RUN" "$*"
        exit 0
    fi
}

require_confirm() {
    local confirm_flag="$1"
    local action_name="$2"
    if [ "$confirm_flag" != "--confirm" ]; then
        error "Action '$action_name' changes runtime state."
        error "Append '--confirm' to execute it."
        log_action "$action_name" "REJECTED" "Missing --confirm"
        exit 1
    fi
}

require_file() {
    local path="$1"
    local label="$2"
    if [ ! -f "$path" ]; then
        error "$label not found: $path"
        return 1
    fi
}

require_executable() {
    local path="$1"
    local label="$2"
    if [ ! -x "$path" ]; then
        error "$label not executable: $path"
        return 1
    fi
}

require_command() {
    local command_name="$1"
    if ! command -v "$command_name" >/dev/null 2>&1; then
        error "Required command not found: $command_name"
        return 1
    fi
}

require_env() {
    local missing=()
    local name
    for name in "$@"; do
        if [ -z "${!name:-}" ]; then
            missing+=("$name")
        fi
    done
    if [ "${#missing[@]}" -gt 0 ]; then
        error "Missing required environment variables: ${missing[*]}"
        return 1
    fi
}

pid_from_file_or_port() {
    local pid_file="$1"
    local port="$2"
    if [ -f "$pid_file" ]; then
        local pid
        pid="$(cat "$pid_file" 2>/dev/null || true)"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            printf '%s\n' "$pid"
            return 0
        fi
    fi
    if command -v lsof >/dev/null 2>&1; then
        lsof -t -i:"$port" 2>/dev/null | head -n 1
    fi
}

stop_pid() {
    local pid="$1"
    local label="$2"
    kill "$pid" 2>/dev/null || true
    for _ in 1 2 3 4 5; do
        if ! kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
        sleep 1
    done
    warn "$label did not stop gracefully; sending SIGKILL"
    kill -9 "$pid" 2>/dev/null || true
}

wait_http_ok() {
    local url="$1"
    local seconds="${2:-20}"
    local deadline=$((SECONDS + seconds))
    while [ "$SECONDS" -lt "$deadline" ]; do
        if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    return 1
}
