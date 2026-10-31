-- LTZ-ALP-PERF-01: cihaz referansı, senkronizasyon izni ve tekrarsız kabul kaydı.
-- Kanonik cihaz kimliği Auth Service'tedir; auth_device_id ve user_id başka servise ait mantıksal REF'tir (FK yok).

CREATE TABLE sync_consents (
    user_id uuid CONSTRAINT pk_sync_consents PRIMARY KEY,
    status varchar(16) NOT NULL,
    granted_at timestamptz,
    revoked_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_sync_consents_status CHECK (
        (status='GRANTED' AND granted_at IS NOT NULL AND revoked_at IS NULL)
        OR (status='REVOKED' AND revoked_at IS NOT NULL)),
    CONSTRAINT ck_sync_consents_version CHECK (version >= 0)
);

CREATE TABLE device_links (
    id uuid CONSTRAINT pk_device_links PRIMARY KEY,
    auth_device_id uuid NOT NULL,
    user_id uuid NOT NULL,
    label varchar(64) NOT NULL,
    status varchar(16) NOT NULL,
    linked_at timestamptz NOT NULL,
    last_sync_at timestamptz,
    disabled_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_device_links_auth_device UNIQUE (auth_device_id),
    CONSTRAINT ck_device_links_status CHECK (
        (status='ACTIVE' AND disabled_at IS NULL) OR (status='DISABLED' AND disabled_at IS NOT NULL)),
    CONSTRAINT ck_device_links_version CHECK (version >= 0)
);

CREATE INDEX ix_device_links_user ON device_links(user_id, linked_at);
CREATE INDEX ix_device_links_disabled ON device_links(disabled_at) WHERE disabled_at IS NOT NULL;

CREATE TABLE sync_batches (
    id uuid CONSTRAINT pk_sync_batches PRIMARY KEY,
    device_link_id uuid NOT NULL CONSTRAINT fk_sync_batches_device_link REFERENCES device_links(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    idempotency_key uuid NOT NULL,
    request_hash varchar(64) NOT NULL,
    schema_version integer NOT NULL,
    sample_count integer NOT NULL,
    client_sent_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT uq_sync_batches_device_key UNIQUE (device_link_id, idempotency_key),
    CONSTRAINT ck_sync_batches_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sync_batches_counts CHECK (schema_version > 0 AND sample_count >= 0),
    CONSTRAINT ck_sync_batches_expiry CHECK (expires_at > received_at)
);

CREATE INDEX ix_sync_batches_user ON sync_batches(user_id);
CREATE INDEX ix_sync_batches_expires ON sync_batches(expires_at);

CREATE TABLE performance_write_limits (
    bucket_key varchar(96) NOT NULL,
    window_start timestamptz NOT NULL,
    hits integer NOT NULL,
    CONSTRAINT pk_performance_write_limits PRIMARY KEY (bucket_key, window_start),
    CONSTRAINT ck_performance_write_limits_hits CHECK (hits > 0)
);

CREATE INDEX ix_performance_write_limits_window ON performance_write_limits(window_start);

CREATE TABLE performance_account_erasure_fences (
    account_hash varchar(64) CONSTRAINT pk_performance_account_erasure_fences PRIMARY KEY,
    expires_at timestamptz NOT NULL,
    CONSTRAINT ck_performance_account_erasure_fences_hash CHECK (account_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_performance_account_erasure_fences_expiry ON performance_account_erasure_fences(expires_at);

-- Auth D4 hesap silme: yalnız kimliği doğrulanmış iç adaptörden, hesap kilidi altında çağrılır.
-- Tüm kayıtlar sınırlı olduğundan (hesap başına en fazla 5 aktif bağlantı + sınırlı makbuz) tek geçişte silinir;
-- false dönerse çağıran 503 ile Auth'u yeniden denemeye yönlendirir.
CREATE FUNCTION erase_performance_account(account_id uuid) RETURNS boolean LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM sync_batches WHERE id IN (
        SELECT id FROM sync_batches WHERE user_id=account_id ORDER BY id LIMIT 1000);
    DELETE FROM device_links d WHERE d.user_id=account_id AND NOT EXISTS (
        SELECT 1 FROM sync_batches b WHERE b.device_link_id=d.id);
    DELETE FROM sync_consents WHERE user_id=account_id;
    DELETE FROM performance_write_limits WHERE bucket_key IN ('sync-user:'||account_id, 'link-user:'||account_id)
        OR bucket_key LIKE 'sync-device:'||account_id||':%';
    RETURN NOT (
        EXISTS(SELECT 1 FROM sync_batches WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM device_links WHERE user_id=account_id)
        OR EXISTS(SELECT 1 FROM sync_consents WHERE user_id=account_id));
END;
$$;
