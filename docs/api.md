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

LTZ-ALP-HW-03: 7 yeni endpoint; profil/alan/kategori gizliliği, owner teknik
karşılaştırmalar ve Performance/Recommendation için owner batch DTO sözleşmesi.
Yerel testler 174 PASS / JaCoCo %98,44; full dev 192 backend testi + 18 HTTP PASS /
SAFE TO COMMIT; Sonar A/A/A + Quality Gate PASS, açık issue/hotspot 0.
Final kanıt [HW-03 kalite kaydında](../docs-hardware/testing/LTZ-ALP-HW-03-quality.md).
User/Social gerçek policy ve consumer E2E açık kullanıcı onayıyla
DEFERRED_CONTRACT / NOT_VERIFIED. Üretimde non-owner paylaşım deny edilir.

LTZ-ALP-HW-04: 10 yeni yorum/rapor/kaynak moderasyon endpoint'i; toplam 41 iş ucu.
Hardware 239 PASS (65 yeni), JaCoCo %98,66; nihai full dev 257 backend testi,
%95,98 ve 18 HTTP PASS / SAFE TO COMMIT. Nihai Sonar A/A/A + Quality Gate PASS,
overall %92,7 / new %94,7 / duplication %0, açık issue/hotspot 0/0.
[HW-04 kalite kanıtı](../docs-hardware/testing/LTZ-ALP-HW-04-quality.md).
Üretim User/Social UGC policy başkalarını deny eder; gerçek Auth DEFERRED_AUTH,
Admin/Notification/User-Social UGC kontrat/E2E DEFERRED_CONTRACT / NOT_VERIFIED.
Yerel PENDING outbox, rapor/bildirim teslim edildiği anlamına gelmez.

LTZ-ALP-HW-05: açık onaylı preview ve profil/cihaz bazlı grant–revoke/idempotent
snapshot için beş yeni endpoint; toplam 46 business uç. Donanım detay ve owner
batch DTO'larında component.source MANUAL/DESKTOP_SYNC ayrılır; profilin MANUAL
oluşturulma kökeni korunur. Manuel PUT synced kaydı MANUAL'a ayırır.
Nihai hardware 293 PASS/%98,75; tüm backend full dev 311 PASS/%96,51,
18 HTTP PASS ve SAFE TO COMMIT. Sonar A/A/A + gate PASS; overall %93,4/new %95,0,
duplication %0, açık issue/hotspot 0/0. [HW-05 kalite](../docs-hardware/testing/LTZ-ALP-HW-05-quality.md),
[final servis envanteri](../docs-hardware/reports/HARDWARE_SERVICE_FINAL_REPORT.md).
Gerçek kullanıcı/cihaz Auth DEFERRED_AUTH; PERF aktif cihaz/pairing/iptal kontratı
ve desktop E2E onaylı DEFERRED_CONTRACT / NOT_VERIFIED. Production cihaz policy deny.

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
HW-07: Owner DESKTOP_SYNC kaydını bu manuel PUT ile değiştirirse source=MANUAL
ve sync key=null olur. Sonraki snapshot bu kaydı ezmez; aynı tarama key'i için
ayrı bir otomatik kayıt oluşturabilir. İstemciden source/key alınmaz.
#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT; 500 INTERNAL_ERROR.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: yok. Mevcut V1/V2; yeni migration yok.
#### Test Durumu
UNIT + INTEGRATION — HardwareComponentControllerTest güncelleme/IDOR/rollback ve paralel
writer testleri PASS; modül toplamı 47 PASS, exit 0 (2026-10-21).
HW-07 manualOverrideDetachesSourceAndSubsequentScanPreservesIt testi PASS;
V10 source CHECK'iyle son 293 test / %98,75 (20:36:36 +03) doğrulandı.

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
HW-07: İzin aktifken silinen DESKTOP_SYNC kaydı sonraki snapshot'ta yeniden
oluşabilir; sürekli otomatik güncellemeyi durdurmak için sync-consent iptal edilir.
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
HW-07 sonrası components.source MANUAL veya DESKTOP_SYNC olabilir; profilin
source=MANUAL değeri oluşturulma kökeni olarak korunur. Sync key/cihaz kimliği dönmez.
#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED;
404 HARDWARE_NOT_FOUND; 500 INTERNAL_ERROR. Cache private/no-store.
#### Bağlantı
Frontend: Gateway tam path. Diğer mikroservisler: yok. V1/V2; yeni migration yok.
#### Test Durumu
UNIT + INTEGRATION — HardwareComponentControllerTest detay/null/sayfalama/IDOR PASS;
modül toplamı 54 PASS, exit 0 (2026-10-21).
HW-07 otomatik/manuel küme koruma testi ve 293 test / %98,75 PASS (20:36:36 +03).

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

### HARDWARE — HW-08 Gizlilik kurallarını okuma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/profiles/{profileId}/privacy

#### Yetkilendirme
Typed actor ve owner-only; katalog yazma izni başka profil için erişim sağlamaz.
DEFERRED_AUTH; gerçek token doğrulaması yokken üretimde fail-closed.

#### Path/Query Parameters
profileId: UUID. Query yok.

#### Request Body
Yok.

#### Response — 200
HardwarePrivacyResponse: profileId, profileVersion, visibility, fields, categories.
visibility PRIVATE/FRIENDS/PUBLIC. Eksik map anahtarı PRIVATE; yeni/eski profiller
PRIVATE başlar. fields anahtarları PROFILE_NAME, DEVICE_TYPE, COMPONENT_MODEL,
COMPONENT_BRAND, COMPONENT_QUANTITY, TECHNICAL_SPECIFICATIONS; categories 12 mevcut
ComponentCategory değerinden oluşur. no-store, private.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED,
404 HARDWARE_NOT_FOUND (olmayan ve başkasına ait kaynak aynı), 500 INTERNAL_ERROR.

#### Bağlantı
V6__add_hardware_profile_privacy.sql; User/Social global karar sözleşmesi
DEFERRED_CONTRACT / NOT_VERIFIED; owner okuması dış karar gerektirmez.

#### Test Durumu
INTEGRATION — HardwarePrivacyControllerTest; PostgreSQL 17 + Flyway + MockMvc.
2026-10-21 ilk modül verify 150 PASS, coverage 776/790=%98,23.

### HARDWARE — HW-08 Gizlilik kurallarını değiştirme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/hardware/profiles/{profileId}/privacy

#### Yetkilendirme
Owner-only typed actor; CSRF zorunlu. DEFERRED_AUTH / fail-closed.

#### Path/Query Parameters
profileId: UUID. Query yok.

#### Request Body
UpdateHardwarePrivacyRequest: profileVersion >=0, visibility, fields (en fazla 6),
categories (en fazla 12), hepsi zorunlu; map değerleri null olamaz. Bilinmeyen alan,
enum, userId ve rol reddedilir. PUT iki map'i tamamen değiştirir; çıkarılan kural
PRIVATE olur. 16 KiB üst sınır. Sentetik örnek:

