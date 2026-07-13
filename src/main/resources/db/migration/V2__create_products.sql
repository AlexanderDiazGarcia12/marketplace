-- US-09: products catalog table.
-- Conventions: NUMERIC for money/weight (never FLOAT), TIMESTAMPTZ for timestamps.
-- category uses the native product_category enum created in V1.

CREATE TABLE products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku         VARCHAR(64) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    category    product_category NOT NULL,
    price       NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    stock       INTEGER NOT NULL CHECK (stock >= 0),
    weight_kg   NUMERIC(9, 3) NOT NULL CHECK (weight_kg >= 0),
    version     INTEGER NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_sku UNIQUE (sku)
);
