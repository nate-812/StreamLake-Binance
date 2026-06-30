#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    echo "Usage: $0 {status|start|stop} [job_name] [--confirm]"
    echo "Commands:"
    echo "  status            Check status of jobs"
    echo "  start <job>       Submit a job (will reject if already RUNNING)"
    echo "  stop <job>        Cancel a job (requires --confirm)"
}

ACTION=${1:-}
JOB_NAME=${2:-}
CONFIRM=${3:-}

if [ -z "$ACTION" ]; then
    usage
    exit 1
fi

case "$ACTION" in
    status)
        info "Fetching running jobs..."
        # Simulated Flink API check
        flink list -r || echo "No flink cluster found or no jobs running"
        log_action "job_status" "SUCCESS" "Checked jobs"
        ;;
    start)
        if [ -z "$JOB_NAME" ]; then
            error "start requires a job_name (e.g. kline, risk, whale)"
            exit 1
        fi
        load_secrets
        info "Checking if $JOB_NAME is already RUNNING..."
        # Simulated check
        if flink list -r 2>/dev/null | grep -q "$JOB_NAME"; then
            error "Job $JOB_NAME is already RUNNING. Refusing duplicate submission."
            log_action "job_start" "FAILED" "Duplicate job $JOB_NAME"
            exit 1
        fi
        check_dry_run "start_job $JOB_NAME"
        info "Submitting job $JOB_NAME..."
        log_action "job_start" "SUCCESS" "Started $JOB_NAME"
        ;;
    stop)
        if [ -z "$JOB_NAME" ]; then
            error "stop requires a job_name"
            exit 1
        fi
        require_confirm "$CONFIRM" "job_stop"
        check_dry_run "stop_job $JOB_NAME"
        info "Stopping job $JOB_NAME..."
        log_action "job_stop" "SUCCESS" "Stopped $JOB_NAME"
        ;;
    --help)
        usage
        ;;
    *)
        usage
        exit 1
        ;;
esac