```json
{"profileVersion":0,"visibility":"PUBLIC","fields":{"DEVICE_TYPE":"PUBLIC","COMPONENT_QUANTITY":"FRIENDS"},"categories":{"RAM":"PUBLIC"}}
```

#### Response — 200
HardwarePrivacyResponse; artırılmış profileVersion ve kaydedilen kurallar.
Parent pessimistic write lock + aggregate @Version; component/profil değişiklikleri
ile aynı sürüm sınırı. no-store, private.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED (CSRF dahil),
404 HARDWARE_NOT_FOUND, 409 DATA_CONFLICT (eski sürüm), 413 REQUEST_TOO_LARGE,
500 INTERNAL_ERROR. ProblemDetail güvenli kodlarla döner.

#### Bağlantı
HardwarePrivacyService; V6 kural tabloları profil silindiğinde CASCADE temizlenir.
Yeni Auth rolü/claim, ENV veya dış servis URL'si tanımlanmaz.

#### Test Durumu
INTEGRATION — HardwarePrivacyControllerTest: ownership, validation, CSRF,
replace/default semantics, version conflict ve rollback. Gerçek Auth NOT_VERIFIED.

### HARDWARE — HW-08 Filtrelenmiş profil paylaşımı

Durum: IMPLEMENTED — yerel kurallar; gerçek paylaşım DEFERRED_CONTRACT
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/profiles/{profileId}/shared

#### Yetkilendirme
Anonim erişim yok. Owner kendi önizlemesinde bütün kayıtlı alanlarını görür.
Non-owner için profile visibility, alan ve kategori kurallarının hepsi izin
vermelidir. PUBLIC yalnız doğrulanmış kullanıcı; FRIENDS güvenilir arkadaşlık kararı
gerektirir. HardwareVisibilityPolicy ayrıca global User görünürlüğü ve Social
ilişki/engel kararını onaylamalıdır. Engelli, bilinmeyen, null veya erişilemeyen
dış karar 404. Üretim UnavailableHardwareVisibilityPolicy daima deny döndürür.
Test mock'u gerçek entegrasyon değildir. Owner detail/mutation uçları owner-only kalır.

#### Path/Query Parameters
profileId UUID; page 0..10000 varsayılan 0; size 1..100 varsayılan 20.
Sıralama createdAt,id ASC. Sayfalama kategori filtresinden sonra veritabanında yapılır.

#### Request Body
Yok.

#### Response — 200
SharedHardwareProfileResponse: profileId; izinli ise name/deviceType; components;
page {number,size,totalElements,totalPages}. Component yalnız category ve izinli
modelName,brandName,quantity,specifications içerir. Gizli alanlar JSON'dan çıkarılır.
Owner UUID, bileşen/model/marka kimlikleri, sürümler, tarihler ve gizlilik kuralları
bu DTO'da bulunmaz. Bütün component alanları gizliyse bileşen ve sayacı da gizlenir.
Toplam yalnız görünür kategorileri sayar; gizli kategoriler toplamı etkilemez.
no-store, private.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED,
404 HARDWARE_NOT_FOUND (gizli/olmayan profil aynı), 500 INTERNAL_ERROR.

#### Bağlantı
V6; HardwareVisibilityPolicy portu. User/Social gerçek adapter ve E2E
DEFERRED_CONTRACT / NOT_VERIFIED, kullanıcı tarafından açıkça ertelendi.

#### Test Durumu
INTEGRATION — HardwarePrivacyControllerTest: PRIVATE/FRIENDS/PUBLIC, non-owner
IDOR, dış deny/null/error, filtrelenmiş sayfa/sayaç, alan/ID sızıntısı, owner önizleme.
İlk verify 150 PASS; gerçek User/Social ve Auth E2E NOT_VERIFIED.

### HARDWARE — HW-09 Bilgisayar profillerini karşılaştırma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/comparisons/profiles

#### Yetkilendirme
Typed actor; iki profil de aynı aktöre ait olmalı. PUBLIC paylaşım veya katalog
yazma izni başka owner'ın profilini karşılaştırma hakkı vermez. CSRF zorunlu;
DEFERRED_AUTH, üretimde fail-closed. Read-only REPEATABLE_READ transaction.

#### Path/Query Parameters
Yok.

#### Request Body
CompareHardwareIdsRequest: leftId, rightId zorunlu UUID; aynı UUID kabul edilir.
Bilinmeyen alanlar reddedilir, 16 KiB body sınırı korunur.

#### Response — 200
HardwareComparisonResponse {left,right}; her HardwareProfileComparisonItem
profileId,deviceType,categories,ram,storage içerir. 12 kategorinin her biri
entries/kayıt ve units/adet gösterir; boş kategori sıfır. ram/storage alanları
knownCapacityGb (kayıtlı kapasite x adet) ve unitsWithoutCapacity içerir. Bilinmeyen
kapasite uydurulmaz; sıfır bilinen toplam yanında bilinmeyen adet açıkça gösterilir.
Kişisel profil adı/owner UUID, FPS, puan, kazanan veya uyumluluk önerisi yok.
no-store, private. SQL aggregate yalnız iki doğrulanmış owner profiliyle sınırlıdır.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED (CSRF dahil),
404 HARDWARE_NOT_FOUND (başkasına ait/olmayan profil aynı), 413 REQUEST_TOO_LARGE,
500 INTERNAL_ERROR.

#### Bağlantı
HardwareComparisonService; mevcut V1/V2 teknik verileri, yeni migration yok.
Auth gerçek E2E NOT_VERIFIED; başka servise çağrı yapılmaz.

#### Test Durumu
INTEGRATION — HardwareComparisonControllerTest; PostgreSQL 17/Flyway/MockMvc:
owner izolasyonu, public profil IDOR, bilinmeyen kapasite, adet/toplam, aynı profil.

### HARDWARE — HW-09 Profil bileşenlerini karşılaştırma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/comparisons/components

#### Yetkilendirme
Typed actor, iki profil ve iki bileşen owner-only; her componentId belirtilen
profileId'ye ait olmalı. CSRF, no-store ve DEFERRED_AUTH korunur.

#### Path/Query Parameters
Yok.

#### Request Body
CompareHardwareComponentsRequest: left ve right zorunlu; her biri
{profileId: UUID, componentId: UUID}. İç içe Bean Validation; aynı kategori zorunlu.

#### Response — 200
HardwareComparisonResponse {left,right}; HardwareTechnicalComparisonItem:
id,category,modelName,quantity,specifications. SpecificationsResponse sekiz kayıtlı
alanı içerir: capacityGb,clockMhz,coreCount,powerWatts,memoryType,socket,interfaceType,
formFactor. Veri yoksa null; tüm değerleri yoksa specifications null.
Farkın performans/FPS veya öneri olduğu iddia edilmez. no-store, private.

