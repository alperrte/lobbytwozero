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

### GAME — Oyun alt verileri (GAME-08)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend; ileride Recommendation

#### Endpoint
GET /api/v1/game/{gameId}/metadata

#### Yetkilendirme
Public. Yalnız okuma; yazma veya indeksleme HTTP üzerinden açılmaz. Tanımsız yollar reddedilir.

#### Path Parameters
- gameId: UUID, zorunlu

#### Response — 200
- `gameId`: kanonik UUID.
- `genres`, `categories`, `tags`, `platforms`: birbirinden ayrı diziler; elemanlar `id`, `code`, `nameTr`, `nameEn` içerir.
- `developers`, `publishers`: ayrı ad dizileri.
- `media`: `type` (COVER/SCREENSHOT/TRAILER), `url` (HTTPS), nullable `provider`, `sortOrder`.
- `systemRequirements`: `level`, `platform`, nullable `os`, `cpu`, `gpu`, `ramMb`, `storageMb`, `rawText`.
- Sağlayıcının sunmadığı koleksiyonlar boş dizi, bilinmeyen skaler alanlar null döner. Ham metin HTML olarak işlenmez.
- `languages`: genel dil desteği için saklanan normalize kodlar; boş liste destek olmadığı anlamına gelmez. `earlyAccess`: true/false/null; null bilinmiyor. Arayüz/ses/altyazı ayrımı GAME-23 kapsamındadır.

#### Hata Durumları
- 400: Geçersiz UUID (ProblemDetail).
- 404: Oyun bulunamadı (ProblemDetail).
- 500: Beklenmeyen sunucu hatası.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path; gateway prefix değiştirmez.
Diğer mikroservisler: Kendi RestClient adaptörüyle doğrudan Game Service; başka DB erişimi yok.

#### Test Durumu
INTEGRATION — PostgreSQL 17/Flyway/JPA ilişkileri ve MockMvc HTTP/güvenlik testleri.

### GAME — Ortak oyun kartı (GAME-10)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend; ileride Library ve Recommendation

#### Endpoint
GET /api/v1/game/{gameId}/card

#### Yetkilendirme
Public. Yalnız ACTIVE oyunlar görünür.

#### Path Parameters
- gameId: UUID, zorunlu.

#### Response — 200
- `gameId`: tek kanonik UUID; `title`: oyun adı.
- `origin`: INTERNAL/EXTERNAL; yerelde saklama kökeni değiştirmez.
- `metadataCompleteness`: INDEX_ONLY/PARTIAL/FULL; köken veya sağlayıcı değildir.
- `gameType`: MAIN_GAME/DLC/DEMO/SOUNDTRACK/SOFTWARE/VIDEO/PACKAGE/HARDWARE/UNKNOWN. Kesin tür sinyali yoksa UNKNOWN; isimden tür tahmini yapılmaz.
- `externalReferences`: `provider`, `externalId`, nullable `lastSyncedAt` dizisi.
- `coverUrl`: mevcut kapak HTTPS URL'si; bilinmiyorsa null.

#### Hata Durumları
- 400: Geçersiz UUID.
- 404: Oyun yok veya ACTIVE değil.
- 500: Beklenmeyen sunucu hatası.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path.
Diğer mikroservisler: RestClient ile Game Service; yerel okuma dış sağlayıcı beklemez.

#### Test Durumu
INTEGRATION — iki köken için ortak HTTP sözleşmesi, eksik veri, kapak seçimi ve görünürlük.

### GAME — Ortak oyun detayı ve güncellik (GAME-10, GAME-17, GAME-22)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/game/games/{gameId}

#### Yetkilendirme
Public. Yalnız ACTIVE oyunlar görünür.

#### Path Parameters
- gameId: UUID, zorunlu.

