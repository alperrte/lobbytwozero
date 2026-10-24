-- COM-21: kullanıcı engelleme etkisi (yerel okuma kopyası).
-- Engelleme verisinin sahibi social-service'tir; community-service başka servisin veritabanına erişmez. social-service'in
-- yayınladığı UserBlocked / UserUnblocked olayları (POST /internal/events) bu tabloya yansıtılır ve içerik görünürlüğü ile
-- etkileşim kuralları buradan uygulanır. blocker_id / blocked_id başka servise ait mantıksal referanstır (REF); FK yoktur.
-- Bu tablo yalnız bir önbellek/projeksiyondur: kaynak gerçek social-service'tedir.

create table user_blocks (
    blocker_id uuid        not null,
    blocked_id uuid        not null,
    created_at timestamptz not null,
    constraint pk_user_blocks primary key (blocker_id, blocked_id),
    constraint ck_user_blocks_not_self check (blocker_id <> blocked_id)
);

-- "Beni engelleyenler" sorgusu (iki yönlü gizleme için).
create index ix_user_blocks_blocked_id on user_blocks (blocked_id, blocker_id);
