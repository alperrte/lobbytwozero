-- AUTH-05/06: Steam (OpenID 2.0) ve Google (OIDC) ile giriş.

-- Harici kimlik: sağlayıcının değişmez kimliği (Steam: SteamID64, Google: "sub") hesaba bağlanır.
-- E-posta eşleşmesiyle hesap birleştirilmez; bağ yalnız bu tabloda, doğrulanmış sağlayıcı kimliğiyle kurulur.
CREATE TABLE external_identities (
    id               uuid        PRIMARY KEY,
    account_id       uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    provider         varchar(16) NOT NULL,
    provider_subject varchar(64) NOT NULL,
    linked_at        timestamptz NOT NULL,
    CONSTRAINT ck_external_identities_provider CHECK (provider IN ('STEAM', 'GOOGLE')),
    CONSTRAINT uq_external_identities_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_external_identities_account_provider UNIQUE (account_id, provider)
);

-- Tek kullanımlık OAuth/OpenID akışı (state). Yalnız state özeti saklanır; PKCE verifier ve nonce
-- sunucu anahtarıyla state'ten türetilir, bu yüzden saklanmaz.
CREATE TABLE oauth_flows (
    state_hash  varchar(64) PRIMARY KEY,
    provider    varchar(16) NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    CONSTRAINT ck_oauth_flows_provider CHECK (provider IN ('STEAM', 'GOOGLE')),
    CONSTRAINT ck_oauth_flows_state_hash CHECK (state_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_oauth_flows_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_oauth_flows_expires_at ON oauth_flows (expires_at);

-- Steam response_nonce replay koruması: aynı nonce ikinci kez kabul edilmez.
CREATE TABLE openid_nonces (
    nonce_hash varchar(64) PRIMARY KEY,
    seen_at    timestamptz NOT NULL,
    CONSTRAINT ck_openid_nonces_hash CHECK (nonce_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_openid_nonces_seen_at ON openid_nonces (seen_at);

-- Steam e-posta vermez: doğrulanmış SteamID ile kısa ömürlü kayıt hakkı. E-posta/kullanıcı adı toplanır,
-- hesap PENDING_VERIFICATION açılır ve AUTH-02 e-posta doğrulamasıyla ACTIVE olur.
CREATE TABLE steam_registrations (
    token_hash  varchar(64) PRIMARY KEY,
    steam_id    varchar(17) NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    CONSTRAINT ck_steam_registrations_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_steam_registrations_steam_id CHECK (steam_id ~ '^[0-9]{17}$'),
    CONSTRAINT ck_steam_registrations_expiry CHECK (expires_at > created_at)
);

-- Parola dışı ilk faktör (harici giriş) 2FA challenge'ında amr değerini doğru taşır.
ALTER TABLE mfa_challenges ADD COLUMN first_factor varchar(16) NOT NULL DEFAULT 'PWD';
ALTER TABLE mfa_challenges ADD CONSTRAINT ck_mfa_challenges_first_factor CHECK (first_factor IN ('PWD', 'EXTERNAL'));
