# Event-Driven Microservices Platform (WIP)

🚧 **Status: Under Active Development** 🚧

This repository contains a **Spring Boot–based event-driven microservices architecture** designed to demonstrate
scalable, loosely coupled services using asynchronous messaging.

The project is currently **under development**, and features, APIs, and configurations may change.

---

## 📌 Overview

This system follows **event-driven architecture (EDA)** principles where microservices communicate through events rather
than direct synchronous calls.

Key goals:

- Loose coupling, scalability and fault tolerance between services
- Asynchronous processing
- Clear separation of responsibilities
- Cloud-native design
- Support for multiple environments (dev, staging, prod)
- Integration with third-party services
- Support for multiple data formats (JSON, Avro, etc.)
- Avro Schema Evolution Support
- Rate limiting and throttling
- CI/CD pipelines for automated testing and deployment (Planned)
- Cloud deployment (Planned for AWS)
- Security best practices (OAuth2, JWT, encryption)
- Error handling and retries
- Database per service & Circuit breaker pattern
- Saga orchestration and compensating actions for order transactions
- Event sourcing and CQRS
- API versioning & documentation with Swagger/OpenAPI
- Unit and integration testing
- Centralized configuration management
- Service discovery and load balancing
- API gateway for routing and security
- Monitoring and logging (Prometheus, Grafana, Zipkin)

---

## 🧱 Architecture

- **Microservices** built with Spring Boot
- **Apache Kafka** used as the event broker
- **Avro** for schema evolution support
- **Spring Data JPA** for persistence
- **REST APIs** for external communication
- **Async event consumers & producers**
- **Centralized configuration** with Spring Cloud Config
- **Service discovery** with Spring Cloud Netflix Eureka
- **API Gateway** for routing requests
- **Monitoring & Logging** with Prometheus, Grafana, and Zipkin
- **Containerization** with Docker
- **CI/CD** pipelines (planned)
- **Unit & Integration Testing** with JUnit 5 and Mockito
- **Documentation** with Swagger/OpenAPI
- **Cloud Deployment** (AWS planned)
- **Security** with OAuth2/JWT
- **Error Handling & Retries** for robust event processing
- **Circuit Breaker Patterns** for resilience
- **Database per Service** pattern for data isolation
- **Saga Pattern** for managing distributed order transactions
- **Spring Batch** jobs for chunked, restartable saga recovery, compensation retries, payment outbox maintenance, product reservation cleanup nightly cross-service reconciliation, daily payment/sales reports and product catalog import/export with low-stock alerts and invoice backfill
- **Event Sourcing & CQRS** (planned)
- **API Versioning** for backward compatibility
- **Rate Limiting & Throttling** at the API Gateway
- **Health Checks & Alerts** for proactive monitoring

### High-Level Flow

1. Client sends request to a service
2. Service processes request and persists data
3. Domain event is published to Kafka
4. Other services consume the event asynchronously
5. Each service reacts independently

### Order Saga

Order creation is orchestrated by the **order service** across the product and payment services. It is a sequence of
local service transactions rather than a distributed database transaction:

1. Validate the customer and persist the order with saga status `PENDING`.
2. Reserve inventory in the product service. Reservations are keyed by order reference and lock product rows during
   the stock check/decrement.
3. Create an idempotent payment authorization keyed by order ID, then confirm it. Confirmation and a
   `payment_outbox_event` record commit in one transaction; a scheduled publisher sends the Kafka notification only
   from committed records and retries until acknowledged. Published events are purged and quarantined ones re-queued by the outbox maintenance batch jobs (see below).
4. Move the order to `CONFIRMING` (payment confirmed), publish the order confirmation and mark it `COMPLETED`.
   `CONFIRMING` is forward-only: publication failures are retried and never trigger compensation.
5. On a failure before `CONFIRMING`, cancel the payment and release the inventory reservation. Successful compensation
   marks the order `FAILED`; a failed compensation leaves it `COMPENSATION_REQUIRED` and the order service retries it.

