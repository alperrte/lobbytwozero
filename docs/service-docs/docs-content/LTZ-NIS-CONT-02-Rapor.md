# LTZ-NIS-CONT-02 — Haber Yayınlama, Keşif ve Çoklu Dil (Rapor)

> Tarih: 2026-11-06 · Servis: content-service (Nisa) · Dal: `feature/content-service`
> Kapsam kaynağı: `docs-community/LTZ_PDA_Nisa_Guncellenmis.md` (content-service / CONT-02). Test durumu: **ilk teslimde 290 test geçti** (`verify`, Checkstyle/PMD, SonarQube Quality Gate o sürümde yeşildi: 0 bug, 0 code smell, kapsama %95,5). **2026-11-07 düzeltmelerinden sonra: 297 test geçti, sahibi pre-push kontrollerini geçirdi, SonarQube `ltz-content-service` Quality Gate geçti** (0 bug, 0 güvenlik açığı, 0 hotspot, 0 code smell, kapsama %95,8, tekrar %0; bkz. §8).

## 1. PDA maddelerinin uygulama durumu

| Madde | Durum | Karşılığı |
| --- | --- | --- |
| **CONT-01** Oyun haberleri | Uygulandı | Yayımlanan içerik herkese açık akışta listelenir: `GET /api/v1/content/contents` (yeniden eskiye, keyset sayfalama). İçerik `gameId` ile LTZ oyununa bağlanabilir |
| **CONT-02** Haber detayları | Uygulandı | `GET /{id}`: başlık, özet, gövde, görseller, kaynaklar, kategoriler, oyun, yayımlanma tarihi, `availableLocales` |
| **CONT-03** Kategoriler | Uygulandı | Sabit 14 kategori (6 platform + 8 konu; V103), `GET /categories`; içerik başına en çok 5 (`PUT /{id}/categories`) |
| **CONT-09** Kullanıcı yayını | Uygulandı | `POST /{id}/publish`: ön moderasyon yok; yayın kuralları tüm dillerde denetlenir; `ANNOUNCEMENT` yalnız ADMIN |
| **CONT-11** Öne çıkanlar | Uygulandı | `PUT/DELETE /{id}/featured` yalnız ADMIN; akışta `featured=true` filtresi |
| **CONT-12** Arama | Uygulandı | `q` parametresi: başlık/özet/gövdede büyük-küçük harf duyarsız "içerir"; servis içinde, ayrı search-service yok |
| **CONT-13** Filtreleme | Uygulandı | `category`, `gameId`, `type`, `featured`, `from`/`to` (yayın zamanı), `locale` |
| **CONT-15** Çoklu dil | Uygulandı | `tr`/`en`; `PUT/DELETE /{id}/translations/{locale}`; akış ve detay `locale` ile istenen dili, yoksa ilk dili gösterir |
| **CONT-16** Kaynaklar | Uygulandı | `PUT /{id}/sources`: en çok 3, yalnız `https://`, kaynak adı ve tarihi; sunucu adrese istek atmaz |

## 2. Değişen / eklenen uçlar (`docs/api.md` → CONTENT)

| Method | Path | Yetki |
| --- | --- | --- |
| GET | `/api/v1/content/contents` | **Public** (akış, arama, filtre) |
| GET | `/api/v1/content/categories` | **Public** |
| GET | `/api/v1/content/contents/{contentId}` | **Public** (yalnız yayımlanmış; sahibi kendi taslağını görür) — *değişti* |
| GET | `/api/v1/content/contents/{contentId}/images/{imageId}` | **Public** (yalnız yayımlanmış içerik; taslak yalnız sahibi; `Cache-Control: no-store`) — *değişti* |
| POST | `/api/v1/content/contents/{contentId}/publish` | Owner |
| PUT / DELETE | `/api/v1/content/contents/{contentId}/featured` | Admin |
| PUT / DELETE | `/api/v1/content/contents/{contentId}/translations/{locale}` | Owner |
| PUT | `/api/v1/content/contents/{contentId}/categories` | Owner |
| PUT | `/api/v1/content/contents/{contentId}/sources` | Owner |
| PUT / DELETE | `/api/v1/content/contents/{contentId}/game` | Owner |
| PATCH, DELETE, görsel ekle/sil | (CONT-01 uçları) | Owner — *yayımlanmış içerikte de çalışır; yayın kuralları yeniden denetlenir* — *değişti* |

