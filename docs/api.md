# LTZ — API Kataloğu

> Son güncelleme: 2026-10-20
> Bu dosya projedeki **tüm API'lerin tek kaydıdır**. Bir endpoint planlandığında, eklendiğinde, değiştiğinde veya kaldırıldığında, aynı değişiklikte buraya kendi servisinin başlığı altında yazılır.
> İlgili belgeler: [architecture.md](architecture.md) (gateway ve servisler arası iletişim), [SECURITY.md](SECURITY.md) (yetkilendirme, ProblemDetail), [web-rules.md](web-rules.md) (frontend tüketimi).

---

## 1. Kurallar

1. Her endpoint, sahibi olan servisin `## <SERVİS>` başlığı altında `### <SERVİS> — <Kısa ad>` alt başlığıyla yazılır. Şablon §2'dedir.
2. Herkes yalnız **kendi servisinin** bölümünü düzenler. Başka servisin API'sini tüketiyorsan o kaydı değiştirme. Sahibine bildir veya kaydın `Tüketici servisler` satırına eklenmesini iste.
3. Endpoint kodu yazılmadan önce kayıt `PLANNED` olarak açılabilir. Kod tamamlandığında aynı değişiklikte `Durum` ve `Test Durumu` güncellenir.
4. Path'ler gateway formatındadır: `/api/v1/<servis>/...`. Servis içi (gateway'den geçmeyen) endpoint'ler `/internal/...` ile yazılır ve `Yetkilendirme: Internal` olarak işaretlenir.
5. Örnek JSON'larda gerçek kişisel veri, token, parola veya gizli değer bulunmaz.
6. Hata gövdeleri `ProblemDetail` (RFC 9457) formatındadır. Yalnız durum kodu ve anlamı listelenir.
7. Sözleşmeyi bozan değişiklikte (alan silme, tip değiştirme, path değiştirme) tüketici servislerin sahipleri bilgilendirilir. Eski kayıt `DEPRECATED` olarak bırakılır.

**Durum değerleri:** `PLANNED` · `IN PROGRESS` · `IMPLEMENTED` · `DEPRECATED`
**Test Durumu değerleri:** `NOT IMPLEMENTED` · `UNIT` · `INTEGRATION` · `UNIT + INTEGRATION` · `E2E`
**Yetkilendirme değerleri:** `Public` · `Authenticated` · `Owner` (yalnız kaynağın sahibi) · `Admin` · `Internal` (yalnız servisler arası)

---

## 2. Şablon

Yeni kayıt açarken bu bloğu kopyala. Kullanılmayan isteğe bağlı bölümleri (Query Parameters, Request Body vb.) sil.

````markdown
### <SERVİS> — <Kısa ad>

Durum: PLANNED
Sorumlu: <İsim>
Tüketici servisler: <Frontend, X Service, ... veya ->

#### Endpoint
<METHOD> /api/v1/<servis>/<yol>

#### Yetkilendirme
<Public | Authenticated | Owner | Admin | Internal>

#### Path Parameters
- <ad>: <tip>, <zorunlu/opsiyonel>

#### Query Parameters
- <ad>: <tip>, <zorunlu/opsiyonel>, <varsayılan / sınır>

#### Request Body
```json
{
  "alan": "tip / açıklama"
}
```

#### Response — <durum kodu>
```json
{
  "alan": "tip / açıklama"
}
```

#### Hata Durumları
- 400: <neden>
- 401: <neden>
- 403: <neden>
- 404: <neden>
- 409: <neden>
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
<API Gateway üzerinden /api/v1/... veya ->

Diğer mikroservisler:
<RestClient ile doğrudan bağlantı / outbox event / ->

#### Test Durumu
NOT IMPLEMENTED
````

### Örnek kayıt

Bu bir biçim örneğidir, gerçek bir kayıt değildir. Gerçek kayıt §3'teki GAME başlığına yazılır.

```markdown
### GAME — Oyun Detayı

Durum: PLANNED
Sorumlu: Hamza
Tüketici servisler: Library, Review, Recommendation

#### Endpoint
GET /api/v1/game/{gameId}

#### Yetkilendirme
Public

#### Path Parameters
- gameId: UUID, zorunlu

#### Response — 200
{
  "id": "UUID",
  "title": "Oyun adı",
  "description": "Oyun açıklaması",
  "status": "ACTIVE"
}

#### Hata Durumları
- 400: Geçersiz gameId
- 404: Oyun bulunamadı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/game/{gameId}

Diğer mikroservisler:
Game Service'e RestClient üzerinden doğrudan bağlantı.

#### Test Durumu
NOT IMPLEMENTED
```

---

## 3. Servisler

Henüz kayıtlı API yok. Kayıtlar eklendikçe her başlığın altındaki "Kayıt yok" satırı silinir.

## AUTH

Sorumlu: Kerem · Gateway: `/api/v1/auth/**` · Görevler: AUTH-

