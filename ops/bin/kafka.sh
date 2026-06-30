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
        info "Checking Kafka status..."
        # Simulated check
        systemctl is-active kafka || echo "Kafka is not active"
        log_action "kafka_status" "SUCCESS" "Checked Kafka status"
        ;;
    start)
        require_confirm "$CONFIRM" "kafka_start"
        check_dry_run "start_kafka"
        info "Starting Kafka..."
        log_action "kafka_start" "SUCCESS" "Started Kafka"
        ;;
    stop)
        require_confirm "$CONFIRM" "kafka_stop"
        check_dry_run "stop_kafka"
        info "Stopping Kafka..."
        log_action "kafka_stop" "SUCCESS" "Stopped Kafka"
        ;;
    restart)
        require_confirm "$CONFIRM" "kafka_restart"
        check_dry_run "restart_kafka"
        info "Restarting Kafka..."
        log_action "kafka_restart" "SUCCESS" "Restarted Kafka"
        ;;
    --help)
        usage
        ;;
    *)
        usage
        exit 1
        ;;
esac
