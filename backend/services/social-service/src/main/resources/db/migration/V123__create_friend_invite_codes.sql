-- SOC-14: kullanıcının başkalarına paylaşabileceği arkadaşlık davet kodu.
-- user_id başka servise ait kimliktir (REF, FK yok).

CREATE TABLE friend_invite_codes (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL,
    code       varchar(10) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    max_uses   int         NOT NULL,
    used_count int         NOT NULL DEFAULT 0,
    revoked_at timestamptz,
    version    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_friend_invite_codes_code UNIQUE (code),
    -- Crockford base32 (I, L, O, U yok): okunurken karışmaz, 10 karakter = 50 bit.
    CONSTRAINT ck_friend_invite_codes_code CHECK (code ~ '^[0-9A-HJKMNP-TV-Z]{10}$'),
    CONSTRAINT ck_friend_invite_codes_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_friend_invite_codes_uses CHECK (max_uses > 0 AND used_count >= 0 AND used_count <= max_uses)
);

-- Kullanıcının aynı anda en fazla bir etkin (iptal edilmemiş) kodu olur. Süresi dolan kod yeni kod üretilirken
-- uygulama tarafından iptal edilir; eşzamanlı iki üretimde kaybeden bu indeksle elenir.
CREATE UNIQUE INDEX uq_friend_invite_codes_active_user ON friend_invite_codes (user_id) WHERE revoked_at IS NULL;