#### Response — 200
- `game`: yukarıdaki ortak kart sözleşmesi.
- `metadata`: GAME-08 alt veri sözleşmesi; aynı kanonik gameId.
- `description`: nullable, en fazla 16000 karakter; düz metin olarak işlenir. `releaseDate`: nullable ISO tarih.
- `freshness`: `status` (LOCAL/UNSUPPORTED/MISSING/FRESH/STALE), nullable `fetchedAt`, `expiresAt`, `nextAttemptAt`, `lastErrorCode`.
- `metadata.languages` ve `metadata.earlyAccess`: GAME-02 kapsamında yerelde bilinen dil ve erken erişim bilgisi; kaynak sağlamazsa boş/null.
- Güncellik son başarılı detay yanıtına aittir; alan bazlı güncellik garantisi değildir. Fiyat verisi sunulmaz.
- GET yalnız yerel okuma yapar; sağlayıcıya istek, yenileme tetikleme veya DB yazımı yapmaz. STALE veri korunur.
- Steam adaptörünün DETAILS yeteneği yoktur: EXTERNAL kayıtlar UNSUPPORTED döner. Gerçek detay entegrasyonu veri kaynağı kararı ve kullanıcı onayı bekliyor; altyapı varlığı gerçek sağlayıcı desteği anlamına gelmez.

#### Hata Durumları
- 400: Geçersiz UUID.
- 404: Oyun yok veya ACTIVE değil.
- 500: Beklenmeyen sunucu hatası.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path.
Diğer mikroservisler: RestClient ile Game Service.

#### Test Durumu
INTEGRATION — PostgreSQL/Flyway üzerinde ortak model, 200/400/404, kökenin korunması, salt okunur GET ve STALE yanıtı. Gerçek detay sağlayıcısı: NOT IMPLEMENTED.

### GAME — Yerel Steam indeks araması (GAME-16)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/game/catalog

#### Yetkilendirme
Public. Yalnız ACTIVE ve STEAM referanslı yerel kayıtlar; varsayılan sonuçlar yalnız MAIN_GAME.

#### Query Parameters
- q: zorunlu, 1–128 karakter; normalize edilmiş başlıkta önek araması. Boş/whitespace reddedilir; `%`/`_` joker değildir.
- page: opsiyonel, varsayılan 0, en az 0.
- size: opsiyonel, varsayılan 20, 1–100.
- includeUnclassified: opsiyonel boolean, varsayılan false; true ise UNKNOWN kayıtlar da türleri belirtilerek eklenir. DLC/demo/diğer bilinen oyun dışı türler bu seçenekle eklenmez.

#### Response — 200
- `items`: ortak GAME-10 kartları; kanonik oyun başına tek sonuç.
- `page`, `size`, `totalElements`: yerel sonuç bilgisi. Sıra normalizedTitle ve gameId artandır.
- `lastSuccessfulAt`: nullable son başarılı indeks turunun başlangıç zamanı.
- `providerTotal`: null; sağlayıcı evreninin toplamı verilmediğinden kapsam yüzdesi uydurulmaz.
- Bu hafif indeks aramasıdır; genel keşif/sıralama ve gelişmiş filtreler Faz 3 kapsamındadır. İstek sağlayıcıya çağrı yapmaz.

#### Hata Durumları
- 400: Eksik/geçersiz q, page veya size.
- 500: Beklenmeyen sunucu hatası.

#### Bağlantı
Frontend: Gateway üzerinden aynı path.
Diğer mikroservisler: Kendi RestClient adaptörüyle Game Service.

#### Test Durumu
UNIT + INTEGRATION — PostgreSQL üzerinde sayfalama, stabil sıra, wildcard kaçışı, sağlayıcı kesintisinde yerel okuma, HTTP validation ve GAME-21 tür temizliği.

### GAME — İndeks kapsamı ve ilerleme (GAME-16, GAME-19, GAME-22)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/game/catalog/status

#### Yetkilendirme
Public. Yalnız katalog kapsamı ve güvenli operasyon kodları; anahtar/ham sağlayıcı hatası içermez.

#### Response — 200
- `provider`: STEAM; `runMode`: null/INITIAL/INCREMENTAL.
- `syncStatus`: IDLE/RUNNING/COMPLETE/FAILED.
- `cursor`: son uygulanan AppID; `processedCount`: aktif/son turda işlenen kayıt sayısı.
- `indexedEntries`: saklanan STEAM referans sayısı; `mainGameEntries` ve `unclassifiedEntries`: türlerine göre referans sayıları (aktif/arşivli tüm saklanan referanslar). `providerTotal`: null.
- `runStartedAt`, `lastSuccessfulAt`: nullable UTC zamanlar; `lastErrorCode`: nullable güvenli kod.
- `failureCount`: art arda başarısız sayfa sayısı; `nextAttemptAt`: nullable otomatik tekrar zamanı. FAILED ve null nextAttemptAt, otomatik denemenin durduğunu belirtir.
- Aktarım her çağrıda tek sayfa işler. `game.catalog.sync.enabled=true` ile isteğe bağlı zamanlayıcı etkinleşir; varsayılan kapalıdır. İlk aktarım, altı saat aralıklı artımlı turlar ve kalıcı checkpoint üzerinden devam desteklenir.
- Geçici hata en fazla beş otomatik tekrar alır; kalıcı hata veya bütçe aşımında uygulama servisinden açık resume gerekir. HTTP mutasyon endpoint'i yoktur.

