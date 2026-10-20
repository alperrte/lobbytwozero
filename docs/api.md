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
Flyway: V2__create_hardware_components_and_selection_catalog.sql. Tam katalog yönetimi/arama yok.

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
