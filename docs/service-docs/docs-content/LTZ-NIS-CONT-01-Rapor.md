# LTZ-NIS-CONT-01 — Haber Oluştur, Taslak Kaydet ve İçeriği Düzenle (Rapor)

> Tarih: 2026-11-06 · Servis: content-service (Nisa) · Dal: `feature/content-service`
> Kapsam kaynağı: `docs-community/LTZ_PDA_Nisa_Guncellenmis.md` (content-service / CONT-01). Test durumu: **170 test geçti; hata ve atlanan test yok**. İzole PostgreSQL ile Maven `clean verify` başarılı; toplam satır coverage'ı %96,99, yeni/değişen executable satırlar %97,40; Checkstyle/PMD "yeni ihlal yok" kapısı geçti (2026-11-06).

## 1. PDA maddelerinin uygulama durumu

| Madde | Durum | Karşılığı |
| --- | --- | --- |
| **CONT-05** İçerik oluşturma | Uygulandı | `POST /api/v1/content/contents`: kayıtlı her kullanıcı `NEWS`/`USER_CONTENT` taslağı oluşturur; yazar oturumdaki `sub`'dan alınır. Resmî duyuru (`ANNOUNCEMENT`) yalnız `ADMIN` |
| **CONT-06** Düzenleme | Uygulandı | `PATCH /{id}`: yalnız sahibi ve yalnız taslak; sahiplik serviste doğrulanır; yazar/tür/dil/durum istekle değişmez (bilinmeyen alan 400) |
| **CONT-07** Silme | Uygulandı | `DELETE /{id}`: silme işareti (`deleted_at`) + metin ve görsellerin aynı transaction'da kalıcı silinmesi (saklama 0 gün); silme sonrası içerik ve görseller okunamaz (404); silme saatlik kotayı sıfırlamaz |
| **CONT-08** Taslak yönetimi | Uygulandı | Her içerik `DRAFT` başlar; taslak kaydedilir, sahibince okunur (`GET /{id}`) ve düzenlenir. **Taslak listesi yok** (aşağıda açık nokta) |
| **CONT-14** Görseller | Uygulandı | `POST/GET/DELETE /{id}/images`: ayrı media-service yok, baytlar `content_db`'de; içerik türü sunucuda doğrulanır, SVG/HTML/GIF reddedilir, boyut/piksel sınırı, yeniden kodlama, taslak görseline yalnız sahibi erişir |
| **CONT-18** Doğrulama ve güvenlik | Uygulandı (MVP eşikleri) | Tür/zorunlu alan/uzunluk/medya/sahiplik/rol doğrulaması; taslak ve yayın doğrulaması ayrı (`ContentPublishRules`: başlık ≥ 5, gövde ≥ 30, yalnız yayında); mükerrer içerik (409, DB indeksiyle), saatlik oluşturma sınırı 10 (429), etkin taslak sınırı 20 (409) |

## 2. Değişen / eklenen uçlar (`docs/api.md` → CONTENT)

| Method | Path | Yetki |
| --- | --- | --- |
| POST | `/api/v1/content/contents` | Authenticated (`ANNOUNCEMENT` için ADMIN) |
| GET | `/api/v1/content/contents/{contentId}` | Authenticated + sahiplik |
| PATCH | `/api/v1/content/contents/{contentId}` | Owner |
| DELETE | `/api/v1/content/contents/{contentId}` | Owner |
| POST | `/api/v1/content/contents/{contentId}/images` | Owner (taslak) |
| GET | `/api/v1/content/contents/{contentId}/images/{imageId}` | Authenticated + sahiplik |
| DELETE | `/api/v1/content/contents/{contentId}/images/{imageId}` | Owner (taslak) |

## 3. Migration

- **V101** `create_contents_tables.sql` (`contents`, `content_translations`), **V102** `create_content_images_table.sql` (`content_images`). Aralık V101–V110; **V103–V110 boş**. Yeni ENV yok.
- Docker ile ayağa kaldırıldığında iki migration `content_db`'ye uygulandı ve Hibernate `validate` geçti (container hatasız açıldı).

## 4. Kesinleşen kararlar (proje sahibi onayı) ve uygulanışı

| Konu | Karar | Uygulama |
| --- | --- | --- |
| Resmî LTZ duyurusu | `ANNOUNCEMENT` oluşturma/yayımlama yalnız `ADMIN`; `MODERATOR` yetkisi kapsamında içeriği yayından kaldırabilir; kayıtlı kullanıcı kendi `NEWS` içeriğini yayımlayabilir | Oluşturmada ADMIN kontrolü korundu (`ContentService`). Yayımlama (CONT-09) ve moderatör kaldırma (CONT-10) sonraki fazlardır |
| Eşikler (MVP) | Saatte 10 yeni içerik, 20 etkin taslak, içerik başına 6 görsel; yayında başlık ≥ 5, gövde ≥ 30 karakter (boşluklar sayılmaz), taslağı engellemez | `ContentService` sabitleri ve `ContentPublishRules` (özet asgarisi kaldırıldı); `docs/api.md`'de MVP kuralı olarak yazıldı |
| Görsel depolama | `content_db` içinde `bytea` | `docs/SECURITY.md` §13'e Content Service kararı olarak eklendi |
| Silinen içerik | Aynı transaction'da: silme işareti + metin ve görsellerin kalıcı silinmesi; saklama 0 gün; silme saatlik kotayı sıfırlamaz | `deleteContent`: `markDeleted` + `deleteByContentId` (görsel ve çeviri); parmak izi nötrlenir; silinen satırlar kotada sayılır |
| Hesap silme | Taslak, yayımlanmış ve önceden silinmiş içeriğe ait kalan kayıtlar kalıcı silinir (yayımladığı haberler de kalkar) | Politika belgelendi; **ayrı görev:** `LTZ-NIS-CONT-AUTH-01-Hesap-Silme-Gorevi.md` |

