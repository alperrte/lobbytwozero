# LTZ — API Kataloğu

> Son güncelleme: 2026-10-22
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
  "category": "opsiyonel; GENERAL | GUIDE | QUESTION | SCREENSHOT (varsayılan GENERAL); yalnız büyük harfli ad kabul edilir",
  "forumId": "UUID, opsiyonel (COM-19); verilirse gönderi o forumda konu olarak açılır. Forum VISIBLE olmalıdır. Forum bir oyuna bağlıysa konu o oyuna bağlanır (gameId verilmezse forumun oyunu atanır; farklı bir gameId 422 döner)"
}
```
Bilinmeyen alanlar (ör. `authorId`) reddedilir (400). `gameId` verilmezse (ve forumdan miras alınmıyorsa) game-service çağrılmaz. Geçersiz `category` (bilinmeyen ad, küçük harf, sayı, boş metin) 400 döner.

#### Response — 201
```json
{
  "id": "UUID",
  "authorId": "UUID",
  "gameId": "UUID | null (bağlı LTZ oyunu)",
  "forumId": "UUID | null (konunun açıldığı forum)",
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
- 404: `forumId` verildi ama forum yok ya da gizli
- 422: `gameId` LTZ oyun kataloğunda yok ya da yayında değil (arşivlenmiş/birleştirilmiş); ya da `gameId`, forumun oyunuyla çelişiyor
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
Public (misafir dahil; kullanıcı kararı). Yalnız `VISIBLE` gönderiler listelenir; silinmiş (`DELETED`) ve moderasyonla gizlenmiş (`HIDDEN`) gönderiler akışta görünmez. Yazma uçları hâlâ kimlik ister. Kimliği doğrulanmış çağıranla arasında **engelleme** (iki yönde, COM-21) bulunan kullanıcıların gönderileri akışta ve aramada listelenmez; misafir ve ilgisiz kullanıcılar etkilenmez. Oran sınırı (anonim erişim için) NOT IMPLEMENTED.

#### Query Parameters
- q: string, opsiyonel, **arama** (COM-11): gönderi içeriğinde "içerir" araması; boşluklar kırpılır, boş değer arama yok sayılır, en az 2 en çok 100 karakter, kontrol karakteri içermez. Büyük/küçük harf duyarsızdır (ASCII için kesin; ASCII dışı harflerin büyük/küçük dönüşümü veritabanı locale'ine bağlıdır, dev/test PostgreSQL imajı `C` locale kullandığından örn. büyük `Ç`, küçük `ç` ile eşleşmez). `%` ve `_` gibi LIKE karakterleri düz metin olarak aranır. Arama servis içinde yapılır (`pg_trgm` GIN indeksi); ayrı search-service yoktur.
- gameId: UUID, opsiyonel, **filtre** (COM-12): yalnız bu LTZ oyununa bağlı gönderiler (`gameId` game-service'te doğrulanmaz; eşleşme yoksa boş sonuç)
- forumId: UUID, opsiyonel, **filtre** (COM-19): yalnız bu forumun konuları. Gizli (`HIDDEN`) bir forumun konuları hiçbir listede görünmez; eşleşme yoksa boş sonuç
- category: `GENERAL | GUIDE | QUESTION | SCREENSHOT`, opsiyonel, **filtre**: yalnız büyük harfli ad
- from: ISO-8601 anı (ör. `2026-10-01T00:00:00Z`), opsiyonel, **filtre**: bu andan itibaren (dahil) oluşturulanlar
- to: ISO-8601 anı, opsiyonel, **filtre**: bu andan önce (hariç) oluşturulanlar; ikisi birlikte verilirse `from` < `to` olmalıdır
- sort: `NEWEST` (varsayılan) | `OLDEST` | `POPULAR`, opsiyonel, **sıralama** (COM-13): yalnız büyük harfli ad. `NEWEST`: `createdAt` azalan; `OLDEST`: `createdAt` artan; `POPULAR`: popülerlik skoru `likeCount + commentCount` azalan, eşitlikte yenilik. Eşitlikte hepsinde `id` ile deterministik sıra. Sayaçlar beğeni (COM-06) ve yorum (COM-07) uçlarıyla güncellenir.
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel, en çok 200 karakter; önceki yanıttaki `nextCursor` (opak, keyset imleci). Sonraki sayfa istenirken aynı `q`, filtreler ve **aynı `sort`** verilmelidir; imleç bunları taşımaz. Başka bir `sort` için üretilmiş imleç 400 döner.

Filtreler ve `q` birlikte (AND) uygulanır.

Yeni gönderiler gelse bile verilmiş bir imleç sayfaları kaydırmaz (keyset sayfalama, COUNT yok). `POPULAR`'da sayfalar arasında bir gönderinin sayaçları değişirse o gönderi sayfa sınırında atlanabilir/tekrarlanabilir; bu, değişken anahtarlı keyset sayfalamanın bilinen sınırıdır.

#### Response — 200
```json
{
  "items": [
    {
      "id": "UUID",
      "authorId": "UUID",
      "gameId": "UUID | null",
      "forumId": "UUID | null (forum konusuysa)",
      "category": "GENERAL | GUIDE | QUESTION | SCREENSHOT",
      "body": "string | null (repost'ta reposter'ın isteğe bağlı notu; null olabilir)",
      "status": "VISIBLE",
      "createdAt": "ISO-8601 UTC",
      "updatedAt": "ISO-8601 UTC",
      "likeCount": 0,
      "commentCount": 0,
      "likedByMe": "boolean (kimlik yoksa her zaman false)",
      "images": [ { "id": "UUID", "postId": "UUID", "mimeType": "image/jpeg | image/png", "sizeBytes": 0, "width": 0, "height": 0, "sortOrder": 0, "contentUrl": "/api/v1/community/posts/{postId}/images/{id}", "createdAt": "ISO-8601 UTC" } ],
      "repostOf": "null | { postId: UUID, available: boolean, authorId: UUID | null, gameId: UUID | null, category, body: string | null, createdAt, images[] } (bkz. 'Repost' kaydı)"
    }
  ],
  "nextCursor": "string | null (son sayfada null)"
}
```
`likeCount`/`commentCount` etkileşim sayaçlarıdır (beğeni ve yorum uçlarıyla atomik güncellenir). `likedByMe`, çağıranın beğenisini gösterir ve sayfa için tek sorguda hesaplanır. Repost öğelerinde `authorId` reposter'dır, orijinal gönderi ve sahibi `repostOf` içinde açık atıf olarak gömülüdür.
Görsel bilgileri sayfa için tek sorguda gelir (içerik baytı yoktur). Yazar yalnız `authorId` olarak döner; görünen ad/avatar user-service sözleşmesi netleşince eklenir.

#### Hata Durumları
- 400: `limit` aralık dışı/sayı değil, `cursor` çok uzun/bozuk/başka `sort` için üretilmiş, `q` 1 karakter / 100'den uzun / kontrol karakterli, `gameId` veya `forumId` UUID değil, `category` veya `sort` bilinmeyen/küçük harfli, `from`/`to` ISO-8601 değil veya `from` >= `to`
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc; gerçek PostgreSQL üzerinde keyset sayfalama, aynı zaman damgası, görünürlük, görsel birleştirme, arama: büyük/küçük harf, LIKE joker kaçırma, SQL enjeksiyonu metni, sayfalama; gerçek beğenilerle `POPULAR` sıralama, `likedByMe`, forum filtresi ve gizli forum konularının akıştan çıkması, repost kaynağı gömme). Gateway ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Kullanıcı Gönderi Geçmişi

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/users/{userId}/posts

#### Yetkilendirme
Public (misafir dahil). Çağıranın kimliği doğrulanmışsa ve `userId` kendi kimliğiyse, kendi moderasyonla gizlenmiş (`HIDDEN`) gönderilerini de görür; diğer herkes (misafir ve başka kullanıcılar) yalnız herkese açık (`VISIBLE`) gönderileri görür. Silinmiş gönderi hiç listelenmez. Bilinmeyen kullanıcı için boş sayfa döner (kullanıcının varlığı sızdırılmaz). Çağıranla arasında engelleme (iki yönde) olan kullanıcının geçmişi boş döner (COM-21). **Henüz yok:** profil gizliliği (user-service) kuralları.

#### Path Parameters
- userId: UUID, zorunlu (gönderi sahibi; doğrulama user-service'ten yapılmaz)

#### Query Parameters
"Gönderi Akışı" ile aynı: `q`, `gameId`, `forumId`, `category`, `from`, `to`, `sort`, `limit`, `cursor` (anlamları ve sınırları aynıdır; arama/filtre/sıralama yalnız bu kullanıcının gönderilerine uygulanır).

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

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/posts/{postId}

#### Yetkilendirme
Public (misafir dahil). Misafir ve diğer kullanıcılar yalnız `VISIBLE` gönderiyi görür; gönderi sahibi kendi gizlenmiş (`HIDDEN`) gönderisini de görür (kimlik varsa). Silinmiş gönderi herkes için 404; sahibi olmayan çağıran için gizlenmiş gönderi de 404'tür (varlığı sızdırılmaz).

#### Path Parameters
- postId: UUID, zorunlu

#### Response — 200
Akıştaki bir öğe ile aynı alanlar (`PostViewResponse`): `id`, `authorId`, `gameId`, `forumId`, `category`, `body`, `status`, `createdAt`, `updatedAt`, `likeCount`, `commentCount`, `likedByMe`, `images[]`, `repostOf`. Gönderinin sahibi, tarihi, içeriği, görselleri ve etkileşim sayaçları bu uçtan döner. Gönderinin yorumları ayrı uçtan okunur (bkz. 'Gönderi Yorumlarını Listele'). Gizli (`HIDDEN`) bir forumun konusu sahibine bile 404 döner. Çağıranla yazar arasında (iki yönde) engelleme (COM-21) varsa içerik hiç yokmuş gibi davranılır (404). Yazarın görünen adı/avatarı user-service sözleşmesi netleşince eklenir.

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
UNIT + INTEGRATION (MockMvc; gerçek PostgreSQL üzerinde görünürlük kuralları, `likedByMe`, gizli forum konusu, repost kaynağı). Gateway ile uçtan uca doğrulama: NOT VERIFIED.

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
  "forumId": "UUID | null",
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
- 409: Gönderi sahibi için düzenlenemez durumda (ör. HIDDEN; **repost düzenlenemez**, yalnız silinebilir) ya da eşzamanlı değişiklik (optimistic lock)
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
- 409: Gönderi düzenlenemez durumda (ör. HIDDEN, repost) ya da eşzamanlı değişiklik
- 422: `gameId` katalogda yok ya da yayında değil; ya da gönderi oyuna bağlı bir forumun konusu ve `gameId` forumun oyunundan farklı (konunun oyun bağlantısı forumunkine eşit kalır)
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
- 409: Gönderi düzenlenemez durumda (ör. HIDDEN, repost) ya da eşzamanlı değişiklik
- 422: Gönderi oyuna bağlı bir forumun konusu; oyun bağlantısı kaldırılamaz
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
Owner. Yalnız gönderi sahibi, yalnız VISIBLE gönderiye görsel ekleyebilir. Sahiplik/görünürlük kuralları "Gönderi Düzenle" ile aynıdır (silinmiş → 404; başkasının VISIBLE gönderisi → 403; başkasının VISIBLE dışı gönderisi → 404; sahibi için VISIBLE dışı → 409). **Repost'a görsel eklenemez** (409): içerik orijinale aittir.

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

### COMMUNITY — Gönderiyi Beğen

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/community/posts/{postId}/like

#### Yetkilendirme
Authenticated. Beğenen kullanıcı yalnız doğrulanmış JWT'nin `sub` claim'inden alınır. Yalnız herkese açık (`VISIBLE`, forumu görünür) gönderi beğenilebilir; silinmiş, gizlenmiş ve gizli forumdaki gönderi (sahibi dahil) 404 döner. Kendi gönderini beğenmek serbesttir. Çağıranla yazar arasında engelleme varsa 404. Oran sınırı NOT IMPLEMENTED.

#### Path Parameters
- postId: UUID, zorunlu

#### Response — 200
```json
{
  "postId": "UUID",
  "liked": true,
  "likeCount": 0
}
```
İşlem **idempotent**tir: aynı kullanıcı aynı gönderiyi tekrar beğenirse mükerrer kayıt oluşmaz ve sayaç artmaz (`post_likes` PK `(post_id, user_id)` + `on conflict do nothing`). `likeCount`, beğeni satırı ekleme ile aynı transaction'da atomik güncellenir.

#### Hata Durumları
- 400: UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 404: Gönderi yok, silinmiş, gizli ya da çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/like

Diğer mikroservisler:
Beğeni gerçekten eklendiğinde (tekrarlanan istekte değil) ve beğenen gönderi sahibi değilse `PostLiked` olayı aynı transaction'da outbox'a yazılır; notification-service'e iletilir (bkz. "Servisler Arası Olay Girişi"). Relay varsayılan kapalı, teslim NOT VERIFIED.

#### Test Durumu
UNIT + INTEGRATION (MockMvc, servis birim testleri, gerçek PostgreSQL: idempotentlik, farklı kullanıcıların toplanması, 12 eşzamanlı kullanıcı ve aynı kullanıcıdan 10 eşzamanlı istek). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Beğeniyi Geri Al

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/community/posts/{postId}/like

#### Yetkilendirme
Authenticated. Yalnız çağıranın kendi beğenisi kaldırılır. Gönderi çağıran için görünür olmalıdır (sahibi kendi gizli gönderisinde dahil); silinmiş ve gizli forumdaki gönderi 404 döner.

#### Path Parameters
- postId: UUID, zorunlu

#### Response — 200
"Gönderiyi Beğen" ile aynı gövde (`liked: false`). Beğeni yoksa işlem no-op'tur ve sayaç düşmez; sayaç hiçbir zaman 0'ın altına inmez.

#### Hata Durumları
- 400: UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 404: Gönderi yok, silinmiş ya da çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/like

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Yorum Yaz / Yoruma Yanıt Ver

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/community/posts/{postId}/comments

#### Yetkilendirme
Authenticated. Yorum sahibi (`authorId`) yalnız JWT `sub` claim'inden alınır. Yalnız herkese açık (`VISIBLE`, forumu görünür) gönderiye yorum yazılabilir; aksi halde 404. Çağıranla gönderi sahibi (ya da yanıtlanan yorumun yazarı) arasında engelleme varsa 404. Oran sınırı ve aynı içeriğin tekrar gönderimine karşı spam kontrolü NOT IMPLEMENTED.

#### Path Parameters
- postId: UUID, zorunlu

#### Request Body
```json
{
  "body": "string, zorunlu, boşluk dışında en az 1 karakter, en çok 2000 karakter, kontrol karakteri içermez (düz metin)",
  "parentCommentId": "UUID, opsiyonel; verilirse yorum bu yoruma yanıttır"
}
```
Yanıt **sınırsız iç içe** olabilir (yanıta yanıt). `parentCommentId`, **aynı gönderide** ve herkese açık (`VISIBLE`) bir yorumu göstermelidir; başka gönderideki, silinmiş, gizli ya da olmayan yorum 404 döner (varlığı sızdırılmaz). Bilinmeyen alanlar (ör. `authorId`) reddedilir (400).

#### Response — 201
```json
{
  "id": "UUID",
  "postId": "UUID",
  "authorId": "UUID",
  "parentCommentId": "UUID | null",
  "body": "string (baştaki/sondaki boşluklar kırpılmış)",
  "status": "VISIBLE",
  "createdAt": "ISO-8601 UTC",
  "updatedAt": "ISO-8601 UTC",
  "replyCount": 0
}
```
Gönderinin `commentCount` sayacı (VISIBLE yorum sayısı, yanıtlar dahil) aynı transaction'da atomik artar.

#### Hata Durumları
- 400: Doğrulama hatası (boş/çok uzun/kontrol karakterli `body`, `parentCommentId` UUID değil, bilinmeyen alan, bozuk JSON), UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 404: Gönderi yok/silinmiş/gizli ya da `parentCommentId` geçerli bir üst yorum değil
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/comments

Diğer mikroservisler:
`CommentCreated` olayı aynı transaction'da outbox'a yazılır; alıcılar gönderi sahibi ve (yanıtsa) üst yorumun yazarıdır, yorumu yazan çıkarılır ve tekrarlar tekilleştirilir (alıcı yoksa olay yazılmaz). notification-service'e iletilir; relay varsayılan kapalı, teslim NOT VERIFIED.

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: yorum/yanıt, 4 seviye iç içe yanıt, çapraz gönderi yanıtı, 12 eşzamanlı yorumda sayaç doğruluğu). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderi Yorumlarını Listele

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/posts/{postId}/comments

#### Yetkilendirme
Public (misafir dahil). Gönderiyi görebilen herkes yorumlarını görür: silinmiş gönderi herkes için, gizli gönderi (ve gizli forum konusu) sahibi dışındakiler için 404'tür. Yalnız **üst düzey** yorumlar listelenir; yanıtlar "Yorumun Yanıtlarını Listele" ucundan okunur. Çağıranla engelleme ilişkisi (iki yönde) olan yazarların yorumları listelenmez (COM-21); `replyCount` bu filtreden etkilenmez.

#### Path Parameters
- postId: UUID, zorunlu

#### Query Parameters
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel, en çok 200 karakter; önceki yanıttaki `nextCursor` (opak, keyset imleci)

#### Response — 200
```json
{
  "items": [
    {
      "id": "UUID",
      "postId": "UUID",
      "authorId": "UUID | null (silinmiş yorumda null)",
      "parentCommentId": null,
      "body": "string | null (silinmiş yorumda null)",
      "status": "VISIBLE | DELETED",
      "createdAt": "ISO-8601 UTC",
      "updatedAt": "ISO-8601 UTC",
      "replyCount": 0
    }
  ],
  "nextCursor": "string | null (son sayfada null)"
}
```
Sıralama eskiden yeniye (`createdAt`, `id`), keyset sayfalama, COUNT sorgusu yok. `replyCount` doğrudan ve herkese açık yanıt sayısıdır (sayfa için tek sorgu). **Silinmiş** (`DELETED`) yorum yalnız yanıtı olduğu için yer tutucu olarak listelenir (içerik ve yazar yok); yanıtı olmayan silinmiş yorum listelenmez. Moderasyonla gizlenmiş (`HIDDEN`) yorum listelenmez.

#### Hata Durumları
- 400: `limit` aralık dışı/sayı değil, `cursor` çok uzun/bozuk, UUID olmayan `postId`
- 404: Gönderi yok, silinmiş ya da çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/comments

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: sıralama, 3 sayfa boşluksuz/tekrarsız sayfalama, yer tutucu kuralı, gizli gönderi görünürlüğü). Gateway ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Yorumun Yanıtlarını Listele

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/comments/{commentId}/replies

#### Yetkilendirme
Public (misafir dahil); yorumun gönderisini görebilen herkes. Gönderi çağıran için görünmezse 404. Moderasyonla gizlenmiş (`HIDDEN`) üst yorumun yanıtları 404'tür; silinmiş üst yorumun yanıtları listelenir (ilişki korunur). Üst yorumun yazarıyla çağıran arasında engelleme varsa 404; engelleme ilişkisi olan yazarların yanıtları listelenmez.

#### Path Parameters
- commentId: UUID, zorunlu (üst yorum)

#### Query Parameters
"Gönderi Yorumlarını Listele" ile aynı: `limit`, `cursor`.

#### Response — 200
"Gönderi Yorumlarını Listele" ile aynı gövde; öğeler üst yorumun **doğrudan** yanıtlarıdır (`parentCommentId` = `commentId`). Daha derin yanıtlar, ilgili yanıtın `replyCount`'u ve kendi `/replies` ucu üzerinden okunur; özyinelemeli sorgu yoktur, bu yüzden iç içe derinlik sorgu maliyetini artırmaz.

#### Hata Durumları
- 400: `limit` aralık dışı/sayı değil, `cursor` çok uzun/bozuk, UUID olmayan `commentId`
- 404: Yorum yok ya da gizli; ya da gönderisi çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/comments/{commentId}/replies

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: iç içe yanıt zinciri, silinmiş yanıt sayımı, gizli/bilinmeyen yorum). Gateway ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Yorumu Düzenle

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
PATCH /api/v1/community/comments/{commentId}

