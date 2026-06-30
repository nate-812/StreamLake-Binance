#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    echo "Usage: flink.sh {status|start|stop|restart} [--confirm]"
}

ACTION=${1:-}
CONFIRM=${2:-}
FLINK_START=${FLINK_START:-$FLINK_HOME/bin/start-cluster.sh}
FLINK_STOP=${FLINK_STOP:-$FLINK_HOME/bin/stop-cluster.sh}

case "$ACTION" in
    status)
        require_executable "$FLINK_BIN" "Flink CLI" || fail_action "flink_status" "Flink CLI unavailable."
        "$FLINK_BIN" list || fail_action "flink_status" "Unable to query Flink cluster."
        log_action "flink_status" "SUCCESS" "Queried Flink cluster"
        ;;
    start)
        require_confirm "$CONFIRM" "flink_start"
        require_executable "$FLINK_START" "Flink start script" || fail_action "flink_start" "Flink start script unavailable."
        check_dry_run "flink_start"
        "$FLINK_START" || fail_action "flink_start" "Flink start script failed."
        log_action "flink_start" "SUCCESS" "Started Flink cluster"
        ;;
    stop)
        require_confirm "$CONFIRM" "flink_stop"
        require_executable "$FLINK_STOP" "Flink stop script" || fail_action "flink_stop" "Flink stop script unavailable."
        check_dry_run "flink_stop"
        "$FLINK_STOP" || fail_action "flink_stop" "Flink stop script failed."
        log_action "flink_stop" "SUCCESS" "Stopped Flink cluster"
        ;;
    restart)
        require_confirm "$CONFIRM" "flink_restart"
        require_executable "$FLINK_STOP" "Flink stop script" || fail_action "flink_restart" "Flink stop script unavailable."
        require_executable "$FLINK_START" "Flink start script" || fail_action "flink_restart" "Flink start script unavailable."
        check_dry_run "flink_restart"
        "$FLINK_STOP" || fail_action "flink_restart" "Flink stop script failed."
        "$FLINK_START" || fail_action "flink_restart" "Flink start script failed."
        log_action "flink_restart" "SUCCESS" "Restarted Flink cluster"
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
