-- US-15: transactional outbox table.
-- Written in the same transaction as the aggregate change; drained to Kafka by
-- an out-of-process relay using SELECT ... FOR UPDATE SKIP LOCKED.
-- status uses the native outbox_status enum created in V1.
-- topic is free-form text (not an enum/CHECK) so future stories can add topics
-- without a schema migration.

CREATE TABLE outbox_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(128) NOT NULL,
    topic          VARCHAR(255) NOT NULL,
    payload        JSONB NOT NULL,
    status         outbox_status NOT NULL DEFAULT 'PENDING',
    retry_count    INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ
);

-- Relay hot path: WHERE status = 'PENDING' ORDER BY created_at FOR UPDATE SKIP LOCKED.
-- Partial index keeps only unpublished rows, so the relay never scans PUBLISHED/FAILED
-- rows and the index stays small as the outbox grows; ordering the index by created_at
-- lets the planner satisfy the ORDER BY without a sort.
CREATE INDEX idx_outbox_events_pending
    ON outbox_events (created_at)
    WHERE status = 'PENDING';

-- Traceability: replay/debug the full event history of a specific aggregate.
-- Non-partial (covers every status) since debugging queries target published and
-- failed rows too. Write cost is one extra B-Tree per insert; acceptable because
-- outbox rows are append-once and this is the only way to trace an aggregate without
-- a sequential scan.
CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id);