#### Yetkilendirme
Owner. Yalnız yorumun yazarı (JWT `sub` = `authorId`) düzenleyebilir. Yazarı olmayan kullanıcıya `VISIBLE` yorum 403, `VISIBLE` dışı (silinmiş/gizli) yorum 404 döner.

#### Path Parameters
- commentId: UUID, zorunlu

#### Request Body
```json
{
  "body": "string, zorunlu, boşluk dışında en az 1 karakter, en çok 2000 karakter, kontrol karakteri içermez (düz metin)"
}
```
Bilinmeyen alanlar reddedilir (400). İçerik değişmediyse `updatedAt` değişmez. Yanıt ilişkisi (`parentCommentId`) ve gönderi değiştirilemez.

#### Response — 200
"Yorum Yaz" ile aynı gövde (`CommentResponse`; `replyCount` bu uçta 0 döner, güncel sayı liste uçlarındadır).

#### Hata Durumları
- 400: Doğrulama hatası, bilinmeyen alan, bozuk JSON ya da UUID olmayan `commentId`
- 401: Kimlik yok ya da token geçersiz
- 403: Yorum başkasına ait
- 404: Yorum yok, silinmiş ya da gizli
- 409: Eşzamanlı değişiklik (optimistic lock)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/comments/{commentId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: yazar/başkası/silinmiş yorum). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Yorumu Sil

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/community/comments/{commentId}

