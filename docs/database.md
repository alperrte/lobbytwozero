# LTZ (LobbyTwoZero) — Veritabanı Tasarımı

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

**Başlangıç migration kaynakları yeniden düzenlendi (2026-10-21, açık kullanıcı talimatı).**
Son açık numaralandırma kararıyla aktif zincir kesintisiz **V31–V40**: 10 dosya,
0 ALTER TABLE. Nihai oyun alanları/genel dil listesi/keşif indeksleri V31;
harici kimlikler V32; provider sync/retry V33; identity audit V34; admin yönetimi V35;
grant V36; geliştirici içerikleri V37; dil desteği/provenance V38; başarımlar V39;
özel feedback V40. Aynı numaralı eski DEV migration'larıyla anlamlar karıştırılmamalıdır.
Aşağıdaki tarihsel faz/sürüm atıfları bu ayrımı açıkça belirtir.
Fiziksel nihai şema değişmemiştir. **Güncel DEV (2026-10-21):** `ltz-dev` Compose ortamı
`down -v` ile sıfırlanıp mevcut kaynakla yeniden kuruldu; `game_db` yeni V31–V40 zinciriyle
(10 başarılı Flyway kaydı, V171–V173 yok) çalışıyor. Eski 13 migration geçmişi geri yüklenmedi.
Eski zincirle oluşmuş yerel bir `game_db` yeni kaynaklarla uyumsuzdur ve sıfırlanmalıdır.
Yetkili (JWT'li) canlı uçlar **NOT VERIFIED**'dır.
[Dosya haritası, testler ve tarihsel veri korumalı geçiş planı](../docs-Game%20Service/LTZ-HAM-GAME-Migration-Yeniden-Yapilandirma.md).

Tarihsel Faz 6 DEV dağıtımı (eski zincirde V172/V173, 2026-10-21; yeni aktif kaynaklarda V39/V40). Bu eski DEV geçmişi aynı gün ortam sıfırlanarak ortadan kalkmıştır:

- Tarihsel V172 / aktif V39 `game_achievement_definitions`: UUID PK, game_id FK CASCADE, code/title/
  nullable description, visible, version, UTC created_at/updated_at; UNIQUE(game_id,code)
  ve (game_id,visible,code,id) public sayfalama indeksi. Başarım tanımıdır, oyuncu ilerlemesi değildir.
- Tarihsel V173 / aktif V40 `game_developer_feedback`: UUID PK, game_id FK RESTRICT, author_id kullanıcı REF,
  type GENERAL/BUG_REPORT/EARLY_ACCESS ve status OPEN/IN_PROGRESS/RESOLVED CHECK,
  title/body, version ve UTC zamanlar. Yazar/tarih/id ve oyun/tür/durum/tarih/id indeksleri.
  Özel kayıt; kullanıcı/rol tablosu veya Review verisi kopyalanmaz. Durum olayları
  mevcut append-only game_admin_audits içinde içerik metni olmadan tutulur.
- Faz 6 dağıtımında V31–V40 ve V171 değiştirilmemişti. V172/V173 yalnız eklemelidir; eski kayıtlara içerik
  eklenmez. Dolu V171 -> V173 geçişi izole testte doğrulanır. (Bu eski zincirdeki "sonraki numara" tahsisi sonradan kaldırılmıştır; bkz. migrations.md.)

Game Service GAME-23 şeması (oyun alanları V31; ilişkili tablolar aktif V38, tarihsel V171): `games.metadata_language` (nullable) ve
`language_data_state` (UNKNOWN/EMPTY/INVALID/AVAILABLE), `game_language_supports`
(game_id+language_code PK, üç bağımsız UNKNOWN/SUPPORTED/UNSUPPORTED alanı),
`game_metadata_provenance` (game_id+field PK, kaynak, veri durumu, kayıt zamanı,
nullable doğrulama zamanı). İki tablo oyun FK'siyle CASCADE bağlıdır; satırların
eşzamanlı yönetimi mevcut games.version + oyun kilidiyle korunur. Eski
game_languages tablosu değiştirilmez. Eski verilere kaynak/doğrulama backfill'i
yapılmaz. LOCALIZATION mevcut manuel kilit allowlist'ine eklenir. Fiyat tablosu
oluşturulmadı; GAME-24 sağlayıcı izni/erişimi bekler.


**Tarihsel gelişim (eski numaralandırma — aktif zincir değildir):** GAME-08 V31, GAME-10 V32, GAME-16 V33, GAME-18 V34, GAME-21 V35, Faz 2 güncellik/retry altyapısı V36, Faz 3 keşif metadata/indeksleri V37, Faz 4 yönetim/geliştirici tabloları V38–V40, GAME-23 V171 ve Faz 6 V172–V173 eski 13 dosyalı zincirin numaralarıdır. Aşağıdaki "tarihsel Vxx" atıfları bu eski zincire, "aktif Vxx" atıfları bu bölümün başındaki yeni V31–V40 zincirine aittir; numaralar karıştırılmamalıdır (özellikle tarihsel V40 geliştirici içeriği, aktif V40 özel geri bildirimdir). V171–V173 yalnız bu eski zincirde kullanılmıştır, aktif migration değildir. V171–V190 ek aralığı ve V174–V190 rezervasyonu kaldırılmıştır; aktif V31–V40 aralığı doludur ve gelecekte yeni migration gerekirse numara aralığı ekipçe belirlenir. Gerçek detay sağlayıcısı henüz entegre edilmedi.

- `games`: UUID `id`, `title`, `normalized_title`, UTC `created_at`/`updated_at`, optimistic `version`.
- `catalog_terms`: UUID `id`, `kind` (GENRE/CATEGORY/TAG/PLATFORM), `code`, nullable TR/EN adlar, `version`; UNIQUE `(kind, code)`.
- `game_terms`: oyun ve terim FK'leri, bileşik PK. Tek sözlük tablosunda kavramlar `kind` ile ayrılır; API dört ayrı dizi döner.
- `game_developers`, `game_publishers`: oyun FK + şirket adı bileşik PK; birden fazla geliştirici/yayıncı desteklenir.
- `game_media`: UUID, oyun FK, tip, HTTPS URL, kaynak, sıralama ve version.
- `game_system_requirements`: UUID, oyun FK, platform, minimum/önerilen seviye, opsiyonel teknik alanlar/ham metin, version; UNIQUE `(game_id, platform, level)`.
- Aktif V31/V32: `games.origin`, `metadata_completeness`, `status` V31'de; `game_external_refs` UUID, oyun FK, provider/external_id, last_synced_at, version ve UNIQUE `(provider, external_id)` V32'de. Köken yerelde saklamayla değişmez.
- Aktif V31/V33: `normalized_title` 1024 karakter ve önek araması B-tree pattern indeksi V31'de; `game_provider_sync_state` V33'te sağlayıcı başına tek cursor, tur modu, durum, watermark, sayım ve version. Sayfa ve checkpoint tek transaction; provider state satırı pessimistic lock ile korunur.
- Aktif V32/V34: Steam AppID başında sıfır olmayan pozitif uint32 metni olmak zorundadır; CHECK ve alias tekilliği V32'de korunur. V34 `game_identity_audits` operatör REF, kaynak/hedef oyun FK, referans FK, kanıt ve UTC zaman içerir; trigger UPDATE/DELETE işlemlerini reddeder.
- Aktif V31 (tarihsel V35): `games.game_type` varchar + CHECK, varsayılan UNKNOWN; aktif katalog için tür/başlık indeksi. MAIN_GAME varsayılan arama kapsamıdır; kesin türü olmayan kayıtlar oyun diye varsayılmaz.
- Tarihsel V36 (artık oyun alanları V31, sync alanları V33): `game_provider_sync_state.failure_count` ve `next_attempt_at` kalıcı retry/backoff durumudur. `games.description` (16000), `release_date`, `details_fetched_at`, `details_expires_at`, `details_next_attempt_at`, `details_failure_count`, `details_last_error_code` detay ve güncellik altyapısıdır. Zamanlar timestamptz/UTC, sayaçlar negatif olamaz. Güncellik, son başarılı kısmi detay yanıtına aittir; alan bazlı değildir.
- Aktif V31 (tarihsel V37): nullable `early_access`; `game_languages` (oyun FK + dil kodu bileşik PK) ve dil arama indeksi. `editor_rank` (1–100, deferred UNIQUE), `popularity_updated_at`, `editor_actor_id` REF ve `editor_evidence` en son editör seçimini tutar; değişiklik geçmişi/audit akışı değildir. Başlık/tarih için ACTIVE MAIN_GAME kısmi indeksleri ve kapak seçimi indeksi vardır.
- Aktif V35 (tarihsel V38): `game_field_overrides` (UNIQUE `(game_id, field)`; LOCALIZATION dahil alan allowlist CHECK) EXTERNAL oyunlarda manuel düzeltilmiş sağlayıcı alanlarını kilitler; senkronizasyon bu alanları yazmaz. `game_admin_audits` yönetim eylemlerinin (oyun, yetki, editör, moderasyon) append-only kaydıdır: aktör REF, rol, eylem allowlist CHECK, gerekçe (varsa en az 8 karakter), değişen alan adları (içerik metni değil); trigger UPDATE/DELETE'i reddeder, `game_id` FK `RESTRICT`'tir.
- Aktif V36 (tarihsel V39): `game_developer_grants` oyun bazlı geliştirici yetkisi. `developer_user_id`/`granted_by`/`revoked_by` başka servise ait REF'tir (FK yok). Aynı geliştirici/oyun için tek aktif kayıt kısmi UNIQUE indeksle (`revoked_at is null`) korunur; geri alma kaydı silmez, `revoked_*` alanlarını doldurur (CHECK ile ikisi birlikte dolu/boş).
- Aktif V37 (tarihsel V40): `game_developer_contents` (oyun başına tek açıklama), `game_developer_media` (IMAGE/VIDEO/DEMO, yalnız HTTPS bağlantı CHECK) ve `game_update_notes`. Katalog tabloları (`games.description`, `game_media`) değiştirilmez; sağlayıcı verisi ve geliştirici içeriği birbirini ezmez. Ortak alanlar: `author_id` REF, `status` (VISIBLE/HIDDEN/DELETED; açıklamada yalnız VISIBLE/HIDDEN), moderasyon alanları, version. Yazma yetkisi uygulama katmanında rol + oyun yetkisiyle denetlenir.
- Keşif sayfası/count/ilişkileri aynı REPEATABLE_READ görünümünde toplu SQL ile okunur; puan projeksiyonu veya review_db erişimi yoktur.
- Yalnız servis içi FK kullanılır; fiyat bilgisi sonraki fazdadır.

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
| `friend_requests` | id, sender_id REF, receiver_id REF, status (PENDING, ACCEPTED, REJECTED, CANCELLED), created_at, responded_at, version | CHECK sender_id <> receiver_id; CHECK (status='PENDING') = (responded_at IS NULL); yönden bağımsız partial UQ (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id)) WHERE status='PENDING' (`uq_friend_requests_pending_pair`) — **V121 ile uygulandı** |
| `friendships` | user_low_id REF, user_high_id REF, created_at | PK (user_low_id, user_high_id); CHECK low < high (tek satır/çift); sıralama uygulamada PostgreSQL uuid sırasıyla (işaretsiz, baytsal) yapılır — `java.util.UUID.compareTo` kullanılmaz — **V121 ile uygulandı** |
| `user_blocks` | blocker_id REF, blocked_id REF, created_at | PK (blocker_id, blocked_id); CHECK blocker_id <> blocked_id; engel yönlüdür (iki yön ayrı kayıt), kısıtlama iki yönde uygulanır; ix (blocked_id), ix (blocker_id, created_at desc, blocked_id desc) — **V124 ile uygulandı** |
| `friend_invite_codes` | id, user_id REF, code (10 karakter Crockford base32), created_at, expires_at, max_uses, used_count, revoked_at, version | UQ code; CHECK code biçimi, expires_at > created_at, 0 ≤ used_count ≤ max_uses; kullanıcı başına tek etkin kod: partial UQ (user_id) WHERE revoked_at IS NULL (`uq_friend_invite_codes_active_user`) — **V123 ile uygulandı** |
| ~~`social_privacy_settings`~~ | — | **Oluşturulmadı (SOC-12 kararı):** arkadaş listesi görünürlüğü user-service'te (`privacy_settings.friends_visibility`) yönetilir; Social bu tercihi saklamaz, `FriendsVisibilityPort` ile okur ve okuyamazsa fail-closed (PRIVATE) davranır. `who_can_send_requests` gereksinimlerde yoktur; ihtiyaç doğarsa ayrı karar ve migration gerekir |
| `outbox_events` | §2.4 — **V122 ile uygulandı** (olaylar: `FriendRequestSent`, `FriendRequestAccepted`, `FriendshipChanged`; bkz. `docs/api.md` → SOCIAL → Giden olay sözleşmeleri) | `inbox_events` Social bir olay tüketmediği için henüz yok |

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
| `forums` | id, game_id REF nullable, slug, title, description, created_at | Oyun forumu/konu (COM-19) |
| `posts` | id, author_id REF, forum_id FK nullable, game_id REF nullable, body, status (VISIBLE, HIDDEN, DELETED), repost_of_id FK nullable, like_count, comment_count, created_at, updated_at, version | Repost orijinale bağlı (COM-20) |
| `post_images` | id, post_id FK, storage_key, mime_type, size_bytes, sort_order | |
| `post_likes` | post_id FK, user_id REF, created_at | PK (post_id, user_id) |
| `comments` | id, post_id FK, author_id REF, parent_comment_id FK nullable, body, status, created_at, updated_at | Yanıtlar (COM-09) |
| `community_reports` | id, target_type (POST, COMMENT), target_id, reporter_id REF, reason, status, created_at | UQ (target_type, target_id, reporter_id) |
| `outbox_events`, `inbox_events` | §2.4 | |

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

