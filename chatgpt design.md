# Production-Style E-Commerce Microservices Backend — System Design

## 1. Overview

This document describes a production-style e-commerce backend built with:

- Java 17
- Spring Boot
- Maven
- PostgreSQL
- Redis
- Apache Kafka
- Netflix Eureka
- Spring Cloud Gateway
- OpenFeign
- Resilience4j
- JWT (JSON Web Token)
- Docker
- Docker Compose

The architecture contains 10 microservices:

1. API Gateway
2. Eureka Server
3. User Management Service
4. Product Service
5. Inventory Service
6. Cart Service
7. Order Service
8. Payment Service
9. Shipment Service
10. Notification Service

The design focuses on database-per-service, REST APIs, synchronous and asynchronous communication, Saga orchestration, Outbox Pattern, caching, idempotency, resilience, security, testing, and containerized local deployment.

---

# 2. High-Level Architecture

```text
                         +-----------------------+
                         |    Web / Mobile       |
                         |       Clients         |
                         +-----------+-----------+
                                     |
                                   HTTPS
                                     |
                                     v
                         +-----------------------+
                         |   Spring Cloud        |
                         |      Gateway          |
                         |                       |
                         |  JWT Validation       |
                         |  Routing              |
                         |  Rate Limiting        |
                         |  Correlation ID       |
                         +-----------+-----------+
                                     |
                                     |
                         +-----------v-----------+
                         |     Eureka Server     |
                         |   Service Discovery   |
                         +-----------+-----------+
                                     |
             +-----------------------+------------------------+
             |                       |                        |
             v                       v                        v
       +-----------+          +-----------+           +-----------+
       |   User    |          |  Product  |           |   Cart    |
       |  Service  |          |  Service  |           |  Service  |
       +-----+-----+          +-----+-----+           +-----+-----+
             |                      |                       |
             v                      v                       v
        PostgreSQL           PostgreSQL + Redis           Redis


                         +-----------------------+
                         |    Order Service      |
                         |   Saga Orchestrator   |
                         +-----------+-----------+
                                     |
                  +------------------+------------------+
                  |                  |                  |
                  v                  v                  v
          +-------------+     +-------------+    +-------------+
          |  Inventory  |     |   Payment   |    |  Shipment   |
          |   Service   |     |   Service   |    |   Service   |
          +------+------+     +------+------+    +------+------+
                 |                   |                  |
            PostgreSQL          PostgreSQL         PostgreSQL
                 |                   |                  |
                 +-------------------+------------------+
                                     |
                                     v
                               +-----------+
                               |   Kafka   |
                               +-----+-----+
                                     |
                         +-----------+-----------+
                         |                       |
                         v                       v
                 Notification Service     Other Consumers
```

## Core principle

Use:

```text
Immediate response required
        |
        +----> REST + OpenFeign

Business event / asynchronous workflow
        |
        +----> Kafka
```

---

# 3. Service Responsibilities

| Service | Responsibility | Storage |
|---|---|---|
| API Gateway | Authentication, routing, rate limiting, correlation IDs | Redis optional |
| Eureka Server | Service registration and discovery | None |
| User Management | Registration, login, users, roles | PostgreSQL |
| Product | Product/catalog/category management | PostgreSQL + Redis |
| Inventory | Stock, reservation, release, confirmation | PostgreSQL |
| Cart | Shopping cart management | Redis |
| Order | Orders and Saga orchestration | PostgreSQL |
| Payment | Payment processing and provider integration | PostgreSQL |
| Shipment | Shipment creation and tracking | PostgreSQL |
| Notification | Email/SMS/push notifications | PostgreSQL optional |

Each service owns its own database.

A service must never directly access another service's database.

Correct:

```text
Order Service
      |
      +---- REST/Feign ----> Product Service
                                  |
                                  +----> Product DB
```

Incorrect:

```text
Order Service
      |
      +--------------------> Product DB
```

---

# 4. Repository Structure

Recommended monorepo:

```text
ecommerce-microservices/
|
+-- api-gateway/
+-- discovery-server/
|
+-- user-service/
+-- product-service/
+-- inventory-service/
+-- cart-service/
+-- order-service/
+-- payment-service/
+-- shipment-service/
+-- notification-service/
|
+-- docker/
|
+-- docker-compose.yml
+-- pom.xml
+-- README.md
```