Yanıt gövdesi (`ContentResponse`) genişledi: `gameId`, `categories`, `sources`, `featured`, `publishedAt`, `availableLocales`. Mevcut alanlar değişmedi (geriye uyumlu ekleme).

## 3. Migration

- **V103** `create_categories_and_sources_tables.sql` (`content_categories` + 14 tohum kayıt, `content_category_assignments`, `content_sources`).
- **V104** `add_publication_columns_to_contents.sql` (`game_id`, `published_at`, `featured*`; durum–tarih ve öne çıkarma CHECK'leri; kısmi indeksler).
- **V105** `add_content_search_indexes.sql` (`pg_trgm` eklentisi, `content_translations` üzerinde başlık/özet/gövde GIN trigram indeksleri).
- Aralık V101–V110; **V106–V110 boş** (V105 teslim öncesi düzeltmeyle eklendi: arama indeksleri, bkz. §8). Uygulanmış V101/V102'ye dokunulmadı. Yeni ENV: `GAME_SERVICE_URL` için `ltz.clients.game-service.url` varsayılanı (`http://game-service:8080`) `application.properties`'e eklendi; `.env` dosyalarına dokunulmadı.

## 4. Kararlar

Proje sahibi onayı:

| Konu | Karar |
| --- | --- |
| Misafir okuma | Akış, detay, görsel ve kategori listesi kimliksiz okunur; yalnız yayımlanmış içerik |
| Öne çıkarma | Yalnız ADMIN |
| Kategoriler | Sabit migration listesi, yönetim ucu yok |
| Arama | ILIKE benzeri "içerir" |

Varsayılan kararlar (onay verilmediği için bilinçli seçildi; itiraz varsa değişir):

- **Yayımlanmış içerik sahibi tarafından düzenlenebilir** (yayın kuralları yeniden denetlenir); `REMOVED`/`ARCHIVED` düzenlenemez.
- **Slug yok**; içerik kimlikle (UUID) açılır. Taslak listesi (`GET /contents/mine`) yok.
- **Olay/outbox üretilmez** (broker kararı bekliyor); notification-service entegrasyonu yok.
- **Kategori çoka-çok**: önceki şema taslağındaki tekil `contents.category_id` yerine `content_category_assignments` (bir haber hem platform hem konu taşır). `docs/database.md` ve `docs/er-diagram.md` güncellendi.
- **Görsel önbelleği (düzeltme sonrası):** tüm görsel yanıtları `Cache-Control: no-store`; ilk teslimdeki `public, max-age=300` kararı geri alındı (silinen içeriğin görseli önbellekte kalabiliyordu).

## 5. Güvenlik özeti

- Akış SQL'i tamamen bağlı parametredir (kategori kodu, arama, imleç); `%`, `_`, `!` aramada düz metindir. SQL enjeksiyon denemesi gerçek PostgreSQL'de boş sonuç verir.
- Taslak, silinmiş ve kaldırılmış içerik hiçbir herkese açık yolda görünmez (akış sorgusu + servis katmanı + görsel erişimi); başkasının taslağı 404, yayımlanmış içerikte yazma girişimi 403.
- Geçersiz Bearer başlığı herkese açık uçlarda da 401'dir; yalnız başlıksız istek misafir sayılır.
- Kaynak bağlantısı: uygulamada ve DB CHECK'inde yalnız `https://`; `javascript:`, `data:`, boşluklu ve `user:pass@` biçimleri reddedilir; sunucu bağlantıya istek atmaz.
- Oyun doğrulaması game-service'e giden tek dış çağrıdır; transaction dışında, zaman aşımıyla; hata ayrıntısı istemciye sızmaz (503).
- Öne çıkarma rolü yalnız doğrulanmış JWT `roles` claim'inden okunur.

## 6. Bu fazda yapılmayanlar / açık işler

- **Taslak listesi**, **slug**, **görsel alt metni**, **moderatör kaldırma / şikâyet (CONT-03 fazı)**, **outbox/inbox olayları**, **hesap silme ucu** (ayrı görev: `LTZ-NIS-CONT-AUTH-01-Hesap-Silme-Gorevi.md`).
- **Arama performansı:** V105 ile çözüldü (aşağıda §8).
- **Kullanım hakkı:** kaynak bilgisi saklanır ve gösterilir; hak denetimi otomatik değildir (politika/moderasyon).
- **Gateway:** `/api/v1/content/**` altındaki dört `GET` yolunun anonim geçirilmesi Gateway yapılandırmasıdır (sahibi: Alper); **NOT VERIFIED**. Çerez→Bearer akışı yalnız sahibin taslak görseli için gerekir.
- Gerçek game-service ve auth-service ile uçtan uca doğrulama: **NOT VERIFIED**.

## 7. Testler (çalıştırıldı, 2026-11-06)

| Dosya | Kapsam |
| --- | --- |
| `ContentFeedServiceTest`, `PublishedCursorTest` | Varsayılanlar, limit+1/imleç, bozuk imleç, tarih aralığı, arama deseni ve kaçışları |
| `ContentPublishingServiceTest` | Yayın kuralları (tüm diller), duyuru yetkisi, öne çıkarma ADMIN, idempotans, sahiplik |
| `ContentTranslationServiceTest`, `ContentMetadataServiceTest` | Dil kuralları (ilk dil, yalnız tr/en), yayımlanmış içerikte kural denetimi, kategori/kaynak/oyun doğrulaması, güvensiz bağlantılar, sahiplik |
| `GameServiceClientTest` | `MockRestServiceServer` ile var/yok/hata/boş yanıt |
| `ContentFeedControllerTest`, `ContentWorkflowControllerTest` | HTTP sözleşmesi, sorgu/gövde doğrulaması, hata eşlemeleri (422/503 dahil), oturum zorunluluğu, bozuk Bearer |
| `JdbcContentFeedIntegrationTest` (gerçek PostgreSQL) | Yalnız yayımlanmış ve silinmemiş içerik, sıralama/eşit zaman, keyset, filtreler, tarih aralığı, dil seçimi, arama (çok dilli, kaçış), SQL enjeksiyon denemesi, kategori/kapak |
| `ContentPublicationLifecycleIntegrationTest` (gerçek PostgreSQL) | Taslak→yayın→akış/arama→öne çıkarma→çeviri→silme tutarlılığı; yabancı taslak görünmezliği; oyun/kategori reddi |
| `ContentRepositoryIntegrationTest` (gerçek PostgreSQL) | V103/V104 kısıtları: 14 tohum kategori, atama PK/FK/cascade, kaynak CHECK'leri, yayın/öne çıkarma CHECK'leri |
| `ContentSecurityTest` (güncellendi) | Herkese açık GET'ler ayrıldı; yazma uçları ve sahte kimlik başlıkları hâlâ 401 |

Doğrulama sırasında bulunan ve düzeltilen gerçek hatalar:

- `JdbcContentFeed`: kategori filtresi başka bir filtreden sonra geldiğinde SQL parçaları bitişiyordu (`:gameIdand exists…`); gerçek PostgreSQL testi yakaladı, SQL birleştirmesi düzeltildi.
- Sonar: `ContentFeedService` imleç hesabında olası null (S2259), `Boolean` açma (S5411); testlerde S5778/S5961 giderildi.

Komutlar (yalnız bu servis):

```powershell
mvn -B -ntp -f backend/pom.xml -pl services/content-service verify
node pre-push/ci/lint-ratchet.mjs check --modules services/content-service
```

## 8. Teslim öncesi düzeltmeler (2026-11-07)

| # | Konu | Yapılan |
| --- | --- | --- |
| 1 | Görsel önbelleği | `Cache-Control: public, max-age=300` → **`no-store`** (tüm görsel yanıtları). Her istekte `loadViewable` ile içeriğin yayımlanmış ve silinmemiş olduğu yeniden denetlenir. `ContentImageContent.publiclyCacheable` alanı kullanılmadığı için kaldırıldı |
| 2 | Arama indeksi | **V105** `add_content_search_indexes.sql`: `create extension if not exists pg_trgm` + `lower(title)`, `lower(summary)`, `lower(body)` üzerinde `gin_trgm_ops` GIN indeksleri. Sorgu ifadeleri indeks ifadeleriyle birebir eşlendi (`coalesce(...)` kaldırıldı; sonuç aynı). V103/V104'e dokunulmadı |
| 3 | Türkçe harf hatası | Desen Java `toLowerCase(Locale.ROOT)` ile katlanıyordu; `İ` → `i` + birleştirici nokta üretip veritabanındaki `lower('İ') = 'i'` ile uyuşmuyordu (`İstanbul` araması `İstanbul` başlığını bulamıyordu). Katlama artık yalnız DB'de: `lower(sütun) like lower(:q) escape '!'` |
| 4 | Doğrulama | Docker'da servis yeniden kuruldu; V103–V105 uygulandı; Gateway üzerinden genel GET'ler ve görsel senaryoları denendi |
| 5 | Kural boşlukları | Kategori sınırı (5) artık serviste de zorlanıyor (yalnız DTO'da değildi); yayımlanmış içeriğin çevirisi çeviri ucundan kısa metinle değiştirilirse `422` olduğu gerçek PostgreSQL'de testle doğrulandı |

### 8.1 Arama indeksi kararı (`docs/database.md` §4 karşılaştırması)

- Kural: metin aramada `pg_trgm` + GIN, eklenti migration ile açılır, DB sahibinin izni gerekir. CONT-02'nin ilk teslimi bu kuralı karşılamıyordu (indekssiz tarama); V105 ile karşılandı.
- **Yetki:** `pg_trgm` PostgreSQL 13+ için "trusted" eklentidir; veritabanında `CREATE` yetkisi olan kullanıcı açabilir. Dev ortamında servis kullanıcısı bu yetkiye sahip olduğundan migration sorunsuz uygulandı. **Engel/kalan iş:** üretim benzeri bir ortamda servis kullanıcısının `CREATE` yetkisi yoksa V105 durur; o durumda DB sahibi `create extension pg_trgm;` komutunu bir kez çalıştırmalı (migration `if not exists` ile bu halde de çalışır).
- **Plan:** gerçek PostgreSQL'de `explain` ile, `enable_seqscan=off` altında üç indeksin de arama koşulunda kullanıldığı test edildi (`JdbcContentFeedIntegrationTest`). Küçük tabloda planlayıcı zaten sıralı taramayı seçer; indeks büyük veride devreye girer. İki karakterlik aramalar trigram üretmez, sıralı taramaya düşer (sonuç aynı).
- Maliyet: gövde indeksi yazmada ek iş ve disk yükü getirir; içerik hacmi küçükken ihmal edilebilir. Gerekirse yalnız `body` indeksi ayrı bir migration ile kaldırılabilir.

### 8.2 `İ/i` ve `I/ı` gözlemi (gerçek PostgreSQL 17, `en_US.utf8`, libc, alpine)

`lower(chr(304)) = 'i'` (doğru), `lower('I') = 'i'`, `lower('I') <> 'ı'`, `lower('ı') = 'ı'`, `upper('i') = 'I'`.

| Arama | İçerik (başlık) | Sonuç |
| --- | --- | --- |
| `istanbul`, `İstanbul`, `ISTANBUL`, `İSTANBUL` | `İstanbul Işık Rehberi` | **Eşleşti** (testle ve Gateway üzerinden doğrulandı) |
| `isparta`, `ISPARTA`, `İsparta` | `ISPARTA gezisi` | Eşleşti |
| `Işık`, `IŞıK` | `İstanbul Işık Rehberi` | Eşleşti |
| `ışık` (noktasız, küçük) | `…Işık…` | **Eşleşmedi** (bilinen sınır) |
| `ısparta` (noktasız) | `ISPARTA gezisi` | **Eşleşmedi** (bilinen sınır) |

Sonuç: İ/i ve I/i çifti çalışıyor; noktasız `ı` ile büyük `I` eşleşmiyor çünkü DB `I`'yı `i`'ye katlıyor. Bu, locale-bağımsız `lower()` kullanan her çözümün sınırıdır; Türkçe `tr-TR` collation/ICU ya da özel katlama ayrı bir karar (ve indeks ifadesinin yeniden yazılması) gerektirir, bu fazın kapsamı dışında bırakıldı.

### 8.3 Görsel URL'si doğrulaması

Birim/entegrasyon testleri (gerçek PostgreSQL): yayımlanmış görsel misafire açılır; içerik silinince ya da `REMOVED` yapılınca aynı URL `404` olur; taslak görseli misafire `404`. Gateway (`127.0.0.1:5080`) ve doğrudan servis (`:5091`) üzerinde canlı denendi (geçici satırlarla; sonra silindi, dev veri korundu):

| İstek (kimliksiz) | Gateway | Servis |
| --- | --- | --- |
| `GET /contents`, `GET /categories` | 200 | 200 |
| `GET` yayımlanmış detay / görsel | 200 / 200 (`Cache-Control: no-store`) | 200 / 200 |
| `GET` taslak, `REMOVED`, silinmiş detay | 404 / 404 / 404 | 404 / 404 / 404 |
| `GET` taslak, `REMOVED`, silinmiş görsel | 404 / 404 / 404 | 404 / 404 / 404 |
| Geçersiz `Authorization: Bearer` ile `GET /contents` | 401 | 401 |
| Tokensız `POST /contents`, `POST …/publish`, `PUT …/featured`, `PUT …/translations/en` | 401 | 401 |

Aynı görsel URL'si Gateway üzerinden: yayında `200` → içerik `REMOVED` yapılınca `404` → geri alınınca `200` → silme işareti + görsel silinince `404` (detay da `404`). Silme ve kaldırma burada veritabanı satırı değiştirilerek benzetildi; gerçek `DELETE`/kaldırma uçları token gerektirdiği için canlı ortamda token'la denenmedi (servis seviyesinde gerçek PostgreSQL testleri bu akışı gerçek servis koduyla doğrular).

**Gateway sonucu:** dört genel GET yolu Gateway'den kimliksiz geçiyor; Alper'in uygulaması gereken ek bir ayar yok. Yazma yolları tokensız 401.

### 8.4 Testler ve komutlar

- Bu düzeltmelerden sonra `mvn test` (content-service): **297 test geçti, 0 hata, 0 atlanan**. **290 test sonucu önceki sürüme aittir**; V105, Türkçe harf, indeks, görsel URL'si ve kural testleri eklenmeden önceydi. Aynı koşuda `ContentRepositoryIntegrationTest` (24), `JdbcContentFeedIntegrationTest` (17) ve `ContentPublicationLifecycleIntegrationTest` (11) gerçek PostgreSQL ile geçti.
- Düzeltmelerden sonra pre-push kontrollerini sahibi çalıştırdı ve geçti; SonarQube (297 test, JaCoCo raporuyla) Quality Gate **geçti**: 0 bug, 0 güvenlik açığı, 0 hotspot, 0 code smell, kapsama %95,8. Komutlar:

```powershell
mvn -B -ntp -f backend/pom.xml -pl services/content-service verify
node pre-push/ci/lint-ratchet.mjs check --modules services/content-service
node pre-push/ci/backend.mjs --modules services/content-service
```
