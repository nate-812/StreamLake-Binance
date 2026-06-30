#!/usr/bin/env bash
set -euo pipefail

echo "=== Flink Running Jobs ==="
flink list -r || echo "No running jobs found"
