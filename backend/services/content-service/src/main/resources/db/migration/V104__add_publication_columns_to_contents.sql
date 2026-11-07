-- CONT-01 / CONT-09 / CONT-11 / CONT-13: yayınlama, öne çıkarma ve oyun ilişkisi.
-- published_at: yayınlanma anı (DRAFT dışındaki her satırda dolu). featured*: öne çıkarma (yalnız PUBLISHED içerik, yalnız ADMIN).
-- game_id: LTZ oyun kataloğundaki oyuna REF (FK yok; game-service'te doğrulanır).
-- Okuma yolları yalnız yayımlanmış ve silinmemiş satırları listeler; bu yüzden indeksler kısmidir.

alter table contents
    add column game_id      uuid,
    add column published_at timestamptz,
    add column featured     boolean not null default false,
    add column featured_at  timestamptz,
    add column featured_by  uuid;

alter table contents
    add constraint ck_contents_published_at check ((status = 'DRAFT') = (published_at is null)),
    add constraint ck_contents_featured check (not featured or (status = 'PUBLISHED' and featured_at is not null and featured_by is not null));

create index ix_contents_published on contents (published_at desc, id desc)
    where status = 'PUBLISHED' and deleted_at is null;

create index ix_contents_featured on contents (published_at desc, id desc)
    where featured and status = 'PUBLISHED' and deleted_at is null;

create index ix_contents_game_id on contents (game_id, published_at desc)
    where game_id is not null and status = 'PUBLISHED' and deleted_at is null;
