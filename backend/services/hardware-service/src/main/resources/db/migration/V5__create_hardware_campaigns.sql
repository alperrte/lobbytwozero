CREATE TABLE hardware_campaigns (
    id uuid CONSTRAINT pk_hardware_campaigns PRIMARY KEY,
    product_id uuid NOT NULL CONSTRAINT fk_hardware_campaigns_product REFERENCES hardware_products(id),
    title varchar(160) NOT NULL,
    source_name varchar(100) NOT NULL,
    source_url varchar(2048) NOT NULL,
    original_price numeric(12,2) NOT NULL,
    sale_price numeric(12,2) NOT NULL,
    currency char(3) NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    observed_at timestamptz NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_hardware_campaigns_text CHECK (
        length(btrim(title)) > 0 AND length(btrim(source_name)) > 0 AND source_url ~* '^https://'),
    CONSTRAINT ck_hardware_campaigns_price CHECK (sale_price > 0 AND original_price > sale_price),
    CONSTRAINT ck_hardware_campaigns_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_hardware_campaigns_time CHECK (ends_at > starts_at AND observed_at <= updated_at),
    CONSTRAINT ck_hardware_campaigns_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_hardware_campaigns_version CHECK (version >= 0)
);
CREATE INDEX ix_hardware_campaigns_active_window ON hardware_campaigns(starts_at, ends_at, id)
    WHERE status = 'ACTIVE';
CREATE INDEX ix_hardware_campaigns_product_window ON hardware_campaigns(product_id, starts_at, ends_at, id)
    WHERE status = 'ACTIVE';
