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
        info "Checking Flink status..."
        # Simulated check
        systemctl is-active flink || echo "Flink is not active via systemd"
        log_action "flink_status" "SUCCESS" "Checked Flink status"
        ;;
    start)
        require_confirm "$CONFIRM" "flink_start"
        check_dry_run "start_flink"
        info "Starting Flink..."
        log_action "flink_start" "SUCCESS" "Started Flink"
        ;;
    stop)
        require_confirm "$CONFIRM" "flink_stop"
        check_dry_run "stop_flink"
        info "Stopping Flink..."
        log_action "flink_stop" "SUCCESS" "Stopped Flink"
        ;;
    restart)
        require_confirm "$CONFIRM" "flink_restart"
        check_dry_run "restart_flink"
        info "Restarting Flink..."
        log_action "flink_restart" "SUCCESS" "Restarted Flink"
        ;;
    --help)
        usage
        ;;
    *)
        usage
        exit 1
        ;;
esac
