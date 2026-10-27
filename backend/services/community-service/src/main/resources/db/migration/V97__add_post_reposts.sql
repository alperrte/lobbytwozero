-- COM-20: kaynağa atıflı repost.
-- Repost, posts tablosunda ayrı bir satırdır; repost_of_id her zaman ORİJİNAL (repost olmayan) gönderiyi gösterir:
-- bir repost'un reposta'ı zincir kurmaz, doğrudan orijinale bağlanır. Repost orijinalin içeriğini kopyalamaz; yalnız
-- referans tutar, bu yüzden kaynak içerik ve asıl sahiplik değişmez. Orijinal silinir/gizlenirse repost kalır ama
-- orijinal içeriği API'de gösterilmez (okuma anında görünürlük yeniden hesaplanır).
-- body repost'ta isteğe bağlı kısa yorumdur (ürün kararı: opsiyonel yorumlu repost); repost olmayan gönderide zorunludur.

alter table posts add column repost_of_id uuid;
alter table posts add constraint fk_posts_repost_of foreign key (repost_of_id) references posts (id) on delete restrict;

alter table posts alter column body drop not null;
alter table posts drop constraint ck_posts_body_length;
alter table posts add constraint ck_posts_body_length check (
    (repost_of_id is null and body is not null and char_length(btrim(body)) between 1 and 5000)
    or (repost_of_id is not null and (body is null or char_length(btrim(body)) between 1 and 5000))
);

alter table posts add constraint ck_posts_repost_not_self check (repost_of_id is null or repost_of_id <> id);
-- Repost forum konusu ya da oyun bağlantılı gönderi olmaz; bağlam orijinalden okunur.
alter table posts add constraint ck_posts_repost_plain check (repost_of_id is null or (forum_id is null and game_id is null));

-- Bir kullanıcı bir orijinali en çok bir kez (silinmemiş) repostlar; silinen repost yeniden repostlamaya engel olmaz.
create unique index ux_posts_one_repost_per_user on posts (author_id, repost_of_id)
    where repost_of_id is not null and status <> 'DELETED';
create index ix_posts_repost_of_id on posts (repost_of_id) where repost_of_id is not null;