#### Yetkilendirme
Owner. Yalnız yorumun yazarı silebilir (kurallar "Yorumu Düzenle" ile aynıdır). Silme **soft delete**'tir: `status=DELETED`, `deletedAt` set edilir; satır fiziksel silinmez. Yanıtları olan yorum yer tutucu olarak kalır (içerik ve yazar API'de gösterilmez), yanıt ilişkisi korunur.

#### Path Parameters
- commentId: UUID, zorunlu

#### Response — 204
Gövde yok. Gönderinin `commentCount` sayacı aynı transaction'da atomik düşer; aynı yorumun eşzamanlı çifte silinmesinde yalnız biri başarılı olur (diğerleri 404/409), sayaç bir kez düşer.

#### Hata Durumları
- 400: UUID olmayan `commentId`
- 401: Kimlik yok ya da token geçersiz
- 403: Yorum başkasına ait
- 404: Yorum yok, zaten silinmiş ya da gizli
- 409: Eşzamanlı değişiklik (optimistic lock)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/comments/{commentId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: yer tutucu, sayaç, 6 eşzamanlı silme). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Forum Oluştur

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/community/forums

#### Yetkilendirme
Admin. Yalnız moderatör (`MODERATOR`) ya da yönetici (`ADMIN`) rolü; rol yalnız doğrulanmış JWT'nin `roles` claim'inden okunur (istemci başlıkları güven kaynağı değildir). **Varsayım:** `roles` claim'i auth-service sözleşmesiyle doğrulanmadı (NOT VERIFIED); auth-service sözleşmesi netleşene kadar decoder fail-closed olduğundan gerçek token'lar şimdilik 401 alır. Rolsüz kimlikli kullanıcı 403 alır.

