-- Minimal selection catalog for HW-02; no catalog CRUD, search or seed data.
CREATE TABLE hardware_brands (
    id uuid CONSTRAINT pk_hardware_brands PRIMARY KEY,
    name varchar(100) NOT NULL,
    normalized_name varchar(100) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_hardware_brands_name UNIQUE (normalized_name),
    CONSTRAINT ck_hardware_brands_name CHECK (length(btrim(name)) > 0 AND length(btrim(normalized_name)) > 0)
);

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
        (power_watts IS NULL OR power_watts BETWEEN 1 AND 10000))
);

-- A general model exists independently of any optional manufacturer.
CREATE TABLE hardware_product_brands (
    product_id uuid NOT NULL,
    brand_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_hardware_product_brands PRIMARY KEY (product_id, brand_id),
    CONSTRAINT fk_hardware_product_brands_product FOREIGN KEY (product_id) REFERENCES hardware_products(id),
    CONSTRAINT fk_hardware_product_brands_brand FOREIGN KEY (brand_id) REFERENCES hardware_brands(id)
);

CREATE TABLE hardware_profile_components (
    id uuid CONSTRAINT pk_hardware_profile_components PRIMARY KEY,
    profile_id uuid NOT NULL,
    product_id uuid,
    brand_id uuid,
    category varchar(32) NOT NULL,
    model_name varchar(160) NOT NULL,
    brand_name varchar(100),
    quantity integer NOT NULL,
    source varchar(32) NOT NULL DEFAULT 'MANUAL',
    capacity_gb integer,
    clock_mhz integer,
    core_count integer,
    power_watts integer,
    memory_type varchar(32),
    socket varchar(32),
    interface_type varchar(32),
    form_factor varchar(32),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_hardware_profile_components_profile FOREIGN KEY (profile_id)
        REFERENCES hardware_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_hardware_profile_components_product_category FOREIGN KEY (product_id, category)
        REFERENCES hardware_products(id, category),
    CONSTRAINT fk_hardware_profile_components_product_brand FOREIGN KEY (product_id, brand_id)
        REFERENCES hardware_product_brands(product_id, brand_id),
    CONSTRAINT ck_hardware_profile_components_brand CHECK (brand_id IS NULL OR product_id IS NOT NULL),
    CONSTRAINT ck_hardware_profile_components_name CHECK (length(btrim(model_name)) > 0),
    CONSTRAINT ck_hardware_profile_components_quantity CHECK (quantity BETWEEN 1 AND 128),
    CONSTRAINT ck_hardware_profile_components_source CHECK (source = 'MANUAL'),
    CONSTRAINT ck_hardware_profile_components_category CHECK (category IN
        ('CPU','GPU','RAM','MOTHERBOARD','STORAGE','COOLING','PSU','MONITOR','KEYBOARD','MOUSE','HEADSET','OTHER')),
    CONSTRAINT ck_hardware_profile_components_specs CHECK (
        (capacity_gb IS NULL OR capacity_gb BETWEEN 1 AND 1048576) AND
        (clock_mhz IS NULL OR clock_mhz BETWEEN 1 AND 20000) AND
        (core_count IS NULL OR core_count BETWEEN 1 AND 1024) AND
        (power_watts IS NULL OR power_watts BETWEEN 1 AND 10000))
);

CREATE INDEX ix_hardware_profile_components_profile_created
    ON hardware_profile_components (profile_id, created_at, id);
CREATE INDEX ix_hardware_profile_components_product ON hardware_profile_components (product_id);
CREATE INDEX ix_hardware_profile_components_brand ON hardware_profile_components (product_id, brand_id);
CREATE INDEX ix_hardware_product_brands_brand ON hardware_product_brands (brand_id);
