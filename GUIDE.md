# Saga Choreography Guide

Dokumen ini menjelaskan cara kerja saga choreography di project `saga-ecommerce` dari awal sampai akhir, dengan fokus pada hal yang biasanya dicari interviewer: event flow, idempotency, compensation transaction, dan transactional outbox.

## 1. Big Picture

Masalah yang ingin diselesaikan adalah transaksi bisnis lintas service. Dalam e-commerce, membuat order bukan cuma insert ke tabel `orders`. Sistem juga harus mengambil pembayaran, mengurangi stock, membuat shipment, dan akhirnya menandai order selesai. Karena setiap service punya database sendiri, kita tidak memakai distributed transaction atau two-phase commit. Kita memakai saga.

Saga adalah rangkaian local transaction. Setiap service melakukan transaksi lokal di database miliknya sendiri, lalu menerbitkan event agar service berikutnya bergerak. Jika salah satu step gagal, sistem tidak rollback global seperti database tunggal. Sistem menjalankan compensation transaction, yaitu aksi bisnis kebalikan untuk membatalkan efek step yang sudah terlanjur sukses.

Di project ini pattern yang dipakai adalah choreography. Artinya tidak ada orchestrator terpusat yang memanggil semua service satu per satu. Setiap service bereaksi terhadap event yang relevan, melakukan pekerjaan lokalnya, lalu menerbitkan event berikutnya.

Order Service tetap menyimpan `SagaState`, tetapi perannya lebih sebagai tracker dan final decision maker untuk status order, bukan orchestrator yang mengirim command sinkron ke semua service.

## 2. Service dan Tanggung Jawab

Ada empat service utama:

| Service | Tanggung jawab | Event masuk | Event keluar |
|---|---|---|---|
| Order Service | Membuat order, tracking saga, final status | `payment.completed`, `payment.failed`, `inventory.reserved`, `inventory.failed`, `shipping.created`, `shipping.failed` | `order.created`, `order.cancelled`, `order.completed` |
| Payment Service | Charge payment dan refund | `order.created`, `order.cancelled` | `payment.completed`, `payment.failed` |
| Inventory Service | Reserve stock dan release stock | `payment.completed`, `order.cancelled` | `inventory.reserved`, `inventory.failed` |
| Shipping Service | Membuat shipment dan cancel shipment | `inventory.reserved`, `order.cancelled` | `shipping.created`, `shipping.failed` |

Shared event contract berada di `shared-library`, misalnya `OrderCreatedEvent`, `PaymentCompletedEvent`, `InventoryReservedEvent`, dan `OrderCancelledEvent`.

Semua event mewarisi `BaseEvent`, yang membawa:

- `eventId`: identitas unik event, dipakai untuk idempotency.
- `timestamp`: waktu event dibuat.
- `aggregateId`: id entity utama, dalam flow ini biasanya `orderId`.

## 2.1. Kenapa Ada `shared-library` Kalau Tidak Di-Run?

`shared-library` bukan microservice. Jadi memang tidak punya container, tidak punya port, dan tidak dijalankan dengan `docker compose up`.

Secara teknis, `shared-library` adalah Maven library atau JAR dependency yang dipakai oleh semua service saat compile dan runtime. Di setiap service ada dependency seperti ini:

```xml
<dependency>
  <groupId>com.company.saga</groupId>
  <artifactId>shared-library</artifactId>
</dependency>
```

Artinya saat kita menjalankan:

```bash
mvn clean package -DskipTests
```

Maven akan build `shared-library` terlebih dahulu menjadi JAR, lalu JAR itu dimasukkan sebagai dependency ke `order-service`, `payment-service`, `inventory-service`, dan `shipping-service`.

Isi `shared-library` adalah kontrak yang harus dimengerti bersama oleh semua service:

| Isi | Contoh | Dipakai untuk |
|---|---|---|
| Event classes | `OrderCreatedEvent`, `PaymentCompletedEvent`, `OrderCancelledEvent` | Payload event Kafka yang dipublish dan dicommit oleh service |
| Base event metadata | `BaseEvent` | `eventId`, `timestamp`, `aggregateId` untuk idempotency dan tracing |
| DTO bersama | `OrderItemDto` | Struktur data item order yang dikirim lintas service |
| Topic constants | `KafkaTopics.ORDER_CREATED` | Mencegah typo nama topic Kafka |

Contoh konkretnya:

