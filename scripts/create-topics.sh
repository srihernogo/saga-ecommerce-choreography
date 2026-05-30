#!/bin/bash
# create-topics.sh — Membuat semua Kafka topics yang diperlukan

set -e
KAFKA_BROKER="kafka:9092"

echo "⏳ Waiting for Kafka to be ready..."
# cub kafka-ready -b $KAFKA_BROKER 1 30 >> Bikin error denied
until kafka-topics \
    --bootstrap-server $KAFKA_BROKER \
    --list > /dev/null 2>&1
do
    sleep 2
done

echo "✅ Kafka ready"

echo "📝 Creating Kafka topics..."

create_topic() {
  local TOPIC=$1
  local PARTITIONS=${2:-3}
  local REPLICATION=${3:-1}
  local RETENTION_MS=${4:-604800000}  # 7 hari default

  kafka-topics --create \
    --bootstrap-server $KAFKA_BROKER \
    --topic $TOPIC \
    --partitions $PARTITIONS \
    --replication-factor $REPLICATION \
    --config retention.ms=$RETENTION_MS \
    --if-not-exists

  echo "  ✅ Created: $TOPIC (partitions=$PARTITIONS)"
}

# ── Forward topics (3 partisi untuk throughput) ─────────────────────────
create_topic "order.created"          3 1
create_topic "order.cancelled"        3 1
create_topic "order.completed"        3 1
create_topic "payment.completed"      3 1
create_topic "payment.failed"         3 1
create_topic "payment.refund.requested" 3 1
create_topic "payment.refunded"       3 1
create_topic "inventory.reserved"     3 1
create_topic "inventory.failed"       3 1
create_topic "inventory.released"     3 1
create_topic "shipping.created"       3 1
create_topic "shipping.failed"        3 1

# ── DLQ topics (1 partisi, retensi 30 hari untuk investigasi) ──────────
DLQ_RETENTION=$((30 * 24 * 60 * 60 * 1000))  # 30 hari dalam ms

create_topic "order.created.dlq"          1 1 $DLQ_RETENTION
create_topic "payment.completed.dlq"      1 1 $DLQ_RETENTION
create_topic "payment.failed.dlq"         1 1 $DLQ_RETENTION
create_topic "inventory.reserved.dlq"     1 1 $DLQ_RETENTION
create_topic "inventory.failed.dlq"       1 1 $DLQ_RETENTION
create_topic "shipping.created.dlq"       1 1 $DLQ_RETENTION

echo ""
echo "✅ All Kafka topics created successfully!"
echo ""
echo "📋 Topics created:"
kafka-topics --list --bootstrap-server $KAFKA_BROKER | grep -E "(order|payment|inventory|shipping)"
