# Kafka Event Catalog

## Conventions

- **Envelope** (every message on every topic):
```json
{
  "eventId": "uuid",
  "eventType": "string",
  "sagaId": "uuid",
  "aggregateId": "uuid",
  "timestamp": "ISO-8601",
  "payload": { }
}
```
- **Partition key** for every order-saga topic = `orderId` (== `sagaId`) — guarantees all events for one order land on the same partition, so the orchestrator sees them in order.
- **Consumer group naming:** `{service-name}-group` (e.g. `order-service-group`), one group per service so each service gets every message exactly once as a group.
- **Delivery guarantee:** at-least-once (outbox + idempotent producer). Every consumer must check `processed_event(event_id)` before acting — see the idempotency section of the system design doc.

---

## Domain events (not saga commands)

| Topic | Producer | Consumers | Payload |
|---|---|---|---|
| `user.registered` | User Service | Notification | `{ userId, email, firstName, lastName }` |
| `product.created` | Product Service | (none required; reserved for future search/indexing) | `{ productId, name, price, categoryId }` |
| `product.updated` | Product Service | Inventory (to know new products exist), Cart (cache invalidation, optional) | `{ productId, name, price, categoryId, active }` |

---

## Saga commands & replies (Order Service is the sole command producer)

### Step 1 — Reserve inventory

**`inventory.reserve.command`** (Order → Inventory)
```json
{ "orderId": "uuid", "items": [ { "productId": "uuid", "qty": 2 } ] }
```

**`inventory.reserve.reply`** (Inventory → Order)
```json
{ "orderId": "uuid", "status": "SUCCEEDED", "reservationIds": ["uuid"] }
```
```json
{ "orderId": "uuid", "status": "FAILED", "reason": "INSUFFICIENT_STOCK", "productId": "uuid" }
```

### Step 2 — Charge payment

**`payment.charge.command`** (Order → Payment)
```json
{ "orderId": "uuid", "userId": "uuid", "amount": 149.99 }
```

**`payment.charge.reply`** (Payment → Order)
```json
{ "orderId": "uuid", "status": "SUCCEEDED", "paymentId": "uuid", "gatewayRef": "mock-txn-123" }
```
```json
{ "orderId": "uuid", "status": "FAILED", "reason": "CARD_DECLINED" }
```

### Step 3 — Confirm inventory (reserved → deducted)

**`inventory.confirm.command`** (Order → Inventory)
```json
{ "orderId": "uuid" }
```

**`inventory.confirm.reply`** (Inventory → Order)
```json
{ "orderId": "uuid", "status": "SUCCEEDED" }
```

### Step 4 — Create shipment

**`shipment.create.command`** (Order → Shipment)
```json
{ "orderId": "uuid", "userId": "uuid", "shippingAddress": { "line1": "...", "city": "...", "zip": "..." } }
```

**`shipment.create.reply`** (Shipment → Order)
```json
{ "orderId": "uuid", "status": "SUCCEEDED", "shipmentId": "uuid", "trackingNumber": "TRK123456" }
```

---

## Compensating commands & replies

**`inventory.release.command`** (Order → Inventory) — undo a reservation
```json
{ "orderId": "uuid", "reason": "PAYMENT_FAILED" }
```
**`inventory.release.reply`**
```json
{ "orderId": "uuid", "status": "SUCCEEDED" }
```

**`payment.refund.command`** (Order → Payment)
```json
{ "orderId": "uuid", "paymentId": "uuid", "amount": 149.99, "reason": "SHIPMENT_CREATION_FAILED" }
```
**`payment.refund.reply`**
```json
{ "orderId": "uuid", "status": "SUCCEEDED" }
```

---

## Terminal saga events

**`order.confirmed`** (Order → Notification)
```json
{ "orderId": "uuid", "userId": "uuid", "totalAmount": 149.99 }
```

**`order.cancelled`** (Order → Notification)
```json
{ "orderId": "uuid", "userId": "uuid", "reason": "PAYMENT_FAILED" }
```

---

## Topic → consumer summary

| Topic | Consumer(s) |
|---|---|
| `user.registered` | notification-service |
| `product.updated` | inventory-service (optional), cart-service (optional cache evict) |
| `inventory.reserve.command` | inventory-service |
| `inventory.reserve.reply` | order-service |
| `payment.charge.command` | payment-service |
| `payment.charge.reply` | order-service |
| `inventory.confirm.command` | inventory-service |
| `inventory.confirm.reply` | order-service |
| `shipment.create.command` | shipment-service |
| `shipment.create.reply` | order-service, notification-service |
| `inventory.release.command` | inventory-service |
| `inventory.release.reply` | order-service |
| `payment.refund.command` | payment-service |
| `payment.refund.reply` | order-service |
| `order.confirmed` | notification-service |
| `order.cancelled` | notification-service |

16 topics total — create them all with a small number of partitions (3 is plenty for local learning) and `replication-factor=1` (single broker).
