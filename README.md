# E-Commerce Microservices Backend — System Design

**Stack:** Java 17 · Spring Boot 3 · Maven · PostgreSQL · Redis · Kafka · Eureka · Spring Cloud Gateway · OpenFeign · Resilience4j · JWT · Docker

---

## 1. High-Level Architecture

```
                                   ┌─────────────────────┐
                                   │   Eureka Server      │
                                   │   (Service Registry) │
                                   └──────────▲───────────┘
                                              │ register/discover
                     ┌────────────────────────┼─────────────────────────┐
                     │                        │                         │
   Client (Web/App)  │                        │                         │
        │            │                        │                         │
        ▼            │                        │                         │
 ┌───────────────┐   │                        │                         │
 │ API Gateway    │◄──┘  JWT filter, routing, rate-limit, CB fallback     │
 │ (Spring Cloud  │──────────────────────────────────────────────────────┘
 │  Gateway)      │
 └───────┬────────┘
         │ lb://SERVICE-NAME  (HTTP)
 ┌───────┼──────────────────────────────────────────────────────────────────┐
 │       ▼            ▼            ▼            ▼            ▼              │
 │  ┌─────────┐  ┌──────────┐ ┌──────────┐ ┌─────────┐ ┌───────────┐         │
 │  │  User   │  │ Product  │ │Inventory │ │  Cart   │ │  Order    │  ...    │
 │  │ Service │  │ Service  │ │ Service  │ │ Service │ │ Service   │         │
 │  └────┬────┘  └────┬─────┘ └────┬─────┘ └────┬────┘ └─────┬─────┘         │
 │       │DB          │DB          │DB          │Redis+DB    │DB             │
 │  ┌────▼───┐   ┌────▼───┐   ┌────▼────┐  ┌────▼────┐  ┌────▼────┐         │
 │  │user_db │   │product │   │inventory│  │cart_db  │  │order_db │         │
 │  │(PG)    │   │_db(PG) │   │_db(PG)  │  │(Redis)  │  │(PG)     │         │
 │  └────────┘   └────────┘   └─────────┘  └─────────┘  └─────────┘         │
 │                                                                             │
 │  ┌───────────┐ ┌───────────┐ ┌──────────────┐                             │
 │  │ Payment   │ │ Shipment  │ │ Notification │                             │
 │  │ Service   │ │ Service   │ │ Service      │                             │
 │  └─────┬─────┘ └─────┬─────┘ └──────┬───────┘                             │
 │        │DB           │DB            │DB(optional)                        │
 │  ┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼──────┐                             │
 │  │payment_db │  │shipment_db│  │notif_db    │                             │
 │  └───────────┘  └───────────┘  └────────────┘                             │
 └──────────────────────────────────────────────────────────────────────────┘
                     │                    ▲
                     ▼                    │
              ┌───────────────────────────────────┐
              │        Kafka (Event Backbone)       │
              │  order.*, inventory.*, payment.*,   │
              │  shipment.*, user.*, notification.* │
              └───────────────────────────────────┘
```

**Sync (OpenFeign, service→service, over Eureka via Gateway or direct LB client):**
Cart→Product, Cart→Inventory, Order→User (address/profile lookup).
Every Feign client is wrapped with a Resilience4j CircuitBreaker + Retry + TimeLimiter + fallback.

**Async (Kafka):**
All cross-service state propagation for the order lifecycle, user registration, and notifications — this is what keeps services decoupled and lets the Order Saga run without blocking calls.

---

## 2. Service Catalog

