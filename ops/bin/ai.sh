#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    echo "Usage: ai.sh {status|start|stop|restart} [--confirm]"
}

ACTION=${1:-}
CONFIRM=${2:-}
AI_HOST=${AI_HOST:-127.0.0.1}
AI_BIND_HOST=${AI_BIND_HOST:-0.0.0.0}
AI_PORT=${AI_PORT:-8000}
AI_HEALTH_URL=${AI_HEALTH_URL:-http://$AI_HOST:$AI_PORT/health}
AI_DIR=${AI_DIR:-$STREAMLAKE_ROOT/ai-engine}
AI_LOG=${AI_LOG:-$STREAMLAKE_LOG_DIR/ai-engine.log}
PID_FILE="$STREAMLAKE_RUN_DIR/ai-engine.pid"

get_pid() {
    pid_from_file_or_port "$PID_FILE" "$AI_PORT"
}

uvicorn_bin() {
    if [ -x "$AI_DIR/.venv/bin/uvicorn" ]; then
        printf '%s\n' "$AI_DIR/.venv/bin/uvicorn"
    elif command -v uvicorn >/dev/null 2>&1; then
        command -v uvicorn
    else
        return 1
    fi
}

start_ai() {
    load_secrets
    [ -d "$AI_DIR" ] || fail_action "ai_start" "AI directory not found: $AI_DIR"
    UVICORN_BIN="$(uvicorn_bin)" || fail_action "ai_start" "uvicorn not found. Create ai-engine/.venv and install requirements."
    local pid
    pid="$(get_pid || true)"
    if [ -n "$pid" ]; then
        fail_action "ai_start" "AI Engine is already running with PID $pid."
    fi
    check_dry_run "ai_start"
    info "Starting AI Engine from $AI_DIR"
    (
        cd "$AI_DIR"
        nohup "$UVICORN_BIN" main:app --host "$AI_BIND_HOST" --port "$AI_PORT" > "$AI_LOG" 2>&1 &
        echo "$!" > "$PID_FILE"
    )
    if ! wait_http_ok "$AI_HEALTH_URL" 30; then
        fail_action "ai_start" "AI Engine did not become healthy at $AI_HEALTH_URL. See $AI_LOG."
    fi
    log_action "ai_start" "SUCCESS" "Started AI Engine with PID $(cat "$PID_FILE")"
}

stop_ai() {
    local pid
    pid="$(get_pid || true)"
    if [ -z "$pid" ]; then
        info "AI Engine is not running."
        log_action "ai_stop" "SUCCESS" "AI Engine already stopped"
        return 0
    fi
    check_dry_run "ai_stop $pid"
    info "Stopping AI Engine (PID $pid)"
    stop_pid "$pid" "AI Engine"
    rm -f "$PID_FILE"
    log_action "ai_stop" "SUCCESS" "Stopped AI Engine ($pid)"
}

case "$ACTION" in
    status)
        PID="$(get_pid || true)"
        if [ -n "$PID" ]; then
            info "AI Engine is running with PID $PID"
            curl -fsS --max-time 3 "$AI_HEALTH_URL" || true
            echo
        else
            info "AI Engine is NOT running."
        fi
        log_action "ai_status" "SUCCESS" "Checked AI Engine status"
        ;;
    start)
        start_ai
        ;;
    stop)
        require_confirm "$CONFIRM" "ai_stop"
        stop_ai
        ;;
    restart)
        require_confirm "$CONFIRM" "ai_restart"
        stop_ai
        start_ai
        log_action "ai_restart" "SUCCESS" "Restarted AI Engine"
        ;;
    --help|-h|"")
        usage
        [ -n "$ACTION" ]
        ;;
    *)
        usage
        exit 1
        ;;
esac