**Auth entegrasyonu — 2026-10-24:** V1–V7 checksum'ları korunarak
`V8__add_hardware_account_erasure.sql` eklenir; toplam 17 tablo. V9–V10 boştur.
Yeni `hardware_account_erasure_fences(account_hash varchar(64) PK, expires_at timestamptz)`
yalnız SHA-256 hesap özeti ve expiry tutar; UUID/credential içermez. Teknik fence tablosu
entity değildir; hesap bazlı transaction advisory lock + atomik upsert ile yönetilir.
17 dakika etkin, dakikalık süpürmede fiziksel silinir (normal scheduler çalışırken en çok
yaklaşık bir ek dakika; scheduler kapalıysa bir sonraki çalışmada). Anonim veri sayılmaz.
Kerem/Auth sahibiyle mutabık kullanıcı onayı kaydı:
[HW-AUTH-02](../docs-hardware/integration/auth/auth-integration-02.md).
V8 audit DELETE istisnasını yalnız transaction-local hesap bağlamında ilgili actor/hedef
satırlarına sınırlar; UPDATE/TRUNCATE ve ilgisiz satırların silinmesi reddedilir.
`erase_hardware_account(uuid)` yalnız Hardware verisini temizler; başka DB'ye erişmez.
Fonksiyon bounded paket işler: 20 profil/20 yorum; audit/outbox/rapor türü başına
1000 satır. Paket tamamlanır ama hesap bitmezse commit + 503; son paket commit'i 204.
Actor/reporter/outbox aggregate ve JSON owner için V8 lookup indeksleri bulunur.
Katalog/marka/kampanya ve ilgisiz kullanıcı kayıtları korunur. Kişisel outbox/rapor/audit,
profil aggregate'i ve hesap rate bucket'ları kaldırılır. Cihaz bucket anahtarı artık
`sync-device:{ownerId}:{deviceId}` biçimindedir; erasure eski bağlı cihaz bucket'ını da temizler.
Bu ileri migration bir DB reseti gerektirmez; canlı ortama ajan tarafından uygulanmadı.
Devamındaki 2026-10-21 envanteri tarihsel başlangıç temizliği kaydıdır.