#### Hata Durumları
400 INVALID_REQUEST (farklı kategori dahil), 401 AUTHENTICATION_REQUIRED,
403 ACCESS_DENIED, 404 HARDWARE_NOT_FOUND (yanlış profil/bileşen veya owner),
413 REQUEST_TOO_LARGE, 500 INTERNAL_ERROR.

#### Bağlantı
HardwareComparisonService; yalnız hardware_db, gerçek Auth NOT_VERIFIED.

#### Test Durumu
INTEGRATION — HardwareComparisonControllerTest: cross-owner/cross-profile,
eksik bileşen, kategori uyuşmazlığı, bilinmeyen teknik alanlar, CSRF/typed actor.

### HARDWARE — HW-09 Genel katalog modellerini karşılaştırma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/comparisons/catalog-products

#### Yetkilendirme
Typed actor; katalog yazma izni gerekmez. İki model ACTIVE ve aynı kategoride
olmalı. Kullanıcı profili paylaşım yolu değildir. CSRF, DEFERRED_AUTH korunur.

#### Path/Query Parameters
Yok.

#### Request Body
CompareHardwareIdsRequest: leftId,rightId zorunlu UUID; genel model kimlikleri.

#### Response — 200
HardwareComparisonResponse {left,right}; HardwareTechnicalComparisonItem;
model teknik değerleri ve null quantity. Marka seçimi gerektirmez. Eksik veri null;
otomatik benchmark/FPS/uyumluluk/performans önerisi yok. no-store, private.

#### Hata Durumları
400 INVALID_REQUEST (farklı kategori), 401 AUTHENTICATION_REQUIRED,
403 ACCESS_DENIED, 404 HARDWARE_NOT_FOUND (arşivli/olmayan model aynı),
413 REQUEST_TOO_LARGE, 500 INTERNAL_ERROR.

#### Bağlantı
HardwareComparisonService; V2/V3 genel katalog. Dış sağlayıcı/servis çağrısı yok.

#### Test Durumu
INTEGRATION — HardwareComparisonControllerTest: aktif/markasız model, arşiv,
olmayan kimlik, kategori, nested validation/mass assignment; üç uçta auth ve CSRF.
2026-10-21 modül verify 160 PASS; gerçek Auth E2E NOT_VERIFIED.

### HARDWARE — HW-17 Owner teknik profillerini toplu okuma

Durum: IMPLEMENTED — yerel sözleşme; tüketici E2E DEFERRED_CONTRACT
Sorumlu: Alper
Tüketici servisler: Performance, Recommendation; gerçek tüketici çağrıları NOT_VERIFIED

#### Endpoint
POST /api/v1/hardware/profiles/batch

#### Yetkilendirme
Kullanıcı adına doğrulanmış typed actor, owner-only. Kullanıcı bağlamı olmayan yeni
servis-token erişimi açılmaz. userId/rol/servis kimliği body/header'dan alınmaz.
Her profil aynı aktöre ait olmalıdır; bir tane yanlış/olmayan kaynak bütün batch'i
404 yapar. Paylaşılan PUBLIC profil de başka owner ise reddedilir. CSRF korunur;
DEFERRED_AUTH, üretimde gerçek Auth adapter'ı yokken fail-closed.

#### Path/Query Parameters
Yok.

#### Request Body
HardwareBatchRequest {profiles:[{profileId,page,size}]}. 1..20 tekil profil; her
profileId UUID, page 0..10000, size 1..100; üç alan da zorunlu. İstenen size
toplamı en fazla 200; sonuç az olsa bile daha büyük istek kabul edilmez. Aynı profil
için aynı istekte birden fazla sayfa 400. JSON bilinmeyen alanları reddeder; 16 KiB.

```json
{"profiles":[{"profileId":"00000000-0000-0000-0000-000000000001","page":0,"size":20}]}
```

#### Response — 200
HardwareBatchResponse profiles istek sırasındadır. Her profil profileId,
profileVersion,deviceType,components,page döndürür. Components mevcut teknik
HardwareComponentResponse sözleşmesidir: id,category,productId,brandId,modelName,
brandName,quantity,source,version,specifications. Seri numarası, owner UUID,
kişisel profil adı, gizlilik ayarları ve performans ölçümü yoktur.
Page {number,size,totalElements,totalPages,hasNext,nextPage}; son sayfada nextPage
null. Boş profil totalElements/totalPages=0; aralık dışı sayfa boş listeyle toplamı
korur. Her profil createdAt,id ASC sıralanır; devam ayrı istekle okunur.
no-store, private. Dört sınırlı DB sorgusu: tek owner sorgusu, toplu count, parametrik
parametrik array unnest + LATERAL ile ayrı sayfaların ID'leri, tek bileşen hydration sorgusu.
Boş bileşen sonucunda hydration sorgusu atlanır. REPEATABLE_READ snapshot;
N+1 sorgu, dış servis/DB erişimi veya sınırsız bileşen yüklemesi yapılmaz.

#### Hata Durumları
400 INVALID_REQUEST (limit/tekrar/nested validation/mass assignment),
401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED (CSRF dahil),
404 HARDWARE_NOT_FOUND (owner/eksik kaynak aynı), 413 REQUEST_TOO_LARGE,
500 INTERNAL_ERROR.

#### Bağlantı
HardwareBatchService + HardwareBatchQuery; mevcut V1/V2 component indeksi.
Performance/Recommendation gerçek consumer adapter ve E2E DEFERRED_CONTRACT /
NOT_VERIFIED; açık kullanıcı onayıyla ertelendi. Servisler değiştirilmedi.

#### Test Durumu
INTEGRATION — HardwareBatchControllerTest: owner izolasyonu, tüm isteği reddetme,
sayfa/sıra/devam, bilinmeyen/boş veri, 20/200 sınırları, nested validation, body limit,
CSRF/anonim/yanlış principal; 20 profil için sabit ORM sorgu sayısı ölçülür.
İlk modül verify 174 PASS; tüketici E2E ve gerçek Auth NOT_VERIFIED.

### HARDWARE — HW-12 Ortak yorum güvenlik sözleşmesi

Kimlikli yanıtlar no-store, private; tüm mutasyonlarda CSRF ve 16 KiB sınırı korunur.
Normal yazmalar actor başına 20/dakika, socket peer başına 120/dakika; hash bucket'ları
iki dakikayı aşınca dakikalık job ile temizlenir. X-Forwarded-For kimlik/IP kaynağı değildir.
Sayaçlar başarılı iş transaction'ına katılır; rollback olmuş denemeler kotayı tüketmez.
Bu, proxy zincirindeki gerçek son kullanıcı IP doğrulaması değildir (NOT_VERIFIED).
Gerçek Auth DEFERRED_AUTH; User/Social UGC kararı DEFERRED_CONTRACT / NOT_VERIFIED,
üretim adapter'ı deny. İzin veren policy yalnız testlerde; yeni public/anonim uç yok.

