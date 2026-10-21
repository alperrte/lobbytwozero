-- Development schema baseline: initialize an empty hardware_db.
-- External user, actor and device UUIDs are logical references, never cross-service foreign keys.

CREATE TABLE hardware_reviews (
    id uuid CONSTRAINT pk_hardware_reviews PRIMARY KEY,
    product_id uuid NOT NULL CONSTRAINT fk_hardware_reviews_product REFERENCES hardware_products(id),
    user_id uuid NOT NULL,
    body varchar(5000),
    rating integer,
    body_hash varchar(64),
    status varchar(16) NOT NULL DEFAULT 'VISIBLE',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT ck_hardware_reviews_status CHECK (status IN ('VISIBLE','HIDDEN','DELETED')),
    CONSTRAINT ck_hardware_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_hardware_reviews_version CHECK (version >= 0),
    CONSTRAINT ck_hardware_reviews_content CHECK (
        (status = 'DELETED' AND body IS NULL AND body_hash IS NULL AND rating IS NULL AND deleted_at IS NOT NULL)
        OR (status <> 'DELETED' AND body IS NOT NULL AND length(btrim(body)) BETWEEN 1 AND 5000
            AND body_hash IS NOT NULL AND body_hash ~ '^[0-9a-f]{64}$' AND deleted_at IS NULL))
);

CREATE UNIQUE INDEX uq_hardware_reviews_product_user ON hardware_reviews(product_id,user_id) WHERE status <> 'DELETED';

CREATE UNIQUE INDEX uq_hardware_reviews_user_body ON hardware_reviews(user_id,body_hash) WHERE status <> 'DELETED';

CREATE INDEX ix_hardware_reviews_product_status_created ON hardware_reviews(product_id,status,created_at,id);

CREATE INDEX ix_hardware_reviews_user_status_created ON hardware_reviews(user_id,status,created_at,id);

CREATE TABLE hardware_review_audit (
    id uuid CONSTRAINT pk_hardware_review_audit PRIMARY KEY,
    actor_id uuid NOT NULL,
    action varchar(32) NOT NULL,
    target_id uuid NOT NULL,
    reason varchar(500) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_hardware_review_audit_action CHECK (action IN ('CREATE_REVIEW','UPDATE_REVIEW','DELETE_REVIEW','REPORT_REVIEW','HIDE_REVIEW','RESTORE_REVIEW')),
    CONSTRAINT ck_hardware_review_audit_reason CHECK (length(btrim(reason)) BETWEEN 1 AND 500)
);

CREATE INDEX ix_hardware_review_audit_target_created ON hardware_review_audit(target_id,created_at,id);

CREATE FUNCTION reject_hardware_review_audit_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Review audit is append-only' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER hardware_review_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE
    ON hardware_review_audit FOR EACH STATEMENT EXECUTE FUNCTION reject_hardware_review_audit_change();

CREATE TABLE hardware_write_limits (
    bucket_key varchar(96) NOT NULL,
    window_start timestamptz NOT NULL,
    hits integer NOT NULL,
    CONSTRAINT pk_hardware_write_limits PRIMARY KEY(bucket_key,window_start),
    CONSTRAINT ck_hardware_write_limits_hits CHECK (hits > 0)
);

CREATE INDEX ix_hardware_write_limits_window ON hardware_write_limits(window_start);
