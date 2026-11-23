# EVT-17 — Oyun bağlantısı: gereksinim karşılama durumu

> Durum: **IN PROGRESS, doğrulama bekliyor.** Kod ve testler yazıldı; **testler, V114 migration'ı ve Game Service çağrıları hiçbir veritabanında/serviste çalıştırılmadı.** Docker imajı güncel (2026-11-11 16:43 UTC; jar + V111–V115 doğrulandı), ama davranış token'la doğrulanmadı.
> Son güncelleme: 2026-11-09 · Sorumlu: Nisa · İlgili: [EVT-05](evt-05-etkinlik-arama.md), [Game talebi req-01](../game-service/talep/req-01.md)

## 1. Kaynaklar

| # | Kaynak | İçerik |
| --- | --- | --- |
| K-1 | `LTZ_Servis_Icerikleri_Nisa.md` EVT-17 | "Etkinliğin ilgili LTZ oyunuyla `gameId` üzerinden ilişkilendirilebilmesi." |
| K-2 | `docs/database.md` §2.1, §3.12 | Servisler arası ilişki REF'tir (FK yok); `events.game_id REF nullable`; başka servis verisi için snapshot izni (bu iş için **kullanılmadı**). |
| K-3 | `docs/architecture.md` §6.1 | `event → game`: etkinliğin oyun bağlantısı RestClient ile doğrulanır; timeout ENV'den, yönlendirme izlenmez, POST otomatik yeniden denenmez. |
| K-4 | Karar KD-5/B (Nisa, 2026-11-09) | Oyun adı Event DB'sine kopyalanmaz; ad araması Game Service'te yapılır. |
| K-5 | `docs/api.md` GAME-15 | `POST /api/v1/game/games/batch` public; 1–50 kimlik; `items` (ACTIVE, her tür) / `unavailable`. |

## 2. Gereksinim karşılama tablosu

| # | Gereksinim | Durum | Kodlandı | Doğrulandı | Docker'da |
| --- | --- | --- | --- | --- | --- |
| 1 | Etkinlik `gameId` ile oyuna bağlanabilir | **Karşılandı** (oluşturma anında) | `CreateEventRequest.gameId`, `Event.gameId`, V114 `events.game_id` | Testler yazıldı; çalıştırılmadı | İmajda var; davranış token'la denenmedi |
| 2 | Bağlantı başka servisin DB'sine erişmeden kurulur | **Karşılandı** | `GameServiceClient` → `POST /games/batch` (public API) | `GameServiceClientTest` yazıldı; çalıştırılmadı | İmajda var; davranış token'la denenmedi |
| 3 | Oyun yoksa/yayında değilse reddedilir | **Karşılandı** | `GameNotAvailableException` → 422 | `EventServiceTest`, controller/güvenlik testleri | İmajda var; davranış token'la denenmedi |
| 4 | Game Service erişilemezse "oyun yok" gibi gösterilmez | **Karşılandı** | `GameCatalogUnavailableException` → 503; hiçbir şey kaydedilmez | aynı | İmajda var; davranış token'la denenmedi |
| 5 | Oyun adı events tablosuna kopyalanmaz | **Karşılandı** | yalnız `game_id` (UUID) saklanır | — | — |
| 6 | Oluşturulmuş etkinliğin oyun bağlantısını **değiştirme/kaldırma** | **Eksik — EVT-14** | — | — | — |
| 7 | Yanıtta oyun adı | **Kapsam dışı** | Yanıt yalnız `gameId` döner; ad için istemci Game Service'e (`/games/batch`) sorar | — | — |

**Sonuç:** 1–5 karşılandı (kodlandı, doğrulanmadı). 6 eksik (EVT-14 etkinlik güncelleme henüz yok). EVT-17 "ilişkilendirilebilmesi" ifadesini oluşturma anı için karşılar; sonradan bağlantı değişikliği EVT-14'e bağlıdır.

## 3. Davranış