### HARDWARE — HW-12 Yorum oluşturma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/catalog/products/{productId}/reviews

#### Yetkilendirme
Typed actor; aktif genel ürün. Katalog yazma izni gerekmez; yazar UUID'si yalnız CurrentActor'dan gelir. Ortak HW-12 güvenlik sözleşmesi geçerlidir.

#### Path/Query Parameters
productId zorunlu UUID; query yok.

#### Request Body
CreateHardwareReviewRequest: body zorunlu düz metin 1..5000; rating opsiyonel tam sayı 1..5. Bilinmeyen alan reddedilir. HTML açı ayraçları/kontrol karakterleri ham ve NFKC sonrası reddedilir; satır sonu/tab desteklenir.

#### Response
201 HardwareReviewResponse; Location /api/v1/hardware/reviews/{id}. Alanlar id,productId,body,rating,status,version,ownedByActor,createdAt,updatedAt. author/user UUID, hash, IP ve rapor sayısı dönmez. İlk status VISIBLE/version 0.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED/CSRF; 404 HARDWARE_NOT_FOUND (ürün yok/arşivli); 409 DATA_CONFLICT (aynı ürün/yazar veya aynı yazarın normalize kopyası); 413 REQUEST_TOO_LARGE; 429 RATE_LIMITED (Retry-After 60); 500 INTERNAL_ERROR. ProblemDetail iç detay içermez.

#### Bağlantı
HardwareReviewService; V8__create_hardware_reviews_and_write_limits.sql.
Başka servise/DB'ye çağrı yok; gerçek Auth ve User/Social E2E NOT_VERIFIED.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewControllerTest + HardwareReviewTextTest;
2026-10-21 18:40:14 +03 verify exit 0: 206 PASS, 0 failure/error/skipped;
JaCoCo 1044/1060=%98,49; gerçek PostgreSQL 17/Flyway V1–V8/JPA/MockMvc.

### HARDWARE — HW-12 Görünür yorumları listeleme ve puan özeti

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/catalog/products/{productId}/reviews

#### Yetkilendirme
Typed actor; aktif ürün. VISIBLE yorumlar, owner veya güvenilir global UGC/iki yönlü engel kararıyla filtrelenir. Production policy başkalarını deny eder. Ortak HW-12 güvenlik sözleşmesi geçerlidir.

#### Path/Query Parameters
productId UUID; page 0..10000 varsayılan 0; size 1..100 varsayılan 20. createdAt,id ASC.

#### Request Body
Yok.

#### Response
200 HardwareReviewPageResponse: items,page,size,totalElements,totalPages,summary {ratedReviews,averageRating}. SQL audience/engel/status filtresi sayfa/toplam/AVG'den önce uygulanır. Yalnız dolu rating'ler ortalamaya girer; hiç puan yoksa averageRating null. Gizli/silinmiş/engelli yorum ve sayaçları sızmaz.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED; 404 HARDWARE_NOT_FOUND (ürün yok/arşivli); 500 INTERNAL_ERROR. ProblemDetail iç detay içermez.

#### Bağlantı
HardwareReviewService; V8__create_hardware_reviews_and_write_limits.sql.
Başka servise/DB'ye çağrı yok; gerçek Auth ve User/Social E2E NOT_VERIFIED.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewControllerTest + HardwareReviewTextTest;
2026-10-21 18:40:14 +03 verify exit 0: 206 PASS, 0 failure/error/skipped;
JaCoCo 1044/1060=%98,49; gerçek PostgreSQL 17/Flyway V1–V8/JPA/MockMvc.

### HARDWARE — HW-12 Kendi yorumlarını listeleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/reviews/mine

#### Yetkilendirme
Owner typed actor; yalnız CurrentActor'ın silinmemiş VISIBLE/HIDDEN yorumları. Arşivli ürün geçmişi korunur; dış audience kararı gerekmez. Ortak HW-12 güvenlik sözleşmesi geçerlidir.

#### Path/Query Parameters
page 0..10000 varsayılan 0; size 1..100 varsayılan 20; createdAt,id ASC.

#### Request Body
Yok.

#### Response
200 HardwareOwnReviewPageResponse: items,page,size,totalElements,totalPages. DELETED hariç; tüm items ownedByActor=true. Yazar kimliği veya IP dönmez.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED; 500 INTERNAL_ERROR. ProblemDetail iç detay içermez.

#### Bağlantı
HardwareReviewService; V8__create_hardware_reviews_and_write_limits.sql.
Başka servise/DB'ye çağrı yok; gerçek Auth ve User/Social E2E NOT_VERIFIED.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewControllerTest + HardwareReviewTextTest;
2026-10-21 18:40:14 +03 verify exit 0: 206 PASS, 0 failure/error/skipped;
JaCoCo 1044/1060=%98,49; gerçek PostgreSQL 17/Flyway V1–V8/JPA/MockMvc.

### HARDWARE — HW-12 Yorum detayı

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/hardware/reviews/{reviewId}

#### Yetkilendirme
Typed actor. Owner HIDDEN/arşivli geçmişini görebilir; non-owner yalnız VISIBLE + aktif ürün + güvenilir audience izinli ise. DELETED her durumda 404. Ortak HW-12 güvenlik sözleşmesi geçerlidir.

#### Path/Query Parameters
reviewId UUID; query yok.

#### Request Body
Yok.

#### Response
200 HardwareReviewResponse. Body yalnız izinli hedef için döner; gizli/engelli/olmayan kaynak aynı güvenli 404. Owner dışı author kimliği/profil verisi dönmez.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED; 404 HARDWARE_NOT_FOUND; 500 INTERNAL_ERROR. ProblemDetail iç detay içermez.

#### Bağlantı
HardwareReviewService; V8__create_hardware_reviews_and_write_limits.sql.
Başka servise/DB'ye çağrı yok; gerçek Auth ve User/Social E2E NOT_VERIFIED.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewControllerTest + HardwareReviewTextTest;
2026-10-21 18:40:14 +03 verify exit 0: 206 PASS, 0 failure/error/skipped;
JaCoCo 1044/1060=%98,49; gerçek PostgreSQL 17/Flyway V1–V8/JPA/MockMvc.

### HARDWARE — HW-12 Kendi yorumunu düzenleme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/hardware/reviews/{reviewId}

#### Yetkilendirme
Owner typed actor + CSRF; yalnız VISIBLE ve aktif ürüne bağlı yorum. HIDDEN sahibi bile metin güncellemesiyle moderasyonu aşamaz. Ortak HW-12 güvenlik sözleşmesi geçerlidir.

#### Path/Query Parameters
reviewId UUID; query yok.

