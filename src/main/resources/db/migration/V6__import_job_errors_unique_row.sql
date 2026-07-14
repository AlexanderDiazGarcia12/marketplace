-- US-17: make per-row error recording idempotent under at-least-once redelivery.
-- The US-17 consumer inserts one error row per rejected CSV line. If a partially-processed job
-- is redelivered, re-inserting the same (import_job_id, row_number) must be a silent no-op, not a
-- duplicate row — enabling INSERT ... ON CONFLICT (import_job_id, row_number) DO NOTHING.
-- The failure reasons for an identical row do not change between identical retries, so DO NOTHING
-- (not DO UPDATE) is the correct convergence: the first-written reasons are kept.
--
-- This promotes V5's plain composite index to a UNIQUE constraint rather than adding a second
-- index: a UNIQUE constraint is itself backed by a B-Tree on (import_job_id, row_number), so it
-- still serves US-18's "WHERE import_job_id = ? ORDER BY row_number" without a Sort and still lets
-- ON DELETE CASCADE find child rows by FK. Keeping both would be a redundant duplicate index.

DROP INDEX idx_import_job_errors_job_id;

ALTER TABLE import_job_errors
    ADD CONSTRAINT uq_import_job_errors_job_row UNIQUE (import_job_id, row_number);