- Order Service membuat object `OrderCreatedEvent` dari `shared-library`, lalu serialize ke JSON dan simpan ke outbox.
- Payment Service consume JSON dari Kafka dan deserialize kembali menjadi `OrderCreatedEvent` dari class yang sama.
- Semua service memakai `BaseEvent.eventId` untuk menyimpan `processed_events`.
- Semua service memakai `KafkaTopics` agar nama topic konsisten.

Jadi `shared-library` tidak di-run, tetapi tetap sangat penting karena ia adalah kontrak komunikasi antar service.

Analogi sederhananya:

```text
order-service       payment-service       inventory-service       shipping-service
     |                    |                      |                       |
     +--------------------+----------+-----------+-----------------------+
                                     |
                              shared-library
                       event contract + DTO + constants
```

Tanpa `shared-library`, setiap service harus mendefinisikan sendiri class event dan nama topic. Itu rawan drift:

- Order Service mengirim field `totalAmount`.
- Payment Service menunggu field `amount`.
- Satu service publish ke `payment.completed`.
- Service lain listen ke `payments.completed`.

Bug seperti ini sering baru ketahuan runtime. Dengan shared contract, mismatch lebih cepat ketahuan saat compile.

Namun di production yang lebih besar, ada trade-off. Shared library membuat kontrak kuat dan praktis, tetapi juga bisa membuat service terlalu coupling jika semua domain internal ikut dimasukkan ke sana. Isi shared library sebaiknya hanya contract lintas service: event, DTO, constant, schema/version metadata. Jangan masukkan business logic internal service.

## 3. Happy Path dari Awal sampai Akhir

Bayangkan user membuat order lewat API Order Service.

### Step 1: Client membuat order

Client memanggil endpoint create order dengan header `Idempotency-Key`.

Order Service menjalankan local transaction:

1. Cek apakah `Idempotency-Key` sudah pernah dipakai.
2. Jika belum, simpan `Order`.
3. Buat `OrderCreatedEvent`.
4. Simpan event ke tabel `outbox_messages`.
5. Buat `SagaState` dengan status awal.
6. Simpan record idempotency request.
7. Commit database transaction.

Poin senior-level penting: Order Service tidak langsung publish ke Kafka di tengah transaksi create order. Ia menyimpan event ke outbox dalam transaksi yang sama dengan order. Ini mencegah kondisi berbahaya seperti order sudah tersimpan tapi event gagal terkirim, atau event terkirim tapi order gagal commit.

Setelah commit, `OutboxPublisher` akan mengambil pending message dari tabel outbox dan publish `order.created` ke Kafka.

### Step 2: Payment Service menerima `order.created`

Payment Service consume `OrderCreatedEvent`.

Dalam local transaction:

1. Cek `processed_events` berdasarkan `eventId`.
2. Buat record `Payment`.
3. Panggil payment gateway.
4. Jika sukses, tandai payment sukses.
5. Simpan `PaymentCompletedEvent` ke outbox.
6. Tandai event `order.created` sebagai processed.
7. Commit.

Jika charge gagal, Payment Service menyimpan `PaymentFailedEvent` ke outbox.

Pada happy path, outbox Payment Service menerbitkan:

```text
payment.completed
```

### Step 3: Order Service dan Inventory Service menerima `payment.completed`

Event `payment.completed` punya dua efek.

Order Service menerimanya untuk update tracking:

1. Cek idempotency event.
2. Cari order.
3. Update order bahwa payment confirmed.
4. Update `SagaState` ke `PAYMENT_COMPLETED`.
5. Simpan `paymentId`.
6. Tandai event processed.

Inventory Service juga menerimanya untuk reserve stock:

1. Cek idempotency event.
2. Ambil product dengan pessimistic lock.
3. Kurangi available stock atau buat reservation.
4. Simpan reservation.
5. Simpan `InventoryReservedEvent` ke outbox.
6. Tandai event processed.

Poin senior-level penting: Kafka bisa deliver event lebih dari sekali. Karena itu setiap consumer harus aman jika event yang sama diproses ulang. Inilah alasan `processed_events` ada di setiap service.

### Step 4: Shipping Service menerima `inventory.reserved`

Inventory Service menerbitkan:

```text
inventory.reserved
```

Shipping Service consume event ini.

Dalam local transaction:

1. Cek event sudah pernah diproses atau belum.
2. Buat shipment untuk order.
3. Generate tracking number.
4. Simpan shipment.
5. Simpan `ShippingCreatedEvent` ke outbox.
6. Tandai event processed.

