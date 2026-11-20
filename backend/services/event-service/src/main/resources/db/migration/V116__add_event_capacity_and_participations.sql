-- EVT-13 / EVT-08 / EVT-09: katılımcı sınırı ve etkinliğe katılma.
-- capacity: null = sınırsız (karar: Nisa'nın EVT-03 T-1 açık noktası, sınırsızlık null ile ifade edilir; 0 anlamsızdır).
-- participant_count: yalnız ACTIVE/ATTENDED katılımları sayan sayaç. Katılım/iptal ile AYNI transaction'da, tek atomik UPDATE
-- ile değiştirilir (kilit satırı UPDATE sırasında alınır); CHECK kısıtı kapasite aşımını eşzamanlı isteklerde de DB seviyesinde
-- engeller. Mevcut etkinliklerde sayaç 0, kapasite null (sınırsız) olur.

alter table events add column capacity integer;
alter table events add column participant_count integer not null default 0;

alter table events add constraint ck_events_capacity
    check (capacity is null or capacity between 1 and 100000);
alter table events add constraint ck_events_participant_count
    check (participant_count >= 0 and (capacity is null or participant_count <= capacity));

-- user_id başka servise (auth-service hesap kimliği) ait mantıksal referanstır (REF): FK yoktur. Etkinlik silinirse katılımları da gider.
create table event_participations (
    id           uuid        not null,
    event_id     uuid        not null,
    user_id      uuid        not null,
    status       varchar(32) not null,
    joined_at    timestamptz not null,
    cancelled_at timestamptz,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    version      bigint      not null default 0,
    constraint pk_event_participations primary key (id),
    constraint fk_event_participations_event foreign key (event_id) references events (id) on delete cascade,
    constraint ck_event_participations_status
        check (status in ('ACTIVE', 'CANCELLED_BY_USER', 'CANCELLED_BY_EVENT', 'ATTENDED')),
    constraint ck_event_participations_cancelled_at
        check ((status in ('CANCELLED_BY_USER', 'CANCELLED_BY_EVENT')) = (cancelled_at is not null))
);

-- Mükerrer katılımı DB seviyesinde engeller: bir kullanıcının bir etkinlikte en çok bir geçerli (ACTIVE/ATTENDED) kaydı olur.
-- İptal edilmiş kayıtlar geçmiş olarak kalır (yeniden katılım yeni satırdır).
create unique index ux_event_participations_current on event_participations (event_id, user_id)
    where status in ('ACTIVE', 'ATTENDED');

-- Kullanıcının katılımları (liste yanıtında izleyici durumu, hesap silme).
create index ix_event_participations_user on event_participations (user_id, event_id);
