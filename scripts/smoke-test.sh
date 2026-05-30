#!/bin/bash
# smoke-test.sh — Verifikasi Saga end-to-end setelah docker compose up

set -e

BASE_URL="http://localhost:8081"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "🔍 Starting Saga Smoke Test..."
echo "================================"

# Tunggu semua service siap
echo "⏳ Waiting for services to be ready..."
for port in 8081 8082 8083 8084; do
  until curl -sf "http://localhost:$port/actuator/health" > /dev/null; do
    echo "  Service :$port not ready yet, waiting..."
    sleep 5
  done
  echo "  ✅ Service :$port is ready"
done

echo ""
echo "1️⃣  Creating new order (Happy Path)..."
IDEMPOTENCY_KEY=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)

RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "customerId": "customer-test-001",
    "shippingAddress": "Jl. Sudirman No.1, Jakarta Pusat 10220",
    "paymentMethod": "TRANSFER_VA",
    "items": [
      {
        "productId": "prod-laptop-001",
        "productName": "Laptop Gaming 15 inch",
        "quantity": 1,
        "unitPrice": 15000000
      },
      {
        "productId": "prod-mouse-001",
        "productName": "Wireless Mouse",
        "quantity": 2,
        "unitPrice": 250000
      }
    ]
  }')

echo "Response: $RESPONSE" | head -c 200
ORDER_ID=$(echo $RESPONSE | python3 -c "import sys,json; print(json.load(sys.stdin).get('orderId',''))" 2>/dev/null || \
           echo $RESPONSE | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ORDER_ID" ]; then
  echo -e "${RED}❌ Failed to create order!${NC}"
  exit 1
fi

echo -e "${GREEN}✅ Order created: $ORDER_ID${NC}"

echo ""
echo "2️⃣  Waiting for Saga to complete (20 seconds)..."
sleep 20

echo ""
echo "3️⃣  Checking order status..."
ORDER_STATUS=$(curl -s "$BASE_URL/api/v1/orders/$ORDER_ID" | \
  python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)

echo "   Order status: $ORDER_STATUS"
if [[ "$ORDER_STATUS" == "SHIPPED" || "$ORDER_STATUS" == "COMPLETED" ]]; then
  echo -e "${GREEN}✅ Happy path: SUCCESS${NC}"
else
  echo -e "${YELLOW}⚠️  Order not yet SHIPPED (status=$ORDER_STATUS) — might need more time${NC}"
fi

echo ""
echo "4️⃣  Checking Saga state..."
SAGA_STATUS=$(curl -s "$BASE_URL/internal/saga/$ORDER_ID" | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(f'step={d[\"currentStep\"]} status={d[\"status\"]}')" 2>/dev/null)
echo "   Saga: $SAGA_STATUS"

echo ""
echo "5️⃣  Testing idempotency (same request again)..."
RESPONSE2=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{"customerId":"customer-test-001","items":[],"shippingAddress":"test","paymentMethod":"VA"}')

ORDER_ID_2=$(echo $RESPONSE2 | python3 -c "import sys,json; print(json.load(sys.stdin).get('orderId',''))" 2>/dev/null)

if [ "$ORDER_ID" = "$ORDER_ID_2" ]; then
  echo -e "${GREEN}✅ Idempotency: PASS (same orderId returned)${NC}"
else
  echo -e "${RED}❌ Idempotency: FAIL (different orderId: $ORDER_ID_2)${NC}"
fi

echo ""
echo "6️⃣  Checking for stuck sagas..."
STUCK=$(curl -s "$BASE_URL/internal/saga/stuck?olderThanMinutes=5")
echo "   Stuck sagas: $STUCK"

echo ""
echo "================================"
echo "🔗 Useful links:"
echo "   Kafka UI:  http://localhost:8080"
echo "   Zipkin:    http://localhost:9411"
echo "   Grafana:   http://localhost:3000 (admin/admin)"
echo "   Prometheus:http://localhost:9090"
echo ""
echo "📊 Saga Stats:"
curl -s "$BASE_URL/internal/saga/stats" | python3 -m json.tool 2>/dev/null
echo ""
echo "✅ Smoke test completed!"