#### Request Body
```json
{
  "slug": "string, zorunlu, 3–80 karakter, [a-z0-9] ve tekil tire (ör. elden-ring), benzersiz, sonradan değişmez",
  "title": "string, zorunlu, en çok 120 karakter, kontrol karakteri içermez",
  "description": "string, opsiyonel, en çok 1000 karakter (boş metin = açıklama yok)",
  "gameId": "UUID, opsiyonel; verilirse LTZ oyun kataloğunda (game-service) doğrulanır ve sonradan değişmez"
}
```
Bilinmeyen alanlar reddedilir (400).

#### Response — 201
```json
{
  "id": "UUID",
  "gameId": "UUID | null",
  "slug": "string",
  "title": "string",
  "description": "string | null",
  "status": "VISIBLE",
  "createdAt": "ISO-8601 UTC",
  "updatedAt": "ISO-8601 UTC"
}
```

#### Hata Durumları
- 400: Doğrulama hatası (slug biçimi/uzunluğu, boş/çok uzun başlık, kontrol karakteri, bilinmeyen alan, bozuk JSON)
- 401: Kimlik yok ya da token geçersiz
- 403: Moderatör/yönetici rolü yok
- 409: Bu `slug` ile bir forum zaten var (ön kontrol ve veritabanı UNIQUE kısıtı)
- 422: `gameId` LTZ oyun kataloğunda yok ya da yayında değil
- 503: `gameId` verildi ama game-service doğrulanamadı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/forums