_Kayıt yok._

## USER

Sorumlu: Kerem · Gateway: `/api/v1/user/**` · Görevler: USER-

_Kayıt yok._

## MAIL

Sorumlu: Kerem · Gateway: `/api/v1/mail/**` · Görevler: MAIL-

_Kayıt yok._

## GAME

Sorumlu: Hamza · Gateway: `/api/v1/game/**` · Görevler: GAME-

_Kayıt yok._

## LIBRARY

Sorumlu: Hamza · Gateway: `/api/v1/library/**` · Görevler: LIB-

_Kayıt yok._

## REVIEW

Sorumlu: Hamza · Gateway: `/api/v1/review/**` · Görevler: REV-

_Kayıt yok._

## SOCIAL

Sorumlu: Umut · Gateway: `/api/v1/social/**` · Görevler: SOC-

_Kayıt yok._

## MESSAGING

Sorumlu: Umut · Gateway: `/api/v1/messaging/**` · Görevler: MSG-

_Kayıt yok._

## LFP

Sorumlu: Umut · Gateway: `/api/v1/lfp/**` · Görevler: LFP-

_Kayıt yok._

## COMMUNITY

Sorumlu: Nisa · Gateway: `/api/v1/community/**` · Görevler: COM-

_Kayıt yok._

## CONTENT

Sorumlu: Nisa · Gateway: `/api/v1/content/**` · Görevler: CONT-

_Kayıt yok._

## EVENT

Sorumlu: Nisa · Gateway: `/api/v1/event/**` · Görevler: EVT-

_Kayıt yok._

## HARDWARE

Sorumlu: Alper · Gateway: `/api/v1/hardware/**` · Görevler: HW-

LTZ-ALP-HW-01: dokuz protected iş endpoint'i. Tüm POST/PUT gövdelerinde **16 KiB**
sınırı vardır (Content-Length olmasa da uygulanır); aşılırsa 413 `REQUEST_TOO_LARGE`
ProblemDetail döner. 2026-10-21 son pre-push: 58 PASS, JaCoCo %96,69.
Son kod Sonar analizi: A/A/A, Quality Gate PASS (bütün backend scope; overall %84,3).
Teslim kanıtı: [LTZ-ALP-HW-01](../docs-hardware/reports/LTZ-ALP-HW-01-delivery.md).
Gerçek Auth/JWT/E2E DEFERRED_AUTH; bütün üretim iş istekleri doğrulayıcı gelene kadar reddedilir.

LTZ-ALP-HW-02: 15 yeni katalog/kampanya endpoint'i, ürün listesinde geriye uyumlu
HW-11 filtreleri ve profil seçiminde ACTIVE katalog kontrolü. Son kod pre-push
134 PASS / %98,02; Sonar A/A/A + Quality Gate PASS, overall %89,2 / duplication %0.
Yeni kod coverage %92,3, issue 0. [HW-02 teslimi](../docs-hardware/reports/LTZ-ALP-HW-02-delivery.md).
Gerçek Auth rol/claim → hardware:catalog:write eşlemesi DEFERRED_AUTH.

### HARDWARE — HW-01 Profil oluşturma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/profiles

#### Yetkilendirme
Owner. Yalnız güvenilir `AuthenticatedActor` üzerinden owner alınır; body/header kimliği
kabul edilmez. Gerçek Auth adapter'ı yokken üretim HTTP istekleri reddedilir (DEFERRED_AUTH).
CSRF etkin; gerçek cookie/JWT taşıma protokolü henüz uygulanmadı. Yeni public API yok.

#### Request Body
`CreateHardwareProfileRequest`: `name` zorunlu, 1..100 karakter, boş/kontrol/HTML ayraçları
reddedilir; `deviceType` zorunlu DESKTOP/LAPTOP/OTHER. Bilinmeyen alanlar reddedilir.

```json
{"name":"Oyun PC","deviceType":"DESKTOP"}
```

#### Response — 201
`HardwareProfileResponse`: id, name, deviceType, source (MANUAL), version, createdAt, updatedAt.
Owner UUID dönmez. `Location: /api/v1/hardware/profiles/{id}`; `Cache-Control: no-store, private`.

```json
{"id":"00000000-0000-0000-0000-000000000001","name":"Oyun PC","deviceType":"DESKTOP","source":"MANUAL","version":0,"createdAt":"2026-10-21T00:00:00Z","updatedAt":"2026-10-21T00:00:00Z"}
```

#### Hata Durumları
- 400 INVALID_REQUEST: validation, bozuk JSON, bilinmeyen alan.
- 401 AUTHENTICATION_REQUIRED: doğrulanmış aktör yok.
- 403 ACCESS_DENIED: CSRF eksik/geçersiz veya güvenilmeyen principal.
- 409 DATA_CONFLICT: veri bütünlüğü çakışması.
- 500 INTERNAL_ERROR: güvenli genel hata; iç detay dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path; gateway değiştirilmedi.
Diğer mikroservisler: çağrı yok. Gerçek Auth sözleşmesi/E2E DEFERRED_AUTH.
Flyway: V1__create_hardware_profiles.sql.