#### Hata Durumları
- 500: Beklenmeyen sunucu hatası.

#### Bağlantı
Frontend: Gateway üzerinden aynı path.
Diğer mikroservisler: RestClient ile Game Service.

#### Test Durumu
INTEGRATION — kalıcı checkpoint, idempotent tekrar, ayrı incremental watermark, eşzamanlı sayfalar, kısmi hata, retry bütçesi, backoff ve açık resume. Scheduler opt-in koşulu test edildi; canlı tam katalog aktarımı yapılmadı.

GAME-21 notu: Canlı GetAppList yanıtı kayıt başına kesin tür alanı sunmadığından Steam indeks girdileri UNKNOWN kalır. `include_games` filtresi tek başına demo/soundtrack ayrımı kanıtı kabul edilmez. Bu kayıtlar önek aramasında `includeUnclassified=true` ile bulunabilir; doğrulanmış MAIN_GAME sinyali olmadan varsayılan ana oyun sonuçlarına girmez. Gerçek detay zenginleştirme, Faz 2 sağlayıcı onayı beklemektedir.

### GAME — Birleşik yerel katalog (GAME-01, GAME-04, GAME-05)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend; ileride Library, Recommendation

#### Endpoint
GET /api/v1/game/games

#### Yetkilendirme
Public. Kullanıcı onayıyla yalnız GET. Yalnız ACTIVE + MAIN_GAME kayıtları; INTERNAL ve EXTERNAL aynı kuralla listelenir. UNKNOWN, DLC/demo ve diğer türler genel keşfe dahil değildir. Diğer HTTP metotları deny-by-default; editör mutasyon endpoint'i yoktur.

#### Query Parameters
- page: varsayılan 0, 0–10000. size: varsayılan 20, 1–100.
- q: opsiyonel; verilirse 1–128 karakter, boş olamaz. NFKC + whitespace normalizasyonu ve Locale.ROOT küçük harf; normalize başlıkta önek araması. `%`, `_`, `!` literal olarak kaçırılır; NUL reddedilir.
- genre, platform: opsiyonel, 1–64 karakterlik yerel sözlük kodu. Tür filtresi yalnız GENRE terimidir; kategori/etiket yerine geçmez. Kodlar büyük/küçük harfe duyarlıdır.
- language: opsiyonel; normalize küçük harf dil kodu, ör. tr/en/en-us. Kesin saklanan kodla eşleşir; dil ailesinden destek tahmini yapılmaz.
- earlyAccess: opsiyonel boolean. Bilinmeyen null kayıt, true veya false filtresine eşleşmez.
- releasedAfter, releasedBefore: opsiyonel ISO tarih, yıl 0001–9999; iki sınır da dahil. Ters aralık reddedilir.
- popularOnly: varsayılan false; true ise yalnız editör listesine alınmış uygun kayıtlar.
- sort: TITLE (varsayılan), RELEASE_DATE veya POPULARITY. direction: ASC (varsayılan) veya DESC. Alan/yön allowlist ile sınırlıdır.
- minRating veya sort=RATING: **desteklenmiyor**; Review Service entegrasyonu bekleniyor, veri uydurulmaz.

Filtreler AND ile birleşir. İlgili metadata bilinmiyorsa o filtreye eşleşmez; filtresiz listede kayıt kalır. Fiyat/indirim filtresi uygulanmadı. Her sıralamada gameId ASC bağlayıcıdır. Null tarih ve editör sırası her iki yönde sonda. POPULARITY DESC küçük editör sırasını önce getirir; ölçülmüş kullanıcı etkileşimi puanı değildir.

