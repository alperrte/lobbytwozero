# EVT-01 — Etkinlik oluşturma: kaynaklar, açık kararlar ve varsayımlar

> Durum: **IN PROGRESS, doğrulama bekliyor.** Kod ve testler yazıldı; testler, Flyway migration (V111) ve `ddl-auto=validate` **çalıştırılmadı**. Bu belge hangi kuralın nereden geldiğini ayırır: gereksinim, öneri (açık karar) veya geliştirici varsayımı.
> Son güncelleme: 2026-11-09 · Sorumlu: Nisa · Endpoint kaydı: [docs/api.md](../../docs/api.md) → EVENT

## 1. Gereksinimden / belgeden gelenler

| # | Kural | Kaynak |
| --- | --- | --- |
| G-1 | Yetkili kullanıcı yeni etkinlik oluşturabilir. | `LTZ_Servis_Icerikleri_Nisa.md` EVT-01 |
| G-2 | Etkinlik tarih, açıklama ve katılım bilgisi içerir; turnuva, oyun gecesi, topluluk oylaması vb. oluşturulabilir. | `LTZ Gereksinim Dokümanı` FR-CONT-05 |
| G-3 | `events` tablosu: `organizer_id` (REF), `type` (TOURNAMENT, GAME_NIGHT, COMMUNITY, OTHER), `title`, `description`, `starts_at`, `ends_at`, `status` (PLANNED, ACTIVE, COMPLETED, CANCELLED), `version`; `CHECK ends_at > starts_at`. | `docs/database.md` §3.12 |
| G-4 | Yeni etkinlik `PLANNED` başlar. | `docs/architecture.md` §8 |
| G-5 | Kullanıcı kimliği yalnız doğrulanmış token'dan alınır; id'ler untrusted; 401/403 ayrımı; hata yanıtları `ProblemDetail`; bilinmeyen JSON alanları reddedilir. | `docs/SECURITY.md` §4, §10 |
| G-6 | Kimlik: `sub` = hesap kimliği, roller `roles` claim'inde (`PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`); token servis tarafından JWKS ile doğrulanır (`typ=at+jwt`, RS256, `iss`, `aud=ltz-api`). | `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` §3–§4 |
| G-7 | Migration numarası Event aralığından alınır (V111–V120). | `docs/decisions/migrations.md` |
| G-8 | Kullanıcı içeriği (etkinlik açıklaması dahil) güvenilmezdir: sunucuda uzunluk, boşluk ve karakter kontrolü. | `docs/SECURITY.md` §5 |

## 2. Kararlar (servis sahibi Nisa, 2026-11-09)

Üç karar servis sahibi tarafından verildi. Kaynak: Nisa'nın 2026-11-09 tarihli kararı (bu belgeye işlenmiş hali; ayrı bir ADR yok). KD-1 koda uygulandı; KD-2 ve KD-3 kararı kayıtlıdır, **kodu ilgili görevlerde (EVT-02, hesap silme ucu) yazılacaktır** ve şu an uygulanmış değildir.

### KD-1 — Etkinliği kim oluşturabilir? — **KARAR VERİLDİ, UYGULANDI (doğrulama bekliyor)**

- **Karar:** Kayıtlı oyuncular (`PLAYER`), `MODERATOR` ve `ADMIN` etkinlik oluşturabilir.
- **Kaynak durumu:** Belgelerde bağlayıcı bir rol kuralı yoktu; Auth'un `docs-auth/entegrasyon/event-service.md` önerisi (yalnız `ADMIN`/`MODERATOR`) bu kararla **değiştirildi**. Gereksinim dokümanının izlenebilirlik tablosu ("İçerik / Etkinlik" → `AKT-02` Kayıtlı Oyuncu, `AKT-06` Sistem Yöneticisi) bu kararla uyumludur.
- **Pratik anlamı:** Auth'ta `PLAYER` temel roldür ve geri alınamaz (`BASE_ROLE_REQUIRED`); her hesap `PLAYER` taşır. Dolayısıyla kimliği doğrulanmış her aktif hesap (ek olarak `CONTENT_CREATOR`/`GAME_DEVELOPER` rolleri olsa da) etkinlik oluşturabilir. Rolsüz veya yalnız tanımsız/ek rolle gelen token 403 alır.
- **Uygulama:** `infrastructure/security/EventRoles.java` (`EVENT_CREATORS = {PLAYER, MODERATOR, ADMIN}`); URL düzeyinde (`SecurityConfig`) ve serviste (`CurrentUserPort.canCreateEvents`) uygulanır.
- **Kapsam:** Yalnız **oluşturma**. Güncelleme/iptal (EVT-14, EVT-15) için "sahip veya yetkili kullanıcı" kuralı ayrıca karara bağlanır (öneri: sahip + `MODERATOR`/`ADMIN`); bu karar onlara otomatik uzanmaz.
- **Risk notu:** Oluşturma herkese açıldığı için kötüye kullanım yüzeyi büyüdü (spam etkinlik, uygunsuz başlık/açıklama). EVT-01'de oran sınırı, mükerrer önlemi ve raporlama/moderasyon yoktur (bkz. VR-8).

