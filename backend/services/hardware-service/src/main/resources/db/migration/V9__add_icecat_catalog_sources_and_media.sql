-- Additive V2 catalog ingestion. Existing products and profile enum values are preserved.
ALTER TABLE hardware_products ADD COLUMN catalog_variant varchar(128) NOT NULL DEFAULT '';
ALTER TABLE hardware_products ADD COLUMN catalog_published boolean NOT NULL DEFAULT true;
ALTER TABLE hardware_products DROP CONSTRAINT uq_hardware_products_category_model;
ALTER TABLE hardware_products ADD CONSTRAINT uq_hardware_products_category_model
    UNIQUE(category, normalized_model, catalog_variant);

CREATE TABLE hardware_product_sources (
    id uuid CONSTRAINT pk_hardware_product_sources PRIMARY KEY,
    product_id uuid UNIQUE CONSTRAINT fk_hardware_product_sources_product REFERENCES hardware_products(id) ON DELETE CASCADE,
    source_provider varchar(32) NOT NULL CHECK (source_provider = 'OPEN_ICECAT'),
    external_product_id varchar(64) NOT NULL,
    category varchar(32) NOT NULL CHECK (category IN ('CPU','MOTHERBOARD','RAM','STORAGE')),
    brand varchar(100) NOT NULL,
    mpn varchar(160),
    gtins jsonb NOT NULL DEFAULT '[]',
    locale varchar(8) NOT NULL,
    source_updated_at timestamptz,
    synced_at timestamptz NOT NULL,
    quality varchar(32) NOT NULL CHECK (quality IN ('VERIFIED','QUARANTINED','REMOVED')),
    issue_code varchar(64),
    content_hash varchar(64) NOT NULL,
    typed_specs jsonb NOT NULL DEFAULT '{}',
    feature_groups jsonb NOT NULL DEFAULT '[]',
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_hardware_product_sources_provider_external UNIQUE(source_provider,external_product_id)
);
CREATE UNIQUE INDEX uq_hardware_product_sources_brand_mpn ON hardware_product_sources(lower(brand),lower(mpn))
    WHERE mpn IS NOT NULL AND quality='VERIFIED';
CREATE INDEX ix_hardware_product_sources_gtins ON hardware_product_sources USING gin(gtins);

CREATE TABLE hardware_product_media (
    id uuid CONSTRAINT pk_hardware_product_media PRIMARY KEY,
    source_id uuid NOT NULL CONSTRAINT fk_hardware_product_media_source REFERENCES hardware_product_sources(id) ON DELETE CASCADE,
    asset_id varchar(64) NOT NULL,
    storage_key varchar(80) NOT NULL,
    content_hash varchar(64) NOT NULL,
    media_type varchar(32) NOT NULL CHECK (media_type='image/png'),
    width integer NOT NULL CHECK (width BETWEEN 1 AND 4096),
    height integer NOT NULL CHECK (height BETWEEN 1 AND 4096),
    alt varchar(160) NOT NULL,
    rights_verified boolean NOT NULL DEFAULT false,
    is_primary boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_hardware_product_media_source_asset UNIQUE(source_id,asset_id)
);
CREATE UNIQUE INDEX uq_hardware_product_media_primary ON hardware_product_media(source_id) WHERE is_primary;

CREATE TABLE hardware_catalog_import_runs (
    id uuid CONSTRAINT pk_hardware_catalog_import_runs PRIMARY KEY,
    mode varchar(16) NOT NULL CHECK(mode IN ('BOOTSTRAP','DAILY')),
    status varchar(16) NOT NULL CHECK(status IN ('RUNNING','SUCCEEDED','FAILED')),
    added integer NOT NULL DEFAULT 0,
    updated integer NOT NULL DEFAULT 0,
    skipped integer NOT NULL DEFAULT 0,
    quarantined integer NOT NULL DEFAULT 0,
    error_code varchar(64),
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);
CREATE TABLE hardware_catalog_sync_state (
    provider varchar(32) CONSTRAINT pk_hardware_catalog_sync_state PRIMARY KEY CHECK(provider='OPEN_ICECAT'),
    mode varchar(16) NOT NULL DEFAULT 'BOOTSTRAP',
    generated varchar(32),
    row_offset bigint NOT NULL DEFAULT 0 CHECK(row_offset >= 0),
    last_completed_at timestamptz,
    lease_id uuid,
    lease_until timestamptz,
    version bigint NOT NULL DEFAULT 0
);
-- No seed products, credentials, source content or real account data are installed here.
