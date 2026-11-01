# LTZ — API Kataloğu

> Son güncelleme: 2026-11-01
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
  "password": "string, zorunlu, 15-128 karakter; içeriği dönüştürülmez; yaygın/tahmin edilebilir parola reddedilir (WEAK_PASSWORD)"
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
- 400: Doğrulama hatası (`VALIDATION_FAILED`), zayıf parola (`WEAK_PASSWORD`: yaygın parola listesi, tekrar/ardışık desen, kullanıcı adı veya e-posta yerel kısmını içerme, servis adı) veya okunamayan/bilinmeyen alan içeren gövde (`MALFORMED_REQUEST`)
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
- Production: `ltz.auth.mail.adapter=mail-service` (`HttpMailServiceAdapter`, hesap silme ayarlarındaki `mail` adresi) — **IMPLEMENTED** (2026-10-31, Mailpit ile uçtan uca doğrulandı, 2026-10-31). `prod` profili bu adaptör olmadan açılışta bilerek başlamaz (fail-fast).
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
Mail Service (MAIL-01) — production `mail-service` adaptörü **IMPLEMENTED** (Mailpit ile uçtan uca doğrulandı, 2026-10-31); development varsayılanı Mailpit (bkz. Kayıt ol → Bağlantı).

#### Test Durumu
UNIT + INTEGRATION

### AUTH — Oturum ortak kuralları (Faz 2)

- Token'lar yalnız HttpOnly cookie'dedir, gövdede dönmez: `__Host-ltz_access` (Secure, SameSite=Lax, Path=/), `__Secure-ltz_refresh` (Secure, SameSite=Strict, Path=/api/v1/auth/session). Production'da Secure kapatılamaz.
- **CSRF:** Aşağıdaki POST uçları `X-XSRF-TOKEN` başlığı ister; değer `__Host-XSRF-TOKEN` cookie'sinden okunur (`GET /api/v1/auth/csrf` ile üretilir). `Authorization: Bearer` başlığı taşıyan istekler (tarayıcı dışı istemci) muaftır. Eksik/yanlış token: `403 CSRF_TOKEN_INVALID`.
- **Access token (JWT, RS256):** `iss`, `sub` (hesap UUID), `aud=ltz-api`, `iat`, `nbf`, `exp` (varsayılan 60 sn; `JWT_ACCESS_TTL_SECONDS`, 1–15 dk), `jti`, `sid` (oturum), `ver` (token sürümü), `roles`, `amr`; başlık `typ=at+jwt`, `kid`. Diğer servisler imzayı JWKS ile doğrular. Auth uçlarında oturum/hesap durumu her istekte denetlenir (anında iptal); diğer servislerde iptal gecikmesi D3 kararına bağlıdır.
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

### AUTH — Steam bağlama ve kaldırma (oturumdaki hesap)

Durum: IMPLEMENTED (gerçek Steam ile uçtan uca doğrulanmadı; yerel sahte sağlayıcı ve duman testi)
Sorumlu: Kerem
Tüketici servisler: Frontend (tarayıcı gezinmesi), User Service (olay)

#### Endpoint
- POST /api/v1/auth/oauth/steam/link/start — yeniden doğrulayıp bağlama akışını başlatır → `200 { "redirectUrl": "<Steam OpenID adresi>" }`, `Cache-Control: no-store`; tek kullanımlık `state` HttpOnly `__Host-ltz_oauth` çerezine yazılır. Tarayıcı `redirectUrl`'e gönderilir.
- GET /api/v1/auth/oauth/steam/link/callback?state&openid.* — Steam dönüşü (tarayıcı gezinmesi)
- POST /api/v1/auth/oauth/steam/unlink — bağlantıyı kaldırır → `204`

#### Yetkilendirme
`start` ve `unlink`: oturum (çerez + CSRF veya Bearer) **ve yeniden doğrulama**: `{ "password", "code"?, "recoveryCode"? }`. Mevcut parola zorunlu; TOTP etkinse `code` veya `recoveryCode` (yalnız biri). Cihaz bağlama ve e-posta değiştirmeyle aynı kural; denemeler hesap başına sınırlıdır. `callback` public: hesap oturumdan değil, `start`'ın yazdığı hesaba bağlı akıştan gelir; `state` çerezle eşleşmeli ve tek kullanımlık olmalıdır (başkasının başlattığı akış kurbanın tarayıcısında tamamlanamaz). Bir giriş akışının `state`'i bağlamada, bağlama `state`'i girişte geçmez.

#### Sonuç iletimi (callback)
Yapılandırmadaki SABİT frontend adresine URL **fragment**'i ile (`302`):
- `#status=steam_linked` — Steam kimliği hesaba bağlandı (aynı kimlik zaten bu hesaptaysa da bu sonuç, olay üretilmez)
- `#error=<KOD>` — `EXTERNAL_AUTH_FAILED` (state/imza/nonce/replay/Steam hatası; ayrıntı verilmez), `EXTERNAL_IDENTITY_TAKEN` (bu Steam kimliği başka hesapta), `EXTERNAL_IDENTITY_ALREADY_LINKED` (hesapta zaten başka bir Steam kimliği var), `SESSION_INVALID` (hesap aktif değil)