### Step 5: Order Service menerima `shipping.created`

Order Service consume `ShippingCreatedEvent`.

Dalam local transaction:

1. Cek idempotency event.
2. Update order menjadi shipped.
3. Mark order sebagai completed.
4. Update `SagaState` menjadi completed.
5. Record metric durasi saga.
6. Simpan `OrderCompletedEvent` ke outbox.
7. Tandai event processed.

Pada titik ini saga selesai.

Flow happy path bisa diringkas seperti ini:

```text
Client
  |
  v
Order Service
  - save Order
  - save SagaState
  - save outbox(order.created)
  |
  v
Kafka: order.created
  |
  v
Payment Service
  - charge payment
  - save Payment
  - save outbox(payment.completed)
  |
  v
Kafka: payment.completed
  |                         |
  v                         v
Order Service           Inventory Service
- mark paid             - reserve stock
- update SagaState      - save Reservation
                          - save outbox(inventory.reserved)
                          |
                          v
                  Kafka: inventory.reserved
                          |
                          v
                    Shipping Service
                    - create shipment
                    - save outbox(shipping.created)
                          |
                          v
                  Kafka: shipping.created
                          |
                          v
                    Order Service
                    - mark shipped
                    - mark completed
                    - save outbox(order.completed)
```

## 4. Failure Path dan Compensation Transaction

Dalam saga, failure bukan berarti rollback database lintas service. Failure berarti kita publish event pembatalan agar service lain menjalankan aksi kompensasi.

### Failure di Payment

Jika Payment Service gagal charge payment:

1. Payment Service menyimpan payment failed.
2. Payment Service menerbitkan `payment.failed`.
3. Order Service menerima `payment.failed`.
4. Order Service membatalkan order.
5. Order Service update `SagaState` menjadi compensating.
6. Order Service menerbitkan `order.cancelled`.

Karena payment belum sukses, kompensasi untuk payment biasanya tidak perlu refund. Namun Payment Service tetap consume `order.cancelled` secara idempotent. Jika menemukan payment yang sudah sukses, ia refund. Jika tidak ada, tidak melakukan apa-apa.

### Failure di Inventory

Jika inventory gagal reserve stock setelah payment sukses:

1. Inventory Service menerbitkan `inventory.failed`.
2. Order Service menerima `inventory.failed`.
3. Order Service cancel order.
4. Order Service menerbitkan `order.cancelled`.
5. Payment Service menerima `order.cancelled` dan melakukan refund.
6. Inventory Service menerima `order.cancelled`; jika ada reservation parsial, stock dilepas.
7. Shipping Service menerima `order.cancelled`; jika shipment sudah ada, shipment dibatalkan.

Ini inti compensation transaction: setiap service tahu cara membatalkan efek lokalnya sendiri.

### Failure di Shipping

Jika shipping gagal setelah payment dan inventory sukses:

1. Shipping Service menerbitkan `shipping.failed`.
2. Order Service menerima `shipping.failed`.
3. Order Service cancel order.
4. Order Service menerbitkan `order.cancelled`.
5. Payment Service refund payment.
6. Inventory Service release reservation.
7. Shipping Service cancel shipment jika record shipment sudah sempat dibuat.

Flow compensation bisa dilihat seperti ini:

```text
shipping.failed / inventory.failed / payment.failed
        |
        v
Order Service
  - cancel Order
  - SagaState -> COMPENSATING / FAILED
  - save outbox(order.cancelled)
        |
        v
Kafka: order.cancelled
   |              |                 |
   v              v                 v
Payment       Inventory          Shipping
refund        release stock      cancel shipment
```

Poin interview penting: compensation harus idempotent. Refund dua kali, release stock dua kali, atau cancel shipment dua kali bisa menjadi bug serius. Karena itu compensation handler juga memakai `processed_events`.

## 5. Transactional Outbox Pattern

Masalah utama event-driven system adalah dual write problem.

Contoh dual write yang berbahaya:

```text
save order to database
publish order.created to Kafka
```

Ada dua resource berbeda: database dan Kafka. Tanpa distributed transaction, kita bisa gagal di tengah:

- Database commit sukses, Kafka publish gagal: order ada, tapi saga tidak berjalan.
- Kafka publish sukses, database rollback: consumer menerima event untuk order yang tidak ada.

Transactional outbox menyelesaikan ini dengan menjadikan event sebagai data lokal dulu.

Dalam satu database transaction:

```text
save business data
save outbox message
commit
```

