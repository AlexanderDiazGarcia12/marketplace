-- US-22: transactional purchase use case (Unit of Work) — orders + order_items.
-- Numbering note: the CA text says "Migración V7", but V7 was already taken by
-- idempotency_keys (US-21). This is V8, the next free version.
-- The checkout use case persists an Order aggregate and its OrderItem lines atomically in one
-- transaction, keyed by the Idempotency-Key so a retried checkout can never create a second order.
-- status reuses the native order_status enum created in V1 (CONFIRMED | REJECTED), no mirror table.

CREATE TABLE orders (
    -- UUID (not the BIGINT identity used by products/outbox_events) because this id is the
    -- externally-exposed OrderId(UUID). No DEFAULT gen_random_uuid(): unlike import_jobs, the
    -- OrderId is always minted in the domain (OrderId.generate()) before persist, so the app
    -- always supplies it. Omitting the default means a write that forgets the id fails loudly
    -- instead of silently getting a random, un-returned id.
    id              UUID PRIMARY KEY,
    status          order_status NOT NULL,
    -- >= 0 (not > 0 like products.price): a REJECTED order is still persisted for audit, and Money
    -- permits zero, so the DB must not be stricter than the domain invariant.
    total_amount    NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    -- Matches the PaymentToken VO (non-empty, max 255). A fake/test-card token from US-20's
    -- FakePaymentGatewayAdapter — never a real PAN, so no PCI handling is implied here.
    payment_token   VARCHAR(255) NOT NULL,
    -- One order per Idempotency-Key, enforced at the DB (UNIQUE) not just in application logic:
    -- even if the idempotency guard has a bug, a second order for the same key is rejected by the
    -- engine. FK to idempotency_keys(key) with ON DELETE RESTRICT so a key that has produced an
    -- order can never be pruned out from under it (a later TTL cleanup — see V7 — must skip keys
    -- still referenced here). VARCHAR(128) mirrors the IdempotencyKey VO and idempotency_keys.key.
    idempotency_key VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_orders_idempotency_key FOREIGN KEY (idempotency_key)
        REFERENCES idempotency_keys (key) ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE TABLE order_items (
    -- Synthetic identity PK: line rows are never addressed externally, only read back per order.
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Deleting an order deletes its lines (they have no meaning without the order) — CASCADE per CA.
    order_id    UUID NOT NULL
        REFERENCES orders (id) ON DELETE CASCADE ON UPDATE CASCADE,
    -- References products by SKU, not by products.id: the OrderItem aggregate carries a SKU (not a
    -- product id), so this maps the domain 1:1 with no sku->id lookup on write. products.sku is
    -- UNIQUE (uq_products_sku), making it a valid FK target. RESTRICT (not CASCADE) per CA: a
    -- product must never be hard-deletable while a historical line references it. This does not
    -- block US-12 soft-delete (which only sets products.deleted_at), only physical DELETE.
    product_sku VARCHAR(64) NOT NULL
        REFERENCES products (sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    -- Price snapshot captured at purchase time. This is deliberately a stored value, never a live
    -- read/join against products.price: the price the customer paid must be immutable even if the
    -- catalog price later changes. >= 0 (not > 0) to match Money's non-negative invariant and allow
    -- free/promotional line items.
    unit_price  NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0)
);

-- FK columns are not auto-indexed in Postgres. findById(OrderId) reads an order's lines back via
-- WHERE order_id = ?, and ON DELETE CASCADE finds child rows by FK — both would seq-scan the whole
-- table without this. Plain (order_id): line ordering is not a domain invariant (the aggregate
-- recomputes the total regardless of row order), so no second key is needed.
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