### KD-2 — "Oylama" modeli — **KARAR VERİLDİ, EVT-02'DE UYGULANDI (doğrulama bekliyor)**

- **Karar:** Oylama, etkinliğe bağlı bir özellik değil **ayrı bir etkinlik türüdür** (`POLL`). Oy verme davranışı EVT-20'de geliştirilir.
- **Uygulama:** `EventType.POLL` ve V112 migration'ı ile `ck_events_type`'a eklendi. Ayrıntı: [evt-02-etkinlik-turleri.md](evt-02-etkinlik-turleri.md). V111 değiştirilmedi.
- **Not:** EVT-01 sırasında yazılan "yalnız dört değer" ifadeleri (G-3, VR-5, §4 tablosu) V111'in tarihsel halidir; güncel değer kümesi beş değerdir.

### KD-3 — Hesabı silinen düzenleyicinin etkinlikleri — **KARAR VERİLDİ, UYGULANMADI (hesap silme ucu)**

- **Karar:**
  1. Gelecekteki ve aktif etkinlikler iptal edilir (`PLANNED`/`ACTIVE` → `CANCELLED`).
  2. Tamamlanan etkinlikler geçmişte korunur.
  3. Düzenleyicinin kullanıcı bağlantısı kaldırılır; gösterimde "Silinmiş kullanıcı" yazılır.
- **Uygulama gereksinimleri (henüz yok):** ayrıntı ve açık noktalar [hesap-silme-entegrasyonu.md](hesap-silme-entegrasyonu.md) §3'te. Özet: `organizer_id` şu an `NOT NULL`; bağlantının kaldırılması için nullable yapan yeni migration (V112+) gerekir; iptal `cancelled_reason` kolonunu (EVT-15) ve katılımcı bildirimini (EVT-22) etkiler.

## 3. Geliştirici varsayımları (gereksinimde bu değerler yazmaz)

| # | Varsayım | Gerekçe | Nerede uygulanıyor |
| --- | --- | --- | --- |
| VR-1 | Başlık en çok **120** karakter, boş/yalnız boşluk olamaz, kontrol karakteri (satır sonu dahil) içeremez. | SECURITY.md §5 sınır ister ama sayı vermez; community `Forum.TITLE_MAX_LENGTH=120` ile hizalı. | `CreateEventRequest`, `Event.TITLE_MAX_LENGTH`, DB `varchar(120)` + `ck_events_title` |
| VR-2 | Açıklama **zorunlu**, en çok **4000** karakter; satır sonu ve sekme serbest, diğer kontrol karakterleri yasak. | "açıklama içermelidir" (G-2) zorunluluğu verir; 4000 sınırı varsayımdır. | `CreateEventRequest`, `Event.DESCRIPTION_MAX_LENGTH`, DB `text` + `ck_events_description` |
| VR-3 | `startsAt` **şimdiden sonra** olmalıdır; geçmişte başlayan etkinlik oluşturulamaz. | Gereksinim yalnız "başlangıç/bitiş kontrolü" der; geçmiş etkinliğin `PLANNED` olması anlamsızdır. Gelecek sınırı (en geç ne zaman) ve azami süre **yoktur**. | `Event.schedule` (DB'de uygulanamaz: zamana bağlı) |
| VR-4 | `endsAt` > `startsAt`; eşitlik geçersiz. | G-3 `CHECK ends_at > starts_at`. | `Event.schedule` + `ck_events_schedule` |
| VR-5 | `type` **zorunlu** (varsayılan atanmaz). | `database.md` kolonu `not null`; EVT-02 tür-özel kurallar ekler. | `CreateEventRequest` (`@NotNull`), DB `ck_events_type` |
| VR-6 | Zamanlar UTC `Instant` (ISO-8601) olarak alınır ve **milisaniyeye kırpılır**. | PostgreSQL `timestamptz` mikrosaniye saklar; kırpma olmadan yalnız nanosaniyede farklı iki zaman domain kuralını geçip `ck_events_schedule`'ı ihlal ederdi. | `Event.schedule` |
| VR-7 | Başlık ve açıklama kaydedilmeden önce `strip()` ile kırpılır. | Boşluk tutarlılığı. | `EventService` |
| VR-8 | Mükerrer oluşturma önlemi (idempotency anahtarı, aynı sahip + başlık + zaman tekilliği) ve oran sınırı **yok**. | SECURITY.md §12 idempotency'yi "etkinliğe katılım" için ister, oluşturma için değil; yeni iş kuralı uydurulmadı. Çift tıklama iki kayıt üretebilir. **KD-1 ile oluşturma tüm kayıtlı oyunculara açıldığından risk artmıştır** (spam etkinlik); SECURITY.md §12 "ilan/gönderi oluşturma" için ayrı risk profili/oran sınırı ister. | — (NOT IMPLEMENTED; öneri: kullanıcı başına oran sınırı/aktif etkinlik kotası, karar gerekir) |
| VR-9 | Yanıtta `organizerId` döner. | Hesap kimliği (UUID); e-posta/ad yok. | `EventResponse` |

## 4. API ↔ domain ↔ veritabanı tutarlılığı

| Alan | API (`CreateEventRequest`) | Domain (`Event`) | DB (V111) | Sonuç |
| --- | --- | --- | --- | --- |
| `type` | `@NotNull`, enum | `EventType` | `varchar(32)` + `ck_events_type` (aynı 4 değer) | Tutarlı |
| `title` | `@NotBlank`, `@Size(max=120)`, kontrol karakteri yasak | `strip()` sonrası kaydedilir | `varchar(120)`, `ck_events_title` (btrim 1–120) | Tutarlı. Kontrol karakteri yalnız API'de denetlenir (DB'de yok). |
| `description` | `@NotBlank`, `@Size(max=4000)`, `\n\r\t` dışı kontrol karakteri yasak | `strip()` sonrası kaydedilir | `text`, `ck_events_description` (btrim 1–4000) | Tutarlı. Kontrol karakteri yalnız API'de. |
| `startsAt`/`endsAt` | `@NotNull`, `Instant` | gelecekte + `end > start`, ms'ye kırpılı | `timestamptz`, `ck_events_schedule` | Tutarlı (kırpma ile, VR-6) |
| `status` | istekte **yok** (bilinmeyen alan 400) | her zaman `PLANNED` | `default 'PLANNED'`, `ck_events_status` (4 değer) | Tutarlı |
| `organizerId` | istekte **yok** | token `sub` | `uuid not null`, FK yok (REF) | Tutarlı |
| `version` | — | `@Version` | `bigint default 0` | Tutarlı |

