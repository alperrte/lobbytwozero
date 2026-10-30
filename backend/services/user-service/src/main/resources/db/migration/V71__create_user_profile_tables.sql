-- USER-01..04: oyuncu profili, profil görselleri ve servisler arası olay tekilleştirme (docs/database.md §3.2, §2.4).
-- user_id Auth hesabının kimliğidir (REF, FK yok). Parola, e-posta ve token bu serviste tutulmaz.

CREATE TABLE user_profiles (
    user_id                 uuid         NOT NULL,
    -- Auth'tan UserRegistered olayıyla gelen kopya; doğruluk kaynağı Auth'tur. Olay gelene kadar NULL.
    username_snapshot       varchar(20),
    username_normalized     varchar(20),
    display_name            varchar(32),
    bio                     varchar(500),
    player_types            text[]       NOT NULL DEFAULT '{}',
    favorite_genres         text[]       NOT NULL DEFAULT '{}',
    favorite_platforms      text[]       NOT NULL DEFAULT '{}',
    country                 varchar(2),
    account_status          varchar(32)  NOT NULL DEFAULT 'ACTIVE',
    -- Eski bir AccountStatusChanged olayının sonra geldiğinde yeni durumu ezmesini engeller.
    status_changed_at       timestamptz,
    onboarding_completed_at timestamptz,
    created_at              timestamptz  NOT NULL,
    updated_at              timestamptz  NOT NULL,
    version                 bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id),
    CONSTRAINT ck_user_profiles_status CHECK (account_status IN
        ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'BANNED', 'DISABLED', 'DELETION_REQUESTED', 'DELETED')),
    CONSTRAINT ck_user_profiles_username_normalized CHECK (username_normalized IS NULL
        OR username_normalized ~ '^[a-z0-9_]{3,20}$'),
    CONSTRAINT ck_user_profiles_username_pair CHECK ((username_snapshot IS NULL) = (username_normalized IS NULL)),
    CONSTRAINT ck_user_profiles_country CHECK (country IS NULL OR country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_user_profiles_player_types CHECK (cardinality(player_types) <= 2),
    CONSTRAINT ck_user_profiles_favorite_genres CHECK (cardinality(favorite_genres) <= 10),
    CONSTRAINT ck_user_profiles_favorite_platforms CHECK (cardinality(favorite_platforms) <= 10)
);

-- Kullanıcı adı Auth'ta tekildir; kopya da tekil olmalı (arama ve kullanıcı adıyla profil okuma).
CREATE UNIQUE INDEX uq_user_profiles_username_normalized ON user_profiles (username_normalized)
    WHERE username_normalized IS NOT NULL;

-- Profil görselleri (Faz 1b): dosya diskte, burada yalnız üst veri. Satırlar profil silinince birlikte silinir.
CREATE TABLE user_media (
    id           uuid         NOT NULL,
    user_id      uuid         NOT NULL,
    kind         varchar(16)  NOT NULL,
    storage_key  varchar(64)  NOT NULL,
    content_type varchar(32)  NOT NULL,
    byte_size    bigint       NOT NULL,
    width        int          NOT NULL,
    height       int          NOT NULL,
    frame_count  int          NOT NULL DEFAULT 1,
    status       varchar(16)  NOT NULL DEFAULT 'CURRENT',
    created_at   timestamptz  NOT NULL,
    CONSTRAINT pk_user_media PRIMARY KEY (id),
    CONSTRAINT fk_user_media_user FOREIGN KEY (user_id) REFERENCES user_profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_media_kind CHECK (kind IN ('AVATAR', 'COVER')),
    CONSTRAINT ck_user_media_status CHECK (status IN ('CURRENT', 'PENDING_DELETE')),
    CONSTRAINT ck_user_media_size CHECK (byte_size > 0 AND width > 0 AND height > 0 AND frame_count > 0),
    CONSTRAINT uq_user_media_storage_key UNIQUE (storage_key)
);

-- Kullanıcı başına her tür için tek güncel görsel.
CREATE UNIQUE INDEX uq_user_media_current ON user_media (user_id, kind) WHERE status = 'CURRENT';
-- Temizleyicinin "silinmeyi bekleyen" taraması.
CREATE INDEX ix_user_media_pending_delete ON user_media (created_at) WHERE status = 'PENDING_DELETE';

-- Inbox: tüketilen olayların tekilleştirilmesi. Olay yükü ve hesap kimliği saklanmaz (kişisel veri yok).
CREATE TABLE inbox_events (
    event_id     uuid         NOT NULL,
    event_type   varchar(128) NOT NULL,
    producer     varchar(64)  NOT NULL,
    processed_at timestamptz  NOT NULL,
    CONSTRAINT pk_inbox_events PRIMARY KEY (event_id)
);

-- Eski kayıtların temizliği için.
CREATE INDEX ix_inbox_events_processed_at ON inbox_events (processed_at);
