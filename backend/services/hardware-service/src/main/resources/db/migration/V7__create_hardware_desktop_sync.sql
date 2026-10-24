-- Development schema baseline: initialize an empty hardware_db.
-- External user, actor and device UUIDs are logical references, never cross-service foreign keys.

CREATE TABLE hardware_sync_consents (
    id uuid CONSTRAINT pk_hardware_sync_consents PRIMARY KEY,
    profile_id uuid NOT NULL CONSTRAINT fk_hardware_sync_consents_profile REFERENCES hardware_profiles(id) ON DELETE CASCADE,
    device_id uuid NOT NULL,
    generation uuid NOT NULL,
    status varchar(16) NOT NULL,
    granted_at timestamptz NOT NULL,
    revoked_at timestamptz,
    last_sequence bigint NOT NULL DEFAULT 0,
    last_captured_at timestamptz,
    last_synced_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_hardware_sync_consents_profile UNIQUE(profile_id),
    CONSTRAINT ck_hardware_sync_consents_status CHECK (
        (status='GRANTED' AND revoked_at IS NULL) OR (status='REVOKED' AND revoked_at IS NOT NULL)),
    CONSTRAINT ck_hardware_sync_consents_sequence CHECK (last_sequence >= 0 AND
        ((last_sequence=0 AND last_captured_at IS NULL AND last_synced_at IS NULL) OR
         (last_sequence>0 AND last_captured_at IS NOT NULL AND last_synced_at IS NOT NULL))),
    CONSTRAINT ck_hardware_sync_consents_version CHECK (version >= 0),
    CONSTRAINT ck_hardware_sync_consents_times CHECK (created_at <= updated_at AND
        granted_at <= updated_at AND (revoked_at IS NULL OR granted_at <= revoked_at))
);

CREATE INDEX ix_hardware_sync_consents_device ON hardware_sync_consents(device_id);

CREATE TABLE hardware_sync_receipts (
    id uuid CONSTRAINT pk_hardware_sync_receipts PRIMARY KEY,
    profile_id uuid NOT NULL CONSTRAINT fk_hardware_sync_receipts_profile REFERENCES hardware_profiles(id) ON DELETE CASCADE,
    idempotency_key uuid NOT NULL,
    generation uuid NOT NULL,
    sequence bigint NOT NULL,
    request_hash varchar(64) NOT NULL,
    response_body jsonb NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT uq_hardware_sync_receipts_profile_key UNIQUE(profile_id,idempotency_key),
    CONSTRAINT uq_hardware_sync_receipts_profile_generation_sequence UNIQUE(profile_id,generation,sequence),
    CONSTRAINT ck_hardware_sync_receipts_sequence CHECK (sequence > 0),
    CONSTRAINT ck_hardware_sync_receipts_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_hardware_sync_receipts_body CHECK (jsonb_typeof(response_body)='object' AND octet_length(response_body::text)<=4096),
    CONSTRAINT ck_hardware_sync_receipts_version CHECK (version >= 0),
    CONSTRAINT ck_hardware_sync_receipts_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_hardware_sync_receipts_expires ON hardware_sync_receipts(expires_at);
