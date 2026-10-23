# LTZ-ALP-HW-02 — Donanım Model Kataloğu, Arama ve Kampanyalar

Tarih: **2026-10-21** (Europe/Istanbul). Sorumlu: **Alper**.
Durum: **DONE — DEFERRED_AUTH**. Alt görevler **3/3**, toplam **10/17**.
Kullanıcı 2026-10-21 tarihinde PDA kapanışını bildirdi; ana task kutusu **[x]**.
Kullanıcının **6756210** commit'i salt okunur doğrulandı.

## 1. Teslim kapsamı ve kanıt

| Alt görev | Sonuç | Uygulama ve kanıt |
|---|---|---|
| HW-10 Donanım ürün kataloğu | PASS | Marka/genel model create/update/detail/list/archive; markadan bağımsız DB tekilliği, opsiyonel çoklu üretici, version, audit, arşiv/ref ve owner profil regresyonları |
| HW-11 Donanım arama ve filtreleme | PASS | GET katalogda model/kategori/üretici ve sekiz teknik alana typed filtre; literal LIKE escape, AND koşulları, distinct sayaç/sayfa, indeks kontrolleri |
| HW-14 Donanım kampanyaları | PASS | Kaynak/gözlem/fiyat/para birimi/geçerlilik içeren yerel kayıt; create/update/archive/detail/list, aktif ürün/zaman filtresi, fiyat/zaman/URL/izin ve concurrency testleri |

HW-01'in profil işlevleri korunur. Yeni katalog seçimi arşivli ürün/markayı reddeder;
önceki bileşen ad/marka/teknik snapshot'ları değişmez. Manuel bileşen girişi sürer.
User görünürlük tercihleri, karşılaştırma, yorum/moderasyon, masaüstü sync ve
servisler arası yeni API/adapter bu görevin kapsamına alınmadı.

## 2. API envanteri

