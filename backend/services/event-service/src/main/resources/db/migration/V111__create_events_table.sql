-- EVT-01: etkinlik oluşturma.
-- events tablosu etkinliğin çekirdek kaydıdır (docs/database.md §3.12). Bu migration yalnız EVT-01'in yazdığı alanları
-- içerir; kapasite/katılımcı sayacı (EVT-13), oyun bağlantısı (EVT-17), iptal gerekçesi (EVT-15) ve katılım/ceza/oylama
-- tabloları ilgili görevlerde kendi migration'larıyla eklenir (V112+).
-- organizer_id başka servise (auth-service hesap kimliği) ait mantıksal referanstır (REF): FK yoktur.

create table events (
    id           uuid         not null,
    organizer_id uuid         not null,
    type         varchar(32)  not null,
    title        varchar(120) not null,
    description  text         not null,
    starts_at    timestamptz  not null,
    ends_at      timestamptz  not null,
    status       varchar(32)  not null default 'PLANNED',
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    version      bigint       not null default 0,
    constraint pk_events primary key (id),
    constraint ck_events_type check (type in ('TOURNAMENT', 'GAME_NIGHT', 'COMMUNITY', 'OTHER')),
    constraint ck_events_title check (char_length(btrim(title)) between 1 and 120),
    constraint ck_events_description check (char_length(btrim(description)) between 1 and 4000),
    constraint ck_events_schedule check (ends_at > starts_at),
    constraint ck_events_status check (status in ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED'))
);
