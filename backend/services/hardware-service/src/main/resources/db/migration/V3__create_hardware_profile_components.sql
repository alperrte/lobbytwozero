-- Development schema baseline: initialize an empty hardware_db.
-- External user, actor and device UUIDs are logical references, never cross-service foreign keys.

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
    sync_component_key uuid,
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
    CONSTRAINT ck_hardware_profile_components_source CHECK (
        (source = 'MANUAL' AND sync_component_key IS NULL) OR
        (source = 'DESKTOP_SYNC' AND sync_component_key IS NOT NULL AND product_id IS NULL AND brand_id IS NULL)),
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

CREATE INDEX ix_hardware_profile_components_profile_category_created_id
    ON hardware_profile_components(profile_id, category, created_at, id);

CREATE UNIQUE INDEX uq_hardware_profile_components_profile_sync_key
    ON hardware_profile_components(profile_id,sync_component_key) WHERE sync_component_key IS NOT NULL;
