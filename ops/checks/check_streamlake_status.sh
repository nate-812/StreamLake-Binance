#!/usr/bin/env bash
set -euo pipefail

# Output format: JSON
echo "{"
echo "  \"api\": \"$(curl -s http://127.0.0.1:8080/actuator/health | jq -r .status || echo "DOWN")\","
echo "  \"ai\": \"$(curl -s http://127.0.0.1:8000/health | jq -r .status || echo "DOWN")\""
echo "}"
