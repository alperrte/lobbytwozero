-- GAME-04/05/15: Review'ın imleçli dışa aktarma ucundan beslenen LTZ puan projeksiyonu (karar: Seçenek A).
-- Yalnız toplam bilgi tutulur (kişisel veri yok). Review DB'sine erişilmez; filtre/sıralama bu tablo üzerinde yapılır.

-- Tek satırlık tarama durumu: periyodik taramanın kiralaması (çok örnekli dağıtımda tek tarayıcı),
-- tarama sayacı ve son hatasız tamamlanma zamanı. last_completed_at boşken projeksiyon "hazır değil" sayılır.
create table game_rating_sync_state (
    id uuid primary key check (id = '00000000-0000-0000-0000-000000000002'),
    scan_generation bigint not null default 0 check (scan_generation >= 0),
    lease_owner uuid,
    lease_until timestamptz,
    last_attempt_at timestamptz,
    last_completed_scan bigint check (last_completed_scan >= 0),
    last_completed_at timestamptz,
    last_error_code varchar(64),
    version bigint not null default 0,
    check ((lease_owner is null) = (lease_until is null))
);
insert into game_rating_sync_state(id) values ('00000000-0000-0000-0000-000000000002');

-- Puanı olan (veya taramada puansızlaşan) oyun başına en çok bir satır. Satır yoksa oyun puansızdır.
-- last_seen_scan: satırı en son güncelleyen tarama. Hatasız biten tarama, görmediği satırları puansız yapar.
create table game_rating_projection (
    game_id uuid primary key references games(id) on delete cascade,
    average_rating numeric(4,2) check (average_rating between 1 and 10),
    rating_count bigint not null default 0 check (rating_count >= 0),
    last_seen_scan bigint not null check (last_seen_scan > 0),
    synced_at timestamptz not null,
    version bigint not null default 0,
    check ((average_rating is null) = (rating_count = 0))
);
