#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/common.sh"

usage() {
    cat <<'USAGE'
Usage: job.sh {status|start|stop} [kline|risk|whale] [--confirm]

Commands:
  status              List running Flink jobs.
  start <job>         Submit one known StreamLake job if it is not already running.
  stop <job> --confirm
                      Cancel the running StreamLake job by Flink Job ID.
USAGE
}

ACTION=${1:-}
JOB_KEY=${2:-}
CONFIRM=${3:-}

job_config() {
    case "$1" in
        kline)
            JOB_NAME=streamlake-kline-aggregation
            JOB_JAR="$STREAMLAKE_ROOT/stream-jobs/job-kline/target/job-kline-1.0.0-SNAPSHOT.jar"
            REQUIRED_ENV=(DORIS_PASSWORD)
            ;;
        risk)
            JOB_NAME=streamlake-risk-control
            JOB_JAR="$STREAMLAKE_ROOT/stream-jobs/job-risk-control/target/job-risk-control-1.0.0-SNAPSHOT.jar"
            REQUIRED_ENV=(DORIS_PASSWORD MYSQL_PASSWORD REDIS_PASSWORD)
            ;;
        whale)
            JOB_NAME=streamlake-whale-cep
            JOB_JAR="$STREAMLAKE_ROOT/stream-jobs/job-whale-cep/target/job-whale-cep-1.0.0-SNAPSHOT.jar"
            REQUIRED_ENV=(DORIS_PASSWORD MYSQL_PASSWORD)
            ;;
        *)
            fail_action "job_config" "Unknown job '$1'; expected kline, risk, or whale."
            ;;
    esac
}

list_running_jobs() {
    require_executable "$FLINK_BIN" "Flink CLI" || return 1
    "$FLINK_BIN" list -r
}

is_job_running() {
    local name="$1"
    list_running_jobs 2>/dev/null | grep -F "$name" >/dev/null 2>&1
}

find_job_id() {
    local name="$1"
    list_running_jobs | awk -F' : ' -v name="$name" '$0 ~ name {print $2; exit}'
}

case "$ACTION" in
    status)
        info "Fetching running Flink jobs..."
        list_running_jobs || fail_action "job_status" "Unable to list Flink jobs."
        log_action "job_status" "SUCCESS" "Listed running jobs"
        ;;
    start)
        [ -n "$JOB_KEY" ] || fail_action "job_start" "start requires a job key."
        job_config "$JOB_KEY"
        load_secrets
        require_executable "$FLINK_BIN" "Flink CLI" || fail_action "job_start" "Flink CLI unavailable."
        require_file "$JOB_JAR" "Job jar" || fail_action "job_start" "Job jar is missing. Build stream-jobs first."
        require_env "${REQUIRED_ENV[@]}" || fail_action "job_start" "Missing credentials for $JOB_NAME."
        if is_job_running "$JOB_NAME"; then
            fail_action "job_start" "Job $JOB_NAME is already RUNNING. Refusing duplicate submission."
        fi
        check_dry_run "job_start $JOB_NAME"
        info "Submitting $JOB_NAME from $JOB_JAR"
        "$FLINK_BIN" run -d "$JOB_JAR" || fail_action "job_start" "Flink submission failed for $JOB_NAME."
        log_action "job_start" "SUCCESS" "Submitted $JOB_NAME"
        ;;
    stop)
        [ -n "$JOB_KEY" ] || fail_action "job_stop" "stop requires a job key."
        require_confirm "$CONFIRM" "job_stop"
        job_config "$JOB_KEY"
        require_executable "$FLINK_BIN" "Flink CLI" || fail_action "job_stop" "Flink CLI unavailable."
        JOB_ID="$(find_job_id "$JOB_NAME")"
        [ -n "$JOB_ID" ] || fail_action "job_stop" "No RUNNING job found for $JOB_NAME."
        check_dry_run "job_stop $JOB_NAME $JOB_ID"
        info "Cancelling $JOB_NAME ($JOB_ID)"
        "$FLINK_BIN" cancel "$JOB_ID" || fail_action "job_stop" "Flink cancel failed for $JOB_NAME ($JOB_ID)."
        log_action "job_stop" "SUCCESS" "Cancelled $JOB_NAME ($JOB_ID)"
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
