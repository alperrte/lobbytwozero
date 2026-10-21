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
  "code": "string, zorunlu, ^[0-9]{6}$ (başında 0 olabilir)"
}
```

#### Response — 204
Gövde yok. Hesap `ACTIVE` olur, `email_verified_at` yazılır, kod tek kullanımlık olarak tüketilir. Bu uç token veya oturum üretmez.

#### Hata Durumları
- 400: Kod geçersiz, süresi dolmuş, kullanılmış, deneme hakkı bitmiş veya e-posta bilinmiyor/zaten doğrulanmış (`VERIFICATION_FAILED`, tüm nedenler için aynı yanıt); doğrulama hatası (`VALIDATION_FAILED`)
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
- POST /api/v1/auth/oauth/steam/complete — Steam ile yeni hesap: `{ "registrationToken", "email", "username" }` → `202 { "nextStep": "VERIFY_EMAIL" }`

#### Yetkilendirme
Public. Callback'ler, start'ın yazdığı HttpOnly `__Host-ltz_oauth` state cookie'si ile eşleşen, tek kullanımlık `state` ister (login CSRF koruması). `complete` yalnız tek kullanımlık kayıt token'ına dayanır (cookie/CSRF gerekmez).

#### Sonuç iletimi
Start ve callback `302` döner. Callback sonucu yapılandırmadaki SABİT frontend adresine (`FRONTEND_PUBLIC_URL/auth/callback`) URL **fragment**'i ile iletilir; istemciden yönlendirme adresi alınmaz.
- `#status=authenticated` — oturum cookie'leri yazıldı
- `#status=mfa_required&challenge=<id>` — TOTP etkin; `POST /login/2fa` ile tamamlanır, cookie yazılmaz
- `#status=registration_required&token=<t>` — yalnız Steam, bağsız SteamID; `steam/complete` çağrılır
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

### AUTH — Giden sözleşme taslakları (PLANNED, onay bekliyor)

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