Diğer mikroservisler:
`gameId` verildiğinde Game Service'e RestClient ile doğrudan `POST /api/v1/game/games/batch` ("Gönderi Oluştur" kaydına bakın). Çağrı transaction dışında yapılır.

#### Test Durumu
UNIT + INTEGRATION (MockMvc, servis birim testleri, gerçek PostgreSQL). Çalışan game-service ve auth-service rol claim'i ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Forumları Listele

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/forums

#### Yetkilendirme
Public (misafir dahil). Yalnız `VISIBLE` forumlar listelenir (gizli forumu moderatör tek tek okuyabilir).

#### Query Parameters
- gameId: UUID, opsiyonel, **filtre**: yalnız bu oyunun forumları (game-service'te doğrulanmaz)
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel, en çok 200 karakter; önceki yanıttaki `nextCursor`

#### Response — 200
```json
{
  "items": [ { "id": "UUID", "gameId": "UUID | null", "slug": "string", "title": "string", "description": "string | null", "status": "VISIBLE", "createdAt": "ISO-8601 UTC", "updatedAt": "ISO-8601 UTC" } ],
  "nextCursor": "string | null (son sayfada null)"
}
```
Sıralama eskiden yeniye, keyset sayfalama. Forumun konuları `GET /api/v1/community/posts?forumId=<id>` ile listelenir.

#### Hata Durumları
- 400: `limit` aralık dışı/sayı değil, `cursor` çok uzun/bozuk, `gameId` UUID değil
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/forums

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: oyun filtresi, sayfalama, gizli forum). Gateway ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Forum Detayı

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/community/forums/{forumId}

