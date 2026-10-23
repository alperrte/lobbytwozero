-- Development schema baseline: initialize an empty hardware_db.
-- External user, actor and device UUIDs are logical references, never cross-service foreign keys.

CREATE TABLE hardware_brands (
    id uuid CONSTRAINT pk_hardware_brands PRIMARY KEY,
    name varchar(100) NOT NULL,
    normalized_name varchar(100) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_hardware_brands_name UNIQUE (normalized_name),
    CONSTRAINT ck_hardware_brands_name CHECK (length(btrim(name)) > 0 AND length(btrim(normalized_name)) > 0),
    CONSTRAINT ck_hardware_brands_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_hardware_brands_version CHECK (version >= 0)
);

CREATE INDEX ix_hardware_brands_status_name ON hardware_brands(status, normalized_name, id);

CREATE TABLE hardware_products (
    id uuid CONSTRAINT pk_hardware_products PRIMARY KEY,
    category varchar(32) NOT NULL,
    model varchar(160) NOT NULL,
    normalized_model varchar(160) NOT NULL,
    capacity_gb integer,
    clock_mhz integer,
    core_count integer,
    power_watts integer,
    memory_type varchar(32),
    socket varchar(32),
    interface_type varchar(32),
    form_factor varchar(32),
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_hardware_products_category_model UNIQUE (category, normalized_model),
    CONSTRAINT uq_hardware_products_id_category UNIQUE (id, category),
    CONSTRAINT ck_hardware_products_name CHECK (length(btrim(model)) > 0 AND length(btrim(normalized_model)) > 0),
    CONSTRAINT ck_hardware_products_category CHECK (category IN
        ('CPU','GPU','RAM','MOTHERBOARD','STORAGE','COOLING','PSU','MONITOR','KEYBOARD','MOUSE','HEADSET','OTHER')),
    CONSTRAINT ck_hardware_products_specs CHECK (
        (capacity_gb IS NULL OR capacity_gb BETWEEN 1 AND 1048576) AND
        (clock_mhz IS NULL OR clock_mhz BETWEEN 1 AND 20000) AND
        (core_count IS NULL OR core_count BETWEEN 1 AND 1024) AND
        (power_watts IS NULL OR power_watts BETWEEN 1 AND 10000)),
    CONSTRAINT ck_hardware_products_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_hardware_products_version CHECK (version >= 0)
);

CREATE INDEX ix_hardware_products_status_model ON hardware_products(status, normalized_model, id);

CREATE INDEX ix_hardware_products_status_category_model ON hardware_products(status, category, normalized_model, id);

CREATE INDEX ix_hardware_products_active_capacity ON hardware_products(category, capacity_gb, id)
    WHERE status = 'ACTIVE' AND capacity_gb IS NOT NULL;

CREATE INDEX ix_hardware_products_active_clock ON hardware_products(category, clock_mhz, id)
    WHERE status = 'ACTIVE' AND clock_mhz IS NOT NULL;

CREATE INDEX ix_hardware_products_active_cores ON hardware_products(category, core_count, id)
    WHERE status = 'ACTIVE' AND core_count IS NOT NULL;

CREATE INDEX ix_hardware_products_active_power ON hardware_products(category, power_watts, id)
    WHERE status = 'ACTIVE' AND power_watts IS NOT NULL;

CREATE TABLE hardware_product_brands (
    product_id uuid NOT NULL,
    brand_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_hardware_product_brands PRIMARY KEY (product_id, brand_id),
    CONSTRAINT fk_hardware_product_brands_product FOREIGN KEY (product_id) REFERENCES hardware_products(id),
    CONSTRAINT fk_hardware_product_brands_brand FOREIGN KEY (brand_id) REFERENCES hardware_brands(id)
);

CREATE INDEX ix_hardware_product_brands_brand ON hardware_product_brands (brand_id);

CREATE TABLE hardware_catalog_audit (
    id uuid CONSTRAINT pk_hardware_catalog_audit PRIMARY KEY,
    actor_id uuid NOT NULL,
    action varchar(32) NOT NULL,
    target_id uuid NOT NULL,
    reason varchar(500) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_hardware_catalog_audit_action CHECK (action IN
        ('CREATE_BRAND','UPDATE_BRAND','ARCHIVE_BRAND','CREATE_PRODUCT','UPDATE_PRODUCT','ARCHIVE_PRODUCT',
         'CREATE_CAMPAIGN','UPDATE_CAMPAIGN','ARCHIVE_CAMPAIGN')),
    CONSTRAINT ck_hardware_catalog_audit_reason CHECK (length(btrim(reason)) BETWEEN 1 AND 500)
);

CREATE INDEX ix_hardware_catalog_audit_actor_created ON hardware_catalog_audit(actor_id, created_at);

CREATE FUNCTION reject_hardware_catalog_audit_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Catalog audit is append-only' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER hardware_catalog_audit_immutable
    BEFORE UPDATE OR DELETE OR TRUNCATE ON hardware_catalog_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_hardware_catalog_audit_change();