#### Request Body
UpdateHardwareReviewRequest: reviewVersion zorunlu >=0, body zorunlu 1..5000 düz metin, rating opsiyonel 1..5. Eksik rating puanı kaldırır; body/hash değişimi atomik tekillik kontrolüne katılır.

#### Response
200 HardwareReviewResponse; artırılmış version. Pessimistic review kilidi + @Version. Gerekçesi sabit USER_REVIEW_UPDATED olan append-only audit aynı transaction'da.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED/CSRF; 404 HARDWARE_NOT_FOUND (yabancı/olmayan/silinmiş hedef veya arşivli ürün); 409 DATA_CONFLICT (eski sürüm/HIDDEN/kopya); 413 REQUEST_TOO_LARGE; 429 RATE_LIMITED; 500 INTERNAL_ERROR. ProblemDetail iç detay içermez.

#### Bağlantı
HardwareReviewService; V8__create_hardware_reviews_and_write_limits.sql.
Başka servise/DB'ye çağrı yok; gerçek Auth ve User/Social E2E NOT_VERIFIED.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewControllerTest + HardwareReviewTextTest;
2026-10-21 18:40:14 +03 verify exit 0: 206 PASS, 0 failure/error/skipped;
JaCoCo 1044/1060=%98,49; gerçek PostgreSQL 17/Flyway V1–V8/JPA/MockMvc.

### HARDWARE — HW-12 Kendi yorumunu silme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/hardware/reviews/{reviewId}

#### Yetkilendirme
Owner typed actor + CSRF; VISIBLE/HIDDEN ve arşivli ürün geçmişi silinebilir. Yabancı hedef 404. Ortak HW-12 güvenlik sözleşmesi geçerlidir.

#### Path/Query Parameters
reviewId UUID; reviewVersion zorunlu query >=0.

#### Request Body
Yok.

#### Response
204; soft-delete status DELETED/deletedAt, body/rating/hash temizlenir, version artar. Unique aktif yorum/kopya anahtarları serbest kalır; yeni yorum yazılabilir. Audit korunur; silinen yorum okunamaz/geri açılamaz.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED/CSRF; 404 HARDWARE_NOT_FOUND; 409 DATA_CONFLICT (eski sürüm); 429 RATE_LIMITED; 500 INTERNAL_ERROR. ProblemDetail iç detay içermez.

#### Bağlantı
HardwareReviewService; V8__create_hardware_reviews_and_write_limits.sql.
Başka servise/DB'ye çağrı yok; gerçek Auth ve User/Social E2E NOT_VERIFIED.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewControllerTest + HardwareReviewTextTest;
2026-10-21 18:40:14 +03 verify exit 0: 206 PASS, 0 failure/error/skipped;
JaCoCo 1044/1060=%98,49; gerçek PostgreSQL 17/Flyway V1–V8/JPA/MockMvc.

### HARDWARE — HW-13 Ortak moderasyon/teslim sözleşmesi

Tüm yanıtlar no-store, private; mutasyonlarda 16 KiB + CSRF korunur.
Normal raporlar actor 20/dakika; moderasyon actor 60/dakika; socket peer ortak
120/dakika sınırındadır. X-Forwarded-For güven kaynağı değildir. Body/reason/detail
HTML olarak işlenmez; log/audit/event'e hedef UGC metni veya ham IP kopyalanmaz.
V9 rapor, outbox ve audit action şemasını ekler; V8 değiştirilmedi.
Yerel ContentReported/HardwareReviewModerated v1 zarfı actual consumer kontratı değildir.
Producer/UUID/version/UTC metadata ve gerekli kimlik/enum payload'ı; 4 KiB/event,
PENDING+FAILED toplamı en fazla 10000. Transaction advisory kilidi kapasite yarışını
korur. Relay, broker ve uydurma servis URL/token modeli yok; olaylar PENDING kalır.
Admin/Notification/User-Social gerçek sözleşme ve E2E DEFERRED_CONTRACT / NOT_VERIFIED,
açık kullanıcı onaylı. Gerçek Auth DEFERRED_AUTH; production fail-closed.

### HARDWARE — HW-13 Yorumu raporlama

Durum: IMPLEMENTED — yerel kaynak sözleşmesi; gerçek entegrasyon DEFERRED_CONTRACT
Sorumlu: Alper
Tüketici servisler: Frontend, Admin (gerçek consumer NOT_VERIFIED)

#### Endpoint
POST /api/v1/hardware/reviews/{reviewId}/reports

#### Yetkilendirme
Typed actor + CSRF; yalnız okunabilen, VISIBLE/aktif ürüne bağlı yabancı yorum. Owner kendi yorumunu raporlayamaz; global UGC/engel kararı bilinmiyorsa yabancı hedef 404.

#### Path/Query Parameters
reviewId UUID; query yok.

#### Request Body
CreateHardwareReviewReportRequest: reason zorunlu SPAM/HARASSMENT/HATE_SPEECH/SEXUAL_CONTENT/PERSONAL_DATA/OTHER; details opsiyonel 1..500 düz metin. Bilinmeyen alan, boş/zararlı details reddedilir.

#### Response
201 HardwareReviewReportResponse: id,reviewId,reason,details,createdAt,outboxStatus=PENDING,deliveryStatus=NOT_DELIVERED. Location /api/v1/hardware/review-reports/{id}. Reporter/author UUID ve hedef metni dönmez. Doğal (reviewId,reporterId) tekilliği; tekrar 409, ikinci event/audit yok.

#### Hata Durumları
400 INVALID_REQUEST (self report dahil), 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED/CSRF, 404 HARDWARE_NOT_FOUND, 409 DATA_CONFLICT (tekrar), 413 REQUEST_TOO_LARGE, 429 RATE_LIMITED (Retry-After 60), 503 MODERATION_DELIVERY_UNAVAILABLE (outbox dolu), 500 INTERNAL_ERROR. ProblemDetail güvenli ve iç detaysızdır.

#### Bağlantı
HardwareReviewReportService / HardwareReviewModerationService;
V9__create_hardware_review_reports_and_outbox.sql. Gerçek servis/DB çağrısı yok;
Admin ve Notification teslimi doğrulanmadı. Yerel PENDING kayıt teslim kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewModerationControllerTest +
SecurityContextHardwareReviewModerationAccessTest. PostgreSQL 17/Flyway V1–V9/JPA,
239 PASS / exit 0, 2026-10-21 18:56:40 +03; JaCoCo 1176/1192=%98,66.
Permission/IDOR, rollback, doğal tekillik, global kapasite yarışı, orphan/purge,
stale/concurrent edit ve yalnız PENDING notice testli. Gerçek consumer E2E NOT_VERIFIED.

### HARDWARE — HW-13 Kendi raporunun yerel durumunu okuma

