# LTZ — API Kataloğu

> Son güncelleme: 2026-10-21
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

Kayıtlar eklendikçe her başlığın altındaki "Kayıt yok" satırı silinir.

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

### COMMUNITY — Gönderi Oluştur

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/community/posts

#### Yetkilendirme
Authenticated. Gönderi sahibi (`authorId`) yalnız doğrulanmış JWT'nin `sub` claim'inden alınır; istek gövdesindeki kimlik güven kaynağı değildir. Not: JWT doğrulama anahtarı/JWKS sözleşmesi auth-service tarafında netleşene kadar community-service decoder'ı fail-closed çalışır; gerçek token'lar şimdilik 401 alır.

#### Request Body
```json
{
  "body": "string, zorunlu, boşluk dışında en az 1 karakter, en çok 5000 karakter, kontrol karakteri içermez (düz metin)",
  "gameId": "UUID, opsiyonel; verilirse LTZ oyun kataloğunda (game-service) doğrulanır"
}
```
Bilinmeyen alanlar (ör. `authorId`) reddedilir (400). `gameId` verilmezse game-service çağrılmaz.

#### Response — 201
```json
{
  "id": "UUID",
  "authorId": "UUID",
  "gameId": "UUID | null (bağlı LTZ oyunu)",
  "body": "string (baştaki/sondaki boşluklar kırpılmış)",
  "status": "VISIBLE",
  "createdAt": "ISO-8601 UTC",
  "updatedAt": "ISO-8601 UTC"
}
```

#### Hata Durumları
- 400: Doğrulama hatası (boş/çok uzun/kontrol karakterli `body`, bilinmeyen alan, bozuk JSON); gövde `violations[]` içinde alan adı ve mesaj döner, girdi değeri döndürülmez
- 401: Kimlik yok, token geçersiz ya da `sub` UUID değil
- 422: `gameId` LTZ oyun kataloğunda yok ya da yayında değil (arşivlenmiş/birleştirilmiş)
- 503: `gameId` verildi ama game-service doğrulanamadı (zaman aşımı/bağlantı/429/beklenmeyen yanıt); gönderi oyun bağlantısı doğrulanmadan yazılmaz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts

Diğer mikroservisler:
`gameId` verildiğinde Game Service'e RestClient ile doğrudan: `POST /api/v1/game/games/batch` (tek kimlik; anonim, istek gövdesi `{"gameIds":[...]}`, yanıt `items[].gameId` / `unavailable[]`). Sözleşme Hamza'nın `feature/game-service` branch'indeki `docs/api.md` GAME-15 kaydındadır (bu branch'te henüz yok). Çağrı transaction dışında yapılır, otomatik yeniden deneme yoktur, adres `GAME_SERVICE_URL`'dendir.

#### Test Durumu
UNIT + INTEGRATION (MockMvc + gerçek PostgreSQL üzerinde Flyway/JPA testi; game-service istemcisi `MockRestServiceServer` ile). Çalışan game-service ile uçtan uca doğrulama: NOT VERIFIED (yerel game-service container'ı batch ucunda 401 döndü; sözleşme henüz o imajda yok). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderi Düzenle

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
PATCH /api/v1/community/posts/{postId}

#### Yetkilendirme
Owner. Yalnız gönderinin sahibi (JWT `sub` = `authorId`) düzenleyebilir. Silinmiş gönderi herkes için 404; sahibi olmayan kullanıcıya VISIBLE dışındaki (ör. moderasyonla gizlenmiş) gönderi 404 döner, VISIBLE gönderi 403 döner. JWT doğrulama notu için "Gönderi Oluştur" kaydına bakın.

#### Path Parameters
- postId: UUID, zorunlu

#### Request Body
```json
{
  "body": "string, zorunlu, boşluk dışında en az 1 karakter, en çok 5000 karakter, kontrol karakteri içermez (düz metin)"
}
```
Bilinmeyen alanlar (ör. `authorId`) reddedilir (400). İçerik değişmediyse `updatedAt` değişmez.

#### Response — 200
```json
{
  "id": "UUID",
  "authorId": "UUID",
  "gameId": "UUID | null (bağlı LTZ oyunu)",
  "body": "string (baştaki/sondaki boşluklar kırpılmış)",
  "status": "VISIBLE",
  "createdAt": "ISO-8601 UTC",
  "updatedAt": "ISO-8601 UTC"
}
```

#### Hata Durumları
- 400: Doğrulama hatası, bilinmeyen alan, bozuk JSON veya UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 403: Gönderi başkasına ait
- 404: Gönderi yok, silinmiş ya da çağıran için görünmez
- 409: Gönderi sahibi için düzenlenemez durumda (ör. HIDDEN) ya da eşzamanlı değişiklik (optimistic lock)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, servis birim testleri, gerçek PostgreSQL üzerinde sürüm/optimistic lock testi). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderiye Oyun Bağla

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/community/posts/{postId}/game