#### Test Durumu
UNIT + INTEGRATION — HardwareProfileControllerTest + HardwareProfileTest;
2026-10-21 modül testleri 19 PASS, exit 0. Gerçek PostgreSQL 17 + Flyway + JPA + MockMvc.
Canlı Auth/E2E doğrulanmadı.

### HARDWARE — HW-02 Bileşen ekleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/profiles/{profileId}/components

#### Yetkilendirme
Owner; HW-01 ile aynı onaylı güvenlik sınırı/CSRF/DEFERRED_AUTH.
Başkasına ait profil ile olmayan profil aynı 404 yanıtını verir.

#### Path Parameters
- profileId: UUID, zorunlu.

#### Request Body
`SaveHardwareComponentRequest`: profileVersion (>=0, zorunlu), category, quantity (1..128).
Manuel giriş: modelName (1..160), opsiyonel brandName (1..100); productId/brandId yok.
Katalog seçimi: productId; opsiyonel, modele bağlı brandId; manuel adlar gönderilemez.
HW-10 sonrası arşivli ürün/marka yeni seçim ve bileşen değiştirmede 404 ile reddedilir;
önceki profil snapshot'ları korunur.
Markasız genel model desteklenir. Kategori ürünle aynı olmalıdır.
Kategoriler: CPU, GPU, RAM, MOTHERBOARD, STORAGE, COOLING, PSU, MONITOR, KEYBOARD, MOUSE, HEADSET, OTHER.
Opsiyonel specifications: capacityGb (1..1048576), clockMhz (1..20000), coreCount (1..1024),
powerWatts (1..10000), memoryType/socket/interfaceType/formFactor (1..32, kontrol/HTML ayraçları yok).
Teknik değerler gönderilmezse seçilen katalog modelinin kayıtlı değerleri kullanılır;
olmayan veri null kalır. İstemci source/owner/version alanlarını atayamaz.

#### Response — 201
`ComponentMutationResponse`: profileVersion (yeni aggregate sürümü), component
(`HardwareComponentResponse`: id, category, productId?, brandId?, modelName, brandName?, quantity,
source=MANUAL, version, specifications?). Location yeni bileşen yolu, cache private/no-store.

```json
{"profileVersion":1,"component":{"id":"00000000-0000-0000-0000-000000000003","category":"RAM","productId":null,"brandId":null,"modelName":"DDR5 RAM","brandName":null,"quantity":2,"source":"MANUAL","version":0,"specifications":{"capacityGb":16,"clockMhz":6000,"coreCount":null,"powerWatts":null,"memoryType":"DDR5","socket":null,"interfaceType":null,"formFactor":null}}}
```

#### Hata Durumları
- 400 INVALID_REQUEST: kategori/marka uyuşmazlığı, karışık manuel/katalog alanları, validation.
- 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED: HW-01 güvenlik sınırı.
- 404 HARDWARE_NOT_FOUND: olmayan/başkasına ait profil veya bulunamayan ürün.
- 409 DATA_CONFLICT: eski profileVersion veya DB kısıtı.
- 500 INTERNAL_ERROR: güvenli genel hata.

#### Bağlantı
Frontend: Gateway üzerinden yukarıdaki tam path. Diğer mikroservisler: çağrı yok.
Flyway: V2__create_hardware_components_and_selection_catalog.sql; aktif katalog seçimi
V3 status sınırına uyar. Tam katalog yönetimi/arama HW-10/HW-11 kayıtlarındadır.

#### Test Durumu
UNIT + INTEGRATION — HardwareComponentControllerTest; modül 42 test PASS, exit 0 (2026-10-21).
12 kategori, markasız/markalı katalog seçimi, manuel giriş, teknik validation, IDOR,
eski profileVersion ve marka bağımsız genel model tekilliği PostgreSQL 17 ile doğrulandı.

### HARDWARE — HW-03 Profil güncelleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/hardware/profiles/{profileId}
#### Yetkilendirme
Owner; HW-01 ile aynı CSRF/DEFERRED_AUTH sınırı. Başka owner kaynağı 404 ile gizlenir.
#### Path Parameters
- profileId: UUID, zorunlu.
#### Request Body
`UpdateHardwareProfileRequest`: profileVersion >=0, name ve deviceType zorunlu;
ad/tür kuralları HW-01 ile aynı. Owner/source değiştirilemez.
#### Response — 200
`HardwareProfileResponse`; yeni version, private/no-store.
#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (eski profil sürümü); 500 INTERNAL_ERROR.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: yok. Mevcut V1; yeni migration yok.
#### Test Durumu
UNIT + INTEGRATION — HardwareProfileControllerTest güncelleme/owner/stale version PASS;
modül toplamı 47 test PASS, exit 0 (2026-10-21).

