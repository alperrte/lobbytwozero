-- LTZ-UMT-MSG-01: birebir ve grup sohbetleri, grup uyeligi ve rolleri (MSG-01..05, MSG-14).
-- Kullanicilar baska servise (auth/user) aittir: yalniz kimlik (REF) tutulur, FK yoktur.
-- Servis ici iliski (uyelik -> sohbet) FK ile korunur.

create table conversations (
    id          uuid         not null,
    type        varchar(32)  not null,
    -- Yalniz GROUP: 1..100 karakter, bas/son bosluksuz kaydedilir.
    title       varchar(100),
    -- Yalniz DIRECT: iki kullanicinin PostgreSQL uuid sirasiyla (kucuk:buyuk) birlesimi; birebir sohbet tekilligi.
    direct_key  varchar(73),
    created_by  uuid         not null,
    created_at  timestamptz  not null,
    version     bigint       not null default 0,
    constraint pk_conversations primary key (id),
    constraint uq_conversations_direct_key unique (direct_key),
    constraint ck_conversations_type check (type in ('DIRECT', 'GROUP')),
    constraint ck_conversations_direct_key check ((type = 'DIRECT') = (direct_key is not null)),
    constraint ck_conversations_title check (
        (type = 'GROUP' and title is not null and char_length(title) between 1 and 100)
        or (type = 'DIRECT' and title is null))
);

create table conversation_members (
    conversation_id uuid         not null,
    user_id         uuid         not null,
    role            varchar(32)  not null,
    joined_at       timestamptz  not null,
    -- Doluysa uye ayrilmis ya da cikarilmistir; erisim yalniz left_at bos uyelere aciktir.
    left_at         timestamptz,
    -- Cikaran yonetici (MSG-05); kendi ayrilan uyede bostur.
    removed_by      uuid,
    constraint pk_conversation_members primary key (conversation_id, user_id),
    constraint fk_conversation_members_conversation foreign key (conversation_id)
        references conversations (id) on delete cascade,
    constraint ck_conversation_members_role check (role in ('OWNER', 'ADMIN', 'MEMBER')),
    constraint ck_conversation_members_left check (left_at is null or left_at >= joined_at),
    constraint ck_conversation_members_removed check (removed_by is null or left_at is not null)
);

-- Bir sohbette ayni anda en fazla bir aktif sahip.
create unique index uq_conversation_members_active_owner
    on conversation_members (conversation_id)
    where role = 'OWNER' and left_at is null;

-- Kullanicinin aktif sohbetleri (MSG-08 sohbet listesi ve erisim kontrolu).
create index ix_conversation_members_user_active
    on conversation_members (user_id, conversation_id)
    where left_at is null;