**Güncel geliştirme şeması — 2026-10-21:** Kullanıcının “7 ilişkili grup” onayıyla
eski V1–V10 zinciri yeniden düzenlendi. Boş DB'ye uygulanan aktif dosyalar:

| Migration | Oluşturduğu tablolar |
|---|---|
| V1__create_hardware_profiles_and_privacy.sql | hardware_profiles, hardware_profile_field_visibility, hardware_profile_category_visibility |
| V2__create_hardware_catalog.sql | hardware_brands, hardware_products, hardware_product_brands, hardware_catalog_audit |
| V3__create_hardware_profile_components.sql | hardware_profile_components |
| V4__create_hardware_campaigns.sql | hardware_campaigns |
| V5__create_hardware_reviews.sql | hardware_reviews, hardware_review_audit, hardware_write_limits |
| V6__create_hardware_review_reports_and_outbox.sql | outbox_events, hardware_review_reports |
| V7__create_hardware_desktop_sync.sql | hardware_sync_consents, hardware_sync_receipts |

16 tablo son sütun/default/constraint'leriyle doğrudan CREATE TABLE içinde kurulur;
ALTER TABLE/INDEX veya rename/drop adımı yoktur. 93 named constraint, 51 toplam indeks,
2 immutable audit trigger/fonksiyonu eski zincirle izole PostgreSQL 17.11'de eşdeğerdir.
Bu başlangıç tesliminde V8–V10 boştu; güncel V8 yukarıda kayıtlıdır.
Eski Flyway history ile yükseltme desteklenmez; DB sıfırlamasını kullanıcı yapar.
[Karar ve kanıt](../docs-hardware/progress/hardware-migration-cleanup.md).