### HARDWARE — HW-03 Bileşen değiştirme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/hardware/profiles/{profileId}/components/{componentId}
#### Yetkilendirme
Owner; CSRF ve HW-01 güvenlik sınırı. Bileşenin URL'deki profile ait olması ayrıca doğrulanır.
#### Path Parameters
- profileId, componentId: UUID, zorunlu.
#### Request Body
`SaveHardwareComponentRequest`; HW-02 ile aynı kurallar. Tam değiştirme:
gönderilmeyen opsiyonel alanlar temizlenir; katalogda specs gönderilmezse katalog değerleri kullanılır.
#### Response — 200
`ComponentMutationResponse`; parent profileVersion ve bileşen version artar; kimlik/createdAt korunur.
#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT; 500 INTERNAL_ERROR.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: yok. Mevcut V1/V2; yeni migration yok.
#### Test Durumu
UNIT + INTEGRATION — HardwareComponentControllerTest güncelleme/IDOR/rollback ve paralel
writer testleri PASS; modül toplamı 47 PASS, exit 0 (2026-10-21).

### HARDWARE — HW-03 Bileşen kaldırma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/hardware/profiles/{profileId}/components/{componentId}
#### Yetkilendirme
Owner; CSRF ve HW-01 güvenlik sınırı.
#### Path Parameters
- profileId, componentId: UUID, zorunlu.
#### Query Parameters
- profileVersion: long >=0, zorunlu.
#### Response — 204
Gövdesiz; `X-Profile-Version` yeni profil sürümüdür. Profil silinmez; private/no-store.
#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT; 500 INTERNAL_ERROR.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: yok. Mevcut V1/V2; yeni migration yok.
#### Test Durumu
UNIT + INTEGRATION — HardwareComponentControllerTest silme/owner/stale version PASS;
modül toplamı 47 PASS, exit 0 (2026-10-21).

### HARDWARE — HW-04 Bilgisayarları listeleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend
#### Endpoint
GET /api/v1/hardware/profiles
#### Yetkilendirme
Owner; yalnız doğrulanmış aktörün profilleri. HW-01 güvenlik sınırı/DEFERRED_AUTH.
#### Query Parameters
- page: int >=0, varsayılan 0.
- size: int 1..100, varsayılan 20.
Sıralama sabit `createdAt ASC, id ASC`; istemci SQL/sort alanı gönderemez.
#### Response — 200
`HardwareProfilePageResponse`: items (`HardwareProfileResponse`), page, size,
totalElements, totalPages. Başka owner profilleri/sayaçları dahil edilmez. Cache private/no-store.
#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED; 500 INTERNAL_ERROR.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: yok. V1 owner/created_at/id indeksi.
#### Test Durumu
UNIT + INTEGRATION — HardwareProfileControllerTest sayfalama/izolasyon testleri PASS;
modül toplamı 52 PASS, exit 0 (2026-10-21).

### HARDWARE — HW-05 Donanım detayları

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend
#### Endpoint
GET /api/v1/hardware/profiles/{profileId}
#### Yetkilendirme
Owner; HW-01 güvenlik sınırı/DEFERRED_AUTH. Diğer kullanıcıların profilleri 404.
#### Path Parameters
- profileId: UUID, zorunlu.
#### Query Parameters
- page: int >=0, varsayılan 0 (bileşen sayfası).
- size: int 1..100, varsayılan 20.
#### Response — 200
`HardwareProfileDetailResponse`: profile, components, page, size, totalComponents, totalPages.
Profil HW-01 yanıtı, bileşenler HW-02 DTO'sudur. Eksik teknik alanlar null kalır;
seri numarası, owner, FPS/sıcaklık/tahmin dönmez. Kayıtlı teknik değerler ve birimleri
HW-02 sözleşmesindedir. Sabit createdAt/id sıralaması ve REPEATABLE_READ snapshot kullanılır.
#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 500 INTERNAL_ERROR. Cache private/no-store.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: yok. V1/V2; yeni migration yok.
#### Test Durumu
UNIT + INTEGRATION — HardwareComponentControllerTest detay/null/sayfalama/IDOR PASS;
modül toplamı 54 PASS, exit 0 (2026-10-21).

