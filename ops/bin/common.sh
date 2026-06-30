#!/usr/bin/env bash
set -euo pipefail

LOG_FILE="/var/log/streamlake-ops/actions.log"

# Setup logging directory if run as root
if [ -w "$(dirname "$LOG_FILE")" ] || [ "$EUID" -eq 0 ]; then
    mkdir -p "$(dirname "$LOG_FILE")"
    touch "$LOG_FILE"
else
    # Fallback to local
    LOG_FILE="/tmp/streamlake-ops-actions.log"
    touch "$LOG_FILE"
fi

log_action() {
    local action="$1"
    local status="$2"
    local msg="$3"
    echo "$(date '+%Y-%m-%d %H:%M:%S') | $(whoami) | $action | $status | $msg" >> "$LOG_FILE"
}

info() {
    echo -e "\033[1;34m[INFO]\033[0m $*"
}

warn() {
    echo -e "\033[1;33m[WARN]\033[0m $*"
}

error() {
    echo -e "\033[1;31m[ERROR]\033[0m $*" >&2
}

load_secrets() {
    local secret_file="/root/.streamlake-secrets"
    if [ -f "$secret_file" ]; then
        info "Loading secrets from $secret_file (cloud environment)"
        source "$secret_file"
    elif [ -f "$(dirname "$0")/../../.env" ]; then
        info "Loading secrets from local .env (local dev)"
        source "$(dirname "$0")/../../.env"
    else
        warn "No secret file found. Assuming environment variables are already set."
    fi
}

check_dry_run() {
    if [ "${DRY_RUN:-0}" = "1" ]; then
        info "DRY_RUN=1 is set. Simulating action: $*"
        log_action "$1" "DRY_RUN" "Simulated command"
        exit 0
    fi
}

require_confirm() {
    local confirm_flag="$1"
    local action_name="$2"
    if [ "$confirm_flag" != "--confirm" ]; then
        error "Action '$action_name' is high risk."
        error "You must explicitly append '--confirm' to execute this command."
        log_action "$action_name" "REJECTED" "Missing --confirm"
        exit 1
    fi
}
