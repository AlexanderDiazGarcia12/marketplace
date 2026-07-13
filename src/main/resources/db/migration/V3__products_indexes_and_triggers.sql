-- US-09: catalog search indexes and updated_at maintenance trigger.

-- GIN + pg_trgm supports substring search (ILIKE '%term%'), not linguistic FTS.
CREATE INDEX idx_products_name_trgm
    ON products USING gin (name gin_trgm_ops);

CREATE INDEX idx_products_description_trgm
    ON products USING gin (description gin_trgm_ops);

-- Partial index: soft-deleted rows (deleted_at IS NOT NULL) are excluded from
-- category browsing, so indexing them wastes space and slows writes.
CREATE INDEX idx_products_category
    ON products (category)
    WHERE deleted_at IS NULL;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_set_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
