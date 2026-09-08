-- =====================================================================
-- E-Commerce Microservices — Database Init Script
-- Runs against a single Postgres container, creates 7 isolated databases.
-- Mount this at /docker-entrypoint-initdb.d/init.sql in docker-compose.
-- Uses UUID primary keys throughout so IDs are safe to share across
-- service boundaries (Feign responses, Kafka event payloads) without
-- a central ID authority.
-- =====================================================================

CREATE DATABASE user_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE shipment_db;
CREATE DATABASE notification_db;

-- =====================================================================
-- USER_DB
-- =====================================================================
\connect user_db
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) UNIQUE NOT NULL          -- ROLE_USER, ROLE_ADMIN
);

CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) UNIQUE NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(30),
    is_active      BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id  UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE addresses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    line1       VARCHAR(255) NOT NULL,
    line2       VARCHAR(255),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100),
    zip         VARCHAR(20) NOT NULL,
    country     VARCHAR(100) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_addresses_user_id ON addresses(user_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN');

-- =====================================================================
-- PRODUCT_DB
-- =====================================================================
\connect product_db
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    parent_id   UUID REFERENCES categories(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    price         NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    category_id   UUID REFERENCES categories(id),
    image_url     VARCHAR(500),
    active        BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- generic outbox pattern table, reused shape across all publishing services
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published      BOOLEAN NOT NULL DEFAULT false,
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_outbox_unpublished ON outbox_event(published, created_at) WHERE published = false;

-- =====================================================================
-- INVENTORY_DB
-- =====================================================================
\connect inventory_db
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE stock_items (
    product_id     UUID PRIMARY KEY,               -- mirrors Product.id, no FK (different DB)
    available_qty  INT NOT NULL DEFAULT 0 CHECK (available_qty >= 0),
    reserved_qty   INT NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    version        BIGINT NOT NULL DEFAULT 0,       -- @Version optimistic lock
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_reservations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL,
    product_id  UUID NOT NULL,
    qty         INT NOT NULL CHECK (qty > 0),
    status      VARCHAR(20) NOT NULL,               -- RESERVED, RELEASED, CONFIRMED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_event (                       -- consumer-side idempotency
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published      BOOLEAN NOT NULL DEFAULT false,
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_reservations_order_id ON stock_reservations(order_id);
CREATE INDEX idx_outbox_unpublished ON outbox_event(published, created_at) WHERE published = false;

-- =====================================================================
-- ORDER_DB
-- =====================================================================
\connect order_db
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE orders (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL,
    status                VARCHAR(30) NOT NULL,       -- PENDING, CONFIRMED, CANCELLED
    saga_state            VARCHAR(30) NOT NULL,       -- CREATED, INVENTORY_RESERVED, PAYMENT_COMPLETED,
                                                       -- INVENTORY_CONFIRMED, SHIPMENT_CREATED, CONFIRMED,
                                                       -- COMPENSATING, CANCELLED
    total_amount          NUMERIC(12,2) NOT NULL,
    shipping_address      JSONB,                      -- snapshot at order time
    idempotency_key       VARCHAR(100) UNIQUE,
    payment_id            UUID,                       -- set when payment.charge.reply SUCCEEDED
    compensations_pending INT NOT NULL DEFAULT 0,     -- tracks in-flight compensation commands (0,1,2)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id    UUID NOT NULL,
    product_name  VARCHAR(255) NOT NULL,             -- denormalized snapshot
    qty           INT NOT NULL CHECK (qty > 0),
    unit_price    NUMERIC(12,2) NOT NULL
);

CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status      VARCHAR(30) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (                       -- API-level idempotency (POST /orders)
    key            VARCHAR(100) PRIMARY KEY,
    request_hash   VARCHAR(255) NOT NULL,
    response_body  JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_event (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published      BOOLEAN NOT NULL DEFAULT false,
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_outbox_unpublished ON outbox_event(published, created_at) WHERE published = false;

-- =====================================================================
-- PAYMENT_DB
-- =====================================================================
\connect payment_db
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE payments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID UNIQUE NOT NULL,               -- unique constraint = hard stop on double-charge
    amount       NUMERIC(12,2) NOT NULL,
    status       VARCHAR(20) NOT NULL,                -- PENDING, COMPLETED, FAILED, REFUNDED
    gateway_ref  VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id  UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,                 -- CHARGE, REFUND
    amount      NUMERIC(12,2) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_event (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published      BOOLEAN NOT NULL DEFAULT false,
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_transactions_payment_id ON transactions(payment_id);
CREATE INDEX idx_outbox_unpublished ON outbox_event(published, created_at) WHERE published = false;

-- =====================================================================
-- SHIPMENT_DB
-- =====================================================================
\connect shipment_db
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE shipments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID UNIQUE NOT NULL,
    status           VARCHAR(20) NOT NULL,            -- CREATED, DISPATCHED, DELIVERED, CANCELLED
    carrier          VARCHAR(50),
    tracking_number  VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shipment_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id  UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_event (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published      BOOLEAN NOT NULL DEFAULT false,
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_shipment_events_shipment_id ON shipment_events(shipment_id);
CREATE INDEX idx_outbox_unpublished ON outbox_event(published, created_at) WHERE published = false;

-- =====================================================================
-- NOTIFICATION_DB
-- =====================================================================
\connect notification_db
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE notification_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(20) NOT NULL,                 -- EMAIL, SMS
    recipient   VARCHAR(255) NOT NULL,
    subject     VARCHAR(255),
    payload     JSONB,
    status      VARCHAR(20) NOT NULL,                 -- SENT, FAILED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- notification consumes many topics, single shared dedupe table
CREATE TABLE processed_event (
    event_id      UUID PRIMARY KEY,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