Durum: IMPLEMENTED — yerel kaynak sözleşmesi; gerçek entegrasyon DEFERRED_CONTRACT
Sorumlu: Alper
Tüketici servisler: Frontend, Admin (gerçek consumer NOT_VERIFIED)

#### Endpoint
GET /api/v1/hardware/review-reports/{reportId}

#### Yetkilendirme
Yalnız reporter typed actor; hedef yorumun yazarı veya katalog yöneticisi olması erişim vermez. Yabancı/olmayan rapor aynı 404.

#### Path/Query Parameters
reportId UUID; query yok.

#### Request Body
Yok.

#### Response
200 HardwareReviewReportResponse. Gerçek consumer E2E doğrulanmaz. PENDING ise NOT_DELIVERED; yerel SENT/FAILED kaydı dahi NOT_VERIFIED. Event purge edilmişse outboxStatus=NOT_AVAILABLE ve deliveryStatus=NOT_VERIFIED. Event FK ON DELETE SET NULL raporu korur.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED, 404 HARDWARE_NOT_FOUND, 500 INTERNAL_ERROR. ProblemDetail güvenli ve iç detaysızdır.

#### Bağlantı
HardwareReviewReportService / HardwareReviewModerationService;
V9__create_hardware_review_reports_and_outbox.sql. Gerçek servis/DB çağrısı yok;
Admin ve Notification teslimi doğrulanmadı. Yerel PENDING kayıt teslim kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewModerationControllerTest +
SecurityContextHardwareReviewModerationAccessTest. PostgreSQL 17/Flyway V1–V9/JPA,
239 PASS / exit 0, 2026-10-21 18:56:40 +03; JaCoCo 1176/1192=%98,66.
Permission/IDOR, rollback, doğal tekillik, global kapasite yarışı, orphan/purge,
stale/concurrent edit ve yalnız PENDING notice testli. Gerçek consumer E2E NOT_VERIFIED.

### HARDWARE — HW-13 Yetkili kaynak moderasyon detayı

Durum: IMPLEMENTED — yerel kaynak sözleşmesi; gerçek entegrasyon DEFERRED_CONTRACT
Sorumlu: Alper
Tüketici servisler: Frontend, Admin (gerçek consumer NOT_VERIFIED)

#### Endpoint
GET /api/v1/hardware/moderation/reviews/{reviewId}

#### Yetkilendirme
Typed actor + hardware:reviews:moderate uygulama izni hem HTTP hem service sınırında zorunlu. Katalog izni/ROLE_ADMIN/X-Role tek başına kabul edilmez. Gerçek Auth rol/claim eşlemesi DEFERRED_AUTH.

#### Path/Query Parameters
reviewId UUID; page 0..10000 varsayılan 0; size 1..100 varsayılan 20; raporlar createdAt,id ASC.

#### Request Body
Yok.

#### Response
200 HardwareReviewModerationDetailResponse: review,reports,page,size,totalReports,totalPages. Yetkili kullanıcı HIDDEN hedefi ve rapor details'ını görür; DELETED metni null kalır. Reporter/author UUID ve ham IP dönmez. Event status'ları tek bounded sorguyla yüklenir; 25 rapor testinde üç ORM statement + tek JDBC status sorgusu, N+1 yok.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED, 404 HARDWARE_NOT_FOUND, 500 INTERNAL_ERROR. ProblemDetail güvenli ve iç detaysızdır.

#### Bağlantı
HardwareReviewReportService / HardwareReviewModerationService;
V9__create_hardware_review_reports_and_outbox.sql. Gerçek servis/DB çağrısı yok;
Admin ve Notification teslimi doğrulanmadı. Yerel PENDING kayıt teslim kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewModerationControllerTest +
SecurityContextHardwareReviewModerationAccessTest. PostgreSQL 17/Flyway V1–V9/JPA,
239 PASS / exit 0, 2026-10-21 18:56:40 +03; JaCoCo 1176/1192=%98,66.
Permission/IDOR, rollback, doğal tekillik, global kapasite yarışı, orphan/purge,
stale/concurrent edit ve yalnız PENDING notice testli. Gerçek consumer E2E NOT_VERIFIED.

### HARDWARE — HW-13 Kaynak yorumuna HIDE/RESTORE uygulama

Durum: IMPLEMENTED — yerel kaynak sözleşmesi; gerçek entegrasyon DEFERRED_CONTRACT
Sorumlu: Alper
Tüketici servisler: Frontend, Admin (gerçek consumer NOT_VERIFIED)

#### Endpoint
POST /api/v1/hardware/moderation/reviews/{reviewId}/actions

#### Yetkilendirme
Typed actor + hardware:reviews:moderate; service recheck + CSRF. Bu kaynak servis komutudur; merkezi Admin case/rol/hesap yönetimi burada kurulmaz.

#### Path/Query Parameters
reviewId UUID; query yok.

#### Request Body
ModerateHardwareReviewRequest: reviewVersion >=0, action HIDE/RESTORE, reason 1..500; hepsi zorunlu. Geçersiz action/rol/userId alanı reddedilir. HIDE VISIBLE->HIDDEN; RESTORE HIDDEN->VISIBLE yalnız aktif üründe. DELETED geri getirilemez.

#### Response
200 HardwareReviewModerationResponse: review (artan version),noticeEventId,notificationDeliveryStatus=NOT_DELIVERED. Review kilidi/@Version, gerekçeli immutable audit ve PENDING notice aynı transaction'da. HIDDEN yorum normal liste/puan özetinden çıkar; RESTORE geri dahil eder. Outbox doluysa flush edilmiş hedef/audit/quota bile rollback olur.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED/CSRF, 404 HARDWARE_NOT_FOUND (silinmiş/olmayan hedef, RESTORE için arşivli ürün), 409 DATA_CONFLICT (eski sürüm/yanlış transition), 413 REQUEST_TOO_LARGE, 429 RATE_LIMITED, 503 MODERATION_DELIVERY_UNAVAILABLE, 500 INTERNAL_ERROR. ProblemDetail güvenli ve iç detaysızdır.

#### Bağlantı
HardwareReviewReportService / HardwareReviewModerationService;
V9__create_hardware_review_reports_and_outbox.sql. Gerçek servis/DB çağrısı yok;
Admin ve Notification teslimi doğrulanmadı. Yerel PENDING kayıt teslim kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — HardwareReviewModerationControllerTest +
SecurityContextHardwareReviewModerationAccessTest. PostgreSQL 17/Flyway V1–V9/JPA,
239 PASS / exit 0, 2026-10-21 18:56:40 +03; JaCoCo 1176/1192=%98,66.
Permission/IDOR, rollback, doğal tekillik, global kapasite yarışı, orphan/purge,
stale/concurrent edit ve yalnız PENDING notice testli. Gerçek consumer E2E NOT_VERIFIED.

