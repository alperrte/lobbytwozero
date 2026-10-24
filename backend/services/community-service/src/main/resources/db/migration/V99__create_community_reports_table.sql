-- COM-16: gönderi ve yorum raporları.
-- target_id, hedef türüne göre posts ya da comments satırını gösteren mantıksal referanstır (polimorfik, FK yok);
-- reporter_id ve target_author_id başka servise (auth/user) ait REF'tir. Aynı kullanıcı aynı içeriği bir kez raporlar.
-- Karar admin-service'te verilir (ModerationActionTaken); durum yalnız bu karara göre OPEN'dan çıkar.

create table community_reports (
    id               uuid         not null,
    target_type      varchar(16)  not null,
    target_id        uuid         not null,
    target_author_id uuid         not null,
    reporter_id      uuid         not null,
    reason           varchar(32)  not null,
    detail           varchar(500),
    status           varchar(16)  not null default 'OPEN',
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0,
    constraint pk_community_reports primary key (id),
    constraint uq_community_reports_target_reporter unique (target_type, target_id, reporter_id),
    constraint ck_community_reports_target_type check (target_type in ('POST', 'COMMENT')),
    constraint ck_community_reports_reason check (reason in (
        'SPAM', 'HARASSMENT', 'HATE_SPEECH', 'SEXUAL_CONTENT', 'VIOLENCE', 'ILLEGAL_CONTENT', 'MISINFORMATION', 'OTHER')),
    constraint ck_community_reports_status check (status in ('OPEN', 'RESOLVED', 'DISMISSED')),
    constraint ck_community_reports_not_self check (reporter_id <> target_author_id)
);

-- Kullanıcı başına saatlik rapor sınırı ve moderasyon kuyruğu sorguları.
create index ix_community_reports_reporter_created on community_reports (reporter_id, created_at desc);
create index ix_community_reports_target on community_reports (target_type, target_id);
create index ix_community_reports_open on community_reports (created_at, id) where status = 'OPEN';