Concurrency guarantees:

- Each saga run (creation, recovery, compensation retry) holds a persisted, expiring claim on the order
  (`claim_owner`, `claim_expires_at`); every state change is a conditional update that fails if the claim was lost or
  the order is no longer in the expected state, so replicas and slow requests cannot overwrite each other.
- Payment transitions lock the payment row. Cancelling an order with no payment yet stores a `CANCELLED` record that
  rejects a late authorization.
- Product reservations are serialized per order reference through `product_reservation_order` (migration
  `V5__create_product_reservation_orders.sql`). Releasing first leaves a `RELEASED` tombstone that rejects a late
  reservation, and reservation rows are locked before their status is checked.
- Payment confirmation is retried (idempotently) from the `CONFIRMING` status, so an uncertain confirmation outcome is
  reconciled instead of compensated. The order stores a customer snapshot for recovery, completed payments cannot be
  cancelled, unencodable amounts are rejected up front, and outbox events that keep failing are quarantined after 10
  attempts so they cannot block other notifications.
- Order caches are evicted after every committed saga change, including failed creations and scheduled recovery.

Saga recovery runs as two Spring Batch jobs in the order service (`compensationRetryJob` for `COMPENSATION_REQUIRED`
and `staleOrderRecoveryJob` for `PENDING`/`CONFIRMING`), launched by `SagaRecoveryScheduler` on the existing
`order.saga.compensation-retry-delay` / `order.saga.recovery-delay` intervals. Each job reads claimable order ids in
keyset-paged chunks (`order.saga.batch.chunk-size`, default 50), retries transient database errors
(`retry-limit`, default 3), skips orders whose recovery fails (`skip-limit`, default 100) and is restarted from its last
processed id when a run fails. Per-run counts and duration are logged and exposed as the Micrometer counter
`order.saga.batch.items` (tags `job`, `outcome`). Batch metadata tables are created automatically
(`spring.batch.jdbc.initialize-schema: always`); set `order.saga.batch.enabled=false` to disable the schedulers.

The order API includes the saga status in `OrderResponse`. Stale `PENDING`/`CONFIRMING` orders are resumed from their
saved order lines by the batch jobs above; inventory and payment operations use order reference/order ID idempotency keys, and notification
consumers deduplicate events by order reference. Product reservation history is stored in the product database and
introduced with Flyway migration `V4__create_product_reservations.sql`.

Payment outbox maintenance runs as two Spring Batch jobs in the payment service, launched by
`OutboxMaintenanceScheduler`: `outboxPurgeJob` deletes `SENT` events older than `payment.outbox.batch.retention`
(default `7d`) in chunks, and `outboxReprocessQuarantinedJob` returns `QUARANTINED` events to `PENDING` (with a fresh
retry budget) once their payload can be encoded again; events that are still unpublishable stay quarantined. Both are
keyset-paged and restartable, and share `payment.outbox.batch.chunk-size` (default 500), `retry-limit` (3) and
`skip-limit` (100). They run on `payment.outbox.purge-delay` / `payment.outbox.reprocess-delay` (default 1 hour), emit
the Micrometer counter `payment.outbox.batch.items` (tags `job`, `outcome`), and can be disabled with
`payment.outbox.batch.enabled=false`. Batch metadata tables are created automatically.

Cross-service reconciliation runs nightly (`order.reconciliation.cron`, default `0 0 2 * * *`) as two read-only Spring Batch
jobs in the order service, launched by `ReconciliationScheduler`. `orderPaymentReconciliationJob` flags `COMPLETED` orders
that have no payment or no `COMPLETED` payment. `paymentOrderReconciliationJob` pages the payment service's
`GET /api/v1/payments/reconciliation` endpoint and flags `AUTHORIZED` payments whose order is missing or `FAILED` (a leaked
authorization hold). Orders and payments younger than `order.reconciliation.batch.min-age` (default `1h`) are ignored so
in-flight sagas are not reported. Findings are never auto-repaired: each is logged at WARN and counted in
`order.reconciliation.findings{type}`, alongside per-run `order.reconciliation.batch.items` counters. A failed payment lookup
is retried and then skipped, never reported as a finding. Jobs are keyset-paged and restartable (`chunk-size` 100,
`retry-limit` 3, `skip-limit` 100), and an interrupted run is resumed by an hourly check
(`order.reconciliation.restart-delay`). Disable with `order.reconciliation.batch.enabled=false`. Order-to-reservation checks
are not included yet.

