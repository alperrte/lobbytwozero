-- MAIL-10..16: kalıcı gönderim kuyruğu, şablonlar ve deneme geçmişi.
-- Gizlilik değişmezi: tam e-posta adresi ve şifreli içerik (kod/bağlantı) yalnız PENDING/PROCESSING iken tutulur;
-- SENT/FAILED satırında ikisi de NULL olur, geriye yalnız maskeli adres (recipient_masked) kalır.

CREATE TABLE mail_templates (
    id         uuid         NOT NULL,
    code       varchar(48)  NOT NULL,
    locale     varchar(2)   NOT NULL,
    subject    varchar(200) NOT NULL,
    body_html  text         NOT NULL,
    body_text  text         NOT NULL,
    version    int          NOT NULL,
    active     boolean      NOT NULL DEFAULT false,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT pk_mail_templates PRIMARY KEY (id),
    CONSTRAINT uq_mail_templates_code_locale_version UNIQUE (code, locale, version),
    CONSTRAINT ck_mail_templates_locale CHECK (locale IN ('tr', 'en')),
    CONSTRAINT ck_mail_templates_version CHECK (version >= 1)
);

CREATE TABLE mail_messages (
    id                  uuid         NOT NULL,
    idempotency_key     uuid         NOT NULL,
    request_hash        varchar(64)  NOT NULL,
    type                varchar(40)  NOT NULL,
    recipient_user_id   uuid         NOT NULL,
    recipient_email     varchar(254),
    recipient_masked    varchar(254) NOT NULL,
    locale              varchar(2),
    payload_encrypted   bytea,
    payload_key_version smallint     NOT NULL,
    status              varchar(16)  NOT NULL,
    attempts            int          NOT NULL DEFAULT 0,
    next_attempt_at     timestamptz  NOT NULL,
    lease_until         timestamptz,
    expires_at          timestamptz  NOT NULL,
    last_error_code     varchar(64),
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    sent_at             timestamptz,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_mail_messages PRIMARY KEY (id),
    CONSTRAINT uq_mail_messages_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_mail_messages_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    CONSTRAINT ck_mail_messages_type CHECK (type IN (
        'EMAIL_VERIFICATION', 'PASSWORD_RESET_LINK', 'ACCOUNT_DELETION_CONFIRMATION',
        'PASSWORD_CHANGED', 'PASSWORD_RESET_COMPLETED', 'EMAIL_CHANGED',
        'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED', 'ACCOUNT_DISABLED', 'ACCOUNT_REACTIVATED')),
    CONSTRAINT ck_mail_messages_locale CHECK (locale IS NULL OR locale IN ('tr', 'en')),
    CONSTRAINT ck_mail_messages_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_mail_messages_sensitive_data CHECK (
        (status IN ('PENDING', 'PROCESSING') AND recipient_email IS NOT NULL AND payload_encrypted IS NOT NULL)
        OR (status IN ('SENT', 'FAILED') AND recipient_email IS NULL AND payload_encrypted IS NULL)),
    CONSTRAINT ck_mail_messages_sent_at CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

CREATE INDEX ix_mail_messages_status_next_attempt ON mail_messages (status, next_attempt_at);
CREATE INDEX ix_mail_messages_processing_lease ON mail_messages (lease_until) WHERE status = 'PROCESSING';
CREATE INDEX ix_mail_messages_recipient_user_id ON mail_messages (recipient_user_id);
CREATE INDEX ix_mail_messages_terminal_updated ON mail_messages (updated_at) WHERE status IN ('SENT', 'FAILED');

CREATE TABLE mail_delivery_attempts (
    id           uuid        NOT NULL,
    message_id   uuid        NOT NULL,
    attempt_no   int         NOT NULL,
    attempted_at timestamptz NOT NULL,
    result       varchar(24) NOT NULL,
    smtp_code    int,
    error_code   varchar(64),
    duration_ms  int         NOT NULL,
    CONSTRAINT pk_mail_delivery_attempts PRIMARY KEY (id),
    CONSTRAINT fk_mail_delivery_attempts_message FOREIGN KEY (message_id) REFERENCES mail_messages (id) ON DELETE CASCADE,
    CONSTRAINT ck_mail_delivery_attempts_result CHECK (result IN ('SENT', 'TRANSIENT_FAILURE', 'PERMANENT_FAILURE', 'EXPIRED')),
    CONSTRAINT ck_mail_delivery_attempts_duration CHECK (duration_ms >= 0)
);

CREATE INDEX ix_mail_delivery_attempts_message_id ON mail_delivery_attempts (message_id);
