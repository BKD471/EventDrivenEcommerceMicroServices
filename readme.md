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
   from committed records and retries until acknowledged.
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

The order API includes the saga status in `OrderResponse`. Stale `PENDING`/`CONFIRMING` orders are resumed from their
saved order lines; inventory and payment operations use order reference/order ID idempotency keys, and notification
consumers deduplicate events by order reference. Product reservation history is stored in the product database and
introduced with Flyway migration `V4__create_product_reservations.sql`.

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