### HARDWARE — HW-06 Açık onaylı donanım tarama önizlemesi

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend; gelecekte masaüstü istemci (gerçek E2E NOT_VERIFIED)

#### Endpoint
POST /api/v1/hardware/profiles/{profileId}/sync/preview

#### Yetkilendirme
Owner typed actor + CSRF; processingConsent=true bu tek isteğin açık onayıdır.
Sürekli sync izni vermez. Donanım/consent/source/profileVersion değiştirmez;
yalnız kısa ömürlü rate bucket'ları kaydedilir. Gerçek Auth DEFERRED_AUTH;
üretim doğrulayıcı yokken istekler reddedilir. Bu uç cihaz doğrulamaz.

#### Path/Query Parameters
profileId zorunlu UUID; query yok.

#### Request Body
PreviewHardwareScanRequest: processingConsent=true ve scan zorunlu.
HardwareScanRequest: schemaVersion=1, capturedAt UTC (son 24 saat, en fazla 5 dakika
gelecek), components 1..32. ScannedHardwareComponentRequest: tekil opaque UUID
componentKey, mevcut kategori enum'u, modelName 1..160, opsiyonel brandName 1..100,
quantity 1..128 ve mevcut SpecificationsRequest alanları/range'leri.
Metinler ham/NFKC sonrası doğrulanır; body 16 KiB. source/userId/deviceId,
seri/MAC/hostname/telemetri, katalog UUID ve bilinmeyen alanlar kabul edilmez.

#### Response
200 HardwareScanPreviewResponse: profileVersion,schemaVersion,capturedAt,
componentCount,persisted=false,components. Her component key/category/normalize
modelName/brandName/quantity/specifications içerir; kaynak veya owner UUID dönmez.
capturedAt microsecond UTC'ye normalize edilir; components key'e göre stabil sıralıdır.
Yanıt no-store/private; preview gerçek donanım attestation'ı veya sync teslimi değildir.

#### Hata Durumları
400 INVALID_REQUEST; 401 AUTHENTICATION_REQUIRED; 403 ACCESS_DENIED/CSRF;
404 HARDWARE_NOT_FOUND (yabancı/olmayan profil); 413 REQUEST_TOO_LARGE;
429 RATE_LIMITED (Retry-After 60); 500 INTERNAL_ERROR. Güvenli ProblemDetail.
Fixed-minute user 20/dakika ve socket peer 120/dakika; XFF'a güvenilmez,
ham IP saklanmaz. Sayaçlar başarılı transaction'a katılır; rollback kota tüketmez.

#### Bağlantı
HardwareScanController/Service/Mapper, HardwareScan domain ve HardwareSyncLimits.
Yeni migration yok; mevcut V8 rate bucket altyapısı. Başka servise/DB'ye çağrı yok.
HW-07 kalıcı izin ve ayrı doğrulanmış cihaz apply sözleşmesi ayrıca uygulanacak.

#### Test Durumu
UNIT + INTEGRATION — HardwareScanControllerTest (28) ve HardwareScanTest (2);
2026-10-21 20:21:49 +03 verify exit 0: 269 PASS, failure/error/skipped 0;
JaCoCo 1243/1261=%98,57. Gerçek PostgreSQL 17/Flyway V1–V9/JPA/MockMvc.
Gerçek Auth/desktop E2E NOT_VERIFIED. Son kalite sonucu HW-05 kalite kaydında tutulur.

### HARDWARE — HW-07 Ortak sync güvenlik ve tekrar gönderim sözleşmesi

Gerçek kullanıcı/cihaz Auth DEFERRED_AUTH; PERF cihaz sahipliği/iptal sözleşmesi ve
masaüstü E2E DEFERRED_CONTRACT / NOT_VERIFIED, açık kullanıcı onaylıdır. Gerçek
RestClient URL/JSON, eşleştirme servisi veya credential üretilmez. Production deny.
Parent profil kilidi tüm owner/consent/apply yarışlarını ve profileVersion'ı korur.
Yeni sıra kesin artar; eski capturedAt reddedilir. Aynı key/normalize hash 24 saat
önceki yanıtı döndürür; güncel cihaz ve izin her retry'da yeniden doğrulanır.
Profil başına 500 canlı receipt sınırı, saatlik expiry temizliği (yaklaşık 24–25 saat);
expiry sonrası lastSequence anti-replay koruması sürer. Snapshot/state/version/receipt
tek transaction, kapasite/validation hatasında rollback. Grant/revoke/preview user
ortak 20/dakika; apply user 30 + device 10/dakika; tümü socket peer 120/dakika.
Hash bucket'ları mevcut TTL job ile temizlenir; XFF'a güvenilmez, ham IP saklanmaz.
Manuel PUT, synced bileşeni MANUAL'a çevirip sync key'i ayırır; sonraki snapshot
manuel kaydı ezmez ve aynı tarama key'i için ayrı otomatik kayıt oluşturabilir.
İzin aktifken manuel silinen otomatik bileşen sonraki snapshot'ta geri gelebilir.

### HARDWARE — HW-07 Owner sync iznini okuma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend; gelecekte masaüstü (gerçek E2E NOT_VERIFIED)

#### Endpoint
GET /api/v1/hardware/profiles/{profileId}/sync-consent

#### Yetkilendirme
Owner typed actor; yalnız kendi profili. Cihaz principal'ı kontrol API'sine giremez.

#### Path/Query Parameters
profileId UUID; query/body yok.

#### Request Body
Yok.

#### Response
200 HardwareSyncConsentResponse: profileVersion,status,deviceId,generation,grantedAt,revokedAt,lastSequence,lastCapturedAt,lastSyncedAt. Kayıt yoksa NOT_GRANTED ve null metadata. Yalnız owner; no-store/private.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED, 404 HARDWARE_NOT_FOUND. Güvenli ProblemDetail, iç detay yok; tüm yanıtlar no-store/private.

#### Bağlantı
HardwareSyncController, HardwareSyncConsentService/HardwareSyncService;
V10__add_hardware_desktop_sync.sql. Başka servis/DB çağrısı yok; gerçek Auth/PERF
cihaz/desktop bağlantısı NOT_VERIFIED. Yerel sözleşme gerçek cihaz kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — HardwareSyncControllerTest (21), HardwareSyncDomainTest (2),
SecurityContextHardwareSyncDeviceAccessTest (1); 2026-10-21 20:36:36 +03 verify
exit 0: 293 PASS (54 yeni HW-05), failure/error/skipped 0; JaCoCo
1420/1438=%98,75. Gerçek PostgreSQL 17/Flyway V1–V10/JPA/MockMvc.
Gerçek Auth/PERF cihaz/desktop E2E NOT_VERIFIED; final kapılar ayrıca kaydedilir.

### HARDWARE — HW-07 Owner aktif cihaza sync izni verme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend; gelecekte masaüstü (gerçek E2E NOT_VERIFIED)