**Aşağıdaki eski V1–V10 numaraları, dosya adları ve test sayıları görevlerin teslim
tarihçesidir; güncel migration envanteri yukarıdaki V1–V7 tablosudur.**

**LTZ-ALP-HW-01 uygulama durumu (2026-10-21):**
`V1__create_hardware_profiles.sql` eklendi; SQL ve constraint'ler izole PostgreSQL 17
üzerinde doğrulandı. V1/V2 Flyway ve JPA validate testleri de PASS; canlı DB'ye uygulanmadı.
Gerçek ilk tablo `hardware_profiles`: `id uuid`, `user_id uuid` (dış REF),
`name varchar(100)`, `device_type varchar(32)` (DESKTOP/LAPTOP/OTHER),
`source varchar(32)` (yalnız MANUAL), `version bigint`, `created_at/updated_at timestamptz`.
Ad/tür/kaynak/sürüm CHECK'leri ve `(user_id, created_at, id)` indeksi vardır.
`V2__create_hardware_components_and_selection_catalog.sql` HW-02 için eklendi (test PASS):
`hardware_products` genel modelleri `(category, normalized_model)` ile markadan bağımsız
tekilleştirir. `hardware_brands` ve `hardware_product_brands` opsiyonel üretici ilişkisidir;
ürün tablosunda nullable marka üzerinden tekillik kurulmaz. Katalog yönetimi/arşiv ve
arama HW-10/HW-11 ile aşağıda uygulandı.
`hardware_profile_components`: profile_id (CASCADE FK), opsiyonel product_id/brand_id,
category, model_name/brand_name snapshot, quantity 1..128, source=MANUAL, version, UTC zamanları.
Ürün/kategori ve ürün/marka bileşik FK'leri vardır. Parent profil kilidi ve profileVersion
aynı profilin paralel mutasyonlarını korur. Sekiz sınırlı teknik alan (capacity_gb,
clock_mhz, core_count, power_watts, memory_type, socket, interface_type, form_factor)
embeddable olarak eşlenir; veri yoksa null. JSON serbest özellik alanı kullanılmaz.
Profil silme use-case'i yalnız owner + güncel profileVersion ile hard delete uygular;
bağlı bileşenler CASCADE ile silinir, katalog/marka ve diğer profiller korunur.
İstatistikler owner filtresiyle DB'de toplanır; kayıt ve adet ayrıdır. Yalnız kaydedilen
RAM/depolama kapasitesi adetle çarpılır, kapasitesi bilinmeyen adetler ayrıca bildirilir.
Aşağıdaki genişletilmiş eski tasarım **öneridir**. Gerçek kampanya şeması için V5,
gizlilik için V6/V7, yorum ve rapor için aşağıdaki V8/V9 uygulama tanımları
geçerlidir; sync için aşağıdaki V10 uygulama tanımı geçerlidir.
JPA validate ve open-in-view=false korunur.

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