### HARDWARE — HW-15 Donanım profil istatistikleri

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend
#### Endpoint
GET /api/v1/hardware/profiles/statistics
#### Yetkilendirme
Owner; HW-01 güvenlik sınırı/DEFERRED_AUTH. Kullanıcı kimliği parametre alınmaz.
#### Response — 200
`HardwareStatisticsResponse`: totalProfiles, profilesByDeviceType,
totalComponentEntries, totalComponentUnits, componentsByCategory (entries/units),
recordedRamCapacityGb, ramUnitsWithoutCapacity, recordedStorageCapacityGb,
storageUnitsWithoutCapacity. Yalnız kayıtlı capacityGb * quantity toplanır;
eksik kapasite ayrıca sayılır, gerçek toplam kapasite iddiası yapılmaz. Boş kayıtlar
sıfır sayaç döndürür; benchmark/FPS tahmini yoktur. Cache private/no-store.
#### Hata Durumları
401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED; 500 INTERNAL_ERROR.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: yok. SQL aggregation owner filtresi
ve REPEATABLE_READ snapshot kullanır; V1/V2, yeni migration yok.
#### Test Durumu
UNIT + INTEGRATION — HardwareComponentControllerTest owner/kayıtlı kapasite/eksik veri PASS;
modül toplamı 55 PASS, exit 0 (2026-10-21).

### HARDWARE — HW-16 Profil silme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend
#### Endpoint
DELETE /api/v1/hardware/profiles/{profileId}
#### Yetkilendirme
Owner; CSRF ve HW-01 güvenlik sınırı/DEFERRED_AUTH. Admin/header bypass yoktur.
#### Path Parameters
- profileId: UUID, zorunlu.
#### Query Parameters
- profileVersion: long >=0, zorunlu; güncel profil sürümüyle eşleşmeli.
#### Response — 204
Gövdesiz. Profil ve ilişkili bileşenler transaction + FK CASCADE ile kalıcı silinir.
Diğer profiller ve katalog/marka kayıtları korunur. Cache private/no-store.
#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND (olmayan/başkasına ait/önceden silinmiş profil);
409 DATA_CONFLICT (eski sürüm); 500 INTERNAL_ERROR.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: çağrı/event yok; Performance/Recommendation
entegrasyonu bu ana taskta uygulanmaz. V1/V2; veri silen migration eklenmedi.
#### Test Durumu
UNIT + INTEGRATION — HardwareComponentControllerTest owner/CSRF/sürüm/CASCADE/izolasyon PASS;
son modül clean verify 58 PASS, exit 0 (2026-10-21). Gerçek Auth/E2E DEFERRED_AUTH.


### HARDWARE — HW-10 Marka oluşturma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/catalog/brands

#### Yetkilendirme
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Request Body
CreateHardwareBrandRequest: name (1..100), reason (1..500), zorunlu düz metin.
Bilinmeyen alanlar, HTML ayraçları/kontrol karakterleri reddedilir; gövde en fazla 16 KiB.

#### Response — 201
HardwareBrandResponse: id, name, status=ACTIVE, version=0; Location marka yolu.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (aktör başına dakikada 60 başarılı yönetim mutasyonu);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10 Marka güncelleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/hardware/catalog/brands/{id}

#### Yetkilendirme
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Path Parameters
- id: UUID, zorunlu.

#### Request Body
UpdateHardwareBrandRequest: version >=0, name (1..100), reason (1..500), zorunlu.
Bilinmeyen alanlar, HTML ayraçları/kontrol karakterleri reddedilir; gövde en fazla 16 KiB.

#### Response — 200
HardwareBrandResponse; güncel version. Arşivli kayıt değiştirilemez.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (aktör başına dakikada 60 başarılı yönetim mutasyonu);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10 Marka arşivleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/hardware/catalog/brands/{id}

#### Yetkilendirme
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Path Parameters
- id: UUID, zorunlu.

#### Request Body
ArchiveCatalogEntryRequest: JSON body version >=0, reason (1..500), zorunlu.
Bilinmeyen alanlar, HTML ayraçları/kontrol karakterleri reddedilir; gövde en fazla 16 KiB.

#### Response — 204
gövdesiz; fiziksel silme yok, eski ürün/profil referansları korunur.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (aktör başına dakikada 60 başarılı yönetim mutasyonu);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10 Marka detayı

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/catalog/brands/{id}

#### Yetkilendirme
Authenticated — doğrulanmış typed AuthenticatedActor.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Path Parameters
- id: UUID, zorunlu.

#### Response — 200
HardwareBrandResponse; yalnız ACTIVE marka, kişisel veri/audit dönmez.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10 Marka listesi

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/catalog/brands

#### Yetkilendirme
Authenticated — doğrulanmış typed AuthenticatedActor.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Query Parameters
- page: 0..10000, varsayılan 0.
- size: 1..100, varsayılan 20.
Sıralama sabit normalizedName/id veya normalizedModel/id ASC.

#### Response — 200
HardwareCatalogPageResponse<HardwareBrandResponse>: items, page, size, totalElements, totalPages; yalnız ACTIVE.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10 Genel model oluşturma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/catalog/products

#### Yetkilendirme
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Request Body
CreateHardwareProductRequest: category (12 mevcut kategori), model (1..160), opsiyonel brandIds (en fazla 100 aktif UUID), opsiyonel SpecificationsRequest, reason (1..500).
Bilinmeyen alanlar, HTML ayraçları/kontrol karakterleri reddedilir; gövde en fazla 16 KiB.

