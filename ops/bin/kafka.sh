#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    echo "Usage: kafka.sh {status|start|stop|restart} [--confirm]"
}

ACTION=${1:-}
CONFIRM=${2:-}
KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-192.168.1.10:9092}
KAFKA_START=${KAFKA_START:-$KAFKA_HOME/bin/kafka-server-start.sh}
KAFKA_STOP=${KAFKA_STOP:-$KAFKA_HOME/bin/kafka-server-stop.sh}
KAFKA_BROKER_API=${KAFKA_BROKER_API:-$KAFKA_HOME/bin/kafka-broker-api-versions.sh}

case "$ACTION" in
    status)
        require_executable "$KAFKA_BROKER_API" "Kafka broker API tool" || fail_action "kafka_status" "Kafka broker API tool unavailable."
        "$KAFKA_BROKER_API" --bootstrap-server "$KAFKA_BOOTSTRAP" >/dev/null \
            || fail_action "kafka_status" "Kafka broker is not reachable at $KAFKA_BOOTSTRAP."
        info "Kafka broker is reachable at $KAFKA_BOOTSTRAP"
        log_action "kafka_status" "SUCCESS" "Kafka reachable at $KAFKA_BOOTSTRAP"
        ;;
    start)
        require_confirm "$CONFIRM" "kafka_start"
        require_executable "$KAFKA_START" "Kafka start script" || fail_action "kafka_start" "Kafka start script unavailable."
        require_file "$KAFKA_CONFIG" "Kafka config" || fail_action "kafka_start" "Kafka config unavailable."
        check_dry_run "kafka_start"
        "$KAFKA_START" -daemon "$KAFKA_CONFIG" || fail_action "kafka_start" "Kafka start failed."
        log_action "kafka_start" "SUCCESS" "Started Kafka with $KAFKA_CONFIG"
        ;;
    stop)
        require_confirm "$CONFIRM" "kafka_stop"
        require_executable "$KAFKA_STOP" "Kafka stop script" || fail_action "kafka_stop" "Kafka stop script unavailable."
        check_dry_run "kafka_stop"
        "$KAFKA_STOP" || fail_action "kafka_stop" "Kafka stop failed."
        log_action "kafka_stop" "SUCCESS" "Stopped Kafka"
        ;;
    restart)
        require_confirm "$CONFIRM" "kafka_restart"
        require_executable "$KAFKA_START" "Kafka start script" || fail_action "kafka_restart" "Kafka start script unavailable."
        require_executable "$KAFKA_STOP" "Kafka stop script" || fail_action "kafka_restart" "Kafka stop script unavailable."
        require_file "$KAFKA_CONFIG" "Kafka config" || fail_action "kafka_restart" "Kafka config unavailable."
        check_dry_run "kafka_restart"
        "$KAFKA_STOP" || fail_action "kafka_restart" "Kafka stop failed."
        sleep 3
        "$KAFKA_START" -daemon "$KAFKA_CONFIG" || fail_action "kafka_restart" "Kafka start failed."
        log_action "kafka_restart" "SUCCESS" "Restarted Kafka"
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
