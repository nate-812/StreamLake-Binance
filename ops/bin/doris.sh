#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    cat <<'USAGE'
Usage: doris.sh {status|start|stop|restart} [--confirm]

Environment overrides:
  DORIS_FE_HOME=/opt/doris/fe
  DORIS_BE_HOME=/opt/doris/be
  DORIS_FE_HTTP=http://127.0.0.1:8030
USAGE
}

ACTION=${1:-}
CONFIRM=${2:-}
DORIS_FE_HOME=${DORIS_FE_HOME:-/opt/doris/fe}
DORIS_BE_HOME=${DORIS_BE_HOME:-/opt/doris/be}
DORIS_FE_START=${DORIS_FE_START:-$DORIS_FE_HOME/bin/start_fe.sh}
DORIS_FE_STOP=${DORIS_FE_STOP:-$DORIS_FE_HOME/bin/stop_fe.sh}
DORIS_BE_START=${DORIS_BE_START:-$DORIS_BE_HOME/bin/start_be.sh}
DORIS_BE_STOP=${DORIS_BE_STOP:-$DORIS_BE_HOME/bin/stop_be.sh}
DORIS_FE_HTTP=${DORIS_FE_HTTP:-http://127.0.0.1:8030}

start_doris() {
    local started=0
    if [ -x "$DORIS_FE_START" ]; then
        started=1
    fi
    if [ -x "$DORIS_BE_START" ]; then
        started=1
    fi
    [ "$started" -eq 1 ] || fail_action "doris_start" "No Doris start script found. Set DORIS_FE_HOME or DORIS_BE_HOME."
    check_dry_run "doris_start"
    if [ -x "$DORIS_FE_START" ]; then
        "$DORIS_FE_START" --daemon || fail_action "doris_start" "Doris FE start failed."
    fi
    if [ -x "$DORIS_BE_START" ]; then
        "$DORIS_BE_START" --daemon || fail_action "doris_start" "Doris BE start failed."
    fi
    log_action "doris_start" "SUCCESS" "Started configured Doris components"
}

stop_doris() {
    local stopped=0
    if [ -x "$DORIS_BE_STOP" ]; then
        stopped=1
    fi
    if [ -x "$DORIS_FE_STOP" ]; then
        stopped=1
    fi
    [ "$stopped" -eq 1 ] || fail_action "doris_stop" "No Doris stop script found. Set DORIS_FE_HOME or DORIS_BE_HOME."
    check_dry_run "doris_stop"
    if [ -x "$DORIS_BE_STOP" ]; then
        "$DORIS_BE_STOP" || fail_action "doris_stop" "Doris BE stop failed."
    fi
    if [ -x "$DORIS_FE_STOP" ]; then
        "$DORIS_FE_STOP" || fail_action "doris_stop" "Doris FE stop failed."
    fi
    log_action "doris_stop" "SUCCESS" "Stopped configured Doris components"
}

case "$ACTION" in
    status)
        if curl -fsS --max-time 3 "$DORIS_FE_HTTP/api/bootstrap" >/dev/null 2>&1; then
            info "Doris FE HTTP is reachable at $DORIS_FE_HTTP"
            log_action "doris_status" "SUCCESS" "Doris FE HTTP reachable"
        else
            fail_action "doris_status" "Doris FE HTTP is not reachable at $DORIS_FE_HTTP."
        fi
        ;;
    start)
        require_confirm "$CONFIRM" "doris_start"
        start_doris
        ;;
    stop)
        require_confirm "$CONFIRM" "doris_stop"
        stop_doris
        ;;
    restart)
        require_confirm "$CONFIRM" "doris_restart"
        stop_doris
        start_doris
        log_action "doris_restart" "SUCCESS" "Restarted Doris"
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