Notlar:
- `@Size` UTF-16 kod birimi sayar, PostgreSQL `char_length` kod noktası sayar; vekil çift (ör. emoji) içeren metinde API, DB'den **daha sıkı** davranır — güvenli yöndedir.
- `btrim` yalnız boşluk kırpar; Java `strip()` tüm Unicode boşluklarını kırpar. DB kuralı daha gevşektir, API ihlal edilemez.
- Entity `description` alanı `@Column` ile eşlenir (DB'de `text`); community `Post.body` ile aynı kalıp. `ddl-auto=validate` ile uyumu **doğrulanmadı** (§6).

## 5. Bilerek kapsam dışı bırakılanlar (EVT-01'e eklenmedi)

| Konu | Görev |
| --- | --- |
| `capacity`, `participant_count` | EVT-13 |
| `game_id` ve game-service doğrulaması | EVT-17 |
| `cancelled_reason`, iptal | EVT-15 |
| Durum geçişleri (ACTIVE/COMPLETED) | EVT-16 |
| Outbox / `EventCreated` olayı, bildirim, duyuru | EVT-21, EVT-22, EVT-24 |
| `DELETE /internal/v1/accounts/{accountId}` | EVT-24 / [ayrı bağımlılık](hesap-silme-entegrasyonu.md) |
| Oluşturma/okuma dışındaki tüm uçlar (GET, PUT, DELETE) | EVT-03.. |

## 6. Doğrulama durumu

**Docker dev ortamında doğrulandı (2026-11-09, KD-1 öncesi kodla; ADMIN/MODERATOR kuralı):** event-service imajı yeniden build edildi, container başladı, Flyway V111 uygulandı (`flyway_schema_history` success), `ddl-auto=validate` ile servis hatasız başladı, `events` tablosu ve CHECK kısıtları V111 ile birebir, health 200 (doğrudan ve Gateway), Gateway `/api/v1/event/**` yönlendirmesi çalışıyor, token'sız/sahte token'lı `POST` ProblemDetail ile 401, `/internal/**` kapalı.

**Doğrulanmadı (NOT VERIFIED):**
- Hiçbir test çalıştırılmadı (JUnit, pre-push, `node pre-push/ci/backend.mjs`).
- **KD-1 (PLAYER/MODERATOR/ADMIN) kodu Docker'da çalışan container'a henüz alınmadı**; çalışan imaj eski ADMIN/MODERATOR kuralını içerir. Yeniden build gerekir.
- Geçerli bir Auth token'ıyla 201/403/400 yanıtları ve `events` tablosuna satır yazılması (hiç denenmedi); event-service'in Auth JWKS'ini container içinden çekebildiği.
- DB seviyesinde CHECK testi yok (Testcontainers/izole PostgreSQL altyapısı bu serviste kurulmadı).
- Gateway üzerinden çerez → Bearer + CSRF akışı.
- Checkstyle/PMD kapısı derleme sonrası `lint-ratchet check` ile yalnız yerel çalıştırıldı (0 yeni ihlal); KD-1 değişikliği sonrası **yeniden çalıştırılmadı**.