Setelah commit, background scheduler membaca tabel `outbox_messages` dan publish ke Kafka.

Di project ini setiap service punya `OutboxPublisher`:

1. Scheduler berjalan periodik.
2. Query pending outbox message.
3. Publish payload ke Kafka via `StreamBridge`.
4. Jika sukses, tandai message `PUBLISHED`.
5. Jika gagal, tandai `FAILED` atau retry sesuai status.

Karena outbox message disimpan satu transaksi dengan business data, sistem tidak kehilangan event ketika service crash setelah commit. Saat service hidup lagi, outbox publisher bisa melanjutkan publish message yang belum terkirim.

Trade-off-nya: event menjadi eventually consistent. Data order bisa sudah commit beberapa milidetik atau detik sebelum event benar-benar sampai ke Kafka. Untuk microservices, ini biasanya acceptable dan jauh lebih reliable daripada dual write langsung.

## 6. Idempotency

Idempotency berarti operasi aman dijalankan lebih dari sekali dengan hasil akhir yang sama.

Dalam sistem event-driven, idempotency bukan nice-to-have. Ini wajib, karena Kafka dan consumer biasanya menjamin at-least-once delivery, bukan exactly-once business processing.

Project ini punya dua level idempotency.

### API Idempotency

Saat client membuat order, client mengirim:

```http
Idempotency-Key: test-001
```

Order Service menyimpan key ini ke tabel idempotency. Jika request yang sama dikirim ulang karena timeout atau retry dari client, service tidak membuat order baru. Service mengembalikan order yang sudah dibuat untuk key tersebut.

Tanpa API idempotency, retry dari client bisa membuat duplicate order.

### Event Consumer Idempotency

Setiap event punya `eventId`. Consumer menyimpan event yang sudah diproses ke tabel `processed_events`.

Pola dasarnya:

```text
if eventId already exists:
    skip
else:
    process business logic
    mark eventId as processed
```

Di code, ini dilakukan oleh `ProcessedEventTracker`.

Kenapa penting?

- Kafka bisa mengirim event yang sama lagi setelah rebalance.
- Consumer bisa crash setelah business data commit tetapi sebelum offset commit.
- Outbox publisher bisa publish duplicate jika status update gagal setelah Kafka send.

Idempotency membuat duplicate delivery tidak berubah menjadi duplicate side effect.

## 7. Event Flow dan Ownership

Dalam choreography, event bukan command tersembunyi. Event adalah fakta yang sudah terjadi.

Contoh:

- `order.created`: order sudah dibuat.
- `payment.completed`: payment sudah sukses.
- `inventory.reserved`: stock sudah di-reserve.
- `shipping.created`: shipment sudah dibuat.
- `order.cancelled`: order sudah dibatalkan.

Service lain mendengar fakta itu dan memutuskan sendiri apa yang harus dilakukan.

Ini beda dengan command:

- `ReserveInventory`
- `ChargePayment`
- `CreateShipment`

Pada choreography murni, coupling antar service lebih longgar karena publisher tidak memanggil consumer tertentu. Namun konsekuensinya flow bisnis tersebar. Untuk membuatnya observable, project ini menyimpan `SagaState` di Order Service dan metrics saga.

Senior engineer harus bisa menjelaskan trade-off ini:

| Aspek | Choreography |
|---|---|
| Coupling | Lebih rendah, publisher hanya publish event |
| Flow visibility | Lebih sulit karena tersebar |
| Service autonomy | Tinggi |
| Debugging | Butuh tracing, correlation id, saga state, logs |
| Risiko | Event loop, hidden dependency, sulit melihat urutan bisnis jika tidak terdokumentasi |

## 8. Consistency Model

Sistem ini eventually consistent.

Saat order baru dibuat, status order belum langsung `COMPLETED`. Ia bergerak melalui beberapa state:

```text
CREATED -> PAYMENT_COMPLETED -> INVENTORY_RESERVED -> COMPLETED
```

Jika gagal:

```text
CREATED -> PAYMENT_COMPLETED -> INVENTORY_FAILED -> COMPENSATING -> CANCELLED
```

Yang penting bukan semua database berubah bersamaan. Yang penting setiap local transaction valid, setiap event tidak hilang, dan setiap kompensasi bisa dijalankan sampai sistem mencapai final state.

## 9. Cara Menjelaskan di Interview

Jika interviewer bertanya, "Bagaimana saga choreography ini bekerja?", jawaban ringkas senior-level bisa seperti ini:

