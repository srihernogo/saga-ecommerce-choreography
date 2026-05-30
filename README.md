# 🛒 Saga E-Commerce — Choreography Pattern

Production-grade implementation of the **Saga Choreography Pattern** for distributed transactions in a microservices-based e-commerce system, built with **Java 21**, **Spring Boot 3.2**, **Spring Cloud Stream**, **Apache Kafka**, and **PostgreSQL**.

---

## 📐 Architecture Overview

```
┌──────────┐   order.created   ┌──────────────┐  payment.completed  ┌─────────────────┐
│  Order   │ ───────────────► │   Payment    │ ──────────────────► │   Inventory     │
│ Service  │ ◄─────────────── │   Service    │ ◄────────────────── │   Service       │
│ :8081    │  payment.failed   │   :8082      │  inventory.failed   │   :8083         │
└──────────┘                   └──────────────┘                     └─────────────────┘
     ▲                                                                      │
     │                                                              inventory.reserved
     │         shipping.created / shipping.failed                           │
     │◄──────────────────────────────────────────── ┌─────────────────┐ ◄───┘
     │                                              │   Shipping      │
     └─────────── order.cancelled ────────────────► │   Service       │
                  (compensation)                    │   :8084         │
                                                    └─────────────────┘
```

### Happy Path Flow

1. **Order Service** menerima request → publish `order.created`
2. **Payment Service** consume → proses pembayaran → publish `payment.completed`
3. **Inventory Service** consume → reserve stock (pessimistic lock) → publish `inventory.reserved`
4. **Shipping Service** consume → buat shipment → publish `shipping.created`
5. **Order Service** consume → mark order `COMPLETED`

### Compensation (Unhappy Path)

Jika ada failure di step manapun, **Order Service** publish `order.cancelled` dan semua service melakukan rollback:
- **Payment Service** → refund
- **Inventory Service** → release stock
- **Shipping Service** → cancel shipment

---

## 🧩 Key Features

| Feature | Implementasi |
|---|---|
| **Transactional Outbox** | Setiap service menyimpan event ke tabel `outbox_messages` dalam transaksi yang sama, lalu scheduler publish ke Kafka |
| **Idempotent Consumer** | Tabel `processed_events` mencegah duplikasi event processing |
| **Idempotent API** | Header `Idempotency-Key` pada Order creation |
| **Pessimistic Locking** | `@Lock(PESSIMISTIC_WRITE)` di `ProductRepository` untuk mencegah race condition pada stock |
| **Saga State Tracking** | `SagaState` entity melacak setiap step dari saga lifecycle |
| **Stuck Saga Detection** | Scheduled job mendeteksi saga yang terlalu lama IN_PROGRESS |
| **Dead Letter Queue** | Topic `.dlq` untuk setiap event type |
| **Monitoring** | Prometheus metrics + Grafana + Zipkin tracing |

---

## 📁 Project Structure

```
saga-ecommerce/
├── pom.xml                         # Parent POM (multi-module Maven)
├── docker-compose.yml              # Full infrastructure + services
├── Makefile                        # Shortcut commands
├── .env.example                    # Environment variables template
│
├── scripts/
│   ├── init-databases.sql          # PostgreSQL init (auto-run on first start)
│   ├── create-topics.sh            # Kafka topics creation
│   └── smoke-test.sh               # End-to-end verification script
│
├── monitoring/
│   ├── prometheus.yml              # Prometheus scrape config
│   └── saga_alerts.yml             # Alerting rules
│
├── shared-library/                 # Shared event contracts & DTOs
│   └── src/main/java/.../shared/
│       ├── constant/KafkaTopics.java
│       ├── dto/OrderItemDto.java
│       └── event/                  # All domain events (BaseEvent, etc.)
│
├── order-service/                  # Order management + Saga tracker
├── payment-service/                # Payment processing (Midtrans gateway)
├── inventory-service/              # Stock reservation (pessimistic lock)
└── shipping-service/               # Shipment creation
```

---

## ⚙️ Prerequisites

Pastikan tools berikut sudah terinstall:

| Tool | Minimum Version | Cek Versi |
|---|---|---|
| **Java JDK** | 21 | `java -version` |
| **Maven** | 3.9+ | `mvn -version` |
| **Docker** | 24+ | `docker --version` |
| **Docker Compose** | v2.20+ | `docker compose version` |
| **curl** | any | `curl --version` |
| **jq** *(optional)* | any | `jq --version` |

> ⚠️ **Penting:** Pastikan Docker Desktop sudah running dan memiliki minimal **4 GB RAM** yang dialokasikan (Settings → Resources → Memory).

---

## 🚀 Step-by-Step: Menjalankan Aplikasi

### Step 1 — Clone & Navigate

```bash
cd saga-ecommerce
```

### Step 2 — Konfigurasi Environment

Copy file `.env.example` menjadi `.env`:

```bash
# Linux / macOS
cp .env.example .env

# Windows (PowerShell)
Copy-Item .env.example .env
```

Edit `.env` sesuai kebutuhan (untuk local development, nilai default sudah cukup):