#### Response — 201
HardwareProductResponse: id, category, model, brands[], specifications?, status=ACTIVE, version=0; Location model yolu.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (aktör başına dakikada 60 başarılı yönetim mutasyonu);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10 Genel model güncelleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/hardware/catalog/products/{id}

#### Yetkilendirme
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Path Parameters
- id: UUID, zorunlu.

#### Request Body
UpdateHardwareProductRequest: version >=0, model, opsiyonel brandIds/specifications, reason. Tam değiştirme; gönderilmeyen opsiyonel alanlar temizlenir. category değiştirilemez.
Bilinmeyen alanlar, HTML ayraçları/kontrol karakterleri reddedilir; gövde en fazla 16 KiB.

#### Response — 200
HardwareProductResponse; version artar. Kullanılan ürün/marka bağını kaldırmak FK nedeniyle 409; mevcut profil snapshot'ları değişmez.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (aktör başına dakikada 60 başarılı yönetim mutasyonu);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10 Genel model arşivleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/hardware/catalog/products/{id}

#### Yetkilendirme
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Path Parameters
- id: UUID, zorunlu.

#### Request Body
ArchiveCatalogEntryRequest: JSON body version >=0, reason (1..500), zorunlu.
Bilinmeyen alanlar, HTML ayraçları/kontrol karakterleri reddedilir; gövde en fazla 16 KiB.

#### Response — 204
model ARCHIVED; profil referansları/teknik snapshot'ları ve marka bağları korunur.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (aktör başına dakikada 60 başarılı yönetim mutasyonu);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10 Genel model detayı

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/catalog/products/{id}

#### Yetkilendirme
Authenticated — doğrulanmış typed AuthenticatedActor.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Path Parameters
- id: UUID, zorunlu.

#### Response — 200
HardwareProductResponse; yalnız ACTIVE model ve ACTIVE opsiyonel markalar.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-10/HW-11 Genel model listesi, arama ve filtreleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/catalog/products

#### Yetkilendirme
Authenticated — doğrulanmış typed AuthenticatedActor.
Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH; üretim istekleri fail-closed reddedilir.
Public iş endpoint'i yok. Tüm yanıtlar private/no-store.

#### Query Parameters
- page: 0..10000, varsayılan 0.
- size: 1..100, varsayılan 20.
- q: opsiyonel 0..160; model içinde NFKC/Locale.ROOT normalizasyonuyla literal alt metin araması.
- category: mevcut 12 ComponentCategory değerinden biri.
- brandId: opsiyonel UUID; aktif modele bağlı aktif üretici.
- brand: opsiyonel 0..100; aynı aktif üreticinin adı içinde literal arama.
- minCapacityGb/maxCapacityGb: 1..1048576.
- minClockMhz/maxClockMhz: 1..20000.
- minCoreCount/maxCoreCount: 1..1024.
- minPowerWatts/maxPowerWatts: 1..10000.
- memoryType/socket/interfaceType/formFactor: opsiyonel 1..32, büyük/küçük harf duyarsız tam eşleşme.
Tüm verilen filtreler AND ile birleşir; aynı üretici brandId + brand koşullarını sağlamalıdır.
Alt sınır üst sınırı geçerse 400. Teknik alan null ise filtreyi sağlamaz.
Boş q/brand filtresizdir; marka filtresi yokken markasız genel modeller listelenir.
LIKE %, _ ve ! literal escape edilir; SQL parametreli Criteria ile üretilir.
Sıralama sabit normalizedModel/id ASC; istemci sıralama veya SQL alanı seçemez.

#### Response — 200
HardwareCatalogPageResponse<HardwareProductResponse>; marka bilgileri sayfa başına toplu yüklenir, collection fetch ile sayfalama yapılmaz.
Sayfa ve toplam sayaç aynı filtre/REPEATABLE_READ snapshot'ını kullanır.
HW-11 filtreleri HardwareCatalogSearchTest içindeki 27 yeni senaryoda PASS;
modül 117 PASS, exit 0, 2026-10-21 01:47:00 +03:00, PostgreSQL 17/Flyway V1–V4/JPA validate.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (tekillik, eski sürüm veya korunmuş ilişki);
500 INTERNAL_ERROR. İç hata/SQL veya audit içeriği dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden tam path. Diğer mikroservisler: çağrı yok.
Flyway V3__manage_hardware_catalog_and_audit.sql; V1/V2 değişmedi.
İsim tekilliği NFKC, boşluk normalizasyonu ve Locale.ROOT küçük harfle korunur;
model tekilliği category/normalized_model olup marka bağımsızdır.

#### Test Durumu
INTEGRATION — HardwareCatalogControllerTest: 32 yeni senaryo PASS; modül 90 PASS, exit 0, 2026-10-21 01:44:06 +03:00. PostgreSQL 17/Flyway V1–V3/JPA validate; gerçek Auth DEFERRED_AUTH.


