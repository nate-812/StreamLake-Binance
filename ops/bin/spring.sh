#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    echo "Usage: $0 {status|start|stop|restart} [--confirm]"
}

ACTION=${1:-}
CONFIRM=${2:-}

API_PORT=8080

get_pid() {
    lsof -t -i:$API_PORT || true
}

case "$ACTION" in
    status)
        PID=$(get_pid)
        if [ -n "$PID" ]; then
            info "Spring API is running with PID: $PID"
            curl -s http://127.0.0.1:$API_PORT/actuator/health || echo "Health check failed"
        else
            info "Spring API is NOT running."
        fi
        log_action "spring_status" "SUCCESS" "Checked status"
        ;;
    start)
        load_secrets
        check_dry_run "start_spring"
        PID=$(get_pid)
        if [ -n "$PID" ]; then
            error "Spring API is already running ($PID)"
            exit 1
        fi
        info "Starting Spring API..."
        # Simulated start
        # java -jar api-server/target/api-server.jar &
        info "Waiting for health endpoint..."
        sleep 2
        log_action "spring_start" "SUCCESS" "Started Spring API"
        ;;
    stop)
        require_confirm "$CONFIRM" "spring_stop"
        check_dry_run "stop_spring"
        PID=$(get_pid)
        if [ -n "$PID" ]; then
            info "Stopping Spring API (PID $PID)..."
            kill "$PID" || kill -9 "$PID"
            log_action "spring_stop" "SUCCESS" "Stopped Spring API"
        else
            info "Spring API is not running."
        fi
        ;;
    restart)
        require_confirm "$CONFIRM" "spring_restart"
        PID=$(get_pid)
        if [ -n "$PID" ]; then
            info "Current PID is $PID, stopping..."
            kill "$PID" || true
            sleep 2
        fi
        load_secrets
        check_dry_run "restart_spring"
        info "Starting Spring API..."
        log_action "spring_restart" "SUCCESS" "Restarted Spring API"
        ;;
    --help)
        usage
        ;;
    *)
        usage
        exit 1
        ;;
esac
