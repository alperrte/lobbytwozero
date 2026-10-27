-- Faz 2 (COM-04, COM-05, COM-11, COM-12, COM-13): akış, arama, filtre ve sıralama desteği.
-- Faz 2 boyunca bu dosya genişler; commit/paylaşım yapılmadığı için aynı dosyada tutulur
-- (V91-V100 aralığını korumak için tek migration).

-- COM-04: yenilik sırasıyla keyset sayfalama (yalnız herkese açık gönderiler).
create index ix_posts_visible_created_at on posts (created_at desc, id desc) where status = 'VISIBLE';

-- COM-11: servis içi gönderi arama (ayrı search-service yok). Büyük/küçük harf duyarsız "içerir" aramaları
-- lower(body) üzerindeki trigram GIN indeksini kullanır. pg_trgm eklentisi veritabanı sahibinin/süper kullanıcının
-- izniyle açılır (bkz. docs/database.md §4).
create extension if not exists pg_trgm;
create index ix_posts_body_trgm on posts using gin (lower(body) gin_trgm_ops);

-- COM-12: sabit kategori listesi (ürün kararı: GENERAL, GUIDE, QUESTION, SCREENSHOT; oluşturmada opsiyonel,
-- varsayılan GENERAL) ve filtre indeksleri (kategori, ilişkili oyun; yalnız herkese açık gönderiler).
alter table posts add column category varchar(32) not null default 'GENERAL';
alter table posts add constraint ck_posts_category check (category in ('GENERAL', 'GUIDE', 'QUESTION', 'SCREENSHOT'));
create index ix_posts_visible_category_created_at on posts (category, created_at desc, id desc) where status = 'VISIBLE';
create index ix_posts_visible_game_id_created_at on posts (game_id, created_at desc, id desc)
    where status = 'VISIBLE' and game_id is not null;

-- COM-05 / COM-13: etkileşim sayaçları. Sayaçları beğeni (COM-06) ve yorum (COM-07) özellikleri (Faz 3) günceller;
-- o zamana kadar 0'dır.
alter table posts
    add column like_count    integer not null default 0,
    add column comment_count integer not null default 0;
alter table posts add constraint ck_posts_like_count check (like_count >= 0);
alter table posts add constraint ck_posts_comment_count check (comment_count >= 0);

-- COM-13: popülerlik sıralaması (skor = beğeni + yorum; eşitlikte yenilik) için veritabanında hesaplanan kolon ve indeks.
alter table posts add column popularity_score integer generated always as (like_count + comment_count) stored;
create index ix_posts_visible_popularity on posts (popularity_score desc, created_at desc, id desc) where status = 'VISIBLE';
