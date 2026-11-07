-- CONT-12: içerik araması için pg_trgm + GIN indeksleri (docs/database.md §4: metin arama kuralı).
-- Arama sorgusu (JdbcContentFeed.SEARCH_MATCH) başlık, özet ve gövdede "lower(sütun) like '%metin%' escape '!'" biçimindedir;
-- indeks ifadeleri sorgudaki ifadelerle birebir aynıdır (lower(sütun)), yoksa PostgreSQL indeksi kullanamaz. Özet ve gövde
-- null olabilir; null satırlar indekse girmez ve koşulu sağlamaz (sonuç değişmez).
-- Not: iki karakterlik aramalar (en az uzunluk) üç harfli trigram üretmez, bu durumda PostgreSQL sıralı taramaya düşer; sonuç
-- aynıdır, yalnız hız farklıdır.
--
-- pg_trgm "trusted" bir eklentidir (PostgreSQL 13+): veritabanında CREATE yetkisi olan kullanıcı oluşturabilir, süper kullanıcı
-- gerekmez. Servis kullanıcısının bu yetkisi yoksa migration burada durur; bu durumda DB sahibi eklentiyi bir kez
-- "create extension pg_trgm;" ile açmalıdır (aşağıdaki IF NOT EXISTS bu halde de çalışır).

create extension if not exists pg_trgm;

create index ix_content_translations_title_trgm on content_translations using gin (lower(title) gin_trgm_ops);
create index ix_content_translations_summary_trgm on content_translations using gin (lower(summary) gin_trgm_ops);
create index ix_content_translations_body_trgm on content_translations using gin (lower(body) gin_trgm_ops);