#### Response — 200
- items: her elemanda `game` (ortak GameCardResponse), nullable `releaseDate`, nullable `earlyAccess`, `languages`, nullable `editorialRank`, nullable `popularityUpdatedAt`.
- page, size, totalElements; sort, direction; popularityBasis=EDITORIAL.
- Kayıt yoksa boş items ve gerçek totalElements. Aynı veri kümesinde kararlı sayfalama; farklı istekler arasında katalog değişirse snapshot devamlılığı garanti edilmez.
- Count, sayfa ve toplu kart ilişkileri tek REPEATABLE_READ transaction görünümündedir. Dolu sayfada dört toplu SQL okuması; HTTP/sağlayıcı çağrısı veya oyun başına servis çağrısı yoktur.

#### Hata Durumları
- 400: Geçersiz parametre/enum/aralık veya desteklenmeyen puan filtresi/sıralaması. Puan için ProblemDetail code=RATING_INTEGRATION_PENDING.
- 500: Beklenmeyen sunucu hatası; SQL veya iç hata ayrıntısı dönmez.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. Mikroservisler: sözleşmeli RestClient ile Game Service; başka DB erişimi yok.

#### Test Durumu
INTEGRATION — birleşik kökenler, görünürlük/tür temizliği, toplam/sayfa, boş sayfa, filtre bileşimi ve eksik veri, allowlist, null sırası, 400/403 ve 100.000 kayıtta eşzamanlı ölçüm. LTZ puanı entegrasyonu: NOT IMPLEMENTED; GAME-04/05 bu nedenle kısmi.

### GAME — Birleşik yerel oyun araması (GAME-03)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/game/games/search

#### Yetkilendirme
Public GET. Yalnız ACTIVE + MAIN_GAME, iki köken birlikte. Diğer HTTP metotları kapalıdır.

#### Query Parameters
q zorunludur; diğer filtreler, sayfa ve sıralama parametreleri birleşik katalog kaydıyla aynıdır. Eksik/boş q reddedilir. Arama yalnız yerel önek aramasıdır; canlı harici arama eklenmedi. Eski `/api/v1/game/catalog` Steam indeks araması mevcut sözleşmesiyle korunur.

#### Response — 200
GameDiscoveryResponse; birleşik katalog kaydıyla aynı alanlar. Eşleşme yoksa 200 + boş items.

#### Hata Durumları
- 400: Eksik/boş/geçersiz q veya diğer parametreler; puan bağımlılığı için RATING_INTEGRATION_PENDING.
- 500: Genel sunucu hatası.

#### Bağlantı
Frontend: Gateway üzerinden aynı path. Diğer servisler: RestClient; dış sağlayıcı erişimi gerekmez.

#### Test Durumu
INTEGRATION — Unicode normalizasyonu, literal jokerler, boş arama, tür temizliği, kararlı sayfalar ve sağlayıcı kesintisinden bağımsız eşzamanlı sorgular.

### GAME — Editör seçimine dayalı popüler oyunlar (GAME-06, GAME-20)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/game/games/popular

#### Yetkilendirme
Public GET. Yalnız ACTIVE + MAIN_GAME ve editör listesinde bulunan oyunlar. Yazma endpoint'i yoktur.

#### Query Parameters
page: varsayılan 0, 0–10000. size: varsayılan 20, 1–100. Sabit sıra POPULARITY DESC + gameId ASC; diğer filtreler için genel liste + popularOnly kullanılır.

#### Response — 200
GameDiscoveryResponse. popularityBasis=EDITORIAL, sort=POPULARITY, direction=DESC. editorialRank seçim sırası, popularityUpdatedAt bu seçimin UTC zamanıdır. Steam trendi veya LTZ etkileşim skoru iddiası yoktur. Editör listesi boşsa sonuç boş gelir; oyunlara tek tek dış detay çağrısı yapılmaz.

#### Hata Durumları
- 400: Geçersiz page/size.
- 500: Genel sunucu hatası.

#### Bağlantı
Frontend: Gateway üzerinden aynı path. Liste iç uygulama komutuyla hazırlanır; yetkili yönetim HTTP akışı Faz 4 kapsamındadır.

#### Test Durumu
INTEGRATION — uygunluk, sıralama, seçim zamanı, atomik liste değiştirme/temizleme, operatör/gerekçe doğrulaması, başarısız komutta önceki listeyi koruma ve detay yenileme önceliği. Gerçek detay sağlayıcısı entegrasyonu GAME-17'de bekliyor.

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

_Kayıt yok._

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