**LTZ-ALP-HW-02 / HW-10 geliştirmesi:** V3 katalog tablolarına ACTIVE/ARCHIVED
durumu ve sürüm CHECK'leri, aktif ad/model/kategori sayfalama indeksleri ekler.
Mevcut satırlar ACTIVE kalır; V1/V2 değiştirilmez. Silme yerine arşivleme yapılır.
Ürün/marka bağı kullanılan profil bileşeninden kaldırılamaz (mevcut FK → 409).
Yeni seçimde arşivli ürün/marka reddedilir; mevcut profil snapshot'ları korunur.

`hardware_catalog_audit`: id UUID PK, actor_id dış UUID REF, action CHECK,
target_id UUID (hardware kaydına mantıksal referans), reason varchar(500),
created_at timestamptz. UPDATE/DELETE/TRUNCATE trigger ile engellenir;
audit ve iş mutasyonu aynı transaction'dadır. Aktör/zaman indeksi ve transaction
advisory kilidiyle aktör başına 60 başarılı yönetim mutasyonu/dakika sınırı uygulanır.
Limit aşımı tüm mutasyonu geri alır. V3 PostgreSQL 17.11/Flyway/JPA validate
90 testte PASS; canlı DB'ye uygulanmadı.
HW-11 V4 arama migration'ı ACTIVE ürünlerde kategori ile kapasite/frekans/çekirdek/güç
aralıkları için dört partial indeks ekler. Teknik alanlar typed; bilinmeyen değer
filtreyi sağlamaz. LIKE alt metin araması literal escape edilir; extension/harici
arama motoru eklenmedi. V4 ve filtreler PostgreSQL 17/Flyway/JPA ile 117 testte PASS;
indeks varlığı doğrulandı; üretim verisi üzerinde sorgu süre hedefi ölçülmedi.

