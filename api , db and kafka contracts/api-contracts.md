# REST API Contracts

All paths are prefixed `/api/v1` and sit behind the API Gateway. "Auth" column: `Public` (no token), `User` (valid JWT, any role), `Admin` (JWT with `ROLE_ADMIN`), `Internal` (service-to-service Feign call, not routed publicly by the Gateway).

---

## Gateway Route Table

| Path prefix | Routes to | Auth filter applied |
|---|---|---|
| `/api/v1/auth/**` | user-service | Skipped (public) |
| `/api/v1/users/**` | user-service | ✅ |
| `/api/v1/products/**`, `/api/v1/categories/**` | product-service | ✅ (GETs could be left public if you prefer open browsing) |
| `/api/v1/inventory/**` | inventory-service | ✅ |
| `/api/v1/cart/**` | cart-service | ✅ |
| `/api/v1/orders/**` | order-service | ✅ |
| `/api/v1/shipments/**` | shipment-service | ✅ |

Payment Service and Notification Service are **not routed through the Gateway** — they're driven by Kafka and internal Feign only, consistent with the saga design.

---

## User Service

| Method | Path | Auth | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|
| POST | `/auth/register` | Public | `RegisterRequest{email, password, firstName, lastName}` | `UserResponse{id, email, firstName, lastName, roles}` | 201 |
| POST | `/auth/login` | Public | `LoginRequest{email, password}` | `TokenResponse{accessToken, refreshToken, expiresIn}` | 200 |
| POST | `/auth/refresh` | Public | `RefreshRequest{refreshToken}` | `TokenResponse` | 200 |
| POST | `/auth/logout` | User | – (JWT in header) | – | 204 |
| GET | `/users/me` | User | – | `UserResponse` | 200 |
| PUT | `/users/me` | User | `UpdateUserRequest{firstName, lastName, phone}` | `UserResponse` | 200 |
| GET | `/users/{userId}` | Internal (Feign, called by Order) | – | `UserResponse` | 200 |
| GET | `/users/me/addresses` | User | – | `List<AddressResponse>` | 200 |
| POST | `/users/me/addresses` | User | `AddressRequest{line1, line2, city, state, zip, country, isDefault}` | `AddressResponse` | 201 |
| PUT | `/users/me/addresses/{id}` | User | `AddressRequest` | `AddressResponse` | 200 |
| DELETE | `/users/me/addresses/{id}` | User | – | – | 204 |

`AddressResponse{id, line1, line2, city, state, zip, country, isDefault}`

---

## Product Service

| Method | Path | Auth | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|
| GET | `/products?category=&search=&page=&size=` | Public/User | – | `Page<ProductResponse>` | 200 |
| GET | `/products/{id}` | Public/User | – | `ProductResponse` | 200 |
| POST | `/products` | Admin | `ProductRequest{name, description, price, categoryId, imageUrl}` | `ProductResponse` | 201 |
| PUT | `/products/{id}` | Admin | `ProductRequest` | `ProductResponse` | 200 |
| DELETE | `/products/{id}` | Admin | – | – | 204 |
| GET | `/categories` | Public/User | – | `List<CategoryResponse>` | 200 |
| POST | `/categories` | Admin | `CategoryRequest{name, parentId}` | `CategoryResponse` | 201 |

`ProductResponse{id, name, description, price, categoryId, imageUrl, active, createdAt}`
`CategoryResponse{id, name, parentId}`

---

## Inventory Service

| Method | Path | Auth | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|
| GET | `/inventory/{productId}` | User | – | `StockResponse{productId, availableQty, reservedQty}` | 200 |
| GET | `/inventory/{productId}/availability?qty=5` | Internal (Feign, called by Cart) | – | `AvailabilityResponse{productId, available}` | 200 |
| PUT | `/inventory/{productId}` | Admin (restock) | `StockUpdateRequest{quantity}` | `StockResponse` | 200 |

Reservation/release/confirm happen **only via Kafka commands** from the Order saga — deliberately no REST endpoint for these, so the saga is the single writer of reservation state.

---

## Cart Service

| Method | Path | Auth | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|
| GET | `/cart` | User | – | `CartResponse{userId, items:[CartItem{productId, name, price, qty, subtotal}], total}` | 200 |
| POST | `/cart/items` | User | `AddItemRequest{productId, qty}` | `CartResponse` | 200 |
| PUT | `/cart/items/{productId}` | User | `UpdateItemRequest{qty}` | `CartResponse` | 200 |
| DELETE | `/cart/items/{productId}` | User | – | `CartResponse` | 200 |
| DELETE | `/cart` | User | – | – | 204 |
| POST | `/cart/checkout` | User | `CheckoutRequest{shippingAddressId}` + header `Idempotency-Key` | `OrderResponse` | 201 |

`POST /cart/checkout` internally: Cart Service validates items against Product (price) and Inventory (availability) via Feign, then calls `POST /orders` on Order Service via Feign, forwarding the `Idempotency-Key` header, and clears the cart on success.

---

## Order Service

| Method | Path | Auth | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|
| POST | `/orders` | Internal (Feign, called by Cart) + header `Idempotency-Key` | `CreateOrderRequest{userId, items:[OrderItemRequest{productId, qty, unitPrice}], shippingAddressId}` | `OrderResponse{id, status, sagaState, totalAmount, items, createdAt}` | 201 |
| GET | `/orders/{id}` | User (own order) | – | `OrderResponse` | 200 |
| GET | `/orders` | User | – | `Page<OrderResponse>` | 200 |
| GET | `/orders/{id}/status` | User | – | `OrderStatusResponse{status, sagaState}` | 200 |
| POST | `/orders/{id}/cancel` | User | – | `OrderResponse` | 200 (409 if not cancellable) |

`OrderResponse` includes `items: [OrderItemResponse{productId, productName, qty, unitPrice}]`.

---

## Payment Service (internal only)

| Method | Path | Auth | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|
| GET | `/payments/{orderId}` | Internal/Admin | – | `PaymentResponse{id, orderId, amount, status, gatewayRef}` | 200 |

All charge/refund activity is Kafka-driven (`payment.charge.command` → `payment.charge.reply`). This endpoint exists for debugging/admin lookup only.

---

## Shipment Service

| Method | Path | Auth | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|
| GET | `/shipments/{orderId}` | User | – | `ShipmentResponse{id, orderId, status, carrier, trackingNumber}` | 200 |
| GET | `/shipments/track/{trackingNumber}` | Public | – | `ShipmentResponse` | 200 |

---

## Notification Service

No public REST surface — pure Kafka consumer. Optionally, for debugging:

| Method | Path | Auth | Response DTO | Status |
|---|---|---|---|---|
| GET | `/notifications?userId=` | Admin | `List<NotificationLogResponse>` | 200 |

---

## Shared: Standard Error Response (all services)

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
