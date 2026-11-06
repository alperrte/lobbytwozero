-- CONT-03 / CONT-16: sabit kategori listesi, içerik-kategori ilişkisi ve içerik kaynakları.
-- Kategoriler (PLATFORM ve TOPIC) bu migration ile tohumlanır (referans veri; ürün kararı: yönetim ucu yoktur, değişiklik yeni
-- migration ister). "Oyun" ilişkisi kategori değil, contents.game_id (REF; game-service'te doğrulanır) ile kurulur (V104).
-- Sabit kimlikler ortamlar arasında aynı kalsın diye gen_random_uuid() yerine literal UUID kullanılır.

create table content_categories (
    id         uuid        not null,
    code       varchar(32) not null,
    kind       varchar(16) not null,
    name_tr    varchar(64) not null,
    name_en    varchar(64) not null,
    sort_order integer     not null,
    constraint pk_content_categories primary key (id),
    constraint uq_content_categories_code unique (code),
    constraint ck_content_categories_kind check (kind in ('PLATFORM', 'TOPIC')),
    constraint ck_content_categories_code check (code ~ '^[A-Z][A-Z0-9_]{1,31}$')
);

insert into content_categories (id, code, kind, name_tr, name_en, sort_order) values
    ('0198d000-0000-7000-8000-000000000001', 'PC',              'PLATFORM', 'PC',               'PC',               10),
    ('0198d000-0000-7000-8000-000000000002', 'PLAYSTATION',     'PLATFORM', 'PlayStation',      'PlayStation',      20),
    ('0198d000-0000-7000-8000-000000000003', 'XBOX',            'PLATFORM', 'Xbox',             'Xbox',             30),
    ('0198d000-0000-7000-8000-000000000004', 'NINTENDO_SWITCH', 'PLATFORM', 'Nintendo Switch',  'Nintendo Switch',  40),
    ('0198d000-0000-7000-8000-000000000005', 'MOBILE',          'PLATFORM', 'Mobil',            'Mobile',           50),
    ('0198d000-0000-7000-8000-000000000006', 'VR',              'PLATFORM', 'Sanal Gerçeklik',  'Virtual Reality',  60),
    ('0198d000-0000-7000-8000-000000000011', 'INDUSTRY',        'TOPIC',    'Sektör',           'Industry',         110),
    ('0198d000-0000-7000-8000-000000000012', 'RELEASE',         'TOPIC',    'Çıkış',            'Release',          120),
    ('0198d000-0000-7000-8000-000000000013', 'UPDATE',          'TOPIC',    'Güncelleme',       'Update',           130),
    ('0198d000-0000-7000-8000-000000000014', 'ESPORTS',         'TOPIC',    'E-spor',           'Esports',          140),
    ('0198d000-0000-7000-8000-000000000015', 'GUIDE',           'TOPIC',    'Rehber',           'Guide',            150),
    ('0198d000-0000-7000-8000-000000000016', 'HARDWARE',        'TOPIC',    'Donanım',          'Hardware',         160),
    ('0198d000-0000-7000-8000-000000000017', 'COMMUNITY',       'TOPIC',    'Topluluk',         'Community',        170),
    ('0198d000-0000-7000-8000-000000000018', 'EVENT',           'TOPIC',    'Etkinlik',         'Event',            180);

-- Bir içerik en çok 5 kategoriye bağlanır (uygulama kuralı); kategori silinemez (restrict), içerik silinirse bağlantı gider.
create table content_category_assignments (
    content_id  uuid not null,
    category_id uuid not null,
    constraint pk_content_category_assignments primary key (content_id, category_id),
    constraint fk_content_category_assignments_content foreign key (content_id) references contents (id) on delete cascade,
    constraint fk_content_category_assignments_category foreign key (category_id) references content_categories (id) on delete restrict
);

create index ix_content_category_assignments_category_id on content_category_assignments (category_id, content_id);

-- CONT-16: kaynağa dayalı haberlerde kaynak adı, https bağlantısı ve kaynak tarihi. İçerik başına en çok 3 kaynak.
-- Bağlantı yalnız saklanır ve gösterilir; sunucu bu adrese hiçbir zaman istek atmaz (SSRF yüzeyi yoktur).
create table content_sources (
    id          uuid         not null,
    content_id  uuid         not null,
    title       varchar(150) not null,
    url         varchar(500) not null,
    source_date date,
    sort_order  integer      not null,
    constraint pk_content_sources primary key (id),
    constraint fk_content_sources_content foreign key (content_id) references contents (id) on delete cascade,
    constraint uq_content_sources_content_id_sort_order unique (content_id, sort_order),
    constraint ck_content_sources_title check (char_length(btrim(title)) between 1 and 150),
    constraint ck_content_sources_url check (url ~ '^https://[^[:space:]]+$'),
    constraint ck_content_sources_sort_order check (sort_order between 0 and 2)
);
