# LTZ (LobbyTwoZero) — Veritabanı Tasarımı

> Tarih: 2026-10-20
>
> Durum: Veritabanları `[MEVCUT]` (boş, init SQL ile oluşturulur). **Tablo tasarımları `[ÖNERİ]`dır**; henüz hiçbir serviste Flyway migration yoktur. Kesin şema servis sahibinin ilk migration'ı ile belirlenir ve bu belge aynı değişiklikte güncellenir.
>
> İlgili belgeler: [er-diagram.md](er-diagram.md) (diyagramlar), [architecture.md](architecture.md) (event'ler, Saga), [SECURITY.md](SECURITY.md) §9.

## 1. Mevcut altyapı `[MEVCUT]`

- Tek PostgreSQL 17 instance (`postgres:17-alpine`), DEV host portu `127.0.0.1:5542`.
- `postgres/init/01-create-databases.sql` 17 veritabanı oluşturur; her birinde `public` şeması kullanılır.
- Her servis `jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${DB_<SVC>_NAME}` ile **yalnız kendi** veritabanına bağlanır.
- Hikari: `DB_POOL_MAX_SIZE=3`, `DB_POOL_MIN_IDLE=1` (17 servis × havuz < PostgreSQL `max_connections`).
- JPA: `ddl-auto=validate`, `open-in-view=false`. Flyway: `enabled=true`, `locations=classpath:db/migration`.
- API Gateway veritabanı kullanmaz.

| Veritabanı | Servis | Sahip |
| --- | --- | --- |
| auth_db | auth-service | Kerem |
| user_db | user-service | Kerem |
| mail_db | mail-service | Kerem |
| game_db | game-service | Hamza |
| library_db | library-service | Hamza |
| review_db | review-service | Hamza |
| social_db | social-service | Umut |
| messaging_db | messaging-service | Umut |
| lfp_db | lfp-service | Umut |
| community_db | community-service | Nisa |
| content_db | content-service | Nisa |
| event_db | event-service | Nisa |
| hardware_db | hardware-service | Alper |
| recommendation_db | recommendation-service | Alper |
| performance_db | performance-service | Alper |
| admin_db | admin-service | Ortak |
| notification_db | notification-service | Ortak |

## 2. Ortak kurallar

### 2.1 Sahiplik ve referanslar

- **Database-per-service.** Bir servis başka DB'ye SQL/JPA ile erişmez, cross-database join yapmaz.
- Servisler arası ilişki **REF** (mantıksal referans) olarak tutulur: yalnız `uuid` sütunu, **FK constraint yok**. Varlık doğrulaması API veya event ile yapılır.
- Servis içi ilişkiler **FK** constraint ile korunur; silme davranışı (`ON DELETE CASCADE/RESTRICT`) bilinçli seçilir.
- Başka servisten gelen görüntüleme verisi (ör. kullanıcı adı, oyun adı) gerekirse **snapshot/önbellek sütunu** olarak tutulur ve event ile güncellenir; doğruluk kaynağı sahibi servistir.

### 2.2 Adlandırma ve tipler

| Konu | Kural |
| --- | --- |
| Tablo / sütun | `snake_case`, tablo adı çoğul (`hardware_profiles`) |
| Birincil anahtar | `id uuid` (uygulamada üretilir, UUIDv7 tercih) |
| Referans sütunu | `<varlik>_id` (`user_id`, `game_id`) |
| Zaman | `timestamptz`, **UTC** (`TZ=UTC`); `created_at`, `updated_at`, gerekirse `deleted_at` |
| Optimistic lock | `version bigint not null default 0` (`@Version`) — eşzamanlı güncellenen tablolarda |
| Durum/enum | `varchar(32)` + `CHECK (status IN (...))`; PostgreSQL native enum kullanılmaz (migration kolaylığı) |
| Para | `numeric(12,2)` + `currency char(3)` |
| Metin | Sınırlı alanlar `varchar(n)`; uzun içerik `text` + uygulama/CHECK uzunluk sınırı |
| Esnek veri | `jsonb` yalnız şeması gerçekten değişken veride (ör. sağlayıcı ham metadata, event payload) |
| Boolean | `not null default false` |
| Kısıt adları | `pk_<tablo>`, `fk_<tablo>_<ref>`, `uq_<tablo>_<sütunlar>`, `ck_<tablo>_<kural>`, `ix_<tablo>_<sütunlar>` |

### 2.3 Silme ve saklama

- Kullanıcı içeriğinde (inceleme, gönderi, mesaj, ilan) **soft delete** (`deleted_at`) + moderasyon için `HIDDEN` durumu; kişisel veri silme talebinde (AccountDeleted) kalıcı silme veya anonimleştirme.
- Ölçüm, log ve teknik tablolarda saklama süresi tanımlanır ve zamanlanmış görevle temizlenir (bkz. §5).

### 2.4 Teknik tablolar (event/idempotency kullanan her serviste)

```sql
-- Outbox: iş verisiyle aynı transaction'da yazılır
create table outbox_events (
  id             uuid primary key,
  aggregate_type varchar(64)  not null,
  aggregate_id   uuid         not null,
  event_type     varchar(128) not null,
  event_version  int          not null default 1,
  payload        jsonb        not null,
  occurred_at    timestamptz  not null,
  status         varchar(16)  not null default 'PENDING'
                 check (status in ('PENDING','SENT','FAILED')),
  attempts       int          not null default 0,
  next_attempt_at timestamptz,
  sent_at        timestamptz
);
create index ix_outbox_events_status_next on outbox_events (status, next_attempt_at);

-- Inbox: tüketilen event'lerin tekilleştirilmesi
create table inbox_events (
  event_id     uuid primary key,
  event_type   varchar(128) not null,
  producer     varchar(64)  not null,
  processed_at timestamptz  not null
);

-- Idempotency: Idempotency-Key ile gelen mutasyonlar
create table idempotency_records (
  id              uuid primary key,
  user_id         uuid         not null,
  idempotency_key varchar(128) not null,
  request_hash    varchar(64)  not null,
  response_status int,
  response_body   jsonb,
  created_at      timestamptz  not null,
  expires_at      timestamptz  not null,
  constraint uq_idempotency_records_user_key unique (user_id, idempotency_key)
);
```

- Aynı anahtar farklı `request_hash` ile gelirse `409/422` döner.
- `audit_logs` (admin-service ve auth-service'te zorunlu; diğerlerinde moderasyon işlemi varsa): `id, actor_id, actor_role, action, target_type, target_id, reason, metadata jsonb, request_id, created_at` — **append-only**, güncelleme/silme yok.

## 3. Servis bazlı tablolar `[ÖNERİ]`

Gösterim: **PK** birincil anahtar, **FK** servis içi yabancı anahtar, **REF** servisler arası mantıksal referans, **UQ** tekillik.

### 3.1 auth_db — auth-service (Kerem)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `accounts` | id, email, email_normalized, username, username_normalized, password_hash (nullable: yalnız OAuth), status, email_verified_at, two_factor_enabled, failed_login_count, locked_until, version | UQ email_normalized, UQ username_normalized; status ∈ PENDING_VERIFICATION, ACTIVE, SUSPENDED, BANNED, DISABLED, DELETION_REQUESTED, DELETED |
| `roles` | id, code (PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN) | UQ code |
| `account_roles` | account_id FK, role_id FK, granted_by REF, granted_at | PK (account_id, role_id) |
| `external_identities` | id, account_id FK, provider (STEAM, GOOGLE), provider_subject, linked_at | UQ (provider, provider_subject) — giriş amaçlı |
| `email_verification_codes` | id, account_id FK, code_hash, expires_at, attempts, consumed_at | Kod düz saklanmaz |
| `password_reset_tokens` | id, account_id FK, token_hash, expires_at, consumed_at | Tek kullanımlık |
| `two_factor_secrets` | account_id PK/FK, secret_encrypted, recovery_codes_hash jsonb, enabled_at | Şifreli |
| `refresh_tokens` | id, account_id FK, family_id, token_hash, issued_at, expires_at, revoked_at, replaced_by, user_agent_hash, ip_hash | Rotation + replay tespiti (family iptali) |
| `account_status_history` | id, account_id FK, from_status, to_status, reason, actor_id REF, until, created_at | Askı süresi `until` |
| `security_events` | id, account_id FK nullable, type, ip_hash, created_at | AUTH-15; parola/token yok |
| `audit_logs`, `outbox_events`, `idempotency_records` | §2.4 | |

### 3.2 user_db — user-service (Kerem)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `user_profiles` | user_id PK (REF auth.accounts), display_name, username_snapshot, bio, avatar_key, cover_key, country, onboarding_completed_at, version | Profil tamamen gizlenemez |
| `privacy_settings` | user_id PK/FK, library_visibility, hardware_visibility, friends_visibility, performance_visibility, activity_visibility | Değerler: PUBLIC, FRIENDS, PRIVATE |
| `user_preferences` | user_id PK/FK, locale, theme, favorite_genres jsonb, platforms jsonb | Tema dilden bağımsız |
| `linked_accounts` | id, user_id FK, provider (STEAM, DISCORD), external_id, display_name, access_token_encrypted, refresh_token_encrypted, scopes, consent_at, linked_at | UQ (provider, external_id); UQ (user_id, provider) |
| `inbox_events`, `outbox_events` | §2.4 | |

### 3.3 mail_db — mail-service (Kerem)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `mail_templates` | id, code, locale, subject, body_html, body_text, version, active | UQ (code, locale, version) |
| `mail_messages` | id, idempotency_key, template_code, locale, recipient_email, recipient_user_id REF, variables_encrypted jsonb, status, attempts, next_attempt_at, last_error_code, sent_at, created_at | UQ idempotency_key; status ∈ PENDING, PROCESSING, SENT, FAILED |
| `mail_delivery_attempts` | id, message_id FK, attempted_at, result, smtp_code | Hata metni kişisel veri içermez |
| `inbox_events` | §2.4 | |

> Doğrulama kodu/sıfırlama linki gibi hassas değişkenler `variables_encrypted` içinde şifreli ve gönderimden sonra temizlenir.

### 3.4 game_db — game-service (Hamza)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `games` | id (kanonik gameId), slug, title, normalized_title, description, release_date, developer_name, publisher_name, origin (INTERNAL, EXTERNAL), metadata_completeness (INDEX_ONLY, PARTIAL, FULL), status (ACTIVE, ARCHIVED, MERGED), merged_into_id FK, owner_developer_id REF, version | UQ slug |
| `game_external_refs` | id, game_id FK, provider, external_id, last_synced_at | **UQ (provider, external_id)** |
| `genres`, `tags`, `platforms` | id, code, name_tr, name_en | UQ code |
| `game_genres`, `game_tags`, `game_platforms` | game_id FK, <ref>_id FK | PK bileşik |
| `game_media` | id, game_id FK, type (COVER, SCREENSHOT, TRAILER), url, provider, sort_order | |
| `game_system_requirements` | id, game_id FK, level (MINIMUM, RECOMMENDED), os, cpu, gpu, ram_mb, storage_mb, raw_text | REC-07/08 girdisi |
| `game_provider_sync_state` | id, provider, cursor, sync_status, last_run_at, last_error_code | GAME-19 |
| `game_popularity` | game_id PK/FK, score, review_count, avg_rating, library_count, calculated_at | Event ile güncellenir |
| `game_prices` | id, game_id FK, provider, region, currency, price, discount_percent, fetched_at | GAME-24 (koşullu) |
| `game_developer_posts` | id, game_id FK, author_id REF, title, body, status, created_at | GAME-13 |
| `outbox_events`, `inbox_events` | §2.4 | |

İndeks: `ix_games_normalized_title` (`pg_trgm` GIN, arama), `ix_games_status_release_date`, `ix_game_popularity_score`.

### 3.5 library_db — library-service (Hamza)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `library_entries` | id, user_id REF, game_id REF, in_collection, favorite, completed, completed_at, play_status, added_at, updated_at, version | **UQ (user_id, game_id)**; favori/bitirildi → in_collection=true (LIB-15) |
| `game_snapshots` | game_id PK, title, cover_url, status, refreshed_at | Katalog değişikliklerine dayanıklılık (LIB-17) |
| `idempotency_records`, `outbox_events`, `inbox_events` | §2.4 | |

İndeks: `ix_library_entries_user_favorite`, `ix_library_entries_user_completed`.

### 3.6 review_db — review-service (Hamza)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `reviews` | id, user_id REF, game_id REF, rating smallint, recommended boolean, body, status (VISIBLE, HIDDEN, DELETED), created_at, updated_at, version | Kullanıcı başına oyun başına **tek aktif inceleme**: partial UQ (user_id, game_id) WHERE status <> 'DELETED'; CHECK rating aralığı |
| `review_likes` | review_id FK, user_id REF, created_at | **PK (review_id, user_id)** |
| `review_reports` | id, review_id FK, reporter_id REF, reason, status, created_at | UQ (review_id, reporter_id) |
| `game_rating_summaries` | game_id PK, review_count, rating_sum, avg_rating, positive_count, negative_count, updated_at | Batch özet (REV-12) |
| `outbox_events`, `inbox_events` | §2.4 | |

### 3.7 social_db — social-service (Umut)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `friend_requests` | id, sender_id REF, receiver_id REF, status (PENDING, ACCEPTED, REJECTED, CANCELLED), created_at, responded_at | CHECK sender_id <> receiver_id; partial UQ (sender_id, receiver_id) WHERE status='PENDING' |
| `friendships` | user_low_id REF, user_high_id REF, created_at | PK (user_low_id, user_high_id); CHECK low < high (tek satır/çift) |
| `user_blocks` | blocker_id REF, blocked_id REF, created_at | PK (blocker_id, blocked_id); CHECK farklı |
| `friend_invite_codes` | id, user_id REF, code, expires_at, max_uses, used_count, revoked_at | UQ code |
| `social_privacy_settings` | user_id PK, who_can_send_requests, friends_list_visibility | |
| `outbox_events`, `inbox_events` | §2.4 | |

### 3.8 messaging_db — messaging-service (Umut)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `conversations` | id, type (DIRECT, GROUP), title, direct_key, created_by REF, created_at | UQ direct_key (birebir sohbet tekilliği: sıralı iki userId) |
| `conversation_members` | conversation_id FK, user_id REF, role (OWNER, ADMIN, MEMBER), joined_at, left_at, removed_by REF, last_read_message_id | PK (conversation_id, user_id) |
| `messages` | id, conversation_id FK, sender_id REF, client_message_id, type (TEXT, IMAGE, GIF, VOICE), body, reply_to_id FK, forwarded_from_id FK, deleted_for_everyone_at, created_at, edited_at | **UQ (sender_id, client_message_id)**; ix (conversation_id, created_at desc) |
| `message_attachments` | id, message_id FK, storage_key, mime_type, size_bytes, width, height | CHECK size_bytes ≤ 1048576 |
| `message_reactions` | message_id FK, user_id REF, emoji | PK (message_id, user_id, emoji) |
| `message_reads` | message_id FK, user_id REF, read_at | PK (message_id, user_id) — MSG-11/12 |
| `message_hidden_for_users` | message_id FK, user_id REF, hidden_at | "Benden sil" (MSG-25) |
| `outbox_events`, `inbox_events` | §2.4 | |

### 3.9 lfp_db — lfp-service (Umut)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `lfp_listings` | id, owner_id REF, game_id REF, game_title_snapshot, platform, skill_level (nullable), roles jsonb, players_needed, play_time_start, play_time_end, mic_required, description, status (OPEN, FULL, CLOSED, EXPIRED), expires_at, created_at, version | CHECK players_needed > 0; ix (status, game_id, platform), ix (expires_at) WHERE status='OPEN' |
| `lfp_reports` | id, listing_id FK, reporter_id REF, reason, status, created_at | UQ (listing_id, reporter_id) |
| `outbox_events`, `inbox_events` | §2.4 | |

> İlan sahibiyle iletişim messaging-service üzerinden yapılır; lfp_db mesaj saklamaz.

### 3.10 community_db — community-service (Nisa)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `forums` | id, game_id REF nullable, slug, title, description, status (VISIBLE, HIDDEN), created_by REF, created_at, updated_at, version | **V96 ile uygulandı** (COM-19). UQ `slug`, CHECK slug `^[a-z0-9]+(-[a-z0-9]+)*$` (3–80), başlık 1–120, status ∈ VISIBLE, HIDDEN; `slug` ve `game_id` sonradan değişmez (entity `updatable=false`). Oluşturma/gizleme moderatör/admin rolü ister. Gizli forumun konuları tüm listelerden ve etkileşimden çıkar. İndeksler: `ix_forums_visible_created_at`, `ix_forums_visible_game_id` (kısmi, `status='VISIBLE'`) |
| `posts` | id, author_id REF, forum_id FK nullable, game_id REF nullable, body, status (VISIBLE, HIDDEN, DELETED), repost_of_id FK nullable, like_count, comment_count, created_at, updated_at, version | Repost orijinale bağlı (COM-20); **V91 ile uygulanan kolonlar:** id, author_id, game_id (REF game-service, FK yok; COM-14'te API'ye açıldı), body, status, created_at, updated_at, deleted_at, version (CHECK: status, body uzunluğu 1–5000, `deleted_at` yalnız `DELETED` durumunda dolu). **V96 (Faz 3):** `forum_id` (uuid, FK → forums `ON DELETE RESTRICT`; forum konusu, oluşturulduktan sonra değişmez), `ix_posts_visible_forum_created_at`. **V97 (Faz 3):** `repost_of_id` (uuid, FK → posts `ON DELETE RESTRICT`; her zaman ORİJİNAL repost olmayan gönderiyi gösterir, zincir yok), `body` artık NULL olabilir (repost'ta isteğe bağlı not) ve `ck_posts_body_length` yeniden tanımlandı: repost olmayan gönderide 1–5000 zorunlu, repost'ta null ya da 1–5000; `ck_posts_repost_not_self`, `ck_posts_repost_plain` (repost'ta `forum_id` ve `game_id` null); UNIQUE kısmi indeks `ux_posts_one_repost_per_user (author_id, repost_of_id)` (`repost_of_id` dolu ve `status <> 'DELETED'`), `ix_posts_repost_of_id`. **V93 (Faz 2):** `category` (varchar(32), CHECK ∈ GENERAL, GUIDE, QUESTION, SCREENSHOT, varsayılan GENERAL), `like_count`/`comment_count` (integer, ≥ 0, varsayılan 0; beğeni/yorum uçlarıyla atomik `update ... set x = x ± 1` ile güncellenir, entity'de `updatable=false` olduğundan gönderi düzenlemesi sayacı ezmez); `popularity_score` (integer, `generated always as (like_count + comment_count) stored`; popülerlik sıralaması); indeksler: `ix_posts_visible_popularity`, `ix_posts_visible_created_at`, `ix_posts_visible_category_created_at`, `ix_posts_visible_game_id_created_at` (kısmi, `status='VISIBLE'`), `ix_posts_body_trgm` (gin, `lower(body) gin_trgm_ops`, `pg_trgm` eklentisi gerekir — süper kullanıcı/DB sahibi izniyle açılır) |
| `post_images` | id, post_id FK, content bytea, mime_type, size_bytes, width, height, sort_order, created_at | **V92 ile uygulandı** (depolama kararı: community_db/bytea; `storage_key` yok, `id` kullanılır). UQ (post_id, sort_order); CHECK sort_order 0–3 (en çok 4 görsel), mime ∈ image/jpeg, image/png, size ≤ 2 MiB; FK `ON DELETE RESTRICT` |
| `post_likes` | post_id FK, user_id REF, created_at | **V94 ile uygulandı** (COM-06). PK (post_id, user_id) mükerrer beğeniyi engeller; FK `ON DELETE RESTRICT`; `ix_post_likes_user_id`. Ekleme `insert ... on conflict do nothing` ile idempotent; `posts.like_count` aynı transaction'da güncellenir |
| `comments` | id, post_id FK, author_id REF, parent_comment_id FK nullable, body, status (VISIBLE, HIDDEN, DELETED), created_at, updated_at, deleted_at, version | **V95 ile uygulandı** (COM-07/08/09). CHECK: status, body 1–2000, `deleted_at` yalnız `DELETED`, `parent_comment_id <> id`; FK'ler `ON DELETE RESTRICT`. Sınırsız iç içe yanıt: yanıtlar üst yorum başına keyset okunur (özyinelemeli sorgu yok). Soft delete; yanıtı olan silinmiş yorum satır olarak kalır. İndeksler: `ix_comments_post_top_level`, `ix_comments_parent` (kısmi), `ix_comments_author_id`. `posts.comment_count` = VISIBLE yorum sayısı |
| `community_reports` | id, target_type (POST, COMMENT), target_id, target_author_id REF, reporter_id REF, reason, detail, status (OPEN, RESOLVED, DISMISSED), created_at, updated_at, version | **V99 ile uygulandı** (COM-16). UQ (target_type, target_id, reporter_id); `target_id` polimorfik mantıksal referanstır (FK yok); CHECK: target_type, reason ∈ SPAM, HARASSMENT, HATE_SPEECH, SEXUAL_CONTENT, VIOLENCE, ILLEGAL_CONTENT, MISINFORMATION, OTHER, status, `reporter_id <> target_author_id`; indeksler: `ix_community_reports_reporter_created` (saatlik sınır), `ix_community_reports_target`, `ix_community_reports_open` (kısmi, `status='OPEN'`). Durum yalnız admin-service kararıyla (`ModerationActionTaken`) OPEN'dan çıkar |
| `user_blocks` | blocker_id REF, blocked_id REF, created_at | **V100 ile uygulandı** (COM-21). PK (blocker_id, blocked_id); CHECK blocker ≠ blocked; `ix_user_blocks_blocked_id`. Veri sahibi social-service'tir; bu tablo `UserBlocked` / `UserUnblocked` olaylarının yerel okuma yansımasıdır (iki yönlü gizleme ve etkileşim engeli) |
| `outbox_events`, `inbox_events` | §2.4 | **V98 ile uygulandı** (§2.4 şeması + `ck_outbox_events_status/attempts`). Outbox: `ContentReported` (→ admin), `CommentCreated`, `PostLiked`, `PostReposted` (→ notification); olay iş verisiyle aynı transaction'da yazılır, relay kapalıyken `PENDING` bekler; `SENT` kayıtlar 14 gün sonra temizlenir. Inbox: `ModerationActionTaken`, `UserBlocked`, `UserUnblocked` için `event_id` tekilleştirmesi |

### 3.11 content_db — content-service (Nisa)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `content_categories` | id, code, name_tr, name_en | UQ code |
| `contents` | id, author_id REF, type (NEWS, ANNOUNCEMENT, USER_CONTENT), category_id FK, slug, status (DRAFT, PUBLISHED, REMOVED, ARCHIVED), featured, published_at, removed_by REF, removed_reason, created_at, updated_at, version | UQ slug; ANNOUNCEMENT yalnız admin (uygulama kuralı) |
| `content_translations` | content_id FK, locale, title, summary, body | PK (content_id, locale) |
| `content_images` | id, content_id FK, storage_key, alt_tr, alt_en, sort_order | |
| `content_sources` | id, content_id FK, title, url | CONT-16 |
| `outbox_events`, `inbox_events` | §2.4 | |

### 3.12 event_db — event-service (Nisa)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `events` | id, organizer_id REF, type (TOURNAMENT, GAME_NIGHT, COMMUNITY, OTHER), game_id REF nullable, title, description, starts_at, ends_at, capacity, participant_count, status (PLANNED, ACTIVE, COMPLETED, CANCELLED), cancelled_reason, version | CHECK participant_count ≤ capacity; CHECK ends_at > starts_at |
| `event_participations` | id, event_id FK, user_id REF, status (ACTIVE, CANCELLED_BY_USER, CANCELLED_BY_EVENT, ATTENDED), joined_at, cancelled_at | Partial UQ (event_id, user_id) WHERE status='ACTIVE' |
| `participation_penalties` | id, user_id REF, source_participation_id FK, status (ACTIVE, CONSUMED), created_at, consumed_at, consumed_event_id FK | Bir sonraki etkinliğe katılım engeli (EVT-10/11) |
| `event_polls`, `event_poll_options`, `event_poll_votes` | poll: id, event_id FK, question, closes_at; vote: poll_id, user_id REF, option_id | PK vote (poll_id, user_id) |
| `event_reminders` | id, event_id FK, remind_at, status | Zamanlayıcı (EVT-21) |
| `tournament_brackets` | id, event_id FK, format, data jsonb | EVT-18 (kapsam netleşince) |
| `outbox_events`, `inbox_events`, `idempotency_records` | §2.4 | |

### 3.13 hardware_db — hardware-service (Alper)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `hardware_brands` | id, name, normalized_name | UQ normalized_name |
| `hardware_products` | id, category (CPU, GPU, RAM, STORAGE, MOTHERBOARD, PSU, MONITOR, OTHER), brand_id FK nullable, model, normalized_model, specs jsonb, benchmark_score, release_year, status | UQ (category, brand_id, normalized_model); marka seçimi opsiyonel (HW-02) |
| `hardware_profiles` | id, user_id REF, name, device_type (DESKTOP, LAPTOP, OTHER), is_primary, visibility (PUBLIC, FRIENDS, PRIVATE), source (MANUAL, DESKTOP_SYNC), last_synced_at, deleted_at, version | Birden fazla bilgisayar (HW-04); ix (user_id) |
| `hardware_profile_components` | id, profile_id FK, category, product_id FK nullable, custom_name, quantity, details jsonb, source (MANUAL, DESKTOP_SYNC) | Katalogda olmayan parça `custom_name` ile |
| `hardware_sync_consents` | id, user_id REF, profile_id FK, granted_at, revoked_at | HW-07: otomatik sync kullanıcı iznine bağlı |
| `hardware_reviews` | id, product_id FK, user_id REF, rating, body, status (VISIBLE, HIDDEN, DELETED), created_at | Partial UQ (product_id, user_id) WHERE status<>'DELETED' |
| `hardware_review_reports` | id, review_id FK, reporter_id REF, reason, status | HW-13; UQ (review_id, reporter_id) |
| `hardware_campaigns` | id, product_id FK, title, url, discount_percent, starts_at, ends_at, source | HW-14 |
| `hardware_provider_refs` | id, product_id FK, provider, external_id, last_synced_at | HW-17; UQ (provider, external_id) |
| `outbox_events`, `inbox_events`, `idempotency_records` | §2.4 | |

### 3.14 recommendation_db — recommendation-service (Alper)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `user_taste_profiles` | user_id PK, genre_weights jsonb, platform_weights jsonb, updated_at | Favori/puan/tamamlanan oyunlardan türetilir (REC-02..05) |
| `user_hardware_snapshots` | user_id + profile_id PK, cpu_score, gpu_score, ram_mb, vram_mb, refreshed_at | hardware event'lerinden |
| `game_requirement_snapshots` | game_id PK, min_cpu_score, min_gpu_score, min_ram_mb, rec_cpu_score, rec_gpu_score, rec_ram_mb, refreshed_at | game-service'ten |
| `recommendation_runs` | id, user_id REF, type (GAME, HARDWARE_UPGRADE, PLAYER, FRIEND, EVENT, GRAPHICS_SETTINGS), algorithm (RULE_BASED, PYTHON_V1, ...), algorithm_version, filters jsonb, created_at | REC-18 geçmiş |
| `recommendation_items` | id, run_id FK, target_type, target_id REF, score numeric, rank, reasons jsonb, estimated_fps numeric nullable, compatibility (BELOW_MIN, MIN, RECOMMENDED, ABOVE) | REC-17 neden; REC-09 tahmin |
| `recommendation_feedback` | id, item_id FK, user_id REF, feedback (LIKE, DISLIKE, NOT_INTERESTED, ALREADY_OWN), created_at | UQ (item_id, user_id) |
| `assistant_rules` | id, topic (GAME, HARDWARE, LTZ_USAGE), rule_key, conditions jsonb, response_template_key, active, version | REC-22: Python algoritmaları + tanımlı şablonlar, **LLM yok** |
| `recommendation_metrics_daily` | date, type, impressions, clicks, positive_feedback, negative_feedback | REC-23; PK (date, type) |
| `inbox_events` | §2.4 | |

### 3.15 performance_db — performance-service (Alper)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `devices` | id, user_id REF, hardware_profile_id REF nullable, name, platform, status (PENDING_PAIRING, ACTIVE, REVOKED), credential_hash, paired_at, last_seen_at, revoked_at | PERF-01/02; cihaz kimlik bilgisi hash'li |
| `device_pairing_codes` | id, user_id REF, code_hash, expires_at, consumed_at | Kısa ömürlü, tek kullanımlık |
| `performance_sessions` | id, user_id REF, device_id FK nullable, game_id REF, game_title_snapshot, source (MANUAL, DESKTOP), status (ACTIVE, COMPLETED, ABANDONED), started_at, ended_at, graphics_preset, resolution, notes, version | PERF-03/04 |
| `performance_samples` | id, session_id FK, captured_at, metric (CPU_TEMP, GPU_TEMP, CPU_USAGE, GPU_USAGE, RAM_USED_MB, VRAM_USED_MB, DISK_READ_MBPS, DISK_WRITE_MBPS, FAN_RPM, FPS, FRAME_TIME_MS), value numeric, unit | PERF-05..12; ix (session_id, metric, captured_at); CHECK değer aralıkları |
| `performance_reports` | id, session_id FK UQ, user_id REF, avg_fps, p1_low_fps, max_cpu_temp, max_gpu_temp, avg_cpu_usage, avg_gpu_usage, summary jsonb, visibility, created_at | PERF-13 |
| `sync_batches` | id, device_id FK, idempotency_key, received_at, sample_count, status | **UQ (device_id, idempotency_key)** — PERF-17/18 |
| `idempotency_records`, `outbox_events`, `inbox_events` | §2.4 | |

> `performance_samples` hızlı büyür; MVP'de manuel/az veriyle başlar. Masaüstü senkronizasyonu açıldığında aylık **range partitioning** (`captured_at`) ve saklama süresi değerlendirilir.

### 3.16 admin_db — admin-service (Ortak)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `moderation_cases` | id, source_service, target_type, target_id REF, report_count, status (OPEN, IN_REVIEW, RESOLVED, REJECTED), assigned_to REF, created_at, resolved_at | UQ (source_service, target_type, target_id) WHERE status in ('OPEN','IN_REVIEW') |
| `moderation_actions` | id, case_id FK, actor_id REF, action (HIDE, REMOVE, RESTORE, WARN, SUSPEND, BAN), reason, apply_status (PENDING_APPLY, APPLIED, FAILED), created_at | Gerekçe zorunlu |
| `user_sanctions` | id, user_id REF, type (SUSPEND, BAN), reason, starts_at, ends_at, actor_id REF, revoked_at | auth-service'e komut olarak iletilir |
| `admin_permissions` | id, admin_user_id REF, permission | FR-ADM-08 rol ayrımı |
| `platform_stats_daily` | date PK, new_users, active_users, reviews, posts, events, reports | FR-ADM-05 |
| `audit_logs` | §2.4 | Append-only |
| `outbox_events`, `inbox_events` | §2.4 | |

### 3.17 notification_db — notification-service (Ortak)

| Tablo | Önemli sütunlar | Kısıtlar / not |
| --- | --- | --- |
| `notifications` | id, user_id REF, type, title_key, params jsonb, target_type, target_id, source_event_id, read_at, created_at | UQ (user_id, source_event_id) — tekrar bildirim yok; ix (user_id, read_at, created_at desc) |
| `notification_preferences` | user_id REF, type, channel (IN_APP, EMAIL), enabled | PK (user_id, type, channel) |
| `announcements` | id, title_tr, title_en, body_tr, body_en, audience, published_at, expires_at, created_by REF | FR-NOT duyurular |
| `announcement_reads` | announcement_id FK, user_id REF, read_at | PK (announcement_id, user_id) |
| `inbox_events` | §2.4 | |

## 4. İndeks ve sorgu kuralları

- Her REF sütunu sık sorgulanıyorsa indekslenir (`ix_<tablo>_user_id`).
- Liste endpoint'leri **keyset (cursor) sayfalama** tercih eder (`created_at, id`); offset sayfalama yalnız küçük listelerde.
- Metin arama: `pg_trgm` + GIN (oyun adı, kullanıcı adı, ilan, içerik başlığı). Extension migration ile açılır (`create extension if not exists pg_trgm;`) ve DB sahibinin izni gerekir.
- Sayaç sütunları (`like_count`, `participant_count`) aynı transaction'da güncellenir veya periyodik olarak yeniden hesaplanır; yarış koşulu için `version` veya atomik `update ... set x = x + 1`.
- Dinamik sıralama alanları allowlist ile kısıtlanır; SQL string birleştirme yapılmaz.

## 5. Saklama ve imha `[ÖNERİ]`

| Veri | Süre | Yöntem |
| --- | --- | --- |
| E-posta doğrulama kodu, sıfırlama token'ı, eşleştirme kodu | Süresi dolunca + 7 gün | Zamanlanmış silme |
| Revoke edilmiş refresh token | Süre bitimi + 30 gün | Zamanlanmış silme |
| `outbox_events` (SENT) | 7 gün | Zamanlanmış silme |
| `inbox_events` | 30 gün | Zamanlanmış silme |
| `idempotency_records` | 24 saat – 7 gün (`expires_at`) | Zamanlanmış silme |
| `mail_messages` (SENT) | 90 gün, değişkenler gönderimde temizlenir | Zamanlanmış silme |
| `security_events` | 1 yıl | Zamanlanmış silme |
| `audit_logs` | Hukuki karara bağlı (NFR-LEG) | Yalnız arşivleme |
| `performance_samples` | Kullanıcı silene kadar veya ürün kararıyla belirlenen süre | Partition drop |
| Silinen hesap verisi | AccountDeleted saga'sı | Silme/anonimleştirme |

## 6. Flyway stratejisi

- **Bağlayıcı karar:** [decisions/migrations.md](decisions/migrations.md). Her servis yalnız kendisine ayrılan sürüm aralığını kullanır; bu bölümle çelişirse o dosya geçerlidir.

  | Sahip | Servis → aralık |
  | --- | --- |
  | Alper | hardware V1–V10, recommendation V11–V20, performance V21–V30 |
  | Hamza | game V31–V40, library V41–V50, review V51–V60 |
  | Kerem | auth V61–V70, user V71–V80, mail V81–V90 |
  | Nisa | community V91–V100, content V101–V110, event V111–V120 |
  | Umut | social V121–V130, messaging V131–V140, lfp V141–V150 |
  | Ortak | notification V151–V160, admin V161–V170 (sorumlu geliştirmeden önce belirlenir) |

- Konum: `backend/services/<servis>/src/main/resources/db/migration/`.
- Ad: `V{NUMARA}__{aciklama}.sql` (ör. `V1__create_hardware_tables.sql`, `V31__create_game_tables.sql`, `V61__create_auth_tables.sql`). Numaralar servisin aralığı içinde artan sırayla kullanılır; aralık yetmezse yeni aralık ekipçe kararlaştırılır.
- Yeni migration eklemeden önce servisin mevcut migration geçmişi kontrol edilir; aynı serviste birden fazla geliştirici varsa numara koordine edilir.
- **Uygulanmış migration değiştirilmez**; düzeltme yeni migration ile yapılır.
- Her migration tek mantıksal değişiklik içerir; büyük veri dönüşümleri ayrı migration'dır.
- Kolon silme/yeniden adlandırma iki aşamalıdır (expand → contract): önce yeni kolon + uygulama uyumu, sonraki sürümde eski kolon kaldırılır.
- Seed/referans verisi (roller, kategoriler, türler) migration ile eklenir; DEV'e özel örnek veri production migration'larına girmez.
- `clean`, `repair`, `baseline`, `out-of-order` ve veri silen migration'lar **açık onay** ister (SECURITY.md §9).
- Entity değişikliği ile migration aynı değişiklikte gelir; `ddl-auto=validate` uyuşmazlığı başlangıçta yakalar.
- Testler gerçek PostgreSQL üzerinde migration çalıştırır (Testcontainers veya Compose PostgreSQL — yaklaşım servis sahibince belirlenir).

## 7. Yedekleme ve ortamlar

- DEV verisi `postgres` volume'ündedir; volume silme veya DB reseti açık onay gerektirir.
- Production için günlük `pg_dump` (DB başına) + düzenli geri yükleme testi `[ÖNERİ]`. Üretim verisi DEV'e maskesiz kopyalanmaz.
