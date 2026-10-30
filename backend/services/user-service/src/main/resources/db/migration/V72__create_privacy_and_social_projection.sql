-- USER-05/06/11/12: alan bazlı gizlilik, açık profil, oyuncu arama ve profil özetleri (docs/database.md §3.2).
-- Social ve Library/Review verisi bu servise yalnız olayla gelir (yansıma); başka servisin tablosuna erişilmez.

-- Arama: görünen adın küçük harfli, Türkçe i katlamalı kopyası (Java tarafında UserProfile.normalize ile üretilir:
-- Locale.ROOT küçük harf, 'i̇' ve 'ı' -> 'i'; küçük harf bazı karakterleri ikiye açabildiği için 64).
-- Mevcut satırlar için aynı kuralla başlangıç değeri.
ALTER TABLE user_profiles ADD COLUMN display_name_normalized varchar(64);
UPDATE user_profiles
SET display_name_normalized = translate(replace(lower(display_name), 'i' || U&'\0307', 'i'), U&'\0131', 'i')
WHERE display_name IS NOT NULL;

-- Önek araması (LIKE 'abc%') yalnız aktif hesaplarda yapılır.
CREATE INDEX ix_user_profiles_username_prefix ON user_profiles (username_normalized text_pattern_ops)
    WHERE account_status = 'ACTIVE';
CREATE INDEX ix_user_profiles_display_name_prefix ON user_profiles (display_name_normalized text_pattern_ops)
    WHERE account_status = 'ACTIVE';

-- Gizlilik ayarları. Satır yoksa varsayılanlar geçerlidir (eski profiller için doldurma gerekmez).
CREATE TABLE privacy_settings (
    user_id                uuid        NOT NULL,
    friends_visibility     varchar(16) NOT NULL,
    library_visibility     varchar(16) NOT NULL,
    hardware_visibility    varchar(16) NOT NULL,
    performance_visibility varchar(16) NOT NULL,
    guest_profile_access   varchar(16) NOT NULL,
    updated_at             timestamptz NOT NULL,
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_privacy_settings PRIMARY KEY (user_id),
    CONSTRAINT fk_privacy_settings_user FOREIGN KEY (user_id) REFERENCES user_profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_privacy_settings_visibility CHECK (
        friends_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE')
        AND library_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE')
        AND hardware_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE')
        AND performance_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE')),
    CONSTRAINT ck_privacy_settings_guest CHECK (guest_profile_access IN ('BASIC', 'DETAILED'))
);

-- Social yansıması (FriendshipChanged). Kimlikler social-service'in sırasıyla gelir (REF, FK yok). Kaldırılan ilişki
-- active=false olarak kalır: sıra bozulmuş eski bir "eklendi" olayı yeni "kaldırıldı" durumunu ezemez (changed_at).
CREATE TABLE social_friendships (
    user_low_id  uuid        NOT NULL,
    user_high_id uuid        NOT NULL,
    active       boolean     NOT NULL,
    changed_at   timestamptz NOT NULL,
    CONSTRAINT pk_social_friendships PRIMARY KEY (user_low_id, user_high_id),
    CONSTRAINT ck_social_friendships_distinct CHECK (user_low_id <> user_high_id)
);
CREATE INDEX ix_social_friendships_high ON social_friendships (user_high_id);

-- Social yansıması (UserBlocked / UserUnblocked). Engel yönlüdür; kısıt iki yönde uygulanır.
CREATE TABLE social_blocks (
    blocker_id uuid        NOT NULL,
    blocked_id uuid        NOT NULL,
    active     boolean     NOT NULL,
    changed_at timestamptz NOT NULL,
    CONSTRAINT pk_social_blocks PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_social_blocks_distinct CHECK (blocker_id <> blocked_id)
);
CREATE INDEX ix_social_blocks_blocked ON social_blocks (blocked_id);

-- Profil özetleri (USER-12): Library/Review olaylarından gelen MUTLAK sayılar. NULL = kaynaktan henüz veri gelmedi.
-- Profil henüz yokken de gelebilir (REF, FK yok); hesap silmede ayrıca silinir.
CREATE TABLE profile_counters (
    user_id                  uuid        NOT NULL,
    library_collection_count int,
    library_completed_count  int,
    library_favorite_count   int,
    library_changed_at       timestamptz,
    review_count             int,
    review_changed_at        timestamptz,
    CONSTRAINT pk_profile_counters PRIMARY KEY (user_id),
    CONSTRAINT ck_profile_counters_non_negative CHECK (
        coalesce(library_collection_count, 0) >= 0 AND coalesce(library_completed_count, 0) >= 0
        AND coalesce(library_favorite_count, 0) >= 0 AND coalesce(review_count, 0) >= 0)
);