Diğer tasarım notları: metin düz metindir (zengin metin için sanitizer kararı yok); taslak doğrulaması yalnız başlık ister, yayın kuralları yayınlama fazındadır.

## 5. Bu fazda yapılmayanlar / açık işler

- **Taslak listesi** (`GET /contents/mine`) yok: kullanıcı taslağını kimliğiyle açar; listeleme yayın/akış fazıyla birlikte önerilir.
- **Hesap silme ucu** (`DELETE /internal/v1/accounts/{accountId}`) yok: Auth `content` görevini `PENDING` tutuyor. Ayrı görev olarak takip edilir (`LTZ-NIS-CONT-AUTH-01-Hesap-Silme-Gorevi.md`).
- **Alt metin, çoklu dil (CONT-15), kategori (CONT-03), kaynak (CONT-16), yayınlama (CONT-09), öne çıkarma, arama/filtre, moderasyon, outbox/inbox olayları:** sonraki fazlar.
- Gateway/edge: IP bazlı rate limit ve çerez→Bearer ile görsel erişimi uçtan uca **NOT VERIFIED**.

## 6. Testler (çalıştırıldı, 2026-11-06)

| Dosya | Kapsam |
| --- | --- |
| `ContentSecurityTest`, `JwtConfigTest`, `SecurityContextCurrentUserTest` | Gerçek RS256 token + yerel JWKS: oturumsuz/geçersiz/servis token'ı 401, sahte kimlik başlıkları, `/internal/**` kapalı |
| `ContentControllerTest`, `ContentImageControllerTest` | HTTP sözleşmesi, doğrulama, hata eşlemeleri; geçersiz/zararlı görseller; sahiplik ihlali (IDOR); oturumsuz istek |
| `ContentServiceTest`, `ContentImageServiceTest` | Rol kuralı (normal kullanıcı duyuru oluşturamaz), sahiplik, kota, mükerrer, silme, görsel kuralları |
| `ImageSanitizerTest` | PNG/JPEG kabulü, SVG/HTML/GIF/bozuk/boş/büyük/piksel sınırı, polyglot yük |
| `ContentFingerprintTest`, `ContentPublishRulesTest`, `ContentTest` | Parmak izi, yayın kuralları, entity kuralları |
| `ContentRepositoryIntegrationTest` (gerçek PostgreSQL) | Migration + Hibernate doğrulaması, kısmi tekil indeks, CHECK/FK kısıtları, cascade, sayaçlar |
| `ContentLifecycleIntegrationTest` (gerçek PostgreSQL) | Oluştur→düzenle→görsel→sil akışı, silme sonrası tutarlılık, IDOR, rol, mükerrer, kota |
| `ContentServiceApplicationTests` | Tüm bağlam açılışı (Flyway + validate + güvenlik) |

Yalnız bu servisi gerçek ENV ve uygulama veritabanından yalıtarak doğrulama:

```powershell
node pre-push/ci/backend.mjs --modules services/content-service
```

Bu koşuda Maven `clean verify`, 170 test, merkezi coverage politikası ve yeni ihlal denetimi geçti. Komutun sonundaki **ek Trivy bağımlılık taraması tamamlanamadı** (`Trivy execution failed; raw output withheld`); bu nedenle bağımlılık taraması **NOT VERIFIED**, CI komutunun toplam çıkış kodu **1**'dir. Servis testlerinin başarısı, komutun tamamının geçtiği anlamında kullanılmaz.

Yerel kalite kontrolündeki iki ön engel giderildi: sabitlenmiş Gitleaks 8.30.1 aracı resmî arşivinden SHA-256 doğrulamasıyla kuruldu; `ContentFingerprint` ayıracı compiler-aware coverage kontrolünün reddettiği Unicode escape yerine aynı ASCII karakteri üretecek biçimde yazıldı. Saklanan hash'lerin değişmediği sabit bir regresyon testiyle doğrulandı. JWT testleri doğrulanmış ve doğrulanmamış oturumları doğru kuracak şekilde düzeltildi; authority üzerinden admin yetkisi kazanılamadığı ayrıca test edildi. Controller testindeki hata fırlatan mock yeniden ayarlamaları `doThrow` ile düzeltildi.

Gateway ve gerçek auth-service ile uçtan uca doğrulama bu çalışmanın kapsamında değildir: **NOT VERIFIED**.