#### Endpoint
PUT /api/v1/hardware/profiles/{profileId}/sync-consent

#### Yetkilendirme
Owner + CSRF; dış policy cihazın aktif ve owner'a bağlı olduğunu doğrulamalıdır. Client deviceId yalnız seçimdir. Production policy deny.

#### Path/Query Parameters
profileId UUID; query yok.

#### Request Body
GrantHardwareSyncRequest: profileVersion >=0, deviceId UUID, accepted=true; hepsi zorunlu. Aktif izin varken ikinci grant 409; cihaz değişimi için önce revoke.

#### Response
200 HardwareSyncConsentResponse, yeni generation UUID ve artan profileVersion. Yeni generation lastSequence=0 ve son sync metadata'sını sıfırlar. Profil source/gizlilik/donanımı değişmez.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED/CSRF, 404 HARDWARE_NOT_FOUND (profil/cihaz), 409 DATA_CONFLICT, 413 REQUEST_TOO_LARGE, 429 RATE_LIMITED. Güvenli ProblemDetail, iç detay yok; tüm yanıtlar no-store/private.

#### Bağlantı
HardwareSyncController, HardwareSyncConsentService/HardwareSyncService;
V10__add_hardware_desktop_sync.sql. Başka servis/DB çağrısı yok; gerçek Auth/PERF
cihaz/desktop bağlantısı NOT_VERIFIED. Yerel sözleşme gerçek cihaz kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — HardwareSyncControllerTest (21), HardwareSyncDomainTest (2),
SecurityContextHardwareSyncDeviceAccessTest (1); 2026-10-21 20:36:36 +03 verify
exit 0: 293 PASS (54 yeni HW-05), failure/error/skipped 0; JaCoCo
1420/1438=%98,75. Gerçek PostgreSQL 17/Flyway V1–V10/JPA/MockMvc.
Gerçek Auth/PERF cihaz/desktop E2E NOT_VERIFIED; final kapılar ayrıca kaydedilir.

### HARDWARE — HW-07 Owner sync iznini iptal etme

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend; gelecekte masaüstü (gerçek E2E NOT_VERIFIED)

#### Endpoint
DELETE /api/v1/hardware/profiles/{profileId}/sync-consent

#### Yetkilendirme
Owner + CSRF; iptal için dış cihazın online/aktif olması gerekmez.

#### Path/Query Parameters
profileId UUID; profileVersion zorunlu query >=0.

#### Request Body
Yok.

#### Response
200 HardwareSyncConsentResponse: REVOKED, revokedAt, artan profileVersion. Donanım korunur; yeni apply ve eski receipt retry'ı reddedilir. Zaten REVOKED veya kayıt yoksa güncel version ile değişikliksiz yanıt; mevcut version yine doğrulanır.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED/CSRF, 404 HARDWARE_NOT_FOUND, 409 DATA_CONFLICT, 429 RATE_LIMITED. Güvenli ProblemDetail, iç detay yok; tüm yanıtlar no-store/private.

#### Bağlantı
HardwareSyncController, HardwareSyncConsentService/HardwareSyncService;
V10__add_hardware_desktop_sync.sql. Başka servis/DB çağrısı yok; gerçek Auth/PERF
cihaz/desktop bağlantısı NOT_VERIFIED. Yerel sözleşme gerçek cihaz kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — HardwareSyncControllerTest (21), HardwareSyncDomainTest (2),
SecurityContextHardwareSyncDeviceAccessTest (1); 2026-10-21 20:36:36 +03 verify
exit 0: 293 PASS (54 yeni HW-05), failure/error/skipped 0; JaCoCo
1420/1438=%98,75. Gerçek PostgreSQL 17/Flyway V1–V10/JPA/MockMvc.
Gerçek Auth/PERF cihaz/desktop E2E NOT_VERIFIED; final kapılar ayrıca kaydedilir.

### HARDWARE — HW-07 İzinli cihaz snapshot kabulü

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend; gelecekte masaüstü (gerçek E2E NOT_VERIFIED)

#### Endpoint
POST /api/v1/hardware/profiles/{profileId}/sync/snapshots

#### Yetkilendirme
Yalnız AuthenticatedHardwareDevice + hardware:sync:write, aktif cihaz sahipliği policy'si ve aynı owner/device/generation için GRANTED yerel izin. Browser actor bu authority'ye sahip olsa da reddedilir. CSRF korunur; üretimde cihaz doğrulaması yok ve policy deny.

#### Path/Query Parameters
profileId UUID; zorunlu Idempotency-Key UUID header; query yok.

#### Request Body
ApplyHardwareSyncRequest: profileVersion >=0, consentGeneration UUID, sequence >=1 ve HW-06 ile aynı scan v1. Toplam gövde 16 KiB, snapshot 1..32 tekil componentKey. Caller userId/deviceId/source veya katalog UUID atayamaz.

#### Response
200 HardwareSyncReceiptResponse: idempotencyKey,profileVersion,sequence,capturedAt,appliedAt,receivedComponents,created,updated,removed. Version ilk uygulamanın sürümüdür; retry aynı asgari yanıtı döndürür, güncel profil görünümü değildir. Owner kimliği, cihaz kimliği veya model metni receipt'e kopyalanmaz. Yalnız DESKTOP_SYNC kümesi güncellenir; MANUAL korunur. Toplam profil kaydı apply sonrası <=200.

#### Hata Durumları
400 INVALID_REQUEST, 401 AUTHENTICATION_REQUIRED, 403 ACCESS_DENIED/CSRF, 404 HARDWARE_NOT_FOUND (owner/cihaz/policy), 409 DATA_CONFLICT (izin yok/iptal/eski generation/version/sequence/capture veya key-hash uyuşmazlığı), 413 REQUEST_TOO_LARGE, 429 RATE_LIMITED veya SYNC_RECEIPT_LIMIT_REACHED (Retry-After 3600). Güvenli ProblemDetail, iç detay yok; tüm yanıtlar no-store/private.

#### Bağlantı
HardwareSyncController, HardwareSyncConsentService/HardwareSyncService;
V10__add_hardware_desktop_sync.sql. Başka servis/DB çağrısı yok; gerçek Auth/PERF
cihaz/desktop bağlantısı NOT_VERIFIED. Yerel sözleşme gerçek cihaz kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — HardwareSyncControllerTest (21), HardwareSyncDomainTest (2),
SecurityContextHardwareSyncDeviceAccessTest (1); 2026-10-21 20:36:36 +03 verify
exit 0: 293 PASS (54 yeni HW-05), failure/error/skipped 0; JaCoCo
1420/1438=%98,75. Gerçek PostgreSQL 17/Flyway V1–V10/JPA/MockMvc.
Gerçek Auth/PERF cihaz/desktop E2E NOT_VERIFIED; final kapılar ayrıca kaydedilir.

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
