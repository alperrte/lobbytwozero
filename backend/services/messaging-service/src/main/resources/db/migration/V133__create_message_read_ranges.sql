-- LTZ-UMT-MSG-03: okundu bilgisi (MSG-11/12). Üyenin okuduğunu bildirdiği her ilerleme, hangi mesaj aralığını hangi
-- anda okuduğunu saklar; böylece bir mesajın okunma saati ve okuyanlar sonradan sorgulanabilir.

-- Kapalı aralık [from_seq, to_seq]: işaretçi (last_read_seq) ilerlediğinde önceki değer + 1'den yeni değere kadar.
create table message_read_ranges (
    id              uuid         not null,
    conversation_id uuid         not null,
    -- Okuyan başka servise (auth/user) aittir: REF, FK yok.
    user_id         uuid         not null,
    from_seq        bigint       not null,
    to_seq          bigint       not null,
    read_at         timestamptz  not null,
    constraint pk_message_read_ranges primary key (id),
    constraint fk_message_read_ranges_conversation foreign key (conversation_id)
        references conversations (id) on delete cascade,
    constraint ck_message_read_ranges_from_seq check (from_seq >= 1),
    constraint ck_message_read_ranges_order check (from_seq <= to_seq)
);

-- "Bu mesajı hangi üye ne zaman okudu?" sorgusu: (sohbet, üye) içinde to_seq >= seq olan ilk aralık.
create index ix_message_read_ranges_member_to on message_read_ranges (conversation_id, user_id, to_seq);