- `POST /api/v1/event/events` gövdesine isteğe bağlı `gameId` (UUID) eklendi. Verilmezse etkinlik oyunsuzdur (`gameId: null`).
- Sıra: kimlik → rol → **yerel zaman kuralları** → Game Service doğrulaması → kayıt. Geçersiz zamanlı istek Game Service'e gitmez; yetkisiz kullanıcı Game Service çağrısı tetikleyemez.
- Doğrulama `POST {GAME_SERVICE_URL}/api/v1/game/games/batch` ile tek kimlik: `items` içinde varsa oyun yayındadır (ACTIVE, **her tür**: ana oyun, DLC vb.). Yoksa 422.
- Game Service hata/zaman aşımı/429/geçersiz yanıt → 503 ProblemDetail; etkinlik kaydedilmez; yeniden deneme yok.
- Game çağrısı DB transaction'ı **dışındadır** (bağlantı havuzu 3 bağlantı).
- `gameId` detay (EVT-03), liste (EVT-04) ve oluşturma yanıtlarında döner.
- ENV: yeni değişken **yok**. `GAME_SERVICE_URL` zaten tüm servislere geçirilir; `ltz.clients.game-service.url` ona bağlanır. Servis kimliği/secret gerekmez (iki uç herkese açık).

## 4. Varsayımlar

| # | Varsayım | Gerekçe |
| --- | --- | --- |
| VR-1 | Her ACTIVE oyun türü (DLC dahil) bağlanabilir. | Gereksinim tür kısıtı koymaz; kısıtlamak yeni iş kuralı olurdu. **Sonuç:** DLC'ye bağlı etkinlik oyun adıyla bulunamaz (Game araması MAIN_GAME ile sınırlı) — bkz. EVT-05 ve req-01 S-2. |
| VR-2 | Oyun, bağlandıktan sonra arşivlense bile etkinlikteki `gameId` korunur. | Servisler arası tutarlılık olay/senkronla (`GameCatalogChanged`) yönetilir; bu görevde yok. |
| VR-3 | Tür-özel kural yok (ör. `TOURNAMENT`/`GAME_NIGHT` için oyun zorunlu değil). | EVT-18 (turnuva), EVT-19 (oyun gecesi: "belirli bir oyun ve zaman") kendi kurallarını getirir. |

## 5. Doğrulama durumu

**Doğrulanan:**
- Derleme ve Checkstyle/PMD: çıkış 0, `lint-ratchet` event-service 0 yeni ihlal (ajan çalıştırdı).
- Çalışan Game Service'in herkese açık arama ucuna salt-okunur `GET` ile sözleşme gözlendi (yanıt alanları, `size`/`page`/`q` sınırları). `POST /games/batch` canlı çağrılmadı (yazma değil, ama bu turda denenmedi).

**Doğrulanmayan (NOT VERIFIED):** tüm yeni testler (birim, WebMvc, `MockRestServiceServer`, gerçek PostgreSQL `EventRepositoryTest`); V114; `ddl-auto=validate` ile yeni `game_id` eşlemesi; gerçek Game Service'e karşı uçtan uca oluşturma; Gateway üzerinden çağrı.

**Docker (2026-11-11 16:43 UTC):** yalnız event-service yeniden build edilip başlatıldı (`docker compose build event-service` + `up -d --no-deps --no-build event-service`; `down` yok; volume, veri ve diğer servisler korundu). **İmaj (çalışan container'ın jar'ı):** EVT-01..06, EVT-17 ve platform kodu (`EventPlatform`, `platform` filtresi, `platforms` alanı) imajda bulunuyor; jar'da V111–V115 var. **Veritabanı (ayrı doğrulandı):** `flyway_schema_history` 111–115 hepsi `success`; V113–V115 bu başlatmada uygulandı; `events.game_id`, `event_platforms`, `ix_events_open_starts_at`, `ix_events_game_id`, `ix_event_platforms_platform` var; `events` ve `event_platforms` 0 satır (mevcut veri yoktu, hiçbir etkinliğe platform atanmadı). **Başlatma:** `Started EventServiceApplication`, `ddl-auto=validate` hatasız, sağlık 200 (doğrudan ve Gateway). Token'sız `GET` (liste, filtre, `platform=PS5` dahil) ve `POST` Gateway'den 401. **Doğrulanmadı:** geçerli token'la 201/200/400/422/503 davranışları (token yok), gerçek PostgreSQL'de filtre/arama sonuçları (testler çalıştırılmadı), Event → Game çağrısı. **Ortam bulgusu:** bu oturumda Docker Desktop motoru kendiliğinden yeniden başladı ve sonrasında Auth (JWKS) ve Game'in herkese açık uçları doğrudan ve Gateway'den 401 döndü (kod/belge bu uçların herkese açık olduğunu söyler; 9 Kasım'da 200'dü). Nedeni teşhis edilmedi; başka servislere dokunulmadı. Event token doğrulaması Auth JWKS'ine, oyun araması/doğrulaması Game'in herkese açık uçlarına bağlıdır: bu durum düzelmeden token'la uçtan uca doğrulama yapılamaz.