| # | Service | Owns DB | Core Responsibility | Exposes REST | Talks Sync (Feign) | Talks Async (Kafka) |
|---|---|---|---|---|---|---|
| 1 | Eureka Server | – | Service registry/discovery | – | – | – |
| 2 | API Gateway | – | Routing, JWT auth, rate limiting, CB fallback | – | – | – |
| 3 | User Service | `user_db` (PG) | Auth, registration, profile, address, JWT issuance | ✅ | – | Publishes `user.registered` |
| 4 | Product Service | `product_db` (PG) | Catalog, category, pricing | ✅ | – | Publishes `product.created/updated` |
| 5 | Inventory Service | `inventory_db` (PG) | Stock levels, reservation, release | ✅ | – | Consumes order commands, publishes `inventory.*` |
| 6 | Cart Service | `cart_db` (Redis, primary) | Active cart, checkout trigger | ✅ | Product, Inventory | – |
| 7 | Order Service | `order_db` (PG) | **Saga orchestrator**, order lifecycle | ✅ | User (optional) | Publishes commands, consumes replies |
| 8 | Payment Service | `payment_db` (PG) | Charge, refund, idempotent transactions | ✅ (internal) | – | Consumes `payment.requested`, publishes `payment.*` |
| 9 | Shipment Service | `shipment_db` (PG) | Shipment creation, tracking | ✅ | – | Consumes `shipment.requested`, publishes `shipment.*` |
| 10 | Notification Service | `notification_db` (PG, log only) | Email/SMS on events | – (mostly) | – | Consumes almost every topic |

Each PostgreSQL DB is a **separate schema/instance** — no service ever queries another service's tables directly. Cross-service reads go through Feign (sync, need-it-now) or are denormalized locally via Kafka event consumption (async, eventually-consistent local copy).

---

## 3. Data Ownership (key entities per service)

- **User**: `User(id, email, passwordHash, roles)`, `Address(id, userId, line1, city, zip, isDefault)`, `RefreshToken(id, userId, tokenHash, expiresAt)`
- **Product**: `Product(id, name, description, price, categoryId, active)`, `Category(id, name, parentId)`
- **Inventory**: `StockItem(productId, availableQty, reservedQty, version)`, `StockReservation(id, orderId, productId, qty, status)`, `OutboxEvent(...)`
- **Cart**: Redis hash `cart:{userId}` → `{productId: qty}` with TTL (e.g., 7 days); optional `cart_snapshot` row in Postgres written at checkout for audit/history
- **Order**: `Order(id, userId, status, totalAmount, idempotencyKey)`, `OrderItem(orderId, productId, qty, price)`, `OrderStatusHistory(orderId, status, timestamp)`, `OutboxEvent(...)`
- **Payment**: `Payment(id, orderId UNIQUE, amount, status, gatewayRef)`, `Transaction(...)`, `ProcessedEvent(eventId UNIQUE)`, `OutboxEvent(...)`
- **Shipment**: `Shipment(id, orderId, status, carrier, trackingNumber)`, `ShipmentEvent(...)`, `OutboxEvent(...)`
- **Notification**: `NotificationLog(id, type, recipient, payload, status)`

`version` columns are used for optimistic locking (`@Version`) on Inventory stock rows to guard against concurrent reservation races.

---

## 4. Order Processing Saga (Orchestration-based, over Kafka)

**Why orchestration over choreography:** with 5 participants (Order, Inventory, Payment, Shipment, and indirectly Notification), choreography quickly becomes hard to trace and debug. A single orchestrator (Order Service) owning the state machine gives one place to see saga progress, timeouts, and retries — much closer to what you'd actually run in production.

### 4.1 Saga participants & topics

| Command topic (Order → participant) | Reply topic (participant → Order) |
|---|---|
| `inventory.reserve.command` | `inventory.reserve.reply` (SUCCEEDED / FAILED) |
| `payment.charge.command` | `payment.charge.reply` (SUCCEEDED / FAILED) |
| `inventory.confirm.command` | `inventory.confirm.reply` |
| `shipment.create.command` | `shipment.create.reply` |
| (compensations) `inventory.release.command` / `payment.refund.command` | reply topics for each |

