-- LTZ-UMT-LFP-01 (LFP-01..09) ve LFP-14/15 durum + süre alanları.
-- Oyun (game_id) ve kullanıcı (owner_id) başka servislere aittir: yalnız kimlik (REF) tutulur, FK yoktur.
-- Oyun adı görüntüleme için ilan açılırken game-service'ten alınan anlık görüntüdür (game_title_snapshot).

create table lfp_listings (
    id                  uuid         not null,
    owner_id            uuid         not null,
    game_id             uuid         not null,
    game_title_snapshot varchar(500) not null,
    platform            varchar(32)  not null,
    skill_level         varchar(50),
    players_needed      integer      not null,
    play_time_start     timestamptz,
    play_time_end       timestamptz,
    mic_required        boolean      not null default false,
    description         varchar(1000) not null,
    status              varchar(16)  not null,
    expires_at          timestamptz  not null,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    closed_at           timestamptz,
    version             bigint       not null default 0,
    constraint pk_lfp_listings primary key (id),
    constraint ck_lfp_listings_platform
        check (platform in ('PC', 'PLAYSTATION', 'XBOX', 'NINTENDO_SWITCH', 'MOBILE', 'OTHER')),
    constraint ck_lfp_listings_status check (status in ('OPEN', 'CLOSED', 'EXPIRED')),
    constraint ck_lfp_listings_game_title check (char_length(btrim(game_title_snapshot)) between 1 and 500),
    constraint ck_lfp_listings_skill_level
        check (skill_level is null or char_length(btrim(skill_level)) between 1 and 50),
    constraint ck_lfp_listings_players_needed check (players_needed between 1 and 50),
    constraint ck_lfp_listings_description check (char_length(btrim(description)) between 1 and 1000),
    -- Bitiş yalnız başlangıçla birlikte verilir ve başlangıçtan sonradır.
    constraint ck_lfp_listings_play_time
        check (play_time_end is null or (play_time_start is not null and play_time_end > play_time_start)),
    -- Süre: en az 1 saat, en çok 7 gün (uygulama ayarı daha dar olabilir, geniş olamaz).
    constraint ck_lfp_listings_expiry
        check (expires_at >= created_at + interval '1 hour' and expires_at <= created_at + interval '7 days'),
    -- Kapanış zamanı yalnız kapatılmış ilanda dolu.
    constraint ck_lfp_listings_closed_at check ((status = 'CLOSED') = (closed_at is not null))
);

-- Aktif ilan listesi (LFP-10): en yeni önce, keyset sayfalama.
create index ix_lfp_listings_open_created on lfp_listings (created_at desc, id desc) where status = 'OPEN';
-- Arama (LFP-11): oyun ve platform süzgeci.
create index ix_lfp_listings_open_game_platform on lfp_listings (game_id, platform) where status = 'OPEN';
-- Süresi dolan ilanları bulma (LFP-15).
create index ix_lfp_listings_open_expires on lfp_listings (expires_at) where status = 'OPEN';
-- Sahibin ilanları, açık ilan sınırı ve hesap silme.
create index ix_lfp_listings_owner_created on lfp_listings (owner_id, created_at desc);

-- Oyuncu rolü / tipi beklentisi (LFP-05). İlan başına 1–5 rol, sırası korunur. İlan silinince roller de silinir.
create table lfp_listing_roles (
    listing_id uuid        not null,
    position   integer     not null,
    role       varchar(30) not null,
    constraint pk_lfp_listing_roles primary key (listing_id, position),
    constraint fk_lfp_listing_roles_listing foreign key (listing_id) references lfp_listings (id) on delete cascade,
    constraint ck_lfp_listing_roles_position check (position between 0 and 4),
    constraint ck_lfp_listing_roles_role check (char_length(btrim(role)) between 1 and 30)
);

-- Aynı ilanda aynı rolün (büyük/küçük harf farkı gözetmeksizin) tekrarlanmaması uygulamada sağlanır: liste
-- güncellenirken satırlar pozisyona göre yerinde değiştiğinden ertelenemeyen bir tekil indeks geçici çakışma verirdi.
-- Rol süzgeci (LFP-11), büyük/küçük harfe duyarsız.
create index ix_lfp_listing_roles_role on lfp_listing_roles (lower(role));
