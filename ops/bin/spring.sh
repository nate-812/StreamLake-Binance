#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    echo "Usage: spring.sh {status|start|stop|restart} [--confirm]"
}

ACTION=${1:-}
CONFIRM=${2:-}
API_HOST=${API_HOST:-127.0.0.1}
API_PORT=${API_PORT:-8080}
API_HEALTH_URL=${API_HEALTH_URL:-http://$API_HOST:$API_PORT/actuator/health}
API_JAR=${API_JAR:-$STREAMLAKE_ROOT/api-server/target/api-server-1.0.0-SNAPSHOT.jar}
API_LOG=${API_LOG:-$STREAMLAKE_LOG_DIR/api-server.log}
PID_FILE="$STREAMLAKE_RUN_DIR/api-server.pid"

get_pid() {
    pid_from_file_or_port "$PID_FILE" "$API_PORT"
}

start_api() {
    load_secrets
    require_command java || fail_action "spring_start" "java command unavailable."
    require_file "$API_JAR" "API jar" || fail_action "spring_start" "API jar is missing. Build api-server first."
    require_env DORIS_PASSWORD || fail_action "spring_start" "DORIS_PASSWORD is required for Spring API."
    local pid
    pid="$(get_pid || true)"
    if [ -n "$pid" ]; then
        fail_action "spring_start" "Spring API is already running with PID $pid."
    fi
    check_dry_run "spring_start"
    info "Starting Spring API from $API_JAR"
    nohup java -jar "$API_JAR" > "$API_LOG" 2>&1 &
    echo "$!" > "$PID_FILE"
    if ! wait_http_ok "$API_HEALTH_URL" 30; then
        fail_action "spring_start" "Spring API did not become healthy at $API_HEALTH_URL. See $API_LOG."
    fi
    log_action "spring_start" "SUCCESS" "Started Spring API with PID $(cat "$PID_FILE")"
}

stop_api() {
    local pid
    pid="$(get_pid || true)"
    if [ -z "$pid" ]; then
        info "Spring API is not running."
        log_action "spring_stop" "SUCCESS" "Spring API already stopped"
        return 0
    fi
    check_dry_run "spring_stop $pid"
    info "Stopping Spring API (PID $pid)"
    stop_pid "$pid" "Spring API"
    rm -f "$PID_FILE"
    log_action "spring_stop" "SUCCESS" "Stopped Spring API ($pid)"
}

case "$ACTION" in
    status)
        PID="$(get_pid || true)"
        if [ -n "$PID" ]; then
            info "Spring API is running with PID $PID"
            curl -fsS --max-time 3 "$API_HEALTH_URL" || true
            echo
        else
            info "Spring API is NOT running."
        fi
        log_action "spring_status" "SUCCESS" "Checked Spring API status"
        ;;
    start)
        start_api
        ;;
    stop)
        require_confirm "$CONFIRM" "spring_stop"
        stop_api
        ;;
    restart)
        require_confirm "$CONFIRM" "spring_restart"
        stop_api
        start_api
        log_action "spring_restart" "SUCCESS" "Restarted Spring API"
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