Each command/event carries a standard envelope:
```json
{
  "eventId": "uuid",
  "eventType": "InventoryReserveCommand",
  "sagaId": "order-uuid",
  "aggregateId": "order-uuid",
  "timestamp": "...",
  "payload": { ... }
}
```

### 4.2 State machine (happy path)

```
CREATED
   │  publish inventory.reserve.command
   ▼
INVENTORY_RESERVED
   │  publish payment.charge.command
   ▼
PAYMENT_COMPLETED
   │  publish inventory.confirm.command (convert reserved→deducted)
   ▼
INVENTORY_CONFIRMED
   │  publish shipment.create.command
   ▼
SHIPMENT_CREATED
   │
   ▼
CONFIRMED  ──► publish order.confirmed (Notification listens)
```

### 4.3 Compensating transactions (failure paths)

| Failure point | Compensating action(s) | End state |
|---|---|---|
| Inventory reservation fails (out of stock) | none needed upstream | `CANCELLED` (`order.cancelled` published) |
| Payment fails | `inventory.release.command` to free reserved stock | `CANCELLED` |
| Inventory confirm fails (rare, e.g. DB error) | `payment.refund.command` + `inventory.release.command` | `CANCELLED` |
| Shipment creation fails | `payment.refund.command` + `inventory.release.command` | `CANCELLED` |

The orchestrator persists `sagaState` on the `Order` row after every transition (transactionally, in the same DB write as any local change) so a crash mid-saga can resume from the last durable state — the Order Service polls/consumes replies and is itself idempotent per `sagaId`.

**Timeouts:** each command has a max-wait (e.g., 30s for inventory, 60s for payment). If no reply arrives, the orchestrator triggers the same compensation path as an explicit failure — this needs a scheduled reaper job scanning for orders stuck in a non-terminal state past their SLA.

---

## 5. Transactional Outbox Pattern

Every service that must publish an event **as a side effect of a DB write** (Order, Inventory, Payment, Shipment) uses the outbox pattern instead of a dual-write (DB commit + Kafka publish) which can fail halfway.

**Mechanics:**
1. Business write and an `OutboxEvent` row insert happen in **one local transaction** (e.g., `Order` status update + `OutboxEvent(payload=OrderCreatedEvent)`).
2. A relay process reads unpublished `OutboxEvent` rows and publishes them to Kafka.
    - *Simple option (fine for this project):* a `@Scheduled` poller every ~500ms, `SELECT ... WHERE published = false ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED`, publish, mark `published = true`.
    - *Production-grade option:* Debezium CDC reading the Postgres WAL and streaming outbox rows straight to Kafka — no polling latency, no extra load on the table.
3. Kafka producer uses `acks=all` + idempotent producer (`enable.idempotence=true`) so retries from the relay can't create duplicate-but-different messages.
4. Consumers dedupe using `eventId` (see Idempotency below) since outbox delivery is **at-least-once**.

`OutboxEvent` schema: `(id, aggregateType, aggregateId, eventType, payload JSONB, createdAt, published, publishedAt)`.

---

## 6. Idempotency Strategy

Three layers, because duplicates can be introduced at each hop:

1. **Client → API (REST):** `Idempotency-Key` header required on unsafe POSTs that must not double-execute (`POST /orders`, `POST /payments` if ever exposed). Service stores `(idempotencyKey, requestHash, responseSnapshot)`; a repeat with the same key returns the stored response instead of re-executing.
2. **Producer → Kafka:** Outbox pattern (above) + idempotent Kafka producer avoids duplicate/garbled publishes.
3. **Kafka → Consumer:** Every consumer checks a `ProcessedEvent(eventId UNIQUE)` table (or Redis `SETNX eventId`) *before* applying business logic, in the same transaction as the business write. If the eventId is already present, ack and skip — this makes "at-least-once" delivery behave as "effectively-once" processing.

Payment Service additionally enforces a **DB unique constraint on `orderId`** in the `Payment` table so even a logic bug can't double-charge an order.

