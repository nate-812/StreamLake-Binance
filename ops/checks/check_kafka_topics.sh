#!/usr/bin/env bash
set -euo pipefail

KAFKA_HOME=${KAFKA_HOME:-/opt/kafka}
KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-192.168.1.10:9092}
KAFKA_TOPICS_BIN=${KAFKA_TOPICS_BIN:-$KAFKA_HOME/bin/kafka-topics.sh}
KAFKA_GROUPS_BIN=${KAFKA_GROUPS_BIN:-$KAFKA_HOME/bin/kafka-consumer-groups.sh}

if [ ! -x "$KAFKA_TOPICS_BIN" ]; then
    echo "ERROR: kafka-topics.sh not executable: $KAFKA_TOPICS_BIN" >&2
    exit 2
fi
if [ ! -x "$KAFKA_GROUPS_BIN" ]; then
    echo "ERROR: kafka-consumer-groups.sh not executable: $KAFKA_GROUPS_BIN" >&2
    exit 2
fi

echo "=== Kafka Topics ==="
"$KAFKA_TOPICS_BIN" --bootstrap-server "$KAFKA_BOOTSTRAP" --list

echo "=== Consumer Groups ==="
"$KAFKA_GROUPS_BIN" --bootstrap-server "$KAFKA_BOOTSTRAP" --list
