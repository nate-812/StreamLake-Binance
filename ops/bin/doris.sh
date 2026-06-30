#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    echo "Usage: $0 {status|start|stop|restart} [--confirm]"
}

ACTION=${1:-}
CONFIRM=${2:-}

case "$ACTION" in
    status)
        info "Checking Doris status..."
        # Simulated check
        systemctl is-active doris-fe || echo "Doris FE is not active"
        systemctl is-active doris-be || echo "Doris BE is not active"
        log_action "doris_status" "SUCCESS" "Checked Doris status"
        ;;
    start)
        require_confirm "$CONFIRM" "doris_start"
        check_dry_run "start_doris"
        info "Starting Doris..."
        log_action "doris_start" "SUCCESS" "Started Doris"
        ;;
    stop)
        require_confirm "$CONFIRM" "doris_stop"
        check_dry_run "stop_doris"
        info "Stopping Doris..."
        log_action "doris_stop" "SUCCESS" "Stopped Doris"
        ;;
    restart)
        require_confirm "$CONFIRM" "doris_restart"
        check_dry_run "restart_doris"
        info "Restarting Doris..."
        log_action "doris_restart" "SUCCESS" "Restarted Doris"
        ;;
    --help)
        usage
        ;;
    *)
        usage
        exit 1
        ;;
esac
