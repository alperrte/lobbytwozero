-- COM-21: engelleme olaylarında sıra güvencesi (social-service entegrasyonu).
-- social-service olayları en az bir kez ve üstel geri çekilmeyle teslim eder; bu yüzden gecikmiş eski bir UserBlocked,
-- daha sonra işlenmiş bir UserUnblocked'ın üzerine yazıp kaldırılmış engeli geri getirebilir (ya da tersi). Her kullanıcı
-- çifti için uygulanmış en yeni olayın occurred_at değeri burada tutulur; ondan eski bir olay etkisiz sayılır.
-- user_blocks ile aynı şekilde yalnız yerel bir projeksiyondur: blocker_id / blocked_id başka servise ait mantıksal
-- referanstır (REF), FK yoktur. Engel kaldırılınca user_blocks satırı silinir ama bu işaret kalır (tombstone).

create table user_block_markers (
    blocker_id    uuid        not null,
    blocked_id    uuid        not null,
    last_event_at timestamptz not null,
    constraint pk_user_block_markers primary key (blocker_id, blocked_id),
    constraint ck_user_block_markers_not_self check (blocker_id <> blocked_id)
);

-- Hesap silmede "hesabın taraf olduğu tüm satırlar" silinir; blocker_id PK ile, blocked_id bu indeksle bulunur.
create index ix_user_block_markers_blocked_id on user_block_markers (blocked_id, blocker_id);
