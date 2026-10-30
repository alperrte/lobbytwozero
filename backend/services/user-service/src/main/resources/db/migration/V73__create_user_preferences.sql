-- USER-09/10: kullanıcı tercihleri (dil, tema, sınırlı arayüz tercihleri). Satır yoksa varsayılanlar geçerlidir.
-- Onboarding durumu user_profiles.onboarding_completed_at sütunundadır (V71); burada ek tablo gerekmez.

CREATE TABLE user_preferences (
    user_id        uuid        NOT NULL,
    locale         varchar(8)  NOT NULL,
    theme          varchar(16) NOT NULL,
    reduced_motion boolean     NOT NULL,
    compact_layout boolean     NOT NULL,
    updated_at     timestamptz NOT NULL,
    version        bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES user_profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_preferences_locale CHECK (locale IN ('tr', 'en')),
    CONSTRAINT ck_user_preferences_theme CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM'))
);
