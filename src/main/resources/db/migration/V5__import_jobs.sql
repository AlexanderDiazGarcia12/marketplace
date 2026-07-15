-- US-16: decoupled CSV upload — async import job tracking.
-- The web thread validates + stores the file, then inserts one import_jobs row in PENDING
-- and publishes via the outbox (US-15). The US-17 consumer processes the CSV row-by-row,
-- writing per-row failures to import_job_errors and moving the job to a terminal state.
-- status reuses the native import_job_status enum created in V1.

CREATE TABLE import_jobs (
    -- UUID (not the BIGINT identity used by products/outbox_events) because this id is
    -- exposed externally: it appears in upload responses and status-poll URLs via ImportJobId.
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Opaque handle (storage path/key) to the already-persisted upload, not the bytes.
    file_reference    TEXT NOT NULL,
    -- Reconciliation: not in US-16's textual AC column list, but ImportProductsCommand (US-04)
    -- already commits to persisting it "for job auditing/display (US-18)". DEFAULT '' mirrors
    -- the record's own null->"" normalization.
    original_filename VARCHAR(255) NOT NULL DEFAULT '',
    status            import_job_status NOT NULL DEFAULT 'PENDING',
    total_rows        INTEGER NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    accepted_rows     INTEGER NOT NULL DEFAULT 0 CHECK (accepted_rows >= 0),
    rejected_rows     INTEGER NOT NULL DEFAULT 0 CHECK (rejected_rows >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Set by the US-17 worker on reaching a terminal state (COMPLETED/FAILED); null while PENDING/PROCESSING.
    completed_at      TIMESTAMPTZ
);

CREATE TABLE import_job_errors (
    -- Synthetic identity PK: these rows are never addressed externally, only listed per-job.
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_job_id  UUID NOT NULL REFERENCES import_jobs (id) ON DELETE CASCADE,
    -- 1-based CSV line number of the rejected row.
    row_number     INTEGER NOT NULL CHECK (row_number > 0),
    raw_line       TEXT NOT NULL,
    -- JSONB array of validation-failure strings, a direct mirror of the Vavr Seq<String>
    -- the US-17 processor accumulates per row. JSONB (not delimiter-joined TEXT) so US-17
    -- needs no schema migration and reasons stay structured/queryable.
    error_reason   JSONB NOT NULL
);

-- US-18 renders a job's error list: WHERE import_job_id = ? ORDER BY row_number.
-- FK columns are not auto-indexed in Postgres, so without this the status view seq-scans the
-- whole errors table per job; the index also lets ON DELETE CASCADE find child rows by FK
-- instead of scanning. Ordered by row_number to satisfy the ORDER BY without a sort.
CREATE INDEX idx_import_job_errors_job_id
    ON import_job_errors (import_job_id, row_number);