#### Yetkilendirme
Public (misafir dahil) `VISIBLE` forum için. Gizli (`HIDDEN`) forum yalnız moderatör/yöneticiye görünür; diğer herkese 404 döner (varlığı sızdırılmaz).

#### Path Parameters
- forumId: UUID, zorunlu

#### Response — 200
"Forum Oluştur" yanıtıyla aynı gövde.

#### Hata Durumları
- 400: UUID olmayan `forumId`
- 404: Forum yok ya da çağıran için görünmez
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/forums/{forumId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Forumu Güncelle / Gizle

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
PATCH /api/v1/community/forums/{forumId}

#### Yetkilendirme
Admin. Yalnız moderatör/yönetici ("Forum Oluştur" kaydındaki rol varsayımı geçerlidir).

#### Path Parameters
- forumId: UUID, zorunlu

#### Request Body
```json
{
  "title": "string, opsiyonel, boşluk dışında en az 1, en çok 120 karakter",
  "description": "string, opsiyonel, en çok 1000 karakter (boş metin = açıklamayı kaldır)",
  "status": "VISIBLE | HIDDEN, opsiyonel"
}
```
Gönderilmeyen alanlar korunur; hiçbir şey değişmezse `updatedAt` değişmez. `slug` ve `gameId` **değiştirilemez** (gönderilirse 400). `HIDDEN`, forumu ve **tüm konularını** akış, arama, profil geçmişi, detay, beğeni, yorum ve repost kaynağından herkes için gizler (konu sahibi dahil); `VISIBLE` hepsini geri getirir. Veri silinmez.

#### Response — 200
"Forum Oluştur" yanıtıyla aynı gövde.

#### Hata Durumları
- 400: Doğrulama hatası (boş başlık, bilinmeyen `status`/alan, bozuk JSON), UUID olmayan `forumId`
- 401: Kimlik yok ya da token geçersiz
- 403: Moderatör/yönetici rolü yok
- 404: Forum yok
- 409: Eşzamanlı değişiklik (optimistic lock)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/forums/{forumId}

Diğer mikroservisler:
- (Moderasyon denetim kaydı admin-service ile Faz 4'te planlıdır; şimdi yok.)

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: gizleme/yeniden açma, konuların akıştan çıkması). auth-service rol claim'i ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderiyi Repostla

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/community/posts/{postId}/reposts

#### Yetkilendirme
Authenticated. Reposter yalnız JWT `sub` claim'inden alınır. Yalnız herkese açık (`VISIBLE`, forumu görünür) gönderi repostlanabilir; silinmiş, gizlenmiş ve gizli forumdaki gönderi (sahibi dahil) 404 döner, böylece erişilemeyen içerik repost üzerinden sızmaz. Çağıranla hedefin yazarı arasında engelleme varsa 404. Hedef bir repost ise kaynak, onun **orijinalidir** (orijinal de açık olmalıdır) ve yeni repost zincir kurmadan doğrudan orijinale bağlanır. Repost'u kaldırmak için repost'un kendisi `DELETE /api/v1/community/posts/{repostId}` ile silinir.

#### Path Parameters
- postId: UUID, zorunlu

#### Request Body
```json
{
  "comment": "string, opsiyonel, en çok 500 karakter, kontrol karakteri içermez (boş metin = düz repost)"
}
```
Gövde tamamen gönderilmeyebilir (düz repost). Bilinmeyen alanlar reddedilir (400).

#### Response — 201
Akıştaki bir öğe ile aynı alanlar (`PostViewResponse`); `authorId` reposter'dır, `body` reposter'ın notudur (null olabilir) ve orijinal **açık atıf** olarak gömülüdür:
```json
{
  "id": "UUID (repost'un kendi kimliği)",
  "authorId": "UUID (reposter)",
  "body": "string | null",
  "repostOf": {
    "postId": "UUID (orijinal gönderi)",
    "available": true,
    "authorId": "UUID (orijinal gönderinin sahibi)",
    "gameId": "UUID | null",
    "category": "GENERAL | GUIDE | QUESTION | SCREENSHOT",
    "body": "string (orijinal içerik)",
    "createdAt": "ISO-8601 UTC",
    "images": []
  }
}
```
Repost içeriği **kopyalamaz**; yalnız referans tutar, orijinalin içeriği, sahibi ve zaman damgaları değişmez. Görünürlük her okumada yeniden hesaplanır: orijinal sonradan silinir, gizlenir, gizli bir foruma düşerse ya da yazarıyla okuyan arasında engelleme olursa `repostOf` yalnız `{ "postId": "UUID", "available": false }` olarak döner (sahip, içerik ve görseller yok); orijinal tekrar açılırsa atıf geri gelir. Repost düzenlenemez (`PATCH` 409), görsel/oyun bağlanamaz; yalnız silinebilir ve beğenilip yorumlanabilir.

#### Hata Durumları
- 400: `comment` çok uzun/kontrol karakterli, bilinmeyen alan, bozuk JSON, UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 404: Gönderi (ya da repost hedefinin orijinali) yok, silinmiş, gizli ya da çağıran için erişilemez
- 409: Bu orijinal bu kullanıcı tarafından zaten (silinmemiş bir repost ile) paylaşılmış (veritabanı UNIQUE indeksi `ux_posts_one_repost_per_user` ile de korunur); eşzamanlı değişiklik
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/reposts

Diğer mikroservisler:
Repost oluşunca ve repost eden orijinalin sahibi değilse `PostReposted` olayı aynı transaction'da outbox'a yazılır; notification-service'e iletilir. Relay varsayılan kapalı, teslim NOT VERIFIED.

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: atıf, orijinalin değişmemesi, mükerrer repost, zincir, erişilemeyen kaynak, sonradan gizlenen/silinen/gizli forumdaki kaynak, görsel gömme, DB kısıtları). Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Gönderiyi Raporla

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend, Admin Service (olay üzerinden)

