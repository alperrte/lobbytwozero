-- AUTH-11/12/14: kanonik rollerin tamamı, hesap durumu geçmişi ve rol değişikliği geçmişi.

-- V61 yalnız PLAYER'ı seed etti; diğer kanonik roller (docs/database.md) rol yönetimi için burada eklenir.
INSERT INTO roles (id, code) VALUES
    (gen_random_uuid(), 'CONTENT_CREATOR'),
    (gen_random_uuid(), 'GAME_DEVELOPER'),
    (gen_random_uuid(), 'MODERATOR'),
    (gen_random_uuid(), 'ADMIN')
ON CONFLICT (code) DO NOTHING;

-- Durum değişikliği denetim kaydı: kim, neden, hangi durumdan hangisine (silinmez; hesap silinirse düşer).
CREATE TABLE account_status_history (
    id         uuid         PRIMARY KEY,
    account_id uuid         NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    from_status varchar(32) NOT NULL,
    to_status  varchar(32)  NOT NULL,
    reason     varchar(500) NOT NULL,
    actor_id   uuid         NOT NULL,
    created_at timestamptz  NOT NULL,
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

ALTER TABLE auth_sessions DROP CONSTRAINT ck_auth_sessions_reason;
ALTER TABLE auth_sessions ADD CONSTRAINT ck_auth_sessions_reason CHECK (
    revoke_reason IS NULL OR revoke_reason IN (
        'LOGOUT', 'LOGOUT_ALL', 'REFRESH_REUSE', 'ACCOUNT_INACTIVE', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'ROLE_CHANGED'
    )
);
