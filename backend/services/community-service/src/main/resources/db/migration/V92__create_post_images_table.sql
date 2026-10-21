-- COM-10: gönderi görselleri. Ayrı media-service yoktur; görsel baytları community_db içinde saklanır
-- (depolama kararı: bytea). Sunucu tarafında yeniden kodlanmış (EXIF'siz) JPEG/PNG kabul edilir.
-- Limitler (görsel başına 2 MiB, gönderi başına 4 görsel) uygulama sabitleriyle aynıdır.

create table post_images (
    id         uuid         not null,
    post_id    uuid         not null,
    content    bytea        not null,
    mime_type  varchar(32)  not null,
    size_bytes integer      not null,
    width      integer      not null,
    height     integer      not null,
    sort_order integer      not null,
    created_at timestamptz  not null,
    constraint pk_post_images primary key (id),
    constraint fk_post_images_post foreign key (post_id) references posts (id) on delete restrict,
    constraint uq_post_images_post_id_sort_order unique (post_id, sort_order),
    constraint ck_post_images_mime_type check (mime_type in ('image/jpeg', 'image/png')),
    constraint ck_post_images_size_bytes check (size_bytes between 1 and 2097152),
    constraint ck_post_images_dimensions check (width > 0 and height > 0),
    constraint ck_post_images_sort_order check (sort_order between 0 and 3)
);
