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

Henüz kayıtlı API yok. Kayıtlar eklendikçe her başlığın altındaki "Kayıt yok" satırı silinir.

## AUTH

Sorumlu: Kerem · Gateway: `/api/v1/auth/**` · Görevler: AUTH-

Ortak kurallar (aşağıdaki üç uç için):
- Bu uçlar dışındaki her yol, fail-closed güvenlik zinciri nedeniyle kimliksiz istekte `401`, kimlikli istekte `403` döner (Faz 2'de korumalı uçlar açıkça tanımlanacak). Açık olan diğer tek uç `GET /actuator/health`.
- İstek gövdesi en fazla 8 KB'tır; aşan istek `413`, bilinmeyen alan (ör. `role`, `status`) içeren istek `400` alır.
- Hata gövdesi `ProblemDetail` + `code` alanıdır; doğrulama hatalarında `errors: [{field, code}]` döner, gönderilen değer geri yansıtılmaz.
- Yanıtlar e-postanın kayıtlı olup olmadığını ve e-postanın gerçekten gönderildiğini açıklamaz (hesap numaralandırma koruması).

### AUTH — Kayıt ol

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/auth/register

#### Yetkilendirme
Public

#### Request Body
```json
{
  "email": "string, zorunlu, geçerli e-posta, en fazla 254 karakter (büyük/küçük harf duyarsız tekil)",
  "username": "string, zorunlu, ^[A-Za-z0-9_]{3,20}$ (büyük/küçük harf duyarsız tekil, görünen biçim korunur)",
  "password": "string, zorunlu, 12-128 karakter; içeriği dönüştürülmez"
}
```

#### Response — 202
```json
{
  "nextStep": "VERIFY_EMAIL"
}
```
E-posta zaten kayıtlıysa da aynı yanıt döner; yeni hesap açılmaz. Hesap `PENDING_VERIFICATION` durumunda ve yalnız `PLAYER` rolüyle oluşur.

#### Hata Durumları
- 400: Doğrulama hatası (`VALIDATION_FAILED`) veya okunamayan/bilinmeyen alan içeren gövde (`MALFORMED_REQUEST`)
- 409: Kullanıcı adı kullanımda (`USERNAME_TAKEN`)
- 413: İstek gövdesi çok büyük (`PAYLOAD_TOO_LARGE`)
- 503: Parola hash kapasitesi geçici olarak dolu (`SERVICE_BUSY`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/register

Diğer mikroservisler:
Doğrulama kodu `VerificationCodeMailPort` üzerinden iletilir (aşağıdaki "Giden sözleşme taslakları", MAIL-01).
- Development (`dev` profili): Mailpit'e SMTP ile iletilir (Compose profili `dev-mail`, arayüz `http://127.0.0.1:8025`). Mailpit dışarıya e-posta göndermez; production teslimi sayılmaz.
- Production: gerçek Mail Service adaptörü yok — **PENDING**. `prod` profili bu nedenle açılışta bilerek başlamaz (fail-fast).
- Teslim başarısızsa kod `DELIVERY_FAILED` olarak geçersiz kılınır; yanıt hiçbir durumda gönderim iddia etmez.

#### Test Durumu
UNIT + INTEGRATION

### AUTH — E-posta doğrulama kodunu onayla

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/auth/email-verification/confirm

#### Yetkilendirme
Public

#### Request Body
```json
{
  "email": "string, zorunlu, geçerli e-posta",
  "code": "string, zorunlu, ^[0-9]{6}$ (başında 0 olabilir)",
  "password": "string, parola ile kayıt olan hesaplar için ZORUNLU (kayıttaki parola; kayıt sahibi kanıtı)",
  "verificationProof": "string, Steam ile kayıt olan hesaplar için ZORUNLU (steam/complete yanıtı veya callback fragment'i)"
}
```

#### Response — 204
Gövde yok. Hesap `ACTIVE` olur, `email_verified_at` yazılır, kod tek kullanımlık olarak tüketilir. Bu uç token veya oturum üretmez.

#### Hesap ön-ele-geçirme koruması (SEC-01)
Kod tek başına yetmez: e-posta sahibi olmayan biri bir e-postayla kendi kimlik bilgisiyle kayıt açıp kurbana doğrulama kodu gönderilmesini sağlayabilir. Bu yüzden onay, **kaydı yapan tarafın kanıtını** da ister: parola (parola ile kayıt) veya tek kullanımlık, SteamID'ye bağlı `verificationProof` (Steam kaydı). Kanıt yanlış/eksikse kod denemesi tüketilmez ve yanıt diğer hatalarla aynıdır. Mevcut hesabın kimlik bilgisi hiçbir akışta üzerine yazılmaz.

#### Hata Durumları
- 400: Kod geçersiz, süresi dolmuş, kullanılmış, deneme hakkı bitmiş, kayıt sahibi kanıtı (parola/`verificationProof`) eksik veya yanlış ya da e-posta bilinmiyor/zaten doğrulanmış (`VERIFICATION_FAILED`, tüm nedenler için aynı yanıt); doğrulama hatası (`VALIDATION_FAILED`)
- 413: İstek gövdesi çok büyük
- 500: Beklenmeyen sunucu hatası

Kod 10 dakika geçerlidir; kod başına en fazla 5 deneme yapılır (deneme sonuçtan bağımsız sayılır).

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/email-verification/confirm

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION

### AUTH — Doğrulama kodunu yeniden gönder

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/auth/email-verification/resend

#### Yetkilendirme
Public

#### Request Body
```json
{
  "email": "string, zorunlu, geçerli e-posta"
}
```

#### Response — 202
```json
{
  "nextStep": "VERIFY_EMAIL"
}
```
Bilinmeyen, zaten doğrulanmış veya sınıra takılmış hesap için de aynı yanıt döner. Bekleyen hesapta önceki aktif kod geçersiz kılınır ve **yeni** kod üretilir. Bekleme süresi 60 saniye, son 24 saatte en fazla 5 kod.

#### Hata Durumları
- 400: Doğrulama hatası (`VALIDATION_FAILED`)
- 413: İstek gövdesi çok büyük
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/email-verification/resend

Diğer mikroservisler:
Mail Service (MAIL-01) — production **PENDING**; development'ta Mailpit (bkz. Kayıt ol → Bağlantı).

#### Test Durumu
UNIT + INTEGRATION

### AUTH — Oturum ortak kuralları (Faz 2)

- Token'lar yalnız HttpOnly cookie'dedir, gövdede dönmez: `__Host-ltz_access` (Secure, SameSite=Lax, Path=/), `__Secure-ltz_refresh` (Secure, SameSite=Strict, Path=/api/v1/auth/session). Production'da Secure kapatılamaz.
- **CSRF:** Aşağıdaki POST uçları `X-XSRF-TOKEN` başlığı ister; değer `__Host-XSRF-TOKEN` cookie'sinden okunur (`GET /api/v1/auth/csrf` ile üretilir). `Authorization: Bearer` başlığı taşıyan istekler (tarayıcı dışı istemci) muaftır. Eksik/yanlış token: `403 CSRF_TOKEN_INVALID`.
- **Access token (JWT, RS256):** `iss`, `sub` (hesap UUID), `aud=ltz-api`, `iat`, `nbf`, `exp` (5 dk), `jti`, `sid` (oturum), `ver` (token sürümü), `roles`, `amr`; başlık `typ=at+jwt`, `kid`. Diğer servisler imzayı JWKS ile doğrular. Auth uçlarında oturum/hesap durumu her istekte denetlenir (anında iptal); diğer servislerde iptal gecikmesi D3 kararına bağlıdır.
- Kimliksiz istek `401 UNAUTHENTICATED`, izinsiz `403 FORBIDDEN`. Parola/2FA denemeleri: 15 dakikada 5 hata → `429 TOO_MANY_ATTEMPTS` + `Retry-After`.

### AUTH — Giriş

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/auth/login

#### Yetkilendirme
Public (CSRF gerekli)

#### Request Body
```json
{
  "identifier": "string, zorunlu, e-posta veya kullanıcı adı",
  "password": "string, zorunlu, en fazla 128 karakter"
}
```

#### Response — 200
```json
{ "status": "AUTHENTICATED", "accessTokenExpiresAt": "ISO-8601" }
```
Oturum cookie'leri yazılır. TOTP etkinse cookie yazılmaz ve şu döner:
```json
{ "status": "MFA_REQUIRED", "challengeId": "opaque string", "challengeExpiresAt": "ISO-8601" }
```

#### Hata Durumları
- 400: Doğrulama hatası
- 401: Kimlik veya parola hatalı (`INVALID_CREDENTIALS`, hesap var/yok ayrımı yapılmaz)
- 403: Parola doğru ama hesap kullanılamaz (`EMAIL_NOT_VERIFIED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_BANNED`, `ACCOUNT_UNAVAILABLE`); 2FA zorunlu rolde TOTP yok (`TWO_FACTOR_ENROLLMENT_REQUIRED`); CSRF
- 429: Çok fazla deneme (`TOO_MANY_ATTEMPTS`, `Retry-After`)
- 503: Parola hash kapasitesi dolu (`SERVICE_BUSY`)

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/login

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION

### AUTH — Giriş ikinci adımı (2FA)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/auth/login/2fa

#### Yetkilendirme
Public (CSRF gerekli; geçerli `challengeId` şart)

#### Request Body
```json
{
  "challengeId": "string, zorunlu",
  "code": "string, ^[0-9]{6}$ (TOTP) — recoveryCode ile birlikte gönderilemez",
  "recoveryCode": "string, XXXXX-XXXXX (tek kullanımlık)"
}
```

#### Response — 200
```json
{ "status": "AUTHENTICATED", "accessTokenExpiresAt": "ISO-8601" }
```

#### Hata Durumları
- 400: Doğrulama hatası (iki faktör birden veya hiçbiri)
- 401: Kod/kurtarma kodu geçersiz, tekrar kullanılmış, challenge süresi dolmuş veya 5 deneme aşılmış (`MFA_FAILED`)
- 429: Hesap bazlı deneme sınırı

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/login/2fa

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION

### AUTH — Oturum uçları

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
- POST /api/v1/auth/session/refresh — refresh cookie ile yeni access + refresh (rotation)
- POST /api/v1/auth/session/logout — bu cihazdaki oturumu kapatır, cookie'leri siler (idempotent)
- POST /api/v1/auth/session/logout-all — tüm oturumları kapatır, eski access token'ları geçersiz kılar
- GET /api/v1/auth/csrf — CSRF cookie'sini üretir (`204`)

#### Yetkilendirme
refresh/logout: Public (refresh cookie + CSRF) · logout-all: Authenticated (+ CSRF) · csrf: Public

#### Response
- refresh — 200: `{ "status": "AUTHENTICATED", "accessTokenExpiresAt": "ISO-8601" }`
- logout, logout-all — 204

#### Hata Durumları
- 401: Refresh token yok, geçersiz, süresi dolmuş, oturum iptal edilmiş veya hesap aktif değil (`SESSION_INVALID`; cookie'ler silinir). Daha önce döndürülmüş token 10 sn'den sonra yeniden gelirse oturum ailesi tümüyle iptal edilir.
- 403: CSRF
- 409: Aynı refresh token ile eşzamanlı yenileme (`REFRESH_CONFLICT`); oturum korunur, istemci tek refresh isteği yürütüp isteği tekrarlar.

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/session/** ve /api/v1/auth/csrf

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### AUTH — JWKS

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Tüm backend servisleri (access token doğrulama)

#### Endpoint
GET /api/v1/auth/.well-known/jwks.json

#### Yetkilendirme
Public

#### Response — 200
```json
{ "keys": [ { "kty": "RSA", "use": "sig", "alg": "RS256", "kid": "string", "n": "string", "e": "AQAB" } ] }
```
Yalnız açık anahtar alanları döner.

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
`${AUTH_SERVICE_URL}/api/v1/auth/.well-known/jwks.json` (RestClient / resource server jwk-set-uri). Doğrulamada `iss`, `aud=ltz-api`, `exp`, `typ=at+jwt` ve yalnız RS256 zorunludur.

#### Test Durumu
INTEGRATION

### AUTH — TOTP yönetimi

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
- POST /api/v1/auth/2fa/totp/setup — yeni (PENDING) secret; yanıt: `{ "secret": "Base32", "otpauthUri": "otpauth://totp/..." }` (yalnız bir kez gösterilir)
- POST /api/v1/auth/2fa/totp/activate — `{ "code": "123456" }`; yanıt: `{ "recoveryCodes": ["XXXXX-XXXXX", ...10] }` (yalnız bir kez)
- POST /api/v1/auth/2fa/totp/disable — `{ "password": "...", "code": "123456" }` veya `recoveryCode`; yanıt 204

#### Yetkilendirme
Authenticated (+ CSRF). Hesap kimliği yalnız token'dan alınır.

#### Hata Durumları
- 400: Kod geçersiz (`INVALID_TWO_FACTOR_CODE`) veya doğrulama hatası
- 401: Kimliksiz; disable'da parola hatalı (`INVALID_CREDENTIALS`)
- 403: `ADMIN`/`MODERATOR` rolünde kapatma (`TWO_FACTOR_REQUIRED_FOR_ROLE`); CSRF
- 409: Zaten etkin (`TWO_FACTOR_ALREADY_ENABLED`), bekleyen kurulum yok (`TWO_FACTOR_NOT_PENDING`), etkin değil (`TWO_FACTOR_NOT_ENABLED`)
- 429: Deneme sınırı

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/2fa/totp/**

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION

### AUTH — Harici giriş (Steam ve Google)

Durum: IMPLEMENTED (gerçek sağlayıcıyla uçtan uca doğrulanmadı — Google client bilgileri `.env`'de yok)
Sorumlu: Kerem
Tüketici servisler: Frontend (tarayıcı gezinmesi)

#### Endpoint
- GET /api/v1/auth/oauth/google/start — Google'a yönlendirir (`302`): Authorization Code + PKCE S256, `state`, `nonce`
- GET /api/v1/auth/oauth/google/callback?code&state — Google dönüşü
- GET /api/v1/auth/oauth/steam/start — Steam OpenID 2.0'a yönlendirir (`302`)
- GET /api/v1/auth/oauth/steam/callback?state&openid.* — Steam dönüşü
- POST /api/v1/auth/oauth/steam/complete — Steam ile yeni hesap: `{ "registrationToken", "email", "username" }` → `202 { "nextStep": "VERIFY_EMAIL", "verificationProof": "<tek kullanımlık>" }`. `verificationProof` her yanıtta döner (e-posta kayıtlı olsa bile; hesap varlığı sızdırılmaz) ve `email-verification/confirm` isteğinde gönderilir.

#### Yetkilendirme
Public. Callback'ler, start'ın yazdığı HttpOnly `__Host-ltz_oauth` state cookie'si ile eşleşen, tek kullanımlık `state` ister (login CSRF koruması). `complete` yalnız tek kullanımlık kayıt token'ına dayanır (cookie/CSRF gerekmez).

#### Sonuç iletimi
Start ve callback `302` döner. Callback sonucu yapılandırmadaki SABİT frontend adresine (`FRONTEND_PUBLIC_URL/auth/callback`) URL **fragment**'i ile iletilir; istemciden yönlendirme adresi alınmaz.
- `#status=authenticated` — oturum cookie'leri yazıldı
- `#status=mfa_required&challenge=<id>` — TOTP etkin; `POST /login/2fa` ile tamamlanır, cookie yazılmaz
- `#status=registration_required&token=<t>` — yalnız Steam, bağsız SteamID; `steam/complete` çağrılır
- `#status=email_verification_required&proof=<t>` — yalnız Steam, SteamID e-postası henüz doğrulanmamış hesaba bağlı; `proof` ile `email-verification/confirm` çağrılır (cookie yazılmaz)
- `#error=<KOD>` — `EXTERNAL_AUTH_FAILED` (state/imza/nonce/aud/iss/replay/sağlayıcı hatası; ayrıntı verilmez), `EXTERNAL_EMAIL_UNVERIFIED`, `ACCOUNT_LINK_REQUIRED`, `EMAIL_NOT_VERIFIED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_BANNED`, `ACCOUNT_UNAVAILABLE`, `TWO_FACTOR_ENROLLMENT_REQUIRED`, `PROVIDER_NOT_CONFIGURED`

#### Güvenlik kuralları
- **Eşleştirme:** yalnız sağlayıcının değişmez kimliğiyle (Steam: doğrulanmış SteamID64, Google: `sub`). E-posta eşleşmesiyle hesaplar **birleştirilmez**; kayıtlı e-postayla gelen yeni Google kimliği `ACCOUNT_LINK_REQUIRED` alır (bağlama ucu henüz yok).
- **Google:** `id_token` Google JWKS ile RS256 doğrulanır; `iss`, `aud=client_id`, `exp/iat`, `nonce`, `sub` zorunlu. Yeni hesap yalnız `email_verified=true` ise açılır (ACTIVE, parolasız, `PLAYER`). PKCE verifier ve nonce state'ten sunucu anahtarıyla türetilir.
- **Steam:** `ns`, `mode=id_res`, `op_endpoint`, `return_to` (state dahil birebir), `claimed_id` (steamcommunity.com + 17 haneli ID) = `identity`, `openid.signed` kapsamı, `response_nonce` zaman penceresi (5 dk) ve tek kullanım, ardından Steam'e `check_authentication`. Çoğaltılmış `openid.*` alanları reddedilir. Yeni Steam hesabı `PENDING_VERIFICATION` açılır; e-posta AUTH-02 koduyla doğrulanmadan oturum verilmez. Kayıtlı e-postada hesap açılmaz, yanıt değişmez.
- Harici giriş 2FA'yı atlatmaz: TOTP etkinse challenge, `ADMIN`/`MODERATOR` için TOTP yoksa oturum verilmez. Oturumun `amr` değeri `fed` içerir.
- `GOOGLE_CLIENT_ID/SECRET` boşsa Google girişi kapalıdır (`503 PROVIDER_NOT_CONFIGURED`). Production'da tüm adresler `https` olmalıdır (aksi halde servis başlamaz).

#### Hata Durumları
- 400: `steam/complete` doğrulama hatası veya `REGISTRATION_TOKEN_INVALID`
- 409: `steam/complete` kullanıcı adı çakışması (`USERNAME_TAKEN`)
- 503: Google yapılandırılmamış (`start`)

#### Bağlantı
Frontend:
Tarayıcı bağlantısı ile API Gateway üzerinden /api/v1/auth/oauth/** (Google'da kayıtlı redirect URI: `<AUTH_PUBLIC_BASE_URL>/api/v1/auth/oauth/google/callback`)

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (sağlayıcılar yerel sahte sunucularla; gerçek Google/Steam ile denenmedi)

### AUTH — Parola değiştir

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/auth/password/change

#### Yetkilendirme
Authenticated (+ CSRF). Hesap kimliği yalnız token'dan alınır.

#### Request Body
```json
{
  "currentPassword": "string, zorunlu",
  "newPassword": "string, zorunlu, 12-128 karakter; içeriği dönüştürülmez/kırpılmaz",
  "newPasswordConfirmation": "string, zorunlu, newPassword ile birebir aynı"
}
```

#### Response — 204
Gövde yok. Hesabın **tüm** oturumları (bu cihaz dahil) iptal edilir, token sürümü artar (eski access token'lar düşer), bekleyen sıfırlama token'ları geçersiz olur, cookie'ler silinir; yeniden giriş gerekir. 2FA etkilenmez.

#### Hata Durumları
- 400: Doğrulama hatası (`VALIDATION_FAILED`; tekrar uyuşmazlığı alanı `newPasswordConfirmed`), mevcut parola yanlış (`CURRENT_PASSWORD_INVALID`; parolasız harici hesap dahil), yeni parola mevcutla aynı (`NEW_PASSWORD_SAME_AS_CURRENT`)
- 401: Kimliksiz veya oturum geçersiz
- 403: CSRF
- 429: Mevcut parola denemesi hesap bazında 15 dakikada 5 hatayla sınırlı (`TOO_MANY_ATTEMPTS`, `Retry-After`)

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/password/change

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### AUTH — Şifremi unuttum

Durum: IMPLEMENTED (development'ta Mailpit; production e-posta teslimi PENDING)
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
- POST /api/v1/auth/password/forgot — `{ "email": "…" }` → `202 { "nextStep": "CHECK_EMAIL" }`
- POST /api/v1/auth/password/reset — `{ "token", "newPassword", "newPasswordConfirmation" }` → `204`

#### Yetkilendirme
Public (cookie okumaz; CSRF gerekmez). `reset` yetkisini gövdedeki tek kullanımlık token'dan alır.

#### Davranış
- `forgot` hesap varlığını sızdırmaz: bilinen/bilinmeyen/uygun olmayan (doğrulanmamış, askıda, parolasız) hesapta yanıt aynıdır; e-posta yalnız ACTIVE ve parolalı hesaba gider ve istek iş parçacığı dışında teslim edildiği için yanıt süresi teslimden bağımsızdır. Aynı e-posta için 15 dakikada 5 istek, hesap varlığından bağımsız sınırlıdır (`429`). Aynı hesap için 60 sn bekleme içindeki istek sessizce e-posta üretmez; yeni istek eski token'ı geçersiz kılar.
- Token: 256 bit rastgele, 30 dk geçerli, tek kullanımlık, yalnız SHA-256 özeti saklanır. Bağlantı: `{FRONTEND_PUBLIC_URL}/auth/reset-password#token=<token>` (fragment sunucuya/Referer'a gitmez; frontend token'ı `reset` gövdesinde gönderir).
- `reset` başarılıysa parola değişir, tüm oturumlar ve eski access token'lar iptal edilir. **Otomatik giriş yapılmaz, 2FA kapatılmaz** (giriş sonrası TOTP yine istenir).
- Teslim edilemeyen token `DELIVERY_FAILED` ile geçersiz kılınır; yanıt gönderim iddia etmez.

#### Hata Durumları
- 400: Doğrulama hatası; `PASSWORD_RESET_TOKEN_INVALID` (geçersiz, süresi dolmuş, kullanılmış, hesap aktif değil — tek yanıt)
- 429: `forgot` istek sınırı (`Retry-After`)

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/password/**

Diğer mikroservisler:
Mail Service (MAIL-02) — production **PENDING**; development'ta Mailpit (`PasswordResetMailPort`).

#### Test Durumu
INTEGRATION

### AUTH — Yönetici: hesap durumu ve rol yönetimi

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend (yönetim paneli), admin-service (servis kimliği D4 gelene kadar kullanıcı token'ıyla; ayrı `/internal` ucu yok)

#### Endpoint
- GET /api/v1/auth/admin/roles — kanonik rol kodları (`PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`)
- GET /api/v1/auth/admin/accounts/{accountId} — `{ id, username, status, roles, twoFactorEnabled, createdAt }` (e-posta/parola yok)
- POST /api/v1/auth/admin/accounts/{accountId}/status — `{ "status": "SUSPENDED|BANNED|DISABLED|ACTIVE", "reason": "1-500 karakter" }` → `204`
- POST /api/v1/auth/admin/accounts/{accountId}/roles/grant — `{ "role", "reason" }` → `204`
- POST /api/v1/auth/admin/accounts/{accountId}/roles/revoke — `{ "role", "reason" }` → `204`
- GET /api/v1/auth/admin/account-deletions/pending?limit=50 — tamamlanmamış hesap silme görevleri (`limit` 1–200 aralığına sınırlanır): `[{ requestId, accountId, service, attempts, lastError, nextAttemptAt, createdAt, stuck }]`. `stuck`, `ltz.auth.deletion.stuck-attempts` (varsayılan 10) denemeye ulaşıldığını gösterir. E-posta/kullanıcı adı içermez. Takılı görev otomatik onaylanmaz; ulaşılamayan servis başarı sayılmaz.

#### Yetkilendirme
Admin: geçerli access token + `ROLE_ADMIN`, servis sınırında SecurityConfig ile zorunlu (Gateway'e/arayüze bırakılmaz); kimliksiz `401`, ADMIN olmayan `403`. Cookie ile çağrıda CSRF gerekir; `Authorization: Bearer` ile gerekmez. Yönetici kimliği yalnız token'dan alınır.

#### Kurallar
- **Durum geçişleri:** `ACTIVE → SUSPENDED|BANNED|DISABLED`, `SUSPENDED → ACTIVE|BANNED`, `DISABLED → ACTIVE`. `BANNED` sondur (kaldırma kararı açık). `PENDING_VERIFICATION` (AUTH-02) ve `DELETION_REQUESTED/DELETED` (AUTH-16) bu uçtan yönetilmez. Süreli askı henüz yok.
- **ACTIVE dışına çıkış:** hesabın tüm oturumları iptal edilir ve token sürümü artar; eski access/refresh token'lar auth uçlarında anında düşer, giriş `403` olur.
- **Rol değişikliği:** hesabın oturumları iptal edilir (yeni rol için yeniden giriş). `PLAYER` alınamaz. `ADMIN`/`MODERATOR` yalnız **ACTIVE ve TOTP'si etkin** hesaba verilir (2FA zorunluluğu atlatılamaz).
- **Yetki yükseltme yok:** yönetici kendi durumunu/rolünü değiştiremez (`SELF_MODIFICATION_FORBIDDEN`).
- Her işlem gerekçe + yönetici kimliğiyle `account_status_history` / `account_role_history` tablolarına ve güvenlik olaylarına yazılır.

#### Hata Durumları
- 400: Doğrulama (gerekçe boş/501+ karakter, bilinmeyen durum/rol)
- 401 / 403: kimliksiz / ADMIN değil / kendi hesabı / CSRF
- 404: Hesap yok (`ACCOUNT_NOT_FOUND`)
- 409: `INVALID_STATUS_TRANSITION`, `TARGET_NOT_ACTIVE`, `ROLE_ALREADY_ASSIGNED`, `ROLE_NOT_ASSIGNED`, `BASE_ROLE_REQUIRED`, `ROLE_REQUIRES_TWO_FACTOR`

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/admin/**

Diğer mikroservisler:
admin-service servis JWT'si (D4) hazır olduğunda `/internal` ucu üzerinden bağlanacak — PENDING.

#### Test Durumu
INTEGRATION

### AUTH — Hesap silme

Durum: IMPLEMENTED (Auth tarafı); diğer servislerle koordinasyon **PENDING** (hedef servislerde silme ucu ve servis kimliği yok)
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
- POST /api/v1/auth/account/deletion/request — yeniden doğrulama; yanıt `202 { "nextStep": "CONFIRM_BY_EMAIL" }`
- POST /api/v1/auth/account/deletion/confirm — `{ "token": "…", "username": "…" }`; yanıt `202 { "nextStep": "DELETION_IN_PROGRESS" }`

#### Yetkilendirme
Authenticated (+ CSRF). Hesap ve oturum yalnız token'dan alınır; e-posta token'ı yalnız isteği yapan hesap için geçerlidir.

#### Request Body (`request`)
```json
{
  "password": "string; parolalı hesapta zorunlu",
  "code": "string ^[0-9]{6}$; TOTP etkinse code veya recoveryCode zorunlu (yalnız biri)",
  "recoveryCode": "string"
}
```
Parolasız (yalnız Google/Steam) hesapta parola gönderilmez; son 10 dakika içinde açılmış bir oturum aranır, aksi halde `403 REAUTHENTICATION_REQUIRED` (sağlayıcıyla yeniden giriş yapılır).

#### Davranış
1. **İstek:** yeniden doğrulama + TOTP başarılıysa e-postaya 256 bit, tek kullanımlık, 30 dk geçerli token gider (bağlantı: `{FRONTEND_PUBLIC_URL}/account/delete#token=…`; yalnız SHA-256 özeti saklanır). Hesap bu aşamada silinmez. Aynı hesap için 60 sn içindeki ikinci istek yeni e-posta üretmez; yeni istek eski token'ı geçersiz kılar.
2. **Onay:** token + kullanıcı adının yazılması (açık onay). Hesap `DELETION_REQUESTED` olur, tüm oturumlar iptal edilir, cookie'ler silinir, hesap başına **tek** silme talebi ve her veri sahibi servis için bir silme görevi açılır (idempotent).
3. **Koordinasyon (arka plan):** Auth her servise `DELETE <SERVICE>_SERVICE_URL/internal/v1/accounts/{accountId}` çağrısı yapar (idempotent; 2xx = silindi). Başarısız görev üstel geri çekilmeyle (1 dk → en fazla 6 sa) yeniden denenir; onaylanan görev tekrarlanmaz. **Tüm** servisler onaylayınca Auth'a ait kişisel veri silinir ve hesap anonimleştirilip `DELETED` olur.
4. **Saklananlar (KVKK/denetim):** güvenlik olayları (1 yıl), durum ve rol geçmişi, silme talebi/görev kayıtları, anonimleştirilmiş hesap iskeleti (kimlik). **Silinenler:** e-posta, kullanıcı adı, parola özeti, harici kimlikler, TOTP sırrı, kurtarma kodları, oturumlar, tüm token'lar.

#### Hata Durumları
- 400: Doğrulama; yanlış parola (`CURRENT_PASSWORD_INVALID`); yanlış/eksik ikinci faktör (`INVALID_TWO_FACTOR_CODE`); geçersiz/süresi dolmuş token veya yanlış kullanıcı adı (`DELETION_CONFIRMATION_INVALID`)
- 401: Kimliksiz / oturum geçersiz (onaydan sonra eski oturum dahil)
- 403: `REAUTHENTICATION_REQUIRED`; CSRF
- 409: Yönetici hesabı silinemez (`ADMIN_DELETION_FORBIDDEN`; önce ADMIN rolü kaldırılmalı)
- 429: Yeniden doğrulama denemesi hesap bazında sınırlı (`TOO_MANY_ATTEMPTS`)

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/account/deletion/**

Diğer mikroservisler:
Mail Service (`AccountDeletionMailPort`) — production **PENDING**, development'ta Mailpit. Veri sahibi servisler (`DELETE /internal/v1/accounts/{accountId}`, bkz. aşağıdaki taslak ve `docs-auth/servis-sozlesmeleri.md`) — **PENDING**.

#### Test Durumu
INTEGRATION

### AUTH — Giden sözleşme taslakları (PLANNED, onay bekliyor)

- **Auth → her veri sahibi servis, hesap verisi silme (AUTH-16/17):** `DELETE /internal/v1/accounts/{accountId}`, Yetkilendirme: Internal. Servis JWT'si (D4): `iss=ltz-auth`, `aud=<hedef servis>`, `scope=account:erase`, `typ=svc+jwt`, ≤60 sn ömür, Auth özel anahtarıyla RS256. Hedef servis imza/aud/scope/typ'ı bağımsız doğrular; kullanıcı cookie'sine dayanmaz. İdempotent: 2xx = veri silindi/anonimleştirildi (zaten yoksa da 2xx), başka her yanıt başarısızlık sayılır ve tekrarlanır. Auth'ta onay alan bir iç uç **yoktur**.

Bunlar Auth'un çağıracağı/yayımlayacağı sözleşmelerdir; karşı servisin kaydı o servis uygulanırken kendi başlığı altında açılır.

- **Auth → Mail, doğrulama kodu teslimi (MAIL-01):** `POST /internal/v1/mail/verification-codes`, Yetkilendirme: Internal (Auth'un imzaladığı, `aud=mail-service` sınırlı servis JWT'si — D4, Faz 2). Gövde: `idempotencyKey` (UUID, kod kaydı kimliği), `accountId`, `recipientEmail`, `code`, `validitySeconds`, `locale` (`tr`/`en`, açık konu). Mail Service yalnız teslim eder; kodu düz metin olarak kalıcı tutmaz (`variables_encrypted`, gönderim sonrası temizleme) ve loglamaz. `202` = teslim kuyruğa alındı; diğer her yanıt Auth'ta teslim başarısızlığı sayılır. Kimlik doğrulaması olmadan bu uç açılmaz.
- **Auth → User, `UserRegistered` olayı:** Hesap `ACTIVE` olduktan **sonra** (kayıt anında değil) outbox ile yayımlanır. Alanlar: `eventId`, `accountId`, `username`, `occurredAt`. User Service profili `accountId` ile idempotent oluşturur; parola, e-posta doğrulama durumu ve rol User'a taşınmaz. Outbox altyapısı henüz yoktur (sonraki faz).

## USER

Sorumlu: Kerem · Gateway: `/api/v1/user/**` · Görevler: USER-

_Kayıt yok._

## MAIL

Sorumlu: Kerem · Gateway: `/api/v1/mail/**` · Görevler: MAIL-

_Kayıt yok._

## GAME

> Auth entegrasyonu (2026-10-22): Korumalı GAME uçları Auth'un RS256 imzasını JWKS ile,
> `typ=at+jwt`, `kid`, issuer/audience, UUID `sub` ve zorunlu `exp`/`nbf`/`iat` alanlarını
> doğrular. Mevcut `AUTH_SERVICE_URL`, `JWT_ISSUER`, `JWT_AUDIENCE` kullanılır.
> `roles` yalnız metin listesidir; ayrıcalıklı roller birebir eşleşir. Eski metin/`ROLE_`/küçük harf
> esnekliği kaldırılmıştır. Public uçlar anonim kalır; geçersiz Bearer varsa 401 döner.
> `SecurityBoundaryTest` mevcut 29 korumalı method/path için anonim 401 ve feedback dışındaki
> yollar için PLAYER 403 sınırını kontrol eder. `RealAuthIntegrationTest` gerçek Auth üreticisi/JWKS
> koduyla PLAYER feedback, ADMIN audit ve geliştirici grant senaryolarını doğrular.
> İzole PostgreSQL/Mailpit ve ayrı Auth/Gateway/Game JVM'leriyle gerçek kayıt, e-posta doğrulama,
> login, Gateway cookie/Bearer, PLAYER feedback/IDOR ve CSRF/Origin akışları **44 kontrol PASS**.
> Onaylı yerel anahtar kurulumu sonrasında mevcut DEV'de Auth/Gateway/Game health,
> Gateway routing ve Game konteynerinden Auth JWKS erişimi **PASS**.
> DEV üzerinde canlı ADMIN/2FA ve geliştirici grant mutasyonları **NOT VERIFIED**.
> Ayrıntı: [Game–Auth entegrasyon kaydı](../docs-Game%20Service/LTZ-HAM-GAME-04-Auth-Entegrasyonu.md).

Sorumlu: Hamza · Gateway: `/api/v1/game/**` · Görevler: GAME-

### GAME — Oyun alt verileri (GAME-08)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend; ileride Recommendation

#### Endpoint
GET /api/v1/game/{gameId}/metadata

#### Yetkilendirme
Public. Bu uç yalnız okur; yönetim yazmaları ayrı, korumalı uçlardadır (admin/developer/moderation). Tanımsız yollar reddedilir.

#### Path Parameters
- gameId: UUID, zorunlu

#### Response — 200
- `gameId`: kanonik UUID.
- `genres`, `categories`, `tags`, `platforms`: birbirinden ayrı diziler; elemanlar `id`, `code`, `nameTr`, `nameEn` içerir.
- `developers`, `publishers`: ayrı ad dizileri.
- `media`: `type` (COVER/SCREENSHOT/TRAILER), `url` (HTTPS), nullable `provider`, `sortOrder`.
- `systemRequirements`: `level`, `platform`, nullable `os`, `cpu`, `gpu`, `ramMb`, `storageMb`, `rawText`.
- Sağlayıcının sunmadığı koleksiyonlar boş dizi, bilinmeyen skaler alanlar null döner. Ham metin HTML olarak işlenmez.
- `languages`: genel dil desteği için saklanan normalize kodlar; boş liste destek olmadığı anlamına gelmez. `earlyAccess`: true/false/null; null bilinmiyor. GAME-23 ayrıntıları ek alanlardadır; bu eski listenin anlamı ve filtre davranışı korunur.
- GAME-23 ek alanları: `metadataLanguage` (nullable normalize dil kodu), `languageDataState` (`UNKNOWN/EMPTY/INVALID/AVAILABLE`), `languageSupport` ve `provenance`.
- `languageSupport`: `{languageCode, interfaceSupport, audioSupport, subtitleSupport}`; her destek bağımsız `UNKNOWN/SUPPORTED/UNSUPPORTED`. Eski genel dil listesi bu üç durumu kanıtlamaz. Eski kayıtlar UNKNOWN ve boş ayrıntı listesiyle döner; false/UNSUPPORTED üretilmez.
- `provenance`: alan adı, kaynak (`ADMIN` veya gerçekten veri alınan sağlayıcı kodu), `dataState`, `recordedAt`, nullable `verifiedAt`. Kayıt/alım zamanı doğrulama değildir. ADMIN doğrulaması harici sağlayıcı doğrulaması olarak gösterilmez. Eski kaynağı bilinmeyen alanlarda kayıt yoktur; atıf/tarih uydurulmaz. Operatör kimliği ve gerekçe public yanıtta yoktur.
- `LOCALIZATION.verifiedAt` yalnız admin'in metadata dili ve dil desteği beyanını doğruladığı zamanı gösterir; description veya diğer metadata alanlarının dili, kaynağı veya doğruluğuna güvence vermez. Sağlayıcıdan dil bilgisi alınmaz ve otomatik dil tespiti yapılmaz. `GAME_TYPE` UNKNOWN ise kaynak veri durumu da UNKNOWN'dır.

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
Public GET. Yalnız ACTIVE + MAIN_GAME ve editör listesinde bulunan oyunlar. Bu uç yalnız okur; listeyi değiştirmek için `PUT /api/v1/game/admin/popular` (Admin) kullanılır.

#### Query Parameters
page: varsayılan 0, 0–10000. size: varsayılan 20, 1–100. Sabit sıra POPULARITY DESC + gameId ASC; diğer filtreler için genel liste + popularOnly kullanılır.

#### Response — 200
GameDiscoveryResponse. popularityBasis=EDITORIAL, sort=POPULARITY, direction=DESC. editorialRank seçim sırası, popularityUpdatedAt bu seçimin UTC zamanıdır. Steam trendi veya LTZ etkileşim skoru iddiası yoktur. Editör listesi boşsa sonuç boş gelir; oyunlara tek tek dış detay çağrısı yapılmaz.

#### Hata Durumları
- 400: Geçersiz page/size.
- 500: Genel sunucu hatası.

#### Bağlantı
Frontend: Gateway üzerinden aynı path. Liste yetkili yönetim ucuyla (`PUT /api/v1/game/admin/popular`) hazırlanır.

#### Test Durumu
INTEGRATION — uygunluk, sıralama, seçim zamanı, atomik liste değiştirme/temizleme, operatör/gerekçe doğrulaması, başarısız komutta önceki listeyi koruma ve detay yenileme önceliği. Gerçek detay sağlayıcısı entegrasyonu GAME-17'de bekliyor.

### GAME — Toplu oyun kartı (GAME-15)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Library Service, Review Service, Frontend

#### Endpoint
POST /api/v1/game/games/batch

#### Yetkilendirme
Public. Kimlik/JWT gerekmez ve gelecekte de zorunlu tutulmaz. Yalnız herkese açık (ACTIVE) kart verisi döner; kişisel, yönetimsel veya LTZ puanı verisi içermez. Kullanıcıya özel ya da yetki gerektiren toplu okumalar bu uca eklenmez, ayrı korumalı sözleşmeyle tasarlanır. Geçersiz bir `Authorization` başlığı yine 401 verir. CSRF: durum değiştirmez ve çerez kullanmaz; bu tek istek, yalnız `Content-Type: application/json` ise CSRF filtresinden açıkça atlanır (başka içerik türleri 403 alır), başka hiçbir istek atlanmaz.
**İstek sınırı:** istemci başına jeton kovası (ani yük 100, sürekli 20 istek/sn; `ltz.rate-limit.batch.*`). Aşımda 429. İstemci anahtarı doğrudan bağlantı adresidir; `X-Forwarded-For` varsayılan olarak yok sayılır. Gateway istemci IP'sini iletmediği sürece Gateway'den gelen trafik tek bir kovayı paylaşır; doğrudan çağıran iç servisler kendi anahtarını kullanır.

#### Request Body
```json
{ "gameIds": ["UUID (1–50 adet, tekrarlar tek sayılır)"] }
```

#### Response — 200
```json
{
  "items": [ { "gameId": "UUID", "title": "…", "origin": "INTERNAL|EXTERNAL", "metadataCompleteness": "…", "gameType": "…", "externalReferences": [], "coverUrl": "https://… | null" } ],
  "unavailable": ["UUID"]
}
```
- `items`: istek sırasını korur; her eleman `GET /{gameId}/card` ile aynı sözleşmedir. Tür (MAIN_GAME, DLC vb.) fark etmeksizin ACTIVE kayıtlar döner.
- `unavailable`: bulunamayan, ARCHIVED veya MERGED kimlikler. Nedenler birbirinden ayırt edilmez. Library Service bu kimlikler için kendi anlık görüntüsünü kullanır (LIB-17).

#### Hata Durumları
- 400: Boş/51+ kimlik, null eleman, geçersiz UUID, bilinmeyen alan, bozuk gövde (ProblemDetail).
- 401: Geçersiz Bearer token gönderildi.
- 403: `Content-Type: application/json` değil (CSRF).
- 429: İstek sınırı aşıldı (`code: RATE_LIMITED`, `Retry-After` saniye cinsinden başlığıyla; ProblemDetail).
- 500: Beklenmeyen sunucu hatası.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path.
Diğer mikroservisler: RestClient ile doğrudan; sayfa başına tek çağrı. 429 alınırsa `Retry-After` süresi beklenmeli, otomatik sıkı döngüyle yeniden denenmemelidir. Sabit sayıda SQL (oyunlar + dış referanslar) çalışır; oyun başına sorgu yoktur. LTZ puan özeti için Review Service sözleşmesi yoktur ve bu uç puan alanı üretmez.

#### Test Durumu
INTEGRATION — sıra, tekrar, 50/51 sınırı, geçersiz gövdeler, arşiv/MERGED/olmayan kimlikler, tek kart sözleşmesiyle eşitlik, yönetim verisi sızıntısı olmaması, anonim erişim, CSRF atlamasının kapsamı (yalnız JSON, diğer tüm mutasyon yolları korumalı), salt-okunurluk, 429 istek sınırı (sahte başlıkla atlatılamaz) + birim testleri (jeton kovası, istemci adresi çözümleme).

### GAME — Herkese açık geliştirici içeriği (GAME-13)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/game/games/{gameId}/developer-content

#### Yetkilendirme
Public GET. Yalnız ACTIVE oyunlar; yalnız VISIBLE (moderasyonla gizlenmemiş, silinmemiş) içerik.

#### Path Parameters
- gameId: UUID, zorunlu

#### Response — 200
`contributor` her zaman `DEVELOPER`: içerik katalog/sağlayıcı verisi değil, oyunun yetkili geliştiricisi tarafından eklenmiştir ve yayın öncesi onaylanmamıştır. Yazar kimliği dönmez.
```json
{
  "gameId": "UUID",
  "contributor": "DEVELOPER",
  "description": { "text": "düz metin", "updatedAt": "UTC" },
  "media": [ { "id": "UUID", "type": "IMAGE|VIDEO|DEMO", "url": "https://…", "altText": "…|null", "createdAt": "UTC" } ],
  "updateNotes": [ { "id": "UUID", "title": "…", "body": "düz metin", "createdAt": "UTC", "updatedAt": "UTC" } ]
}
```
`description` içerik yoksa null; listeler boş olabilir. Metinler düz metindir, istemci kaçışlı göstermelidir (HTML işlenmez). En fazla 50 güncelleme notu, yeniden eskiye sıralı.

#### Hata Durumları
- 400: Geçersiz UUID.
- 404: Oyun bulunamadı veya ACTIVE değil.
- 500: Beklenmeyen sunucu hatası.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. Oyun sayfasında katalog verisinden ayrı bölüm olarak gösterilir.

#### Test Durumu
INTEGRATION — yalnız görünür içerik, gizlenen içeriğin çıkması/geri gelmesi, yazar kimliği sızıntısı olmaması, boş içerik, arşivlenmiş/olmayan oyun.

### GAME — Yönetici: oyun oluşturma (GAME-07)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli)

#### Endpoint
POST /api/v1/game/admin/games

#### Yetkilendirme
Admin. Auth sözleşmesine uygun Bearer JWT ve birebir `ADMIN` rolü zorunludur (GAME ortak Auth notuna bakınız). Aktör kimliği yalnız doğrulanmış `sub` UUID'sinden alınır. İmza kaynağı yokken token kabul edilmez; hatalı yapılandırma uygulama başlangıcını engeller.

#### Request Body
```json
{
  "title": "zorunlu, ≤256", "gameType": "zorunlu: MAIN_GAME|DLC|DEMO|SOUNDTRACK|SOFTWARE|VIDEO|PACKAGE|HARDWARE|UNKNOWN",
  "description": "≤16000", "releaseDate": "yyyy-MM-dd", "earlyAccess": true,
  "languages": ["tr", "en"], "developers": ["…"], "publishers": ["…"],
  "genres": ["action"], "categories": ["single-player"], "tags": ["indie"], "platforms": ["pc"],
  "coverUrl": "https://… (izinli görsel hostu)"
}
```
Köken (`INTERNAL`), durum (`ACTIVE`) ve `gameId` sunucuda belirlenir; istekte gönderilirse 400. Terim kodları `[a-z0-9][a-z0-9-]{0,63}`; yoksa sözlüğe eklenir. Diller küçük harf BCP47 benzeri kod.

#### Response — 201
AdminGameResponse: `gameId`, `status`, `version`, `updatedAt`, `game` (ortak detay yanıtı), `manualOverrides` (INTERNAL oyunda boş). `metadataCompleteness` yalnız var olan veriyi yansıtır (INDEX_ONLY/PARTIAL), FULL iddiası yoktur.

#### Hata Durumları
- 400: Doğrulama, bilinmeyen alan, geçersiz terim/dil/URL (`code`: `INVALID_TITLE`, `INVALID_MEDIA_URL` vb.).
- 401: Token yok/geçersiz. 403: Rol ADMIN değil.
- 500: Beklenmeyen sunucu hatası.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. Eylem `game_admin_audits` kaydına (append-only) yazılır.

#### Test Durumu
INTEGRATION — oluşturma, public katalogda görünme, istemci kontrollü alanların reddi, doğrulama, denetim kaydı.

GAME-23 — Yukarıdaki admin oluşturma isteğine isteğe bağlı `localization` eklenmiştir; eski istekler değişmez:
```json
{"metadataLanguage":"tr-TR","dataState":"AVAILABLE","languageSupport":[{"languageCode":"tr","interfaceSupport":"SUPPORTED","audioSupport":"UNKNOWN","subtitleSupport":"UNKNOWN"}],"verified":false,"evidence":"Dil bilgisi kaynak üzerinden kontrol edildi"}
```
`evidence` nesne varsa 8–1000 karakterdir. En çok 100 benzersiz normalize dil kodu; boş/dil etiketi olmayan metin reddedilir. Türkçe/Turkish/Turkce etiketleri `tr` olarak normalize edilir; HTML/asterisk metni destek yok diye yorumlanmaz, 400 döner. Eksik destek türü UNKNOWN olur. AVAILABLE için dolu ayrıntı listesi, diğer durumlar için boş liste gerekir. Eksik liste güncellemede mevcut listeyi korur; `[]` açıkça temizler. Dil kodu null ise mevcut değer kalır; boş metin metadata dilini temizler. `verified` opsiyoneldir; sadece `true` ile admin açık doğrulama beyanı yaparsa sunucu saatiyle `verifiedAt` kaydedilir. UNKNOWN/INVALID doğrulanmış sayılamaz. Sonraki sıradan düzenleme önceki doğrulama zamanını kaldırır. İstemci `source`/`verifiedAt`/operatör atayamaz. Bu alanlar role/sürüm/audit sınırlarını değiştirmez.
Test: INTEGRATION — `LocalizationMetadataTest`; eski istek, durum ayrımı, rol, sürüm, kilit/senkronizasyon, kaynak ve doğrulama testleri. Migration: `LocalizationMigrationTest` V40→V171 veri koruması.
### GAME — Yönetici: oyun görünümü (GAME-07)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli)

#### Endpoint
GET /api/v1/game/admin/games/{gameId}

#### Yetkilendirme
Admin (Auth sözleşmesine uygun Bearer JWT, rol `ADMIN`; ortak doğrulama kuralları için bkz. GAME Auth notu).

#### Response — 200
AdminGameResponse (ARCHIVED oyunlar dahil; MERGED da görülebilir). `version` PATCH için iyimser kilit sürümüdür; `manualOverrides` senkronizasyonun ezmediği alanlardır.

#### Hata Durumları
- 400: Geçersiz UUID. 401/403: Kimlik/rol. 404: Oyun yok.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path.

#### Test Durumu
INTEGRATION.

### GAME — Yönetici: oyun güncelleme ve manuel düzeltme (GAME-07)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli)

#### Endpoint
PATCH /api/v1/game/admin/games/{gameId}

#### Yetkilendirme
Admin (Bearer JWT, rol `ADMIN`).

#### Request Body
GAME-23: isteğe bağlı localization nesnesi oluşturma isteğiyle aynı kurallara tabidir. Gönderilmezse yeni ve eski dil verileri değişmez; LOCALIZATION manuel kilidi EXTERNAL oyunda yeniden kullanılır.

`version` zorunlu. Diğer alanlar oluşturmadakiyle aynıdır ve yalnız gönderilenler değişir: gönderilmeyen (null) alan değişmez; boş metin/boş dizi alanı açıkça temizler. Bu nedenle kısmi istek sağlayıcı alanlarını silmez. `releaseDate` ve `earlyAccess` bu fazda yalnız ayarlanır, null'a döndürülemez.

#### Response — 200
AdminGameResponse. Değişiklik yoksa sürüm ve denetim kaydı değişmez. Dönen `version` istekteki tüm yazmalardan sonraki nihai sürümdür.
**Manuel düzeltme koruması:** EXTERNAL oyunda `title`, `description`, `releaseDate`, `gameType`, `developers`, `publishers` alanlarından gerçekten değişenler `manualOverrides` içine kilitlenir; katalog aktarımı ve detay yenilemesi bu alanları ezmez. Kilit `manual-overrides/{field}/release` ile kaldırılır. INTERNAL oyunda kilit gerekmez.

#### Hata Durumları
- 400: Doğrulama / `version` yok. 401/403: Kimlik/rol. 404: Oyun yok.
- 409: `VERSION_CONFLICT` (eski sürüm), `GAME_MERGED`, `CONCURRENT_MODIFICATION`.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. Eylem ve değişen alan adları denetim kaydına yazılır (içerik metni yazılmaz).

#### Test Durumu
INTEGRATION — kısmi güncelleme, sürüm çakışması, kilitlenen alanların katalog aktarımı ve detay yenilemesinde korunması.

### GAME — Yönetici: oyunu arşivleme ve geri alma (GAME-07)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli); Library/Review (yan etki: kartlar `unavailable` döner)

#### Endpoint
POST /api/v1/game/admin/games/{gameId}/archive
POST /api/v1/game/admin/games/{gameId}/restore

#### Yetkilendirme
Admin (Bearer JWT, rol `ADMIN`).

#### Request Body
```json
{ "reason": "zorunlu, 8–1000 karakter" }
```

#### Response — 200
AdminGameResponse. Arşivleme görünürlüğü kapatır (public detay/kart/liste/batch'te görünmez, editör seçimi temizlenir); oyun ve alt verileri **fiziksel olarak silinmez**. Fiziksel silme uç noktası bilinçli olarak yoktur: Library/Review referansları başka servislerde olduğundan doğrulanamaz. İşlemler idempotenttir; tekrar çağrı denetim kaydı eklemez.

#### Hata Durumları
- 400: Gerekçe yok/kısa. 401/403. 404: Oyun yok. 409: `GAME_MERGED` (kimlik birleştirme işlemidir).

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. `ARCHIVE_GAME` / `RESTORE_GAME` denetim kaydı gerekçeyle yazılır.

#### Test Durumu
INTEGRATION — gizlenme, veri korunması, idempotency, geri alma, MERGED reddi, gerekçe doğrulaması.

### GAME — Yönetici: dış kimliği yeniden eşleme ve ayırma (GAME-26)

Durum: IMPLEMENTED — korumalı giriş ve güvenlik engeli; gerçek mutasyon entegrasyon bekler.
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli)

#### Endpoint
POST /api/v1/game/admin/identity/reassign
POST /api/v1/game/admin/identity/split

#### Yetkilendirme
Auth sözleşmesine uygun Bearer JWT; yalnız `ADMIN`. Ortak JWT/JWKS doğrulaması uygulanmıştır; gerçek kimlik mutasyonlarının dış referans güvenliği kısıtları devam eder.

#### Path/Query Parameters
Yok.

#### Request Body
```json
{
  "provider": "STEAM",
  "externalId": "12345",
  "expectedGameId": "11111111-1111-1111-1111-111111111111",
  "targetGameId": "22222222-2222-2222-2222-222222222222",
  "evidence": "Yönetici tarafından incelenen eşleme gerekçesi"
}
```
`targetGameId` yalnız reassign için zorunludur; split isteğinde gönderilmez.
Provider mevcut katalog kilidiyle uyumlu olarak STEAM; externalId pozitif uint32 kanonik ondalık kimliktir.
expectedGameId zorunlu mevcut eşlemedir. Gerekçe 8–1000 karakterdir. `force` gibi ek alanlar reddedilir.

#### Response — 200 / 409
Reassign yalnız mevcut=beklenen=hedef olduğunda 200 `{ "gameId": "..." }` döndürür;
no-op hiçbir veri veya başarı audit'i oluşturmaz. Gerçek reassign ve tüm geçerli split
istekleri dış referans güvenliği doğrulanamadığından 409 `FOREIGN_REFERENCE_SAFETY_UNVERIFIED`
döndürür. Operatör beyanı bu engeli aşmaz. Kanonik oyunlar, eski eşlemeler ve diğer
servislerin verileri değiştirilmez; mevcut transaction/kilit altyapısı kullanılır.

#### Hata Durumları
- 400: Eksik/geçersiz gövde, kimlik veya gerekçe.
- 401/403: Kimlik doğrulama/ADMIN rolü.
- 404: Dış referans veya aktif hedef oyun bulunamadı.
- 409: `REFERENCE_MAPPING_CONFLICT` (beklenen eşleme eski), `FOREIGN_REFERENCE_SAFETY_UNVERIFIED`.
Yanıtlar ProblemDetail biçimindedir.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. Library/Review DB erişimi yoktur.
Gerçek taşıma/ayırma, yeni referans yazma yarışlarını da kapsayan servisler arası
koordinasyon sözleşmesi bekler. Başarılı gerçek mutasyon audit'i mevcut iç serviste
operatör/gerekçe/zaman/önceki-sonraki kimliklerle tutulur; HTTP üzerinden henüz açılamaz.

#### Test Durumu
INTEGRATION — ADMIN/USER/developer/moderator/anonim, doğrulama, eski eşleme,
no-op, mutasyon engeli, veri ve audit korunması, gerçek PostgreSQL transaction rollback ve eşzamanlı kilit.

### GAME — Yönetici: manuel düzeltme kilidini kaldırma (GAME-07)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli)

#### Endpoint
POST /api/v1/game/admin/games/{gameId}/manual-overrides/{field}/release

#### Yetkilendirme
Admin (Bearer JWT, rol `ADMIN`).

#### Path Parameters
- gameId: UUID. field: `TITLE|DESCRIPTION|RELEASE_DATE|GAME_TYPE|DEVELOPERS|PUBLISHERS`.

#### Request Body
`{ "reason": "zorunlu, 8–1000 karakter" }`

#### Response — 200
AdminGameResponse. Sonraki senkronizasyon bu alanı sağlayıcı verisiyle yeniden yazabilir.

#### Hata Durumları
- 400: Geçersiz alan/gerekçe. 401/403. 404: Oyun veya kilit yok (`OVERRIDE_NOT_FOUND`).

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. `RELEASE_OVERRIDE` denetim kaydı.

#### Test Durumu
INTEGRATION.

### GAME — Yönetici: editör (popüler) listesi (GAME-20 yönetim girişi)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli)

#### Endpoint
PUT /api/v1/game/admin/popular

#### Yetkilendirme
Admin (Bearer JWT, rol `ADMIN`).

#### Request Body
```json
{ "gameIds": ["UUID, sıralı, ≤100, benzersiz"], "reason": "zorunlu, 8–1000 karakter" }
```
Boş liste mevcut seçimi temizler. Yalnız ACTIVE MAIN_GAME seçilebilir; tüm liste doğrulanır, hatalı komut önceki listeyi değiştirmez.

#### Response — 204

#### Hata Durumları
- 400: Geçersiz/yinelenen/olmayan/uygun olmayan oyun, gerekçe. 401/403.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. Seçen aktör kimliği ve gerekçe oyuna yazılır; `SET_EDITORIAL` denetim kaydı eklenir. Okuma: `GET /api/v1/game/games/popular`.

#### Test Durumu
INTEGRATION.

### GAME — Yönetici: geliştirici yetkisi (GAME-12)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli)

#### Endpoint
POST /api/v1/game/admin/games/{gameId}/developers
GET /api/v1/game/admin/games/{gameId}/developers
POST /api/v1/game/admin/games/{gameId}/developers/{developerUserId}/revoke

#### Yetkilendirme
Admin (Bearer JWT, rol `ADMIN`).

#### Request Body
Verme: `{ "developerUserId": "UUID", "reason": "8–1000" }`. Geri alma: `{ "reason": "8–1000" }`.

#### Response
Verme 200: `{ "grantId", "gameId", "developerUserId", "grantedAt" }`; aynı geliştirici/oyun için tekrar çağrı aynı aktif kaydı döndürür (idempotent, tek aktif kayıt DB kısıtıyla korunur). Listeleme 200: aktif yetkiler. Geri alma 204; yetki kaydı silinmez, `revoked_*` alanlarıyla kapatılır.
Bu kayıt yalnız **oyun bazlı yetkidir**. Kullanıcının `GAME_DEVELOPER` rolüne sahip olup olmadığı Auth Service'in konusudur; kullanım anında token rolü ve bu yetki ayrı ayrı denetlenir.

#### Hata Durumları
- 400: Doğrulama. 401/403. 404: Oyun yok veya (geri almada) aktif yetki yok (`GRANT_NOT_FOUND`).

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. `GRANT_DEVELOPER` / `REVOKE_DEVELOPER` denetim kayıtları.

#### Test Durumu
INTEGRATION + DB kısıtı testleri.

### GAME — Geliştirici: yönetilen oyunlar ve içerik görünümü (GAME-12, GAME-13)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (geliştirici alanı)

#### Endpoint
GET /api/v1/game/developer/games
GET /api/v1/game/developer/games/{gameId}/content

#### Yetkilendirme
Owner. Auth sözleşmesine uygun Bearer JWT; rol `GAME_DEVELOPER` **ve** o oyun için aktif geliştirici yetkisi gerekir (ikisi ayrı denetlenir). Yetkisiz oyun için 403 (IDOR); ARCHIVED oyun için 404. Ortak doğrulama kuralları için bkz. GAME Auth notu.

#### Response — 200
`/games`: `[ { "gameId", "title" } ]` (yalnız yetkili ve ACTIVE oyunlar). `/content`: açıklama (durum, sürüm, moderasyon gerekçesi), medya ve güncelleme notları; silinmiş kayıtlar hariç, HIDDEN kayıtlar durumuyla birlikte görünür.

#### Hata Durumları
- 401: Token yok/geçersiz. 403: Rol veya oyun yetkisi yok (`ACCESS_FORBIDDEN`). 404: Oyun ACTIVE değil.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path.

#### Test Durumu
INTEGRATION — rol var/yetki yok, yetki var/rol yok, başka oyun, geri alınmış yetki, arşivlenmiş oyun.
Geliştirici içerik yazmaları ve yetki iptali aynı oyun satırını transaction sonuna kadar kilitler.
READ COMMITTED altında kilit beklenirken commit edilen iptal, yazma öncesindeki ikinci yetki okumasında reddedilir (403).
`DeveloperRevocationRaceTest`, iptalin önce commit edildiği ve yazmanın kilidi önce aldığı iki sırayı gerçek PostgreSQL ile sınar.

### GAME — Geliştirici: açıklama (GAME-13)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (geliştirici alanı)

#### Endpoint
PUT /api/v1/game/developer/games/{gameId}/description

#### Yetkilendirme
Owner (rol `GAME_DEVELOPER` + oyun yetkisi).

#### Request Body
```json
{ "description": "düz metin, 1–8000", "version": "mevcut içerik varsa zorunlu (iyimser kilit)" }
```
Sunucu yalnız `description` ve `version` alanlarını kabul eder; yazar ve durum sunucuda belirlenir. Kontrol karakterleri reddedilir. İçerik katalog açıklamasından (`games.description`) ayrı saklanır.

#### Response — 200
`{ "text", "status", "version", "moderationReason", "updatedAt" }`

#### Hata Durumları
- 400: Doğrulama. 401/403/404.
- 409: `VERSION_CONFLICT`, `CONTENT_HIDDEN` (moderasyonla gizli içerik düzenlenemez), `CONCURRENT_MODIFICATION`.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path.

#### Test Durumu
INTEGRATION.

### GAME — Geliştirici: medya bağlantıları (GAME-13)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (geliştirici alanı)

#### Endpoint
POST /api/v1/game/developer/games/{gameId}/media
DELETE /api/v1/game/developer/games/{gameId}/media/{mediaId}

#### Yetkilendirme
Owner (rol `GAME_DEVELOPER` + oyun yetkisi).

#### Request Body
```json
{ "type": "IMAGE|VIDEO|DEMO", "url": "https://…", "altText": "≤200, isteğe bağlı" }
```
**Bağlantı politikası:** yalnız bağlantı saklanır; sunucu bu adreslere istek atmaz. Zorunlu: HTTPS, tam host eşleşmesi ve tür bazlı yol öneki allowlist'i, kullanıcı bilgisi/port/fragment yok, IP/alan adı taklidi yok, ASCII RFC 3986 karakterleri, ≤1024; yolda nokta segmenti, çift eğik çizgi, yüzde kodlaması ve noktalı virgül yok; görsellerde `.jpg/.jpeg/.png/.webp/.gif` uzantısı. Kaynaklar `application.properties` içinde `ltz.game.media.{image,video,demo}-sources` ile merkezi yönetilir (kayıt biçimi `host` veya `host/yol-öneki`). Varsayılanlar gerçek Steam sayfası ve örnek URL'lerle doğrulanmıştır: görsel `shared.akamai.steamstatic.com` ve `shared.fastly.steamstatic.com` (`/store_item_assets/steam/apps/`) ile `cdn.akamai.steamstatic.com` (`/steam/apps/`); video `www.youtube.com` (`/watch`, `/embed/`), `www.youtube-nocookie.com` (`/embed/`), `youtu.be`, `vimeo.com`, `player.vimeo.com` (`/video/`); demo `store.steampowered.com` (`/app/`). Dosya yükleme yoktur. Tür başına sınır: 10 görsel, 5 video, 3 demo.

#### Response
POST 201: `{ "id", "type", "url", "altText", "status", "moderationReason", "createdAt" }`. DELETE 204 (yumuşak silme).

#### Hata Durumları
- 400: `INVALID_MEDIA_URL` ve doğrulama. 401/403. 404: `MEDIA_NOT_FOUND`.
- 409: `MEDIA_LIMIT_REACHED`.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. Medya depolama kararı bekliyor; ayrı media servisi yoktur.

#### Test Durumu
UNIT + INTEGRATION — URL politikası tablo testleri (taklit host, userinfo, port, IP, şema, kaçış, uzunluk), tür/host eşleşmesi, sınır, silme.

### GAME — Geliştirici: güncelleme notları (GAME-13)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (geliştirici alanı)

#### Endpoint
POST /api/v1/game/developer/games/{gameId}/update-notes
PUT /api/v1/game/developer/games/{gameId}/update-notes/{noteId}
DELETE /api/v1/game/developer/games/{gameId}/update-notes/{noteId}

#### Yetkilendirme
Owner (rol `GAME_DEVELOPER` + oyun yetkisi).

#### Request Body
`{ "title": "1–120", "body": "düz metin, 1–4000", "version": "yalnız PUT, zorunlu" }`

#### Response
POST 201 / PUT 200: `{ "id", "title", "body", "status", "version", "moderationReason", "createdAt", "updatedAt" }`. DELETE 204 (yumuşak silme; gizlenmiş not da kaldırılabilir). Oyun başına en fazla 100 not.

#### Hata Durumları
- 400: Doğrulama / `VERSION_REQUIRED`. 401/403. 404: `NOTE_NOT_FOUND`.
- 409: `VERSION_CONFLICT`, `CONTENT_HIDDEN`, `NOTE_LIMIT_REACHED`.

#### Bağlantı
Frontend: API Gateway üzerinden aynı path.

#### Test Durumu
INTEGRATION.

### GAME — Moderasyon: geliştirici içeriğini gizleme/geri alma (GAME-13)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (moderasyon paneli)

#### Endpoint
POST /api/v1/game/moderation/games/{gameId}/developer-content/actions

#### Yetkilendirme
Admin veya Moderator (Bearer JWT; rol `ADMIN` ya da `MODERATOR`). Geliştirici bu uca erişemez.

#### Request Body
```json
{ "target": "DESCRIPTION|MEDIA|UPDATE_NOTE", "targetId": "MEDIA/UPDATE_NOTE için zorunlu, DESCRIPTION için yok", "action": "HIDE|RESTORE", "reason": "zorunlu, 8–1000" }
```

#### Response — 200
`{ "target", "targetId", "status": "VISIBLE|HIDDEN" }`. Gizlenen içerik public görünümden çıkar, sahibi düzenleyemez ama kaldırabilir ve gerekçeyi yönetim görünümünde görür. İşlem idempotenttir. Eylem `HIDE_/RESTORE_DEVELOPER_CONTENT` olarak, işlemi yapan rolle birlikte denetim kaydına yazılır. Bu politika yayın sonrası moderasyondur; yayın öncesi onay yoktur. Moderasyon bildirimi sahibine henüz gönderilmez (notification-service entegrasyonu yok).

#### Hata Durumları
- 400: Doğrulama / `INVALID_TARGET`. 401/403. 404: Oyun veya içerik yok (`CONTENT_NOT_FOUND`).

#### Bağlantı
Frontend: API Gateway üzerinden aynı path. Admin Service iş akışı bu faza dahil değildir.

#### Test Durumu
INTEGRATION — roller, hedef doğrulaması, çapraz oyun kimliği, idempotency, denetim rolü, sahibin düzenleme engeli, geri alma.

### GAME — Genişletilebilir modül okuma (GAME-11)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (oyun detayları)

#### Endpoint
GET /api/v1/game/games/{gameId}/modules/{type}

#### Yetkilendirme
Public. Yalnız aktif kanonik oyun ve görünür tanımlar.

#### Path/Query Parameters
gameId UUID; type ACHIEVEMENTS/GUIDES/EASTER_EGGS/EXPERIENCES.
page=0 (0–10000), size=20 (1–50); sıralama code ASC, id ASC.

#### Response — 200
`{gameId,type,state,items,page,size,totalElements}`. ACHIEVEMENTS için state
AVAILABLE/EMPTY; items `{id,code,title,description,visible,version,source,updatedAt}`.
source yalnız ADMIN; bu veri kişisel başarım ilerlemesi değildir. Diğer üç tip
INTEGRATION_PENDING, items=[], totalElements=null döner. Başarılı dış veri alımı değildir;
bu içeriklerin sahibi ve gerçek entegrasyon sözleşmesi beklenmektedir.

#### Hata Durumları
400 geçersiz tip/UUID/sayfalama; 404 olmayan veya aktif olmayan oyun.

#### Bağlantı
Gateway üzerinden aynı path. Dış servis çağrısı veya verisi yoktur.

#### Test Durumu
INTEGRATION — GameModuleTest; boş/bekleyen ayrımı, görünürlük, sıralama, sayfalama, eski metadata.

### GAME — ADMIN başarım tanımı yönetimi (GAME-11)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (yönetim paneli)

#### Endpoint
GET /api/v1/game/admin/games/{gameId}/achievements
PUT /api/v1/game/admin/games/{gameId}/achievements/{code}

#### Yetkilendirme
Admin — Auth sözleşmesine uygun Bearer JWT ve `ADMIN`; ortak JWT/JWKS doğrulaması için bkz. GAME Auth notu. Bu uçta canlı Auth oturumuyla işlem **NOT VERIFIED**; izole API güvenlik testleri mevcuttur.

#### Path/Query Parameters
gameId UUID; code küçük harf/rakam/tire, 1–64 karakter.
GET page=0 (0–10000), size=20 (1–50); code ASC, id ASC; gizli tanımlar dahil.

#### Request Body
PUT: `{title,description,visible,version,reason}`. title zorunlu 1–120, description
opsiyonel en çok 2000, visible zorunlu boolean, reason 8–1000 karakter. Düz metindir.
Yeni tanımda version gönderilmez; var olan tanımda mevcut version zorunlu.
Bir oyunda en çok 1000 tanım; gizlemek için visible=false kullanılır. Fiziksel silme yoktur.

#### Response — 200
GET modül sayfası; PUT yukarıdaki Achievement nesnesi. PUT aktif oyunda çalışır.
İşlem mevcut append-only admin audit'e UPDATE_GAME ve achievement ID'siyle yazılır;
başarım başlığı/açıklaması audit'e kopyalanmaz. Kaynak ADMIN'dir.

#### Hata Durumları
400 girdi; 401 anonim; 403 ADMIN dışı; 404 oyun yok/PUT için aktif değil;
409 VERSION_CONFLICT, ACHIEVEMENT_LIMIT_REACHED veya CONCURRENT_MODIFICATION.

#### Bağlantı
Gateway üzerinden aynı path. Oyun satır kilidi, unique(game_id,code), version ve transaction kullanılır.

#### Test Durumu
INTEGRATION — GameModuleTest; ADMIN/USER/developer/moderator/anonim, sürüm, kota ve audit.

### GAME — Oyuncunun özel geliştirici geri bildirimleri (GAME-14)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (oyuncu geri bildirim alanı)

#### Endpoint
POST /api/v1/game/feedback
GET /api/v1/game/feedback
GET /api/v1/game/feedback/{id}

#### Yetkilendirme
Authenticated/Owner — Auth sözleşmesine uygun Bearer JWT; gerçek `PLAYER` token'ı desteklenir.
Kimlik yalnız doğrulanmış `sub` üzerinden güvenlik bağlamından alınır.
GET yalnız yazarın kendi kayıtlarıdır; başka yazara ait id 404 verir.
ADMIN/MODERATOR rolü başka yazarların kayıtlarını açmaz. Gerçek Auth üreticisiyle
oluşturma/okuma ve yazar IDOR kontrolü izole PostgreSQL üzerinde testlidir.
Ayrıca gerçek kayıt/e-posta doğrulama/login sonrasında Gateway üzerinden iki PLAYER
oturumuyla oluşturma, okuma ve çapraz kullanıcı 404 kontrolü geçici HTTP ortamında **PASS**.

#### Path/Query Parameters
id UUID. Liste: opsiyonel gameId UUID, type GENERAL/BUG_REPORT/EARLY_ACCESS,
status OPEN/IN_PROGRESS/RESOLVED; page=0 (0–10000), size=20 (1–50).
Sıralama createdAt DESC, id ASC. Arşivlenen oyunda yazar eski kaydını okumaya devam edebilir.

#### Request Body
POST: `{gameId,type,title,body}`. gameId/type zorunlu; title 1–120, body 1–4000
karakter, boş/kontrol karakteri içeren metin reddedilir. Düz metin; HTML olarak render edilmez.
userId, authorId, status, version gibi istemci alanları kabul edilmez.
Oyun aktif olmalıdır. EARLY_ACCESS yalnız earlyAccess=true için kabul edilir;
false/null erken erişim sayılmaz. Genel kayıt Review incelemesi/puanı değildir.

#### Response — 201 / 200
POST 201, tek GET 200: `{id,gameId,type,status,title,body,version,createdAt,updatedAt}`.
İlk status OPEN. Yazar kimliği/profili yanıt içinde yoktur.
Liste 200: `{items,page,size,totalElements}`. Cache-Control: private, no-store.
Yazarın içerik düzenleme, durum değiştirme, yorumlaşma veya yeniden açma ucu yoktur.

#### Hata Durumları
400 geçersiz girdi/filtre/sayfa veya GAME_NOT_EARLY_ACCESS;
401 anonim/geçersiz JWT; 403 yetkisiz işlem; 404 kayıt/aktif oyun bulunamadı;
429 FEEDBACK_RATE_LIMITED + Retry-After (oluşturma).

#### Bağlantı
Gateway üzerinden aynı path. Başka servis DB/API çağrısı yoktur.
Kullanıcı başına kova: ani 5, sürekli 1/dk; IP başına ani 30, sürekli 30/dk.
Mevcut sınırlı bellekli token bucket ve güvenilir proxy/IP çözümleme yeniden kullanılır.
Sınır süreç içidir; dağıtık değildir. Gateway gerçek IP bağımlılığı nedeniyle Gateway
trafiği ortak IP kotası paylaşabilir; kullanıcı kotası JWT sub ile ayrıca korunur.
Gerçek Auth, bildirim ve servisler arası koordinasyon bu fazda tamamlanmış sayılmaz.

#### Test Durumu
INTEGRATION — GameFeedbackTest, GameFeedbackTransactionTest; JWT öznesi, IDOR,
gizlilik, tür, arşiv, filtre/sıralama, validation, rollback.
UNIT — FeedbackRateLimitTest; kullanıcı/IP kotaları, sahte forwarded header ve cache.

### GAME — Yetkili geliştiricinin özel geri bildirim yönetimi (GAME-14)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend (geliştirici alanı)

#### Endpoint
GET /api/v1/game/developer/games/{gameId}/feedback
GET /api/v1/game/developer/games/{gameId}/feedback/{id}
PATCH /api/v1/game/developer/games/{gameId}/feedback/{id}/status

#### Yetkilendirme
Bearer JWT GAME_DEVELOPER rolü VE ilgili oyunda aktif game_developer_grants yetkisi.
ADMIN/MODERATOR rolü tek başına yeterli değildir. Kimlik istemciden alınmaz.

#### Path/Query Parameters
gameId/id UUID; başka oyunun feedback kimliği 404.
Liste type/status/page/size ve sıralaması oyuncu listesiyle aynıdır; gameId path'ten gelir.

#### Request Body
PATCH: `{status,version}`; ikisi zorunlu, version >= 0. Yalnız OPEN -> IN_PROGRESS
ve IN_PROGRESS -> RESOLVED geçişleri vardır. Atlama, tekrarlı durum ve yeniden açma reddedilir.

#### Response — 200
Tek kayıt/listede yukarıdaki özel FeedbackResponse/FeedbackPageResponse;
Cache-Control private, no-store. Yazar UUID/profili ve audit gerekçesi dönmez.
Durum değişikliği mevcut admin audit'e GAME_DEVELOPER, UPDATE_GAME, feedback ID,
önceki/sonraki durum ve sunucu zamanı ile yazılır. Başlık/gövde audit'e kopyalanmaz.

#### Hata Durumları
400 validation/filtre; 401 JWT yok/geçersiz; 403 rol veya aktif oyun grant'i yok;
404 aktif oyun veya bu oyuna ait kayıt yok; 409 VERSION_CONFLICT,
INVALID_FEEDBACK_TRANSITION veya CONCURRENT_MODIFICATION. ProblemDetail kullanılır.

#### Bağlantı
Gateway üzerinden aynı path. Mevcut GameAccessPolicy kullanılır. Durum yazması oyun
kilidi altında grant'i tekrar denetler; geri bildirim version ve transaction ile korunur.
Audit başarısızsa durum değişikliği de geri alınır. Yeni mesajlaşma/dosya/bildirim sistemi yoktur.

#### Test Durumu
INTEGRATION — GameFeedbackTest, GameFeedbackTransactionTest; roller/grant,
çapraz oyun IDOR, durum/sürüm, iptal yarışı, eşzamanlı yazma ve audit rollback.

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