Each business service can follow:

```text
src/main/java/com/ecommerce/order/
|
+-- controller/
+-- service/
+-- repository/
+-- entity/
+-- dto/
|   +-- request/
|   +-- response/
|
+-- mapper/
+-- client/
+-- event/
|   +-- producer/
|   +-- consumer/
|   +-- model/
|
+-- saga/
+-- outbox/
+-- exception/
+-- security/
+-- config/
+-- util/
```

---

# 5. API Gateway

The Gateway is the single public entry point.

Example routes:

```text
/api/v1/users/**       -> USER-SERVICE
/api/v1/products/**    -> PRODUCT-SERVICE
/api/v1/inventory/**   -> INVENTORY-SERVICE
/api/v1/cart/**        -> CART-SERVICE
/api/v1/orders/**      -> ORDER-SERVICE
/api/v1/payments/**    -> PAYMENT-SERVICE
/api/v1/shipments/**   -> SHIPMENT-SERVICE
```

Example configuration:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: product-service
          uri: lb://PRODUCT-SERVICE
          predicates:
            - Path=/api/v1/products/**

        - id: order-service
          uri: lb://ORDER-SERVICE
          predicates:
            - Path=/api/v1/orders/**

        - id: cart-service
          uri: lb://CART-SERVICE
          predicates:
            - Path=/api/v1/cart/**
```

Responsibilities:

- Request routing
- JWT validation
- Correlation ID generation
- Rate limiting
- CORS configuration
- Request/response logging
- Basic security enforcement

---

# 6. Eureka Server

Eureka provides service discovery.

```text
                  Eureka Server
                       |
       +---------------+---------------+
       |               |               |
       v               v               v
 Product Service   Order Service   Payment Service
```

Services register themselves with Eureka.

Order Service can discover Product Service without hardcoding:

```text
http://product-service:8082
```

Instead:

```text
PRODUCT-SERVICE
```

This allows multiple instances:

```text
PRODUCT-SERVICE
       |
       +---- Instance 1
       +---- Instance 2
       +---- Instance 3
```

---

# 7. JWT Authentication

Authentication is handled by User Management Service.

## Login flow

```text
Client
  |
  | POST /api/v1/auth/login
  v
Gateway
  |
  v
User Service
  |
  +-- Validate username/password
  |
  +-- Generate JWT
  |
  v
Client
```

Subsequent requests:

```text
Authorization: Bearer <JWT>
```

Example JWT claims:

```json
{
  "sub": "user@example.com",
  "userId": "USR-100001",
  "roles": [
    "CUSTOMER"
  ]
}
```

Possible roles:

```text
CUSTOMER
ADMIN
SUPPORT
```

---

# 8. User Management Service

Responsibilities:

- User registration
- Login
- Password validation
- Role management
- User profile
- Address management
- JWT generation

APIs:

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login

GET    /api/v1/users/{id}
PUT    /api/v1/users/{id}

GET    /api/v1/users/{id}/addresses
POST   /api/v1/users/{id}/addresses
```

Database:

```text
USERS
-----------------------
id
email
password_hash
first_name
last_name
status
created_at
updated_at

USER_ROLES
-----------------------
user_id
role
```

Never store plaintext passwords.

Use a strong password hashing algorithm such as BCrypt.

---

# 9. Product Service

Responsibilities:

- Product management
- Category management
- Product search
- Product details
- Pricing
- Product status

APIs:

```text
POST   /api/v1/products
GET    /api/v1/products/{id}
GET    /api/v1/products
PUT    /api/v1/products/{id}
DELETE /api/v1/products/{id}
```

Search example:

```text
GET /api/v1/products?category=mobile&page=0&size=20
```

Database:

```text
PRODUCT
-----------------------
id
sku
name
description
category_id
price
status
created_at
updated_at
```

---

# 10. Redis Product Caching

Product information is read frequently.

Flow:

```text
GET Product
     |
     v
   Redis
     |
     +---- HIT ----> Return product
     |
     +---- MISS
             |
             v
        PostgreSQL
             |
             v
       Store in Redis
             |
             v
           Return
```

Redis key:

```text
product:{productId}
```

Example:

```text
product:P100
```

Use a TTL such as:

```text
10-30 minutes
```

When a product changes:

```text
Update PostgreSQL
       |
       v
Evict Redis key
```

---

# 11. Inventory Service

Inventory is responsible for stock consistency.

Database:

```text
INVENTORY
-----------------------
id
product_id
available_quantity
reserved_quantity
version
updated_at
```

Use optimistic locking:

```java
@Version
private Long version;
```

APIs:

```text
GET  /api/v1/inventory/{productId}

POST /api/v1/inventory/reserve
POST /api/v1/inventory/release
POST /api/v1/inventory/confirm
```

Reservation request:

```json
{
  "orderId": "ORD-1001",
  "items": [
    {
      "productId": "P100",
      "quantity": 2
    }
  ]
}
```

Optimistic locking prevents concurrent requests from overselling inventory.

---

# 12. Cart Service

Redis is the primary storage for active carts.

Example key:

```text
cart:user:1001
```

Example data:

```json
{
  "userId": "1001",
  "items": [
    {
      "productId": "P100",
      "quantity": 2
    }
  ]
}
```

APIs:

```text
POST   /api/v1/cart/items
GET    /api/v1/cart
PUT    /api/v1/cart/items/{productId}
DELETE /api/v1/cart/items/{productId}
DELETE /api/v1/cart
```

Possible cart TTL:

```text
7 days
```

---

# 13. Order Service

The Order Service is the central business service for order processing.

Responsibilities:

- Create order
- Validate order
- Maintain order state
- Start Saga
- Coordinate compensation
- Store order history
- Publish order events
- Handle idempotency

API:

```text
POST /api/v1/orders
GET  /api/v1/orders/{orderId}
GET  /api/v1/orders
POST /api/v1/orders/{orderId}/cancel
```

Example request:

```json
{
  "items": [
    {
      "productId": "P100",
      "quantity": 2
    }
  ],
  "shippingAddressId": "ADDR100",
  "paymentMethodId": "PAYMETHOD100"
}
```

---

# 14. Order Database

```text
ORDERS
-----------------------
id
order_number
user_id
total_amount
status
created_at
updated_at
version

ORDER_ITEM
-----------------------
id
order_id
product_id
product_name
price
quantity

SAGA_INSTANCE
-----------------------
id
order_id
current_step
status
created_at

OUTBOX_EVENT
-----------------------
id
aggregate_id
event_type
payload
status
created_at
published_at

PROCESSED_EVENT
-----------------------
event_id
consumer_name
processed_at

IDEMPOTENCY
-----------------------
idempotency_key
request_hash
response
status
created_at
```

## Important design decision

Store product name and price as a snapshot in `ORDER_ITEM`.

Do not depend on Product Service for historical order prices.

For example:

```text
Purchase time:
iPhone = ₹70,000

Later:
iPhone = ₹60,000
```

The historical order must continue to show:

```text
₹70,000
```

---

# 15. Order State Machine

Recommended states:

```text
CREATED
   |
   v
INVENTORY_RESERVED
   |
   v
PAYMENT_PROCESSING
   |
   +---- FAILED ----> CANCELLED
   |
 SUCCESS
   |
   v
PAID
   |
   v
SHIPMENT_CREATED
   |
   v
SHIPPED
   |
   v
DELIVERED
```

Avoid arbitrary transitions such as:

```text
CREATED -> DELIVERED
```

State transitions should be validated.

---

# 16. Saga Pattern

Use Saga Orchestration.

The Order Service acts as the Saga Orchestrator.

```text
                  ORDER SERVICE
                 Saga Orchestrator
                        |
                        v
                Reserve Inventory
                        |
                     SUCCESS
                        |
                        v
                 Process Payment
                        |
                     SUCCESS
                        |
                        v
                 Create Shipment
                        |
                     SUCCESS
                        |
                        v
                 Order Confirmed
```

Saga steps:

```text
1. Create Order
2. Reserve Inventory
3. Process Payment
4. Create Shipment
5. Confirm Order
```

---

# 17. Saga Compensation

If payment fails:

```text
Create Order
     |
     v
Reserve Inventory
     |
     v
Payment
     |
     X
Payment Failed
     |
     v
Release Inventory
     |
     v
Cancel Order
```

Compensating action:

```text
Reserve inventory
       |
       X
Payment failed
       |
       v
Release inventory
```

This provides eventual consistency across services.

---

# 18. Kafka Architecture

Use domain-oriented topics.

Recommended topics:

```text
order-events
payment-events
inventory-events
shipment-events
notification-events
```

Example event:

```json
{
  "eventId": "8f86291b",
  "eventType": "ORDER_CREATED",
  "aggregateId": "ORD-10001",
  "timestamp": "2026-09-05T10:20:00Z",
  "version": 1,
  "payload": {
    "userId": "USR100",
    "totalAmount": 45000
  }
}
```

Use:

```text
orderId
```

as the Kafka message key.

This keeps events for the same order in the same partition and preserves ordering within that partition.

---

# 19. Outbox Pattern

Do not do:

```java
repository.save(order);
kafkaTemplate.send(event);
```

because this can produce:

```text
Database save  -> SUCCESS
Kafka publish  -> FAILURE
```

The database and Kafka event are now inconsistent.

Instead:

```text
BEGIN TRANSACTION

INSERT INTO orders

INSERT INTO outbox_events

COMMIT
```

The transaction guarantees both records are persisted together.

Then:

```text
Outbox Publisher
      |
      v
Read NEW events
      |
      v
Publish to Kafka
      |
      v
Mark event PUBLISHED
```

Query example:

```sql
SELECT *
FROM outbox_events
WHERE status = 'NEW';
```

A future production enhancement can use Change Data Capture (CDC) such as Debezium.

---

# 20. Idempotency

REST APIs such as order creation should support idempotency.

Example:

```text
POST /api/v1/orders
Idempotency-Key: abc123
```

Store the key:

```text
IDEMPOTENCY
-----------------------
idempotency_key
request_hash
response
status
created_at
```

If the same request is retried:

```text
Request
  |
  v
Idempotency key exists?
  |
  +---- YES ----> Return previous response
  |
  +---- NO -----> Process request
```

This prevents duplicate orders caused by:

- Double-clicks
- Network retries
- Client retries
- Gateway retries
- Request timeouts

---

# 21. Kafka Consumer Idempotency

Kafka consumers should also be idempotent.

Every event has:

```text
eventId
```

Store processed events:

```text
PROCESSED_EVENT
-----------------------
event_id
consumer_name
processed_at
```

Processing:

```text
Receive Event
     |
     v
Already processed?
     |
     +---- YES ----> Ignore
     |
     +---- NO
            |
            v
        Process Event
            |
            v
       Save eventId
```

This prevents duplicate processing.

---

# 22. OpenFeign

Use OpenFeign for synchronous service-to-service communication.

Example:

```java
@FeignClient(name = "PRODUCT-SERVICE")
public interface ProductClient {

    @GetMapping("/internal/products/{id}")
    ProductResponse getProduct(@PathVariable Long id);
}
```

Example:

```text
Order Service
      |
      +---- Feign ----> Product Service
```

Use internal APIs:

```text
/internal/products/{id}
```

and public APIs:

```text
/api/v1/products/{id}
```

---

# 23. Resilience4j

Protect synchronous remote calls using:

- Circuit Breaker
- Retry
- Timeout
- Bulkhead

Example:

```java
@CircuitBreaker(
    name = "productService",
    fallbackMethod = "productFallback"
)
```

Recommended starting values:

```text
Retry attempts: 2-3
Timeout: 1-3 seconds
Circuit breaker: configured based on observed failure rate
```

Do not blindly retry payment operations.

Payment APIs should use provider-supported idempotency keys.

---

# 24. Payment Service

Flow:

```text
Order Service
      |
      v
Payment Service
      |
      v
External Payment Provider
```

Payment states:

```text
PENDING
PROCESSING
SUCCESS
FAILED
REFUNDED
```

Database:

```text
PAYMENT
-----------------------
id
order_id
user_id
amount
currency
status
provider_reference
idempotency_key
created_at
updated_at
```

Never store raw:

```text
Credit card number
CVV
```

Use a payment-provider token/reference instead.

---

# 25. Shipment Service

Shipment is normally created after successful payment.

```text
PaymentCompletedEvent
        |
        v
Shipment Service
        |
        v
Create Shipment
```

Shipment states:

```text
CREATED
PACKED
SHIPPED
IN_TRANSIT
DELIVERED
FAILED
CANCELLED
```

Database:

```text
SHIPMENT
-----------------------
shipment_id
order_id
carrier
tracking_number
status
shipping_address
created_at
updated_at
```

---

# 26. Notification Service

Notification should be asynchronous.

Avoid:

```text
Order Service
      |
      | HTTP
      v
Notification Service
```

Prefer:

```text
OrderConfirmedEvent
        |
        v
      Kafka
        |
        v
Notification Service
        |
        +---- Email
        +---- SMS
        +---- Push
```

Notification failure should not cause order creation to fail.

---

# 27. Complete Order Flow

```text
CUSTOMER
   |
   | JWT
   v
API GATEWAY
   |
   | Eureka
   v
ORDER SERVICE
   |
   +---- Feign ----> PRODUCT SERVICE
   |
   +---- Save Order
   |
   +---- Save Outbox Event
                |
                v
              Kafka
                |
                v
        INVENTORY SERVICE
                |
          Reserve Stock
                |
                v
              Kafka
                |
                v
          ORDER SERVICE
                |
                v
          PAYMENT SERVICE
                |
          Payment Provider
                |
                v
              Kafka
                |
                v
          ORDER SERVICE
                |
                v
          SHIPMENT SERVICE
                |
                v
              Kafka
                |
        +-------+-------+
        |               |
        v               v
   ORDER SERVICE   NOTIFICATION
        |           Email/SMS
        v
    CONFIRMED
```

---

# 28. Communication Matrix

| Communication | Technology | Reason |
|---|---|---|
| Client -> Gateway | HTTPS/REST | Public API |
| Gateway -> Services | HTTP | Routing |
| Order -> Product | OpenFeign | Immediate validation |
| Cart -> Product | OpenFeign | Product information |
| Order -> User | OpenFeign | Immediate user/address validation |
| Order -> Inventory | Kafka/Saga | Distributed workflow |
| Order -> Payment | Kafka/Saga | Distributed workflow |
| Payment -> Order | Kafka | Event notification |
| Order -> Shipment | Kafka/Saga | Asynchronous workflow |
| Shipment -> Notification | Kafka | Non-critical async work |
| Order -> Notification | Kafka | Non-critical async work |

---

# 29. Database Architecture

Logical databases:

```text
PostgreSQL
|
+-- user_db
+-- product_db
+-- inventory_db
+-- order_db
+-- payment_db
+-- shipment_db
+-- notification_db
```

Cart:

```text
Redis
```

Important rule:

```text
One service owns its data.
Other services access it through APIs/events.
```

---

# 30. Global Exception Handling

Every service should have:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
}
```

Standard response:

```json
{
  "timestamp": "2026-09-05T10:15:32Z",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Product not found",
  "path": "/api/v1/products/100",
  "correlationId": "71fa82ab"
}
```

Common exceptions:

```text
ResourceNotFoundException
ValidationException
InsufficientInventoryException
PaymentFailedException
UnauthorizedException
DuplicateRequestException
ServiceUnavailableException
```

---

# 31. DTO Architecture

Do not expose JPA entities directly from REST APIs.

Use:

```text
Request JSON
     |
     v
Request DTO
     |
     v
Service
     |
     v
Entity
     |
     v
Repository
```

Response:

```text
Repository
     |
     v
Entity
     |
     v
Mapper
     |
     v
Response DTO
     |
     v
Controller
```

MapStruct can be used for mapping.

---

# 32. Correlation ID

Generate a correlation ID at the Gateway.

Example:

```text
X-Correlation-ID: 3cdadae2-5901-46df
```

Propagate it through:

```text
Gateway
   |
   v
Order
   |
   v
Inventory
   |
   v
Payment
   |
   v
Kafka
```

Include the ID in logs.

This makes distributed debugging much easier.

---

# 33. Logging and Observability

Use structured logs:

```json
{
  "service": "order-service",
  "level": "INFO",
  "correlationId": "abc123",
  "orderId": "ORD100",
  "message": "Order created"
}
```

Recommended observability stack:

```text
Spring Boot Actuator
        |
        v
   Prometheus
        |
        v
     Grafana
```

Distributed tracing:

```text
Microservices
      |
      v
OpenTelemetry
      |
      v
Tracing Backend
```

Logging can later use:

```text
Elasticsearch
      |
      v
    Kibana
```

---

# 34. Docker Compose

Local development environment:

```text
docker-compose.yml

Services:
  postgres
  redis
  kafka

  eureka-server
  api-gateway

  user-service
  product-service
  inventory-service
  cart-service
  order-service
  payment-service
  shipment-service
  notification-service
```

Architecture:

```text
                    Docker Network

+------------------------------------------------+
|                                                |
| postgres       redis          kafka             |
|                                                |
| eureka-server                                   |
|                                                |
| api-gateway                                     |
|                                                |
| user-service                                    |
| product-service                                 |
| inventory-service                               |
| cart-service                                    |
| order-service                                   |
| payment-service                                 |
| shipment-service                                |
| notification-service                            |
|                                                |
+------------------------------------------------+
```

---

# 35. Testing Strategy

## Unit Tests

Use:

```text
JUnit 5
Mockito
AssertJ
```

Example:

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderService orderService;
}
```

Test:

- Business logic
- Validation
- Exception handling
- State transitions
- Saga logic
- Idempotency
- Mappers

## Integration Tests

Use:

```text
@SpringBootTest
Testcontainers
```

Run real containers for:

```text
PostgreSQL
Kafka
Redis
```

This is preferable to relying only on H2 because PostgreSQL behavior can differ from H2.

---

# 36. API Versioning

Use:

```text
/api/v1/products
/api/v1/orders
/api/v1/cart
/api/v1/users
```

Instead of:

```text
/products
/orders
```

This allows future versions:

```text
/api/v2/orders
```

without immediately breaking existing clients.

---

# 37. Kafka Retry and Dead Letter Topic

Failed Kafka processing should not block the entire consumer.

Recommended flow:

```text
Main Topic
    |
    v
Consumer
    |
    X
Processing Failed
    |
    v
Retry
    |
    X
Retries Exhausted
    |
    v
Dead Letter Topic
```

Example:

```text
payment-events
payment-events.DLT
```

Dead Letter Topic messages can be inspected and replayed after fixing the underlying problem.

---

# 38. Production Concerns

Additional technologies/practices worth adding:

| Concern | Solution |
|---|---|
| DB migrations | Flyway |
| API documentation | OpenAPI / Swagger |
| Distributed tracing | OpenTelemetry |
| Metrics | Prometheus |
| Dashboard | Grafana |
| Health checks | Spring Boot Actuator |
| Kafka failures | Retry + Dead Letter Topic |
| Correlation | Correlation ID |
| Secrets | Environment variables / Secret Manager |
| API versioning | `/api/v1` |
| Concurrency | Optimistic locking |
| Validation | Jakarta Validation |
| Mapping | MapStruct |
| Integration tests | Testcontainers |
| Caching | Redis |

---

# 39. Service Layer Design

Keep controllers thin.

Bad:

```text
Controller
   |
   v
Repository
```

Preferred:

```text
Controller
   |
   v
Service
   |
   v
Repository
   |
   v
Database
```

For external services:

```text
Service
   |
   v
Feign Client
   |
   v
Remote Service
```

Business logic should live in the service layer.

---

# 40. Final Architecture

```text
                         INTERNET
                            |
                            v
                 +--------------------+
                 |  Spring Cloud      |
                 |  API Gateway       |
                 |                    |
                 |  JWT               |
                 |  Rate Limit        |
                 |  Correlation ID    |
                 +---------+----------+
                           |
                     Eureka Discovery
                           |
        +------------------+------------------+
        |                  |                  |
        v                  v                  v
      USER              PRODUCT              CART
   PostgreSQL       PostgreSQL+Redis          Redis
                           |
                           |
                           v
                    +-------------+
                    |    ORDER    |
                    |   SERVICE   |
                    |    Saga     |
                    | Orchestrator|
                    +------+------+
                           |
              +------------+------------+
              |            |            |
              v            v            v
         INVENTORY      PAYMENT      SHIPMENT
         PostgreSQL    PostgreSQL    PostgreSQL
              |            |            |
              +------------+------------+
                           |
                          Kafka
                           |
                +----------+----------+
                |                     |
                v                     v
        NOTIFICATION            Other Consumers
```

---

# 41. Recommended Implementation Order

Build the system incrementally.

### Phase 1 — Foundation

```text
1. Parent Maven project
2. Eureka Server
3. API Gateway
4. Docker Compose
5. PostgreSQL
6. Redis
7. Kafka
```

### Phase 2 — Core Services

```text
8. User Service
9. Product Service
10. Inventory Service
11. Cart Service
```

### Phase 3 — Order Processing

```text
12. Order Service
13. OpenFeign
14. Kafka events
15. Outbox Pattern
16. Idempotency
17. Saga Orchestration
```

### Phase 4 — Payment and Fulfillment

```text
18. Payment Service
19. Shipment Service
20. Notification Service
```

### Phase 5 — Resilience

```text
21. Resilience4j
22. Retry
23. Circuit Breaker
24. Timeout
25. Bulkhead
26. Kafka Retry/DLT
```

### Phase 6 — Security

```text
27. JWT
28. Spring Security
29. Role-based authorization
30. Gateway security
```

### Phase 7 — Testing

```text
31. Unit tests
32. Integration tests
33. Testcontainers
34. API tests
35. Kafka integration tests
```

### Phase 8 — Observability

```text
36. Actuator
37. Correlation ID
38. OpenTelemetry
39. Prometheus
40. Grafana
41. Centralized logging
```

---

# 42. Final Technology Mapping

| Requirement | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot |
| Build | Maven |
| Database | PostgreSQL |
| Cache | Redis |
| Messaging | Apache Kafka |
| Discovery | Netflix Eureka |
| Gateway | Spring Cloud Gateway |
| Synchronous communication | OpenFeign |
| Resilience | Resilience4j |
| Authentication | JWT + Spring Security |
| Persistence | Spring Data JPA |
| DB migration | Flyway |
| Validation | Jakarta Validation |
| Mapping | MapStruct |
| Unit testing | JUnit 5 + Mockito |
| Integration testing | Testcontainers |
| API documentation | OpenAPI / Swagger |
| Containerization | Docker |
| Local orchestration | Docker Compose |
| Metrics | Actuator + Prometheus |
| Dashboard | Grafana |
| Distributed tracing | OpenTelemetry |

---

# 43. Key Design Principles

1. Database per service.
2. No direct cross-service database access.
3. REST/OpenFeign for synchronous communication.
4. Kafka for asynchronous events.
5. Saga orchestration for distributed transactions.
6. Outbox Pattern for reliable event publishing.
7. Idempotency for REST requests and Kafka consumers.
8. Redis for high-read data and active carts.
9. Optimistic locking for inventory.
10. Resilience4j for remote-call resilience.
11. JWT for authentication and authorization.
12. Global exception handling.
13. DTOs instead of exposing entities.
14. Correlation IDs for distributed debugging.
15. Kafka Retry + Dead Letter Topics for failures.
16. Testcontainers for production-like integration tests.
17. Docker Compose for local deployment.
18. Flyway for database versioning.
19. Observability through Actuator, metrics, logs and tracing.
20. API versioning through `/api/v1`.

---

# 44. Expected End-to-End Behavior

A successful order:

```text
Client
  |
  v
Gateway
  |
  v
Order Service
  |
  +--> Validate Product
  |
  +--> Create Order
  |
  +--> Outbox Event
          |
          v
        Kafka
          |
          v
     Reserve Inventory
          |
          v
     Process Payment
          |
          v
     Create Shipment
          |
          v
     Confirm Order
          |
          v
     Publish Event
          |
          +----> Notification Service
                    |
                    +----> Email/SMS/Push
```

A failed payment:

```text
Order Created
     |
     v
Inventory Reserved
     |
     v
Payment Failed
     |
     v
Release Inventory
     |
     v
Order Cancelled
     |
     v
Notification
```

This architecture provides a strong production-style foundation for implementing the complete e-commerce microservices backend and for demonstrating senior-level Java/Spring Boot microservices design concepts.