### HARDWARE — HW-14 Ortak kampanya veri sözleşmesi

Kullanıcı onaylı yerel kayıtlar; dış API/scraping/fiyat çekme/URL doğrulama çağrısı yoktur.
SaveHardwareCampaignRequest ortak zorunlu alanları: title (1..160), sourceName (1..100),
sourceUrl (en fazla 2048 ASCII karakter; HTTPS, host gerekli; userinfo/fragment yok),
originalPrice ve salePrice (pozitif numeric(12,2), salePrice < originalPrice), currency
(geçerli ISO 4217 kodu; XXX gibi parasal olmayan kodlar reddedilir), startsAt/endsAt
(UTC Instant, bitiş başlangıçtan sonra), observedAt (UTC, gelecekte olamaz), reason (1..500).
Ürün aktif olmalı; bilinmeyen JSON alanları reddedilir. Mutasyon gövdesi en fazla 16 KiB.

HardwareCampaignResponse: id, productId, title, sourceName, sourceUrl, originalPrice,
salePrice, currency, discountPercent (fiyatlardan hesaplanır, iki basamak HALF_UP),
startsAt, endsAt, observedAt, status, version. Aktör UUID ve audit gerekçesi dönmez.
Kur dönüşümü ve anlık fiyat garantisi yoktur; gözlem zamanı kaynağın kayıtlı zamanıdır.
Kampanya henüz başlamadıysa/bittiyse/arşivliyse veya ürün arşivliyse normal okumada gizlenir.

### HARDWARE — HW-14 Kampanya oluşturma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/campaigns

#### Yetkilendirme
Admin — typed actor + hardware:catalog:write uygulama izni; CSRF ve gerekçeli atomik audit zorunlu.
Gerçek Auth/JWT rol/claim eşlemesi DEFERRED_AUTH; üretimde fail-closed. Public uç yok.

#### Request Body
SaveHardwareCampaignRequest: productId zorunlu UUID; version gönderilmez. Aşağıdaki ortak kampanya alanları zorunlu.

#### Response — 201
HardwareCampaignResponse; version=0; Location kampanya yolu.
Cache-Control: no-store, private.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND (olmayan veya normal okumada geçersiz kampanya/ürün);
409 DATA_CONFLICT (eski sürüm/arşivli kayıt/veri kuralı);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (tüm yönetim yazmalarıyla ortak aktör limiti);
500 INTERNAL_ERROR. Güvenli ProblemDetail; iç SQL/URL hata detayı dönmez.

#### Bağlantı
Frontend: tam Gateway path. Diğer mikroservisler/harici sağlayıcı: çağrı yok.
Flyway V5__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
currency char(3), UTC zamanlar, @Version, CHECK ve active-window indeksleri.

#### Test Durumu
INTEGRATION — HardwareCampaignControllerTest 15 senaryo PASS; modül 134 PASS, exit 0, 2026-10-21 01:53:29 +03:00. PostgreSQL 17/Flyway V1–V5/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-14 Kampanya güncelleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/hardware/campaigns/{id}

#### Yetkilendirme
Admin — typed actor + hardware:catalog:write uygulama izni; CSRF ve gerekçeli atomik audit zorunlu.
Gerçek Auth/JWT rol/claim eşlemesi DEFERRED_AUTH; üretimde fail-closed. Public uç yok.

#### Path Parameters
- id: UUID, zorunlu.

#### Request Body
SaveHardwareCampaignRequest: version >=0 zorunlu, productId gönderilmez (ürün referansı değişmez); diğer alanlar ortak sözleşmeyle tam değiştirilir.

#### Response — 200
HardwareCampaignResponse; version artar; eski sürüm 409.
Cache-Control: no-store, private.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND (olmayan veya normal okumada geçersiz kampanya/ürün);
409 DATA_CONFLICT (eski sürüm/arşivli kayıt/veri kuralı);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (tüm yönetim yazmalarıyla ortak aktör limiti);
500 INTERNAL_ERROR. Güvenli ProblemDetail; iç SQL/URL hata detayı dönmez.

#### Bağlantı
Frontend: tam Gateway path. Diğer mikroservisler/harici sağlayıcı: çağrı yok.
Flyway V5__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
currency char(3), UTC zamanlar, @Version, CHECK ve active-window indeksleri.

#### Test Durumu
INTEGRATION — HardwareCampaignControllerTest 15 senaryo PASS; modül 134 PASS, exit 0, 2026-10-21 01:53:29 +03:00. PostgreSQL 17/Flyway V1–V5/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-14 Kampanya arşivleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/hardware/campaigns/{id}

#### Yetkilendirme
Admin — typed actor + hardware:catalog:write uygulama izni; CSRF ve gerekçeli atomik audit zorunlu.
Gerçek Auth/JWT rol/claim eşlemesi DEFERRED_AUTH; üretimde fail-closed. Public uç yok.