```properties
# Database
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Grafana
GRAFANA_PASSWORD=admin
```

### Step 3 — Build Semua Module

Build dilakukan di host menggunakan Maven lokal. Dockerfile service hanya membuat runtime image dari JAR di folder `target`, sehingga Docker tidak perlu pull image `maven:*` dan dependency Maven memakai cache lokal host (`~/.m2` / `%USERPROFILE%\.m2`).

```bash
mvn clean package -DskipTests
```

Output yang diharapkan:
```
[INFO] Saga E-Commerce — Parent ............. SUCCESS
[INFO] Saga E-Commerce — Shared Library ..... SUCCESS
[INFO] Saga E-Commerce — Order Service ...... SUCCESS
[INFO] Saga E-Commerce — Payment Service .... SUCCESS
[INFO] Saga E-Commerce — Inventory Service .. SUCCESS
[INFO] Saga E-Commerce — Shipping Service ... SUCCESS
[INFO] BUILD SUCCESS
```

### Step 4 — Build Docker Image Microservices

Setelah JAR terbentuk, build image service. Image ini hanya membutuhkan base runtime `eclipse-temurin:21-jre-alpine`.

```bash
docker compose build order-service payment-service inventory-service shipping-service
```

Untuk build satu service saja:

```bash
docker compose build order-service
```

> Jika source code berubah, jalankan ulang `mvn clean package -DskipTests` sebelum `docker compose build`, supaya JAR terbaru yang masuk ke image.

### Step 5 — Jalankan Infrastructure (Database + Kafka + Monitoring)

Jalankan infrastructure terlebih dahulu:

```bash
docker compose up -d postgres kafka zipkin prometheus grafana kafka-ui
```

Tunggu sampai PostgreSQL dan Kafka healthy:

```bash
# Cek status container
docker compose ps

# Tunggu postgres healthy
docker compose exec postgres pg_isready -U postgres
```

### Step 6 — Inisialisasi Kafka Topics

```bash
docker compose up kafka-init
```

Tunggu sampai keluar pesan:
```
✅ All Kafka topics created successfully!
```

### Step 7 — Jalankan Semua Microservices

```bash
docker compose up -d order-service payment-service inventory-service shipping-service
```

Atau jalankan semuanya sekaligus:

```bash
docker compose up -d
```

### Step 8 — Verifikasi Semua Service Running

Tunggu ~90 detik (Spring Boot startup time), lalu cek health:

```bash
# Cek semua container
docker compose ps

# Health check per-service
curl -s http://localhost:8081/actuator/health | jq .status
curl -s http://localhost:8082/actuator/health | jq .status
curl -s http://localhost:8083/actuator/health | jq .status
curl -s http://localhost:8084/actuator/health | jq .status
```

Semua harus return `"UP"`.

---

## 🧪 Step-by-Step: Testing

### Test 1 — Create Order (Happy Path)

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{
    "customerId": "customer-001",
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
  }'
```

Catat `orderId` dari response.

### Test 2 — Tunggu Saga Selesai (~10-20 detik)

```bash
# Ganti <ORDER_ID> dengan orderId dari response sebelumnya
curl -s http://localhost:8081/api/v1/orders/<ORDER_ID> | jq .
```

Status yang diharapkan: `COMPLETED`

### Test 3 — Cek Saga State

```bash
curl -s http://localhost:8081/internal/saga/<ORDER_ID> | jq .
```

### Test 4 — Cek Idempotency

Kirim request yang sama dengan `Idempotency-Key: test-001`:

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{"customerId":"x","shippingAddress":"x","paymentMethod":"VA","items":[{"productId":"x","productName":"x","quantity":1,"unitPrice":100}]}'
```

Harus return `orderId` yang sama — artinya idempotency bekerja.

### Test 5 — Saga Statistics

```bash
curl -s http://localhost:8081/internal/saga/stats | jq .
```

### Test 6 — Stuck Saga Detection

```bash
curl -s "http://localhost:8081/internal/saga/stuck?olderThanMinutes=5" | jq .
```

### Automated Smoke Test

Jalankan script smoke test yang sudah disiapkan:

```bash
# Linux / macOS
./scripts/smoke-test.sh

# Windows (Git Bash)
bash scripts/smoke-test.sh
```

---

## 📊 Monitoring & Observability

### Dashboard URLs

