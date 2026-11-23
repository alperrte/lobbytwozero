# LTZ-UMT-LFP-01 — Oyun Arkadaşı İlanı Oluştur (TASK-01)

> Tarih: 2026-11-13 · Sorumlu: Umut · Servis: `lfp-service` · Dal: `feature/lfp-service` · Kapsam: LFP-01, LFP-02, LFP-03, LFP-04, LFP-05, LFP-06, LFP-07, LFP-08, LFP-09 (+ LFP-15 süre seçimi) · Durum: **kod, testler ve belgeler tamam; merge öncesi doğrulama PASS** (testler kullanıcı kararıyla servisin tüm görevleri bitince toplu koşuldu; sonuçlar: [TASK-03 §8](TASK-03.md#8-merge-öncesi-doğrulama-2026-11-13))
>
> Bu belge lfp-service çalışmasına özgüdür; ortak kurallar `AGENTS.md`, `docs/SECURITY.md` ve `docs/api.md` içindedir. Endpoint sözleşmesi `docs/api.md` → `## LFP` altındadır.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| LFP-01 İlan oluşturma | IMPLEMENTED | `POST /api/v1/lfp/listings`; sahip token'dan |
| LFP-02 Oyun seçimi | IMPLEMENTED | game-service kart ucuyla doğrulanır; adı anlık görüntü olarak saklanır |
| LFP-03 Platform | IMPLEMENTED | Sabit liste: PC, PLAYSTATION, XBOX, NINTENDO_SWITCH, MOBILE, OTHER |
| LFP-04 Seviye (opsiyonel) | IMPLEMENTED | Boş/yalnız boşluk = belirtilmedi (`null`) |
| LFP-05 Rol | IMPLEMENTED | 1–5 rol, tekrar (büyük/küçük harf) bir kez tutulur |
| LFP-06 Oyuncu sayısı | IMPLEMENTED | 1–50 |
| LFP-07 Oyun zamanı | IMPLEMENTED | Opsiyonel başlangıç/bitiş; geçmiş ve ilan bitişinden sonrası reddedilir |
| LFP-08 Mikrofon | IMPLEMENTED | `micRequired` (zorunlu boolean) |
| LFP-09 Açıklama | IMPLEMENTED | 1–1000 karakter düz metin |
| LFP-15 (süre seçimi) | IMPLEMENTED (kısmen) | `durationHours` 1–168, varsayılan 24; otomatik `EXPIRED` geçişi LFP-02 görevinde |

Doğrulama durumu:

| Kontrol | Sonuç |
| --- | --- |
| Derleme (`mvn -pl services/lfp-service -DskipTests test-compile`, JDK 25) | PASS — Checkstyle derleme sırasında temiz |
| Birim + entegrasyon testleri (69 test, Testcontainers PostgreSQL 17) | PASS — merge öncesi toplu koşu (TASK-03 §8) |
| JaCoCo, PMD, `lint-ratchet` | PASS — merge öncesi `pre-push` (TASK-03 §8) |
| `.\pre-push\pre-push.cmd full dev` / SonarQube | full dev SAFE TO COMMIT; SonarQube bkz. TASK-03 §8 |
| Gerçek game-service ile uçtan uca | NOT VERIFIED |

## 2. Uç ve akış

| Uç | Ne yapar |
| --- | --- |
| `POST /api/v1/lfp/listings` | İlan açar; 201 + `Location` |

1. **Gövde:** Jakarta Validation (zorunlu alanlar, ham uzunluk üst sınırı, 1–50, 1–168), bilinmeyen alan reddi, 8 KB sınırı.
2. **İçerik kuralları** (`ListingContent.of` → `ListingText`): boşluk atma, karakter (code point) sayımı, kontrol/görünmez karakter reddi, rol tekilleştirme. Bu adım game-service'ten **önce** çalışır; geçersiz gövde dış çağrı tetiklemez.
3. **Süre:** `durationHours` → ayarlı aralık (`ltz.lfp.listings.min/max-duration`) dışı `400 INVALID_DURATION`.
4. **Oyun:** `GameServiceClient` → `GET {GAME_SERVICE_URL}/api/v1/game/{gameId}/card`, transaction dışında (dış çağrı DB bağlantısı tutmasın). `404` → `422 GAME_NOT_FOUND`; oyun olmayan tür → `422 GAME_NOT_PLAYABLE`; diğer her durum → `503 GAME_CATALOG_UNAVAILABLE`.
5. **Transaction:** sahip advisory kilidi (`OwnerLock`, `lfp:<ownerId>`) → açık ilan sınırı (5) → saatlik oluşturma sınırı (10) → oyun zamanı kuralları (`Listing.open`) → kayıt.

## 3. Kararlar ve varsayımlar (itiraz edilebilir)

| Konu | Karar | Neden |
| --- | --- | --- |
| Süre | Kullanıcı seçer, 1 saat – 7 gün, varsayılan 24 saat; sonradan değişmez (kullanıcı kararı, 2026-11-13) | LFP-15 "her ilanın bir geçerlilik süresi"; sınırsız uzatma ilanları sonsuza kadar açık tutardı |
| Platform | Sabit liste, oyunun platform verisine göre doğrulanmaz (kullanıcı kararı) | game-service platform verisi Steam kaynaklı; konsol ilanlarını haksız reddederdi |
| Görünürlük | Yalnız giriş yapmış kullanıcılar (kullanıcı kararı) | Engel filtresi (LFP-17) kimlik ister; misafire ilan gösterilmez |
| Kapsam | Yalnız backend (kullanıcı kararı) | Messaging'deki gibi; `frontend/src/features/lfp` boş kalır |
| Tek platform | İlan başına bir platform | `database.md` taslağı tek `platform` sütunu; crossplay için `OTHER` ya da iki ilan |
| Roller | Zorunlu, 1–5, her biri 1–30 karakter; ayrı tablo (`lfp_listing_roles`) | LFP-04 seviye için açıkça "opsiyonel" der, LFP-05 demez. `jsonb` yerine tablo: sıralı, rol süzgeci indekslenebilir, Hibernate şema doğrulaması güvenli |
| Rol tekilliği | Uygulamada (büyük/küçük harf duyarsız), DB tekil indeksi yok | Liste güncellenirken satırlar pozisyona göre yerinde değişir; ertelenemeyen tekil indeks geçici çakışma verirdi |
| Oyun zamanı | Opsiyonel ("eklenebilmesi"); başlangıç ≥ şimdi − 5 dk ve ≤ ilan bitişi; bitiş yalnız başlangıçla, > başlangıç, aralık ≤ 24 saat | Süresi dolan ilanın oyun zamanı anlamsız; saat farkı payı |
| Mikrofon | Zorunlu boolean | `database.md` `mic_required`; "gereklilik" ikili |
| Açıklama | Zorunlu, 1–1000 | İlanın asıl içeriği |
| Oyuncu sayısı | 1–50 | Raid/büyük takım ihtiyacı; spam için üst sınır |
| Oyun türü | `SOUNDTRACK`, `VIDEO`, `HARDWARE`, `SOFTWARE` reddedilir; `UNKNOWN` ve yeni türler kabul | Oyun olmayan kayıt için ilan anlamsız; türü bilinmeyen gerçek oyunlar engellenmesin |
| `FULL` durumu | Yok; durumlar `OPEN`, `CLOSED`, `EXPIRED` | LFP-14 bu üç durumu sayar; katılım/kontenjan takibi istenmedi |
| Kötüye kullanım | 5 açık ilan / kullanıcı, 10 yeni ilan / saat (kapatılanlar dahil) | SECURITY.md §12 "ilan oluşturma" risk profili |
| Kullanıcı varlığı | Doğrulanmaz | Sahip token'dan gelir (Auth'un verdiği kimlik) |
| `lfp_reports` | Oluşturulmadı | LFP-01..18'de ilan raporlama görevi yok |

## 4. Güvenlik

| Katman | Koruma |
| --- | --- |
| Kimlik | Access token servis tarafında JWKS ile doğrulanır: yalnız RS256; `iss`, `aud`, `typ=at+jwt`, `exp`/`nbf`/`iat`/`sub` zorunlu; UUID olmayan `sub` 401. Servis token'ı kullanıcı ucunda geçmez |
| Yetki | Deny-by-default: yalnız `/actuator/health` açık, `/api/v1/lfp/**` kimlik ister, gerisi (`/internal/**` dahil) `denyAll` |
| `userId` | Yalnız token `sub`; gövdede `ownerId`/`status` gibi bilinmeyen alan `400` (testli) |
| UGC | Düz metin; kontrol, yön değiştirme ve sıfır genişlikli karakterler reddedilir (Trojan-source/sahte metin), uzunluk code point; DB'de aynı sınırlar CHECK ile |
| Dış girdi | game-service yanıtı güvenilmez: başka oyunun kimliği/boş ad → 503; ad temizlenir ve 500 karaktere kısaltılır; bilinmeyen alanlar yok sayılır |
| Fail-closed | Katalog yanıt vermezse ilan açılmaz (oyun varmış gibi davranılmaz) |
| Eşzamanlılık | Sahip advisory kilidi; sınırlar kilit altında sayılır (`ListingConcurrencyTest`: 10 eşzamanlı istekte tam 5 ilan) |
| Girdi boyutu | 8 KB gövde (Content-Length ve chunked; 413) |
| Veri | `lfp_db` dışına erişim yok; oyun/kullanıcı REF, FK yalnız `lfp_` tabloları arasında (`ServiceBoundaryTest`) |
| Hata | `ProblemDetail` + `code`; değer yansıtılmaz, stack trace/SQL yok |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| LFP-01 | `ListingCreateTest` (mutlu yol, sahiplik, sınırlar), `ListingConcurrencyTest` | `ListingController`, `ListingService.create`, `Listing.open` |
| LFP-02 | `ListingCreateTest` (oyun bölümü), `GameServiceClientTest` (6) | `GameServiceClient`, `ListingService.playableGame` |
| LFP-03 | `ListingCreateTest.tumPlatformlarKabulEdilirBilinmeyenPlatform400`, `FlywayMigrationTest` | `Platform` |
| LFP-04..06, 08, 09 | `ListingCreateTest` (alan doğrulamaları), `ListingTextTest` (9) | `ListingText`, `ListingContent` |
| LFP-07 | `ListingCreateTest.oyunZamaniKurallari400`, `ListingTest.oyunZamaniSinirlari` | `Listing.checkPlayTime` |
| LFP-15 (süre) | `ListingCreateTest.sureSinirlari1ile168Saat` | `ListingService.duration`, `ListingProperties` |
| Kimlik | `JwtValidationTest` (13) | `SecurityConfig`, `JwtConfig` |
| Şema | `FlywayMigrationTest` (7) | `V141__create_lfp_listings.sql` |
| Sınırlar | `ServiceBoundaryTest` (4), `RequestBodySizeLimitTest` (2) | `RequestBodySizeLimitFilter` |

## 6. Değişen dosyalar

- `backend/services/lfp-service/`: `pom.xml` (testcontainers-postgresql, test), `application.properties`, `V141__create_lfp_listings.sql`, `api/` (`ListingController`, `LfpApiPaths`, `AuthenticatedUser`, DTO'lar, `GlobalExceptionHandler`), `application/` (`ListingService`, komut/görünüm, `GameCatalogPort`, mapper), `domain/` (`Listing`, `ListingContent`, `ListingText`, enum'lar, hata kodları), `infrastructure/` (`GameServiceClient`, güvenlik, yapılandırma, repository, `OwnerLock`) ve testler.
- `docs/api.md` (LFP ortak kurallar + ilan oluştur), `docs/database.md` §3.9, `docs/er-diagram.md` §10, `docs/architecture.md` (LFP durum makinesi).

## 7. Açık noktalar

1. **Testler çalıştırılmadı.** Merge öncesi `.\pre-push\pre-push.cmd lfp-service` → `full dev` → SonarQube.
2. **Okuma uçları** (liste, arama, detay), güncelleme, kapatma ve otomatik süre dolumu LTZ-UMT-LFP-02'de.
3. **Engel/gizlilik** (LFP-17), ilan sahibiyle iletişim (LFP-16) ve hesap silme iç ucu LTZ-UMT-LFP-03'te.
4. **Gerçek game-service ile uçtan uca** doğrulanmadı. game-service'in kart ucu kimlik istemez; istek sınırı yalnız toplu kart ucundadır.
5. **Oyun adı güncelliği:** `game_title_snapshot` ilan açılırken alınır; oyun sonradan yeniden adlandırılırsa ilan eski adı gösterir (ilan ömrü en çok 7 gün olduğundan olay aboneliği eklenmedi).