#### Endpoint
POST /api/v1/community/posts/{postId}/reports

#### Yetkilendirme
Authenticated. Raporlayan (`reporterId`) yalnız doğrulanmış JWT'nin `sub` claim'inden alınır. Yalnız çağıranın görebildiği içerik raporlanabilir: silinmiş, moderasyonla gizlenmiş, gizli forumdaki ya da çağıranla yazarı arasında engelleme olan gönderi 404 döner. **Kendi gönderini raporlayamazsın (422).**

#### Path Parameters
- postId: UUID, zorunlu

#### Request Body
```json
{
  "reason": "SPAM | HARASSMENT | HATE_SPEECH | SEXUAL_CONTENT | VIOLENCE | ILLEGAL_CONTENT | MISINFORMATION | OTHER (zorunlu, yalnız büyük harfli ad)",
  "detail": "string, opsiyonel, en çok 500 karakter, kontrol karakteri içermez (boş metin = açıklama yok)"
}
```
Bilinmeyen alanlar (ör. `reporterId`) ve sıra numarası gibi geçersiz `reason` değerleri reddedilir (400).

#### Response — 201
```json
{
  "id": "UUID",
  "targetType": "POST",
  "targetId": "UUID",
  "reason": "SPAM",
  "status": "OPEN",
  "createdAt": "ISO-8601 UTC"
}
```
Rapor `community_reports` tablosunda içerik referansı (`targetType`, `targetId`), içeriğin yazarı ve gerekçeyle saklanır; yanıtta yazar ve diğer raporlayanlar yer almaz. Aynı kullanıcı aynı içeriği **bir kez** raporlayabilir (DB UNIQUE kısıtıyla da korunur). Kullanıcı başına saatlik rapor sayısı **20** ile sınırlıdır. Rapor, aynı transaction'da outbox'a `ContentReported` olarak yazılır (bkz. "Servisler Arası Olay Girişi" altındaki olay sözleşmesi); rapor durumu yalnız admin-service kararıyla `OPEN` → `RESOLVED` / `DISMISSED` olur.

#### Hata Durumları
- 400: Doğrulama hatası (`reason` yok/bilinmeyen, `detail` çok uzun/kontrol karakterli, bilinmeyen alan), UUID olmayan `postId`
- 401: Kimlik yok ya da token geçersiz
- 404: Gönderi yok, silinmiş ya da çağıran için görünmez (gizli, gizli forum, engelleme)
- 409: Bu içeriği zaten raporladın
- 422: Kendi içeriğini raporlayamazsın
- 429: Saatlik rapor sınırı aşıldı (`Retry-After: 3600`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/posts/{postId}/reports

Diğer mikroservisler:
Admin Service'e `ContentReported` olayı (outbox → relay → `POST {ADMIN_SERVICE_URL}/internal/events`). Relay varsayılan olarak **kapalıdır** (`ltz.outbox.relay.enabled=false`); olaylar `outbox_events` tablosunda `PENDING` bekler ve veri kaybolmaz. Admin Service'in alıcı sözleşmesi henüz yok: teslim NOT VERIFIED.

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL: kayıt, olay, tekilleştirme, sınır, engelleme, erişilemeyen içerik). Admin Service ile uçtan uca teslim: NOT VERIFIED. Gateway ve auth-service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Yorumu Raporla

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Frontend, Admin Service (olay üzerinden)

#### Endpoint
POST /api/v1/community/comments/{commentId}/reports

#### Yetkilendirme
Authenticated. "Gönderiyi Raporla" ile aynı kurallar; ek olarak yalnız herkese açık (`VISIBLE`) yorum raporlanabilir, yorumun gönderisi çağıran için görünür olmalı ve çağıranla yorum yazarı arasında engelleme bulunmamalıdır (aksi halde 404). Kendi yorumun raporlanamaz (422).

#### Path Parameters
- commentId: UUID, zorunlu

#### Request Body
"Gönderiyi Raporla" ile aynı (`reason`, `detail`).

#### Response — 201
"Gönderiyi Raporla" ile aynı gövde (`targetType: COMMENT`, `targetId` = yorum kimliği).

#### Hata Durumları
- 400: Doğrulama hatası, UUID olmayan `commentId`
- 401: Kimlik yok ya da token geçersiz
- 404: Yorum yok, silinmiş/gizli ya da gönderisi/yazarı çağıran için görünmez
- 409: Bu yorumu zaten raporladın
- 422: Kendi yorumunu raporlayamazsın
- 429: Saatlik rapor sınırı aşıldı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/community/comments/{commentId}/reports

Diğer mikroservisler:
"Gönderiyi Raporla" ile aynı (`ContentReported` olayı).