| Service | URL | Credentials |
|---|---|---|
| **Kafka UI** | [http://localhost:8080](http://localhost:8080) | — |
| **Zipkin** (Distributed Tracing) | [http://localhost:9411](http://localhost:9411) | — |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | — |
| **Grafana** | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` |

### Prometheus Metrics

Setiap service mengexpose metrics di `/actuator/prometheus`. Contoh query di Prometheus:

```promql
# Jumlah saga yang sedang berjalan
saga_in_progress_count

# Rate order per menit
rate(saga_started_total[5m])

# Rata-rata durasi saga yang sukses
saga_duration_seconds{result="success"}
```

### Zipkin Tracing

Buka Zipkin UI → klik "Run Query" untuk melihat distributed trace dari setiap request yang melewati semua service.

---

## 🛠️ Makefile Shortcuts

```bash
make build     # Build Maven + Docker images
make up        # Start semua container + health check
make down      # Stop & hapus semua container + volumes
make logs svc=order-service  # Tail logs service tertentu
make health    # Cek health semua service
make smoke     # Run smoke test
make test      # Run semua unit test
make clean     # Cleanup artifacts + containers + images
```

---

## 🔌 Service Ports

| Service | Port | Endpoint |
|---|---|---|
| Order Service | `8081` | `http://localhost:8081/api/v1/orders` |
| Payment Service | `8082` | `http://localhost:8082/actuator/health` |
| Inventory Service | `8083` | `http://localhost:8083/actuator/health` |
| Shipping Service | `8084` | `http://localhost:8084/actuator/health` |
| PostgreSQL | `5432` | `jdbc:postgresql://localhost:5432/order_db` |
| Kafka | `29092` | `localhost:29092` (from host) |
| Kafka UI | `8080` | `http://localhost:8080` |
| Zipkin | `9411` | `http://localhost:9411` |
| Prometheus | `9090` | `http://localhost:9090` |
| Grafana | `3000` | `http://localhost:3000` |

---

## 🗄️ Database

Setiap service memiliki database terpisah (Database per Service pattern):

| Service | Database | User |
|---|---|---|
| Order Service | `order_db` | `order_user` / `order_pass` |
| Payment Service | `payment_db` | `payment_user` / `payment_pass` |
| Inventory Service | `inventory_db` | `inventory_user` / `inventory_pass` |
| Shipping Service | `shipping_db` | `shipping_user` / `shipping_pass` |

Schema di-manage oleh **Flyway** — migrasi otomatis saat service startup.

Akses database secara langsung:

```bash
# Connect ke order_db
docker compose exec postgres psql -U postgres -d order_db

# Lihat tabel
\dt

# Lihat outbox messages
SELECT id, event_type, status, created_at FROM outbox_messages ORDER BY created_at DESC LIMIT 10;

# Lihat saga states
SELECT order_id, current_step, status, started_at FROM saga_states;
```

---

## 📨 Kafka Topics

| Topic | Publisher | Consumer(s) |
|---|---|---|
| `order.created` | Order Service | Payment Service |
| `order.cancelled` | Order Service | Payment, Inventory, Shipping |
| `order.completed` | Order Service | — |
| `payment.completed` | Payment Service | Order, Inventory |
| `payment.failed` | Payment Service | Order Service |
| `inventory.reserved` | Inventory Service | Order, Shipping |
| `inventory.failed` | Inventory Service | Order Service |
| `shipping.created` | Shipping Service | Order Service |
| `shipping.failed` | Shipping Service | Order Service |

Setiap topic memiliki DLQ (Dead Letter Queue) pasangannya, contoh: `order.created.dlq`

---

## 🔧 Troubleshooting

### Container tidak mau start

```bash
# Lihat log container tertentu
docker compose logs order-service

# Restart satu service
docker compose restart order-service
```

### Database connection error

```bash
# Pastikan postgres sudah ready
docker compose exec postgres pg_isready -U postgres

# Cek apakah database sudah dibuat
docker compose exec postgres psql -U postgres -l
```

### Kafka topics tidak terbuat

```bash
# Jalankan ulang kafka-init
docker compose restart kafka-init

# Cek topics secara manual
docker compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Service stuck di "starting"

Spring Boot butuh waktu ~60-90 detik untuk startup. Cek log:

```bash
docker compose logs -f order-service
```

### Docker build gagal karena JAR tidak ditemukan

Dockerfile service meng-copy JAR dari folder `target`. Jika muncul error seperti `COPY ... target/*.jar` atau `no such file or directory`, build ulang module Maven terlebih dahulu:

```bash
mvn clean package -DskipTests
docker compose build order-service payment-service inventory-service shipping-service
```

### Docker gagal pull base image

Jika muncul error DNS seperti `lookup registry-1.docker.io: no such host` atau `lookup auth.docker.io: no such host`, masalahnya ada di koneksi Docker Desktop ke Docker Hub. Coba pull base image runtime secara manual setelah memperbaiki DNS/proxy Docker Desktop:

```bash
docker pull eclipse-temurin:21-jre-alpine
```

### Reset semua data

```bash
# Stop semua, hapus volumes, dan mulai dari awal
docker compose down -v
docker compose up -d
```

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.3 |
| Messaging | Spring Cloud Stream + Apache Kafka (KRaft) |
| Database | PostgreSQL 16 |
| Migration | Flyway |
| Scheduling | ShedLock |
| Metrics | Micrometer + Prometheus |
| Tracing | Brave + Zipkin |
| Dashboard | Grafana |
| Container | Docker + Docker Compose |
| Build | Maven (multi-module) |

---

## 📜 License

This project is for educational purposes — demonstrating the Saga Choreography Pattern in a production-grade setup.
