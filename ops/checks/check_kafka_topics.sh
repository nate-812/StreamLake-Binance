#!/usr/bin/env bash
set -euo pipefail

KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-"192.168.1.10:9092"}

echo "=== Kafka Topics ==="
kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --list

echo "=== Consumer Groups ==="
kafka-consumer-groups.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --list