#### Yetkilendirme
Owner. Yalnız gönderi sahibi, yalnız VISIBLE gönderide (kurallar "Gönderi Düzenle" ile aynıdır). Sahiplik, game-service çağrısından **önce** kontrol edilir.

#### Path Parameters
- postId: UUID, zorunlu

#### Request Body
```json
{
  "gameId": "UUID, zorunlu; LTZ oyun kataloğunda doğrulanır"
}
```

#### Response — 200
Güncel gönderi (`PostResponse`, "Gönderi Oluştur" ile aynı alanlar). Aynı `gameId` tekrar bağlanırsa işlem no-op'tur: game-service çağrılmaz, `updatedAt` değişmez.

#### Hata Durumları
- 400: `gameId` yok/UUID değil, bilinmeyen alan, UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 403: Gönderi başkasına ait
- 404: Gönderi yok, silinmiş ya da çağıran için görünmez
- 409: Gönderi düzenlenemez durumda (ör. HIDDEN) ya da eşzamanlı değişiklik
- 422: `gameId` katalogda yok ya da yayında değil
- 503: game-service doğrulanamadı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/game

Diğer mikroservisler:
Game Service'e RestClient ile doğrudan `POST /api/v1/game/games/batch` ("Gönderi Oluştur" kaydına bakın).

#### Test Durumu
UNIT + INTEGRATION (MockMvc, servis birim testleri, `MockRestServiceServer`, gerçek PostgreSQL). Çalışan game-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderiden Oyun Bağlantısını Kaldır

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/community/posts/{postId}/game

#### Yetkilendirme
Owner. Yalnız gönderi sahibi, yalnız VISIBLE gönderide. game-service çağrılmaz. Oyun bağlantısı zaten yoksa işlem no-op'tur (204).

#### Path Parameters
- postId: UUID, zorunlu

#### Response — 204
Gövde yok.

#### Hata Durumları
- 400: UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 403: Gönderi başkasına ait
- 404: Gönderi yok, silinmiş ya da çağıran için görünmez
- 409: Gönderi düzenlenemez durumda (ör. HIDDEN) ya da eşzamanlı değişiklik
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/game

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, servis birim testleri, gerçek PostgreSQL). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderi Sil

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/community/posts/{postId}

#### Yetkilendirme
Owner. Yalnız gönderinin sahibi silebilir. Silme **soft delete**'tir: `status=DELETED`, `deletedAt` ve `updatedAt` set edilir; satır fiziksel silinmez, böylece ileride eklenecek yorum/beğeni/repost kayıtları orphan kalmaz. Gönderiye bağlı görsel baytları (COM-10) aynı işlemde fiziksel olarak silinir. Silinmiş gönderi herkes için 404'tür. Sahibi, moderasyonla gizlenmiş (HIDDEN) kendi gönderisini silebilir. Sahibi olmayan kullanıcıya VISIBLE gönderi 403, VISIBLE dışı gönderi 404 döner.

#### Path Parameters
- postId: UUID, zorunlu

#### Response — 204
Gövde yok.

#### Hata Durumları
- 400: UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 403: Gönderi başkasına ait
- 404: Gönderi yok, zaten silinmiş ya da çağıran için görünmez
- 409: Eşzamanlı değişiklik (optimistic lock)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, servis birim testleri, gerçek PostgreSQL üzerinde soft delete ve CHECK testleri). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderiye Görsel Ekle

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/community/posts/{postId}/images

#### Yetkilendirme
Owner. Yalnız gönderi sahibi, yalnız VISIBLE gönderiye görsel ekleyebilir. Sahiplik/görünürlük kuralları "Gönderi Düzenle" ile aynıdır (silinmiş → 404; başkasının VISIBLE gönderisi → 403; başkasının VISIBLE dışı gönderisi → 404; sahibi için VISIBLE dışı → 409).

