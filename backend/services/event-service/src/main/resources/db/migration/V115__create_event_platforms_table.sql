-- EVT-06: platform filtresi. Platform, etkinliğin KENDİ alanıdır (karar KD-6/A): bağlı oyundan türetilmez, oyuna bağlı
-- olmayan etkinlikte de verilebilir. Bir etkinlik birden çok platformda olabilir (çapraz platform turnuva) ya da hiç
-- platform belirtmeyebilir. Mevcut etkinliklere platform ATANMAZ (satır eklenmez): platformu belirtilmemiş sayılırlar ve
-- platform filtresiyle eşleşmezler.
-- Değerler Content Service'in sabit PLATFORM kategori listesiyle aynıdır (KD-6a: sözlük kararı açık); değişiklik yeni
-- migration ister. Servis içi ilişki olduğundan FK vardır (etkinlik silinirse platformları da silinir).

create table event_platforms (
    event_id uuid        not null,
    platform varchar(32) not null,
    constraint pk_event_platforms primary key (event_id, platform),
    constraint fk_event_platforms_event foreign key (event_id) references events (id) on delete cascade,
    constraint ck_event_platforms_platform check (platform in ('PC', 'PLAYSTATION', 'XBOX', 'NINTENDO_SWITCH', 'MOBILE', 'VR'))
);

-- Platform filtresi: platforma göre etkinlik bulma (exists alt sorgusu).
create index ix_event_platforms_platform on event_platforms (platform, event_id);
