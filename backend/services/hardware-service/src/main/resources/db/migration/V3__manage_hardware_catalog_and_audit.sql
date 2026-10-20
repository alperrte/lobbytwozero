-- Extends V2 without deleting catalog entries or changing existing profile references.
ALTER TABLE hardware_brands ADD COLUMN status varchar(32) NOT NULL DEFAULT 'ACTIVE'
    CONSTRAINT ck_hardware_brands_status CHECK (status IN ('ACTIVE','ARCHIVED'));
ALTER TABLE hardware_products ADD COLUMN status varchar(32) NOT NULL DEFAULT 'ACTIVE'
    CONSTRAINT ck_hardware_products_status CHECK (status IN ('ACTIVE','ARCHIVED'));
ALTER TABLE hardware_brands ADD CONSTRAINT ck_hardware_brands_version CHECK (version >= 0);
ALTER TABLE hardware_products ADD CONSTRAINT ck_hardware_products_version CHECK (version >= 0);

CREATE INDEX ix_hardware_brands_status_name ON hardware_brands(status, normalized_name, id);
CREATE INDEX ix_hardware_products_status_model ON hardware_products(status, normalized_model, id);
CREATE INDEX ix_hardware_products_status_category_model ON hardware_products(status, category, normalized_model, id);

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