Kanonik ayrıntılar: [docs/api.md — HARDWARE](../../docs/api.md#hardware).
Tam prefix **/api/v1/hardware**; Gateway yolu kırpılmadı/değiştirilmedi.
Aşağıdaki 15 yeni endpoint ile mevcut 9 profil/bileşen endpoint'i toplam 24 iş API'sidir.

| Method | Prefix sonrası path | Davranış / DTO |
|---|---|---|
| POST | /catalog/brands | CreateHardwareBrandRequest → HardwareBrandResponse, 201 |
| GET | /catalog/brands | Aktif marka sayfası, 200 |
| GET | /catalog/brands/{id} | Aktif marka detayı, 200 |
| PUT | /catalog/brands/{id} | UpdateHardwareBrandRequest, güncel version, 200 |
| DELETE | /catalog/brands/{id} | ArchiveCatalogEntryRequest JSON body; arşiv, 204 |
| POST | /catalog/products | CreateHardwareProductRequest → HardwareProductResponse, 201 |
| GET | /catalog/products | HW-11 filtreli/sayfalı aktif genel modeller, 200 |
| GET | /catalog/products/{id} | Aktif model ve opsiyonel aktif üreticiler, 200 |
| PUT | /catalog/products/{id} | UpdateHardwareProductRequest; kategori değişmez, 200 |
| DELETE | /catalog/products/{id} | Sürüm/gerekçeyle arşiv, 204 |
| POST | /campaigns | SaveHardwareCampaignRequest + productId, version yok, 201 |
| GET | /campaigns | Geçerli kampanya sayfası; opsiyonel productId filtresi, 200 |
| GET | /campaigns/{id} | Yalnız aktif ürün ve geçerlilik penceresi, 200 |
| PUT | /campaigns/{id} | Aynı request + version, productId yok; tam değiştirme, 200 |
| DELETE | /campaigns/{id} | ArchiveCatalogEntryRequest; fiziksel silme yok, 204 |

Yeni listelerde page 0..10000, size 1..100 (varsayılan 0/20).
Katalog normalizedModel/id, marka normalizedName/id, kampanya startsAt/id ASC sıralıdır.
Model ve kampanya listeleri count/page/hydration için REPEATABLE_READ kullanır.
Üreticiler sayfa başına toplu fetch edilir; collection fetch üzerinden sayfalama yapılmaz.

HW-11: q/brand literal alt metin, category ve brandId, capacityGb/clockMhz/coreCount/
powerWatts min-max sınırları; memoryType/socket/interfaceType/formFactor case-insensitive
tam eşleşme. Bilinmeyen teknik alan eşleşmez; marka filtresi yokken markasız modeller
dışlanmaz. Filtreler AND'dir, brandId ve brand aynı üreticide sağlanır.
SQL değerleri parametrik; LIKE %, _ ve ! literal escape edilir.
Katalog adları NFKC/boşluk/Locale.ROOT ile normalize edilir; dönüşüm sonrası da
HTML ayraçları ve kontrol karakterleri reddedilir.

## 3. Güvenlik, audit ve gerçek Auth sınırı

Kullanıcı onayı: [HW02 sözleşmesi](../decisions/HW02-catalog-campaign-proposal.md).
Okumalar doğrulanmış typed actor; yönetim yazmaları ayrıca
**hardware:catalog:write** uygulama izni ister. Normal kullanıcı küresel veriyi
değiştiremez. Bu ad bir Auth rolü/JWT claim'i değildir; gerçek eşleme ertelenmiştir.
İzin servis sınırındaki CatalogWriteAccess portunda da ayrıca kontrol edilir.

Üretimde authenticator/provider eklenmedi; header, Basic, cookie veya bearer girdisi
typed actor oluşturamaz. Korumalı iş istekleri fail-closed reddedilir. Mock aktörler
yalnız testlerde bulunur. CSRF etkin, tüm hardware POST/PUT/DELETE gövdeleri gerçek
okunan byte sayısıyla **16 KiB** sınırında. Yanıtlar private/no-store ve ProblemDetail.

Yönetim yazmaları gerekçeli audit ile aynı DB transaction'ındadır; audit actor UUID,
eylem, hedef UUID, gerekçe ve UTC zaman tutar. UPDATE/DELETE/TRUNCATE trigger ile
engellenir. Aktör başına dakikada 60 başarılı yönetim mutasyonu sınırı DB advisory
kilidi/audit sayacıyla servis instance'ları arasında korunur. 429 + Retry-After: 60;
audit veya rate kontrolü başarısızsa iş mutasyonu da rollback olur.

Kampanya kaynağı yalnız yetkili kişinin beyanıdır. HTTPS host gerekli, userinfo/
fragment/HTTPS dışı URL reddedilir; ASCII kodlanmış kaynak URL en fazla 2048 karakter.
Sunucu URL'ye istek atmaz; provider/scraping/otomatik fiyat doğrulaması yoktur.
Fiyatlar pozitif numeric(12,2), indirimli fiyat orijinalden küçüktür. ISO para birimi,
gözlem zamanı ve başlangıç/bitiş zorunludur; gelecekteki gözlem reddedilir.
Yüzde iki basamak HALF_UP ile hesaplanır; kur dönüşümü/anlık fiyat garantisi yoktur.
Normal okuma yalnız ACTIVE kampanya + ACTIVE ürün + startsAt <= şimdi < endsAt döndürür.

## 4. Veri modeli ve migration

| Migration | İçerik |
|---|---|
| V3__manage_hardware_catalog_and_audit.sql | Ürün/marka ACTIVE/ARCHIVED, version CHECK, aktif ad/model/kategori indeksleri; append-only hardware_catalog_audit ve trigger |
| V4__index_hardware_catalog_specification_filters.sql | Aktif ürünlerde kategori + kapasite/frekans/çekirdek/güç partial indeksleri |
| V5__create_hardware_campaigns.sql | Ürün FK, kaynak/fiyat/char(3) para birimi/UTC zamanlar/status/version, CHECK ve aktif ürün/pencere indeksleri |

**V1/V2 değiştirilmedi.** V3/V4/V5 uygulandıktan sonra da yeniden yazılmadı.
Yalnız hardware_db; user/actor UUID dış REF olup başka servisin DB'sine FK yoktur.
Ürün/marka ve kullanılan profil bileşeni bağları gerçek FK ile korunur; kullanılmakta
olan üretici bağını kaldırma 409 verir. Katalog/kampanya DELETE arşivlemedir; kayıt,
ad tekilliği ve eski referanslar korunur. Güncellemeler pessimistic lock + version ister.
Flyway ve ddl-auto=validate/open-in-view=false korunur. Canlı DB'ye bu migration'lar uygulanmadı.

## 5. Dosya ve uygulama envanteri

Kaynak: backend/services/hardware-service/src/main/java/com/ltz/hardware/.

- 27 yeni üst seviye production Java dosyası; 12 mevcut production Java dosyasında
  kapsam içi değişiklik. API entity döndürmez; request/response record'ları kullanır.
- Controller: HardwareCatalogController ve HardwareCampaignController.
- Application: HardwareBrandService, HardwareCatalogService, HardwareCampaignService;
  HardwareCatalogMapper/HardwareCampaignMapper, CatalogWriteAccess.
- Domain: CatalogStatus, CatalogText, HardwareCampaign; HardwareBrand/Product yönetim
  metotları; HardwareRateLimitException. Sonar için Campaign.Terms,
  Component.Selection, Specifications.Values küçük domain record'ları.
- Infrastructure: HardwareCampaignRepository, HardwareCatalogAuditRepository,
  HardwareCatalogSearch, SecurityContextCatalogWriteAccess; mevcut repository,
  body filter, SecurityFilterChain ve exception/actor sınırları güncellendi.
- Dört yeni test sınıfı: HardwareCatalogControllerTest, HardwareCatalogSearchTest,
  HardwareCampaignControllerTest, SecurityContextCatalogWriteAccessTest.
  Mevcut HardwareComponentControllerTest fixture ve lambda'sı güncellendi.
- docs/api.md yalnız HARDWARE; database/ER/architecture hardware kayıtları,
  master checklist ve docs-hardware progress/quality/karar/teslim dosyaları güncellendi.
  Önceki HW-01 PDA kapanışının dört dokümantasyon değişikliği korundu.
- Yeni dependency, ENV, Compose, Gateway, ortak runner veya başka servis kodu değişikliği yok.

## 6. Son test ve kalite kanıtları

| Kontrol | Son doğrulanmış sonuç |
|---|---|
| Hardware regresyonları | 134 PASS; 0 failure/error/skipped |
| Test dağılımı | Katalog 33 + arama 27 + kampanya 15 + doğrudan güvenlik 1 + önceki 58 |
| Gerçek DB | Testcontainers PostgreSQL 17.11; V1–V5 Flyway/JPA validate PASS |
| Hardware JaCoCo | 694/708 satır = **%98,02**, exclusion eklenmedi |
| Final pre-push | **PASS**, exit 0; 2026-10-21 02:01:49 +03:00; BACKEND TEST CHECK PASSED |
| Sonar öncesi bütün backend | 152 PASS, 18 modül; ağırlıklı yerel JaCoCo %93,68 |
| Sonar Security / Reliability / Maintainability | **A / A / A** |
| Quality Gate / bütün kod hedefleri | **PASS / PASS**, scan exit 0 |
| Sonar overall coverage / duplication | **%89,2 / %0** |
| Sonar yeni kod coverage / duplication / yeni issue | **%92,3 / %0 / 0**, PREVIOUS_VERSION baseline |
| Açık issue / vulnerability / hotspot | **0 / 0 / 0**, son analiz sonrası yerel API kontrolü |

Sonar proje **ltz-backend**; yerel Git branch **feature/hardware-service**;
mevcut runner ayrı Sonar branch parametresi göndermez.
Son analiz **1d274361-0d59-4aee-9e54-3303c0ef52e5**,
**2026-10-21 02:04:53 +03:00** (server: 2026-10-20T23:04:53+0000).
CE task **af69ad0f-b846-4f1c-87b6-04604cd76d36**.
Scan bitişi **02:05:11 +03:00**, exit 0. Atanmış **Sonar way** politikası VERIFIED;
gerçek analiz gate koşulları ve sıfır yeni issue doğrulandı.
[Sonar paneli](http://localhost:5199/dashboard?id=ltz-backend).

Komutlar: mevcut kullanıcı onayına dayanarak pre-push.cmd hardware-service ve
sonar.cmd scan dev. Pre-push backend-only; full Compose/health kontrolü iddiası yoktur.

Kanıt logları:

- logs/pre-push-2026-10-20T23-01-26-677Z-14516-d2f69fe4.log
- logs/sonarqube-scan-2026-10-20T23-02-21-151Z-40956-fd31b65b.log

İlk Sonar Reliability C/new issue FAIL sonucu gizlenmedi: 9 bulgu hardware içinde
düzeltildi, sonra **iki kapı da son kodda yeniden PASS** oldu.
Ayrıntılı geçmiş: [kalite raporu](../testing/LTZ-ALP-HW-02-quality.md).

## 7. Açık kalanlar ve teslim sınırı

**DEFERRED_AUTH: gerçek Auth/JWT doğrulaması ve rol/claim eşlemesi/E2E tamamlanmadı;
production-ready değildir.** İlgili mutabakat [Auth kaydında](../decisions/auth-integration.md).

Kampanyalar kaynak/gözlem bilgili yerel kayıtlar olup dış sağlayıcıdan doğrulanmış
anlık fiyat değildir. Üretim verisi üzerinde P95/yük ölçümü yapılmadı; sorgu testleri
ve indeks varlığı kanıtı performans garantisi diye sunulmaz. Canlı DB migration
uygulaması ve gerçek Gateway/Auth E2E bu teslimde doğrulanmadı.

HW-10/HW-11/HW-14 ve iki kalite kapısı doğrulandı; kullanıcı PDA üzerinden yalnız
**LTZ-ALP-HW-02** ana görevini kapatabilir. Ana kutu kapanış bildirimi gelene kadar açık.
Ajan Git add/commit/push veya PDA durum değişikliği yapmadı.
Teslim sonrası kullanıcı PDA kapanışını bildirdi ve HW-03'e geçişi onayladı.
HW-04/HW-05 için henüz izin verilmedi.