#### Kurallar
- **Tekillik:** bir SteamID64 tek hesaba, bir hesap tek Steam kimliğine bağlanır (veritabanı kısıtlarıyla da zorlanır).
- **Son giriş yöntemi koruması:** hesapta parola yoksa ve başka harici kimlik (Google) yoksa kaldırma `409 LAST_LOGIN_METHOD`.
- **Parolasız hesap** bağlama ve kaldırmada `409 PASSWORD_REQUIRED` alır (cihaz bağlama ve e-posta değiştirmeyle aynı davranış; açık B11 kararına bağlı).
- Bağlama ve kaldırma güvenlik olayı olarak kaydedilir (`EXTERNAL_IDENTITY_LINKED`, `EXTERNAL_IDENTITY_UNLINKED`, `EXTERNAL_LINK_CONFLICT`, `EXTERNAL_IDENTITY_LINK_REJECTED`).
- **Olaylar** (outbox, `AUTH_EVENT_TARGETS`, hedef User): `ExternalIdentityLinked { accountId, provider: "STEAM", providerSubject: "<17 haneli SteamID64>" }` ve `ExternalIdentityUnlinked { accountId, provider: "STEAM" }`, `eventVersion=1`. SteamID64 herkese açık bir kimliktir; olaylara konabilen tek kimlik bilgisi odur (e-posta ve parola konmaz). Zarf ve teslim kuralları `docs-auth/servis-sozlesmeleri.md` 3.5.
- **Mevcut Steam hesapları** için geriye dönük `ExternalIdentityLinked` yayını yoktur (DEV'de hesaplar yeniden kaydolur; gerçek hesap varsa ayrıca konuşulur).

#### Hata Durumları
- 400: yeniden doğrulama hatası (`CURRENT_PASSWORD_INVALID`, `INVALID_TWO_FACTOR_CODE`), doğrulama hatası
- 401/403: oturum yok / CSRF yok
- 404: `unlink` için bağlı Steam kimliği yok (`EXTERNAL_IDENTITY_NOT_LINKED`)
- 409: `PASSWORD_REQUIRED`, `EXTERNAL_IDENTITY_ALREADY_LINKED` (`start`), `LAST_LOGIN_METHOD` (`unlink`)
- 429: yeniden doğrulama denemesi sınırı

#### Bağlantı
Frontend:
Tarayıcı bağlantısı ile API Gateway üzerinden /api/v1/auth/oauth/** ; `start` bir `fetch` ile çağrılır, dönen `redirectUrl`'e `window.location` ile gidilir.

Diğer mikroservisler:
User Service: `POST /internal/events` ile `ExternalIdentityLinked/Unlinked` alır (`aud=user-service`, `scope=events:ingest`, `sub=svc:auth-service`).

#### Test Durumu
UNIT + INTEGRATION (`SteamLinkTest`, `AuthEventOutboxTest`; sahte sağlayıcı) + duman testi (start, yanlış parola, bağsız kaldırma); gerçek Steam ile denenmedi

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
  "newPassword": "string, zorunlu, 15-128 karakter; içeriği dönüştürülmez/kırpılmaz; yaygın/tahmin edilebilir parola reddedilir (WEAK_PASSWORD)",
  "newPasswordConfirmation": "string, zorunlu, newPassword ile birebir aynı"
}
```

#### Response — 204
Gövde yok. Hesabın **tüm** oturumları (bu cihaz dahil) iptal edilir, token sürümü artar (eski access token'lar düşer), bekleyen sıfırlama token'ları geçersiz olur, cookie'ler silinir; yeniden giriş gerekir. 2FA etkilenmez.

#### Hata Durumları
- 400: Doğrulama hatası (`VALIDATION_FAILED`; tekrar uyuşmazlığı alanı `newPasswordConfirmed`), mevcut parola yanlış (`CURRENT_PASSWORD_INVALID`; parolasız harici hesap dahil), yeni parola mevcutla aynı (`NEW_PASSWORD_SAME_AS_CURRENT`), zayıf yeni parola (`WEAK_PASSWORD`)
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

### AUTH — E-posta değiştir

Durum: IMPLEMENTED (development'ta Mailpit; production e-posta teslimi PENDING)
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
- POST /api/v1/auth/email/change — yeniden doğrulama + yeni adres; yanıt `202 { "nextStep": "VERIFY_EMAIL" }`
- POST /api/v1/auth/email/change/confirm — yeni adrese gelen kod; yanıt `204`

#### Yetkilendirme
Authenticated (+ CSRF). Hesap kimliği yalnız token'dan alınır.

#### Request Body (`change`)
```json
{
  "newEmail": "string, zorunlu, geçerli e-posta, en fazla 254 karakter",
  "password": "string, zorunlu (mevcut parola)",
  "code": "string ^[0-9]{6}$; TOTP etkinse code veya recoveryCode zorunlu (yalnız biri)",
  "recoveryCode": "string"
}
```

#### Request Body (`confirm`)
```json
{ "code": "string, zorunlu, ^[0-9]{6}$ (yeni adrese gelen kod)" }
```

#### Davranış
- `change`: parola (+ TOTP) doğrulanırsa **yeni** adrese 6 haneli, tek kullanımlık kod gider (kayıttaki doğrulama koduyla aynı TTL, deneme sınırı ve bekleme/günlük limit kuralları). Hesap adresi bu aşamada değişmez; yeni istek eski kodu geçersiz kılar. Yeni adres başka hesapta varsa (veya mevcut adresle aynıysa) yanıt aynıdır, kod gönderilmez (e-posta numaralandırması sızmaz).
- `confirm`: kod doğruysa adres değişir ve doğrulanmış sayılır; hesabın **tüm** oturumları iptal edilir, token sürümü artar, eski adrese gitmiş bekleyen parola sıfırlama bağlantıları geçersiz olur, cookie'ler silinir; yeni adresle yeniden giriş gerekir. Kod tek kullanımlıktır.
- Parolasız (yalnız Google/Steam) hesap bu uçtan adres değiştiremez (`PASSWORD_REQUIRED`).
- Yeni adres yalnız bekleyen kod satırında tutulur; süresi dolan kod (ve adres) temizlik işiyle, hesap silinirken silme akışıyla silinir.
- **Açık:** eski adrese "e-posta adresiniz değişti" bildirimi gönderilmez (Mail Service bildirim sözleşmesi yok; B12 ile birlikte PENDING).

#### Hata Durumları
- 400: Doğrulama; yanlış parola (`CURRENT_PASSWORD_INVALID`); yanlış/eksik ikinci faktör (`INVALID_TWO_FACTOR_CODE`); kod yanlış/süresi dolmuş/deneme hakkı bitmiş veya adres bu arada başkasına geçmiş (`EMAIL_CHANGE_INVALID`, tek yanıt)
- 401: Kimliksiz / oturum geçersiz
- 403: CSRF
- 409: Hesapta parola yok (`PASSWORD_REQUIRED`)
- 429: Yeniden doğrulama denemesi hesap bazında sınırlı (`TOO_MANY_ATTEMPTS`)

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/email/**

Diğer mikroservisler:
Mail Service (`VerificationCodeMailPort`, alıcı = yeni adres; amaç alanı port sözleşmesinde yok) — production `mail-service` adaptörü **IMPLEMENTED** (Mailpit ile uçtan uca doğrulandı, 2026-10-31), development'ta Mailpit. **User Service:** adres değişikliği için olay/çağrı yok (`UserRegistered` gibi Auth→User olayları PLANNED); profil servisi e-posta tutuyorsa ayrıca senkronize edilmelidir.

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
- 400: Doğrulama hatası; `PASSWORD_RESET_TOKEN_INVALID` (geçersiz, süresi dolmuş, kullanılmış, hesap aktif değil — tek yanıt); `WEAK_PASSWORD` (token tüketilmez, aynı bağlantıyla güçlü parola denenebilir)
- 429: `forgot` istek sınırı (`Retry-After`)

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/password/**

Diğer mikroservisler:
Mail Service (MAIL-02) — production `mail-service` adaptörü **IMPLEMENTED** (Mailpit ile uçtan uca doğrulandı, 2026-10-31); development'ta Mailpit (`PasswordResetMailPort`).

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
- **Durum geçişleri:** `ACTIVE → SUSPENDED|BANNED|DISABLED`, `SUSPENDED → ACTIVE|BANNED`, `DISABLED → ACTIVE`, `BANNED → ACTIVE` (yasak yönetici kararıyla kaldırılabilir; gerekçe ve denetim kaydı zorunlu). `PENDING_VERIFICATION` (AUTH-02) ve `DELETION_REQUESTED/DELETED` (AUTH-16) bu uçtan yönetilmez. Süreli askı henüz yok.
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
- POST /api/v1/auth/account/deletion/cancel — bekleme süresi içinde vazgeçme (aşağıda); yanıt `200 { "nextStep": "DELETION_CANCELLED" }`

#### Yetkilendirme
`request` ve `confirm`: Authenticated (+ CSRF). Hesap ve oturum yalnız token'dan alınır; e-posta token'ı yalnız isteği yapan hesap için geçerlidir.
`cancel`: Public (cookie okumaz, CSRF gerekmez, oturum açmaz); kimlik gövdedeki parolayla doğrulanır.

#### Request Body (`cancel`)
```json
{
  "identifier": "string, zorunlu, e-posta veya kullanıcı adı",
  "password": "string, zorunlu",
  "code": "string ^[0-9]{6}$; TOTP etkinse code veya recoveryCode zorunlu (yalnız biri)",
  "recoveryCode": "string"
}
```
Parolasız (yalnız Google/Steam) hesap bu uçla iptal edemez (yönetici müdahalesi gerekir). Başarıda oturum açılmaz; kullanıcı normal girişle devam eder.

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
2. **Onay:** token + kullanıcı adının yazılması (açık onay). Hesap `DELETION_REQUESTED` olur, tüm oturumlar iptal edilir, cookie'ler silinir, hesap başına **tek** silme talebi ve her veri sahibi servis için bir silme görevi açılır (idempotent). Talep **14 günlük geri alma (bekleme) süresiyle** zamanlanır (`ltz.auth.deletion.grace-period`, `scheduled_for`); görevler bu süre bitene kadar çalışmaz. Bu sürede giriş `403 ACCOUNT_DELETION_PENDING` döner (parola doğruysa; hesap kapalı).
2a. **İptal:** süre dolmadan `cancel` doğru parolayla (+ TOTP) çağrılırsa hesap `ACTIVE` olur, görevler silinir, talep `CANCELLED` olur; tüm eski token'lar geçersiz kalır. Süre dolduktan sonra `409 DELETION_CANCEL_WINDOW_CLOSED`. İptal sonrası yeniden silme isteği aynı talebi yeni bekleme süresiyle yeniden açar.
2b. **Bekleme bitince:** vadesi gelen ilk görevle birlikte Auth PII'si (e-posta, kullanıcı adı, parola özeti, harici kimlikler, TOTP, kurtarma kodları, oturumlar) **hemen** silinir/anonimleştirilir; servislerin onayı gecikse bile Auth'ta kişisel veri kalmaz. Hesap kimliği ve durum (`DELETION_REQUESTED`) kalır; "silindi" yalnız tüm servisler onaylayınca denir.
3. **Koordinasyon (arka plan):** Auth her servise `DELETE <SERVICE>_SERVICE_URL/internal/v1/accounts/{accountId}` çağrısı yapar (idempotent; her çağrıda yeni D4 servis token'ı; yalnız `200`/`204` = silindi, `202`/`3xx`/`404`/`401`/`403`/`5xx`/zaman aşımı onay değildir). Başarısız görev üstel geri çekilmeyle (1 dk → en fazla 6 sa) yeniden denenir; onaylanan görev tekrarlanmaz. **Tüm** servisler onaylayınca Auth'a ait kişisel veri silinir ve hesap anonimleştirilip `DELETED` olur.
4. **Saklananlar (KVKK/denetim):** güvenlik olayları (1 yıl), durum ve rol geçmişi, silme talebi/görev kayıtları, anonimleştirilmiş hesap iskeleti (kimlik). **Silinenler:** e-posta, kullanıcı adı, parola özeti, harici kimlikler, TOTP sırrı, kurtarma kodları, oturumlar, tüm token'lar.

#### Hata Durumları
- 400: Doğrulama; yanlış parola (`CURRENT_PASSWORD_INVALID`); yanlış/eksik ikinci faktör (`INVALID_TWO_FACTOR_CODE`); geçersiz/süresi dolmuş token veya yanlış kullanıcı adı (`DELETION_CONFIRMATION_INVALID`)
- 401: Kimliksiz / oturum geçersiz (onaydan sonra eski oturum dahil)
- 403: `REAUTHENTICATION_REQUIRED`; CSRF
- 409: Yönetici hesabı silinemez (`ADMIN_DELETION_FORBIDDEN`; önce ADMIN rolü kaldırılmalı); `cancel`: iptal edilecek silme yok (`DELETION_NOT_PENDING`) veya bekleme süresi bitti (`DELETION_CANCEL_WINDOW_CLOSED`)
- 401: `cancel` için yanlış/bilinmeyen kimlik tek yanıt (`INVALID_CREDENTIALS`)
- 429: Yeniden doğrulama / iptal denemesi hesap bazında sınırlı (`TOO_MANY_ATTEMPTS`)

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/auth/account/deletion/**

Diğer mikroservisler:
Mail Service (`AccountDeletionMailPort`) — production `mail-service` adaptörü **IMPLEMENTED** (Mailpit ile uçtan uca doğrulandı, 2026-10-31), development'ta Mailpit. Veri sahibi 16 servis (`DELETE /internal/v1/accounts/{accountId}`, bkz. aşağıdaki sözleşme ve `docs-auth/servis-sozlesmeleri.md` §3–§3.2) — Auth tarafı (D4 servis token'ı + HTTP adaptörü) **IMPLEMENTED**; hedef uçlar servis sahiplerinde **PENDING**.

#### Test Durumu
INTEGRATION

### AUTH — Token iptali ve hesap durumu yayılımı (politika)

Durum: IMPLEMENTED (politika; yeni uç yoktur)
Sorumlu: Kerem
Tüketici servisler: kullanıcı token'ı doğrulayan tüm servisler (hardware-service dahil)

#### Endpoint
Yeni uç yoktur. Bilinçli karar: tüketicilere iptal sorgusu (introspection) ya da iptal listesi (revocation list) sunulmaz; iptal, kısa token ömrüyle yayılır.

#### Politika
- Access token ömrü **60 sn** (`JWT_ACCESS_TTL_SECONDS`, prod'da da 60; değişirse tüketicilere önceden haber verilir). Servis token'ı JWKS ile yerel doğrular, Auth'a çağrı yapmaz.
- Alıcı servis saat sapması için en çok **30 sn** tolerans uygular. Dolayısıyla iptal edilen bir hesabın eski access token'ı diğer servislerde **en çok 90 sn** (60 sn + 30 sn) geçerli görünür. Yeni token alınamaz, çünkü oturum iptal edilmiştir.
- Şu olaylar hesabın tüm oturumlarını iptal eder ve token sürümünü artırır: çıkış, parola değişimi ve sıfırlama, e-posta değişimi, rol verme ve alma, hesabın askıya alınması, yasaklanması ya da devre dışı bırakılması, silme onayı. **Auth'un kendi uçları** eski token'ı anında reddeder (`401`).
- Anlık iptal iddiası yoktur. 90 sn kritik bir işlem için yetmiyorsa ayrı bir sözleşme (sorgu ya da iptal listesi) gerekir; şu an böyle bir ihtiyaç kaydı yoktur.
- Auth'a ulaşılamazsa: servis önbellekteki JWKS ile doğrulamaya devam eder, bilinmeyen `kid` reddedilir (fail-closed). Hesap durumunu öğrenmek için Auth'a çağrı yapılmaz, yeni bir polling ya da önbellek kurulmaz.
- **İsteğe bağlı olay:** `AccountStatusChanged` yalnız yönetici durum değişikliğinde (askıya alma, yasaklama, devre dışı bırakma, yeniden açma) transactional outbox ile hedef servise iletilir. Çıkış, parola, rol ve silme olay üretmez. Hedef listesi (`ltz.auth.events.targets`) varsayılan boştur; alıcı uç yazan servis eklenmesini ister. Zarf alanları ve alıcı doğrulama kuralları: `docs-auth/servis-sozlesmeleri.md` bölüm 3.5. Olay gecikmeyi kısaltabilir ama zorunlu değildir; olay yoksa 90 sn kuralı geçerlidir.

#### Hata Durumları
Yeni uç olmadığı için yeni hata kodu yoktur. İptal edilmiş token Auth'un uçlarında `401 UNAUTHENTICATED` alır.

#### Bağlantı
Frontend: oturum yenileme `401` sonrası `session/refresh` ile yapılır (bkz. "Oturum uçları").

Diğer mikroservisler: token doğrulama kuralları `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` ve `docs-auth/entegrasyon/<servis>.md` içindedir. İstek geçmişi: `docs-auth/verilen-cevaplar/hardware-service/talep-01-token-iptali-ve-hesap-durumu.md`.

#### Test Durumu
INTEGRATION (Auth tarafı: oturum iptali, token sürümü ve eski token'ın Auth uçlarında reddi). Diğer servislerdeki yayılım süresi tüketicinin kendi testleriyle kanıtlanır.

### AUTH — Masaüstü cihaz kimliği (hardware-service)

Durum: IMPLEMENTED (Auth tarafı); tüketici hardware-service ve masaüstü uygulaması **PENDING**
Sorumlu: Kerem
Tüketici servisler: hardware-service (cihaz token'ını doğrular), masaüstü uygulaması (bağlama/token)

#### Endpoint
- POST /api/v1/auth/devices — cihazı hesaba bağlar (kullanıcı oturumu + CSRF)
- GET /api/v1/auth/devices — hesabın etkin cihazları
- DELETE /api/v1/auth/devices/{deviceId} — cihazı iptal eder (204)
- POST /api/v1/auth/devices/token — cihaz anahtarıyla kısa ömürlü cihaz token'ı alır

#### Yetkilendirme
Bağlama, listeleme, iptal: kullanıcı oturumu (cookie + CSRF veya Bearer). `devices/token`: Public, çerezsiz; kimlik yalnız gövdedeki `deviceId` + `deviceSecret`.

#### Request Body (`bind`)
```json
{ "label": "1-64 karakter; harf, rakam, boşluk ve . _ ( ) -", "password": "zorunlu (yeniden doğrulama)", "code": "TOTP etkinse ^[0-9]{6}$", "recoveryCode": "code yerine, yalnız biri" }
```
Response — 201, `Cache-Control: no-store`: `{ "deviceId", "deviceSecret", "label", "createdAt", "expiresAt" }`. `deviceSecret` (256 bit) **yalnız bu yanıtta bir kez** görünür; Auth yalnız SHA-256 özetini saklar, sonradan alınamaz.

#### Request / Response (`token`)
```json
{ "deviceId": "UUID", "deviceSecret": "string", "target": "isteğe bağlı: hardware (varsayılan) | performance" }
```
200, `no-store`: `{ "accessToken", "tokenType": "Bearer", "expiresIn": 60, "scope": "<hedefin kapsamı>" }`. Token: `typ=dev+jwt`, RS256 (JWKS aynı), `iss=ltz-auth`, `sub=<deviceId>`, `account_id`, `jti`, ömür 60 sn; **tek `aud` ve o hedefin tek kapsamı**:

| `target` | `aud` | `scope` |
| --- | --- | --- |
| `hardware` (yoksa bu) | `hardware-service` | `hardware:sync:write` |
| `performance` | `performance-service` | `performance:sync:write` |

Bir hedef için alınan token başka hedefte geçmez; istemci her servis için ayrı token alır. Tanımsız `target` `400`. Kimlik doğrulama ve iptal kuralları hedeften bağımsızdır (iptal edilmiş cihaz hiçbir hedef için token alamaz; hata yanıtı hedefi sızdırmaz). Kullanıcı rolü/oturumu taşımaz; Auth'un uçlarında ve başka servislerde geçmez.

#### Davranış
- Hesap başına en fazla 5 etkin cihaz, günde en fazla 10 bağlama; cihaz anahtarı 365 gün geçerlidir.
- Cihaz şu durumlarda otomatik iptal edilir: parola değişimi/sıfırlama, hesabın ACTIVE dışına geçmesi, silme onayı, e-posta değişimi.
- `token` uçta kimlik hatalarının hepsi tek yanıt verir (`INVALID_DEVICE_CREDENTIALS`; yanlış anahtar, bilinmeyen/iptal/süresi dolmuş cihaz, pasif hesap); zamanlama farkı yoktur. Hesap kilidi yoktur (iç servis kilitlenmesin); IP sınırı Gateway'dedir.
- Cihaz yeniden bağlanmaz, iptal edilip yeni cihaz bağlanır.

#### Hata Durumları
- 400: Doğrulama (geçersiz ad, birden fazla ikinci faktör); `CURRENT_PASSWORD_INVALID`; `INVALID_TWO_FACTOR_CODE`
- 401: Kimliksiz; `token` için `INVALID_DEVICE_CREDENTIALS`
- 403: CSRF
- 404: `DEVICE_NOT_FOUND` (başkasının cihazı da 404)
- 409: `DEVICE_LIMIT_REACHED`; `PASSWORD_REQUIRED` (parolasız hesap, yani yalnız Google/Steam, cihaz bağlayamaz)
- 429: Günlük bağlama sınırı (`TOO_MANY_ATTEMPTS`)

#### Bağlantı
Frontend: Gateway üzerinden `/api/v1/auth/devices/**` (cihaz yönetim ekranı).
Diğer mikroservisler: hardware-service tokenı JWKS ile doğrular (`typ=dev+jwt`, `aud=hardware-service`, `scope`). Ayrıntı: `docs-auth/entegrasyon/hardware-service.md`.

#### Test Durumu
UNIT + INTEGRATION

### AUTH — Servis kimlikli iç uçlar (Admin → Auth, B4)

Durum: IMPLEMENTED (Auth tarafı, altyapı); **tüketici admin-service henüz yok** → uçtan uca PENDING
Sorumlu: Kerem
Tüketici servisler: admin-service (şimdilik başka tüketici yok)

#### Endpoint
- POST /internal/v1/service-tokens — servis kimlik bilgisiyle (client credentials) Auth'tan kısa ömürlü `svc+jwt` alır
- GET /internal/v1/accounts/{accountId} — hesap özeti (kapsam `account:read`)
- POST /internal/v1/accounts/{accountId}/status — durum değişikliği (kapsam `account:status:write`)
- POST /internal/v1/accounts/{accountId}/roles/grant — rol ver (kapsam `account:role:write`)
- POST /internal/v1/accounts/{accountId}/roles/revoke — rol al (kapsam `account:role:write`)

Bu uçlar **Gateway'e açılmaz** (Gateway yalnız `/api/v1/<servis>/**` yönlendirir); yalnız iç ağdan, `AUTH_SERVICE_URL` ile çağrılır.

#### Yetkilendirme
- `service-tokens`: Public (cookie okunmaz, CSRF gerekmez); kimlik gövdedeki `clientId` + `clientSecret`.
- Diğer uçlar: `Authorization: Bearer <svc+jwt>`. Kullanıcı token'ı (`at+jwt`), cookie ve Auth'un başka servislere yolladığı token'lar kabul edilmez.

#### Request Body (`service-tokens`)
```json
{ "clientId": "admin-service", "clientSecret": "string, en az 32 karakter (AUTH_ADMIN_SERVICE_CLIENT_SECRET)", "audience": "isteğe bağlı; boşsa auth-service", "accountId": "UUID; yalnız hesaba bağlı kapsamda (signals:read) zorunlu, diğerlerinde verilemez" }
```

İstemci kimliği servis adıdır: `admin-service` (Auth'a `account:*`, community'ye `events:ingest`), `social-service` (community'ye `events:ingest`; user-service'e `privacy:read events:ingest`), `community-service` (admin ve notification'a `events:ingest`), `messaging-service` ve `lfp-service` (social-service'e `blocks:check`), `library-service` (user-service'e `privacy:read events:ingest`), `hardware-service` (user-service'e `privacy:read`; performance-service'e `devices:verify`, hesaba bağlı: token `account_id` taşır, hesap `ACTIVE` olmalı), `review-service` (user-service'e `events:ingest`), `game-service` (review-service'e `rating-summaries:read`), `recommendation-service` (library-service'e `signals:read`, hesaba bağlı: token `account_id` taşır, hesap `ACTIVE` olmalı). Her istemci **yalnız kendisine tanımlı (hedef, kapsam) çiftleri** için token alır; kimseye varsayılan imtiyaz yoktur. `audience` başka servis ise token o servisin JWKS doğrulamasında geçer (`aud=<servis>-service`, `scope=events:ingest`, `sub=svc:<istemci>`, 60 sn) ve Auth'un iç uçlarında **geçmez**.

#### Response — 200 (`service-tokens`, `Cache-Control: no-store`)
```json
{ "accessToken": "…", "tokenType": "Bearer", "expiresIn": 60, "scope": "account:read account:role:write account:status:write" }
```
Token: `typ=svc+jwt`, RS256 (JWKS aynı), `iss=ltz-auth`, **`aud=auth-service`** (yalnız Auth'ta geçer), `sub=svc:<istemci>`, `scope`, `jti`, `iat`/`nbf`/`exp` (60 sn).
`roles`, `sid`, `ver`, `amr` taşınmaz. Token'ı 60 sn dolmadan önce yenileyin; önbelleğe uzun süre almayın.

#### Request Body (`status`)
```json
{ "actorId": "UUID, işlemi yapan yönetici hesabı", "status": "SUSPENDED|BANNED|DISABLED|ACTIVE", "reason": "1-500 karakter" }
```

#### Request Body (`roles/grant`, `roles/revoke`)
```json
{ "actorId": "UUID", "role": "PLAYER|CONTENT_CREATOR|GAME_DEVELOPER|MODERATOR|ADMIN", "reason": "1-500 karakter" }
```

#### Davranış
- Servis token'ı kullanıcı yetkisi taşımaz: `actorId` Auth'ta yeniden doğrulanır (hesap var, `ACTIVE` ve `ADMIN` rolünde). Değilse `403 ACTING_ADMIN_INVALID`.
- Kurallar kullanıcı uçlarıyla (`/api/v1/auth/admin/**`) **aynıdır** ve gevşetilmez: kendini değiştirme yasağı, durum geçişleri (`BANNED → ACTIVE` dahil), ADMIN/MODERATOR için TOTP şartı, oturum iptali ve token sürümü artışı, gerekçe + `actorId` ile denetim kaydı.
- Doğrulama kuralları (hepsi zorunlu): imza + `kid`, `typ=svc+jwt`, `iss`, `aud` **yalnız** `auth-service`, `sub=svc:<kayıtlı ve etkin istemci>`, `jti`, `iat`, `nbf`, ömür ≤ 60 sn (bitiş şimdiden ≤ 60 sn ileride), `scope` istemcinin kayıtlı kapsamları içinde.
- Gizli anahtarı boş istemci **devre dışıdır**; `AUTH_ADMIN_SERVICE_CLIENT_SECRET` boşsa token alınamaz (fail-closed). Zayıf (32 karakterden kısa) anahtar veya bilinmeyen kapsam Auth'u açtırmaz.
- Yanlış / bilinmeyen / devre dışı istemci tek yanıt verir (`401 INVALID_CLIENT`), hesap kilidi yoktur (iç ağdaki biri gerçek servisi kilitleyemesin); başarısız denemeler günlüğe anahtarsız yazılır.

#### Hata Durumları
- 400: Doğrulama (bilinmeyen alan, eksik `actorId`/`reason`, geçersiz `clientId`, UUID olmayan `accountId`); `service-tokens` için `SERVICE_ACCOUNT_BINDING_INVALID` (hesaba bağlı kapsamda `accountId` yok ya da bağsız kapsamda verilmiş)
- 401: Kimliksiz, geçersiz/süresi dolmuş/yanlış `aud`·`typ`·`sub`·kapsam dışı servis token'ı (`UNAUTHENTICATED`); `service-tokens` için `INVALID_CLIENT`
- 403: Kapsam yetersiz; `ACTING_ADMIN_INVALID`; `SELF_MODIFICATION_FORBIDDEN`; `service-tokens` için `SERVICE_AUDIENCE_FORBIDDEN` (kimlik doğrulandı ama istemcinin bu hedefe izni yok) ve `SERVICE_TARGET_ACCOUNT_UNAVAILABLE` (hedef hesap yok ya da `ACTIVE` değil)
- 404: Hesap yok (`ACCOUNT_NOT_FOUND`)
- 409: `INVALID_STATUS_TRANSITION`, `TARGET_NOT_ACTIVE`, `ROLE_ALREADY_ASSIGNED`, `ROLE_NOT_ASSIGNED`, `BASE_ROLE_REQUIRED`, `ROLE_REQUIRES_TWO_FACTOR`

#### Bağlantı
Frontend:
Yok (yalnız servisler arası).

Diğer mikroservisler:
social-service, community-service: kendi ortamlarına `AUTH_SOCIAL_SERVICE_CLIENT_SECRET` / `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` ile aynı değer verilir; olay göndermeden önce `audience` ile hedef servis için token alırlar.
library-service, hardware-service, review-service, recommendation-service: `AUTH_LIBRARY_SERVICE_CLIENT_SECRET` / `AUTH_HARDWARE_SERVICE_CLIENT_SECRET` / `AUTH_REVIEW_SERVICE_CLIENT_SECRET` / `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET`. User Service'in alıcı uçları (`POST /internal/v1/privacy/decisions`, `POST /internal/events`) hazır; gerçek Auth token'ıyla uçtan uca doğrulama NOT VERIFIED. Library sinyal ucu henüz yok → PENDING. `user-service` bir istemci değildir: kimseyi çağırmaz, dış veri olayla gelir.
admin-service: kendi ortamına `AUTH_ADMIN_SERVICE_CLIENT_SECRET` ile aynı değer verilir; `POST /internal/v1/service-tokens` ile token alıp iç uçları çağırır. Admin servisinin kullanıcı kimliği/rolü doğrulaması (yönetici oturumu) Admin'in kendi sorumluluğudur; Auth yalnız `actorId`'yi yeniden doğrular.

#### Test Durumu
UNIT + INTEGRATION (Auth tarafı; Admin tüketici testi yok)

### AUTH — Giden sözleşmeler

- **Auth → her veri sahibi servis, hesap verisi silme (AUTH-16/17) — Auth tarafı IMPLEMENTED (2026-10-23):** `DELETE /internal/v1/accounts/{accountId}`, Yetkilendirme: Internal. Servis JWT'si (D4, `ServiceTokenIssuer`): başlık `typ=svc+jwt`, RS256 (JWKS); `iss=ltz-auth`, tek `aud=<servis>-service` (ör. `social-service`), `sub=svc:auth-service`, `scope=account:erase`, `account_id=<silinecek hesap>`, ömür 30 sn, `jti`. Hedef servis tümünü bağımsız doğrular; `sub` tam eşleşme ve `account_id` = yoldaki `{accountId}` zorunludur (aksi halde `403`); kullanıcı cookie'sine dayanmaz. İdempotent: `200`/`204` = veri silindi/anonimleştirildi (zaten yoksa da `204`); `202` dahil başka her yanıt başarısızlık sayılır ve tekrarlanır; yönlendirme izlenmez. Hedefler (16): `user, mail, game, library, review, social, messaging, lfp, community, content, event, hardware, recommendation, performance, admin, notification`. Auth'ta onay alan bir iç uç **yoktur**. Test Durumu: UNIT + INTEGRATION (gerçek HTTP).

Aşağıdakiler Auth'un çağıracağı/yayımlayacağı **PLANNED** sözleşmelerdir; karşı servisin kaydı o servis uygulanırken kendi başlığı altında açılır.

- **Auth → Mail, doğrulama kodu teslimi (MAIL-01) — IMPLEMENTED (2026-10-31, `HttpMailServiceAdapter`; ayrıntılı sözleşme `## MAIL` altında; aynı adaptör `password-resets`, `account-deletions`, `security-notifications` uçlarını da çağırır, `scope=mail:send`, yalnız `202` kabul, `locale` gönderilmez → iki dilli):** `POST /internal/v1/mail/verification-codes`, Yetkilendirme: Internal (Auth'un imzaladığı, `aud=mail-service` sınırlı servis JWT'si — D4, Faz 2). Gövde: `idempotencyKey` (UUID, kod kaydı kimliği), `accountId`, `recipientEmail`, `code`, `validitySeconds`, `locale` (`tr`/`en`, açık konu). Mail Service yalnız teslim eder; kodu düz metin olarak kalıcı tutmaz (`variables_encrypted`, gönderim sonrası temizleme) ve loglamaz. `202` = teslim kuyruğa alındı; diğer her yanıt Auth'ta teslim başarısızlığı sayılır. Kimlik doğrulaması olmadan bu uç açılmaz.
- **Auth → User, `UserRegistered` olayı:** Hesap `ACTIVE` olduktan **sonra** (kayıt anında değil) outbox ile yayımlanır. Alanlar: `eventId`, `accountId`, `username`, `occurredAt`. User Service profili `accountId` ile idempotent oluşturur; parola, e-posta doğrulama durumu ve rol User'a taşınmaz. Outbox altyapısı henüz yoktur (sonraki faz).

## USER

Sorumlu: Kerem · Gateway: `/api/v1/user/**` · Görevler: USER-

> Açık uçlar (2026-10-30, LTZ-KER-USER-02): `GET /api/v1/user/profiles/**` ve `GET /api/v1/user/media/*` misafire açıktır (token varsa doğrulanır); diğer tüm `/api/v1/user/**` uçları kimlik ister. Görünürlük kuralı tektir: "Gizlilik ayarlarım" kaydındaki tablo.
>
> Kimlik doğrulama (2026-10-30, LTZ-KER-USER-01 faz 1a): Kullanıcı uçları Auth'un RS256 imzasını JWKS ile, `typ=at+jwt`, `iss`, `aud` ⊇ `ltz-api`, UUID `sub` ve zorunlu `exp`/`nbf`/`iat` alanlarını doğrular; `iat` eksikse token reddedilir. İç uçlar (`/internal/**`) yalnız `typ=svc+jwt`, `aud=user-service` servis token'ı kabul eder; kullanıcı token'ı iç uçta, servis token'ı kullanıcı uçlarında geçmez. Mevcut `AUTH_SERVICE_URL`, `JWT_ISSUER`, `JWT_AUDIENCE` kullanılır, yeni ENV yoktur. Tanımsız her yol `denyAll`. Gerçek Auth token'ıyla uçtan uca doğrulama **NOT VERIFIED**.

### USER — Kendi profilimi getir (USER-01)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/user/me

#### Yetkilendirme
Authenticated (Owner). `userId` yalnız token'ın `sub` değerinden alınır; istekte kimlik alanı yoktur.

#### Response — 200
Profil yoksa varsayılan değerlerle **oluşturulur** (Auth'un `UserRegistered` olayı gecikmiş ya da henüz hedef açılmamış olsa da kullanıcı 404 görmez; eşzamanlı ilk isteklerde tek satır oluşur). `Cache-Control: no-store`.
```json
{
  "userId": "UUID",
  "username": "string | null (Auth'tan UserRegistered olayıyla gelen kopya; olay gelene kadar null)",
  "displayName": "string 2-32 | null",
  "bio": "string ≤500 | null",
  "playerTypes": ["CASUAL | COMPETITIVE | STORY | SOCIAL | COLLECTOR (en çok 2)"],
  "favoriteGenres": ["katalog kodu (en çok 10)"],
  "favoritePlatforms": ["katalog kodu (en çok 10)"],
  "country": "ISO 3166-1 alpha-2 | null",
  "avatar": { "small": "/api/v1/user/media/<anahtar>-s.jpg", "large": "/api/v1/user/media/<anahtar>.jpg", "animated": false },
  "cover": { "small": "...", "large": "...", "animated": false },
  "onboardingCompletedAt": "ISO-8601 | null",
  "updatedAt": "ISO-8601",
  "version": 0
}
```
`avatar`/`cover` görsel yoksa `null`. Adresler **değişmezdir** (her yükleme yeni adres alır) ve herkese açıktır; ayrıntı: "Profil görseli yükle" kaydı. GIF'te `small` ve `large` aynı dosyadır.

Yanıt e-posta, hesap durumu veya kimlik bilgisi içermez.

#### Hata Durumları
- 401: Token yok/geçersiz (imza, `iss`, `aud`, `typ`, süre, `iat`, UUID olmayan `sub`); servis token'ı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/me

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; `MeControllerTest`, eşzamanlı ilk istek ve token saldırı matrisi dahil). Gerçek Auth token'ıyla uçtan uca: NOT VERIFIED.

### USER — Profil güncelle (USER-02)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
PATCH /api/v1/user/me/profile

#### Yetkilendirme
Authenticated (Owner). Başka kullanıcının profilini değiştiren bir yol yoktur.

#### Request Body
Gönderilmeyen ya da `null` alan **değişmez**; metin alanında `""`, liste alanında `[]` alanı **temizler**. Tanımsız alan (ör. `userId`, `accountStatus`) `400` döner (`fail-on-unknown-properties`). Gövde en fazla 16 KB.
```json
{
  "version": "long ≥ 0, zorunlu: istemcinin son gördüğü sürüm",
  "displayName": "string 2-32 Unicode kod noktası",
  "bio": "string ≤500 kod noktası (düz metin; satır sonu \\n serbest)",
  "country": "ISO 3166-1 alpha-2 (küçük harf kabul, büyük harfe çevrilir)",
  "playerTypes": ["CASUAL | COMPETITIVE | STORY | SOCIAL | COLLECTOR"],
  "favoriteGenres": ["^[a-z0-9][a-z0-9_-]{0,47}$, en çok 10"],
  "favoritePlatforms": ["^[a-z0-9][a-z0-9_-]{0,47}$, en çok 10"]
}
```
Metinler Unicode NFC'ye çevrilir ve kırpılır; denetim, biçim (bidi/sıfır genişlik), özel kullanım karakterleri ve `nbsp` reddedilir. Biyografi HTML'e çevrilmeden **düz metin** saklanır: istemci onu yalnız metin olarak göstermelidir. Listeler küçük/büyük harf normalize edilir, yinelenenler birleştirilir.

#### Response — 200
`GET /me` ile aynı gövde (güncel `version` dahil). Değişiklik yoksa `version` artmaz.

#### Hata Durumları
- 400: Doğrulama (`VALIDATION_FAILED`, `INVALID_DISPLAY_NAME`, `INVALID_BIO`, `INVALID_COUNTRY`, `INVALID_PLAYER_TYPES`, `INVALID_FAVORITE_GENRES`, `INVALID_FAVORITE_PLATFORMS`), bozuk JSON, tanımsız alan
- 401: Token yok/geçersiz
- 409: `CONCURRENT_MODIFICATION`: `version` güncel değil ya da eşzamanlı güncelleme
- 413: Gövde 16 KB'ı aşıyor (`PAYLOAD_TOO_LARGE`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/me/profile

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; `MeControllerTest`, `ProfileTextTest`; eşzamanlı güncellemede yalnız biri başarılı).

### USER — Profil görseli yükle: avatar ve kapak (USER-03, USER-04)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/user/me/avatar
PUT /api/v1/user/me/cover

#### Yetkilendirme
Authenticated (Owner). Kimlik yalnız token'dan gelir; çerez/başlık/gövdedeki kimlik okunmaz.

#### Request Body
**Ham gövde**: gövde görselin kendisidir (multipart değil). `Content-Type` yalnız `image/jpeg`, `image/png` veya `image/gif` olabilir; başka tür `415`. Bildirilen tür gerçek içerikle (ilk baytlar / magic byte) **uyuşmak zorundadır**; SVG, HTML ve tanınmayan her içerik `400`. Gövde boyutu akış sırasında sınırlanır:

| Tür | En büyük yükleme | Kenar / piksel sınırı | Çıktı |
| --- | --- | --- | --- |
| Avatar JPEG/PNG | 2 MB | ≤ 8000 px kenar, ≤ 40 MP | Ortadan kare kırpılır; büyük 512 px, küçük 128 px; **büyütülmez** |
| Kapak JPEG/PNG | 4 MB | ≤ 8000 px kenar, ≤ 40 MP | En-boy oranı korunur, 1600×1600 içine sığdırılır (büyük) ve 800×800 içine (küçük); **büyütülmez** |
| Avatar GIF | 2 MB | ekran ≤ 512×512, ≤ 100 kare | Yeniden kodlanmaz, tek dosya |
| Kapak GIF | 4 MB | ekran ≤ 1600×1600, ≤ 100 kare | Yeniden kodlanmaz, tek dosya |

JPEG/PNG sunucuda yeniden kodlanır: EXIF/konum bilgisi ve gizli (polyglot) içerik temizlenir, JPEG'in EXIF yönlendirmesi uygulanır (telefon fotoğrafı yan dönük görünmez). Saydamlığı olmayan görsel progressive JPEG (kalite 0,85), saydam piksel içeren PNG olarak kalır. GIF'te yalnız kare verisi, süre/saydamlık bloğu ve döngü bloğu korunur; yorum ve diğer uygulama blokları atılır.

#### Response — 200
Güncel profil (`GET /me` ile aynı gövde; `avatar` ya da `cover` dolu). `Cache-Control: no-store`. Yükleme eski görseli değiştirir; eski dosyalar commit sonrası silinir.

#### Hata Durumları
- 400: `INVALID_IMAGE` (SVG/HTML/tanınmayan içerik, bildirilen tür içerikle uyuşmuyor, bozuk/kesik/CMYK görsel, bozuk GIF yapısı), `INVALID_IMAGE_DIMENSIONS` (kenar/piksel/kare sayısı ya da GIF ekranı sınırı aşıldı), `MALFORMED_REQUEST` (boş gövde)
- 401: Token yok/geçersiz; servis token'ı; çerezle kimlik
- 409: `CONCURRENT_MODIFICATION`: aynı kullanıcı aynı türü eşzamanlı yükledi
- 413: Gövde tür sınırını aşıyor (`PAYLOAD_TOO_LARGE`)
- 415: `Content-Type` jpeg/png/gif değil
- 429: `MEDIA_RATE_LIMITED`: kullanıcı başına saatte en çok 10 yükleme (değiştirilenler dahil)
- 503: `MEDIA_BUSY` (aynı anda en çok 2 görsel işlenir; sıra 3 sn içinde boşalmadı), `MEDIA_STORAGE_UNAVAILABLE` (depo yazılamadı; hiçbir kalıntı bırakılmaz)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/me/avatar ve /api/v1/user/me/cover (`fetch` ile `body: file`, `Content-Type: file.type`).

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL ve geçici disk; `MediaControllerTest` 23, `ImageIoInspectorTest`, `GifSanitizerTest`, `ExifOrientationTest`, `LocalFileMediaStorageTest`, `MediaCleanupServiceTest`).

### USER — Profil görseli kaldır (USER-03, USER-04)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/user/me/avatar
DELETE /api/v1/user/me/cover

#### Yetkilendirme
Authenticated (Owner).

#### Response — 204
Gövde yok. Görsel profilden kalkar ve dosyaları silinir; görsel yoksa da `204` (idempotent). Dosya silme geçici olarak başarısız olursa görsel yine profilden kalkar, dosya zamanlanmış temizleyici (5 dk) tarafından silinir.

#### Hata Durumları
- 401: Token yok/geçersiz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/me/avatar ve /api/v1/user/me/cover

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (`MediaControllerTest`, `MediaCleanupServiceTest`, `MediaCleanupWorkerTest`).

### USER — Profil görselini oku (herkese açık)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend, diğer servislerin istemcileri (adres bir yıl önbelleğe alınabilir)

#### Endpoint
GET /api/v1/user/media/{fileName}

#### Yetkilendirme
Public (kimlik gerekmez; misafir kartında da görünür). Adres, 128 bitlik rastgele anahtardır ve profil yanıtlarındaki `avatar`/`cover` alanlarından alınır; liste uç noktası yoktur.

#### Path Parameters
- fileName: `^[0-9a-f]{32}(-s)?\.(jpg|png|gif)$`, zorunlu. `-s` küçük sürümdür (GIF'te yok).

#### Response — 200
Görselin baytları. Başlıklar: `Content-Type: image/jpeg|png|gif`, `Cache-Control: max-age=31536000, public, immutable`, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: default-src 'none'; sandbox`, `Content-Disposition: inline`. **Veritabanına gitmez**: okuma yalnız diskten yapılır. Adres değişmezdir; görsel değiştirilince ya da kaldırılınca eski adres `404` olur (daha önce önbelleğe alan istemci süre dolana kadar görebilir).

#### Hata Durumları
- 404: `MEDIA_NOT_FOUND`: geçersiz ad deseni, büyük harf, tanımsız uzantı ya da dosya yok (hepsi aynı yanıt; yol atlatma denemeleri dahil)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/media/{fileName}

Diğer mikroservisler:
Diğer servisler görsel baytlarını kopyalamaz; yalnız bu adresi taşır.

#### Test Durumu
UNIT + INTEGRATION (`MediaControllerTest`, `LocalFileMediaStorageTest`: yol atlatma).

### USER — Hesap verisi silme (Internal, Auth → User)

Durum: IMPLEMENTED (alıcı taraf)
Sorumlu: Kerem
Tüketici servisler: auth-service (hesap silme koordinasyonu, `ltz.auth.deletion.targets` içinde `user` var)

#### Endpoint
DELETE /internal/v1/accounts/{accountId}

#### Yetkilendirme
Internal. Gateway'e açık **değildir** (`/internal/**` route edilmez). Yalnız Auth'un imzaladığı **servis token'ı** (`docs-auth/servis-sozlesmeleri.md` §3.1): `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud` ⊇ `user-service`, `jti` zorunlu, `scope` ⊇ `account:erase` (yoksa `403`), `sub` **tam olarak** `svc:auth-service` (`403`), `account_id` yoldaki `{accountId}` ile **aynı** (eksik/farklı `403`), `exp - iat ≤ 60 sn` ve `iat` zorunlu. Kullanıcı token'ı bu uçta kabul edilmez.

#### Path Parameters
- accountId: UUID, zorunlu — silinecek hesap

#### Response — 204
Gövde yok. Önce hesabın **tüm** profil görseli dosyaları (güncel olanlar ve silinmeyi bekleyenler dahil) diskten silinir; sonra tek transaction'da profil satırı silinir (görsel üst veri satırları FK `ON DELETE CASCADE` ile gider). **İdempotenttir:** veri yoksa, ikinci çağrıda ya da bilinmeyen hesapta da `204`. Başka hesapların verisine dokunulmaz. `inbox_events` hesap kimliği taşımadığı için silinecek veri içermez.

Dosyalar silinemezse (depo erişilemez) **hiçbir veri satırı silinmez** ve `503 ACCOUNT_ERASURE_PENDING` döner; Auth yeniden dener. Bu yüzden `204` her zaman "kişisel veri kalmadı" demektir.

#### Hata Durumları
- 400: Geçersiz `accountId` (`MALFORMED_REQUEST`)
- 401: Token yok/geçersiz; kullanıcı token'ı; yanlış `iss`/`aud`/`typ`; süresi dolmuş, 60 sn'den uzun ömürlü ya da `iat`/`jti` eksik; `sub` `svc:` ile başlamıyor; bozuk/yanlış anahtarlı imza; `alg=none`/HS256; bilinmeyen `kid`
- 403: `account:erase` kapsamı yok; `sub` `svc:auth-service` değil; `account_id` yok ya da yoldaki hesapla aynı değil (hiçbir şey silinmez); bu yolda başka yöntem ya da başka iç yol
- 500: Beklenmeyen sunucu hatası
- 503: `ACCOUNT_ERASURE_PENDING`: görsel dosyaları silinemedi, hiçbir veri silinmedi; Auth yeniden dener

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth, silme onaylandığında bu ucu `${USER_SERVICE_URL}/internal/v1/accounts/{accountId}` adresine çağırır. Gerçek Auth token'ıyla canlı silme **NOT VERIFIED**.

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; `AccountErasureTest` 18 test: rehberdeki 17 senaryo + büyük/küçük harf UUID, Auth `ServiceTokenIssuer` biçimi uyumluluğu dahil; `AccountErasureStorageFailureTest` depo hatasında 503 ve satırların korunması; `MediaControllerTest` görsel dosyalarının silindiğini doğrular).

### USER — Servisler arası olay girişi (Internal)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler (üretici olarak): auth-service; social-service (T6, henüz göndermiyor); library-service ve review-service (önerilen sözleşme T7, henüz göndermiyor).

#### Endpoint
POST /internal/events

#### Yetkilendirme
Internal. Gateway'de **route edilmez**. `typ=svc+jwt`, `aud=user-service`, `scope` ⊇ `events:ingest` (yoksa `403`); token'ın `sub` değeri zarftaki `producer` ile tam olarak `svc:<producer>` olmalı (bir servis başkası adına olay yayınlayamaz, `403`). Auth olaylarında token ayrıca olayın hesabına bağlıdır: `account_id` claim'i `aggregateId` ile aynı olmalı (`403`). Kullanıcı token'ı `401`.

#### Request Body (olay zarfı, docs/architecture.md §6.2)
Zarfta tanımsız alan reddedilir. Gövde en fazla 16 KB.
```json
{
  "eventId": "UUID (tekilleştirme anahtarı)",
  "eventType": "UserRegistered | AccountStatusChanged | AccountEmailChanged | ExternalIdentityLinked | ExternalIdentityUnlinked",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC",
  "producer": "auth-service",
  "aggregateId": "UUID (hesap)",
  "payload": { }
}
```
**Kabul edilen olaylar** (hepsi yalnız `producer=auth-service`; farklı üretici `400 EVENT_PRODUCER_MISMATCH`):
- `UserRegistered`: `{ "accountId": "UUID = aggregateId", "username": "^[A-Za-z0-9_]{3,20}$" }`. Profili açar ya da var olan profile kullanıcı adı kopyasını yazar; **hesap durumunu değiştirmez**.
- `AccountStatusChanged`: `{ "accountId": "UUID = aggregateId", "status": "ACTIVE | SUSPENDED | BANNED | DISABLED | ..." }`. Profil varsa durumu günceller; **eski olay yeni durumu ezemez** (`occurredAt` karşılaştırılır). Profil yoksa yalnız kısıtlayıcı durumlar (`SUSPENDED`, `BANNED`, `DISABLED`) kaydedilir (askıdaki hesap sonradan görünür hale gelmesin); `ACTIVE` için profil ilk kullanımda zaten `ACTIVE` açılır. Tanınmayan durum adı profili değiştirmez.
- `AccountEmailChanged`: `{ "accountId": "UUID = aggregateId" }`. Yük doğrulanır, etkisi yoktur (User e-posta tutmaz; yeni adres olayda gelmez).
- `FriendshipChanged` (yalnız `social-service`): `{ "userLowId": "UUID", "userHighId": "UUID", "change": "ADDED | REMOVED" }`. social-service'in mevcut olay kaydıyla aynıdır. Arkadaşlık yansımasını günceller.
- `UserBlocked` / `UserUnblocked` (yalnız `social-service`): `{ "blockerId": "UUID", "blockedId": "UUID" }` (farklı olmalı). Engel yansımasını günceller; engel iki yönde uygulanır.
- `LibrarySummaryChanged` (yalnız `library-service`, **önerilen sözleşme**): `{ "userId": "UUID = aggregateId", "collectionCount": int ≥ 0, "completedCount": int ≥ 0, "favoriteCount": int ≥ 0 }`. **Mutlak** sayılar (fark değil).
- `ReviewSummaryChanged` (yalnız `review-service`, **önerilen sözleşme**): `{ "userId": "UUID = aggregateId", "reviewCount": int ≥ 0 }`. Mutlak sayı.
- Yansıma ve sayaç yazmaları son-durum upsert'üdür: aynı ilişki/kullanıcı için `occurredAt`'i daha eski olay yeni durumu **ezmez** (sıra bozuk teslime dayanıklı). Kaldırılan arkadaşlık/engel satırı `active=false` olarak kalır ki gecikmiş eski bir "eklendi" olayı onu geri getiremesin. Sayılar ondalık/metin/negatif ise `400`.
- Bilinmeyen `eventType` başarıyla yok sayılır (`IGNORED`), kaydedilmez, üretici yeniden denemez. `eventVersion` ≠ 1 `400`.

#### Response — 200
```json
{
  "eventId": "UUID",
  "outcome": "PROCESSED | DUPLICATE | IGNORED"
}
```
Olay `inbox_events` tablosunda `eventId` ile tekilleştirilir ve etkisiyle **aynı transaction'da** uygulanır: aynı `eventId` ikinci kez gelirse (eşzamanlı teslim dahil) `DUPLICATE` döner ve hiçbir şey değişmez. Geçersiz yük işlenmiş diye kaydedilmez, aynı olay düzeltilmiş yükle sonra işlenebilir. Olay yükü ve hesap kimliği tabloya yazılmaz.

#### Hata Durumları
- 400: Zarf doğrulama hatası (`VALIDATION_FAILED`), üretici-olay uyuşmazlığı (`EVENT_PRODUCER_MISMATCH`), geçersiz yük ya da `accountId` ≠ `aggregateId` (`INVALID_EVENT`), desteklenmeyen sürüm, tanımsız zarf alanı
- 401: Token yok/geçersiz; kullanıcı token'ı; yanlış `aud`
- 403: `events:ingest` kapsamı yok; `sub` ≠ `svc:<producer>`; Auth olayında `account_id` ≠ `aggregateId` ya da yok; GET gibi başka yöntem
- 413: Gövde 16 KB'ı aşıyor
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth, `ltz.auth.events.targets` (ortam değişkeni `AUTH_EVENT_TARGETS`) listesine `user` eklenince olayları bu uca iletir (`docs-user/integration/auth/auth-service-talepleri/auth-requests-01.md`). Hedef eklenene kadar Auth hiçbir olay yazmaz. Gerçek Auth token'ıyla uçtan uca: **NOT VERIFIED**.

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; `InternalEventTest` 27 test: tekilleştirme, eşzamanlı teslim, sıra bozukluğu, üretici/hesap bağı, geri alma, doğrulama; `ProjectionEventTest` 8 test: arkadaşlık/engel ve özet olayları, sıra bozukluğu, üretici kuralları). Gerçek social/library/review üreticileriyle uçtan uca: NOT VERIFIED.

### USER — Gizlilik ayarlarım (USER-06)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/user/me/privacy
PUT /api/v1/user/me/privacy

#### Yetkilendirme
Authenticated (Owner). Kimlik yalnız token'dan.

#### Request Body (PUT)
Ayarların **tamamı** zorunludur; tanımsız alan ve değer `400`.
```json
{
  "version": "long ≥ 0, zorunlu: son görülen sürüm; hiç kaydedilmemişse 0",
  "friendsList": "PUBLIC | FRIENDS | PRIVATE",
  "library": "PUBLIC | FRIENDS | PRIVATE",
  "hardware": "PUBLIC | FRIENDS | PRIVATE",
  "performance": "PUBLIC | FRIENDS | PRIVATE",
  "guestProfileAccess": "BASIC | DETAILED"
}
```

#### Response — 200
Aynı alanlar ve güncel `version`. Ayar hiç kaydedilmemişse `GET` varsayılanları döner (okuma satır oluşturmaz): `friendsList=FRIENDS`, `library=PUBLIC`, `hardware=FRIENDS`, `performance=PRIVATE`, `guestProfileAccess=BASIC`, `version=0`. İlk `PUT` sürüm 0 ile yazar; sonraki her değişiklik sürümü artırır, değişiklik yoksa artırmaz. `Cache-Control: no-store`.

**Görünürlük kuralı** (tüm uçlarda tek kural, `PrivacyRules`):

| İzleyici | PUBLIC | FRIENDS | PRIVATE |
| --- | --- | --- | --- |
| Sahip | evet | evet | evet |
| Arkadaş | evet | evet | hayır |
| Giriş yapmış üye | evet | hayır | hayır |
| Misafir | yalnız `DETAILED` | hayır | hayır |

Aralarında herhangi bir yönde engel olan izleyici profili hiç göremez. Profil bütünüyle gizlenemez: temel kart (ad, avatar, kapak) her zaman görünür.

#### Hata Durumları
- 400: `VALIDATION_FAILED`, tanımsız alan/değer, negatif sürüm
- 401: Token yok/geçersiz; servis token'ı
- 409: `CONCURRENT_MODIFICATION`: sürüm güncel değil ya da eşzamanlı ilk kayıt
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/me/privacy

Diğer mikroservisler:
Ayarlar diğer servislere "Gizlilik kararı" iç ucuyla verilir.

#### Test Durumu
UNIT + INTEGRATION (`PrivacyControllerTest`, `PrivacyRulesTest` tam matris).

### USER — Açık profil (USER-05, USER-12)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/user/profiles/{userId}
GET /api/v1/user/profiles/by-username/{username}

#### Yetkilendirme
Public + isteğe bağlı kimlik. Token yoksa izleyici **misafirdir**; token varsa doğrulanır ve **geçersiz token misafir sayılmaz, `401`** olur. Servis token'ı `401`.

#### Path Parameters
- userId: UUID
- username: `^[A-Za-z0-9_]{3,20}$` (büyük/küçük harfe duyarsız); desene uymayan değer `404`

#### Response — 200
```json
{
  "userId": "UUID",
  "username": "string | null",
  "displayName": "string | null",
  "avatar": { "small": "...", "large": "...", "animated": false },
  "cover": null,
  "details": {
    "bio": "string | null", "playerTypes": [], "favoriteGenres": [], "favoritePlatforms": [], "country": "TR | null",
    "steam": { "steamId": "76561198000000000", "profileUrl": "https://steamcommunity.com/profiles/..." }
  },
  "visible": { "friendsList": false, "library": true, "hardware": false, "performance": false },
  "summary": {
    "friends": { "status": "AVAILABLE | HIDDEN | UNAVAILABLE", "count": 12 },
    "library": { "status": "AVAILABLE | HIDDEN | UNAVAILABLE", "collection": 40, "completed": 12, "favorite": 5 },
    "reviews": { "status": "AVAILABLE | HIDDEN | UNAVAILABLE", "count": 7 }
  }
}
```
- Temel kart (`userId`, `username`, `displayName`, `avatar`, `cover`) her zaman doludur.
- `details`: üye/arkadaş/sahip için dolu; misafir için yalnız sahip `guestProfileAccess=DETAILED` yaptıysa, aksi halde `null`. `details.steam` yalnız sahibi Steam bağlantısını profilde göstermeyi seçtiyse doludur, aksi halde `null`.
- `visible`: izleyicinin görebileceği bölümler; istemci diğer servislere (Social, Library, Hardware, Performance) yalnız bunlar için gider. O servisler de kararı kendi tarafında yeniden uygular.
- `summary` (USER-12): sayı yalnız `AVAILABLE` iken doludur. `HIDDEN` = izleyici göremez, `UNAVAILABLE` = kaynak servisten henüz veri yok (sayı **uydurulmaz**). Arkadaş sayısı Social yansıması güvenilir ilan edilene kadar (`ltz.user.social.projection-enabled`) `UNAVAILABLE`; incelemeler herkese açık UGC olduğundan `PUBLIC` bölüm kuralıyla gösterilir.
- E-posta, hesap durumu, kimlik bilgisi yoktur. `Cache-Control: no-store, private` (yanıt izleyiciye göre değişir).

#### Hata Durumları
- 400: Geçersiz `userId`
- 401: Geçersiz/süresi dolmuş token; servis token'ı
- 404: `PROFILE_NOT_FOUND`: profil yok, hesap yasaklı/devre dışı/silinmekte/doğrulanmamış **ya da** izleyiciyle arasında herhangi bir yönde engel var (hepsi aynı yanıt). Askıdaki (`SUSPENDED`) hesap görünür.
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/profiles/{userId} ve /api/v1/user/profiles/by-username/{username}

Diğer mikroservisler:
Arkadaşlık/engel ve özet sayıları olaylardan gelen yansımadır; bu uç hiçbir servisi senkron çağırmaz.

#### Test Durumu
UNIT + INTEGRATION (`PublicProfileControllerTest` 12, `SocialProjectionTrustedTest`).

### USER — Toplu profil kartı

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend (arkadaş listesi, mesaj listesi gibi kimlik listelerine ad/avatar eklemek için)

#### Endpoint
POST /api/v1/user/profiles/batch

#### Yetkilendirme
Authenticated. Misafir `401`.

#### Request Body
```json
{ "userIds": ["UUID (1-50 adet; yinelenenler tek sayılır)"] }
```

#### Response — 200
```json
{ "items": [ { "userId": "UUID", "username": "string | null", "displayName": "string | null", "avatar": { "small": "...", "large": "...", "animated": false } } ] }
```
İstek sırası korunur. Olmayan, yasaklı/devre dışı hesaplar ve izleyiciyle engel ilişkisi olanlar **sessizce atlanır** (hangisi olduğu sızdırılmaz). Askıdaki hesap listelenir. Tüm kart verisi en çok üç sorguyla okunur (N+1 yok). `Cache-Control: no-store, private`.

#### Hata Durumları
- 400: `VALIDATION_FAILED` (boş liste, 50'den fazla kimlik, geçersiz UUID)
- 401: Token yok/geçersiz
- 413: Gövde 16 KB'ı aşıyor
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/profiles/batch

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (`PlayerSearchTest`).

### USER — Oyuncu arama (USER-11)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/user/search?q={metin}

#### Yetkilendirme
Authenticated. **Misafir arama yapamaz** (`401`; kullanıcı listesinin toplu çekilmesine karşı).

#### Query Parameters
- q: string, zorunlu; kırpıldıktan sonra 2-32 karakter, denetim/biçim karakteri yok

#### Response — 200
Toplu kart ile aynı gövde (`items`), **en çok 20** sonuç; sayfalama yok (sorguyu daraltın). Kullanıcı adı **ya da** görünen ad **önekiyle** eşleşir; büyük/küçük harfe duyarsızdır ve Türkçe `ı/I/İ/i` aynı sayılır. `%`, `_`, `\` joker değil düz karakterdir. Yalnız `ACTIVE` hesaplar döner: askıdaki, yasaklı ve izleyiciyle herhangi bir yönde engeli olan hesaplar çıkmaz. Sıra: kullanıcı adı (yoksa sonda), görünen ad. Kısmi `text_pattern_ops` indeksleri kullanılır; ayrı arama servisi yoktur.

#### Hata Durumları
- 400: `INVALID_SEARCH_QUERY` (kısa/uzun/yasak karakter), `q` yok
- 401: Token yok/geçersiz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/search

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (`PlayerSearchTest` 9 test: önek, Türkçe katlama, durum/engel süzgeci, sınır, joker kaçırma).

### USER — Gizlilik kararı (Internal, Social/Hardware/Library → User)

Durum: IMPLEMENTED (çağıran tüketiciler ve Auth kapsamı **PENDING**: T3, T6, T7, T8)
Sorumlu: Kerem
Tüketici servisler: social-service (SOC-12), hardware-service (HW-08 `HardwareVisibilityPolicy`), library-service (LIB-11)

#### Endpoint
POST /internal/v1/privacy/decisions

#### Yetkilendirme
Internal. Gateway'de route edilmez. `typ=svc+jwt`, `aud=user-service`, `scope` ⊇ `privacy:read` (yoksa `403`); çağıran (`sub`) yalnız `svc:social-service`, `svc:hardware-service`, `svc:library-service` olabilir (aksi halde `403`). Kullanıcı token'ı `401`. **Auth'ta bu kapsam ve çağıran→hedef çiftleri henüz tanımlı değildir (talep T3).**

#### Request Body
```json
{
  "viewerId": "UUID | null (çağıranın doğruladığı kullanıcı; misafir için null)",
  "field": "FRIENDS_LIST | LIBRARY | HARDWARE | PERFORMANCE",
  "ownerIds": ["UUID (1-100; yinelenenler tek)"]
}
```

#### Response — 200
```json
{ "decisions": [ { "ownerId": "UUID", "setting": "PUBLIC | FRIENDS | PRIVATE", "allowed": false, "friends": false } ] }
```
İstek sırasıyla. `setting`: sahibin ayarı (kayıt yoksa varsayılan; yasaklı/devre dışı hesapta `PRIVATE`). `allowed`: izleyici görebilir mi (görünürlük kuralı + engel + hesap durumu). `friends`: izleyici ile sahip arkadaş mı. En çok dört sorgu (N+1 yok).

**Fail-closed:** Social yansıması güvenilir ilan edilene kadar (`ltz.user.social.projection-enabled=false`, varsayılan) engel ve arkadaşlık bilinmez; bu yüzden **sahibi olmayan izleyiciye `allowed` her zaman `false`** döner (Hardware sözleşmesi: bilinmeyen karar reddedilir). `setting` yine döner: engel ve arkadaşlığı kendisi bilen Social yalnız `setting`'i kullanabilir. Sahip kendi bölümü için her zaman `allowed=true` alır.

#### Hata Durumları
- 400: `VALIDATION_FAILED` (boş/101+ sahip, alan yok), tanımsız alan değeri
- 401: Token yok/geçersiz; kullanıcı token'ı; yanlış `aud`
- 403: `privacy:read` kapsamı yok; çağıran izinli değil; başka yöntem (GET)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
social-service, hardware-service ve library-service Auth'tan `aud=user-service`, `scope=privacy:read` servis token'ı alıp bu ucu `${USER_SERVICE_URL}` üzerinden çağırır (Auth T3 sonrası). Gerçek tüketicilerle uçtan uca: **NOT VERIFIED**.

#### Test Durumu
UNIT + INTEGRATION (`PrivacyDecisionTest` 8, `SocialProjectionTrustedTest` 3, `PrivacyRulesTest` 21).

### USER — Tercihlerim (USER-10)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/user/me/preferences
PUT /api/v1/user/me/preferences

#### Yetkilendirme
Authenticated (Owner). Kimlik yalnız token'dan.

#### Request Body (PUT)
Tercihlerin **tamamı** zorunludur; tanımsız alan ve tanımsız tema `400`.
```json
{
  "version": "long ≥ 0, zorunlu: son görülen sürüm; hiç kaydedilmemişse 0",
  "locale": "tr | en",
  "theme": "LIGHT | DARK | SYSTEM",
  "reducedMotion": true,
  "compactLayout": false
}
```

#### Response — 200
Aynı alanlar ve güncel `version`. Hiç kaydedilmemişse `GET` varsayılanları döner (okuma satır oluşturmaz): `locale=tr`, `theme=SYSTEM`, `reducedMotion=false`, `compactLayout=false`, `version=0`. İlk `PUT` sürüm 0 ile yazar; sonraki her değişiklik sürümü artırır, değişiklik yoksa artırmaz. Tema dilden bağımsızdır. `Cache-Control: no-store`.

#### Hata Durumları
- 400: `VALIDATION_FAILED`, `INVALID_LOCALE` (`tr`/`en` dışı), tanımsız alan, negatif sürüm
- 401: Token yok/geçersiz; servis token'ı
- 409: `CONCURRENT_MODIFICATION`: sürüm güncel değil ya da eşzamanlı ilk kayıt
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/me/preferences

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (`PreferenceOnboardingTest` 11).

### USER — İlk kurulum (onboarding) durumu ve tamamlama (USER-09)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/user/me/onboarding
POST /api/v1/user/me/onboarding/complete

#### Yetkilendirme
Authenticated (Owner). Profil yoksa ilk çağrıda varsayılanlarla oluşturulur.

#### Request Body (POST complete)
Tanımsız alan `400`. Favori tür ve platform listeleri zorunludur ve **boş olamaz** (en az birer tane).
```json
{
  "version": "long ≥ 0, zorunlu: GET /me/onboarding yanıtındaki version",
  "playerTypes": ["isteğe bağlı: CASUAL | COMPETITIVE | STORY | SOCIAL | COLLECTOR (en çok 2)"],
  "favoriteGenres": ["katalog kodu (en az 1, en çok 10)"],
  "favoritePlatforms": ["katalog kodu (en az 1, en çok 10)"],
  "locale": "isteğe bağlı: tr | en",
  "theme": "isteğe bağlı: LIGHT | DARK | SYSTEM"
}
```
Seçimler profil alanlarına (`PATCH /me/profile` ile aynı doğrulama) ve tercihlere yazılır; ayrı bir onboarding tablosu yoktur. Gönderilmeyen `locale`/`theme` ve diğer tercih alanları mevcut değerinde kalır.

#### Response — 200
```json
{ "completed": false, "completedAt": "ISO-8601 | null", "missing": ["FAVORITE_GENRES", "FAVORITE_PLATFORMS"], "version": 0 }
```
`missing`: tamamlanması için eksik adımlar (tamamlanmışsa boş). `GET` yalnız durumu okur. `POST complete` **idempotenttir**: kurulum zaten tamamlandıysa hiçbir şey değişmez ve güncel durum `200` döner (seçimleri sonradan değiştirmek profil ve tercih uçlarının işidir). Dönen `version` güncel profil sürümüdür. `Cache-Control: no-store`.

#### Hata Durumları
- 400: `VALIDATION_FAILED`, `ONBOARDING_INCOMPLETE` (tür ya da platform listesi boş), `INVALID_FAVORITE_GENRES`, `INVALID_FAVORITE_PLATFORMS`, `INVALID_PLAYER_TYPES`, `INVALID_LOCALE`
- 401: Token yok/geçersiz; servis token'ı
- 409: `CONCURRENT_MODIFICATION`: profil sürümü güncel değil (kurulum tamamlanmamışken)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/me/onboarding ve /api/v1/user/me/onboarding/complete

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (`PreferenceOnboardingTest` 11).

### USER — Bağlı hesaplar: Steam (USER-07/08)

Durum: IMPLEMENTED. Auth tarafı (bağlama/kaldırma ucu ve olaylar) PENDING (T4).
Sorumlu: Kerem
Tüketici servisler: Frontend

Bağlama ve kaldırma **Auth'tadır** (kimlik kanıtı yalnız Auth'ta üretilir; son giriş yöntemi koruması da Auth'tadır). User, Auth'un `ExternalIdentityLinked` / `ExternalIdentityUnlinked` olaylarından durumu yansıtır ve yalnız açık profilde gösterme iznini yönetir. Yalnız Steam; Discord kapsam dışıdır. Steam OpenID access token vermez, saklanacak token yoktur.

#### Endpoint
GET /api/v1/user/me/linked-accounts
PATCH /api/v1/user/me/linked-accounts/{provider}   (`steam`, büyük/küçük harfe duyarsız)

#### Yetkilendirme
Authenticated (Owner). Kimlik yalnız token'dan.

#### Request Body (PATCH)
Tanımsız alan reddedilir.
```json
{ "version": "long ≥ 0, zorunlu: GET yanıtındaki sürüm", "showOnProfile": true }
```

#### Response — 200
`GET`: her desteklenen sağlayıcı için bir kayıt; bağlantı yoksa `linked=false`. `PATCH`: tek kayıt. `Cache-Control: no-store`.
```json
{ "accounts": [ {
    "provider": "STEAM", "linked": true, "externalId": "76561198000000000",
    "profileUrl": "https://steamcommunity.com/profiles/76561198000000000",
    "showOnProfile": false, "linkedAt": "ISO-8601 UTC", "version": 3
} ] }
```
- `showOnProfile` varsayılanı `false`; her yeni bağlantıda (farklı Steam kimliği dahil) sıfırlanır. Aynı kimliğin yeniden bildirilmesi izni korur.
- Açık profilde (`GET /profiles/...`) `details.steam` `{ "steamId", "profileUrl" }` yalnız bağlantı etkin, sahibi `showOnProfile=true` yapmış ve izleyici `details` görebiliyorsa doludur; aksi halde `null`.
- Bağlantı kaldırılınca kimlik ve izin silinir; kayıt `linked=false` olur.

#### Hata Durumları
- 400: `VALIDATION_FAILED` (eksik/tanımsız alan, negatif sürüm)
- 401: Token yok/geçersiz; servis token'ı
- 404: `LINKED_ACCOUNT_NOT_FOUND` (bilinmeyen sağlayıcı ya da bağlantı yok)
- 409: `CONCURRENT_MODIFICATION` (sürüm güncel değil; bağlantı olayı araya girmiş olabilir)
- 500: Beklenmeyen sunucu hatası

#### Olay girişi (`POST /internal/events`, yalnız `auth-service`, **önerilen sözleşme T4**)
- `ExternalIdentityLinked`: `{ "accountId": "UUID = aggregateId", "provider": "STEAM", "providerSubject": "^[0-9]{17}$" }`
- `ExternalIdentityUnlinked`: `{ "accountId": "UUID = aggregateId", "provider": "STEAM" }`
- Son-durum upsert, `occurredAt` ile korunur: eski bir "bağlandı" olayı daha yeni bir "kaldırıldı"yı geri alamaz. Tanınmayan `provider` sessizce yok sayılır (`200`); geçersiz kimlik ya da başka hesapta etkin aynı kimlik `400 INVALID_EVENT`.

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/user/me/linked-accounts

Diğer mikroservisler:
Auth: `ExternalIdentityLinked/Unlinked` olayları (T4, henüz yayınlanmıyor)

#### Test Durumu
INTEGRATION (`LinkedAccountTest` 11, `AccountErasureTest` tohumu).

## MAIL

Sorumlu: Kerem · Gateway: `/api/v1/mail/**` · Görevler: MAIL-01..05, 08, 09, 10..16

> Mail Service'in **kullanıcıya açık ucu yoktur**; hepsi `/internal/**` yollarıdır ve Gateway'e açılmaz. Çağıran yalnız Auth'tur (`sub=svc:auth-service`). Auth'un `Mail*Port` sözleşmesine bire bir uyar.
>
> Kimlik doğrulama (2026-10-31, LTZ-KER-MAIL-01): Auth'un RS256 imzasını JWKS ile; `typ=svc+jwt`, `iss`, `aud=mail-service`, `sub=svc:auth-service`, UUID `account_id` claim'i ve zorunlu `exp`/`nbf`/`iat` doğrulanır. Kullanıcı token'ı hiçbir uçta geçmez. Token'daki `account_id`, gövdedeki `accountId` (silme ucunda yoldaki hesap) ile aynı olmalıdır; değilse `403`. Tanımsız her yol reddedilir. Yeni ENV'ler: `MAIL_PAYLOAD_ENCRYPTION_KEY`, `MAIL_SMTP_*`, `MAIL_FROM_ADDRESS`, `MAIL_ALLOWED_LINK_HOSTS` (hepsi `.env.example`'da, boş). Gerçek Auth token'ıyla uçtan uca doğrulama **VERIFIED** (2026-10-31, Compose: Auth `mail-service` adaptörü → Mail → Mailpit; `EMAIL_VERIFICATION` kaydı `SENT`, 1 deneme, gönderim sonrası şifreli içerik ve alıcı adresi temizlendi). Diğer 3 e-posta ucu da aynı gün uçtan uca doğrulandı (`PASSWORD_RESET_LINK`, `PASSWORD_CHANGED`, `ACCOUNT_DELETION_CONFIRMATION`: hepsi `SENT`, 1 deneme). Silme ucu da uçtan uca doğrulandı (Auth silme koordinatörü, bekleme süresi dev'de `PT0S`: onaydan sonra hesabın Mail kayıtları 2 → 0).
>
> Teslim modeli: `202` e-postanın gönderildiği anlamına gelmez; istek **kalıcı olarak kuyruğa yazıldı** demektir. Gönderim ayrı işleyicide yapılır (5 sn tarama, üstel geri çekilme 1 dk → 6 sa, en çok 8 deneme). Doğrulama kodu/bağlantı kuyrukta AES-256-GCM ile şifreli tutulur, terminal durumda silinir; e-posta adresi terminal durumda maskelenir. Kod, bağlantı ve adres loglanmaz.

### MAIL — Doğrulama kodu e-postası (MAIL-01)

Durum: IMPLEMENTED (kuyruk, şifreleme ve işleyici testli; production SMTP sağlayıcısı henüz seçilmedi, development'ta Mailpit)
Sorumlu: Kerem
Tüketici servisler: Auth

#### Endpoint
POST /internal/v1/mail/verification-codes

#### Yetkilendirme
Internal. `svc+jwt`, `aud=mail-service`, `sub=svc:auth-service`, `scope=mail:send`, `account_id` = gövdedeki `accountId`.

#### Request Body
```json
{
  "idempotencyKey": "UUID (Auth'un kod kaydı kimliği)",
  "accountId": "UUID",
  "recipientEmail": "string ≤254, geçerli e-posta",
  "code": "string, 6 hane (^[0-9]{6}$)",
  "validitySeconds": "int 60-3600",
  "locale": "tr | en (opsiyonel; yoksa e-posta TR + EN iki dilli gider)"
}
```

#### Response — 202
Gövde yok. Aynı `idempotencyKey` + aynı içerik tekrar gelirse yine `202` döner ve ikinci e-posta kuyruğa alınmaz.

#### Hata Durumları
- 400: Doğrulama (`VALIDATION_FAILED`, yalnız alan adı ve kural kodu döner; istek değeri geri yansıtılmaz); gövde okunamadı (`MALFORMED_REQUEST`)
- 401: Token yok/geçersiz (`UNAUTHENTICATED`)
- 403: Çağıran `svc:auth-service` değil, scope `mail:send` değil veya token `account_id` ≠ gövde `accountId` (`FORBIDDEN`)
- 409: Aynı `idempotencyKey` farklı içerikle kullanıldı (`IDEMPOTENCY_CONFLICT`)
- 413: Gövde 16 KB üstü (`PAYLOAD_TOO_LARGE`)
- 503: İstek kalıcı yazılamadı, kısa süre sonra tekrar dene (`TEMPORARILY_UNAVAILABLE`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth → Mail, RestClient. Auth tarafı `HttpMailServiceAdapter` **IMPLEMENTED** (`ltz.auth.mail.adapter=mail-service`); uçtan uca doğrulandı (2026-10-31).

#### Test Durumu
INTEGRATION (`MailDeliveryControllerTest`, `MailDispatchServiceTest`, `MailSchemaAndRetentionTest`)

### MAIL — Parola sıfırlama e-postası (MAIL-02)

Durum: IMPLEMENTED (aynı kısıtlar: production SMTP PENDING)
Sorumlu: Kerem
Tüketici servisler: Auth

#### Endpoint
POST /internal/v1/mail/password-resets

#### Yetkilendirme
Internal. Doğrulama kodu ucu ile aynı (`scope=mail:send`).

#### Request Body
```json
{
  "idempotencyKey": "UUID",
  "accountId": "UUID",
  "recipientEmail": "string ≤254, geçerli e-posta",
  "resetLink": "string ≤512; host allowlist (MAIL_ALLOWED_LINK_HOSTS) politikasına uymalı",
  "validitySeconds": "int 60-86400",
  "locale": "tr | en (opsiyonel)"
}
```

#### Response — 202
Gövde yok.

#### Hata Durumları
- 400: Doğrulama; bağlantı politikası dışı host/şema (`INVALID_LINK`)
- 401, 403, 409, 413, 503: Doğrulama kodu ucu ile aynı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth → Mail. Auth tarafı `HttpMailServiceAdapter` **IMPLEMENTED** (Mailpit ile uçtan uca doğrulandı, 2026-10-31).

#### Test Durumu
INTEGRATION (`MailDeliveryControllerTest`)

### MAIL — Hesap silme onay e-postası (MAIL-03)

Durum: IMPLEMENTED (aynı kısıtlar: production SMTP PENDING)
Sorumlu: Kerem
Tüketici servisler: Auth

#### Endpoint
POST /internal/v1/mail/account-deletions

#### Yetkilendirme
Internal. Doğrulama kodu ucu ile aynı (`scope=mail:send`).

#### Request Body
```json
{
  "idempotencyKey": "UUID",
  "accountId": "UUID",
  "recipientEmail": "string ≤254, geçerli e-posta",
  "confirmationLink": "string ≤512; host allowlist politikasına uymalı",
  "validitySeconds": "int 60-86400",
  "locale": "tr | en (opsiyonel)"
}
```

#### Response — 202
Gövde yok.

#### Hata Durumları
- 400: Doğrulama; bağlantı politikası dışı (`INVALID_LINK`)
- 401, 403, 409, 413, 503: Doğrulama kodu ucu ile aynı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth → Mail. Auth tarafı `HttpMailServiceAdapter` **IMPLEMENTED** (Mailpit ile uçtan uca doğrulandı, 2026-10-31).

#### Test Durumu
INTEGRATION (`MailDeliveryControllerTest`)

### MAIL — Güvenlik bildirimi e-postası (MAIL-03, MAIL-04)

Durum: IMPLEMENTED (aynı kısıtlar: production SMTP PENDING)
Sorumlu: Kerem
Tüketici servisler: Auth

#### Endpoint
POST /internal/v1/mail/security-notifications

#### Yetkilendirme
Internal. Doğrulama kodu ucu ile aynı (`scope=mail:send`).

#### Request Body
```json
{
  "idempotencyKey": "UUID",
  "accountId": "UUID",
  "recipientEmail": "string ≤254, geçerli e-posta",
  "type": "PASSWORD_CHANGED | PASSWORD_RESET | EMAIL_CHANGED | ACCOUNT_SUSPENDED | ACCOUNT_BANNED | ACCOUNT_DISABLED | ACCOUNT_REACTIVATED",
  "occurredAt": "ISO-8601 (gelecekte olamaz)",
  "locale": "tr | en (opsiyonel)"
}
```
Bildirim parola, kod, bağlantı veya gerekçe metni taşımaz; yalnız ne olduğunu ve ne zaman olduğunu söyler. `EMAIL_CHANGED` eski adrese gönderilir. Süresi geçmiş (24 saatten eski) bildirim gönderilmez.

#### Response — 202
Gövde yok.

#### Hata Durumları
- 400: Doğrulama; `occurredAt` kabul edilemez (`INVALID_OCCURRED_AT`)
- 401, 403, 409, 413, 503: Doğrulama kodu ucu ile aynı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth → Mail. Auth tarafı `HttpMailServiceAdapter` **IMPLEMENTED** (Mailpit ile uçtan uca doğrulandı, 2026-10-31).

#### Test Durumu
INTEGRATION (`MailDeliveryControllerTest`)

### MAIL — Hesap verisini sil (silme akışı)

Durum: IMPLEMENTED
Sorumlu: Kerem
Tüketici servisler: Auth

#### Endpoint
DELETE /internal/v1/accounts/{accountId}

#### Yetkilendirme
Internal. `svc+jwt`, `aud=mail-service`, `sub=svc:auth-service`, `scope=account:erase`, `account_id` = yoldaki `accountId`.

#### Path Parameters
- accountId: UUID, zorunlu

#### Response — 204
Gövde yok. İdempotenttir: hesaba ait kayıt olmasa da `204` döner. Hesabın kuyruktaki e-postaları ve gönderim geçmişi silinir.

#### Hata Durumları
- 400: `accountId` UUID değil (`MALFORMED_REQUEST`)
- 401: Token yok/geçersiz
- 403: Çağıran `svc:auth-service` değil, scope `account:erase` değil veya `account_id` ≠ yol (hiçbir şey silinmez)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth → Mail, RestClient (`HttpAccountDataEraser` deseni). Auth'un silme işçisi bu ucu çağıracak şekilde yapılandırılmalıdır.

#### Test Durumu
INTEGRATION (`AccountErasureTest`)

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

### GAME — Hesap verisi silme iç ucu

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: auth-service

#### Endpoint
DELETE /internal/v1/accounts/{accountId}

#### Yetkilendirme
Internal; Gateway'e yönlendirilmez. RS256/Auth JWKS, tam `typ=svc+jwt`,
`iss=ltz-auth` (JWT_ISSUER), tek `aud=game-service`, dolu `kid/jti`,
ham `iat`, `nbf/exp`, pozitif ve en çok 60 saniye ömür, en çok 30 saniye saat toleransı.
Yalnız `SCOPE_account:erase`; tam `sub=svc:auth-service` ve
`account_id` = yol UUID'si zorunlu. Kullanıcı token'ı (ADMIN dahil) iç uçta,
servis token'ı kullanıcı namespace'inde reddedilir. Servis rollerinden yetki türetilmez.

#### Path Parameters
- accountId: UUID, zorunlu; token'ın account_id claim'iyle eşleşmeli.

#### Request Body
Yok.

#### Response — 204
Gövde yok. Hesabın geliştirici içerikleri, medya bağlantıları, güncelleme notları,
özel feedback ve kendi grant satırları silinir. Editör/moderatör REF'leri NULL;
audit/alan kilidi ve başkasının grant'ındaki ilgili aktörler nil UUID olur.
Revoke edilmiş grant'ta revoked_by nil olur; revoked_at korunur (CHECK).
İlgili nullable reason/details metinleri NULL; NOT NULL evidence sabit
`[silinen hesap: metin kaldırıldı]` olur. Hedef kişi audit'leri de kapsanır;
başka hesapların aktörleri/verileri korunur. Regex ile metin temizlenmez.
V171'in kontrollü DB fonksiyonu tek transaction'da çalışır; son REF kontrolü ve
commit tamamlanmadan 204 verilmez. Veri yok/tekrar çağrı idempotent 204 döner.

#### Hata Durumları
- 400: Geçersiz yol UUID'si.
- 401: Kimlik yok, kullanıcı token'ı, geçersiz imza/issuer/audience/tür/süre veya eksik token claim'i.
- 403: Silme kapsamı yok, çağıran Auth değil veya account_id eksik/yoldaki hesaptan farklı.
- 503: ACCOUNT_ERASURE_PENDING — kilit/zaman aşımı gibi geçici hata veya eksik temizlik; transaction geri alınır.
- 500: Beklenmeyen sunucu hatası; silme onayı değildir.

#### Bağlantı
Frontend: Yok. Gateway route'u eklenmedi.
Diğer mikroservisler: Auth mevcut GAME_SERVICE_URL üzerinden RestClient ile doğrudan çağırır.
Auth yalnız 200/204'ü onaylar; bu uç gövdesiz 204 kullanır. Yeni ENV yok.
Karar raporu ve onaylı politika: `docs-Game Service/LTZ-HAM-GAME-04-Auth-Entegrasyonu.md` §13–§14.
Migration: V171; ayrı runtime DB rolü varsa yalnız `erase_game_account_data(uuid)`
EXECUTE yetkisi tahsis edilir, özel bağlam tablosuna DML verilmez.

#### Test Durumu
UNIT + INTEGRATION — AccountErasureTest, ServiceTokenDecoderConfigurationTest.
RealAuthIntegrationTest gerçek Auth ServiceTokenIssuer/JWKS uyumluluğunu sınar.
AccountErasureMigrationTest gerçek PostgreSQL'de silme/anonimleştirme, V40 yükseltmesi,
çapraz hesap izolasyonu, normal audit değişmezliği, DB yetkisi, rollback ve kilit tekrarını sınar.
DEV Auth görevinin CONFIRMED olması NOT VERIFIED.

## LIBRARY

Sorumlu: Hamza · Gateway: `/api/v1/library/**` · Görevler: LIB-

### LIBRARY — Profil koleksiyonunu listeleme (LIB-10, LIB-11)

Durum: IMPLEMENTED (Library tarafı); User görünürlük adaptörü PENDING, dış erişim fail-closed.
Sorumlu: Hamza
Tüketici servisler: Frontend / User profil akışı

#### Endpoint
GET /api/v1/library/users/{userId}/entries

#### Yetkilendirme
Authenticated; kanonik kullanıcı rolü ve mevcut at+jwt doğrulaması.
Görüntüleyen yalnız JWT sub'dır; viewerId/userId query veya kimlik başlığı yetki vermez.
Owner kendi verisine erişir. Başka owner için LibraryVisibilityPort açık ALLOW vermelidir.
Üretim adaptörü User API'si bulunmadığından UNAVAILABLE döner; dış veri açılmaz.
ADMIN rolü gizlilik denetimini atlamaz. Anonim profil koleksiyonu erişimi sunulmaz.
User, kullanıcı isteğinde o kullanıcının kendi at+jwt'sini bu uca iletir (Auth karar K1); token
saklanmaz ve arka planda kullanılmaz (User'ın sorumluluğu). Ayrı delegasyon token'ı yoktur.

#### Path/Query Parameters
userId: hedef UUID. page: >=0, varsayılan 0; size: 1..50, varsayılan 20.
Sıra addedAt DESC, id DESC; bu uçta filtre/ad sırası parametresi yoktur.

#### Request Body
Yok.

#### Response — 200
LibraryCollectionPageResponse: items[].entry, items[].game, page, size, totalElements,
metadataPartial. Mevcut kişisel liste ile aynı DTO; Cache-Control: no-store.
Görünürlük denetimi DB sayımı/listeleme ve Game çağrısından önce yapılır.

#### Hata Durumları
400: geçersiz UUID/page/size. 401: kimlik yok/geçersiz. 403: rol yok veya görünür değil.
503: karar alınamıyor, null karar veya adaptör hatası. ProblemDetail; veri/sayaç dönmez.
Olmayan/gizli kullanıcı için özel varlık sorgusu veya ayırt edici ret açıklaması üretilmez.

#### Bağlantı
Gateway üzerinden. Görünürlük User'ın sorumluluğundadır; henüz HTTP adaptörü yoktur.
Karar cache'lenmez. ALLOW sonrası mevcut Library liste/Game batch akışı kullanılır.

#### Test Durumu
UNIT + INTEGRATION — LibraryApiTest.PrivacyTests ve LibraryAccessServiceTest.
İzinli/ret/ulaşılamama/null/adaptör hatası, owner, ADMIN, kimlik sahteciliği ve sayaç sızıntısı.
İzinli dış erişim yalnız test portuyla sınandı; gerçek User entegrasyonu NOT VERIFIED/PENDING.

### LIBRARY — Profil koleksiyon sayacı (LIB-10, LIB-11)

Durum: IMPLEMENTED (Library tarafı); User görünürlük adaptörü PENDING.
Sorumlu: Hamza
Tüketici servisler: Frontend / User profil akışı

#### Endpoint
GET /api/v1/library/users/{userId}/entries/statistics

#### Yetkilendirme
Profil koleksiyon listesiyle aynı at+jwt/rol/owner ve açık görünürlük izni denetimi.
Sayaçlar gizli koleksiyonu açığa çıkaramaz; üretimde non-owner erişimi 503 ile kapanır.

#### Path/Query Parameters
userId: UUID. Query parametresi yok.

#### Request Body
Yok.

#### Response — 200
LibraryStatisticsResponse: totalGames, favoriteGames, completedGames. Cache-Control: no-store.

#### Hata Durumları
400: UUID. 401: kimlik. 403: rol/ret. 503: görünürlük kararı alınamıyor. ProblemDetail.

#### Bağlantı
Gateway; izin sonrası yalnız Library aggregate. Game çağrısı yok. Liste ve sayaç ayrı
HTTP istekleridir; ikisi arasında ortak snapshot veya dağıtık gizlilik kilidi vaat edilmez.

#### Test Durumu
INTEGRATION — PrivacyTests: owner sayacı, izinli hedef izolasyonu, ret/ulaşılamama,
doğrulanmış viewer kullanımı. Gerçek User entegrasyonu PENDING.

### LIBRARY — User servisinin profil okuması (LIB-10, LIB-11)

Durum: DEPRECATED (karar K1, 2026-10-30). Kod yerinde ve kapalıdır; kullanılmaz, kaldırılması ayrı bir temizlik işidir.
Sorumlu: Hamza
Tüketici servisler: yok. User, kullanıcının kendi at+jwt'siyle yukarıdaki public profil uçlarını çağırır.

Auth (Kerem) görüntüleyeni taşıyan delegasyon veya library:profile:read kapsamlı token üretmez
(docs-auth/verilen-cevaplar/library-service/talep-01-servisler-arasi-yetki-iptal-ve-d4.md K1).
Bu iç uçlara gerçek token verilemediği için fail-closed kalır; yeni tüketici eklenmez.

#### Endpoint
GET /internal/v1/library/users/{userId}/entries
GET /internal/v1/library/users/{userId}/entries/statistics

#### Yetkilendirme
Internal; RS256/JWKS svc+jwt, issuer, tek aud=library-service, kid/jti/iat/nbf/exp ve
en fazla 60 saniye ömür; mevcut 30 saniye saat toleransı. Scope library:profile:read,
sub tam svc:user-service, imzalı target_user_id yoldaki kanonik UUID ile aynı olmalıdır.
viewer_id imzalı kanonik UUID claim'i zorunludur; query/header görüntüleyen seçemez.
Viewer=owner ise kişisel erişim; aksi halde PROFILE amacıyla açık görünürlük izni gerekir.
Mevcut Auth bu scope/delegasyon claim'lerini üretmez. D4 account:erase yetkisi kabul edilmez.

#### Path/Query Parameters
userId: UUID. Liste: page>=0 varsayılan 0, size=1..50 varsayılan 20.
İstatistik: query yok.

#### Request Body
Yok.

#### Response — 200
Liste: LibraryCollectionPageResponse; sayaç: LibraryStatisticsResponse.
Cache-Control: no-store. Public profil uçlarıyla aynı veri ve gizlilik sınırı.

#### Hata Durumları
400: parametreler. 401: token yok/geçersiz, yanlış typ/aud/issuer/zaman.
403: scope/sub/target/viewer geçersiz veya görünürlük reddi. 503: karar alınamıyor.
ProblemDetail. Token hatasında veri/görünürlük portu çağrılmaz.

#### Bağlantı
Yalnız servis iç ağı; Gateway iç yolları yönlendirmez. User tüketici adaptörü ve Auth
istemci/hedef/kapsam/delegasyon desteği uygulanmadı. Gerekenler ayrı bağımlılık raporunda.

#### Test Durumu
INTEGRATION — gerçek RSA/JWKS ve PostgreSQL; yerelde üretilmiş test token'larıyla
sub/scope/target/viewer pozitif-negatif sınanır. Gerçek token edinme ve User E2E PENDING.

### LIBRARY — Recommendation girdileri (LIB-14)

Durum: IMPLEMENTED (Library kabul sınırı, Auth signals:read sözleşmesine uyarlandı); User izin kararı ve Recommendation tüketicisi PENDING.
Sorumlu: Hamza
Tüketici servisler: recommendation-service

#### Endpoint
GET /internal/v1/library/users/{userId}/recommendation-signals

#### Yetkilendirme
Internal; Auth'un yayımlanmış hesaba bağlı servis token'ı (talep-01 K2): RS256/JWKS, typ=svc+jwt,
iss=ltz-auth, tek aud=library-service, kid/jti/iat/nbf/exp, en fazla 60 sn ömür, 30 sn saat toleransı.
sub tam svc:recommendation-service; scope signals:read; account_id yoldaki {userId} ile kanonik
UUID olarak aynı olmalıdır (eşleşmezse veya yoksa 403). Eski library:recommendation:read ve
target_user_id artık kabul edilmez. Kullanıcı at+jwt'si, D4 account:erase token'ı ve başka
servislerin token'ları kabul edilmez. Ardından RECOMMENDATION amacıyla User'dan ayrı açık izin
gerekir: Auth hesabın ACTIVE olduğunu denetler, kullanıcı iznini kanıtlamaz. Owner eşitliği bu amacı
atlatmaz; PUBLIC profil tercihi kendi başına öneri kullanım izni sayılmaz. Üretim görünürlük
adaptörü User karar API'si olmadığı için 503 ile kapalıdır.

#### Path/Query Parameters
userId UUID. page>=0 varsayılan 0; size=1..50 varsayılan 20.
addedAt DESC, id DESC. Yalnız favorite OR completed olan satırlar; toplam aynı filtrededir.

#### Request Body
Yok.

#### Response — 200
```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0 }
```
items[] yalnız gameId, favorite, completed içerir. Yerel kayıt UUID'si, kullanıcı kimliği,
tarih/version veya Game metadata'sı yoktur. Liste/toplam REPEATABLE_READ; no-store.

#### Hata Durumları
400: UUID/page/size. 401: kimlik/token sözleşmesi. 403: kapsam/caller/hedef/izin reddi.
503: izin kararı alınamıyor. ProblemDetail; özel kayıt ve sayaç dönmez.

#### Bağlantı
Doğrudan Library iç ağı; Game/diğer DB çağrısı yoktur. Auth tarafı hazırdır (PR #36, origin/main):
Recommendation, POST /internal/v1/service-tokens ile clientId=recommendation-service, audience=library-service,
accountId=<hesap UUID> göndererek token alır. Recommendation istemcisi ve User izin kararı henüz yoktur;
kullanılabilir üretim entegrasyonu olarak değerlendirilmez.

#### Test Durumu
INTEGRATION — LibraryApiTest.PrivacyTests: 3 sinyal/2 sayfa, her iki bayrak, işaretsiz kayıt ve başka
owner dışlama, alan azaltma, yüksek offset, parametre sınırları, ret/ulaşılamama/izin iptali; signals:read
sınırları (yanlış sub/scope/account_id, eksik veya kanonik olmayan account_id, süresi dolmuş, yanlış aud/typ,
eski taslak kapsamı, D4 ve sinyal token'ının çapraz kullanılamaması). AuthIssuedServiceTokenIntegrationTest:
Auth'un gerçek ServiceClientTokenIssuer çıktısı Library'nin gerçek HTTP/PostgreSQL katmanına karşı
(User kararı SAHTE). Gerçek Recommendation ve gerçek User izin kararı E2E'si PENDING.

### LIBRARY — Kütüphane listeleme (LIB-05, LIB-06, LIB-07, LIB-08, LIB-16, LIB-17)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/library/entries

#### Yetkilendirme
Authenticated + Owner. Mevcut RS256/JWKS kullanıcı token'ı ve kanonik rol sözleşmesi;
yalnız JWT sub sahibinin koleksiyon satırları. userId query/header sahip seçemez.

#### Path/Query Parameters
- page: integer, minimum 0, varsayılan 0.
- size: integer, 1–50, varsayılan 20.
- favorite: isteğe bağlı boolean. Yoksa bu bayrak filtrelenmez; true işaretlileri, false işaretsizleri seçer.
- completed: isteğe bağlı boolean; aynı davranış.
İki filtre birlikte AND uygulanır; false değerleri filtre yokmuş gibi değerlendirilmez.
- sort: ADDED_AT (varsayılan) veya TITLE.
- direction: ASC veya DESC (varsayılan).
ADDED_AT sıralamasında id aynı yönde ikincil anahtardır. TITLE, filtrelenmiş bütün
küme batch ile zenginleştirildikten sonra, sayfalama öncesinde sıralanır; yalnız sayfa sıralanmaz.
Adlar Unicode NFKC + Locale.ROOT küçük harf anahtarıyla karşılaştırılır; eşit adlarda id ASC.
Eksik adlar her iki yönde en sondadır, kendi aralarında id ASC. Ad sıralamasında herhangi
bir özet eksikse metadataPartial tüm filtrelenmiş küme üzerinden true olur.
LIB-17: Son bilinen snapshot adı varsa TITLE sırasına bu ad katılır; metadataSource
LAST_KNOWN olarak işaretlenir. Hiç bilinen adı olmayanlar en sonda kalır.
Sonuç dışı sayfa boş items ve doğru totalElements ile 200 döner; metadata HTTP çağrısı
yapılmaz, metadataPartial=false olur. page hesabı long olduğundan integer çarpım taşması yoktur.
Liste/toplam aynı yerel snapshot'tandır; ayrı HTTP sayfa istekleri arasında veri değişirse
sonuç kümesi de değişebilir. Faz 2 favori/bitirildi listelerinin düz DTO sözleşmesi korunur.

#### Request Body
Yok.

#### Response — 200
```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0, "metadataPartial": false }
```
items[].entry mevcut LibraryEntryResponse'tur; koleksiyon/favori/bitirildi korunur.
items[].game: gameId, title, coverUrl, origin, metadataCompleteness, gameType, status,
metadataSource, metadataRefreshedAt.
status: AVAILABLE, UNAVAILABLE veya TEMPORARILY_UNAVAILABLE. Eksik ad/görsel null;
arşivlenmiş/MERGED/olmayan oyun ayırt edilmez. Koleksiyon kaydı silinmez.
metadataSource: LIVE (bu istekte doğrulanan kart), LAST_KNOWN (son bilinen public
snapshot) veya NONE (snapshot da yok). metadataRefreshedAt, LIVE için bu batch'in
başarılı gözlem zamanı; LAST_KNOWN için snapshot'ın özgün zamanı; NONE için null'dır.
Zaman Game sağlayıcısının güncelleme zamanı değildir. LAST_KNOWN metadata güncel katalog
durumu veya tekrar ekleme izni sayılmaz; status güncel batch sonucunu anlatır.
Game erişilebilir olsa da title eksikse mevcut snapshot kullanılır; status AVAILABLE,
metadataSource LAST_KNOWN ve metadataPartial true döner. Snapshot da yoksa alanlar null'dır.
metadataPartial, bilinmeyen ad, kullanılamayan özet veya LAST_KNOWN kullanımı varsa true'dur;
TITLE için tüm filtrelenmiş küme, ADDED_AT için dönen sayfa değerlendirilir.

#### Hata Durumları
- 400: Geçersiz page/size, sınır aşımı, boolean filtre, sort veya direction.
- 401: Kimlik yok/geçersiz kullanıcı token'ı.
- 403: Kanonik kullanıcı rolü yok.
- 500: Beklenmeyen yerel hata; ProblemDetail.
Game hatası listeyi düşürmez: 200 ve açık metadataPartial/status fallback'i kullanılır.

#### Bağlantı
Frontend Gateway üzerinden. Library DB liste/toplam snapshot'ı kapandıktan sonra
mevcut Game public batch API'si çağrılır. ADDED_AT sıralamasında yalnız dönen sayfa
zenginleştirilir: en fazla 50 oyun için tek batch. TITLE sıralamasında filtrelenmiş K
kaydın tamamı gerekir: her istekte en fazla 50 farklı kimlik, en fazla ceil(K/50) batch.
İstekler sıralıdır; ilk geçici hatadan sonra kalan batch'ler çağrılmaz, önceki başarılı
özetler korunur. N+1 yoktur. TITLE bellek maliyeti O(K), sıralama maliyeti O(K log K)'dır.
Yerel snapshot ile ayrı Game batch yanıtları arasında dağıtık snapshot garantisi yoktur.
Boş/256 karakterden uzun ad, geçersiz/HTTPS olmayan/2048 karakterden uzun görsel URL'si
ve tanınmayan opsiyonel enum alanları null döner; sahte metadata üretilmez.
429/timeout/bozuk yanıt otomatik yeniden denenmez; kişisel token dış servise aktarılmaz.
V42__create_game_snapshots.sql yalnız library_db'ye public metadata tablosunu ekler;
V41 değişmez. Kişisel satırlara veya başka servis DB'sine FK yoktur. Game HTTP çağrısı
yerel transaction dışında kalır; snapshot okuma/yazma ayrı, kısa yerel transaction'lardır.
Başarılı ve geçerli title içeren kartlar snapshot'ı yeniler; eksik title, unavailable,
429/timeout/bozuk yanıt son bilinen kaydı ezmez. Daha eski/eşit gözlem snapshot'ı geri alamaz.
Snapshot aktif Game doğrulamasını atlatmaz; yeni ekleme/favori/bitirildi PUT hâlâ Game'i
doğrular. DELETE/işaret geri alma dış katalog gerektirmez. Koleksiyon satırı id/bayrak/
tarih/version alanları okumadan etkilenmez. Snapshot kullanıcıdan bağımsız public veridir;
hesap/koleksiyon silinince başka kullanıcıların da yararlanabileceği snapshot silinmez.
Katalog event tüketimi veya otomatik MERGED kimlik yönlendirmesi eklenmedi; mevcut batch
sözleşmesi yeni kanonik kimliği/ayrıntılı kullanılamama nedenini sağlamaz.

#### Test Durumu
UNIT + INTEGRATION — LibraryApiTest.CollectionTests, GameServiceClientTest,
LibraryGameSummariesTest: owner izolasyonu, boolean AND/false filtreleri, global ad sırası,
kararlı sayfalama, eşzamanlı liste/toplam snapshot'ı, 50/121 oyun batch sınırları,
bozuk/eksik/çelişkili yanıtlar, timeout/429 fallback ve kayıtların korunması.
Faz 3 clean verify: 78 test PASS, LINE %98,84; Checkstyle/PMD 0 ihlal.
ltz-dev Library/Gateway anonim ve geçersiz kimlik HTTP kontrolleri PASS.
Pozitif canlı kullanıcı E2E NOT VERIFIED; başka serviste test verisi oluşturulmadı.
LIB-17 — CatalogResilienceTests gerçek PostgreSQL ve GameServiceClient ile, simüle Game
HTTP yanıtlarında available→unavailable→timeout→eksik title→yeniden available geçişini;
snapshot yokluğunu, bozuk batch'i, güvenli mutasyonları, fallback global sırasını,
eşzamanlı yenilemeyi ve V41→V42 veri korumalı yükseltmesini sınar. Gerçek Game arşivleme
E2E'si değildir. LibraryGameSummariesTest sonraki atlanan batch'lerin snapshot fallback'ini sınar.
Faz 5 clean verify: 100 PASS, LINE %99,09; Checkstyle/PMD 0 ihlal. ltz-dev V42
uygulaması ve 34 HTTP sağlık/güvenlik/public Game batch kontrolü PASS; pozitif canlı
snapshot/arşivleme akışı NOT VERIFIED.

### LIBRARY — Kütüphane istatistikleri (LIB-09)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/library/entries/statistics

#### Yetkilendirme
Authenticated + Owner. Aynı kullanıcı JWT/kanonik rol sözleşmesi; yalnız JWT sub.
userId query/header başka kullanıcı seçemez. Profil/Recommendation erişimi bu uca eklenmez.

#### Path/Query Parameters
Yok. Liste filtreleri istatistiğe uygulanmaz; bütün kişisel koleksiyon sayılır.

#### Request Body
Yok.

#### Response — 200
```json
{ "totalGames": 0, "favoriteGames": 0, "completedGames": 0 }
```
Tek SQL aggregate aynı snapshot'ta hesaplar. Aynı oyun iki bayrağı da true ise toplamda
bir kez, favori ve bitirildi sayılarında ayrı ayrı bir kez sayılır. İdempotent tekrar
sayıları artırmaz; iki bayrak false olsa da koleksiyon toplamına dahildir.

#### Hata Durumları
- 401: Kimlik yok/geçersiz kullanıcı token'ı.
- 403: Kanonik kullanıcı rolü yok.
- 500: Beklenmeyen yerel hata; ProblemDetail.

#### Bağlantı
Frontend Gateway üzerinden; yalnız library_db aggregate sorgusu. Game HTTP çağrısı yoktur.

#### Test Durumu
INTEGRATION — boş koleksiyon, çakışan bayraklar, idempotent tekrar, geri alma/silme,
başka kullanıcı izolasyonu ve 401/403; LibraryApiTest.

### LIBRARY — Koleksiyona oyun ekleme (LIB-01, LIB-12, LIB-13)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/library/entries

#### Yetkilendirme
RS256/Auth JWKS ile `typ=at+jwt`, issuer, audience, zamanlar ve UUID `sub` doğrulanır.
Kanonik rollerden biri gerekir: PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN.
Kullanıcı yalnız doğrulanmış `sub` üzerinden belirlenir; `userId` gövde alanı reddedilir.
Servis yalnız Bearer kabul eder; cookie→Bearer ve cookie CSRF/Origin kontrolü Gateway'dedir.

#### Path/Query Parameters
Yok. Query veya kimlik başlıkları kullanıcı kimliğini belirlemez.

#### Request Body
```json
{ "gameId": "00000000-0000-4000-8000-000000000001" }
```
Zorunlu UUID; bilinmeyen gövde alanları reddedilir.

#### Response — 200
İlk ekleme ve tekrar aynı HTTP durumunu kullanır. `id`, `gameId`, `inCollection`,
`favorite`, `completed`, `addedAt`, `updatedAt`, `version` döner.
Yeni kayıt: `inCollection=true`, `favorite=false`, `completed=false`, `version=0`.
Tekrar ekleme mevcut kimliği, tarihleri ve bayrakları korur; oyun sahipliği kanıtı değildir.
DB `uq_library_entries_user_game` gerçek tekillik garantisidir; atomik PostgreSQL upsert kullanılır.

#### Hata Durumları
- 400: Geçersiz/eksik UUID, bozuk JSON veya bilinmeyen alan.
- 401: Kimlik yok veya token geçersiz (servis token'ı dahil).
- 403: Geçerli token'da izin verilen rol yok.
- 404: Game batch sözleşmesinde oyun `unavailable` (olmayan/arşivlenmiş/birleştirilmiş).
- 503: Game Service timeout/kesinti/429/hatalı veya tutarsız yanıt; kayıt oluşturulmaz.
- 500: Beklenmeyen sunucu hatası. Tüm hatalar ProblemDetail biçimindedir.

#### Bağlantı
Frontend Gateway üzerinden aynı path'i çağırır. Library, transaction dışında mevcut
`GAME_SERVICE_URL` üzerinden public `POST /api/v1/game/games/batch` sözleşmesini
tek `gameId` ile tüketir; token aktarımı ve otomatik retry yoktur.
Migration: `V41__create_library_entries.sql`; yalnız library_db. Yeni ENV yoktur.

#### Test Durumu
UNIT + INTEGRATION — LibraryApiTest: gerçek PostgreSQL/Flyway/JPA, RSA/JWKS,
16 eşzamanlı ekleme ve doğrudan SQL tekillik testi. GameServiceClientTest:
MockRestServiceServer ile sözleşme, kesinti, timeout, 429 ve tutarsız yanıtlar.
Gerçek Auth issuer/Gateway filtre sözleşmesi sınandı. 30 Ekim 2026 son kontrolünde
iki sentetik hesabın gerçek kayıt/doğrulama/login oturumu ile Auth→Gateway→Library→Game→DB
E2E akışı 62/62 PASS: ekleme/tekrar, iki kullanıcı izolasyonu, otomatik bayraklar,
atomik silme, geçersiz oyun/JWT/CSRF ve gerçek Game kesintisinde 503/no-write.
Kanıt: `docs-library/LTZ-HAM-LIB-01-E2E-Sonuc.md`; tekrar: `node ops/library-phase1-e2e.mjs`.
Kullanıcı JWT doğrulayıcısı ayrıca UserTokenDecoderConfigurationTest ile gerçek RS256/JWKS üzerinde
(bozuk imza, alg=none, HS256 karışıklığı, bilinmeyen/eksik kid, anahtar rotasyonu örtüşmesi, süre, issuer/audience, rol)
sınanır. Auth→Library kullanıcı JWT entegrasyonunun canlı negatif/sahiplik doğrulaması:
`docs-library/LTZ-HAM-LIB-06-Auth-Entegrasyonu-Sonuc.md`.
Maven clean verify 45 test PASS, LINE %98,13. Pre-push/SonarQube kullanıcı tarafından manuel yapılır.
Yeni imaj ltz-dev üzerinde çalıştırıldı; Library/Gateway gerçek HTTP health ve
anonim/geçersiz Bearer/sahte kimlik/CSRF kontrolleri 15/15 başarılı. Library
konteynerinden Auth JWKS erişimi HTTP 200. Game public batch gerçek HTTP 200.

### LIBRARY — Koleksiyondan oyun kaldırma (LIB-02)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/library/entries/{gameId}

#### Yetkilendirme
Koleksiyona ekleme ile aynı kullanıcı JWT/rol sözleşmesi. Silme SQL'i hem doğrulanmış
`sub=user_id` hem `game_id` ile sınırlıdır; başka kullanıcı adına silme yolu yoktur.

#### Path/Query Parameters
- gameId: zorunlu UUID. Query kimlik kaynağı değildir.

#### Request Body
Yok.

#### Response — 204
Gövde yok. Kayıt yoksa veya tekrar silinirse de 204. Koleksiyon/favori/bitirildi tek
satırda tutulur ve tek transaction'da fiziksel silinir. Başkasındaki aynı oyun korunur.
Game Service çağrısı gerekmez; katalog kesintisi veya arşivleme kaldırmayı engellemez.

#### Hata Durumları
- 400: Geçersiz yol UUID'si.
- 401: Kimlik yok/geçersiz JWT.
- 403: Gerekli kullanıcı rolü yok.
- 500: Beklenmeyen sunucu hatası; ProblemDetail.

#### Bağlantı
Frontend Gateway üzerinden aynı path; yalnız library_db transaction'ı.

#### Test Durumu
INTEGRATION — LibraryApiTest: gerçek PostgreSQL üzerinde iki kullanıcı izolasyonu,
iki bayrakla birlikte silme, tekrar, anonim erişim ve geçersiz UUID.

### LIBRARY — Favori işaretiyle otomatik koleksiyon kaydı (LIB-03, LIB-15)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/library/entries/{gameId}/favorite

#### Yetkilendirme
Koleksiyona ekleme ile aynı JWT/rol/sahiplik sözleşmesi; kullanıcı yalnız `sub`'dan.

#### Path/Query Parameters
- gameId: zorunlu UUID. Query parametresi kullanılmaz.

#### Request Body
Yok. Bu uç yalnız işareti açar; `false` kabul eden bir durum güncelleme API'si değildir.

#### Response — 200
Koleksiyona ekleme ile aynı LibraryEntryResponse. Eksik kayıt oluşturulurken
`inCollection=true`, `favorite=true`, `completed=false`. Var olan kaydın completed
alanı korunur. Tekrar aynı id/tarih/version değerlerini korur. Gerçek değişiklikte
updatedAt ve version ilerler. Benzersiz upsert, ekleme/bitirildi yarışında işaret kaybetmez.
Favoriden çıkarma ve favori listesi aşağıdaki LIB-03 uçlarıyla sunulur.

#### Hata Durumları
- 400: Geçersiz yol UUID'si.
- 401: Kimlik yok/geçersiz kullanıcı token'ı.
- 403: İzin verilen kullanıcı rolü yok.
- 404: Game Service oyunu kullanılabilir olarak doğrulamadı.
- 503: Oyun doğrulaması kesinti/timeout/429/tutarsız yanıt nedeniyle tamamlanamadı.
- 500: Beklenmeyen hata. Yanıtlar ProblemDetail.

#### Bağlantı
Frontend Gateway üzerinden. Game batch doğrulaması transaction dışında; yalnız
library_db upsert'i atomiktir. HTTP çağrısı dağıtık transaction değildir.
Silme ile eşzamanlı işaretlemede DB işlem sırası geçerlidir: silmeden sonraki
başarılı işaretleme yeniden kayıt oluşturabilir; silmeden önceki işaretler silinir.

#### Test Durumu
INTEGRATION — hedef bayrak, tekrar, 24 karma eşzamanlı istek, geçersiz oyun,
katalog kesintisi, 401/403; gerçek PostgreSQL/JWT. Game adaptörü ayrıca mock HTTP ile sınandı.

### LIBRARY — Favoriden çıkarma (LIB-03)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/library/entries/{gameId}/favorite

#### Yetkilendirme
Authenticated + Owner. Mevcut kullanıcı JWT/rol sözleşmesi geçerlidir; user_id yalnız
doğrulanmış sub'dan alınır. ADMIN dahil hiçbir rol başka kullanıcı adına işlem yapamaz.

#### Path/Query Parameters
- gameId: zorunlu UUID. Query/kimlik başlıkları sahip belirlemez.

#### Request Body
Yok.

#### Response — 204
Gövdesiz. Yalnız favorite=false yapılır; koleksiyon, completed, id ve addedAt korunur.
Gerçek değişiklikte updatedAt/version ilerler. Zaten false/olmayan kayıt ve tekrarda 204;
satır oluşturulmaz, timestamp/version değiştirilmez. Game doğrulaması gerekmez.
Tek atomik UPDATE, aynı kayıttaki eşzamanlı işlemlerle DB kilidi altında sıralanır.

#### Hata Durumları
- 400: Geçersiz yol UUID'si.
- 401: Kimlik yok/geçersiz kullanıcı token'ı.
- 403: Geçerli kullanıcı token'ında kanonik rol yok.
- 500: Beklenmeyen hata; ProblemDetail.

#### Bağlantı
Frontend Gateway üzerinden aynı path. Yalnız library_db; dış servis çağrısı yoktur.

#### Test Durumu
INTEGRATION — LibraryApiTest: gerçek PostgreSQL/JWT ile korunan bayrak/koleksiyon,
tekrar, olmayan kayıt, kullanıcı izolasyonu, 401/403/400 ve 16 eşzamanlı aç/kapat.

### LIBRARY — Kendi favorilerini listeleme (LIB-03)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/library/entries/favorites

#### Yetkilendirme
Authenticated + Owner; yalnız JWT sub sahibinin favorite=true kayıtları.
İstemcinin userId query/header değeri başka koleksiyona erişim vermez.

#### Path/Query Parameters
- page: integer, varsayılan 0, minimum 0; sıfır tabanlı.
- size: integer, varsayılan 20, 1–50.
Sabit sıra addedAt DESC, id DESC. Büyük page değeri taşma üretmez; boş sayfada da toplam döner.

#### Request Body
Yok.

#### Response — 200
```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0 }
```
items, mevcut LibraryEntryResponse kayıtlarıdır; gameId, koleksiyon ve iki bayrak,
id, addedAt, updatedAt ve version içerir. Kişisel userId veya oyun metadata'sı dönmez.
Liste/toplam aynı REPEATABLE_READ snapshot'ında iki sınırlı SQL sorgusuyla okunur.
Bu uç düz favori listesidir; filtre/sıralama ve oyun özetleri GET /api/v1/library/entries ucundadır.

#### Hata Durumları
- 400: Geçersiz page/size veya size sınırının aşılması.
- 401: Kimlik yok/geçersiz kullanıcı token'ı.
- 403: Kanonik kullanıcı rolü yok.
- 500: Beklenmeyen hata; ProblemDetail.

#### Bağlantı
Frontend Gateway üzerinden. Yalnız library_db okunur; Game HTTP çağrısı/N+1 yoktur.
Katalog kesintisi mevcut kayıtların listelenmesini veya favoriden çıkarılmasını engellemez.

#### Test Durumu
INTEGRATION — LibraryApiTest: yalnız owner/favorite, aynı tarihli kayıtlarda stabil sıra,
boş ve sınır dışı sayfa, size limiti, yüksek offset, 401/403/400 ve katalogdan bağımsızlık.
Faz 2 canlı pozitif E2E NOT VERIFIED. ltz-dev üzerinde doğrudan servis/Gateway
sağlık, 401/403, sahte kimlik, geçersiz Bearer/cookie ve CSRF kontrolleri 32/32 PASS.
Tam modül Maven clean verify: 58 test, 0 failure/error/skipped, LINE %98,40;
gerçek Auth issuer/Gateway cookie-CSRF filtreleri yeni GET/DELETE uçlarıyla testlidir.

### LIBRARY — Bitirildi işaretiyle otomatik koleksiyon kaydı (LIB-04, LIB-15)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/library/entries/{gameId}/completed

#### Yetkilendirme
Koleksiyona ekleme ile aynı JWT/rol/sahiplik sözleşmesi; kullanıcı yalnız `sub`'dan.

#### Path/Query Parameters
- gameId: zorunlu UUID. Query parametresi kullanılmaz.

#### Request Body
Yok. Yalnız bitirildi işaretini açar.

#### Response — 200
LibraryEntryResponse. Yeni kayıtta `inCollection=true`, `favorite=false`,
`completed=true`. Mevcut favorite korunur. Tekrar kimlik/tarih/version değiştirmez.
Gerçek değişiklik updatedAt/version ilerletir. Aynı benzersiz upsert mekanizması.
Bitirildi durumunu geri alma ve listeleme aşağıdaki LIB-04 uçlarıyla sunulur.

#### Hata Durumları
- 400: Geçersiz yol UUID'si.
- 401: Kimlik yok/geçersiz kullanıcı token'ı.
- 403: İzin verilen kullanıcı rolü yok.
- 404: Kullanılabilir oyun bulunamadı.
- 503: Game Service doğrulaması tamamlanamadı; yeni kayıt yok.
- 500: Beklenmeyen hata. Yanıtlar ProblemDetail.

#### Bağlantı
Frontend Gateway üzerinden. Game batch doğrulaması ve yerel transaction sınırı,
silme ile yarış davranışı favori otomatik kayıt ucuyla aynıdır.

#### Test Durumu
INTEGRATION — hedef bayrak/diğer bayrağın korunması, tekrar, eşzamanlılık,
geçersiz oyun/kesinti ve 401/403; gerçek PostgreSQL/JWT.

### LIBRARY — Bitirildi durumunu geri alma (LIB-04)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/library/entries/{gameId}/completed

#### Yetkilendirme
Authenticated + Owner; yalnız doğrulanmış kullanıcı JWT sub'ına ait satır.
Mevcut beş kanonik rol sözleşmesi geçerlidir; başka kullanıcıya müdahale yolu yoktur.

#### Path/Query Parameters
- gameId: zorunlu UUID. Query/header kimliği kullanılmaz.

#### Request Body
Yok.

#### Response — 204
Gövdesiz. Yalnız completed=false yapılır; favorite, koleksiyon, id ve addedAt korunur.
Gerçek değişiklikte updatedAt/version ilerler. Zaten false/olmayan kayıt ve tekrar 204;
yeni kayıt oluşmaz, tarih/version değişmez. İki bayrak false olsa da koleksiyon kaydı kalır.
Tek atomik UPDATE kullanılır; eşzamanlı işlemler DB satır kilidi altında sıralanır.

#### Hata Durumları
- 400: Geçersiz yol UUID'si.
- 401: Kimlik yok/geçersiz kullanıcı token'ı.
- 403: Kanonik kullanıcı rolü yok.
- 500: Beklenmeyen hata; ProblemDetail.

#### Bağlantı
Frontend Gateway üzerinden. Yalnız library_db; Game HTTP çağrısı yapılmaz.
Katalog kesintisi/arşivleme kullanıcının kendi işaretini kaldırmasını engellemez.

#### Test Durumu
INTEGRATION — LibraryApiTest: koleksiyon/favori korunması, tekrar, olmayan kayıt,
yeniden işaretleme, kullanıcı izolasyonu, 401/403/400 ve 16 eşzamanlı aç/kapat isteği.

### LIBRARY — Kendi bitirilen oyunlarını listeleme (LIB-04)

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/library/entries/completed

#### Yetkilendirme
Authenticated + Owner; yalnız JWT sub sahibinin completed=true kayıtları.
Bir oyun iki bayrağı da true ise hem favori hem bitirilen listesinde bulunur.

#### Path/Query Parameters
- page: integer, varsayılan 0, minimum 0; sıfır tabanlı.
- size: integer, varsayılan 20, 1–50.
Sabit sıra addedAt DESC, id DESC. Büyük offset taşma üretmez.

#### Request Body
Yok.

#### Response — 200
```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0 }
```
items mevcut LibraryEntryResponse biçimindedir. Liste ve toplam aynı REPEATABLE_READ
snapshot'ından gelir; boş/sonraki sayfada da gerçek toplam korunur. userId veya oyun
adı/görseli dönmez. Filtre/sıralama ve toplu oyun özetleri GET /api/v1/library/entries ucundadır.

#### Hata Durumları
- 400: Geçersiz page/size veya size sınırının aşılması.
- 401: Kimlik yok/geçersiz kullanıcı token'ı.
- 403: Kanonik kullanıcı rolü yok.
- 500: Beklenmeyen hata; ProblemDetail.

#### Bağlantı
Frontend Gateway üzerinden. Yalnız library_db; dış HTTP çağrısı ve N+1 yoktur.

#### Test Durumu
INTEGRATION — LibraryApiTest: yalnız owner/completed, favorite'den bağımsızlık,
aynı tarihte stabil sıra, boş/sınır dışı sayfa, sayfa sınırları ve JWT/rol doğrulaması.
Ortak reader için eşzamanlı yazma sırasında liste/toplam snapshot tutarlılığı testlidir.
Faz 2 canlı pozitif E2E NOT VERIFIED; başka servis kalıcı verisi üretilmedi.
Yeni Library imajı ltz-dev'de çalışır; gerçek HTTP sağlık/401/403/CSRF kontrolleri
32/32 PASS. Modül clean verify 58 test PASS, LINE %98,40. Pre-push/SonarQube manueldir.

### LIBRARY — Hesap verisi silme iç ucu

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: auth-service

#### Endpoint
DELETE /internal/v1/accounts/{accountId}

#### Yetkilendirme
Internal; Gateway'e yönlendirilmez. RS256/Auth JWKS, tam `typ=svc+jwt`,
`iss=ltz-auth` (JWT_ISSUER), tek `aud=library-service`, dolu `kid/jti`,
ham `iat`, `nbf/exp`, pozitif ve en çok 60 saniye ömür, en çok 30 saniye saat toleransı.
Yalnız `SCOPE_account:erase`; tam `sub=svc:auth-service` ve
`account_id` = yol UUID'si zorunlu. Kullanıcı token'ı (ADMIN dahil) iç uçta,
servis token'ı kullanıcı namespace'inde reddedilir. Servis rollerinden yetki türetilmez.

#### Path Parameters
- accountId: UUID, zorunlu; token'ın account_id claim'iyle eşleşmeli.

#### Request Body
Yok.

#### Response — 204
Gövde yok. Yalnız hedef hesabın `library_entries` satırları, favori ve bitirildi
alanlarıyla birlikte tek transaction'da fiziksel silinir. Veri yok/tekrar çağrıda 204.

#### Hata Durumları
- 400: Geçersiz yol UUID'si.
- 401: Kimlik yok, kullanıcı token'ı, geçersiz imza/issuer/audience/tür/süre veya eksik token claim'i.
- 403: Silme kapsamı yok, çağıran Auth değil veya account_id eksik/yoldaki hesaptan farklı.
- 500: Beklenmeyen sunucu hatası; silme onayı değildir.

#### Bağlantı
Frontend: Yok. Gateway route'u eklenmedi.
Diğer mikroservisler: Auth mevcut LIBRARY_SERVICE_URL üzerinden RestClient ile doğrudan çağırır.
Auth yalnız 200/204'ü onaylar; bu uç gövdesiz 204 kullanır. Yeni ENV yok.
Karar raporu: `docs-Game Service/LTZ-HAM-GAME-04-Auth-Entegrasyonu.md` §13.

#### Test Durumu
UNIT + INTEGRATION — AccountErasureTest, ServiceTokenDecoderConfigurationTest.
Geçici RSA/JWKS ile gerçek filtre/controller/servis sınanır.
ProductionServiceTokenSupport gerçek Auth ServiceTokenIssuer kaynağını test dizinine derler.
LibraryApiTest gerçek PostgreSQL üzerinde hedef hesabın silinmesi, tekrar ve diğer
hesabın korunmasını sınar. AuthErasureAdapterIntegrationTest, Auth'un gerçek
HttpAccountDataEraser + ServiceTokenIssuer kaynaklarını (test dizinine derlenir, geçici anahtarla)
Library'nin gerçek HTTP sunucusuna (RANDOM_PORT, gerçek güvenlik zinciri/JWKS/PostgreSQL) karşı çalıştırır:
204 onayı, tekrarda idempotentlik, yalnız bağlı hesabın silinmesi ve başka `aud` token'ının 401 ile
onaylanmaması doğrulanır. Gerçek Auth servisinin canlı silme akışı (14 günlük bekleme süresi nedeniyle)
ve DEV Auth görevinin CONFIRMED olması NOT VERIFIED.

## REVIEW

Sorumlu: Hamza · Gateway: `/api/v1/review/**` · Görevler: REV-

### REVIEW — Ortak güvenlik, kota ve doğrulama notları

İş uçları korumalıdır; `Authorization: Bearer` ile Auth RS256/JWKS `at+jwt` token'ı gerekir. `iss=JWT_ISSUER` (varsayılan `ltz-auth`), `aud` içinde `JWT_AUDIENCE` (varsayılan `ltz-api`), kanonik UUID `sub`, `kid`, ham `iat/nbf/exp` ve 30 saniye zaman toleransı doğrulanır. Auth'un PLAYER/CONTENT_CREATOR/GAME_DEVELOPER/MODERATOR/ADMIN rollerinden biri gerekir. Rol sahipliğin yerine geçmez. Yetki yalnız `roles` **listesindeki** bu beş kanonik rolden gelir; liste olmayan, bilinmeyen veya metin dışı `roles` hiçbir yetki vermez, `MODERATOR`/`ADMIN` Review'da ek yetki taşımaz. `aud` claim'i eksik imzalı token 401'dir. Silinmiş hesabın eski token'ı yazma işlemlerinde 403 alır (kota sayacı da yazılmaz). Ayrıntı: [Auth entegrasyon raporu](../docs-review/REVIEW_AUTH_INTEGRATION_REPORT.md). Cookie veya `X-User-Id` kimlik kaynağı değildir. Başka kullanıcı adına yazma alanı yoktur; bilinmeyen JSON alanları reddedilir.

Kullanıcı token'ı en fazla 15 dakika ömürlü olabilir (Auth JwtProperties sınırı). JSON gövdesi 64 KiB ile sınırlıdır; Content-Length olmadan gelen chunked istekler de sınırlanır. Korunan iş yanıtları `Cache-Control: private, no-store` taşır. Sayısal/boolean alanlara metin değeri verilmesi kabul edilmez.

401: kimlik yok/geçersiz; 403: rol/sahiplik yetersiz veya hesap silme engeli; 404: kaynak veya kullanılabilir oyun yok; 409: tekillik/sürüm çakışması; 400: geçersiz UUID/gövde/alan; 413: gövde sınırı aşıldı; 429: kullanıcı kotası aşıldı (`Retry-After` saniye; yalnız inceleme oluşturma ve beğenme/kaldırma, bkz. REV-13); 503: Game Service kesintisi veya bozuk sözleşme; 500: beklenmeyen hata. Hatalar `application/problem+json` (`ProblemDetail`); SQL/token/iç adresler dönmez.

**Güncel doğrulama (31 Ekim 2026):** Review modülü 153 test, 0 failure/error/skipped;
Maven clean verify, merkezi coverage ve Checkstyle/PMD yeni ihlal kapısı PASS.
Satır kapsamı %98,03; değişen satır kapsamı %98,04. Son başarılı kayıtlı kalite
koşusunda Review pre-push PASS, Sonar Quality Gate OK (A/A/A, kapsam %95,2,
açık issue 0) ve Trivy HIGH/CRITICAL bağımlılık kapısı PASS'tır. Bunlar ilgili
çalıştırmaların kayıtlarıdır; canlı karşı servis E2E veya tüm-repo Sonar kanıtı değildir.
Ayrıntı: [Auth ve kalite raporu](../docs-review/REVIEW_AUTH_INTEGRATION_REPORT.md),
[ilerleme](../docs-review/REVIEW_SERVICE_PROGRESS.md).

Endpointlerin aşağıdaki Test Durumu alanlarında bulunan faz bazlı test sayıları
tarihsel kanıttır; son 153 testlik doğrulama ilgili endpoint testlerini de içerir.
REV-07, REV-10, REV-13 raporlama ve REV-15 entegrasyon bağımlılıkları açıktır.
Game sunucu tüketimi ve servis okuma izni uygulanmış değildir.

### REVIEW — REV-01 — Puan yazma

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend (Gateway üzerinden); Faz 3 toplu özet tüketicileri bu fazda uygulanmaz.
#### Endpoint
`PUT /api/v1/review/games/{gameId}/rating`.
#### Yetkilendirme
Owner — Korumalı; yalnız kendi puanı, `userId=sub`.
#### Path/Query Parameters
`gameId` zorunlu kanonik LTZ UUID; query yok.
#### Request Body
`{"rating":7}`; zorunlu 1–10 tam sayı. Ondalık ve null reddedilir.
#### Response
200; `gameId`, `rating`, `version`, `createdAt`, `updatedAt` (UTC ISO-8601).
#### Hata Durumları
ortak 400/401/403/404/503/500.
#### Bağlantı
`RatingController → RatingService → GameCatalogPort`, ardından transaction içinde `RatingWriter → RatingRepository`. Game çağrısı DB transaction'ı dışında, mevcut batch API ile bir kez yapılır.
#### Test Durumu
UNIT + INTEGRATION — PostgreSQL/Flyway; sınırlar, aynı/farklı kullanıcı, tekrar, eşzamanlılık; gerçek imza/JWKS HTTP güvenlik doğrulaması.

Metin/tavsiye gerekmez. `(user_id,game_id)` UNIQUE ve `rating BETWEEN 1 AND 10` CHECK V51'de korunur. Aynı değer tekrarında sürüm ve zamanlar aynı kalır. Farklı değerler PostgreSQL satır kilidiyle sıralanır; son başarılı yazma geçerlidir. Bir kullanıcının tekrar isteği yeni puan satırı üretmez.

### REVIEW — REV-01 — Kendi puanını okuma

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend.
#### Endpoint
`GET /api/v1/review/games/{gameId}/rating`.
#### Yetkilendirme
Owner — Korumalı; yalnız token sahibinin puanı.
#### Path/Query Parameters
`gameId` zorunlu UUID; query/body yok.
#### Response
200; puan yazma yanıtıyla aynı DTO.
#### Hata Durumları
400/401/403/404/500. Kayıt yoksa 404; başka kullanıcının puanı dönmez.
#### Bağlantı
Yerel Review verisi okunur; geçmiş puan Game kesintisinde de okunabilir.
#### Test Durumu
INTEGRATION — kullanıcı izolasyonu, bulunamayan puan ve gerçek HTTP okuması.

### REVIEW — REV-02 — Metin oluşturma

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend (Gateway).
#### Endpoint
`POST /api/v1/review/games/{gameId}/reviews`.
#### Yetkilendirme
Owner — Korumalı; yazar token `sub` alanından alınır.
#### Path/Query Parameters
`gameId` zorunlu UUID; query yok.
#### Request Body
`{"body":"Oyun deneyimim"}`. 1–5000 UTF-16 karakter; baş/son whitespace temizlenir. Unicode yalnız boşluk içeriği ve newline/tab dışındaki kontrol karakterleri reddedilir. HTML etkinleştirilmez; içerik düz metindir, istemci metin olarak render etmelidir.
#### Response
201 ve `Location: /api/v1/review/reviews/{id}`; `id`, `gameId`, `body`, `ownedByActor`, `version`, `createdAt`, `updatedAt`.
#### Hata Durumları
400/401/403/404/409/429/503/500. 429 için REV-13 bölümüne bakın. Aynı kullanıcı/oyun için ikinci aktif metin her zaman 409; POST tekrarında önceki metin değiştirilmez.
#### Bağlantı
Game batch doğrulaması transaction dışında; ReviewWriter içinde JPA save/flush. V52 partial UNIQUE aktif kullanıcı/oyun metnini korur.
#### Test Durumu
INTEGRATION — 45 testlik koşu PASS; sınırlar, bağımsız puan, kimlik, Game kesintisi ve eşzamanlı oluşturma.

### REVIEW — REV-02 — Tek inceleme okuma

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend.
#### Endpoint
`GET /api/v1/review/reviews/{reviewId}`.
#### Yetkilendirme
Authenticated — Korumalı; görünür içerik diğer kayıtlı kullanıcılarca okunabilir. `ownedByActor` token sahibiyle hesaplanır.
#### Path/Query Parameters
`reviewId` zorunlu UUID; body/query yok.
#### Response
200; oluşturma yanıtıyla aynı DTO. Ham yazar UUID'si dönmez.
#### Hata Durumları
400/401/403/404/500.
#### Bağlantı
Yerel JPA okuması; Game kesintisi okumayı durdurmaz.
#### Test Durumu
INTEGRATION — oluşturma, başka aktör okuması ve bulunamayan kimlik PASS.

### REVIEW — REV-03 — Yalnız metni düzenleme

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend.
#### Endpoint
`PATCH /api/v1/review/reviews/{reviewId}`.
#### Yetkilendirme
Owner — Korumalı; yalnız gerçek yazar. ADMIN/MODERATOR rolü bu uçta sahibin yerine geçmez.
#### Path/Query Parameters
`reviewId` zorunlu UUID; query yok.
#### Request Body
`{"version":0,"body":"Yeni deneyimim"}`. Her iki alan zorunlu; version >=0. Null/eksik metin silme anlamına gelmez; 400 döner. Puan/tavsiye ayrı uçlardan yönetilir ve PATCH bunları kabul etmez.
#### Response
200; ReviewResponse, değişiklik varsa artan sürüm ve updatedAt; createdAt korunur. Aynı metin/güncel sürüm no-op'tur. Eski sürümle tekrar 409 döner; otomatik retry yapılmaz.
#### Hata Durumları
400/401/403/404/409/503/500.
#### Bağlantı
Sahiplik kontrolü, Game doğrulaması (transaction dışında), ardından yeniden sahiplik/sürüm kontrolüyle pessimistic satır kilidi ve JPA @Version. Çakışan sürüm tek yazmayı kabul eder.
#### Test Durumu
INTEGRATION — sahiplik, createdAt koruma, no-op, eski sürüm 409, eşzamanlı düzenleme ve bağımsız puan/tavsiye. Gerçek HTTP PATCH akışı da sınanır.

### REVIEW — REV-04 — Metin silme

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend.
#### Endpoint
`DELETE /api/v1/review/reviews/{reviewId}?version=0`.
#### Yetkilendirme
Owner — Korumalı; yalnız gerçek yazar. Yönetici/moderasyon yetkisi bu uca eklenmez.
#### Path/Query Parameters
reviewId UUID; version zorunlu tam sayı >=0.
#### Request Body
Yok.
#### Response
204; içerik NULL, status=DELETED ve deletedAt atanır. Puan ve bağımsız tavsiye korunur. Aynı yazarın silinmiş kayda tekrarı sürümden bağımsız 204'tür; version parametresi yine geçerli olmalıdır.
#### Hata Durumları
400/401/403/404/409/500. Aktif kayıtta eski sürüm 409; silinmiş kayıt GET/PATCH ile 404. Eksik kimlik 404. Başkasının silinmiş kaydı da 403.
#### Bağlantı
ReviewWriter satır kilidi/sürüm kontrolüyle yerel transaction. Game kesintisi silmeyi engellemez. Yeniden POST yeni kimlik oluşturur; önceki kayıt geri getirilmez.
#### Test Durumu
INTEGRATION — sahiplik, eski/geçersiz sürüm, eşzamanlı tekrar, metin temizleme, tekrar oluşturma, Game kesintisi ve puan/tavsiye koruma; gerçek HTTP DELETE akışı.

### REVIEW — REV-05 — Tavsiye yazma

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend (Gateway).
#### Endpoint
`PUT /api/v1/review/games/{gameId}/recommendation`.
#### Yetkilendirme
Owner — Korumalı; yalnız token sahibinin tavsiyesi.
#### Path/Query Parameters
gameId zorunlu UUID; query yok.
#### Request Body
`{"recommended":false}`; gerçek JSON boolean zorunlu. Null, sayı/metin, userId/body/rating alanları reddedilir.
#### Response
200; `gameId`, `recommended`, `version`, `createdAt`, `updatedAt`.
#### Hata Durumları
400/401/403/404/413/503/500.
#### Bağlantı
Game batch doğrulaması transaction dışında, sonra RecommendationWriter/Repository PostgreSQL UPSERT. V53 kullanıcı/oyun tekilliği. Aynı değer tekrarı sürüm/zaman değiştirmez; farklı değerlerde son başarılı DB yazması geçerlidir.
#### Test Durumu
INTEGRATION — metin/puan olmadan yazma; true/false geçişi, tekrar, kullanıcı izolasyonu, kesinti ve eşzamanlı tekillik. Gerçek HTTP akışında da sınanır.

### REVIEW — REV-05 — Kendi tavsiyesini okuma

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend.
#### Endpoint
`GET /api/v1/review/games/{gameId}/recommendation`.
#### Yetkilendirme
Owner — Korumalı; yalnız token sahibi.
#### Path/Query Parameters
gameId zorunlu UUID; query/body yok.
#### Response
200; tavsiye yazma yanıtıyla aynı DTO. Kayıt bulunmaması false anlamına gelmez, 404 döner.
#### Hata Durumları
400/401/403/404/500.
#### Bağlantı
Yalnız Review verisi; sayısal puandan tavsiye türetilmez.
#### Test Durumu
INTEGRATION — eksik kayıt, başka kullanıcı, false değerini ayırma, metin silindikten sonra koruma ve gerçek HTTP.

### REVIEW — REV-06 — İnceleme beğenme

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend, Gateway üzerinden.
#### Endpoint
`PUT /api/v1/review/reviews/{reviewId}/like`.
#### Yetkilendirme
Owner — Ortak korumalı kullanıcı JWT/rol sözleşmesi; aktör yalnız doğrulanmış sub. Kendi incelemesini beğenme 403. Servis token'ı kabul edilmez.
#### Path/Query Parameters
reviewId zorunlu UUID; query yok.
#### Request Body
Yok. Boş/whitespace dışında gövde 400; userId/rol/oy sahibi seçilemez.
#### Response
200 `ReviewLikeResponse`: `reviewId`, `likeCount` (long), `likedByActor` (boolean). Tekrar PUT aynı oy/sayıyı korur. Beğeni yazarın inceleme sürümünü, metnini, puanını veya tavsiyesini değiştirmez.
#### Hata Durumları
400 geçersiz UUID/gövde; 401 kimlik yok/geçersiz; 403 rol, self-vote veya silinen hesap engeli; 404 olmayan/silinmiş inceleme; 429 kullanıcı kotası aşıldı (REV-13); 500 beklenmeyen hata. ProblemDetail ortak sözleşmesi geçerli.
#### Bağlantı
ReviewLikeService → hesap silme guard'ı → inceleme satır kilidi → ReviewLikeRepository atomik INSERT ON CONFLICT DO NOTHING → gerçek DB satırlarından count/aktör durumu. V55 PK `(review_id,user_id)` ve review FK. Dış servis çağrısı yok.
#### Test Durumu
UNIT/INTEGRATION — 75 testlik strict koşuda PASS; PostgreSQL tekillik/yarışlar ve gerçek TCP üzerinden RS256/JWKS kullanıcı token'ı ile beğeni/tekrar/self-vote/servis kimliği reddi.

### REVIEW — REV-06 — Beğeniyi kaldırma

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend.
#### Endpoint
`DELETE /api/v1/review/reviews/{reviewId}/like`.
#### Yetkilendirme
Owner — Korumalı kullanıcı JWT; yalnız aktörün oyunu kaldırır. İnceleme sahibi olmak başkalarının oyunu silme yetkisi vermez.
#### Path/Query Parameters
reviewId zorunlu UUID; query yok.
#### Request Body
Yok; dolu gövde 400.
#### Response
200 `ReviewLikeResponse`. Zaten oy yoksa aynı sıfır/aktör-false sonucu; başka kullanıcıların oyları korunur. Kendi incelemesine oy veremeyen yazarın DELETE'i no-op olabilir.
#### Hata Durumları
400/401/403/404/429/500. 429 REV-13 kotasıdır (PUT ile ortak). Olmayan veya DELETED hedefte 404; silinmiş incelemenin beğenileri metin silme transaction'ında zaten temizlenir.
#### Bağlantı
Aynı hesap guard'ı ve parent review kilidi; yalnız `(reviewId,sub)` satırı silinir. Fiziksel hesap silmesinde verilen oylar ve silinen incelemelerin oyları temizlenir. Karşılıklı oyları olan iki hesabın silmesi kısa erasure advisory lock ile sıralanır.
#### Test Durumu
INTEGRATION — tekrar DELETE, kullanıcı izolasyonu, author no-op, içerik silmesi, hesap silmesi/tekrar engeli, karşılıklı hesap silme ve beğeni/silme yarışı PASS; gerçek HTTP DELETE akışı PASS.

### REVIEW — REV-06 — Beğeni durumunu okuma

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend.
#### Endpoint
`GET /api/v1/review/reviews/{reviewId}/like`.
#### Yetkilendirme
Authenticated — Korumalı kullanıcı JWT/rol. Görünür inceleme için toplam ve yalnız token sahibinin oy durumu; oy veren kullanıcı kimlikleri döndürülmez.
#### Path/Query Parameters
reviewId zorunlu UUID; query/body yok.
#### Response
200 `ReviewLikeResponse`. Oy yoksa `likeCount=0`, `likedByActor=false`. `Cache-Control: private, no-store`.
#### Hata Durumları
400/401/403/404/500. Silinen hedefin varlığı veya oy listesi açılmaz.
#### Bağlantı
REPEATABLE_READ salt okunur transaction ile görünürlük ve count/aktör sorgusu aynı snapshot'ta; N+1 HTTP çağrısı yok.
#### Test Durumu
INTEGRATION — farklı aktör görünümü, toplam, sıfır, anonim/silinmiş hedef ve gerçek JWT/HTTP PASS.

REV-07 rapor API'si, outbox/relay ve REV-10 dış teslimi uygulanmadı. Önerilen hedef sözleşmeler [request/](../docs-review/request/) altında; mevcut endpoint olarak kullanılamaz.

### REVIEW — REV-13 — Kullanıcı kotası ve 429 sözleşmesi

Durum: IMPLEMENTED (yalnız bağımsız kısım). Rapor oluşturma kotası ve mükerrer rapor engeli REV-07'ye bağlıdır: NOT IMPLEMENTED, ENTEGRASYON BEKLİYOR.
Sorumlu: Hamza.
Tüketici servisler: Frontend (Gateway üzerinden); yeni endpoint yoktur.
#### Kapsam
Yeni endpoint yok; mevcut üç işleme uygulanan kota:
  - `POST /api/v1/review/games/{gameId}/reviews` → kullanıcı başına **60 saniyede 5** deneme (`REVIEW_CREATE`).
  - `PUT` ve `DELETE /api/v1/review/reviews/{reviewId}/like` → ortak kota, kullanıcı başına **60 saniyede toplam 60** deneme (`REVIEW_LIKE`).
  - Diğer uçlar (puan/tavsiye PUT, inceleme PATCH/DELETE, tüm GET'ler) sınırsızdır; bunlar için sayısal politika onaylanmadı.
#### Yetkilendirme
Kota yalnız kimliği doğrulanmış ve rolü uygun kullanıcı isteğinde tüketilir. Anonim/geçersiz token 401, rol/servis token'ı 403/401 mevcut davranışını korur ve sayaç yazmaz. Sayaç anahtarı token `sub` alanıdır; istemci seçemez.
- Sayım kuralı: Kimlik doğrulama geçildikten sonra ilgili rotaya gelen her istek bir denemedir; sonucundan bağımsızdır: 201/200, tekrar (409), kendi incelemesini beğenme (403), olmayan hedef (404), gövde/alan doğrulama hatası (400), Game kesintisi (503). Kota iş transaction'ından önce kendi transaction'ında kesinleşir; iş transaction'ı geri alınsa da deneme geri verilmez. 413 (gövde sınırı) güvenlik/filtre katmanında kaldığından sayılmaz.
- Pencere: Epoch'a hizalı sabit pencere (örn. 12:00:00–12:01:00 UTC). Sayaç `limit+1`'de durur. Pencere sınırında art arda iki pencerede toplam `2×limit` işleme izin verilebilir; bu sabit pencere algoritmasının bilinen sınırıdır.
- Response (aşımda): 429, `application/problem+json` (`ProblemDetail`, `status=429`), `Retry-After: <saniye>` (pencere bitimine kalan süre, yukarı yuvarlanmış, en az 1) ve `Cache-Control: private, no-store`. İş mantığı çalıştırılmaz; kayıt oluşturulmaz/değiştirilmez.
#### Hata Durumları
429 yukarıdaki gibi; kota deposu (PostgreSQL) hatasında istek güvenli tarafta 500 ile reddedilir.
#### Bağlantı
`AbuseQuotaInterceptor` (Spring MVC, yalnız eşleşen rota ve metotlarda) → `AbuseQuotaService` → `AbuseQuotaRepository` (REQUIRES_NEW, tek atomik `INSERT … ON CONFLICT DO UPDATE … RETURNING`). Tablo `review_rate_limits` (V57): SHA-256 hesap özeti, eylem, pencere başlangıcı/bitişi, sayaç; ham kullanıcı kimliği yoktur. Süresi dolan satırlar (bitişten 5 dakika sonra) dakikada bir temizlenir. Sınırlar `ltz.review.abuse.*` ayarlarıdır. Dış servis çağrısı yoktur.
#### Test Durumu
UNIT + INTEGRATION — PostgreSQL 17 üzerinde kontrollü saatle: 5/60 ve 60/60 sınırları, `Retry-After`, pencere yenilenmesi, tekrar/geçersiz/self-like/404/503 sayımı, eşzamanlı 16 inceleme ve 75 beğeni isteğinde kesin sayım, ortak beğeni/kaldırma kotası, GET'lerin sınırsızlığı, 401/403'ün sayaç yazmaması, temizlik ve tablo kısıtları; gerçek TCP/RS256/JWKS ile 429 + `Retry-After`. Ayrıntı: [Faz 4 raporu](../docs-review/REVIEW_SERVICE_PHASE_04_REPORT.md).

### REVIEW — REV-09 — Öne çıkan incelemeler

Durum: IMPLEMENTED.
Sorumlu: Hamza.
Tüketici servisler: Frontend, mevcut Gateway yolu üzerinden; bu görevde frontend/Gateway değiştirilmedi.
#### Endpoint
`GET /api/v1/review/games/{gameId}/reviews/featured`.
#### Yetkilendirme
Authenticated — Mevcut korumalı kullanıcı JWT/rol sözleşmesi; PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR veya ADMIN. Aktör doğrulanmış `sub`; servis token'ı kabul edilmez. Anonim endpoint değildir.
#### Path/Query Parameters
`gameId` zorunlu kanonik UUID; `page` tamsayı 0–10000, varsayılan 0; `size` tamsayı 1–50, varsayılan 20; `minLikes` tamsayı 0–2147483647, varsayılan 0. Oyun ve minimum beğeni filtreleri birlikte uygulanır. Görünürlük yalnız sunucunun `VISIBLE` koşuludur; istemci silinmiş kayıtları açamaz. Serbest sıralama/rapor/moderasyon filtresi sunulmaz.
#### Request Body
Yok.
#### Response
200 `FeaturedReviewsResponse`: `items` (`FeaturedReviewResponse[]`), `page`, `size`, `totalElements` (long), `totalPages` (long). Her öğe `review` (mevcut ReviewResponse: id/gameId/body/ownedByActor/version/createdAt/updatedAt) ve `likeCount` (long) içerir. Yazar ve oy veren kullanıcı UUID'leri döndürülmez. `ownedByActor` token sahibine göre hesaplanır.
- Sıralama: `likeCount DESC, createdAt DESC, id ASC`; aynı snapshot/veride deterministiktir. Sıfır beğenili görünür kayıtlar varsayılan filtreye dahildir. İnceleme düzenlenmesi oluşturulma tarihini değiştirmez, sıralamada updatedAt kullanılmaz. Rapor/şikâyet cezası veya moderasyon skoru yoktur.
- Boş durum: İnceleme olmayan/Review'de bilinmeyen oyun UUID'si 200, `items=[]`, `totalElements=0`, `totalPages=0`. Game Service çağrısı yapılmaz; bu yanıt oyunun katalogda varlığını ya da güncel erişilebilirliğini doğrulamaz. Son sayfa dışına taşan geçerli sayfa 200/boş items döndürür; toplamlar filtrelenen kümenin toplamıdır.
#### Hata Durumları
400 geçersiz UUID/tamsayı/aralık (ProblemDetail); 401 eksik/geçersiz kullanıcı kimliği veya servis token'ı; 403 mevcut rol politikası; 500 beklenmeyen sunucu hatası. `Cache-Control: private, no-store`.
#### Bağlantı
FeaturedReviewController → FeaturedReviewService → FeaturedReviewRepository. Parametreli PostgreSQL toplulaştırması; gerçek review_likes satırlarından sayım, sıfır beğeniler için LEFT JOIN. İki set sorgusu (toplam + sayfa) tek REPEATABLE_READ salt okunur, 4 saniye timeout'lu transaction içinde çalışır. N+1 sorgu/HTTP yok. V56 görünür oyun/tarih/UUID kısmi indeksi; V55 review_id önekli PK beğeni JOIN'ini destekler. Beğeniye göre son sıralama hesaplanır; indeksin bütün sıralamayı kaldırdığı iddia edilmez.
- Sayfalama sınırı: Snapshot tek istek içindir. Ayrı sayfa istekleri arasında beğeni, ekleme veya silme değişirse sonuçlar yer değiştirebilir; istekler arası sabit snapshot/cursor garantisi verilmez.
#### Test Durumu
UNIT/INTEGRATION — Review modülünün 85/85 testlik Maven test koşusunda PASS. Yeni 7 PostgreSQL/MockMvc testi + 2 servis birim testi + 1 gerçek TCP/RS256/JWKS testi. Canlı Auth/Game/Gateway E2E iddiası yoktur. Güncel kalite sonuçları için ortak doğrulama notuna bakın.

### REVIEW — REV-08 — Tek oyun LTZ puan özeti

Durum: IMPLEMENTED — Review tarafı.
Sorumlu: Hamza.
Tüketici servisler: Frontend; Game sunucu entegrasyonu ayrıca sözleşme bekliyor.
#### Endpoint
`GET /api/v1/review/games/{gameId}/rating-summary`.
#### Yetkilendirme
Authenticated — Mevcut kullanıcı JWT/rol politikası. Anonim ve servis token erişimi yoktur. Kullanıcı JWT'si servis kimliği yerine kullanılamaz.
#### Path/Query Parameters
gameId zorunlu kanonik LTZ UUID; query yok.
#### Request Body
Yok.
#### Response
200 `{gameId, averageRating, ratingCount}`. averageRating PostgreSQL numeric/Java BigDecimal, iki ondalık basamak, pozitif puanlarda HALF_UP; ratingCount long ve benzersiz puan veren kullanıcı sayısıdır. Puan yoksa averageRating=null/ratingCount=0; 404 değildir. 0 puan uydurulmaz. JSON sayısı tüketicide sondaki sıfırları korumayabilir.
#### Hata Durumları
400 geçersiz UUID (ProblemDetail), 401 kimlik yok/geçersiz/servis token'ı, 403 izinli kullanıcı rolü yok, 500 beklenmeyen hata.
#### Bağlantı
RatingSummaryController → RatingSummaryService → RatingSummaryRepository; tek parametreli AVG/count SQL sorgusu. V51 kullanıcı/oyun tekilliği nedeniyle count(*) benzersiz kullanıcı sayısıdır. Yalnız game_ratings kullanılır; inceleme, beğeni, tavsiye ve Steam oranları katılmaz. Mevcut game_id indeksi yeterlidir; migration eklenmedi. Salt okunur transaction, 4 saniye timeout, tek SQL snapshot; dış servis çağrısı yok.
- Silme/boş sonuç: Metin silmek bağımsız puanı etkilemez; hesap silme puanını aynı mevcut transaction içinde kaldırır. Bilinmeyen/puan verilmeyen oyun aynı boş özeti alır; bu Game katalog görünürlüğü/varlık doğrulaması değildir. Cache-Control private,no-store.
#### Test Durumu
INTEGRATION — 6 yeni PostgreSQL/MockMvc ve 1 gerçek TCP/RS256/JWKS testi; REV-08 kapanışında 92/92 Review testi PASS. Güncel kalite sonuçları için ortak doğrulama notuna bakın.

### REVIEW — REV-12 — Toplu LTZ oyun puanı özetleri

Durum: IMPLEMENTED — Review backend kabulü; Game→Review sunucu entegrasyonu SÖZLEŞME BEKLENİYOR / NOT IMPLEMENTED / NOT VERIFIED.
Sorumlu: Hamza.
Tüketici servisler: Frontend; Game hedef tüketicidir, servis kimliği henüz yetkilendirilmedi. Kullanıcı JWT'si servis kimliği yerine taşınmaz.
#### Endpoint
`POST /api/v1/review/games/rating-summaries`.
#### Yetkilendirme
Authenticated — Mevcut kullanıcı at+jwt ve PLAYER/CONTENT_CREATOR/GAME_DEVELOPER/MODERATOR/ADMIN rol politikası. Anonim veya svc+jwt erişimi açılmadı. Mevcut CSRF/Bearer ayrımı ve Cache-Control private,no-store korunur.
#### Path/Query Parameters
Yok.
#### Request Body
application/json `{gameIds: UUID[]}`; gameIds zorunlu, null olamaz; ham dizi en fazla 50 eleman, her eleman null olmayan UUID. Sınır tekilleştirmeden önce uygulanır. Boş dizi geçerli; tekrarlar ilk görülme sırasıyla tekilleştirilir. Bilinmeyen alanlar/tür dönüşümü reddedilir. Mevcut 64 KiB gövde sınırı geçerlidir.
#### Response
200 `{items:[{gameId,averageRating,ratingCount}]}`; yalnız istenen benzersiz oyunlar, ilk görülme sırasıyla. Puan verilmemiş/bilinmeyen UUID için averageRating=null/ratingCount=0; boş dizi items=[]. Ortalama REV-08 ile aynı iki basamak HALF_UP BigDecimal/numeric hesabıdır. ratingCount benzersiz kullanıcı sayısı; histogram/tavsiye oranı/inceleme adedi/Steam oranı eklenmedi.
#### Hata Durumları
400 eksik/null/51+ dizi, null/geçersiz UUID, yanlış JSON türü/bilinmeyen alan/bozuk gövde (ProblemDetail); 401 kimlik eksik/geçersiz veya servis token'ı; 403 izinli kullanıcı rolü yok; 413 gövde limiti; 415 desteklenmeyen içerik türü; 500 beklenmeyen hata. Sonuç 404 veya kısmi hata listesi değildir; katalog varlığı doğrulanmaz.
#### Bağlantı
RatingSummaryController → RatingSummaryService → RatingSummaryRepository; tek parametreli IN/GROUP BY AVG/count SQL'i, game_ratings dışına JOIN/HTTP yok. En fazla 50 anahtar bellekte sıralanıp eksik oyunlar null/0 tamamlanır; oyun başına SQL yoktur. Boş listede SQL yok. Tek salt okunur transaction/4 saniye; tek statement snapshot'ı bütün gruplar ve ortalama/count için tutarlıdır. Aynı sorgu tekil özette de kullanılır. Yeni tablo/migration/bağımlılık/kalıcı sayaç eklenmedi.
#### Test Durumu
UNIT/INTEGRATION — 102/102 Review testi PASS. 50 oyun/10.000 puanda JdbcClient spy ile tam 1 SQL ve sıfır Game çağrısı doğrulandı; boş/geçersiz listede sıfır SQL. Eşzamanlı okuma/yazma ve gerçek TCP/RS256/JWKS olumlu/olumsuz testleri PASS. Canlı Game/Auth servis tüketimi NOT VERIFIED. Güncel kalite sonuçları için ortak doğrulama notuna bakın.
- Bekleyen sözleşme: [Game talebi](../docs-review/request/game-review-integration.md), [Auth talebi](../docs-review/request/auth-review-integration.md). Servis okuma yolu ve dar izin adı yalnız öneridir; mevcut yetki olarak kullanılamaz.

### REVIEW — REV-14 — Oyun inceleme ve puan istatistikleri

Durum: IMPLEMENTED — Review backend kabulü. Endpoint adı (`/statistics`) ve DTO bu görevde mevcut `rating-summary` adlandırmasına uygun seçilmiş yeni sözleşmedir; Frontend/Game tüketimi henüz yoktur (NOT VERIFIED).
Sorumlu: Hamza.
Tüketici servisler: Frontend (Gateway). Game→Review servis erişimi tanımlı değildir; bu uç svc+jwt kabul etmez.
#### Endpoint
`GET /api/v1/review/games/{gameId}/statistics`.
#### Yetkilendirme
Authenticated — Mevcut kullanıcı at+jwt ve PLAYER/CONTENT_CREATOR/GAME_DEVELOPER/MODERATOR/ADMIN rolleri. Anonim erişim ve servis token'ı yoktur. Kimlikler yanıtta dönmez.
#### Path/Query Parameters
`gameId` zorunlu kanonik UUID; query/body yok.
#### Response
200.
```json
{
  "gameId": "00000000-0000-4000-8000-000000000001",
  "ratings": {
    "ratingCount": 6,
    "averageRating": 5.5,
    "distribution": [
      {
        "rating": 1,
        "userCount": 2
      },
      {
        "rating": 2,
        "userCount": 0
      },
      {
        "rating": 3,
        "userCount": 0
      },
      {
        "rating": 4,
        "userCount": 0
      },
      {
        "rating": 5,
        "userCount": 0
      },
      {
        "rating": 6,
        "userCount": 0
      },
      {
        "rating": 7,
        "userCount": 3
      },
      {
        "rating": 8,
        "userCount": 0
      },
      {
        "rating": 9,
        "userCount": 0
      },
      {
        "rating": 10,
        "userCount": 1
      }
    ]
  },
  "reviews": {
    "activeCount": 2
  },
  "recommendations": {
    "recommendedCount": 3,
    "notRecommendedCount": 1,
    "totalCount": 4,
    "recommendedPercentage": 75.0,
    "notRecommendedPercentage": 25.0
  }
}
```
  - `ratings`: yalnız sayısal puanlar. `ratingCount` puan veren **benzersiz kullanıcı** sayısıdır (kullanıcı/oyun başına tek puan); REV-08/REV-12 ile aynıdır. `distribution` her zaman 1–10 arası 10 kova içerir, boş kovalar `userCount=0` ile açıkça yazılır; kovaların toplamı `ratingCount`'a eşittir. `averageRating` REV-08/REV-12 ile aynı `round(avg(rating),2)` ifadesidir (iki ondalık, HALF_UP), puan yoksa `null`.
  - `reviews.activeCount`: yalnız `VISIBLE` metin incelemeleri. Silinmişler sayılmaz; puan veren veya tavsiye veren kullanıcı sayısıyla ilgisi yoktur (puan/tavsiye verip metin yazmayan kullanıcı bu sayıya girmez).
  - `recommendations`: yalnız `game_recommendations`. `totalCount = recommendedCount + notRecommendedCount`. Yüzde = `100 × sayı / totalCount`, iki ondalık, HALF_UP, **0–100 ölçeği**; payda `totalCount`'tur (tavsiye vermeyen veya yalnız puan veren kullanıcılar paydaya girmez). `totalCount=0` ise iki yüzde de `null` (0 değil). Her yüzde bağımsız yuvarlandığından tam eşitlik durumlarında toplam 100,01 veya 99,99 olabilir (örn. 1/800 → 0,13 ve 99,88).
  - Boş oyun (bilinmeyen UUID dahil): tüm sayılar 0, `averageRating=null`, 10 sıfırlı kova, yüzdeler `null`; HTTP 200, Game kataloğu çağrılmaz ve oyun varlığı doğrulanmaz (REV-08 ile aynı politika).
#### Hata Durumları
400 geçersiz UUID; 401 kimlik yok/geçersiz veya servis token'ı; 403 izinli rol yok; 405 desteklenmeyen metot; 500 beklenmeyen hata. `Cache-Control: private, no-store`.
#### Bağlantı
`ReviewStatisticsController → ReviewStatisticsService → ReviewStatisticsRepository`. Tek salt okunur transaction/4 saniye ve **tek SQL ifadesi**: bağımsız skaler alt sorgular tek snapshot'tan okur; dağılım `generate_series(1,10)` ile tamamlanır. Oyun başına/kova başına ayrı sorgu (N+1) yoktur; yeni tablo, kalıcı sayaç, materialized view veya migration eklenmedi (V57 yalnız REV-13 kotasıdır). Mevcut `ix_game_ratings_game_id` ve `ix_game_recommendations_game_id` kullanılır (disposable PostgreSQL'de 10.000 puan + 4.000 tavsiye + 2.000 inceleme için ~3,8 ms, sıcak yerel ölçüm; üretim SLA'sı değildir). Dış servis çağrısı yoktur.
- Davranış: Puan PUT güncellemesi kullanıcının kovasını taşır, sayıyı artırmaz; aynı değerin tekrarı değişiklik üretmez. Tavsiye değişikliği iki sayıyı tutarlı kaydırır. Metin silme yalnız `activeCount`'u düşürür; puan/tavsiye korunur. Hesap silme kullanıcının puan, tavsiye ve metnini aynı mevcut transaction'da kaldırdığından sonraki okuma doğru değerleri verir. Gizlenmiş/arşivlenmiş durumlar şemada yoktur; REV-15 politikası kararlaştırılana kadar `activeCount` yalnız `VISIBLE` sayar.
#### Test Durumu
UNIT/INTEGRATION — PostgreSQL 17: boş sonuç, dağılım/kova toplamı, inceleme–puan–tavsiye ayrımı, REV-08 ortalamasıyla eşitlik, yüzde yuvarlama (33,33/66,67 ve 0,13/99,88), puan/tavsiye güncellemesi, metin silme bağımsızlığı, hesap silme, 10.000 puan/tek SQL (JdbcClient spy), eşzamanlı yazıcılarla iç tutarlılık, 401/403/400/405; gerçek TCP/RS256/JWKS ile kullanıcı token'ı 200, svc+jwt ve anonim 401. Güncel kalite sonuçları için ortak doğrulama notuna bakın.

### REVIEW — Hesap verisi silme iç ucu

Durum: IMPLEMENTED
Sorumlu: Hamza
Tüketici servisler: auth-service

#### Endpoint
DELETE /internal/v1/accounts/{accountId}

#### Yetkilendirme
Internal; Gateway'e yönlendirilmez. RS256/Auth JWKS, tam `typ=svc+jwt`,
`iss=ltz-auth` (JWT_ISSUER), tek `aud=review-service`, dolu `kid/jti`,
ham `iat`, `nbf/exp`, pozitif ve en çok 60 saniye ömür, en çok 30 saniye saat toleransı.
Yalnız `SCOPE_account:erase`; tam `sub=svc:auth-service` ve
`account_id` = yol UUID'si zorunlu. Kullanıcı token'ı (ADMIN dahil) iç uçta,
servis token'ı kullanıcı namespace'inde reddedilir. Servis rollerinden yetki türetilmez.

#### Path Parameters
- accountId: UUID, zorunlu; token'ın account_id claim'iyle eşleşmeli.

#### Request Body
Yok.

#### Response — 204
Gövde yok; veri yoksa veya tekrar çağrıda da 204. Hedef kullanıcının sayısal puanları,
metinsel incelemeleri, tavsiyeleri, verdiği beğeniler, silinen incelemelerine ait
beğeniler ve kötüye kullanım sayaçları aynı transaction içinde silinir. Başka
kullanıcıların verileri korunur. V54 silme engeli yalnız SHA-256 hesap özetiyle
17 dakika tutulur; süresi dolan kayıtlar dakikalık görevle temizlenir. Hesap kilidi
yazmaları, kota tüketimini ve silmeyi sıralar; eski token ile yeniden veri yazma 403 olur.

#### Hata Durumları
- 400: Geçersiz yol UUID'si.
- 401: Kimlik yok, kullanıcı token'ı, geçersiz imza/issuer/audience/tür/süre veya eksik token claim'i.
- 403: Silme kapsamı yok, çağıran Auth değil veya account_id eksik/yoldaki hesaptan farklı.
- 500: Beklenmeyen sunucu hatası; silme onayı değildir.

#### Bağlantı
Frontend: Yok. Gateway route'u eklenmedi.
Diğer mikroservisler: Auth mevcut REVIEW_SERVICE_URL üzerinden RestClient ile doğrudan çağırır.
Auth yalnız 200/204'ü onaylar; bu uç gövdesiz 204 kullanır. Yeni ENV yok.
Karar ve kanıtlar: [Review Auth entegrasyon raporu](../docs-review/REVIEW_AUTH_INTEGRATION_REPORT.md),
[Review kararları](../docs-review/REVIEW_SERVICE_DECISIONS.md).

#### Test Durumu
UNIT + INTEGRATION — AccountErasureTest, ServiceTokenDecoderConfigurationTest,
AuthIssuedTokenIntegrationTest ve ilgili yarış testleri. Son Review doğrulaması:
153 test, 0 failure/error/skipped; Maven clean verify ve merkezi coverage PASS.
Auth'un gerçek token üreticisi/HTTP silme adaptörü geçici test anahtarıyla gerçek
Review HTTP sunucusuna karşı sınanır. Başka hesap verisi, kota temizliği, tekrar
silme ve eski token ile yeniden yazmanın reddi test edilir. Canlı Auth login ve
14 günlük silme worker'ı E2E: NOT VERIFIED.

## SOCIAL

Sorumlu: Umut · Gateway: `/api/v1/social/**` · Görevler: SOC-

Ortak kurallar (aşağıdaki altı uç için, görev LTZ-UMT-SOC-01 / SOC-01..SOC-06):
- `/api/v1/social/**` uçları `Authorization: Bearer <access token>` (kullanıcı token'ı, `typ=at+jwt`) ister (Gateway tarayıcı çerezini Bearer'a çevirir). `/internal/**` uçları ise yalnız Auth'un imzaladığı servis token'ını (`typ=svc+jwt`) kabul eder; iki token türü birbirinin yerine geçmez. Token servis tarafında Auth'un JWKS anahtarıyla yeniden doğrulanır (RS256, `iss`, `aud`, `typ=at+jwt`, `exp`). `userId` yalnız token'ın `sub` alanından alınır; gövde/başlıktaki kimlik (`senderId`, `X-User-Id` vb.) güvenilmez: gövdedeki bilinmeyen alan `400`, başlıklar yok sayılır.
- Kimliksiz veya geçersiz token `401 UNAUTHENTICATED`; tanımsız yol `401/403`. Hata gövdesi `ProblemDetail` + `code` alanıdır; doğrulama hatalarında `errors: [{field, code}]` döner, gönderilen değer geri yansıtılmaz.
- İstek gövdesi en fazla 8 KB'tır; aşan istek `413 PAYLOAD_TOO_LARGE`.
- İstek kaynağı (`FriendRequest`) tek tip yanıt döner:
  ```json
  {
    "id": "UUID",
    "senderId": "UUID",
    "receiverId": "UUID",
    "status": "PENDING | ACCEPTED | REJECTED | CANCELLED",
    "createdAt": "ISO-8601 (UTC)",
    "respondedAt": "ISO-8601 (UTC) veya null"
  }
  ```
- Durum makinesi: `PENDING → ACCEPTED | REJECTED | CANCELLED`; yanıtlanmış istek değişmez (`409 FRIEND_REQUEST_NOT_PENDING`). Bir isteğe yalnız tarafları dokunabilir; isteğin var olup olmadığı ve kime ait olduğu sızdırılmasın diye "yok" ve "senin değil" aynı `404 FRIEND_REQUEST_NOT_FOUND` ile döner.
- Liste uçları keyset sayfalama kullanır: `limit` (1–50, varsayılan 20), `cursor` (opak; önceki yanıttaki `nextCursor` aynen gönderilir). Sıralama `createdAt` azalan, eşitlikte `id` azalan. Yanıt: `{ "items": [FriendRequest], "nextCursor": "string veya null" }`.
- Kullanıcıların varlığı bu serviste doğrulanmaz (user-service ucu henüz yok — **PENDING**); `receiverId` yalnız UUID olarak saklanır. Aralarında engel olan kullanıcılara istek gönderilemez (SOC-09): engelleyen `409 USER_BLOCKED`, engellenen `403 FRIEND_REQUEST_NOT_ALLOWED` alır (engelin varlığı ayrıca belirtilmez).

### SOCIAL — Arkadaşlık isteği gönder (SOC-01)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/social/friend-requests

#### Yetkilendirme
Authenticated (gönderen = token sahibi)

#### Request Body
```json
{
  "receiverId": "UUID, zorunlu"
}
```

#### Response — 201
`FriendRequest` (`status: PENDING`, `respondedAt: null`).

#### Hata Durumları
- 400: `receiverId` eksik/biçimsiz (`VALIDATION_FAILED`, `MALFORMED_REQUEST`); kendine istek (`CANNOT_REQUEST_SELF`)
- 401: Kimlik yok/geçersiz (`UNAUTHENTICATED`)
- 403: Alıcı sizi engellemiş (`FRIEND_REQUEST_NOT_ALLOWED`; engelin varlığı ayrıca belirtilmez)
- 409: Siz o kullanıcıyı engellediniz (`USER_BLOCKED`, önce engeli kaldırın); Zaten arkadaş (`ALREADY_FRIENDS`); aynı kişiye bekleyen istek var (`FRIEND_REQUEST_ALREADY_PENDING`); karşı taraf zaten istek göndermiş (`INCOMING_FRIEND_REQUEST_PENDING`, önce o isteği yanıtlayın)
- 413: İstek gövdesi çok büyük
- 429: Bekleyen giden istek sınırı doldu (`TOO_MANY_PENDING_REQUESTS`; varsayılan 100)
- 500: Beklenmeyen sunucu hatası

Aynı kullanıcı çifti arasında yönden bağımsız en fazla bir bekleyen istek olabilir; bu kural veritabanında kısmi tekil indeksle de zorlanır (eşzamanlı istekte kaybeden `409` alır). Reddedilen/iptal edilen istekten sonra yeniden istek gönderilebilir.

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friend-requests

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL ile; social-service 218 test PASS)

### SOCIAL — Arkadaşlık isteğini kabul et (SOC-02)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/social/friend-requests/{requestId}/accept

#### Yetkilendirme
Owner (yalnız isteğin alıcısı)

#### Path Parameters
- requestId: UUID, zorunlu

#### Response — 200
`FriendRequest` (`status: ACCEPTED`, `respondedAt` dolu). Aynı işlemde karşılıklı arkadaşlık kaydı oluşur (çift başına tek satır).

#### Hata Durumları
- 400: Geçersiz `requestId` (`MALFORMED_REQUEST`)
- 401: Kimlik yok/geçersiz
- 404: İstek yok veya çağıran alıcı değil (`FRIEND_REQUEST_NOT_FOUND`)
- 409: İstek zaten yanıtlanmış/iptal edilmiş (`FRIEND_REQUEST_NOT_PENDING`); eşzamanlı değişiklik (`CONCURRENT_MODIFICATION`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friend-requests/{requestId}/accept

Diğer mikroservisler:
`FriendshipChanged` olayı ve bildirim (SOC-13) — **PENDING**.

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL ile; social-service 218 test PASS)

### SOCIAL — Arkadaşlık isteğini reddet (SOC-03)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/social/friend-requests/{requestId}/reject

#### Yetkilendirme
Owner (yalnız isteğin alıcısı)

#### Path Parameters
- requestId: UUID, zorunlu

#### Response — 200
`FriendRequest` (`status: REJECTED`, `respondedAt` dolu). Arkadaşlık oluşmaz.

#### Hata Durumları
- 400: Geçersiz `requestId`
- 401: Kimlik yok/geçersiz
- 404: İstek yok veya çağıran alıcı değil (`FRIEND_REQUEST_NOT_FOUND`)
- 409: `FRIEND_REQUEST_NOT_PENDING`; `CONCURRENT_MODIFICATION`
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friend-requests/{requestId}/reject

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL ile; social-service 218 test PASS)

### SOCIAL — Arkadaşlık isteğini iptal et (SOC-04)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/social/friend-requests/{requestId}/cancel

#### Yetkilendirme
Owner (yalnız isteği gönderen)

#### Path Parameters
- requestId: UUID, zorunlu

#### Response — 200
`FriendRequest` (`status: CANCELLED`, `respondedAt` dolu). Yalnız henüz yanıtlanmamış istek iptal edilebilir.

#### Hata Durumları
- 400: Geçersiz `requestId`
- 401: Kimlik yok/geçersiz
- 404: İstek yok veya çağıran gönderen değil (`FRIEND_REQUEST_NOT_FOUND`)
- 409: İstek zaten kabul/red edilmiş ya da iptal edilmiş (`FRIEND_REQUEST_NOT_PENDING`); `CONCURRENT_MODIFICATION`
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friend-requests/{requestId}/cancel

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL ile; social-service 218 test PASS)

### SOCIAL — Gelen bekleyen istekler (SOC-05)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/social/friend-requests/incoming

#### Yetkilendirme
Authenticated (yalnız token sahibine gelen istekler döner)

#### Query Parameters
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel, önceki yanıttaki `nextCursor`

#### Response — 200
`{ "items": [FriendRequest], "nextCursor": "string | null" }` — yalnız `PENDING` istekler, en yeniden eskiye.

#### Hata Durumları
- 400: Sınır dışı `limit` (`INVALID_PAGE_SIZE`), bozuk `cursor` (`INVALID_CURSOR`)
- 401: Kimlik yok/geçersiz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friend-requests/incoming

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL ile; social-service 218 test PASS)

### SOCIAL — Gönderilen bekleyen istekler (SOC-06)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/social/friend-requests/outgoing

#### Yetkilendirme
Authenticated (yalnız token sahibinin gönderdiği istekler döner)

#### Query Parameters
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel, önceki yanıttaki `nextCursor`

#### Response — 200
`{ "items": [FriendRequest], "nextCursor": "string | null" }` — yalnız `PENDING` istekler, en yeniden eskiye.

#### Hata Durumları
- 400: Sınır dışı `limit` (`INVALID_PAGE_SIZE`), bozuk `cursor` (`INVALID_CURSOR`)
- 401: Kimlik yok/geçersiz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friend-requests/outgoing

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL ile; social-service 218 test PASS)

### SOCIAL — Arkadaş listesi (SOC-07)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/social/friends

#### Yetkilendirme
Authenticated (yalnız token sahibinin kendi arkadaşları döner)

#### Query Parameters
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel, önceki yanıttaki `nextCursor`

#### Response — 200
```json
{
  "items": [
    { "friendId": "UUID", "since": "ISO-8601 (UTC) — arkadaşlığın başlangıcı" }
  ],
  "nextCursor": "string veya null"
}
```
En yeni arkadaşlıktan eskiye sıralıdır. Yalnız kimlik döner; ad/avatar gibi profil bilgisi user-service'tedir. Başkasının listesi için `GET /api/v1/social/users/{userId}/friends` (SOC-12) kullanılır.

#### Hata Durumları
- 400: Sınır dışı `limit` (`INVALID_PAGE_SIZE`), bozuk `cursor` (`INVALID_CURSOR`)
- 401: Kimlik yok/geçersiz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friends

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Arkadaşlıktan çıkar (SOC-08)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/social/friends/{friendId}

#### Yetkilendirme
Owner (yalnız arkadaşlığın iki tarafından biri; işlem token sahibi ile `friendId` çiftine bağlıdır)

#### Path Parameters
- friendId: UUID, zorunlu

#### Response — 204
Gövde yok. Arkadaşlık kaydı silinir; iki tarafın listesinden de düşer. İstek geçmişi korunur; sonrasında yeniden istek gönderilebilir.

#### Hata Durumları
- 400: Geçersiz `friendId` (`MALFORMED_REQUEST`)
- 401: Kimlik yok/geçersiz
- 404: Kullanıcıyla arkadaş değil, ya da kendisi (`FRIENDSHIP_NOT_FOUND`). Başkalarının arkadaşlığı bu uçla etkilenemez.
- 500: Beklenmeyen sunucu hatası

Eşzamanlı iki çıkarma isteğinden yalnız biri `204`, diğeri `404` alır.

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friends/{friendId}

Diğer mikroservisler:
`FriendshipChanged` (`change: REMOVED`) olayı outbox'a yazılır — teslim **PENDING**, bkz. "Giden olay sözleşmeleri".

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Davet kodumu getir (SOC-14)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/social/invite-code

#### Yetkilendirme
Authenticated (yalnız token sahibinin kendi kodu)

#### Response — 200
```json
{
  "code": "10 karakter, Crockford base32 (0-9 A-Z, I L O U yok)",
  "createdAt": "ISO-8601 (UTC)",
  "expiresAt": "ISO-8601 (UTC)",
  "maxUses": "int — bu kodla en fazla kaç istek oluşturulabilir",
  "usedCount": "int"
}
```

#### Hata Durumları
- 401: Kimlik yok/geçersiz
- 404: Etkin ve kullanılabilir kod yok (hiç yok, iptal edilmiş, süresi dolmuş ya da kullanım hakkı bitmiş) (`INVITE_CODE_NOT_FOUND`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/invite-code

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Davet kodu üret (SOC-14)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/social/invite-code

#### Yetkilendirme
Authenticated

#### Response — 201 / 200
Yanıt gövdesi "Davet kodumu getir" ile aynıdır. Etkin ve kullanılabilir kod varsa **200** ile o döner (idempotent); yoksa (hiç yok, süresi dolmuş, kullanım hakkı bitmiş) eski kod iptal edilip yenisi üretilir ve **201** döner. Kullanıcının aynı anda en fazla bir etkin kodu vardır; eşzamanlı çağrılar aynı kodu görür.

Kod `SecureRandom` ile üretilir (50 bit), varsayılan geçerlilik 30 gün ve en fazla 50 kullanım (`ltz.social.invite-codes.*`).

#### Hata Durumları
- 401: Kimlik yok/geçersiz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/invite-code

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Davet kodumu iptal et (SOC-14)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/social/invite-code

#### Yetkilendirme
Authenticated (yalnız token sahibinin kendi kodu)

#### Response — 204
Gövde yok. Etkin kod iptal edilir; kod yoksa da `204` döner (idempotent). İptal edilen kod bir daha kullanılamaz; yeni kod için `POST` çağrılır.

#### Hata Durumları
- 401: Kimlik yok/geçersiz
- 409: Aynı anda yapılan bir kullanımla çakıştı (`CONCURRENT_MODIFICATION`); istek tekrarlanabilir
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/invite-code

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Davet koduyla arkadaşlık isteği gönder (SOC-14)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/social/friend-requests/by-code

#### Yetkilendirme
Authenticated (gönderen = token sahibi; alıcı = kodun sahibi)

#### Request Body
```json
{
  "code": "string, zorunlu, en fazla 32 karakter; büyük/küçük harf, boşluk ve tire fark etmez"
}
```

#### Response — 201
`FriendRequest` (`status: PENDING`) — "Arkadaşlık isteği gönder" ile aynı kayıt ve aynı kurallar. Kullanım sayacı yalnız istek gerçekten oluştuğunda artar; istek reddedilirse (ör. zaten arkadaş) işlem geri alınır.

#### Hata Durumları
- 400: `code` boş/çok uzun (`VALIDATION_FAILED`); kendi kodu (`CANNOT_REQUEST_SELF`)
- 401: Kimlik yok/geçersiz
- 404: Kod bilinmiyor, biçimsiz, iptal edilmiş, süresi dolmuş veya kullanım hakkı bitmiş — **hepsi aynı yanıt** (`INVITE_CODE_NOT_FOUND`, kodun varlığı sızdırılmaz)
- 403: Kod sahibi sizi engellemiş (`FRIEND_REQUEST_NOT_ALLOWED`)
- 409: Siz kod sahibini engellediniz (`USER_BLOCKED`); Zaten arkadaş (`ALREADY_FRIENDS`); bekleyen istek var (`FRIEND_REQUEST_ALREADY_PENDING`, `INCOMING_FRIEND_REQUEST_PENDING`)
- 413: İstek gövdesi çok büyük
- 429: Bekleyen giden istek sınırı doldu (`TOO_MANY_PENDING_REQUESTS`)
- 500: Beklenmeyen sunucu hatası

Engelleme kuralı (SOC-09) normal istek ucuyla aynı yerde (`FriendRequestService.send`) uygulandığı için bu akışı da kapsar; engel varsa kullanım sayacı artmaz.

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/friend-requests/by-code

Diğer mikroservisler:
`FriendRequestSent` olayı outbox'a yazılır — teslim **PENDING**.

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Kullanıcıyı engelle (SOC-09)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/social/blocks

#### Yetkilendirme
Authenticated (engelleyen = token sahibi)

#### Request Body
```json
{
  "blockedUserId": "UUID, zorunlu"
}
```

#### Response — 201 / 200
```json
{ "blockedUserId": "UUID", "createdAt": "ISO-8601 (UTC)" }
```
Yeni engelde **201**, zaten engelliyse **200** (idempotent; yan etki ve olay tekrarlanmaz).

Engel yönlüdür ama kısıtlama **iki yönde** uygulanır. Engellenince aynı işlemde:
- Aralarındaki bekleyen istekler kapanır: engelleyenin gönderdiği `CANCELLED`, engellenenden gelen `REJECTED` olur.
- Arkadaşlık varsa biter (`FriendshipChanged` `REMOVED` olayı yazılır).
- `UserBlocked` olayı yazılır. Engellenen kullanıcıya bildirim gitmez.

Sonrasında: engelleyen o kişiye istek gönderemez (`409 USER_BLOCKED`, önce engeli kaldırmalı); engellenen, engelleyene istek gönderemez (`403 FRIEND_REQUEST_NOT_ALLOWED` — engelin varlığı ayrıca belirtilmez); aynı kurallar davet koduyla istek akışı için de geçerlidir. Engellenen, engelleyenin arkadaş listesini göremez (SOC-12). Engel ile eşzamanlı istek/kabul işlemleri çift kilidiyle sıralanır; engel konduktan sonra aralarında bekleyen istek ya da arkadaşlık kalamaz.

#### Hata Durumları
- 400: `blockedUserId` eksik/biçimsiz (`VALIDATION_FAILED`, `MALFORMED_REQUEST`); kendini engelleme (`CANNOT_BLOCK_SELF`)
- 401: Kimlik yok/geçersiz
- 413: İstek gövdesi çok büyük
- 500: Beklenmeyen sunucu hatası

Kullanıcının varlığı doğrulanmaz (user-service ucu yok — **PENDING**).

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/blocks

Diğer mikroservisler:
`UserBlocked` olayı outbox'a yazılır (messaging, lfp, community, notification) — teslim **PENDING**. messaging/lfp'nin engel sorgusu için bkz. "Giden sözleşme taslağı: engel sorgusu".

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Engeli kaldır (SOC-10)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/social/blocks/{blockedUserId}

#### Yetkilendirme
Owner (yalnız engeli koyan; yön önemlidir — engellenen kişi kendisini engelleyenin engelini kaldıramaz)

#### Path Parameters
- blockedUserId: UUID, zorunlu

#### Response — 204
Gövde yok. Engel silinir ve `UserUnblocked` olayı yazılır. Karşı tarafın kendi koyduğu engel (varsa) sürer. Biten arkadaşlık ve kapanan istekler geri gelmez; yeniden istek gönderilebilir.

#### Hata Durumları
- 400: Geçersiz `blockedUserId` (`MALFORMED_REQUEST`)
- 401: Kimlik yok/geçersiz
- 404: Bu kullanıcıyı engellememişsiniz (`BLOCK_NOT_FOUND`); ikinci kaldırma da `404`
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/blocks/{blockedUserId}

Diğer mikroservisler:
`UserUnblocked` olayı outbox'a yazılır — teslim **PENDING**.

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Engellenen kullanıcılar (SOC-11)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/social/blocks

#### Yetkilendirme
Authenticated (yalnız token sahibinin kendi koyduğu engeller; kimin kimi engellediği başkasına gösterilmez)

#### Query Parameters
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel, önceki yanıttaki `nextCursor`

#### Response — 200
```json
{
  "items": [ { "blockedUserId": "UUID", "createdAt": "ISO-8601 (UTC)" } ],
  "nextCursor": "string veya null"
}
```
En yeni engelden eskiye sıralıdır. Yalnız kimlik döner; ad/avatar user-service'tedir.

#### Hata Durumları
- 400: Sınır dışı `limit` (`INVALID_PAGE_SIZE`), bozuk `cursor` (`INVALID_CURSOR`)
- 401: Kimlik yok/geçersiz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/blocks

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Başka kullanıcının arkadaş listesi (SOC-12)

Durum: IMPLEMENTED (gizlilik tercihi adaptörü **PENDING** — başkasının listesi şu an fail-closed gizli)
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/social/users/{userId}/friends

#### Yetkilendirme
Authenticated + görünürlük kuralı (aşağıda). `userId` token sahibi ise kendi listesidir ve her zaman görünür.

#### Path Parameters
- userId: UUID, zorunlu — listesi istenen kullanıcı

#### Query Parameters
- limit: int, opsiyonel, varsayılan 20, 1–50
- cursor: string, opsiyonel

#### Response — 200
`GET /friends` ile aynı gövde (`items: [{ friendId, since }]`, `nextCursor`).

Görünürlük user-service'in `privacy_settings.friends_visibility` tercihiyle belirlenir (Social bu tercihi saklamaz):
- **Kendi listesi:** her zaman görünür, tercih sorulmaz.
- **Aralarında (herhangi bir yönde) engel varsa:** görünmez.
- Aksi halde tercih: `PUBLIC` → giriş yapmış herkes; `FRIENDS` → yalnız arkadaşlar; `PRIVATE` → kimse.
- Tercih okunamazsa (user-service ulaşılamaz / uç yok): **fail-closed**, `PRIVATE` sayılır.

#### Hata Durumları
- 400: Geçersiz `userId`, sınır dışı `limit`, bozuk `cursor`
- 401: Kimlik yok/geçersiz
- 403: Liste bu kullanıcıya görünür değil (`FRIENDS_LIST_NOT_VISIBLE`). Engel, gizlilik tercihi ve olmayan kullanıcı **aynı yanıtı** verir (hiçbiri sızdırılmaz).
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/social/users/{userId}/friends

Diğer mikroservisler:
user-service'ten görünürlük tercihi okunacak — **PENDING**: user-service henüz bir uç sunmuyor ve servisler arası kimlik (D4) hazır değil. Bu yüzden `ltz.social.privacy.adapter=unavailable` varsayılandır ve başkasının listesi görünmez. Gerçek adaptör (`FriendsVisibilityPort` uygulaması) eklenince kurallar aynen çalışır (kurallar sahte adaptörle testlidir).

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS)

### SOCIAL — Engel sorgusu (Internal, messaging/lfp → Social) (SOC-15)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: messaging-service (MSG-15), lfp-service (LFP-17)

Social, engelleme verisinin tek sahibidir; diğer servisler Social veritabanına erişmez. Messaging ve LFP, bir kullanıcı ile bir grup kullanıcı arasında engel olup olmadığını bu uçla sorar (N+1 çağrı yerine toplu).

#### Endpoint
POST /internal/v1/blocks/check

#### Yetkilendirme
Internal. Gateway'e açık **değildir** (`/internal/**` Gateway'de route edilmez). Yalnız Auth'un imzaladığı **servis token'ı** kabul edilir (`docs-auth/entegrasyon/README.md` bölüm 3):
- JWT başlığı `typ=svc+jwt`; RS256, Auth'un JWKS anahtarıyla doğrulanır.
- `iss=ltz-auth`, `aud` içinde `social-service`, `jti` zorunlu, ömür en fazla 60 sn.
- `scope` içinde `blocks:check` (eksik/farklı kapsam `403`). Silme token'ının `account:erase` kapsamı bu uca yetmez.
- `sub` **tam olarak** `svc:messaging-service` ya da `svc:lfp-service` olmalı; kapsam doğru olsa bile başka bir servis kimliği `403` alır.
- Kullanıcı token'ı (`at+jwt`) bu uçta **kabul edilmez**; servis token'ı da kullanıcı uçlarında geçmez.
- Auth tarafında iki istemci `audience: "social-service"` ile bu token'ı alabilir; gizli anahtar değişkenleri `AUTH_MESSAGING_SERVICE_CLIENT_SECRET` ve `AUTH_LFP_SERVICE_CLIENT_SECRET`.

#### Request Body
```json
{
  "userId": "UUID (zorunlu)",
  "otherUserIds": ["UUID", "... en az 1, en fazla 100"]
}
```
Tanımsız alan reddedilir.

#### Response — 200
```json
{
  "results": [
    { "userId": "UUID", "blocked": true }
  ]
}
```
- `results[].userId`, sorgulanan **diğer** kullanıcıdır; `blocked`, istekteki `userId` ile arasında **herhangi bir yönde** engel varsa `true`.
- Yinelenen kimlikler tek sonuç verir; sonuçlar ilk görülme sırasıyla döner. `userId` kendi listesinde yer alırsa `blocked=false` olur.
- Engelin yönü ve zamanı yanıtta yoktur (sızdırılmaz).

#### Hata Durumları
- 400: Eksik/boş `otherUserIds`, 100'den fazla kimlik, UUID olmayan değer, tanımsız alan (`VALIDATION_FAILED` / `MALFORMED_REQUEST`)
- 401: Token yok/geçersiz; kullanıcı token'ı; yanlış `iss`/`aud`/`typ`; süresi dolmuş veya 60 sn'den uzun ömürlü; bozuk imza
- 403: Geçerli servis token'ı ama `blocks:check` kapsamı yok ya da çağıran messaging/lfp değil; bu yolda başka yöntem (GET)
- 413: Gövde 8KB sınırını aşıyor
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
messaging-service ve lfp-service Auth'tan `POST /internal/v1/service-tokens` ile (`audience: "social-service"`) 60 sn'lik token alıp bu ucu `${SOCIAL_SERVICE_URL}/internal/v1/blocks/check` adresinde çağırır. messaging-service istemcisi (`SocialBlockCheckClient`, LTZ-UMT-MSG-01) yazıldı ve MockRestServiceServer ile testli; lfp-service tarafı iskelet. Gerçek Auth token'ıyla uçtan uca çağrı **NOT VERIFIED**.

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; `BlocksCheckTest` 13 test: iki yön, engel kaldırma, başkasının engeli, yinelenen kimlik, 100/101 sınırı, geçersiz gövde, kimlik/kapsam/çağıran/`aud`/`iss`/`typ` ihlalleri). Gerçek Auth token'ıyla uçtan uca: NOT VERIFIED.

### SOCIAL — Hesap verisi silme (Internal, Auth → Social)

Durum: IMPLEMENTED (alıcı taraf)
Sorumlu: Umut
Tüketici servisler: auth-service (hesap silme koordinasyonu, `ltz.auth.deletion.targets` içinde `social` var)

#### Endpoint
DELETE /internal/v1/accounts/{accountId}

#### Yetkilendirme
Internal. Gateway'e açık **değildir** (Gateway yalnız `/api/v1/**` route eder; `/internal/**` 404). Yalnız Auth'un imzaladığı **servis token'ı** kabul edilir (`docs-auth/servis-sozlesmeleri.md` §3.1):
- JWT başlığı `typ=svc+jwt`; RS256, Auth'un JWKS anahtarıyla doğrulanır.
- `iss=ltz-auth`, `aud` içinde `social-service`, `jti` zorunlu.
- `scope` içinde `account:erase` (eksik/farklı kapsam `403`).
- `sub` **tam olarak** `svc:auth-service` olmalı: silme yetkisi yalnız Auth'tadır, `svc:admin-service` gibi başka bir servis kimliği `403` alır.
- `account_id` claim'i yoldaki `{accountId}` ile **aynı** olmalı (yol UUID olarak çözülüp küçük harfle karşılaştırılır); eksik ya da başka hesap `403` verir, çalınan/yeniden oynatılan bir token başka hesabı silemez.
- Ömür en fazla 60 sn: `exp - iat ≤ 60 sn` ve `exp`, şimdiden en fazla 60 sn (+30 sn saat sapması) ileride.
- Kullanıcı token'ı (`at+jwt`) bu uçta **kabul edilmez**, servis token'ı da kullanıcı uçlarında geçmez (`typ` ve yol bazlı doğrulayıcı ayrımı).

#### Path Parameters
- accountId: UUID, zorunlu — silinecek hesap

#### Response — 204
Gövde yok. Hesaba ait tüm Social verisi tek transaction'da kalıcı olarak silinir: gönderdiği ve aldığı arkadaşlık istekleri (her durumda), arkadaşlıkları, koyduğu ve kendisine konan engeller, davet kodları ve yükünde hesabın kimliği geçen outbox olayları. **İdempotenttir:** veri yoksa, ikinci çağrıda ya da bilinmeyen hesapta da `204` döner (Auth yalnız 2xx'i "silindi" sayar). Başka kullanıcıların kendi verisi (ör. arkadaşın kendi istekleri/kodu) korunur.

#### Hata Durumları
- 400: Geçersiz `accountId` (`MALFORMED_REQUEST`)
- 401: Token yok/geçersiz; kullanıcı token'ı; yanlış `iss`/`aud`/`typ`; süresi dolmuş veya 60 sn'den uzun ömürlü; `sub` `svc:` ile başlamıyor; `jti` yok; bozuk/yanlış anahtarlı imza; `alg=none`/HS256
- 403: Geçerli servis token'ı ama `account:erase` kapsamı yok; `sub` `svc:auth-service` değil; `account_id` yok ya da yoldaki hesapla aynı değil (hiçbir şey silinmez); bu yolda başka yöntem (GET/POST) ya da başka iç yol
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth, silme onaylandığında bu ucu `${SOCIAL_SERVICE_URL}/internal/v1/accounts/{accountId}` adresine RestClient ile çağırır. Auth tarafı (üretici ve adaptör) main'e alındı; gerçek Auth token'ıyla canlı silme **NOT VERIFIED**.

Not: Silme, "silinen hesaba sonradan istek gönderilmesi" ihtimalini ortadan kaldırmaz; kullanıcı varlık doğrulaması (user-service ucu) hâlâ **PENDING**. Social, silinen hesabın arkadaşlarına `FriendshipChanged REMOVED` olayı yazmaz (olay yükü silinen kimliği taşıyacağından); messaging gibi servisler kendi silme çağrısını Auth'tan alır.

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; `AccountErasureTest` 17 test, `sub` tam eşleşme ve `account_id` denetimi dahil). Gerçek Auth servis token'ıyla uçtan uca: NOT VERIFIED.

### SOCIAL — Giden olay sözleşmeleri (SOC-13, teslim kısmen)

Durum: IN PROGRESS (olaylar outbox'a yazılıyor; `UserBlocked`/`UserUnblocked` community'ye teslim edilebilir, diğer türlerin hedefi yok)
Sorumlu: Umut
Tüketici servisler: notification-service, messaging-service, recommendation-service

Bunlar Social'in **yayımladığı** olay sözleşmeleridir; tüketici servislerin kaydı kendi başlıkları altında açılır. Olaylar iş verisiyle aynı transaction'da `outbox_events` tablosuna yazılır (olay kaybı ve hayalet olay yoktur) ve en az bir kez teslim edilir; tüketici `eventId` ile tekilleştirir.

Zarf (docs/architecture.md §6.2): `eventId`, `eventType`, `eventVersion`, `occurredAt` (UTC), `producer: "social-service"`, `aggregateId`, `payload`. Yük yalnız kimlik/enum taşır; ad, e-posta, mesaj gibi kişisel veri yoktur.

| Olay | Ne zaman | `payload` | Tüketici |
| --- | --- | --- | --- |
| `FriendRequestSent` v1 | Yeni istek oluştu (normal ya da kodla) | `requestId`, `senderId`, `receiverId` | notification (alıcıya bildirim) |
| `FriendRequestAccepted` v1 | İstek kabul edildi | `requestId`, `senderId`, `receiverId` | notification (isteği gönderene bildirim) |
| `FriendshipChanged` v1 | Arkadaşlık kuruldu / bitti | `userLowId`, `userHighId`, `change` (`ADDED` \| `REMOVED`) | messaging (sohbet izni), recommendation (arkadaş önerisi) |
| `UserBlocked` v1 | Kullanıcı bir hesabı engelledi | `blockerId`, `blockedId` | messaging, lfp, community (etkileşimleri durdurur), notification (engellenenden bildirim üretmez) |
| `UserUnblocked` v1 | Engel kaldırıldı | `blockerId`, `blockedId` | messaging, lfp, community, notification |

Red ve iptal olay üretmez (reddedilen kişiye bildirim gitmez). Engellenen kullanıcıya hiçbir bildirim/olay gönderilmez; engel olayları yalnız etkileşimi durduracak servisler içindir.

Teslim durumu:
- **`UserBlocked`, `UserUnblocked` → community-service:** Gerçek adaptör `CommunityEventPublisher` (`ltz.social.outbox.adapter=community`, varsayılan). Her teslim, Auth'tan `POST /internal/v1/service-tokens` ile (`clientId: "social-service"`, `audience: "community-service"`) alınan 60 sn'lik token'la `POST {COMMUNITY_SERVICE_URL}/internal/events` ucuna yapılır (`scope=events:ingest`, `sub=svc:social-service`). Gizli anahtar `AUTH_SOCIAL_SERVICE_CLIENT_SECRET` ortam değişkeninden gelir; boşsa relay hiç olay denemez ve deneme hakkı yanmaz. 2xx dışındaki her yanıt (3xx dahil) teslim sayılmaz ve olay üstel geri çekilmeyle yeniden denenir.
- **`FriendRequestSent`, `FriendRequestAccepted`, `FriendshipChanged`:** Auth'ta social → notification/messaging/recommendation çifti **tanımlı değil** ve alıcı uçlar yok; bu türler `No delivery target` ile teslim edilemez, relay açılırsa en fazla 10 denemeden sonra `FAILED` olur (el ile müdahale bekler). Hedef tanımlanınca `CommunityEventPublisher`'a yönlendirme eklenir.
- Relay (`ltz.social.outbox.relay-enabled`, ENV `SOCIAL_OUTBOX_RELAY_ENABLED`) varsayılan **kapalı**; community alıcı ucu gerçek token'la doğrulanınca açılır. Relay mantığı (üstel geri çekilme, en fazla 10 deneme sonrası `FAILED`, `SKIP LOCKED` ile çift teslim önleme) testlidir.

Not: Social bu olayları üretir; `notification-service`/`messaging-service` tarafında tüketici uç eklenmesi o servislerin sahiplerine bildirilmelidir (notification-service ortak/sahipsizdir).

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; social-service 218 test PASS; `CommunityEventPublisherTest` ve `AuthServiceTokenClientTest` giden çağrıyı MockRestServiceServer ile doğrular). Community ve Auth ile gerçek uçtan uca teslim: NOT VERIFIED.

## MESSAGING

Sorumlu: Umut · Gateway: `/api/v1/messaging/**` · Görevler: MSG-

Ortak kurallar (aşağıdaki yedi uç için, görev LTZ-UMT-MSG-01 / MSG-01..05, MSG-14, MSG-15):
- Tüm uçlar `Authorization: Bearer <access token>` (kullanıcı token'ı, `typ=at+jwt`) ister; Gateway tarayıcı çerezini Bearer'a çevirir. Token servis tarafında Auth'un JWKS anahtarıyla yeniden doğrulanır (RS256, `iss`, `aud`, `typ=at+jwt`, `exp`/`nbf`/`iat`/`sub` zorunlu, 30 sn saat payı). Servis token'ı (`svc+jwt`) bu uçlarda geçmez. Messaging'in henüz iç (`/internal/**`) ucu yoktur; bu yollar kapalıdır.
- `userId` yalnız token'ın `sub` alanından alınır; gövde/başlıktaki kimlik (`senderId`, `ownerId`, `X-User-Id` vb.) güvenilmez: gövdedeki bilinmeyen alan `400`, başlıklar yok sayılır.
- Kimliksiz/geçersiz token `401 UNAUTHENTICATED`. Hata gövdesi `ProblemDetail` + `code`; doğrulama hatalarında `errors: [{field, code}]` döner, gönderilen değer geri yansıtılmaz. İstek gövdesi en fazla 8 KB (`413 PAYLOAD_TOO_LARGE`).
- **Erişim (MSG-14):** Sohbete yalnız **aktif üyesi** erişir. Hiç üye olmamış, ayrılmış ya da çıkarılmış kullanıcı ile var olmayan sohbet **aynı** `404 CONVERSATION_NOT_FOUND` alır (sohbetin varlığı ve türü sızdırılmaz). Yazma (mesaj uçları MSG-02 görevinde) ayrıca birebir sohbette iki taraf arasında engel olmamasını ister.
- **Engel (MSG-15):** Engel verisi social-service'tedir; Messaging saklamaz, her etkileşimde `POST /internal/v1/blocks/check` ile sorar. İki kullanıcı arasında **herhangi bir yönde** engel varsa: birebir sohbet açılamaz (yeni ya da mevcut), engelli kişi gruba eklenemez (grup kurarken de) → `403 MESSAGING_NOT_ALLOWED` (engelin yönü ve hangi kişide olduğu söylenmez). Grupta yalnız **ekleyen ↔ eklenen** çifti denetlenir; mevcut grup üyeliği engelden etkilenmez. Engel durumu öğrenilemezse (Auth gizli anahtarı yok, Auth/Social ulaşılamaz ya da yanıt eksik) işlem **fail-closed** `503 BLOCK_CHECK_UNAVAILABLE` ile reddedilir.
- **Roller:** Birebir sohbette iki taraf da `MEMBER`'dır. Grupta tek `OWNER` (oluşturan) vardır; `OWNER` ve `ADMIN` üye ekler, `OWNER` herkesi, `ADMIN` yalnız `MEMBER`'ı çıkarır; rolü yalnız `OWNER` değiştirir. Grup işlemleri birebir sohbette `409 GROUP_CONVERSATION_REQUIRED`.
- Grup en fazla **100 üye** (sahip dahil, `ltz.messaging.groups.max-members`). Kullanıcıların varlığı doğrulanmaz (user-service ucu yok — **PENDING**); kimlikler yalnız UUID (REF) olarak saklanır.
- Sohbet kaynağı (`Conversation`) tek tip yanıt döner:
  ```json
  {
    "id": "UUID",
    "type": "DIRECT | GROUP",
    "title": "string (yalnız GROUP) veya null",
    "createdBy": "UUID",
    "createdAt": "ISO-8601 (UTC)",
    "myRole": "OWNER | ADMIN | MEMBER (isteyen kullanıcının rolü)",
    "members": [ { "userId": "UUID", "role": "OWNER | ADMIN | MEMBER", "joinedAt": "ISO-8601 (UTC)" } ]
  }
  ```
  `members` yalnız aktif üyelerdir, katılma sırasıyla. Ad/avatar user-service'tedir.

### MESSAGING — Birebir sohbet aç (MSG-01)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend (ileride LFP-16 "ilan sahibiyle iletişim" bu uca yönlendirir)

#### Endpoint
POST /api/v1/messaging/conversations/direct

#### Yetkilendirme
Authenticated (açan = token sahibi)

#### Request Body
```json
{
  "userId": "UUID, zorunlu — karşı taraf"
}
```

#### Response — 201 / 200
`Conversation` (`type: DIRECT`, `title: null`, iki üye). İki kullanıcı arasında sohbet yoksa oluşturulur (**201**), varsa aynı sohbet döner (**200**); yön önemli değildir (A→B ve B→A aynı sohbet). Eşzamanlı açılışlar tek sohbette buluşur (çift advisory kilidi + `uq_conversations_direct_key`). Arkadaşlık şartı yoktur; yalnız engel denetlenir.

#### Hata Durumları
- 400: `userId` eksik/biçimsiz, tanımsız alan (`VALIDATION_FAILED`, `MALFORMED_REQUEST`); kendisiyle sohbet (`CANNOT_MESSAGE_SELF`)
- 401: Kimlik yok/geçersiz (`UNAUTHENTICATED`)
- 403: Aralarında herhangi bir yönde engel var (`MESSAGING_NOT_ALLOWED`; mevcut sohbet de açılmaz)
- 413: İstek gövdesi çok büyük
- 503: Engel durumu öğrenilemedi (`BLOCK_CHECK_UNAVAILABLE`); sohbet oluşturulmaz
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/messaging/conversations/direct

Diğer mikroservisler:
social-service `POST /internal/v1/blocks/check` (RestClient). Token: Auth `POST /internal/v1/service-tokens` (`clientId: "messaging-service"`, `audience: "social-service"`, `scope=blocks:check`, 60 sn); gizli anahtar `AUTH_MESSAGING_SERVICE_CLIENT_SECRET`.

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; messaging-service 80 test PASS; engel Social yerine test adaptörüyle, gerçek Social istemcisi MockRestServiceServer ile). Gerçek Auth + Social ile uçtan uca: NOT VERIFIED.

### MESSAGING — Grup sohbeti oluştur (MSG-02)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/messaging/conversations/groups

#### Yetkilendirme
Authenticated (oluşturan = token sahibi, `OWNER` olur)

#### Request Body
```json
{
  "title": "string, zorunlu, 1–100 karakter, kontrol karakteri yok (baş/son boşluk kırpılır)",
  "memberIds": ["UUID", "... en az 1, en fazla 99; oluşturan dışındaki ilk üyeler"]
}
```
Yinelenen kimlikler tek sayılır.

#### Response — 201
`Conversation` (`type: GROUP`, `myRole: OWNER`, diğer üyeler `MEMBER`).

#### Hata Durumları
- 400: Boş/uzun/kontrol karakterli başlık, boş ya da 99'dan uzun liste, `null`/biçimsiz kimlik, tanımsız alan (`VALIDATION_FAILED`, `MALFORMED_REQUEST`); oluşturan listede (`CANNOT_ADD_SELF`)
- 401: Kimlik yok/geçersiz
- 403: Oluşturan ile listedeki biri arasında engel var (`MESSAGING_NOT_ALLOWED`; hiçbir kayıt oluşmaz)
- 409: Üye sınırı aşılıyor (`GROUP_MEMBER_LIMIT_REACHED`; sınır 100'den küçük yapılandırılırsa)
- 413: İstek gövdesi çok büyük
- 503: Engel durumu öğrenilemedi (`BLOCK_CHECK_UNAVAILABLE`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/messaging/conversations/groups

Diğer mikroservisler:
social-service engel sorgusu (oluşturan ↔ tüm ilk üyeler, tek toplu çağrı).

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; messaging-service 80 test PASS). Gerçek Social ile uçtan uca: NOT VERIFIED.

### MESSAGING — Sohbet detayı (MSG-14)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
GET /api/v1/messaging/conversations/{conversationId}

#### Yetkilendirme
Authenticated + sohbetin aktif üyesi

#### Path Parameters
- conversationId: UUID, zorunlu

#### Response — 200
`Conversation` (isteyenin rolü `myRole`'de).

#### Hata Durumları
- 400: Geçersiz `conversationId` (`MALFORMED_REQUEST`)
- 401: Kimlik yok/geçersiz
- 404: Sohbet yok **ya da** aktif üyesi değilsiniz (ayrıldınız/çıkarıldınız) — aynı yanıt (`CONVERSATION_NOT_FOUND`)
- 500: Beklenmeyen sunucu hatası

Engel sorgusu yapılmaz (Social'a bağımlı değildir).

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/messaging/conversations/{conversationId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; messaging-service 80 test PASS)

### MESSAGING — Gruba üye ekle (MSG-03)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/messaging/conversations/{conversationId}/members

#### Yetkilendirme
Authenticated + grubun aktif `OWNER` ya da `ADMIN`'i

#### Path Parameters
- conversationId: UUID, zorunlu

#### Request Body
```json
{
  "userIds": ["UUID", "... en az 1, en fazla 99"]
}
```

#### Response — 200
Güncel `Conversation`. Yeni üyeler `MEMBER` olur. Daha önce ayrılmış/çıkarılmış kullanıcı yeniden eklenebilir; eski rolü geri gelmez (`MEMBER`), katılma zamanı yenilenir. İstek hep-ya-hiç işlenir.

Sıra: yetki önce denetlenir (üye olmayan ya da yetkisiz kişi engel sorgusu tetikleyemez) → engel sorgusu (ekleyen ↔ eklenenler) → sohbet satırı kilitlenip yetki, mevcut üyelik ve sınır yeniden denetlenir → kayıt.

#### Hata Durumları
- 400: Liste boş/99'dan uzun, biçimsiz kimlik (`VALIDATION_FAILED`, `MALFORMED_REQUEST`); kendinizi eklemek (`CANNOT_ADD_SELF`)
- 401: Kimlik yok/geçersiz
- 403: Rolünüz `MEMBER` (`INSUFFICIENT_GROUP_ROLE`); siz ile eklenen biri arasında engel var (`MESSAGING_NOT_ALLOWED`)
- 404: Sohbet yok ya da aktif üyesi değilsiniz (`CONVERSATION_NOT_FOUND`)
- 409: Birebir sohbet (`GROUP_CONVERSATION_REQUIRED`); listedeki biri zaten aktif üye (`ALREADY_MEMBER`, hiçbiri eklenmez); 100 üye sınırı aşılıyor (`GROUP_MEMBER_LIMIT_REACHED`)
- 413: İstek gövdesi çok büyük
- 503: Engel durumu öğrenilemedi (`BLOCK_CHECK_UNAVAILABLE`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/messaging/conversations/{conversationId}/members

Diğer mikroservisler:
social-service engel sorgusu (tek toplu çağrı).

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; messaging-service 80 test PASS). Gerçek Social ile uçtan uca: NOT VERIFIED.

### MESSAGING — Gruptan üye çıkar (MSG-05)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
DELETE /api/v1/messaging/conversations/{conversationId}/members/{userId}

#### Yetkilendirme
Authenticated + yetkili grup yöneticisi: `OWNER` herkesi (kendisi hariç), `ADMIN` yalnız `MEMBER`'ı çıkarır.

#### Path Parameters
- conversationId: UUID, zorunlu
- userId: UUID, zorunlu — çıkarılacak üye

#### Response — 204
Gövde yok. Üyelik satırı silinmez; `left_at` ve `removed_by` dolar, çıkarılanın sohbete erişimi hemen biter (sonraki isteklerinde `404`).

#### Hata Durumları
- 400: Geçersiz kimlik (`MALFORMED_REQUEST`); kendinizi çıkarmak (`CANNOT_REMOVE_SELF` — ayrılma ucunu kullanın)
- 401: Kimlik yok/geçersiz
- 403: Rolünüz bu üyeyi çıkarmaya yetmiyor (`INSUFFICIENT_GROUP_ROLE`: `MEMBER` kimseyi, `ADMIN` `ADMIN`/`OWNER`'ı çıkaramaz)
- 404: Sohbet yok ya da aktif üyesi değilsiniz (`CONVERSATION_NOT_FOUND`); hedef aktif üye değil (`MEMBER_NOT_FOUND`)
- 409: Birebir sohbet (`GROUP_CONVERSATION_REQUIRED`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/messaging/conversations/{conversationId}/members/{userId}

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; messaging-service 80 test PASS)

### MESSAGING — Üye rolünü değiştir (MSG-03)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
PUT /api/v1/messaging/conversations/{conversationId}/members/{userId}/role

#### Yetkilendirme
Authenticated + grubun `OWNER`'ı

#### Path Parameters
- conversationId: UUID, zorunlu
- userId: UUID, zorunlu — rolü değişecek üye

#### Request Body
```json
{
  "role": "ADMIN | MEMBER, zorunlu"
}
```
`OWNER` bu uçla atanamaz (sahiplik yalnız sahip ayrılınca devredilir).

#### Response — 200
Güncel `Conversation`. Aynı rolü yeniden göndermek yan etkisizdir.

#### Hata Durumları
- 400: Eksik/bilinmeyen rol (`VALIDATION_FAILED`, `MALFORMED_REQUEST`); `OWNER` istendi (`ROLE_NOT_ASSIGNABLE`); kendi rolünüz (`CANNOT_CHANGE_OWN_ROLE`)
- 401: Kimlik yok/geçersiz
- 403: Sahip değilsiniz (`INSUFFICIENT_GROUP_ROLE`)
- 404: Sohbet yok ya da aktif üyesi değilsiniz (`CONVERSATION_NOT_FOUND`); hedef aktif üye değil (`MEMBER_NOT_FOUND`)
- 409: Birebir sohbet (`GROUP_CONVERSATION_REQUIRED`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/messaging/conversations/{conversationId}/members/{userId}/role

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; messaging-service 80 test PASS)

### MESSAGING — Gruptan ayrıl (MSG-04)

Durum: IMPLEMENTED
Sorumlu: Umut
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/messaging/conversations/{conversationId}/leave

#### Yetkilendirme
Authenticated + grubun aktif üyesi

#### Path Parameters
- conversationId: UUID, zorunlu

#### Response — 204
Gövde yok. Ayrılanın erişimi biter (`left_at` dolar). **Sahip ayrılırsa** sahiplik aynı işlemde en eski `ADMIN`'e, `ADMIN` yoksa en eski üyeye geçer (katılma zamanı, eşitlikte kullanıcı kimliği). Son üye de ayrılırsa grup boş kalır ve kimse erişemez.

#### Hata Durumları
- 400: Geçersiz `conversationId` (`MALFORMED_REQUEST`)
- 401: Kimlik yok/geçersiz
- 404: Sohbet yok ya da aktif üyesi değilsiniz — ikinci ayrılma da `404` (`CONVERSATION_NOT_FOUND`)
- 409: Birebir sohbetten ayrılınmaz (`GROUP_CONVERSATION_REQUIRED`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/messaging/conversations/{conversationId}/leave

Diğer mikroservisler:
-

#### Test Durumu
UNIT + INTEGRATION (gerçek PostgreSQL; messaging-service 80 test PASS)

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
Authenticated. Gönderi sahibi (`authorId`) yalnız doğrulanmış JWT'nin `sub` claim'inden alınır; istek gövdesindeki kimlik güven kaynağı değildir. Token, Auth Service'in JWKS anahtarıyla bu serviste doğrulanır (yalnız RS256; `typ=at+jwt`, `iss=ltz-auth`, `aud=ltz-api`, `exp`/`nbf`/`iat`, `sub`); Gateway'e ve istemci başlıklarına güvenilmez. Servis token'ı (`svc+jwt`) bu uçlarda `401` alır. Gerçek Auth Service ile uçtan uca doğrulama: NOT VERIFIED.

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
Admin. Yalnız moderatör (`MODERATOR`) ya da yönetici (`ADMIN`) rolü; rol yalnız doğrulanmış JWT'nin `roles` claim'inden okunur (istemci başlıkları güven kaynağı değildir). `roles` claim'i Auth sözleşmesindeki öneksiz rol listesidir (`PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`; docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3.3). Rol, access token ömrü kadar (varsayılan 5 dk) gecikmeyle iptal olur (§7). Rolsüz kimlikli kullanıcı 403 alır.

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
Tüketici servisler: Admin Service, Social Service, Auth Service (üretici olarak); Notification Service ve Admin Service (community olaylarının alıcısı). Servis kimliği: Auth Service servis token'ı

#### Endpoint
POST /internal/events

#### Yetkilendirme
Internal. Gateway'de **route edilmez** (`/api/v1/...` altında değildir). Auth'un servis token'ı sözleşmesine (D4: `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud=community-service`, `jti`, ömür ≤ 60 sn) uyan token kabul edilir ve `SCOPE_events:ingest` kapsamı gerekir; token'ın `sub` değeri zarftaki `producer` ile **tam olarak `svc:<producer>`** olmalıdır (bir servis başkası adına olay yayınlayamaz). Kimlik yoksa/geçersizse 401, kapsam ya da çağıran uyuşmazsa 403; kullanıcı token'ı 401. `iat`, `exp`, `nbf`, `sub` zorunludur. Auth bu kapsamı **çağıran→hedef çifti başına** verir; `admin-service`, `social-service` ve `auth-service` → `community-service` çiftleri Auth tarafından hazır bildirildi (`docs-community/integration/auth/entegrasyon-v2/auth-service-talepleri/auth-requests-05.md` §6). Auth'un olay token'ındaki ek `account_id` claim'i yok sayılır. Auth'un `AccountStatusChanged` teslimi `AUTH_EVENT_TARGETS=community` eklenince başlar. Gerçek servis token'ıyla uçtan uca doğrulama NOT VERIFIED.

#### Request Body (olay zarfı, docs/architecture.md §6.2)
```json
{
  "eventId": "UUID (tekilleştirme anahtarı)",
  "eventType": "ModerationActionTaken | UserBlocked | UserUnblocked | AccountStatusChanged",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC",
  "producer": "admin-service | social-service | auth-service",
  "aggregateId": "UUID",
  "payload": { }
}
```
Zarfta tanımsız alan reddedilir. `producer`, olay türüyle eşleşmelidir (`ModerationActionTaken` yalnız `admin-service`, `UserBlocked`/`UserUnblocked` yalnız `social-service`, `AccountStatusChanged` yalnız `auth-service`); aksi halde 400.

**Kabul edilen olaylar ve yükleri**
- `ModerationActionTaken` (admin-service): `{ "targetType": "POST | COMMENT", "targetId": "UUID", "action": "HIDE | RESTORE | REMOVE | DISMISS", "reportId": "UUID (opsiyonel; yalnız tek raporu hedeflemek için)" }`. `HIDE`: VISIBLE içeriği HIDDEN yapar (yorumsa gönderinin `commentCount`'u düşer); `RESTORE`: HIDDEN içeriği geri açar (sayaç geri artar); `REMOVE`: içeriği kalıcı kaldırır (soft delete, gönderi görselleri silinir; **geri alınamaz**, silinmiş içerik `RESTORE` ile geri gelmez); `DISMISS`: içeriği değiştirmez, raporu reddeder. `HIDE`/`REMOVE` içeriğin açık raporlarını `RESOLVED`, `DISMISS` ise `DISMISSED` yapar. Bulunamayan içerik için olay başarıyla işlenir (etkisiz).
- `UserBlocked` / `UserUnblocked` (social-service): `{ "blockerId": "UUID", "blockedId": "UUID" }` (farklı olmalı). Community yerel `user_blocks` yansımasını günceller; engelleme iki yönlü uygulanır (akış/arama/profil/yorum listelerinden gizleme, beğeni/yorum/yanıt/repost/rapor engeli).
- `AccountStatusChanged` (auth-service): `{ "accountId": "UUID", "status": "ACTIVE | SUSPENDED | BANNED | DISABLED | ..." }` (Auth'un yayımladığı yük; ikisi de zorunlu, eksikse 400, bilinmeyen durum adları kabul edilir). Olay tekilleştirilir ve kaydedilir (`PROCESSED`/`DUPLICATE`) ama **içeriğe etki etmez**: askıya alınan/yasaklanan hesabın içeriği gizlenmez (Auth bunu zorunlu kılmıyor; ürün kararı bekleniyor).
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
- 403: `events:ingest` kapsamı yok ya da token'ın `sub` değeri `svc:<producer>` ile eşleşmiyor
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Admin Service, Social Service ve Auth Service bu ucu servis token'ıyla çağırır. **Community'nin yayınladığı olaylar** (outbox, aynı zarfla alıcıların `POST /internal/events` ucuna iletilir; her çağrı Auth'tan `POST /internal/v1/service-tokens` ile alınan, hedefe bağlı (`aud=admin-service` / `notification-service`) 60 sn'lik servis token'ıyla `Authorization: Bearer` başlığında yapılır; istemci kimliği `community-service`, gizli anahtar `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` ortam değişkeninden gelir ve boşsa relay hiçbir olay denemez): `ContentReported` → admin-service (`{reportId, targetType, targetId, targetAuthorId, reporterId, reason, detail, reportedAt}`); `CommentCreated` → notification-service (`{commentId, postId, parentCommentId, actorId, recipientIds[], isReply}`); `PostLiked` → notification-service (`{postId, actorId, recipientId}`); `PostReposted` → notification-service (`{postId, repostId, actorId, recipientId}`). Yükler yalnız kimlik içerir (gönderi/yorum/repost metni ve görünen ad taşınmaz); kişi kendi içeriğine yaptığı etkileşim için olay üretilmez. `PostReposted`, docs/architecture.md §6.3 olay kataloğunda henüz yoktur (kataloğa eklenmesi önerilir). Relay varsayılan KAPALIDIR; alıcı sözleşmeleri ve Auth'tan gerçek token alımı NOT VERIFIED.

#### Test Durumu
UNIT + INTEGRATION (MockMvc: yetki/doğrulama; gerçek PostgreSQL: moderasyon etkileri ve sayaçlar, tekilleştirme, üretici kontrolü, engelleme yansıması, `AccountStatusChanged` kaydı; giden çağrı: token istemcisi ve `Authorization` başlığı için MockRestServiceServer). Admin/Social/Auth Service ile uçtan uca doğrulama: NOT VERIFIED.

### COMMUNITY — Hesap Verisi Silme (İç Uç)

Durum: IMPLEMENTED
Sorumlu: Nisa
Tüketici servisler: auth-service

#### Endpoint
DELETE /internal/v1/accounts/{accountId}

#### Yetkilendirme
Internal. Gateway'de **route edilmez**; kimliksiz açılmaz. Yalnız Auth'un imzaladığı servis token'ı (`Authorization: Bearer`, D4 sözleşmesi: `docs-auth/servis-sozlesmeleri.md` §3) kabul edilir ve **tüm alanları** doğrulanır: `typ=svc+jwt`, yalnız RS256 (imza Auth JWKS'i ile), `iss=ltz-auth`, `aud=community-service` (`ltz-api` kabul edilmez), `sub=svc:auth-service` (**tam eşleşme**), `scope=account:erase` (`SCOPE_account:erase`), `account_id` = yoldaki `{accountId}` (yoksa/farklıysa `403`), `iat`/`nbf`/`exp` (`exp - iat ≤ 60 sn`, saat sapması ≤ 30 sn, `iat` zorunlu), `jti` zorunlu. `roles`, `sid`, `ver`, `amr` servis token'ında aranmaz. Kullanıcı token'ı (`at+jwt`) bu uçta `401`; servis token'ı `/api/v1/community/**` uçlarında `401`.

#### Path Parameters
- accountId: UUID, zorunlu (silinecek hesap)

#### Response — 204
Gövde yok. Kişisel veri silindi/anonimleştirildi **ya da zaten yoktu** (idempotent: ikinci çağrı ve verisi olmayan hesap da `204`). `202` ve yönlendirme (`3xx`) dönülmez; silme tek işlemde ve istek içinde biter.

#### Veri etkisi (tek `@Transactional`, set tabanlı)
- **Gönderiler ve yorumlar** (yazarı bu hesap): metin sabit `[removed]` yer tutucusuyla değiştirilir (repost notu `null`), yazar referansı "silinmiş hesap" kimliğine (`00000000-0000-0000-0000-000000000000`) çevrilir, durum `DELETED` olur; gönderi görselleri silinir. Soft delete ile saklanan eski metinler de temizlenir. Satırlar silinmez, çünkü başkalarının yorumları, yanıtları ve repostları bu satırlara bağlıdır ve **başkalarının verisi silinmez**.
- **Beğeniler**: hesabın beğenileri silinir, gönderilerin `likeCount` değeri düşer; silinen görünür yorumlar `commentCount`'tan çıkar.
- **Raporlar**: hesabın yaptığı raporlar silinir; hesabın içeriği hakkındaki raporlarda yalnız içerik yazarı referansı anonimleşir.
- **Engellemeler** (`user_blocks`): hesabın taraf olduğu satırlar silinir. **Forumlar**: oluşturan referansı anonimleşir.
- **Outbox**: hesabın kimliğini taşıyan tüm olaylar (bekleyen, başarısız, teslim edilmiş) silinir.
- Başka servise çağrı yapılmaz; yalnız `community_db`. Günlüğe yalnız `accountId` yazılır.

#### Hata Durumları
- 400: UUID olmayan `accountId`
- 401: Token yok/bozuk/süresi dolmuş; yanlış `typ`, `aud`, `iss`, algoritma (HS256, `alg=none`) ya da bilinmeyen `kid`; `exp - iat > 60 sn`; `jti`/`iat` yok; kullanıcı token'ı
- 403: `scope` yok/farklı; `sub` ≠ `svc:auth-service`; `account_id` yok ya da yoldaki hesapla aynı değil; tanımsız `/internal/**` yol/yöntem
- 500: Beklenmeyen sunucu hatası (Auth görevi bekletir, yeniden dener)

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth Service `DELETE {COMMUNITY_SERVICE_URL}/internal/v1/accounts/{accountId}` ile doğrudan çağırır (Gateway'den geçmez). Adres Compose'taki `COMMUNITY_SERVICE_URL`; yeni ENV yoktur.

#### Test Durumu
UNIT + INTEGRATION: gerçek RS256 imzalı token'lar ve yerel JWKS sunucusuyla güvenlik zinciri testi (`InternalEndpointSecurityTest`, `JwtConfigTest`), denetleyici kuralları (`AccountErasureControllerTest`) ve gerçek PostgreSQL üzerinde veri etkisi (`AccountDataEraserIntegrationTest`: silme, başkalarının verisine dokunmama, sayaç düzeltmesi, idempotentlik). **Auth Service'in gerçek `ServiceTokenIssuer`'ıyla uyumluluk ve dev ortamında gerçek hesap silme (görevin `CONFIRMED` olması): NOT VERIFIED** (Auth sahibiyle birlikte denenecek).

## CONTENT

Sorumlu: Nisa · Gateway: `/api/v1/content/**` · Görevler: CONT-

_Kayıt yok._

## EVENT

Sorumlu: Nisa · Gateway: `/api/v1/event/**` · Görevler: EVT-

_Kayıt yok._

## HARDWARE

**Güncel Auth sözleşmesi — 2026-10-29:** Kullanıcı uçları gerçek Auth JWKS'i ile
RS256/kid/at+jwt/issuer/audience/ham exp–nbf–iat ve UUID sub doğrular; typed actor
yalnız doğrulanmış subject'ten oluşur. ADMIN katalog/kampanya yazma izni,
ADMIN veya MODERATOR kaynak yorum moderasyon izni üretir. Roller ownership'i aşmaz.
Yalnız health anonimdir. Cookie doğrudan Hardware'da kimlik oluşturmaz;
Gateway cookie → Bearer öncesinde CSRF + Origin uygular. Hardware CSRF bütünüyle
kapatılmadı; özel CSRF ignore matcher yoktur. Resource Server'ın standart açık Bearer
davranışı uygulanır. D4'te Bearer taşımayan CSRF reddi controller'a ulaşmadan 401 döner.
Silinen hesap fence'i etkinse kullanıcı ve cihaz işlemleri 401 ile reddedilir.
Kullanıcı token ömrü en çok 60 sn, tolerans 30 sn'dir; 15 dakikalık token artık reddedilir.
Cihaz kimliği ayrı `dev+jwt` doğrulayıcısıyla yalnız snapshot POST'unda kabul edilir;
aktif cihaz için Performance ve User/Social policy bağımlılıkları çözülmüş sayılmaz.
[Güncel kapsam](../docs-hardware/integration/auth/auth-integration-05.md),
[güncel test/kalite kanıtı](../docs-hardware/integration/auth/auth-integration-06.md).
Önceki paketli Auth/Gateway/Hardware izole HTTP kabulü 16 kontrol PASS: kayıt/giriş,
SMTP doğrulama, cookie/CSRF/Origin, owner/IDOR ve gerçek Auth worker → Hardware D4
204/CONFIRMED, tekrar ve eski JWT ile yeniden yazma reddi.
[Runtime kabulü](../docs-hardware/integration/auth/entegrasyon-v1/auth-integration-04.md).
Ortak Compose/TLS/tarayıcı ve bütün Saga NOT_VERIFIED. Repoda 11 silme ucu eksiktir;
yalnız Hardware'ı çalıştıran izole fixture 15 PENDING ACK bekler.
Önceki tarihlerdeki test sayıları ve DEFERRED_AUTH ifadeleri teslim tarihçesidir;
aşağıdaki Yetkilendirme alanları bu güncel kullanıcı JWT kuralıyla birlikte geçerlidir.


Sorumlu: Alper · Gateway: `/api/v1/hardware/**` · Görevler: HW-

LTZ-ALP-HW-01: dokuz protected iş endpoint'i. Tüm POST/PUT gövdelerinde **16 KiB**
sınırı vardır (Content-Length olmasa da uygulanır); aşılırsa 413 `REQUEST_TOO_LARGE`
ProblemDetail döner. 2026-10-21 son pre-push: 58 PASS, JaCoCo %96,69.
Son kod Sonar analizi: A/A/A, Quality Gate PASS (bütün backend scope; overall %84,3).
Teslim kanıtı: [LTZ-ALP-HW-01](../docs-hardware/reports/LTZ-ALP-HW-01-delivery.md).
2026-10-21 tesliminde gerçek Auth/JWT/E2E DEFERRED_AUTH idi; güncel kullanıcı JWT
uygulaması ve kalan canlı E2E sınırı yukarıdaki 2026-10-24 kaydındadır.

LTZ-ALP-HW-02: 15 yeni katalog/kampanya endpoint'i, ürün listesinde geriye uyumlu
HW-11 filtreleri ve profil seçiminde ACTIVE katalog kontrolü. Son kod pre-push
134 PASS / %98,02; Sonar A/A/A + Quality Gate PASS, overall %89,2 / duplication %0.
Yeni kod coverage %92,3, issue 0. [HW-02 teslimi](../docs-hardware/reports/LTZ-ALP-HW-02-delivery.md).
2026-10-21'de ertelenen Auth rol/claim eşlemesi artık ADMIN → hardware:catalog:write
olarak uygulanır; güncel ortak JWT/RBAC sözleşmesi geçerlidir.

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

**Güncel geliştirme şeması (2026-10-21):** Kullanıcı onaylı temizlikle eski V1–V10
zinciri, boş DB için 7 ilişkili CREATE migration'ına dönüştürüldü. Bağlantı alanları
güncel dosyalara işaret eder; aşağıdaki eski tarihli test/sürüm sayıları teslim
tarihçesidir. Endpoint, DTO ve erişim sözleşmesi değişmedi.
[Güncel SQL envanteri](../docs-hardware/progress/hardware-migration-cleanup.md),
[yeniden doğrulama](../docs-hardware/testing/hardware-migration-cleanup-quality.md).

### HARDWARE — HW-01 Profil oluşturma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/profiles

#### Yetkilendirme
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner. Yalnız güvenilir `AuthenticatedActor` üzerinden owner alınır; body/header kimliği
kabul edilmez.
CSRF etkin; açık Bearer ve Gateway cookie/CSRF/Origin protokolü uygulanır. Yeni public API yok.

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
İş akışında başka servis çağrısı yok; Auth JWKS ortak doğrulama katmanında tüketilir.
Canlı Auth/Gateway E2E NOT_VERIFIED.
Flyway: V1__create_hardware_profiles_and_privacy.sql.

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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner; HW-01 ile aynı onaylı güvenlik sınırı/CSRF/JWT doğrulaması.
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
Flyway: V2__create_hardware_catalog.sql + V3__create_hardware_profile_components.sql; aktif katalog seçimi
V2 status sınırına uyar. Tam katalog yönetimi/arama HW-10/HW-11 kayıtlarındadır.

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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner; HW-01 ile aynı CSRF/JWT doğrulaması sınırı. Başka owner kaynağı 404 ile gizlenir.

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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
Frontend: Gateway tam path. Diğer mikroservisler: yok. Mevcut V1–V3; yeni migration yok.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
Frontend: Gateway tam path. Diğer mikroservisler: yok. Mevcut V1–V3; yeni migration yok.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner; yalnız doğrulanmış aktörün profilleri. HW-01 güvenlik sınırı/JWT doğrulaması.

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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner; HW-01 güvenlik sınırı/JWT doğrulaması. Diğer kullanıcıların profilleri 404.

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
Frontend: Gateway tam path. Diğer mikroservisler: yok. V1–V3; yeni migration yok.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner; HW-01 güvenlik sınırı/JWT doğrulaması. Kullanıcı kimliği parametre alınmaz.

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
ve REPEATABLE_READ snapshot kullanır; V1–V3, yeni migration yok.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner; CSRF ve HW-01 güvenlik sınırı/JWT doğrulaması. Admin/header bypass yoktur.

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
entegrasyonu bu ana taskta uygulanmaz. V1–V3; veri silen migration eklenmedi.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Authenticated — doğrulanmış typed AuthenticatedActor.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Authenticated — doğrulanmış typed AuthenticatedActor.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — doğrulanmış typed actor + hardware:catalog:write uygulama izni; normal kullanıcı yazamaz. CSRF zorunlu; gerekçeli audit aynı transaction'da.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Authenticated — doğrulanmış typed AuthenticatedActor.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Authenticated — doğrulanmış typed AuthenticatedActor.

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
Flyway V2__create_hardware_catalog.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — typed actor + hardware:catalog:write uygulama izni; CSRF ve gerekçeli atomik audit zorunlu.
 Public uç yok.

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
Flyway V4__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — typed actor + hardware:catalog:write uygulama izni; CSRF ve gerekçeli atomik audit zorunlu.
 Public uç yok.

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
Flyway V4__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Admin — typed actor + hardware:catalog:write uygulama izni; CSRF ve gerekçeli atomik audit zorunlu.
 Public uç yok.

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
Flyway V4__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Authenticated — doğrulanmış typed actor.
 Public uç yok.

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
Flyway V4__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Authenticated — doğrulanmış typed actor.
 Public uç yok.

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
Flyway V4__create_hardware_campaigns.sql; hardware_products gerçek FK, para numeric(12,2),
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Typed actor ve owner-only; katalog yazma izni başka profil için erişim sağlamaz.

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
V1__create_hardware_profiles_and_privacy.sql; User/Social global karar sözleşmesi
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner-only typed actor; CSRF zorunlu. JWT doğrulaması zorunlu.

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
HardwarePrivacyService; V1 kural tabloları profil silindiğinde CASCADE temizlenir.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
V1; HardwareVisibilityPolicy portu. User/Social gerçek adapter ve E2E
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Typed actor; iki profil de aynı aktöre ait olmalı. PUBLIC paylaşım veya katalog
yazma izni başka owner'ın profilini karşılaştırma hakkı vermez. CSRF zorunlu;
JWT doğrulaması zorunlu. Read-only REPEATABLE_READ transaction.

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
HardwareComparisonService; mevcut V1–V3 teknik verileri, yeni migration yok.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Typed actor, iki profil ve iki bileşen owner-only; her componentId belirtilen
profileId'ye ait olmalı. CSRF, no-store ve JWT doğrulaması korunur.

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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Typed actor; katalog yazma izni gerekmez. İki model ACTIVE ve aynı kategoride
olmalı. Kullanıcı profili paylaşım yolu değildir. CSRF, JWT doğrulaması korunur.

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
HardwareComparisonService; V2 genel katalog. Dış sağlayıcı/servis çağrısı yok.

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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Kullanıcı adına doğrulanmış typed actor, owner-only. Kullanıcı bağlamı olmayan yeni
servis-token erişimi açılmaz. userId/rol/servis kimliği body/header'dan alınmaz.
Her profil aynı aktöre ait olmalıdır; bir tane yanlış/olmayan kaynak bütün batch'i
404 yapar. Paylaşılan PUBLIC profil de başka owner ise reddedilir. CSRF korunur;

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
HardwareBatchService + HardwareBatchQuery; mevcut V1–V3 component indeksi.
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
Kullanıcı JWT entegrasyonu uygulanmıştır; User/Social UGC kararı DEFERRED_CONTRACT / NOT_VERIFIED,
üretim adapter'ı deny. İzin veren policy yalnız testlerde; yeni public/anonim uç yok.

### HARDWARE — HW-12 Yorum oluşturma

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend

#### Endpoint
POST /api/v1/hardware/catalog/products/{productId}/reviews

#### Yetkilendirme
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
HardwareReviewService; V5__create_hardware_reviews.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
HardwareReviewService; V5__create_hardware_reviews.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
HardwareReviewService; V5__create_hardware_reviews.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
HardwareReviewService; V5__create_hardware_reviews.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
HardwareReviewService; V5__create_hardware_reviews.sql.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
HardwareReviewService; V5__create_hardware_reviews.sql.
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
V6 rapor/outbox şemasını, V5 review audit'in nihai action CHECK'ini oluşturur.
Yerel ContentReported/HardwareReviewModerated v1 zarfı actual consumer kontratı değildir.
Producer/UUID/version/UTC metadata ve gerekli kimlik/enum payload'ı; 4 KiB/event,
PENDING+FAILED toplamı en fazla 10000. Transaction advisory kilidi kapasite yarışını
korur. Relay, broker ve uydurma servis URL/token modeli yok; olaylar PENDING kalır.
Admin/Notification/User-Social gerçek sözleşme ve E2E DEFERRED_CONTRACT / NOT_VERIFIED,
açık kullanıcı onaylı. Kullanıcı Auth JWT doğrulaması uygulanır; gerçek dış policy
ve teslim sözleşmesi olmayan akışlar production fail-closed kalır.

### HARDWARE — HW-13 Yorumu raporlama

Durum: IMPLEMENTED — yerel kaynak sözleşmesi; gerçek entegrasyon DEFERRED_CONTRACT
Sorumlu: Alper
Tüketici servisler: Frontend, Admin (gerçek consumer NOT_VERIFIED)

#### Endpoint
POST /api/v1/hardware/reviews/{reviewId}/reports

#### Yetkilendirme
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
V6__create_hardware_review_reports_and_outbox.sql. Gerçek servis/DB çağrısı yok;
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
V6__create_hardware_review_reports_and_outbox.sql. Gerçek servis/DB çağrısı yok;
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Typed actor + hardware:reviews:moderate uygulama izni hem HTTP hem service sınırında zorunlu. Katalog izni/ROLE_ADMIN/X-Role tek başına kabul edilmez.

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
V6__create_hardware_review_reports_and_outbox.sql. Gerçek servis/DB çağrısı yok;
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
V6__create_hardware_review_reports_and_outbox.sql. Gerçek servis/DB çağrısı yok;
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
Owner typed actor + CSRF; processingConsent=true bu tek isteğin açık onayıdır.
Sürekli sync izni vermez. Donanım/consent/source/profileVersion değiştirmez;
yalnız kısa ömürlü rate bucket'ları kaydedilir.  Bu uç cihaz doğrulamaz.

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
Yeni migration yok; mevcut V5 rate bucket altyapısı. Başka servise/DB'ye çağrı yok.
HW-07 kalıcı izin ve ayrı doğrulanmış cihaz apply sözleşmesi ayrıca uygulanacak.

#### Test Durumu
UNIT + INTEGRATION — HardwareScanControllerTest (28) ve HardwareScanTest (2);
2026-10-21 20:21:49 +03 verify exit 0: 269 PASS, failure/error/skipped 0;
JaCoCo 1243/1261=%98,57. Gerçek PostgreSQL 17/Flyway V1–V9/JPA/MockMvc.
Gerçek Auth/desktop E2E NOT_VERIFIED. Son kalite sonucu HW-05 kalite kaydında tutulur.

### HARDWARE — HW-07 Ortak sync güvenlik ve tekrar gönderim sözleşmesi

Kullanıcı JWT doğrulaması uygulanır. Auth cihaz token'ı için ayrı RS256/JWKS/kid,
tam typ=dev+jwt, issuer, aud içinde hardware-service, UUID sub=deviceId ve account_id,
scope içinde tam hardware:sync:write, dolu jti ve ham exp/nbf/iat kontrolü uygulanır.
Ömür en çok 60 sn, tolerans 30 sn'dir. Principal kullanıcıya veya yöneticiye dönüşmez.
PERF cihaz sahipliği/iptal sözleşmesi ve masaüstü başarı E2E'si kullanıcı kararıyla
DEFERRED_CONTRACT / NOT_VERIFIED kalır. Gerçek dış URL/JSON veya credential uydurulmaz.
Production aktif cihaz policy'si deny eder; grant ve geçerli cihazla apply 404 alır.
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
V7__create_hardware_desktop_sync.sql. Başka servis/DB çağrısı yok; gerçek Auth/PERF
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
V7__create_hardware_desktop_sync.sql. Başka servis/DB çağrısı yok; gerçek Auth/PERF
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
Güncel kimlik: Auth RS256 at+jwt → doğrulanmış typed actor; üstteki ortak JWT/RBAC kuralı geçerlidir.
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
V7__create_hardware_desktop_sync.sql. Başka servis/DB çağrısı yok; gerçek Auth/PERF
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
Auth RS256/JWKS doğrulanmış dev+jwt → AuthenticatedHardwareDevice(account_id, sub),
hardware:sync:write, aktif cihaz sahipliği policy'si ve aynı owner/device/generation için
GRANTED yerel izin. Kullanıcı/ADMIN token'ı 403; geçersiz cihaz veya svc+jwt 401 alır.
Cihaz token'ı kullanıcı ve D4 uçlarında kabul edilmez. CSRF korunur; üretimde Performance
policy'si deny eder. Geçerli cihaz kimliği tek başına apply yetkisi sağlamaz.

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
V7__create_hardware_desktop_sync.sql. Başka servis/DB çağrısı yok; gerçek Auth/PERF
cihaz/desktop bağlantısı NOT_VERIFIED. Yerel sözleşme gerçek cihaz kanıtı değildir.

#### Test Durumu
UNIT + INTEGRATION — 2026-10-29 Hardware Maven verify: 405 PASS, hata/atlama 0,
JaCoCo %98,90. HardwareDeviceJwtValidatorTest ve gerçek Auth üreticili
HardwareAuthIntegrationTest cihaz türü/claim/scope/zaman/hesap fence ve policy sınırını doğrular.
E2E — gerçek Auth/Gateway/Hardware izole HTTP kabulü 20/20 PASS; cihaz kimliğinin
policy'ye ulaşması ve Performance yokken 404 ile reddi dahil. Gerçek consent→apply
başarı E2E'si DEFERRED_CONTRACT / NOT_VERIFIED.
[Güncel kanıt](../docs-hardware/integration/auth/auth-integration-06.md).

### HARDWARE — HW-AUTH D4 yerel hesap temizliği

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: auth-service

#### Endpoint
DELETE /internal/v1/accounts/{accountId} — INTERNAL ONLY, Gateway route'u yoktur.

#### Yetkilendirme
RS256, Auth JWKS, kid, typ=svc+jwt; iss=JWT_ISSUER, tek aud=hardware-service;
ham iat/nbf/exp, exp−iat≤60 saniye, 30 saniye tolerans ve jti zorunlu.
SCOPE_account:erase; tam sub=svc:auth-service; account_id string'i path UUID'ye eşit.
Kullanıcı token'ı iç uçta ve servis token'ı iş uçlarında reddedilir.
Cookie kabul edilmez. Özel CSRF istisnası tanımlanmaz; standart Resource Server
Bearer davranışı geçerlidir. Bearer taşımayan D4 CSRF reddi controller'a geçmeden 401 döner.

#### Path/Query Parameters
accountId zorunlu UUID. Query parametresi yoktur.

#### Request Body
Yok.

#### Response
204, gövdesiz, Cache-Control: private, no-store. Yalnız yerel transaction commit'i
tamamlanınca döner; hesap yoksa veya tekrar çağrıda da 204. İkinci bir Saga kurulmaz.
Kendi profilleri ve alt kayıtları; yorum/rapor/ilgili audit/outbox ve kişisel rate bucket'ları
silinir. Başka kullanıcıların ilgisiz kayıtları ve global katalog korunur.
Kerem'le onaylı istisna: SHA-256 hesap özeti + expiry fence'i 17 dakika etkin tutulur;
dakikalık purge ile kaldırılır. Bu pseudonymous metadata anonim diye gösterilmez.

#### Hata Durumları
400 bozuk UUID; 401 eksik/geçersiz kullanıcı veya servis JWT'si; 403 yanlış scope,
sub veya account_id binding. Yanlış method/başka internal path 403.
5xx transaction/DB/timeout hatası; temizlik ve fence rollback olur, 204 verilmez.
202/3xx dönülmez. Auth ileri yönlü retry yapar; başka servis çağrısı yapılmaz.
İstisna: `503 ACCOUNT_ERASURE_PENDING` / Retry-After: 60, sınırlı paketin başarıyla
commit olduğunu fakat hesabın henüz bitmediğini belirtir. Bu durumda ilerleme ve
fence korunur; sonraki çağrı kaldığı yerden temizler. Her çağrı en fazla 20 profil,
20 yorum ve ilgili audit/outbox/rapor türü başına 1000 satır işler. Yalnız son paket 204'tür.

#### Bağlantı
HardwareAccountErasureController → HardwareAccountErasureService → HardwareAccountErasure portu
→ HardwareAccountErasureRepository; V8__add_hardware_account_erasure.sql.
Auth mevcut AccountDeletionCoordinator → direct service RestClient çağrısı.
[Mutabakat](../docs-hardware/integration/auth/entegrasyon-v1/auth-integration-02.md).

#### Test Durumu
UNIT + INTEGRATION — HardwareAuthIntegrationTest: gerçek Auth ServiceTokenIssuer/JWKS,
ortak D4 17 senaryo, tüm tablolar, başka hesabı koruma, tekrar, rollback ve race/fence.
İzole PostgreSQL; canlı DEV Saga ve diğer 15 katılımcı E2E NOT_VERIFIED.
[Gerçek uygulama HTTP kabulü](../docs-hardware/integration/auth/entegrasyon-v1/auth-integration-04.md):
gerçek Auth worker → Hardware 204, Auth hardware görevi CONFIRMED; tekrar 204,
başka accountId 403 ve eski kullanıcı JWT'siyle yeniden yazma 401. İzole fixture'da
16 kontrol PASS; diğer 15 görev sahte ACK verilmeden PENDING kalır.
[Nihai kalite kanıtı](../docs-hardware/integration/auth/entegrasyon-v1/auth-integration-quality.md).

## RECOMMENDATION

Sorumlu: Alper · Gateway: `/api/v1/recommendation/**` · Görevler: REC-

_Kayıt yok._

## PERFORMANCE

Sorumlu: Alper · Gateway: `/api/v1/performance/**` · Görevler: PERF-, PWEB-

> **Faz 1 (LTZ-ALP-PERF-01) notu.** Cihaz kimliği, eşleştirme, cihaz token'ı ve iptal **Auth Service'tedir** (bkz. AUTH — Masaüstü cihaz kimliği). Performance yalnız cihazın kendi referansını (`device_links`), izni, senkronizasyon kaydını ve silmeyi yönetir.
> Cihaz token'ıyla senkronizasyon (`POST /sync/batches`) Auth'un `aud=performance-service` / `scope=performance:sync:write` üretmesine bağlıdır (talep: [auth-requests-01.md](../docs-performance/integration/auth-service-talepleri/auth-requests-01.md) REQ-01-A) ve **gerçek Auth cihaz token'ıyla uçtan uca doğrulanmadı**. Aşağıdaki `Test Durumu` alanları bunu açıkça belirtir.
> Tüm başarılı yanıtlar `Cache-Control: private, no-store` taşır. Tüm hata gövdeleri `ProblemDetail` + `code` alanıdır. Ortak hata kodları: `AUTHENTICATION_REQUIRED` (401), `ACCESS_DENIED` (403), `INVALID_REQUEST` (400/422), `RATE_LIMITED` (429), `REQUEST_TOO_LARGE` (413), `AUTH_UNAVAILABLE` (503 + `Retry-After: 30`), `PERFORMANCE_NOT_FOUND` (404), `INTERNAL_ERROR` (500).

### PERFORMANCE — Senkronizasyon izni (oku)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/sync-consent

#### Yetkilendirme
Owner (kullanıcı token'ı `at+jwt`; yalnız kendi izni)

#### Response — 200
```json
{
  "granted": false,
  "grantedAt": null,
  "revokedAt": null
}
```
Kayıt yoksa izin verilmemiş sayılır (`granted=false`).

#### Hata Durumları
- 401: Bearer yok/geçersiz; cihaz veya servis token'ı kullanıcı ucunda geçmez
- 403: Kullanıcı hesabı pasif/silinmeye alınmış
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sync-consent

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION (PostgreSQL Testcontainers + gerçek filtre zinciri; Auth kullanıcı token'ı gerçek Auth kaynak kodundan üretilen JWKS ile imzalı)

### PERFORMANCE — Senkronizasyon izni ver

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
PUT /api/v1/performance/sync-consent

#### Yetkilendirme
Owner

#### Response — 200
```json
{
  "granted": true,
  "grantedAt": "2026-01-01T10:00:00Z",
  "revokedAt": null
}
```
İdempotenttir; tekrar çağrı `grantedAt` değerini değiştirmez.

#### Hata Durumları
- 401: Bearer yok/geçersiz
- 403: Hesap pasif / silinmeye alınmış (`ACCESS_DENIED`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sync-consent

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Senkronizasyon iznini geri al

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
DELETE /api/v1/performance/sync-consent

#### Yetkilendirme
Owner

#### Response — 200
```json
{
  "granted": false,
  "grantedAt": "2026-01-01T10:00:00Z",
  "revokedAt": "2026-01-02T10:00:00Z"
}
```
İdempotenttir. İzin geri alınınca sonraki senkronizasyonlar `403 SYNC_CONSENT_REQUIRED` ile reddedilir.

#### Hata Durumları
- 401: Bearer yok/geçersiz
- 403: Hesap pasif / silinmeye alınmış
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sync-consent

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Bağlı cihazları listele

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/devices

#### Yetkilendirme
Owner (yalnız çağıranın kendi cihaz referansları)

#### Response — 200
```json
[
  {
    "id": "UUID — Performance bağlantı kimliği (yol parametresi olarak kullanılır)",
    "deviceId": "UUID — Auth Service cihaz kimliği",
    "label": "Oyun PC",
    "status": "ACTIVE | DISABLED",
    "linkedAt": "2026-01-01T10:00:00Z",
    "lastSyncAt": null,
    "disabledAt": null
  }
]
```

#### Hata Durumları
- 401: Bearer yok/geçersiz
- 403: Hesap pasif / silinmeye alınmış
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/devices

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Cihaz bağla

Durum: IN PROGRESS
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
POST /api/v1/performance/devices

#### Yetkilendirme
Owner. Cihaz sahipliği ve etkinliği, çağıranın Bearer token'ı **Auth Service'e iletilerek** `GET /api/v1/auth/devices` yanıtından doğrulanır (Auth DB'sine erişilmez). Auth yanıt vermezse fail-closed: `503 AUTH_UNAVAILABLE`.

#### Request Body
```json
{
  "deviceId": "UUID — Auth'ta oluşturulmuş, kullanıcının kendi cihazı"
}
```

#### Response — 200
```json
{
  "id": "UUID",
  "deviceId": "UUID",
  "label": "Auth'taki cihaz etiketi (temizlenmiş, en çok 64 karakter)",
  "status": "ACTIVE",
  "linkedAt": "2026-01-01T10:00:00Z",
  "lastSyncAt": null,
  "disabledAt": null
}
```
İdempotenttir: aynı cihaz tekrar bağlanırsa mevcut kayıt döner (devre dışıysa yeniden etkinleşir).

#### Hata Durumları
- 400/422: Geçersiz `deviceId` (`INVALID_REQUEST`)
- 401: Bearer yok/geçersiz
- 403: Cihaz çağıranın Auth listesinde yok / iptal edilmiş (`DEVICE_NOT_LINKED`) ya da hesap pasif
- 409: Hesap başına 5 bağlantı sınırı (`DEVICE_LINK_LIMIT_REACHED`); cihaz başka hesapta bağlı (`DEVICE_LINK_CONFLICT`)
- 429: Hız sınırı
- 503: Auth Service erişilemez/bozuk yanıt (`AUTH_UNAVAILABLE`, `Retry-After: 30`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/devices

Diğer mikroservisler:
Auth Service'e RestClient: `GET /api/v1/auth/devices` (çağıranın Bearer'ı iletilir)

#### Test Durumu
INTEGRATION — Auth cihaz listesi **yerel Auth sapmasıyla** (belgelenmiş yanıt şekli) test edildi; gerçek Auth ortamıyla uçtan uca doğrulanmadı (durum IN PROGRESS).

### PERFORMANCE — Cihaz bağlantısını kaldır

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
DELETE /api/v1/performance/devices/{linkId}

#### Yetkilendirme
Owner (başkasının bağlantısı için 404 — IDOR bilgi sızdırmaz)

#### Path Parameters
- linkId: UUID, zorunlu — `GET /devices` yanıtındaki `id`

#### Response — 200
```json
{
  "id": "UUID",
  "deviceId": "UUID",
  "label": "Oyun PC",
  "status": "DISABLED",
  "linkedAt": "2026-01-01T10:00:00Z",
  "lastSyncAt": null,
  "disabledAt": "2026-01-02T10:00:00Z"
}
```
Kayıt silinmez, `DISABLED` yapılır; idempotenttir. Devre dışı cihaz yeni senkronizasyon yapamaz. Cihaz kimliğinin Auth'ta iptali ayrıca `DELETE /api/v1/auth/devices/{deviceId}` ile yapılır (Performance iptal etmez).

#### Hata Durumları
- 400: Geçersiz `linkId`
- 401: Bearer yok/geçersiz
- 404: Bağlantı yok veya çağıranın değil (`PERFORMANCE_NOT_FOUND`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/devices/{linkId}

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Ölçüm senkronizasyonu kabul (zarf v1 + örnekler v2, PERF-05..12)

Durum: IN PROGRESS
Sorumlu: Alper
Tüketici servisler: Masaüstü istemcisi (planlı)

#### Endpoint
POST /api/v1/performance/sync/batches

#### Yetkilendirme
Cihaz token'ı (`Authorization: Bearer <dev+jwt>`): `typ=dev+jwt`, `iss=ltz-auth`, **tek** `aud=performance-service`, `scope=performance:sync:write`, `sub`=cihaz UUID, `account_id`, `jti`, ömür ≤ 60 sn. Kullanıcı ve servis token'ı bu uçta geçmez; Hardware için üretilmiş cihaz token'ı (`aud=hardware-service`) **reddedilir**.
Ayrıca: hesapta açık senkronizasyon izni + bu cihaz için aktif `device_links` kaydı gerekir. v2'de `sessionId` çağıran hesabın oturumu olmalıdır (başkasının veya olmayan oturum aynı 404).
> **BLOCKED:** Auth bugün yalnız `aud=hardware-service` cihaz token'ı üretir. Gerçek akış REQ-01-A (Auth değişikliği) sonrası çalışır.

#### Header Parameters
- Idempotency-Key: UUID, zorunlu

#### Request Body — v1 (Faz 1, geriye uyumlu; yalnız zarf)
```json
{
  "schemaVersion": 1,
  "clientSentAt": "2026-01-01T10:00:00Z",
  "sampleCount": 0
}
```
`sampleCount` 0..500 zorunlu; `sessionId` ve `samples` v1'de gönderilemez (400).

#### Request Body — v2 (Faz 3, örnek içeren)
```json
{
  "schemaVersion": 2,
  "clientSentAt": "2026-01-01T10:00:00Z",
  "sessionId": "UUID",
  "samples": [
    { "capturedAt": "2026-01-01T10:00:00.250Z", "metric": "FPS", "value": 143.9 },
    { "capturedAt": "2026-01-01T10:00:00.250Z", "metric": "CPU_TEMP_C", "value": 61.5 }
  ]
}
```
- `sessionId` ve `samples` zorunludur (`samples` boş olabilir, en çok **500**); v2'de `sampleCount` gönderilemez (400), sayı `samples` uzunluğundan türetilir.
- `metric` yalnız **kanonik büyük harfli** ad (büyük/küçük harf dönüşümü yoktur). Birimi istemci seçmez; her metriğin tek birimi vardır.
- `value` yalnız JSON **sayısı**dır; `"60"`, `true`, `null`, dizi ve nesne 400 ile reddedilir. Ölçülemeyen değer için örnek gönderilmez; sunucu hiçbir zaman eksik değeri 0 ile doldurmaz.
- `capturedAt` milisaniyeye kesilir. Pencere: `oturum başlangıcı − 5 dk` .. `min(şimdi + 5 dk, oturum bitişi + 5 dk)`. Sırasız gönderim kabul edilir.
- Kaynak her zaman `DEVICE`'tır (sunucu atar). `clientSentAt` şu andan −24 sa .. +5 dk aralığında olmalıdır.
- Gövde üst sınırı bu uçta **64 KiB** (diğer uçlarda 16 KiB); aşılırsa 413.

| Metrik | Birim | Geçerli aralık |
|---|---|---|
| `CPU_TEMP_C`, `GPU_TEMP_C` | °C | 0 < v ≤ 150 |
| `CPU_USAGE_PERCENT`, `GPU_USAGE_PERCENT` | % | 0 ≤ v ≤ 100 |
| `RAM_USED_MB` | MB | 0 ≤ v ≤ 1.048.576 |
| `VRAM_USED_MB` | MB | 0 ≤ v ≤ 262.144 |
| `DISK_READ_MBPS`, `DISK_WRITE_MBPS` | MB/s | 0 ≤ v ≤ 100.000 |
| `FAN_RPM` | RPM | 0 ≤ v ≤ 30.000 |
| `FPS` | FPS | 0 < v ≤ 1000 |

> Aralıklar **geçici varsayımdır**: gerçek masaüstü sensör sözleşmesi henüz yoktur (bkz. docs-performance/reports/issues.md). Sıcaklık ve FPS için 0 "ölçülememiş" demektir ve reddedilir.

#### Response — 200
```json
{
  "batchId": "UUID",
  "sampleCount": 2,
  "storedSamples": 2,
  "duplicateSamples": 0,
  "receivedAt": "2026-01-01T10:00:01Z",
  "replayed": false
}
```
- v1'de `storedSamples` ve `duplicateSamples` 0'dır (zarf içeriği saklanmaz).
- Aynı oturumda aynı `(metric, capturedAt)` noktası zaten varsa **ilk yazılan korunur**, yeni gelen yazılmaz ve `duplicateSamples`'a sayılır (farklı anahtarla yeniden gönderim çoğaltma yaratmaz).
- Aynı `Idempotency-Key` + aynı içerik tekrarında aynı `batchId` ve ilk sonuçla `replayed=true` döner (yeni kayıt oluşmaz; oturum sonradan silinmiş olsa da ilk sonuç döner).
- Gönderim **hep-ya-hiç**tir: herhangi bir örnek geçersizse hiçbir örnek yazılmaz ve makbuz oluşmaz (aynı anahtar düzeltilmiş içerikle yeniden kullanılabilir).

#### Hata Durumları
- 400: Geçersiz/eksik Idempotency-Key, geçersiz gövde, bilinmeyen alan, şema–alan uyumsuzluğu (v1'de örnek/`sessionId`, v2'de `sampleCount`/eksik `sessionId`/`samples`), 500'den fazla örnek, sayı olmayan `value` (`INVALID_REQUEST`)
- 401: Token yok/geçersiz/yanlış `aud`/`scope`/`typ`/ömür
- 403: Açık izin yok (`SYNC_CONSENT_REQUIRED`); cihaz bağlı değil/devre dışı (`DEVICE_NOT_LINKED`); hesap pasif
- 404: Oturum yok veya başkasına ait (`PERFORMANCE_NOT_FOUND`)
- 409: Aynı anahtar farklı içerikle (`IDEMPOTENCY_KEY_REUSED`); cihaz başına 1500 canlı makbuz sınırı (`SYNC_BATCH_LIMIT_REACHED`); oturum ACTIVE (24 saatten eski değil) veya en çok 10 dk önce COMPLETED değil (`SESSION_NOT_ACCEPTING_SAMPLES`); oturum başına 100.000 örnek (`SESSION_SAMPLE_LIMIT_REACHED`); hesap başına 500.000 örnek (`USER_SAMPLE_QUOTA_REACHED`)
- 413: Gövde çok büyük (`REQUEST_TOO_LARGE`)
- 422 (`sampleIndex` ihlal eden örneğin 0 tabanlı sırasıdır, değer yansıtılmaz): `SYNC_CLIENT_TIME_INVALID`; `SAMPLE_METRIC_UNSUPPORTED`; `SAMPLE_VALUE_INVALID` (aralık dışı/NaN/sonsuz); `SAMPLE_TIME_INVALID` (pencere dışı); `SAMPLE_DUPLICATE_IN_BATCH` (aynı gönderimde aynı metrik+an)
- 429: Hız sınırı
- 503: JWKS/Auth erişilemez (fail-closed)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Masaüstü istemcisi API Gateway üzerinden /api/v1/performance/sync/batches. Auth JWKS ile imza doğrulanır. Okuma/istatistik uçları Faz 4'tedir.

#### Test Durumu
INTEGRATION — gerçek filtre zinciri + PostgreSQL; cihaz token'ı **yerel imzalı** sözleşme token'ıdır, gerçek Auth cihaz token'ıyla E2E değildir (BLOCKED, REQ-01-A). Gerçek masaüstü istemcisiyle E2E yapılmadı (istemci sözleşmesi yok).

### PERFORMANCE — Hesap verisi silme (Auth D4)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Auth Service

#### Endpoint
DELETE /internal/v1/accounts/{accountId}

#### Yetkilendirme
Internal — `svc+jwt`, **tek** `aud=performance-service`, `sub=svc:auth-service`, `scope=account:erase`, `account_id` = path, `jti`, ≤ 60 sn. Gateway'den açılmaz.

#### Path Parameters
- accountId: UUID, zorunlu

#### Response — 204
Gövde yok. İdempotenttir. Hesap için silme çiti (fence) kurulur; silme sonrası yazma engellenir.

#### Hata Durumları
- 401: Token yok/geçersiz
- 403: Yanlış scope/sub/`account_id` bağlaması
- 503: Temizlik partisi tamamlanmadı (`ACCOUNT_ERASURE_PENDING`, `Retry-After: 60`) — Auth yeniden dener
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Auth Service silme işçisi (`ltz.auth.deletion.targets` → `performance`) doğrudan çağırır.

#### Test Durumu
INTEGRATION — uç ve token bağlaması test edildi; ortak DEV ortamında Auth silme işçisiyle **doğrulanmadı** (NOT VERIFIED).

### PERFORMANCE — Aktif sahipli cihaz doğrulama (Hardware)

Durum: IN PROGRESS
Sorumlu: Alper
Tüketici servisler: Hardware Service (istemci henüz yok — öneri)

#### Endpoint
GET /internal/v1/accounts/{accountId}/devices/{deviceId}

#### Yetkilendirme
Internal — `svc+jwt`, `aud=performance-service`, `sub=svc:hardware-service`, `scope=devices:verify`, `account_id` = path. Gateway'den açılmaz.
> **BLOCKED (REQ-01-C):** Auth servis token üreticisi `sub` değerini her zaman `svc:auth-service` yapar ve `devices:verify` çifti yoktur; bu nedenle gerçek Hardware token'ı alınamaz.

#### Path Parameters
- accountId: UUID, zorunlu
- deviceId: UUID, zorunlu — Auth cihaz kimliği

#### Response — 204
Gövde yok: cihaz hesaba ait ve Performance'ta aktif.

#### Hata Durumları
- 401: Token yok/geçersiz
- 403: Yanlış `sub`/scope/`account_id` bağlaması
- 404: Cihaz yok, başka hesaba ait veya devre dışı (bilgi sızdırmaz)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Hardware `HardwareSyncDevicePolicy` için RestClient istemcisi **öneridir**; Hardware kodu değiştirilmemiştir (Hardware sahibine ayrıca bildirilmelidir).

#### Test Durumu
INTEGRATION — yalnız yerel imzalı `sub=svc:hardware-service` token'ıyla; gerçek Auth servis token'ı bu uçta 403 alır (BLOCKED).

> **Faz 2 (LTZ-ALP-PERF-02) notu — manuel oturum ve rapor (PERF-03/04/13/20).** Aşağıdaki uçlar yalnız **kullanıcı token'ıyla** (`at+jwt`, `aud=ltz-api`) çalışır; cihaz ve servis token'ı geçmez (401). Her uçta sahiplik sorgusu `user_id = token.sub` ile yapılır; başkasının veya olmayan kaynak **aynı** `404 PERFORMANCE_NOT_FOUND` döner (IDOR bilgi sızdırmaz). Zaman her zaman sunucu saatindendir; istemci `startedAt`/`endedAt`/`status`/`userId` gönderirse 400 (bilinmeyen alan). Bu fazda oturum yalnız `MANUAL`'dır; cihaz kaynaklı (`DEVICE`) oturum yoktur.
> Oyun ve donanım profili doğrulaması **servis sözleşmeleriyle** yapılır: Game `POST /api/v1/game/games/batch` (GAME-15) ve Hardware `POST /api/v1/hardware/profiles/batch` (HW-17, çağıranın kendi Bearer'ı iletilir). Bu çağrılar **gerçek Game/Hardware ile uçtan uca doğrulanmadı**; testler yerel sapma (mock) kullanır (`DEFERRED_CONTRACT` / `NOT_VERIFIED`). Bağımlı servis yanıt vermezse veya sözleşme dışı gövde dönerse kayıt **açılmaz**; `503` + `Retry-After: 30` (kapalı başarısızlık).
> Ek hata kodları: `ACTIVE_SESSION_EXISTS`, `SESSION_LIMIT_REACHED`, `SESSION_ALREADY_ENDED`, `SESSION_NOT_COMPLETED` (409); `GAME_NOT_FOUND`, `HARDWARE_PROFILE_NOT_FOUND`, `DEVICE_LINK_NOT_FOUND`, `REPORT_EMPTY`, `REPORT_INCONSISTENT` (422); `GAME_SERVICE_UNAVAILABLE`, `HARDWARE_SERVICE_UNAVAILABLE` (503). Yazma uçları hesap başına 60/dk (`session-user`) ve ortak adres kovası 120/dk ile sınırlıdır (429, `Retry-After: 60`).

### PERFORMANCE — Oturum başlat (PERF-03)

Durum: IN PROGRESS
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
POST /api/v1/performance/sessions

#### Yetkilendirme
Owner (kullanıcı token'ı). Kullanıcı başına aynı anda en çok bir `ACTIVE` oturum; hesap başına en çok 1000 saklanan oturum.

#### Request Body
```json
{
  "gameId": "UUID",
  "hardwareProfileId": "UUID (opsiyonel)",
  "deviceLinkId": "UUID (opsiyonel)"
}
```
`gameId` zorunludur ve Game Service'ten doğrulanır; oyun adı gösterim için kopyalanır. `hardwareProfileId` verilirse Hardware Service'ten çağıranın sahipliği doğrulanır. `deviceLinkId` verilirse çağıranın **aktif** `device_links` kaydı olmalıdır. Bilinmeyen alan reddedilir.

#### Response — 201
```json
{
  "id": "UUID",
  "gameId": "UUID",
  "gameTitle": "Oyun adı",
  "hardwareProfileId": null,
  "deviceLinkId": null,
  "source": "MANUAL",
  "status": "ACTIVE",
  "startedAt": "2026-01-01T10:00:00Z",
  "endedAt": null,
  "durationSeconds": null,
  "sampleCount": 0
}
```
24 saatten uzun süredir `ACTIVE` kalan eski oturum otomatik `ABANDONED` yapılır (`endedAt = startedAt + 24 sa`) ve yeni başlatmayı engellemez.

#### Hata Durumları
- 400: Geçersiz/eksik gövde, bilinmeyen alan (`INVALID_REQUEST`)
- 401: Token yok/geçersiz; cihaz/servis token'ı
- 403: Hesap pasif/silinmeye alınmış
- 409: Zaten aktif oturum var (`ACTIVE_SESSION_EXISTS`); 1000 oturum sınırı (`SESSION_LIMIT_REACHED`)
- 422: Oyun yok (`GAME_NOT_FOUND`); profil yok/başkasının (`HARDWARE_PROFILE_NOT_FOUND`); bağlantı yok/başkasının/devre dışı (`DEVICE_LINK_NOT_FOUND`)
- 429: Hız sınırı
- 503: Game/Hardware yanıtsız veya sözleşme dışı (`GAME_SERVICE_UNAVAILABLE`, `HARDWARE_SERVICE_UNAVAILABLE`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions

Diğer mikroservisler:
Game GAME-15 (`/api/v1/game/games/batch`, anonim) ve Hardware HW-17 (`/api/v1/hardware/profiles/batch`, kullanıcı Bearer'ı iletilir). Çağrılar transaction dışında yapılır; yeniden deneme yoktur.

#### Test Durumu
INTEGRATION (PostgreSQL Testcontainers, gerçek filtre zinciri, kullanıcı token'ı gerçek Auth kaynak kodundan üretilen JWKS ile imzalı). Game/Hardware **yerel sapma (mock)**; gerçek servislerle E2E DEFERRED_CONTRACT / NOT_VERIFIED.

### PERFORMANCE — Oturumları listele (PERF-03)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/sessions

#### Yetkilendirme
Owner (yalnız kendi oturumları)

#### Query Parameters
- page: int, varsayılan 0, ≥ 0
- size: int, varsayılan 20, 1..50
- gameId, hardwareProfileId, deviceLinkId: UUID, isteğe bağlı (PERF-19); birlikte verilirse AND
- status: `ACTIVE`, `COMPLETED`, `ABANDONED`, isteğe bağlı
- startedFrom, startedTo: ISO-8601 an, oturum başlangıcına göre yarı açık aralık `[startedFrom, startedTo)`; `startedFrom < startedTo`, sınırlar 2000-01-01..2200-01-01

#### Response — 200
```json
{
  "items": [ { "id": "UUID", "gameId": "UUID", "gameTitle": "Oyun adı", "hardwareProfileId": null, "deviceLinkId": null, "source": "MANUAL", "status": "COMPLETED", "startedAt": "2026-01-01T10:00:00Z", "endedAt": "2026-01-01T11:00:00Z", "durationSeconds": 3600, "sampleCount": 0 } ],
  "page": 0,
  "size": 20,
  "hasNext": false,
  "totalElements": 1
}
```
Sıra sabittir: `startedAt DESC, id DESC`. `totalElements` filtreye uyan toplam oturum sayısıdır (PERF-14).

#### Hata Durumları
- 400: Geçersiz `page`/`size`/filtre/zaman aralığı (`INVALID_REQUEST`)
- 401: Token yok/geçersiz
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Aktif oturum (PERF-03)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/sessions/active

#### Yetkilendirme
Owner

#### Response — 200
Oturum nesnesi ([Oturum başlat](#performance--oturum-başlat-perf-03) ile aynı biçim, `status=ACTIVE`).

#### Hata Durumları
- 401: Token yok/geçersiz
- 404: Aktif oturum yok veya 24 saati aşmış (terk edilmiş sayılır) (`PERFORMANCE_NOT_FOUND`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/active

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Oturum detayı (PERF-03)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/sessions/{sessionId}

#### Yetkilendirme
Owner (başkasının oturumu 404)

#### Path Parameters
- sessionId: UUID, zorunlu

#### Response — 200
Oturum nesnesi ([Oturum başlat](#performance--oturum-başlat-perf-03) ile aynı biçim).

#### Hata Durumları
- 400: `sessionId` UUID değil
- 401: Token yok/geçersiz
- 404: Oturum yok veya başkasına ait (`PERFORMANCE_NOT_FOUND`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION (IDOR: başkasının/olmayan oturum aynı 404)

### PERFORMANCE — Oturumu bitir (PERF-04)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
POST /api/v1/performance/sessions/{sessionId}/end

#### Yetkilendirme
Owner

#### Path Parameters
- sessionId: UUID, zorunlu

#### Request Body (opsiyonel)
```json
{ "status": "COMPLETED" }
```
`status` yalnız `COMPLETED` (varsayılan) veya `ABANDONED`. `ACTIVE`, geçersiz değer ve istemci zamanı reddedilir. Bitiş zamanı sunucu saatidir.

#### Response — 200
```json
{
  "id": "UUID",
  "gameId": "UUID",
  "gameTitle": "Oyun adı",
  "hardwareProfileId": null,
  "deviceLinkId": null,
  "source": "MANUAL",
  "status": "COMPLETED",
  "startedAt": "2026-01-01T10:00:00Z",
  "endedAt": "2026-01-01T11:00:00Z",
  "durationSeconds": 3600,
  "sampleCount": 0
}
```
İdempotenttir: aynı bitiş durumu tekrar istenirse kayıt ve `version` değişmez. Eşzamanlı bitirmelerde hesap kilidi sayesinde tek kazanan oluşur.

#### Hata Durumları
- 400: Geçersiz gövde/durum (`INVALID_REQUEST`)
- 401: Token yok/geçersiz
- 404: Oturum yok veya başkasına ait
- 409: Oturum farklı bir sonuçla zaten bitmiş; mevcut sonuç ezilmez (`SESSION_ALREADY_ENDED`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}/end

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION (idempotent tekrar, 8 eşzamanlı bitirme, bitirme–silme yarışı, iyimser kilit, IDOR)

### PERFORMANCE — Oturumu sil (PERF-20)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
DELETE /api/v1/performance/sessions/{sessionId}

#### Yetkilendirme
Owner

#### Path Parameters
- sessionId: UUID, zorunlu

#### Response — 204
Gövde yok. Oturum ve bağlı raporu (CASCADE) kalıcı silinir; `ACTIVE` oturum da silinebilir.

#### Hata Durumları
- 401: Token yok/geçersiz
- 404: Oturum yok, başkasına ait veya zaten silinmiş (`PERFORMANCE_NOT_FOUND`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Tüm oturumları sil (PERF-20)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
DELETE /api/v1/performance/sessions?confirm=true

#### Yetkilendirme
Owner (yalnız çağıranın verisi)

#### Query Parameters
- confirm: boolean, zorunlu; yalnız `true` kabul edilir

#### Response — 200
```json
{ "deletedSessions": 3 }
```
Çağıranın tüm oturumları ve raporları silinir; başka kullanıcılar etkilenmez. İdempotenttir (ikinci çağrı `0`). Cihaz bağlantıları ve senkronizasyon izni korunur.

#### Hata Durumları
- 400: `confirm` eksik veya `false` (`INVALID_REQUEST`)
- 401: Token yok/geçersiz
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions?confirm=true

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Oyun performans raporu yaz (PERF-13)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
PUT /api/v1/performance/sessions/{sessionId}/report

#### Yetkilendirme
Owner. Oturum **COMPLETED** olmalıdır; oturum başına en çok bir rapor.

#### Path Parameters
- sessionId: UUID, zorunlu

#### Request Body
```json
{
  "resolution": "1920x1080",
  "graphicsPreset": "HIGH",
  "avgFps": 92.5,
  "p1LowFps": 61,
  "maxCpuTempC": 78,
  "maxGpuTempC": 81,
  "avgCpuUsagePercent": 55,
  "avgGpuUsagePercent": 97,
  "notes": "Elle girilen not"
}
```
`resolution` (`^[0-9]{3,5}x[0-9]{3,5}$`) ve `graphicsPreset` (`LOW|MEDIUM|HIGH|ULTRA|CUSTOM`) zorunlu. Ölçümler opsiyoneldir ve **girilmezse `null` kalır, sistem doldurmaz**; en az biri gerekir. Aralıklar: FPS > 0..1000, sıcaklık 0..150, kullanım 0..100, `p1LowFps ≤ avgFps`, `notes` ≤ 500. `source` her zaman `MANUAL`; istemci `source`/`sessionId` gönderemez.

#### Response — 201 (ilk yazım) / 200 (güncelleme)
```json
{
  "id": "UUID",
  "sessionId": "UUID",
  "resolution": "1920x1080",
  "graphicsPreset": "HIGH",
  "avgFps": 92.5,
  "p1LowFps": 61.0,
  "maxCpuTempC": 78.0,
  "maxGpuTempC": 81.0,
  "avgCpuUsagePercent": 55.0,
  "avgGpuUsagePercent": 97.0,
  "notes": "Elle girilen not",
  "source": "MANUAL",
  "createdAt": "2026-01-01T11:00:01Z",
  "updatedAt": "2026-01-01T11:00:01Z"
}
```
İdempotenttir: aynı değerlerle tekrar `200` döner ve kaydı değiştirmez; farklı değerler raporu tamamen değiştirir (tek satır).

#### Hata Durumları
- 400: Geçersiz alan/aralık/biçim, bilinmeyen alan (`INVALID_REQUEST`)
- 401: Token yok/geçersiz
- 404: Oturum yok veya başkasına ait
- 409: Oturum `COMPLETED` değil (`SESSION_NOT_COMPLETED`)
- 422: Hiç ölçüm yok (`REPORT_EMPTY`); `p1LowFps > avgFps` (`REPORT_INCONSISTENT`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}/report

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION + DB CHECK kısıtları gerçek PostgreSQL'de doğrulandı. Otomatik/cihaz kaynaklı ölçüm bu fazda yoktur.

### PERFORMANCE — Oyun performans raporunu oku (PERF-13)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/sessions/{sessionId}/report

#### Yetkilendirme
Owner

#### Path Parameters
- sessionId: UUID, zorunlu

#### Response — 200
Rapor nesnesi ([Rapor yaz](#performance--oyun-performans-raporu-yaz-perf-13) ile aynı biçim).

#### Hata Durumları
- 401: Token yok/geçersiz
- 404: Oturum veya rapor yok, ya da başkasına ait (`PERFORMANCE_NOT_FOUND`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}/report

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Oyun performans raporunu sil (PERF-20)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
DELETE /api/v1/performance/sessions/{sessionId}/report

#### Yetkilendirme
Owner (başkasının raporu silinemez)

#### Path Parameters
- sessionId: UUID, zorunlu

#### Response — 204
Gövde yok. Yalnız rapor silinir; oturum korunur ve rapor yeniden yazılabilir.

#### Hata Durumları
- 401: Token yok/geçersiz
- 404: Rapor yok, zaten silinmiş veya başkasına ait (`PERFORMANCE_NOT_FOUND`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}/report

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Oturum örneklerini sil (PERF-05..12)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
DELETE /api/v1/performance/sessions/{sessionId}/samples

#### Yetkilendirme
Owner (kullanıcı token'ı; cihaz token'ı kabul edilmez). Başkasının veya olmayan oturum aynı 404'ü döner.

#### Path Parameters
- sessionId: UUID, zorunlu

#### Response — 204
Gövde yok. Oturumun tüm ölçüm örnekleri silinir ve `sampleCount` 0'a döner; oturum ve rapor korunur. Örnek yoksa da 204 (idempotent). Silinen aralık yeniden gönderilebilir; hesap örnek kotası serbest kalır.

#### Hata Durumları
- 401: Token yok/geçersiz; cihaz/servis token'ı
- 403: Hesap pasif/silinmeye alınmış
- 404: Oturum yok veya başkasına ait (`PERFORMANCE_NOT_FOUND`)
- 429: Hız sınırı
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}/samples

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Oturum örneklerini oku (PERF-14/19)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/sessions/{sessionId}/samples

#### Yetkilendirme
Owner (kullanıcı token'ı; cihaz/servis token'ı kabul edilmez). Başkasının veya olmayan oturum aynı 404'ü döner.

#### Path/Query Parameters
- sessionId: UUID, zorunlu
- metric: `CPU_TEMP_C`, `GPU_TEMP_C`, `CPU_USAGE_PERCENT`, `GPU_USAGE_PERCENT`, `RAM_USED_MB`, `VRAM_USED_MB`, `DISK_READ_MBPS`, `DISK_WRITE_MBPS`, `FAN_RPM`, `FPS` (kanonik büyük harf); verilmezse tümü
- from: ISO-8601 an, isteğe bağlı, `capturedAt >= from`
- to: ISO-8601 an, isteğe bağlı, `capturedAt < to`; `from < to` olmalı, sınırlar 2000-01-01..2200-01-01
- page: int, varsayılan 0, ≥ 0
- size: int, varsayılan 100, 1..500

#### Response — 200
```json
{
  "items": [ { "metric": "FPS", "unit": "FPS", "capturedAt": "2026-01-01T10:00:01Z", "value": 60.0 } ],
  "page": 0,
  "size": 100,
  "hasNext": false
}
```
Sıra sabittir: `capturedAt, metric, id`. Yanıt yalnız ölçümü taşır (örnek/cihaz/oturum iç kimlikleri yok). `Cache-Control: private, no-store`.

#### Hata Durumları
- 400: Geçersiz `metric`/`from`/`to`/`page`/`size` (`INVALID_REQUEST`)
- 401: Token yok/geçersiz; cihaz/servis token'ı
- 403: Hesap pasif/silinmeye alınmış
- 404: Oturum yok veya başkasına ait (`PERFORMANCE_NOT_FOUND`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}/samples

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION (okuma uçlarında hız sınırı yok; bkz. I-26)

### PERFORMANCE — Oturum istatistikleri (PERF-15)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/sessions/{sessionId}/stats

#### Yetkilendirme
Owner (kullanıcı token'ı). Başkasının veya olmayan oturum aynı 404'ü döner.

#### Path/Query Parameters
- sessionId: UUID, zorunlu
- metric, from, to: örnek okuma ucundakiyle aynı (isteğe bağlı)

#### Response — 200
```json
{
  "sessionId": "UUID",
  "sampleCount": 3,
  "metrics": [
    { "metric": "FPS", "unit": "FPS", "count": 3, "sessionCount": 1, "avg": 60.0, "min": 30.0, "max": 90.0,
      "firstAt": "2026-01-01T10:00:00Z", "lastAt": "2026-01-01T10:00:02Z" }
  ]
}
```
Değerler yalnız saklanmış gerçek örneklerden hesaplanır. Örneği olmayan metrik listede **yer almaz** (0 ile doldurulmaz); örneksiz oturum `sampleCount=0`, `metrics=[]` döner. `sampleCount` metriklerin `count` toplamıdır. Sıra: metrik adı.

#### Hata Durumları
- 400: Geçersiz `metric`/`from`/`to` (`INVALID_REQUEST`)
- 401: Token yok/geçersiz; cihaz/servis token'ı
- 403: Hesap pasif/silinmeye alınmış
- 404: Oturum yok veya başkasına ait (`PERFORMANCE_NOT_FOUND`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}/stats

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Hesap istatistikleri (PERF-15/19)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/stats

#### Yetkilendirme
Owner (yalnız kendi oturumları; başka hesaplarla toplama yoktur).

#### Query Parameters
- gameId, hardwareProfileId, deviceLinkId: UUID, isteğe bağlı oturum filtresi
- status: `ACTIVE`, `COMPLETED`, `ABANDONED`, isteğe bağlı
- startedFrom, startedTo: ISO-8601 an, oturum başlangıcına göre yarı açık aralık `[startedFrom, startedTo)`
- metric: tek metrik, isteğe bağlı

#### Response — 200
```json
{
  "sessionCount": 3,
  "sampleCount": 4,
  "metrics": [
    { "metric": "FPS", "unit": "FPS", "count": 3, "sessionCount": 2, "avg": 80.0, "min": 40.0, "max": 100.0,
      "firstAt": "2026-01-01T10:00:00Z", "lastAt": "2026-01-01T12:00:00Z" }
  ]
}
```
Ortalama **örnek ağırlıklıdır** (oturum ortalamalarının ortalaması değil). `sessionCount` filtreye uyan tüm oturumları (örneksizler dahil) sayar; metrik `sessionCount` o metrikten örneği olan oturumları. Örneksiz sonuçta `metrics=[]`. Maliyet notu: hesap kotası (500.000 örnek) tek sorguda taranabilir üst sınırdır (I-27).

#### Hata Durumları
- 400: Geçersiz filtre/metrik/zaman aralığı (`INVALID_REQUEST`)
- 401: Token yok/geçersiz; cihaz/servis token'ı
- 403: Hesap pasif/silinmeye alınmış
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/stats

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Oturum grafik serisi (PERF-16)

Durum: IMPLEMENTED
Sorumlu: Alper
Tüketici servisler: Frontend (PWEB, planlı)

#### Endpoint
GET /api/v1/performance/sessions/{sessionId}/series

#### Yetkilendirme
Owner (kullanıcı token'ı). Başkasının veya olmayan oturum aynı 404'ü döner.

#### Path/Query Parameters
- sessionId: UUID, zorunlu
- metric: zorunlu, tek metrik
- from, to: isteğe bağlı zaman penceresi (`captured_at`, yarı açık)
- maxPoints: int, varsayılan 300, 10..1000

#### Response — 200
```json
{
  "sessionId": "UUID",
  "metric": "FPS",
  "unit": "FPS",
  "resolution": "BUCKET",
  "downsampled": true,
  "bucketMillis": 199991,
  "aggregation": "AVG_MIN_MAX",
  "totalSamples": 20000,
  "points": [ { "at": "2026-01-01T10:00:00Z", "value": 54.5, "min": 30.0, "max": 79.0, "count": 200 } ]
}
```
Pencerede en çok `maxPoints` örnek varsa `resolution=RAW` (her nokta gerçek örnek, `count=1`, `bucketMillis=null`, `aggregation=NONE`). Daha fazlaysa örnekler gerçek ilk/son zamanları arasında **eşit genişlikli kovalara** bölünür; her nokta kovanın ortalama/min/max/örnek sayısını taşır, `at` kova başlangıcıdır ve yanıt açıkça `downsampled=true` ile bunu bildirir. Nokta sayısı `maxPoints`'i aşmaz; `totalSamples` toplam örnek sayısını korur (kovaların `count` toplamı). Veri yoksa `points=[]`.

#### Hata Durumları
- 400: `metric` yok/bilinmeyen, `maxPoints` aralık dışı, geçersiz zaman (`INVALID_REQUEST`)
- 401: Token yok/geçersiz; cihaz/servis token'ı
- 403: Hesap pasif/silinmeye alınmış
- 404: Oturum yok veya başkasına ait (`PERFORMANCE_NOT_FOUND`)
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
API Gateway üzerinden /api/v1/performance/sessions/{sessionId}/series

Diğer mikroservisler:
-

#### Test Durumu
INTEGRATION

### PERFORMANCE — Hesabın donanım profili performans özeti (Hardware, PERF-21)

Durum: IMPLEMENTED (sözleşme) — gerçek servis kimliği ile uçtan uca BLOCKED (REQ-04-A)
Sorumlu: Alper
Tüketici servisler: Hardware (planlı)

#### Endpoint
GET /internal/v1/accounts/{accountId}/performance-summary

#### Yetkilendirme
Servis token'ı (`svc+jwt`): `aud=performance-service`, `sub=svc:hardware-service`, `scope=summary:read`, `account_id` = yoldaki `accountId`. Başka konu/kapsam/hesap → 403; token yok, kullanıcı/cihaz token'ı veya yanlış audience → 401. Gateway üzerinden dışarı açılmaz.

#### Path/Query Parameters
- accountId: UUID, zorunlu
- hardwareProfileId: UUID, zorunlu

#### Response — 200
```json
{
  "hardwareProfileId": "UUID",
  "sessionCount": 2,
  "sampleCount": 3,
  "lastMeasuredAt": "2026-01-01T10:00:10Z",
  "metrics": [ { "metric": "FPS", "unit": "FPS", "count": 2, "sessionCount": 2, "avg": 60.0, "min": 50.0, "max": 70.0, "firstAt": "2026-01-01T10:00:00Z", "lastAt": "2026-01-01T10:00:10Z" } ]
}
```
Yalnız **o hesabın, o donanım profiliyle, tamamlanmış (`COMPLETED`)** oturumları özetlenir. Oturum/oyun/cihaz kimliği, oyun adı veya ham örnek **dönmez**. Ölçüm yoksa `sessionCount=0`, `metrics=[]`, `lastMeasuredAt=null`. Başka hesapların verisiyle toplama yapılmaz. `Cache-Control: private, no-store`.

#### Hata Durumları
- 400: Eksik/geçersiz `hardwareProfileId` (`INVALID_REQUEST`)
- 401: Token yok/geçersiz; yanlış audience; kullanıcı/cihaz token'ı
- 403: Yanlış servis konusu, kapsam veya hesap bağı; hesap pasif
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Hardware → Performance (RestClient; servis token'ı Auth'tan alınır, REQ-04-A)

#### Test Durumu
INTEGRATION (yerel imzalı sözleşme token'ı; gerçek Auth/Hardware entegrasyonu BLOCKED)

### PERFORMANCE — Oyun performans referansı (Recommendation, PERF-21)

Durum: IMPLEMENTED (sözleşme) — gerçek servis kimliği ile uçtan uca BLOCKED (REQ-04-A)
Sorumlu: Alper
Tüketici servisler: Recommendation (planlı)

#### Endpoint
GET /internal/v1/accounts/{accountId}/games/{gameId}/performance-reference

#### Yetkilendirme
Servis token'ı (`svc+jwt`): `aud=performance-service`, `sub=svc:recommendation-service`, `scope=reference:read`, `account_id` = yoldaki `accountId`. Diğer kurallar özet ucuyla aynı.

#### Path/Query Parameters
- accountId, gameId: UUID, zorunlu
- hardwareProfileId: UUID, isteğe bağlı (verilirse yalnız o profil)

#### Response — 200
```json
{
  "gameId": "UUID",
  "hardwareProfileId": null,
  "reliable": true,
  "sessionCount": 2,
  "fpsSampleCount": 100,
  "avgFps": 54.5,
  "minFps": 30.0,
  "maxFps": 79.0,
  "lastMeasuredAt": "2026-01-01T10:00:10Z"
}
```
Yalnız hesabın tamamlanmış oturumlarındaki **FPS** örnekleri kullanılır. `fpsSampleCount` 60'ın altındaysa `reliable=false` ve `avgFps/minFps/maxFps=null` döner (eşik varsayımdır, I-24); veri yoksa `fpsSampleCount=0`. Tahmin/doldurma yapılmaz; oturum veya cihaz kimliği dönmez.

#### Hata Durumları
- 400: Geçersiz kimlik (`INVALID_REQUEST`)
- 401: Token yok/geçersiz; yanlış audience; kullanıcı/cihaz token'ı
- 403: Yanlış servis konusu, kapsam veya hesap bağı; hesap pasif
- 500: Beklenmeyen sunucu hatası

#### Bağlantı
Frontend:
-

Diğer mikroservisler:
Recommendation → Performance (RestClient; servis token'ı Auth'tan alınır, REQ-04-A)

#### Test Durumu
INTEGRATION (yerel imzalı sözleşme token'ı; gerçek Auth/Recommendation entegrasyonu BLOCKED)

## NOTIFICATION

Sorumlu: Ortak (belirlenecek) · Gateway: `/api/v1/notification/**`

_Kayıt yok._

## ADMIN

Sorumlu: Ortak (belirlenecek) · Gateway: `/api/v1/admin/**`

_Kayıt yok._