HW-14 V5 geliştirmesi: `hardware_campaigns` (id UUID PK, product_id gerçek
hardware_products FK, title/source_name/source_url, original_price/sale_price
numeric(12,2), currency char(3), starts_at/ends_at/observed_at/created_at/updated_at
timestamptz, ACTIVE/ARCHIVED status, version bigint). CHECK indirimli fiyatın
pozitif ve orijinalden küçük olmasını, pencere sırasını, gözlem <= güncelleme ve
durum/sürüm aralıklarını korur. Aktif zaman ve ürün/zaman partial indeksleri vardır.
Yalnız yetkili kişinin kaynak/gözlem bilgili yerel kayıtları; harici çağrı yoktur.
Ürün/kampanya arşivlenince okuma filtresi gizler, FK/kayıtlar silinmez.
V1–V5 PostgreSQL 17.11/Flyway/JPA validate ve son modül 134 test PASS;
canlı DB'ye uygulanmadı.

**LTZ-ALP-HW-03 / HW-08:** V6 hardware_profiles.visibility varchar(16) NOT NULL
DEFAULT PRIVATE + PRIVATE/FRIENDS/PUBLIC CHECK ekler. hardware_profile_field_visibility
(profile_id,field_name) ve hardware_profile_category_visibility (profile_id,category)
bileşik PK'li collection tablolarıdır. Her ikisi profile CASCADE FK taşır;
enum değerleri CHECK ile sınırlanır. Collection'lar bağımsız aggregate değildir;
version hardware_profiles üzerindedir, parent pessimistic lock ve profileVersion
bütün rule/component/profil değişikliklerini birlikte korur. Eksik rule PRIVATE.
Component (profile_id,category,created_at,id) indeksi görünür kategori sayfalamasını destekler.
V1–V5 değişmedi; V6 izole PostgreSQL 17/Flyway/JPA validate testlerinden geçti.
V7__name_hardware_privacy_constraints.sql yalnız V6'nın otomatik constraint ve
indeks adlarını §2.2 standardına uyarlar; veri/erişim kuralı değişmez. Test edilmiş
V6 yeniden yazılmadı. İlk Sonar koşusunun 192 başarılı backend testi sırasında
V1–V7 PostgreSQL/Flyway/JPA validate PASS; Sonar kod bulguları ayrı kalite kaydındadır.
Canlı DB'ye migration uygulanmadı. HW-09 ve HW-17 yeni tablo eklemez;
batch parametrik array unnest/LATERAL sayfalaması yalnız doğrulanmış owner profil ID'leriyle çalışır.
User/Social gerçek global gizlilik/ilişki verisi bu DB'ye kopyalanmaz; güvenilir policy
portu onaylı DEFERRED_CONTRACT durumunda üretimde deny eder.

