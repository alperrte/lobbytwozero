-- USER-07/08: bağlı dış hesaplar (yalnız Steam). Bağlama ve kaldırma Auth'ta yapılır; bu tablo Auth olaylarının
-- (ExternalIdentityLinked/Unlinked) son-durum yansımasıdır. Steam OpenID access token vermez: saklanacak token yok.
-- Kaldırılan bağlantı satırı "active = false" olarak kalır (external_id silinir): sırası bozuk eski bir "bağlandı"
-- olayı kaldırmayı geri alamaz. changed_at, durumu belirleyen son olayın occurredAt değeridir.

CREATE TABLE linked_accounts (
    id              uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    provider        varchar(16) NOT NULL,
    external_id     varchar(32),
    active          boolean     NOT NULL,
    show_on_profile boolean     NOT NULL DEFAULT false,
    consent_at      timestamptz,
    linked_at       timestamptz,
    changed_at      timestamptz NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_linked_accounts PRIMARY KEY (id),
    CONSTRAINT fk_linked_accounts_user FOREIGN KEY (user_id) REFERENCES user_profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT uq_linked_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT uq_linked_accounts_provider_external UNIQUE (provider, external_id),
    CONSTRAINT ck_linked_accounts_provider CHECK (provider IN ('STEAM')),
    CONSTRAINT ck_linked_accounts_state CHECK (active = (external_id IS NOT NULL)),
    CONSTRAINT ck_linked_accounts_visible_only_active CHECK (active OR NOT show_on_profile),
    CONSTRAINT ck_linked_accounts_steam_id CHECK (provider <> 'STEAM' OR external_id IS NULL
                                                  OR external_id ~ '^[0-9]{17}$')
);