---

## 7. Redis Usage

| Use case | Service | Pattern |
|---|---|---|
| Active shopping cart | Cart Service | Primary store — `cart:{userId}` hash, TTL 7 days |
| Product read cache | Product Service | `product:{id}` cache-aside, TTL 10 min, evict on update event |
| Product cache for Cart's Feign fallback | Cart Service | Short-TTL local cache so a Product Service outage still lets users see last-known prices |
| User profile cache | User Service | `user:{id}` cache-aside, TTL 15 min |
| Refresh token store | User Service | `refresh:{tokenHash}` with TTL = token expiry (fast revocation via `DEL`) |
| Idempotency key store | Order/Payment | `idem:{key}` short TTL, or DB table if durability beyond TTL is needed |
| JWT blacklist (logout) | Gateway/User | `blacklist:{jti}` TTL = remaining token life |
| Rate limiting | Gateway | Redis-backed token bucket (Spring Cloud Gateway `RequestRateLimiter` + Redis) |

---

## 8. Resilience4j Configuration Map

| Caller → Callee | CircuitBreaker | Retry | TimeLimiter | Bulkhead | Fallback behavior |
|---|---|---|---|---|---|
| Cart → Product (Feign) | ✅ (50% failure rate opens) | 2 attempts, exp. backoff | 2s | – | Return cached price if available, else "product temporarily unavailable" |
| Cart → Inventory (Feign) | ✅ | 2 attempts | 2s | ✅ (limit concurrent calls) | Treat as "unknown availability", let checkout re-validate synchronously later |
| Order → User (Feign) | ✅ | 1 retry | 2s | – | Proceed with last-known address if cached, else fail the request clearly |
| Payment Service → external gateway | ✅ (production would call a real PSP) | 3 attempts, backoff | 5s | ✅ (cap concurrent PSP calls) | Mark `PAYMENT_FAILED`, saga compensates |
| Gateway → any downstream route | ✅ per-route | – | ✅ | – | Return `503` with a standard error envelope |

All thresholds live in each service's `application.yml` under `resilience4j.*` so they can be tuned per environment without code changes.

---

## 9. Security — JWT Flow

1. `POST /api/v1/auth/login` (User Service) validates credentials, issues:
   - **Access token** (JWT, RS256, ~15 min TTL) — claims: `sub`, `roles`, `jti`, `exp`
   - **Refresh token** (opaque or JWT, 7 day TTL) — stored hashed in Redis for revocation
2. Client sends `Authorization: Bearer <access_token>` on every request.
3. **API Gateway** has a `GlobalFilter` that:
   - Validates signature against the public key (JWKS or shared config) and expiry.
   - Rejects if `jti` is in the Redis blacklist (logout).
   - On success, strips the original header and forwards `X-User-Id`, `X-User-Roles` as trusted internal headers.
   - Routes under `/auth/**` (login, register, refresh) are excluded from the filter.
4. **Downstream services** trust the Gateway-set headers *within the Docker network* (Gateway is the only public entry point) and build a lightweight `SecurityContext` from them for `@PreAuthorize` checks. For defense-in-depth, each service can optionally re-verify the JWT signature too — cheap, and protects against a misconfigured network boundary.
5. `POST /api/v1/auth/refresh` exchanges a valid refresh token for a new access token; refresh tokens are rotated on use.

---

## 10. Global Exception Handling & DTO Conventions

**Every service** ships:
- A custom exception hierarchy: `ResourceNotFoundException`, `BadRequestException`, `ConflictException`, `InsufficientStockException`, `PaymentFailedException`, `UnauthorizedException`, etc., all extending a common `AppException(HttpStatus, String, Object...)`.
- A `@RestControllerAdvice` mapping these (plus `MethodArgumentNotValidException`, `FeignException`, generic `Exception`) to a single response shape:

