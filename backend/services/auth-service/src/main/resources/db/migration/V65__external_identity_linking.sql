-- AUTH-20: oturumdaki hesaba harici kimlik (Steam) bağlama ve kaldırma.
--
-- İçindekiler
--   1. Bağlama akışı : oauth_link_flows (yeni tablo)
--   2. Olay kuyruğu  : auth_event_outbox olay türü listesine ExternalIdentityLinked / ExternalIdentityUnlinked eklenir

-- =====================================================================================================================
-- 1. BAĞLAMA AKIŞI
-- =====================================================================================================================

-- Oturumlu kullanıcının başlattığı, tek kullanımlık bağlama akışı (state). Giriş akışlarından (oauth_flows) ayrıdır:
-- hangi hesaba bağlanacağı akışa yazılır, böylece callback oturum çerezine bakmadan hesabı bilir ve bir giriş
-- state'i bağlama için (ya da tersi) kullanılamaz. Yalnız state özeti saklanır.
CREATE TABLE oauth_link_flows (
    state_hash  varchar(64) PRIMARY KEY,
    account_id  uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    provider    varchar(16) NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    CONSTRAINT ck_oauth_link_flows_provider CHECK (provider IN ('STEAM')),
    CONSTRAINT ck_oauth_link_flows_state_hash CHECK (state_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_oauth_link_flows_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_oauth_link_flows_account_id ON oauth_link_flows (account_id);
CREATE INDEX ix_oauth_link_flows_expires_at ON oauth_link_flows (expires_at);

-- =====================================================================================================================
-- 2. OLAY KUYRUĞU
-- =====================================================================================================================

-- Bağlama ve kaldırma olayları User Service'e iletilir (docs-auth/servis-sozlesmeleri.md 3.5).
ALTER TABLE auth_event_outbox DROP CONSTRAINT ck_auth_event_outbox_type;
ALTER TABLE auth_event_outbox ADD CONSTRAINT ck_auth_event_outbox_type CHECK (
    event_type IN (
        'ACCOUNT_STATUS_CHANGED', 'USER_REGISTERED', 'ACCOUNT_EMAIL_CHANGED',
        'EXTERNAL_IDENTITY_LINKED', 'EXTERNAL_IDENTITY_UNLINKED'
    )
);
