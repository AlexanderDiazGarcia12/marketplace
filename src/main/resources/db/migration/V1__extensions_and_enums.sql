-- US-06: Flyway baseline — extensions and native enums.
-- Conventions: NUMERIC for money/weight (never FLOAT), TIMESTAMPTZ for timestamps.
-- No tables or indexes here; those are colocated with the stories that need them.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Must mirror com.ecommerce.marketplace.domain.model.product.Category enum
-- constant names exactly (Hibernate 7 @JdbcTypeCode(SqlTypes.NAMED_ENUM) maps
-- by Java enum name(), not by the domain's human-readable label()).
CREATE TYPE product_category AS ENUM (
    'ACCESSORIES',
    'BEAUTY',
    'BOOKS',
    'CLOTHING',
    'ELECTRONICS',
    'FOOD_AND_BEVERAGE',
    'FOOTWEAR',
    'GAMES',
    'GIFTS',
    'HEALTH',
    'HOME_AND_OFFICE',
    'KITCHEN',
    'MISC',
    'OUTDOORS',
    'PETS',
    'SPORTS',
    'STATIONERY',
    'TOOLS'
);

CREATE TYPE order_status AS ENUM (
    'CONFIRMED',
    'REJECTED'
);

CREATE TYPE idempotency_status AS ENUM (
    'IN_PROGRESS',
    'COMPLETED'
);

CREATE TYPE outbox_status AS ENUM (
    'PENDING',
    'PUBLISHED',
    'FAILED'
);

CREATE TYPE import_job_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED'
);