#### Path Parameters
- id: UUID, zorunlu.

#### Request Body
ArchiveCatalogEntryRequest: JSON body version >=0 ve reason (1..500) zorunlu.

#### Response — 204
fiziksel silme yok; kampanya/audit kanıtları korunur.
Cache-Control: no-store, private.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND (olmayan veya normal okumada geçersiz kampanya/ürün);
409 DATA_CONFLICT (eski sürüm/arşivli kayıt/veri kuralı);
413 REQUEST_TOO_LARGE; 429 RATE_LIMITED + Retry-After: 60 (tüm yönetim yazmalarıyla ortak aktör limiti);
500 INTERNAL_ERROR. Güvenli ProblemDetail; iç SQL/URL hata detayı dönmez.

#### Bağlantı
Frontend: tam Gateway path. Diğer mikroservisler/harici sağlayıcı: çağrı yok.
Flyway V5__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
currency char(3), UTC zamanlar, @Version, CHECK ve active-window indeksleri.

#### Test Durumu
INTEGRATION — HardwareCampaignControllerTest 15 senaryo PASS; modül 134 PASS, exit 0, 2026-10-21 01:53:29 +03:00. PostgreSQL 17/Flyway V1–V5/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-14 Kampanya detayı

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/campaigns/{id}

#### Yetkilendirme
Authenticated — doğrulanmış typed actor.
Gerçek Auth/JWT rol/claim eşlemesi DEFERRED_AUTH; üretimde fail-closed. Public uç yok.

#### Path Parameters
- id: UUID, zorunlu.

#### Response — 200
HardwareCampaignResponse; yalnız aktif ürüne bağlı ACTIVE kampanya ve startsAt <= şimdi < endsAt.
Cache-Control: no-store, private.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND (olmayan veya normal okumada geçersiz kampanya/ürün);
409 DATA_CONFLICT (eski sürüm/arşivli kayıt/veri kuralı);
500 INTERNAL_ERROR. Güvenli ProblemDetail; iç SQL/URL hata detayı dönmez.

#### Bağlantı
Frontend: tam Gateway path. Diğer mikroservisler/harici sağlayıcı: çağrı yok.
Flyway V5__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
currency char(3), UTC zamanlar, @Version, CHECK ve active-window indeksleri.

#### Test Durumu
INTEGRATION — HardwareCampaignControllerTest 15 senaryo PASS; modül 134 PASS, exit 0, 2026-10-21 01:53:29 +03:00. PostgreSQL 17/Flyway V1–V5/JPA validate; gerçek Auth DEFERRED_AUTH.

### HARDWARE — HW-14 Geçerli kampanya listesi

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/campaigns

#### Yetkilendirme
Authenticated — doğrulanmış typed actor.
Gerçek Auth/JWT rol/claim eşlemesi DEFERRED_AUTH; üretimde fail-closed. Public uç yok.

#### Query Parameters
- productId: opsiyonel UUID, belirli ürünün geçerli kampanyalarını filtreler.
- page: 0..10000, varsayılan 0.
- size: 1..100, varsayılan 20.
Sıralama startsAt/id ASC; filtre/sayaç REPEATABLE_READ snapshot'ında.

#### Response — 200
HardwareCatalogPageResponse<HardwareCampaignResponse>: items, page, size, totalElements, totalPages; detail ile aynı geçerlilik filtresi.
Cache-Control: no-store, private.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND (olmayan veya normal okumada geçersiz kampanya/ürün);
409 DATA_CONFLICT (eski sürüm/arşivli kayıt/veri kuralı);
500 INTERNAL_ERROR. Güvenli ProblemDetail; iç SQL/URL hata detayı dönmez.

#### Bağlantı
Frontend: tam Gateway path. Diğer mikroservisler/harici sağlayıcı: çağrı yok.
Flyway V5__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
currency char(3), UTC zamanlar, @Version, CHECK ve active-window indeksleri.

#### Test Durumu
INTEGRATION — HardwareCampaignControllerTest 15 senaryo PASS; modül 134 PASS, exit 0, 2026-10-21 01:53:29 +03:00. PostgreSQL 17/Flyway V1–V5/JPA validate; gerçek Auth DEFERRED_AUTH.

## RECOMMENDATION

Sorumlu: Alper · Gateway: `/api/v1/recommendation/**` · Görevler: REC-

_Kayıt yok._

## PERFORMANCE

Sorumlu: Alper · Gateway: `/api/v1/performance/**` · Görevler: PERF-, PWEB-

_Kayıt yok._

## NOTIFICATION

Sorumlu: Ortak (belirlenecek) · Gateway: `/api/v1/notification/**`

_Kayıt yok._

## ADMIN

Sorumlu: Ortak (belirlenecek) · Gateway: `/api/v1/admin/**`

_Kayıt yok._
