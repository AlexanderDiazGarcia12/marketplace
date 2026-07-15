-- US-21: purchase idempotency store.
-- The natural idempotency key is the primary key: no synthetic id. The key is the sole point
-- of contention (concurrent retries with the same Idempotency-Key), so making it the PK gives
-- the sub-millisecond direct lookup the adapter needs and lets the PK's UNIQUE B-Tree resolve
-- the same-key race by itself — the second concurrent INSERT fails on PK violation and the
-- adapter catches it to SELECT and decide the response. Same pattern uq_products_sku already
-- uses for DuplicateSku in US-09.
-- status reuses the native idempotency_status enum created in V1 (IN_PROGRESS | COMPLETED),
-- no mirror table.

CREATE TABLE idempotency_keys (
    key               VARCHAR(128) PRIMARY KEY,
    request_hash      TEXT NOT NULL,
    response_snapshot JSONB,
    status            idempotency_status NOT NULL DEFAULT 'IN_PROGRESS',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- request_hash is TEXT (not VARCHAR(64)) on purpose: the hash algorithm is an adapter concern.
-- SHA-256 hex would be 64 chars, but pinning the column to one algorithm would force a migration
-- if the adapter ever switches (e.g. SHA-512, base64 encoding). It is never queried or indexed —
-- only compared for equality after a PK lookup — so TEXT costs nothing and stays decoupled.

-- response_snapshot is nullable: begin() inserts a fresh IN_PROGRESS row before any response
-- exists; complete() writes the snapshot when the row transitions to COMPLETED.

-- No index beyond the PK. Every access path is a direct lookup by key (begin's INSERT, complete's
-- UPDATE ... WHERE key = ?, and the adapter's post-conflict SELECT ... WHERE key = ?), all served
-- by the PK's B-Tree. request_hash, status and created_at are read only after the row is already
-- located by key, so no secondary index would ever be used.

-- Deferred (not in US-21's CA, documented as tech debt): completed keys accumulate forever. A TTL
-- cleanup of old COMPLETED rows (e.g. a periodic DELETE on created_at, which would then justify a
-- created_at index) will eventually be needed to bound table growth. Not implemented now because
-- the CA does not ask for it and premature retention policy is a business decision.