> Kami tidak memakai distributed transaction lintas service. Setiap service punya database sendiri dan melakukan local transaction. Setelah local transaction sukses, service menyimpan event ke outbox dalam transaksi yang sama. Outbox publisher kemudian menerbitkan event ke Kafka. Service lain consume event tersebut, menjalankan local transaction berikutnya, dan menerbitkan event lanjutan. Jika ada failure, Order Service menerbitkan `order.cancelled`, lalu service yang sudah melakukan side effect menjalankan compensation transaction seperti refund payment, release inventory, atau cancel shipment. Karena Kafka bersifat at-least-once, semua consumer memakai idempotency berdasarkan `eventId`, dan API create order memakai `Idempotency-Key` agar retry client tidak membuat duplicate order.

Jika interviewer bertanya, "Kenapa perlu outbox?", jawab:

> Untuk menghindari dual write problem antara database dan Kafka. Business data dan event disimpan dalam satu transaksi database. Publish ke Kafka dilakukan asynchronous oleh outbox publisher. Jika service crash setelah commit, event tetap ada di tabel outbox dan bisa dipublish saat service hidup lagi.

Jika interviewer bertanya, "Apa kelemahan choreography?", jawab:

> Flow bisnis tersebar di beberapa consumer sehingga observability harus kuat. Kita butuh saga state, correlation id atau aggregate id, structured logs, tracing, metrics, dan dokumentasi event contract. Untuk flow yang sangat kompleks, orchestration bisa lebih mudah dikontrol. Tetapi choreography cocok ketika service autonomy penting dan flow masih bisa dikelola dengan event yang jelas.

Jika interviewer bertanya, "Bagaimana mencegah duplicate processing?", jawab:

> Setiap event punya `eventId`. Consumer mengecek tabel `processed_events`. Jika event sudah pernah diproses, consumer skip. Setelah business logic sukses, event ditandai processed. Untuk request HTTP create order, client mengirim `Idempotency-Key`, sehingga retry request mengembalikan order yang sama, bukan membuat order baru.

Jika interviewer bertanya, "Bagaimana rollback-nya?", jawab:

> Tidak ada rollback global. Saga memakai compensation. Setiap service bertanggung jawab membatalkan efek lokalnya sendiri. Payment melakukan refund, inventory release stock, shipping cancel shipment. Compensation juga harus idempotent karena event cancellation bisa diterima lebih dari sekali.

## 10. Mental Model Senior Engineer

Cara berpikir senior bukan hanya "event A memanggil service B". Senior melihat reliability boundary.

Hal yang harus selalu dicek:

- Apakah business data dan event disimpan atomically? Jika ya, gunakan outbox.
- Apakah consumer aman menerima duplicate event? Jika ya, gunakan processed event table.
- Apakah retry client aman? Jika ya, gunakan API idempotency key.
- Apakah failure punya kompensasi yang jelas? Refund, release, cancel.
- Apakah kompensasi idempotent? Jangan sampai refund atau release stock dua kali.
- Apakah event contract membawa data yang cukup untuk consumer?
- Apakah flow bisa diobservasi? Saga state, logs, metrics, tracing.
- Apakah ordering penting? Gunakan `aggregateId` sebagai partition key agar event per order cenderung berurutan di partition yang sama.
- Apakah final consistency bisa diterima oleh bisnis? Saga cocok untuk proses yang boleh eventually consistent.

## 11. End-to-End Summary

Satu order melewati lifecycle seperti ini:

1. Client create order dengan `Idempotency-Key`.
2. Order Service simpan order, saga state, dan outbox `order.created`.
3. Outbox publisher publish `order.created`.
4. Payment Service charge payment.
5. Payment Service publish `payment.completed` atau `payment.failed`.
6. Jika payment sukses, Inventory Service reserve stock.
7. Inventory Service publish `inventory.reserved` atau `inventory.failed`.
8. Jika inventory sukses, Shipping Service create shipment.
9. Shipping Service publish `shipping.created` atau `shipping.failed`.
10. Order Service mark order completed jika shipping sukses.
11. Jika ada failure di tengah, Order Service publish `order.cancelled`.
12. Payment, Inventory, dan Shipping menjalankan compensation masing-masing.

Itulah inti saga choreography: banyak local transaction kecil yang dihubungkan event, dilindungi outbox agar event tidak hilang, dibuat idempotent agar duplicate aman, dan dilengkapi compensation agar failure bisa dikembalikan ke state bisnis yang konsisten.