Daily reporting runs as two Spring Batch jobs that pre-aggregate `COMPLETED` records per day and payment method:
`dailyPaymentSummaryJob` (payment service, table `payment_daily_summary`) and `dailyOrderSalesJob` (order service, table
`order_daily_sales`). They run after midnight (`payment.report.cron` / `order.report.cron`, default `0 30 0 * * *`) and
regenerate the last `*.report.batch.lookback-days` complete days (default 3), so late confirmations are picked up; each day
is replaced in its own transaction, which makes reruns idempotent. A day is read from its `createdDate`. Jobs are
restartable (an interrupted run resumes via an hourly check, `*.report.restart-delay`), emit `*.report.batch.items`
counters and can be disabled with `*.report.batch.enabled=false`. Read the results with
`GET /api/v1/payments/reports/daily?from=&to=` and `GET /api/v1/orders/reports/daily?from=&to=` (ISO dates, inclusive).
The tables are created by Hibernate `ddl-auto: update`.

Product reservation cleanup runs as two Spring Batch jobs in the product service, launched by
`ReservationCleanupScheduler`. `reservationExpiryJob` examines `RESERVED` reservations older than
`product.reservation.batch.expiry-age` (default `1h`) and asks the order service (`GET
/api/v1/orders/reference/{reference}/saga-status`) for the order state. Because `RESERVED` is also the permanent state of
a completed order, stock is released only when the order is `FAILED` or unknown; in-flight and compensating orders are
left alone, `COMPLETED` orders are marked confirmed (`confirmed_at`) and never examined again, and a failed lookup never
releases anything. `reservationTombstonePurgeJob` deletes `RELEASED` tombstones (and their reservation rows) older than
`product.reservation.batch.tombstone-retention` (default `30d`); since a tombstone blocks late reservations for its order
reference, keep this longer than the longest time a saga can still be retried. Both jobs are keyset-paged and
restartable (`chunk-size` 100, `retry-limit` 3, `skip-limit` 100), run on `product.reservation.expiry-delay` (default
10 minutes) / `product.reservation.purge-delay` (default 1 day), emit the Micrometer counter
`product.reservation.batch.items` (tags `job`, `outcome`) and can be disabled with `product.reservation.batch.enabled=false`.
The order service URL comes from `application.config.order.url` (default `http://localhost:8222/api/v1/orders`). Migration
`V6__add_reservation_order_timestamps.sql` adds the `created_at`, `released_at` and `confirmed_at` columns.

Product catalog jobs run in the product service, launched by `CatalogScheduler`. `productExportJob` (cron
`product.catalog.export-cron`, default `0 0 3 * * *`) writes every product to a CSV (columns
`id,name,description,availableQuantity,price,categoryId,imageUrl`) and uploads it to S3 under `exports/`.
`productImportJob` is driven by polling the S3 prefix `imports/incoming/` (`product.catalog.import-delay`, default 15
minutes) rather than an HTTP endpoint: a blank `id` creates a product, an existing `id` updates name, description, price,
category and image, and invalid rows (unknown id or category, bad numbers) are skipped and logged. Stock of existing
products is never overwritten, because it is owned by purchases and reservations. Completed files move to
`imports/processed/`; files that still fail after `product.catalog.batch.max-import-attempts` (default 3) move to
`imports/failed/`, and interrupted runs resume where they stopped. `lowStockAlertJob` (cron
`product.catalog.low-stock-cron`, default `0 0 8 * * *`) logs a warning and increments `product.low_stock.alerts` for
products below `product.catalog.batch.low-stock-threshold` (default 10); there is no notification channel yet. All jobs
are keyset-paged and restartable (`chunk-size` 100, `retry-limit` 3, `skip-limit` 100), emit
`product.catalog.batch.items` (tags `job`, `outcome`) and can be disabled with `product.catalog.batch.enabled=false`.