```json
{
  "timestamp": "2026-09-05T10:15:30Z",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Product 123 not found",
  "path": "/api/v1/products/123",
  "traceId": "a1b2c3d4"
}
```

- Request/response **DTOs are always distinct from JPA entities** — entities never cross a controller boundary. Mapping via MapStruct.
- APIs versioned under `/api/v1/...` from day one.
- OpenAPI docs per service via springdoc, aggregated at the Gateway (`/swagger-ui.html` proxying each service's `/v3/api-docs`).

---

## 11. Testing Strategy

| Layer | Tooling | Scope |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service layer logic, mappers, saga state-transition logic — mock repositories/Feign clients |
| Repository/slice | `@DataJpaTest` + Testcontainers Postgres | Query correctness, constraints (e.g., unique `orderId` on Payment) |
| Integration | `@SpringBootTest` + Testcontainers (Postgres, Kafka, Redis) | Full request→DB→event flow per service, in-container Kafka to verify outbox publishing and consumer idempotency |
| Contract | Feign client stubs (WireMock) or Spring Cloud Contract | Cart↔Product, Cart↔Inventory don't break silently on either side |
| Saga/end-to-end | Testcontainers Kafka + all-services-up (or a subset) | Happy path order confirmation, and each compensation path (stock-out, payment failure, shipment failure) |

---

## 12. Docker Compose Topology

Single `docker-compose.yml` bringing up:

- `zookeeper` + `kafka` (or Kafka in KRaft mode, no Zookeeper) + optional `kafka-ui`
- `redis`
- One Postgres container per service (`user-db`, `product-db`, `inventory-db`, `order-db`, `payment-db`, `shipment-db`, `notification-db`) — or a single `postgres` container with multiple databases created via an init script if you want a lighter dev footprint; separate containers better mirror "database per service" isolation for a portfolio/production-style project.
- `eureka-server`
- `api-gateway`
- `user-service`, `product-service`, `inventory-service`, `cart-service`, `order-service`, `payment-service`, `shipment-service`, `notification-service`

Each app container: `depends_on` its DB and Kafka with `condition: service_healthy`, reads DB/Kafka/Redis connection info from environment variables, and registers with `eureka-server` via `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`. A shared bridge network (`ecommerce-net`) lets services resolve each other by container name.

---

## 13. Cross-Cutting / Non-Functional

- **Observability:** Micrometer + Prometheus metrics on every service, Grafana dashboards; Micrometer Tracing (or Sleuth) + Zipkin for distributed traces across Gateway→Feign→Kafka hops, correlated via `traceId`/`sagaId`.
- **Centralized logging:** structured JSON logs, shipped to an ELK/EFK stack (or just aggregated via `docker logs` for local dev), always including `traceId`.
- **Config:** environment-specific `application-{profile}.yml` per service; a Spring Cloud Config Server is a natural next step if config sprawl grows.
- **Consistency model:** strong consistency inside each service's own DB transaction; **eventual consistency** across services via Kafka — this is the trade-off that makes the saga necessary in the first place.
- **Scalability:** all services are stateless (session state lives in JWT/Redis, not memory), so any service can be horizontally scaled behind Eureka + Gateway load balancing.

---

## 14. Suggested Next Steps

This document is the blueprint. Natural follow-ups, each substantial enough to tackle on its own:
1. Scaffold the Maven multi-module project (or 10 independent Maven projects) with shared `common` library for DTOs like `ErrorResponse` and the event envelope.
2. Implement Eureka Server + Gateway + JWT filter first — everything else depends on this skeleton being up.
3. Build User Service (issues the JWTs everything else needs) and Product/Inventory next.
4. Implement the Order Saga last, once Inventory/Payment/Shipment each have working outbox publishing — the orchestrator is the hardest piece and benefits from stable participants.

Happy to generate the actual Spring Boot code for any of these services, the `docker-compose.yml`, or the Postgres schema/DDL next — just say which piece to start with.
