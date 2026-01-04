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
- Saga pattern for managing distributed transactions
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
- **Saga Pattern** for managing distributed transactions (planned)
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