Invoice backfill runs hourly (`payment.invoice.cron`) as `invoiceBackfillJob` in the payment service. Invoices are
generated by the notification service inside an async consumer that logs and drops failures, so a payment can end up
with no invoice and no email. The job checks `COMPLETED` payments created between `payment.invoice.batch.lookback`
(default `7d`) and `min-age` (default `15m`) ago, asks the notification service (`GET /api/v1/invoices/{orderReference}`)
whether the S3 object exists, and for missing ones calls `POST /api/v1/invoices/{orderReference}/regenerate`, which
rebuilds the PDF, uploads it and emails the customer. Invoices are now stored under the deterministic key
`invoices/{orderReference}.pdf` (previously a random UUID), so retries overwrite instead of duplicating. The notification
endpoints are internal (not routed by the gateway) and are disabled until the same secret is set in
`invoice.backfill.token` (notification) and `payment.invoice.batch.token` (payment), sent as `X-Internal-Token`; the
job does not run without it. The notification URL is `payment.invoice.batch.notification-url` (default
`http://localhost:8040/api/v1/invoices`). The job is chunked and restartable (`chunk-size` 50, `retry-limit` 3,
`skip-limit` 100) and emits `payment.invoice.batch.items` (tags `job`, `outcome`). The notification service's IAM role
needs `s3:GetObject` on the invoice bucket for the existence check.

The orchestration uses synchronous HTTP calls between services, while order/payment notifications remain Kafka events.
This provides compensating consistency, not atomic cross-service commits; downstream payment gateways and future
side effects must support equivalent idempotent authorization/cancellation semantics.

---

## 🧩 Modules (Planned / In Progress)

| Module          | Description                                                                   | Status         |
|-----------------|-------------------------------------------------------------------------------|----------------|
| `ecommerce-bom` | Dependency & version management                                               | ✅ Done         |
| `common`        | Shared DTOs, utilities, base classes                                          | ✅ Done         |
| `config-server` | service to pull configurations from into diffrent services                    | 🚧 In Progress |
| `discovery `    | service to register all services                                              | ✅ Done         |
| `api gateway`   | service to route all incoming request & load balance among different services | 🚧 In Progress |
| `order`         | Order creation & management                                                   | 🚧 In Progress |
| `customer`      | Customer creation & management                                                | 🚧 In Progress |
| `product`       | Product regsiter & fetch                                                      | 🚧 In Progress |
| `payment`       | Payment processing                                                            | 🚧 In Progress |
| `notification`  | Email / notification handling                                                 | 🚧 In Progress |

---

## 🛠️ Tech Stack

- **Java 21**
- **Spring Boot 3.x**
- **Apache Kafka**
- **Avro Schema Registry**
- **Spring Data JPA**
- **Spring Batch**
- **Aurora PostgreSQL**
- **AWS DynamoDB**
- **AWS Secrets Manager**
- **AWS S3**
- **Jasper PDF**
- **Grafana**
- **Prometheus**
- **ZipKin**
- **Promtail**
- **Loki**
- **JUnit 5 & Mockito**
- **REST APIs with Spring WebFlux**
- **Build Tool: Maven**
- **Maven (Multi-module project)**
- **Swagger**
- **Docker**
- **Saga Pattern**
- **OAuth2 / JWT**
- **Circuit Breaker Patterns**
- **Rate Limiting & Throttling**
- **Error Handling & Retries**
- **Database per Service**

---

## 📂 Project Structure

```text
root
├── ecommerce-bom
├── common
├── config-service
├── discovery-service
├── api-gateway
├── customer-service
├── product-service
├── order-service
├── payment-service
└── notification-service
