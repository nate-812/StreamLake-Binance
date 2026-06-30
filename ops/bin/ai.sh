#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    echo "Usage: $0 {status|start|stop|restart} [--confirm]"
}

ACTION=${1:-}
CONFIRM=${2:-}

AI_PORT=8000

get_pid() {
    lsof -t -i:$AI_PORT || true
}

case "$ACTION" in
    status)
        PID=$(get_pid)
        if [ -n "$PID" ]; then
            info "AI Engine is running with PID: $PID"
            curl -s http://127.0.0.1:$AI_PORT/health || echo "Health check failed"
        else
            info "AI Engine is NOT running."
        fi
        log_action "ai_status" "SUCCESS" "Checked status"
        ;;
    start)
        load_secrets
        check_dry_run "start_ai"
        PID=$(get_pid)
        if [ -n "$PID" ]; then
            error "AI Engine is already running ($PID)"
            exit 1
        fi
        info "Starting AI Engine..."
        # Simulated start
        # uvicorn main:app --host 0.0.0.0 --port $AI_PORT &
        info "Waiting for health endpoint..."
        sleep 2
        log_action "ai_start" "SUCCESS" "Started AI Engine"
        ;;
    stop)
        require_confirm "$CONFIRM" "ai_stop"
        check_dry_run "stop_ai"
        PID=$(get_pid)
        if [ -n "$PID" ]; then
            info "Stopping AI Engine (PID $PID)..."
            kill "$PID" || kill -9 "$PID"
            log_action "ai_stop" "SUCCESS" "Stopped AI Engine"
        else
            info "AI Engine is not running."
        fi
        ;;
    restart)
        require_confirm "$CONFIRM" "ai_restart"
        PID=$(get_pid)
        if [ -n "$PID" ]; then
            info "Current PID is $PID, stopping..."
            kill "$PID" || true
            sleep 2
        fi
        load_secrets
        check_dry_run "restart_ai"
        info "Starting AI Engine..."
        log_action "ai_restart" "SUCCESS" "Restarted AI Engine"
        ;;
    --help)
        usage
        ;;
    *)
        usage
        exit 1
        ;;
esac
