-- LTZ-UMT-MSG-07: birden çok örnekte çalışma (yayına hazırlık).

-- Kullanıcı başına dakikalık oran sınırı sayaçları (GIF arama, tepki): bellekte değil DB'de, böylece tüm örnekler aynı
-- sınırı paylaşır. Kullanıcı başka servise aittir: REF, FK yok. Eski pencereler dakikada bir silinir.
create table rate_limit_windows (
    user_id      uuid        not null,
    bucket       varchar(32) not null,
    window_start timestamptz not null,
    hits         int         not null,
    constraint pk_rate_limit_windows primary key (user_id, bucket, window_start),
    constraint ck_rate_limit_windows_hits check (hits >= 1)
);

create index ix_rate_limit_windows_window on rate_limit_windows (bucket, window_start);

-- Canlı akış olaylarının örnekler arası aktarımı (ADR 0001): olay toplu olarak yazılır, kimliği pg_notify ile duyurulur,
-- her örnek kendi açık akışlarına iletir. NOTIFY yükü 8 KB ile sınırlı olduğundan içerik burada; kayıtlar kısa ömürlüdür
-- (dakikada bir, 5 dakikadan eskiler silinir).
create table stream_relay_events (
    id         bigserial   not null,
    created_at timestamptz not null default now(),
    payload    text        not null,
    constraint pk_stream_relay_events primary key (id)
);

create index ix_stream_relay_events_created on stream_relay_events (created_at);
