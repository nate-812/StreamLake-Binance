#!/usr/bin/env bash
set -euo pipefail

FLINK_HOME=${FLINK_HOME:-/opt/flink}
FLINK_BIN=${FLINK_BIN:-$FLINK_HOME/bin/flink}

if [ ! -x "$FLINK_BIN" ]; then
    echo "ERROR: Flink CLI not executable: $FLINK_BIN" >&2
    exit 2
fi

echo "=== Flink Running Jobs ==="
"$FLINK_BIN" list -r