**HW-12 / V8 (2026-10-21):** hardware_reviews gerçek şeması: id/product_id (gerçek FK),
user_id (dış REF), nullable body/rating/body_hash, VISIBLE/HIDDEN/DELETED status,
version, created_at/updated_at/deleted_at. Rating 1..5 veya null. DELETED içerik/puan/hash
null ve deleted_at dolu; diğer durumlarda geçerli body/hash ve deleted_at null CHECK'i.
Silinmemiş (product_id,user_id) ve (user_id,body_hash) partial unique indeksleri;
ürün/status ve yazar/status sayfa indeksleri. DTO'da yazar kimliği/IP/hash yok.
hardware_review_audit iş mutasyonuyla atomik, UPDATE/DELETE/TRUNCATE trigger ile
append-only; body/ham IP saklamaz. hardware_write_limits fixed-minute actor/socket-peer
bucket'ları için composite PK ve TTL indeksidir; ayrı bir job iki dakikayı aşan
bucket'ları dakikada bir temizler (normalde yaklaşık 2–3 dakika tutulur).
HW-12 alt görev verify: 206 PASS, V1–V8 PostgreSQL 17/Flyway/JPA validate PASS;
V8 canlı DB'ye uygulanmadı.

**HW-13 / V9:** hardware_review_reports: id UUID, review_id gerçek FK, reporter_id
dış REF, reason CHECK, details varchar(500), outbox_event_id FK ON DELETE SET NULL,
version bigint ve UTC created_at. (review_id,reporter_id) ve outbox_event_id tekil.
Merkezi Admin case durumu kopyalanmaz. Nullable event bağlantısı, ileride teknik
outbox purge edilirken yerel raporu korur; status/teslim bilinmiyor olarak sunulur.
outbox_events ortak §2.4 alanlarını kullanır; aggregate HARDWARE_REVIEW, v1 tipleri
ContentReported/HardwareReviewModerated, jsonb object <=4 KiB, PENDING/SENT/FAILED
CHECK'leri. Yazıcı PENDING/FAILED toplamını transaction advisory lock ile 10000'de
sınırlar; kapasite aşımı 503 ve iş mutasyonu/audit/quota ile tümden rollback.
V9 immutable audit'in izinli action CHECK'ini genişletir; V8 yeniden yazılmadı.
239 testte PostgreSQL 17/Flyway V1–V9/JPA PASS; canlı DB'ye V8/V9 uygulanmadı.
Gerçek consumer sözleşmesi olmadığından relay/teslim/SENT işlemi uygulanmadı.
SENT retention ve gerçek consumer E2E sonraki mutabakatla ele alınacaktır.

