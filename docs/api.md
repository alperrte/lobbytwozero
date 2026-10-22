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
  "gameId": "UUID, opsiyonel; verilirse LTZ oyun kataloğunda (game-service) doğrulanır",
  "category": "opsiyonel; GENERAL | GUIDE | QUESTION | SCREENSHOT (varsayılan GENERAL); yalnız büyük harfli ad kabul edilir"
}
```
Bilinmeyen alanlar (ör. `authorId`) reddedilir (400). `gameId` verilmezse game-service çağrılmaz. Geçersiz `category` (bilinmeyen ad, küçük harf, sayı, boş metin) 400 döner.

#### Response — 201
```json
{
  "id": "UUID",
  "authorId": "UUID",
  "gameId": "UUID | null (bağlı LTZ oyunu)",
  "category": "GENERAL | GUIDE | QUESTION | SCREENSHOT",
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

### COMMUNITY — Gönderi Akışı

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/posts

#### Yetkilendirme
Public (misafir dahil; kullanıcı kararı). Yalnız `VISIBLE` gönderiler listelenir; silinmiş (`DELETED`) ve moderasyonla gizlenmiş (`HIDDEN`) gönderiler akışta görünmez. Yazma uçları hâlâ kimlik ister. Oran sınırı (anonim erişim için) NOT IMPLEMENTED.

#### Query Parameters
- q: string, opsiyonel, **arama** (COM-11): gönderi içeriğinde "içerir" araması; boşluklar kırpılır, boş değer arama yok sayılır, en az 2 en çok 100 karakter, kontrol karakteri içermez. Büyük/küçük harf duyarsızdır (ASCII için kesin; ASCII dışı harflerin büyük/küçük dönüşümü veritabanı locale'ine bağlıdır, dev/test PostgreSQL imajı `C` locale kullandığından örn. büyük `Ç`, küçük `ç` ile eşleşmez). `%` ve `_` gibi LIKE karakterleri düz metin olarak aranır. Arama servis içinde yapılır (`pg_trgm` GIN indeksi); ayrı search-service yoktur.
- gameId: UUID, opsiyonel, **filtre** (COM-12): yalnız bu LTZ oyununa bağlı gönderiler (`gameId` game-service'te doğrulanmaz; eşleşme yoksa boş sonuç)
- category: `GENERAL | GUIDE | QUESTION | SCREENSHOT`, opsiyonel, **filtre**: yalnız büyük harfli ad
- from: ISO-8601 anı (ör. `2026-10-01T00:00:00Z`), opsiyonel, **filtre**: bu andan itibaren (dahil) oluşturulanlar
- to: ISO-8601 anı, opsiyonel, **filtre**: bu andan önce (hariç) oluşturulanlar; ikisi birlikte verilirse `from` < `to` olmalıdır
- sort: `NEWEST` (varsayılan) | `OLDEST` | `POPULAR`, opsiyonel, **sıralama** (COM-13): yalnız büyük harfli ad. `NEWEST`: `createdAt` azalan; `OLDEST`: `createdAt` artan; `POPULAR` (**kısmi**): popülerlik skoru `likeCount + commentCount` azalan, eşitlikte yenilik. Eşitlikte hepsinde `id` ile deterministik sıra. Sayaçlar Faz 3'te (COM-06/07) dolacağından şimdilik `POPULAR` fiilen `NEWEST` gibi davranır.
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel, en çok 200 karakter; önceki yanıttaki `nextCursor` (opak, keyset imleci). Sonraki sayfa istenirken aynı `q`, filtreler ve **aynı `sort`** verilmelidir; imleç bunları taşımaz. Başka bir `sort` için üretilmiş imleç 400 döner.

Filtreler ve `q` birlikte (AND) uygulanır.

Yeni gönderiler gelse bile verilmiş bir imleç sayfaları kaydırmaz (keyset sayfalama, COUNT yok). `POPULAR`'da sayfalar arasında bir gönderinin sayaçları değişirse (Faz 3 sonrası) o gönderi sayfa sınırında atlanabilir/tekrarlanabilir; bu, değişken anahtarlı keyset sayfalamanın bilinen sınırıdır.

#### Response — 200
```json
{
  "items": [
    {
      "id": "UUID",
      "authorId": "UUID",
      "gameId": "UUID | null",
      "category": "GENERAL | GUIDE | QUESTION | SCREENSHOT",
      "body": "string",
      "status": "VISIBLE",
      "createdAt": "ISO-8601 UTC",
      "updatedAt": "ISO-8601 UTC",
      "likeCount": 0,
      "commentCount": 0,
      "images": [ { "id": "UUID", "postId": "UUID", "mimeType": "image/jpeg | image/png", "sizeBytes": 0, "width": 0, "height": 0, "sortOrder": 0, "contentUrl": "/api/v1/community/posts/{postId}/images/{id}", "createdAt": "ISO-8601 UTC" } ]
    }
  ],
  "nextCursor": "string | null (son sayfada null)"
}
```
`likeCount`/`commentCount` etkileşim sayaçlarıdır; beğeni ve yorum özellikleri Faz 3'te (COM-06/07) geldiğinden şimdilik her zaman 0'dır.
Görsel bilgileri sayfa için tek sorguda gelir (içerik baytı yoktur). Yazar yalnız `authorId` olarak döner; görünen ad/avatar user-service sözleşmesi netleşince eklenir.

#### Hata Durumları
- 400: `limit` aralık dışı/sayı değil, `cursor` çok uzun/bozuk/başka `sort` için üretilmiş, `q` 1 karakter / 100'den uzun / kontrol karakterli, `gameId` UUID değil, `category` veya `sort` bilinmeyen/küçük harfli, `from`/`to` ISO-8601 değil veya `from` >= `to`
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc; gerçek PostgreSQL üzerinde keyset sayfalama, aynı zaman damgası, görünürlük, görsel birleştirme, arama: büyük/küçük harf, LIKE joker kaçırma, SQL enjeksiyonu metni, sayfalama). Gateway ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Kullanıcı Gönderi Geçmişi

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/users/{userId}/posts

#### Yetkilendirme
Public (misafir dahil). Çağıranın kimliği doğrulanmışsa ve `userId` kendi kimliğiyse, kendi moderasyonla gizlenmiş (`HIDDEN`) gönderilerini de görür; diğer herkes (misafir ve başka kullanıcılar) yalnız herkese açık (`VISIBLE`) gönderileri görür. Silinmiş gönderi hiç listelenmez. Bilinmeyen kullanıcı için boş sayfa döner (kullanıcının varlığı sızdırılmaz). **Henüz yok:** engelleme (social-service) ve profil gizliliği (user-service) kuralları — Faz 4/COM-21.

#### Path Parameters
- userId: UUID, zorunlu (gönderi sahibi; doğrulama user-service'ten yapılmaz)

#### Query Parameters
"Gönderi Akışı" ile aynı: `q`, `gameId`, `category`, `from`, `to`, `sort`, `limit`, `cursor` (anlamları ve sınırları aynıdır; arama/filtre/sıralama yalnız bu kullanıcının gönderilerine uygulanır).

#### Response — 200
"Gönderi Akışı" ile aynı (`items[]` + `nextCursor`). Kendi profilinde `status` alanı `HIDDEN` olan öğeler görülebilir.

#### Hata Durumları
- 400: UUID olmayan `userId` ya da "Gönderi Akışı"ndaki geçersiz parametreler
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/users/{userId}/posts

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc; gerçek PostgreSQL üzerinde sahip/başkası/misafir görünürlüğü, silinmiş gönderi, sayfalama/arama/filtre/sıralama). Gateway ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderi Detayı

Durum: IN PROGRESS (kısmi: yorumlar ve etkileşim listeleri Faz 3'te, COM-06/07)
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/posts/{postId}

#### Yetkilendirme
Public (misafir dahil). Misafir ve diğer kullanıcılar yalnız `VISIBLE` gönderiyi görür; gönderi sahibi kendi gizlenmiş (`HIDDEN`) gönderisini de görür (kimlik varsa). Silinmiş gönderi herkes için 404; sahibi olmayan çağıran için gizlenmiş gönderi de 404'tür (varlığı sızdırılmaz).

#### Path Parameters
- postId: UUID, zorunlu

#### Response — 200
Akıştaki bir öğe ile aynı alanlar (`PostViewResponse`): `id`, `authorId`, `gameId`, `body`, `status`, `createdAt`, `updatedAt`, `likeCount`, `commentCount`, `images[]`. Gönderinin sahibi, tarihi, içeriği ve görselleri bu uçtan döner. **Henüz yok:** gönderiye ait yorumlar ve beğeni/repost etkileşim ayrıntıları (Faz 3); sayaçlar şimdilik 0'dır. Yazarın görünen adı/avatarı user-service sözleşmesi netleşince eklenir.

#### Hata Durumları
- 400: UUID olmayan `postId`
- 404: Gönderi yok, silinmiş ya da çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc; gerçek PostgreSQL üzerinde görünürlük kuralları). Gateway ile uçtan uca doğrulama: NOT VERIFIED.

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
  "body": "string, zorunlu, boşluk dışında en az 1 karakter, en çok 5000 karakter, kontrol karakteri içermez (düz metin)",
  "category": "opsiyonel; verilirse kategori değişir, verilmezse mevcut kategori korunur"
}
```
Bilinmeyen alanlar (ör. `authorId`) reddedilir (400). İçerik ve kategori değişmediyse `updatedAt` değişmez.

#### Response — 200
```json
{
  "id": "UUID",
  "authorId": "UUID",
  "gameId": "UUID | null (bağlı LTZ oyunu)",
  "category": "GENERAL | GUIDE | QUESTION | SCREENSHOT",
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
Public (misafir dahil; Faz 2 kararı: akış/detay misafire açık olduğundan görselleri de açık). Silinmiş gönderinin görseli kimseye verilmez; VISIBLE gönderinin görseli herkese, VISIBLE dışı (ör. HIDDEN) gönderinin görseli yalnız kimliği doğrulanmış sahibine verilir; diğerleri için 404.

#### Path Parameters
- postId: UUID, zorunlu
- imageId: UUID, zorunlu; görsel bu gönderiye ait olmalıdır

#### Response — 200
Gövde: görsel baytları. `Content-Type` sunucunun doğruladığı türdür (`image/jpeg` | `image/png`); `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`, `Content-Security-Policy: default-src 'none'; sandbox`, `Cache-Control: no-store`.

#### Hata Durumları
- 400: UUID olmayan `postId`/`imageId`
- 401: Geçersiz token gönderildi (token göndermeyen misafir için 401 yoktur)
- 404: Gönderi/görsel yok, gönderi silinmiş ya da çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/images/{imageId}. VISIBLE gönderilerin görselleri `<img>` etiketiyle doğrudan yüklenebilir (kimlik gerekmez).

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