#### Test Durumu
UNIT + INTEGRATION (MockMvc, gerçek PostgreSQL). Admin Service ile uçtan uca teslim: NOT VERIFIED.

### COMMUNITY — Servisler Arası Olay Girişi

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: Admin Service, Social Service (üretici olarak); Notification Service ve Admin Service (community olaylarının alıcısı)

#### Endpoint
POST /internal/events

#### Yetkilendirme
Internal. Gateway'de **route edilmez** (`/api/v1/...` altında değildir). Yalnız `SCOPE_internal.events` yetkisi taşıyan doğrulanmış servis kimliği (JWT `scope`/`scp` claim'inde `internal.events`) kabul edilir; kimlik yoksa 401, yetki yoksa 403. **Varsayım:** servisler arası kimlik modeli (docs/architecture.md risk tablosu: ADR bekleniyor) henüz kararlaştırılmadı; bu uç "servis JWT'si" seçeneğine göre kurulmuştur. JWT doğrulama anahtarı bağlanana kadar decoder fail-closed olduğundan uç fiilen KAPALIDIR (hiçbir token kabul edilmez); gerçek bir servis token'ıyla doğrulama NOT VERIFIED.

#### Request Body (olay zarfı, docs/architecture.md §6.2)
```json
{
  "eventId": "UUID (tekilleştirme anahtarı)",
  "eventType": "ModerationActionTaken | UserBlocked | UserUnblocked",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC",
  "producer": "admin-service | social-service",
  "aggregateId": "UUID",
  "payload": { }
}
```
Zarfta tanımsız alan reddedilir. `producer`, olay türüyle eşleşmelidir (`ModerationActionTaken` yalnız `admin-service`, `UserBlocked`/`UserUnblocked` yalnız `social-service`); aksi halde 400.

**Kabul edilen olaylar ve yükleri**
- `ModerationActionTaken` (admin-service): `{ "targetType": "POST | COMMENT", "targetId": "UUID", "action": "HIDE | RESTORE | REMOVE | DISMISS", "reportId": "UUID (opsiyonel; yalnız tek raporu hedeflemek için)" }`. `HIDE`: VISIBLE içeriği HIDDEN yapar (yorumsa gönderinin `commentCount`'u düşer); `RESTORE`: HIDDEN içeriği geri açar (sayaç geri artar); `REMOVE`: içeriği kalıcı kaldırır (soft delete, gönderi görselleri silinir; **geri alınamaz**, silinmiş içerik `RESTORE` ile geri gelmez); `DISMISS`: içeriği değiştirmez, raporu reddeder. `HIDE`/`REMOVE` içeriğin açık raporlarını `RESOLVED`, `DISMISS` ise `DISMISSED` yapar. Bulunamayan içerik için olay başarıyla işlenir (etkisiz).
- `UserBlocked` / `UserUnblocked` (social-service): `{ "blockerId": "UUID", "blockedId": "UUID" }` (farklı olmalı). Community yerel `user_blocks` yansımasını günceller; engelleme iki yönlü uygulanır (akış/arama/profil/yorum listelerinden gizleme, beğeni/yorum/yanıt/repost/rapor engeli).
- Bilinmeyen `eventType` başarıyla yok sayılır (`IGNORED`), üretici yeniden denemesin diye.

#### Response — 200
```json
{
  "eventId": "UUID",
  "outcome": "PROCESSED | DUPLICATE | IGNORED"
}
```
Olay `inbox_events` tablosunda `eventId` ile tekilleştirilir ve etkisiyle aynı transaction'da uygulanır: aynı `eventId` ikinci kez gelirse `DUPLICATE` döner ve hiçbir şey değişmez. Geçersiz yük `inbox`'a işlenmiş diye kaydedilmez.

#### Hata Durumları
- 400: Zarf doğrulama hatası, üretici-olay uyuşmazlığı, geçersiz/eksik yük alanı (bilinmeyen `action`/`targetType`, UUID olmayan alan, `blockerId` = `blockedId`)
- 401: Kimlik yok ya da token geçersiz
- 403: Servis yetkisi (`internal.events`) yok
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Admin Service ve Social Service bu ucu RestClient/relay ile çağırır. **Community'nin yayınladığı olaylar** (outbox, aynı zarfla alıcıların `POST /internal/events` ucuna iletilir): `ContentReported` → admin-service (`{reportId, targetType, targetId, targetAuthorId, reporterId, reason, detail, reportedAt}`); `CommentCreated` → notification-service (`{commentId, postId, parentCommentId, actorId, recipientIds[], isReply}`); `PostLiked` → notification-service (`{postId, actorId, recipientId}`); `PostReposted` → notification-service (`{postId, repostId, actorId, recipientId}`). Yükler yalnız kimlik içerir (gönderi/yorum/repost metni ve görünen ad taşınmaz); kişi kendi içeriğine yaptığı etkileşim için olay üretilmez. `PostReposted`, docs/architecture.md §6.3 olay kataloğunda henüz yoktur (kataloğa eklenmesi önerilir). Relay varsayılan KAPALIDIR; alıcı sözleşmeleri NOT VERIFIED.

#### Test Durumu
UNIT + INTEGRATION (MockMvc: yetki/doğrulama; gerçek PostgreSQL: moderasyon etkileri ve sayaçlar, tekilleştirme, üretici kontrolü, engelleme yansıması). Admin/Social Service ile uçtan uca doğrulama: NOT VERIFIED.

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