**HW-07 / V10 (2026-10-21):** hardware_profile_components'a nullable
sync_component_key UUID eklendi. Source CHECK artık MANUAL + null key veya
DESKTOP_SYNC + dolu key + null product_id/brand_id ister; profil/key partial unique
indeksi aynı tarama bileşenini tekilleştirir. Manuel PUT kaynak/key'i MANUAL/null
yapar. Profil source=MANUAL oluşturulma kökenidir, sync bunu değiştirmez.

hardware_sync_consents: id UUID PK, profile_id UNIQUE gerçek CASCADE FK,
device_id dış REF, generation UUID, GRANTED/REVOKED status, granted_at/revoked_at,
last_sequence/last_captured_at/last_synced_at, version ve UTC created_at/updated_at.
Status/zaman/sequence metadata bütünlüğü CHECK'lerle korunur; device indeksi vardır.
Her yeni grant generation ve sıra metadata'sını sıfırlar; eski receipt generation'ları
yerel state'e FK değildir. Aktör owner'ı parent profilden gelir; cihaz ID'si yalnız
güvenilir policy ve ayrı typed principal ile doğrulanabilir. Dış cihaz verisi bu
DB'de hesap/credential sistemi olarak kopyalanmaz.

hardware_sync_receipts: id UUID, profile CASCADE FK, UUID idempotency_key/generation,
sequence, request_hash SHA-256, yalnız asgari metadata içeren <=4 KiB response_body
jsonb, version, created_at/expires_at. UNIQUE (profile_id,idempotency_key) ve
(profile_id,generation,sequence); hash/sequence/expiry/JSON CHECK, expiry indeksi.
Profil başına 500 canlı receipt/24 saat; parent kilidi bütçeyi ve uygulamayı seri
hale getirir. Saatlik job süresi dolan kayıtları temizler (yaklaşık 24–25 saat).
Kalıcı last_sequence receipt silinse de eski sırayı reddeder. Profil silme iki yeni
tabloyu ve bileşenleri CASCADE temizler; başka profil/cihaz kayıtları etkilenmez.

HW-06 preview donanım/consent kaydetmez. HW-07 snapshot/state/version/receipt tek
transaction'dır, yalnız DESKTOP_SYNC kümesi değiştirilir; MANUAL/gizlilik korunur.
Mevcut hardware_write_limits kısa TTL altyapısı yeni user/device/peer bucket'larını
da taşır. V1–V10 gerçek PostgreSQL/Flyway/JPA ve 269 mevcut regresyonda PASS;
yeni davranışların final test kanıtı HW-05 kalite kaydında güncellenir. V10 artık
değiştirilmez kabul edilmişti; sonraki açık geliştirme temizliği onayı bu geçmiş
zinciri yukarıdaki V1–V7 envanteriyle değiştirdi.
Canlı DB'ye V10 uygulanmadı. Gerçek Auth/PERF cihaz kontratı ve desktop E2E
onaylı DEFERRED_AUTH / DEFERRED_CONTRACT / NOT_VERIFIED; production policy deny.

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
