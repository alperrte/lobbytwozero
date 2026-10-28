-- COM-06: gönderi beğenileri.
-- Aynı kullanıcı aynı gönderiyi yalnız bir kez beğenebilir: PK (post_id, user_id) mükerrer kaydı veritabanı düzeyinde engeller.
-- user_id başka servise (auth/user) ait mantıksal referanstır (REF); FK yoktur.
-- posts.like_count sayacı beğeni ekleme/kaldırma ile aynı transaction'da atomik güncellenir (bkz. PostLikeService).

create table post_likes (
    post_id    uuid        not null,
    user_id    uuid        not null,
    created_at timestamptz not null,
    constraint pk_post_likes primary key (post_id, user_id),
    constraint fk_post_likes_post foreign key (post_id) references posts (id) on delete restrict
);

-- Kullanıcının beğendiği gönderileri toplu sorgulamak (likedByMe) ve kullanıcı bazlı sorgular için.
create index ix_post_likes_user_id on post_likes (user_id, post_id);
