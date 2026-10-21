-- AUTH-01/11/12/14: hesap çekirdeği, roller, güvenlik olayları ile durum ve rol geçmişi.
-- Durum ve rol listeleri sözleşmedir (docs/database.md §3.1).

CREATE TABLE accounts (
    id                  uuid         PRIMARY KEY,
    email               varchar(254) NOT NULL,
    email_normalized    varchar(254) NOT NULL,
    username            varchar(20)  NOT NULL,
    username_normalized varchar(20)  NOT NULL,
    password_hash       varchar(255),
    status              varchar(32)  NOT NULL,
    email_verified_at   timestamptz,
    token_version       bigint       NOT NULL DEFAULT 0,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_accounts_email_normalized UNIQUE (email_normalized),
    CONSTRAINT uq_accounts_username_normalized UNIQUE (username_normalized),
    CONSTRAINT ck_accounts_status CHECK (status IN (
        'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'BANNED',
        'DISABLED', 'DELETION_REQUESTED', 'DELETED'
    )),
    CONSTRAINT ck_accounts_email_normalized CHECK (email_normalized = lower(email_normalized)),
    CONSTRAINT ck_accounts_username_normalized CHECK (username_normalized ~ '^[a-z0-9_]{3,20}$')
);

CREATE TABLE roles (
    id   uuid        PRIMARY KEY,
    code varchar(32) NOT NULL,
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code CHECK (code IN (
        'PLAYER', 'CONTENT_CREATOR', 'GAME_DEVELOPER', 'MODERATOR', 'ADMIN'
    ))
);

-- Kanonik roller (docs/database.md). Yeni hesaplara yalnız PLAYER verilir; diğerleri yönetici işlemiyle atanır.
INSERT INTO roles (id, code) VALUES
    (gen_random_uuid(), 'PLAYER'),
    (gen_random_uuid(), 'CONTENT_CREATOR'),
    (gen_random_uuid(), 'GAME_DEVELOPER'),
    (gen_random_uuid(), 'MODERATOR'),
    (gen_random_uuid(), 'ADMIN');

CREATE TABLE account_roles (
    account_id uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    role_id    uuid        NOT NULL REFERENCES roles (id),
    granted_by uuid,
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, role_id)
);

CREATE INDEX ix_account_roles_role_id ON account_roles (role_id);

-- Olay türleri uygulamadaki SecurityEventType enum'ı ile sınırlanır; parola, kod veya e-posta tutulmaz.
CREATE TABLE security_events (
    id         uuid        PRIMARY KEY,
    account_id uuid        REFERENCES accounts (id),
    type       varchar(64) NOT NULL,
    request_id varchar(64),
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_security_events_account_id ON security_events (account_id);
CREATE INDEX ix_security_events_created_at ON security_events (created_at);

-- Durum değişikliği denetim kaydı: kim, neden, hangi durumdan hangisine.
CREATE TABLE account_status_history (
    id          uuid         PRIMARY KEY,
    account_id  uuid         NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    from_status varchar(32)  NOT NULL,
    to_status   varchar(32)  NOT NULL,
    reason      varchar(500) NOT NULL,
    actor_id    uuid         NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT ck_account_status_history_from CHECK (from_status IN (
        'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'BANNED', 'DISABLED', 'DELETION_REQUESTED', 'DELETED')),
    CONSTRAINT ck_account_status_history_to CHECK (to_status IN (
        'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'BANNED', 'DISABLED', 'DELETION_REQUESTED', 'DELETED')),
    CONSTRAINT ck_account_status_history_reason CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX ix_account_status_history_account ON account_status_history (account_id, created_at);

CREATE TABLE account_role_history (
    id         uuid         PRIMARY KEY,
    account_id uuid         NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    role_code  varchar(32)  NOT NULL,
    action     varchar(16)  NOT NULL,
    reason     varchar(500) NOT NULL,
    actor_id   uuid         NOT NULL,
    created_at timestamptz  NOT NULL,
    CONSTRAINT ck_account_role_history_role CHECK (role_code IN (
        'PLAYER', 'CONTENT_CREATOR', 'GAME_DEVELOPER', 'MODERATOR', 'ADMIN')),
    CONSTRAINT ck_account_role_history_action CHECK (action IN ('GRANT', 'REVOKE')),
    CONSTRAINT ck_account_role_history_reason CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX ix_account_role_history_account ON account_role_history (account_id, created_at);
