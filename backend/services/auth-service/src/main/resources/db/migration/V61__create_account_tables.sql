-- AUTH-01: hesap çekirdeği, roller ve güvenlik olayları.
-- Durum ve rol listeleri sözleşmedir (docs/database.md §3.1); yalnız PLAYER rolü seed edilir.

CREATE TABLE accounts (
    id                  uuid         PRIMARY KEY,
    email               varchar(254) NOT NULL,
    email_normalized    varchar(254) NOT NULL,
    username            varchar(20)  NOT NULL,
    username_normalized varchar(20)  NOT NULL,
    password_hash       varchar(255),
    status              varchar(32)  NOT NULL,
    email_verified_at   timestamptz,
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

INSERT INTO roles (id, code) VALUES (gen_random_uuid(), 'PLAYER');

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
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_security_events_account_id ON security_events (account_id);
CREATE INDEX ix_security_events_created_at ON security_events (created_at);