#### Path Parameters
- postId: UUID, zorunlu

#### Request Body
`multipart/form-data`, tek parça: `file` (zorunlu).
Sunucu doğrulaması (istemcinin bildirdiği dosya adı/Content-Type dikkate alınmaz):
- Yalnız gerçek içeriği JPEG veya PNG olan dosyalar (GIF, WebP, SVG, HTML vb. reddedilir). WebP, JDK'nın görsel çözücüsü desteklemediği için bu aşamada **desteklenmez**.
- Yüklenen dosya en çok 2 MiB; en çok 6000 px kenar ve 12 milyon piksel; gönderi başına en çok 4 görsel.
- Görsel çözülüp yeniden kodlanır; EXIF/konum ve diğer gömülü metadata ile görselden sonra eklenmiş veri atılır.

#### Response — 201
```json
{
  "id": "UUID",
  "postId": "UUID",
  "mimeType": "image/jpeg | image/png (sunucunun doğruladığı tür)",
  "sizeBytes": "int, saklanan (yeniden kodlanmış) boyut",
  "width": "int",
  "height": "int",
  "sortOrder": "int, 0-3 (boştaki ilk sıra)",
  "contentUrl": "/api/v1/community/posts/{postId}/images/{id}",
  "createdAt": "ISO-8601 UTC"
}
```

#### Hata Durumları
- 400: `file` parçası yok ya da dosya boş, UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 403: Gönderi başkasına ait
- 404: Gönderi yok, silinmiş ya da çağıran için görünmez
- 409: Gönderi düzenlenemez durumda (ör. HIDDEN) ya da gönderide zaten 4 görsel var
- 413: Dosya 2 MiB sınırını aşıyor
- 415: Multipart değil ya da dosya JPEG/PNG değil
- 422: Bozuk görsel ya da boyut/piksel sınırı aşıldı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/images

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (ImageSanitizer birim testleri, MockMvc, gerçek PostgreSQL üzerinde V92 testleri). Container düzeyinde multipart sınırı (`spring.servlet.multipart.*`) ve gateway/auth ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderi Görselini Getir

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/posts/{postId}/images/{imageId}

#### Yetkilendirme
Authenticated. Silinmiş gönderinin görseli kimseye verilmez; VISIBLE gönderinin görseli her kimliği doğrulanmış kullanıcıya, VISIBLE dışı (ör. HIDDEN) gönderinin görseli yalnız sahibine verilir. Misafir (anonim) erişim kararı Faz 2 görünürlük kuralları belirlenince verilecektir; şimdilik varsayılan olarak kapalıdır.

#### Path Parameters
- postId: UUID, zorunlu
- imageId: UUID, zorunlu; görsel bu gönderiye ait olmalıdır

#### Response — 200
Gövde: görsel baytları. `Content-Type` sunucunun doğruladığı türdür (`image/jpeg` | `image/png`); `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`, `Content-Security-Policy: default-src 'none'; sandbox`, `Cache-Control: no-store`.

#### Hata Durumları
- 400: UUID olmayan `postId`/`imageId`
- 401: Kimlik yok ya da token geçersiz
- 404: Gönderi/görsel yok, gönderi silinmiş ya da çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/images/{imageId}. Not: tarayıcı `<img>` etiketi Authorization başlığı gönderemez; kimlik çerezle taşınacaksa auth-service sözleşmesine bağlıdır (NOT VERIFIED).

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, servis birim testleri, gerçek PostgreSQL). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderi Görselini Sil

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/community/posts/{postId}/images/{imageId}

#### Yetkilendirme
Owner. Yalnız gönderi sahibi kendi gönderisinin görselini silebilir (silinmiş gönderi → 404; başkasının VISIBLE gönderisi → 403). Görsel fiziksel olarak silinir. Gönderi silindiğinde (DELETE /posts/{postId}) tüm görselleri de silinir.

#### Path Parameters
- postId: UUID, zorunlu
- imageId: UUID, zorunlu

#### Response — 204
Gövde yok.

#### Hata Durumları
- 400: UUID olmayan `postId`/`imageId`
- 401: Kimlik yok ya da token geçersiz
- 403: Gönderi başkasına ait
- 404: Gönderi/görsel yok ya da çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/images/{imageId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, servis birim testleri). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

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
