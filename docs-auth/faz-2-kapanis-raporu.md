# LTZ-KER-AUTH-02 — Faz 2 Kapanış Raporu (Giriş, JWT Oturum, 2FA)

> Tarih: 2026-10-21 · Sorumlu: Kerem · Kapsam: AUTH-03, 04, 07, 08, 13, 15

## 1. Gereksinim durumu

| Görev | Durum | Not |
| --- | --- | --- |
| AUTH-03 Giriş | IMPLEMENTED | E-posta veya kullanıcı adı; hesap varlığı sızmaz (aynı 401, sahte hash ile eşit süre) |
| AUTH-04 2FA | IMPLEMENTED | TOTP (RFC 6238), kurtarma kodları, replay koruması; ADMIN/MODERATOR için zorunlu |
| AUTH-07 JWT | IMPLEMENTED | RS256 + JWKS, refresh rotation, aile bazlı reuse tespiti |
| AUTH-08 Logout | IMPLEMENTED | Tek cihaz ve tüm cihazlar; access token'lar auth uçlarında anında geçersiz |
| AUTH-13 Hesap güvenliği | PARTIAL | Kimlik ve hesap bazlı kilit var; **IP bazlı limit yok** (güvenilir istemci IP'si yok, K8) |
| AUTH-15 Güvenlik olayları | IMPLEMENTED | Giriş, 2FA, rotation, reuse, logout olayları + `request_id` |

## 2. Güvenlik modeli

- **JWT:** Özel anahtar yalnız auth-service'te (`AUTH_JWT_PRIVATE_KEY`, RSA ≥3072). `kid` = RFC 7638 parmak izi. Doğrulayıcı yalnız RS256 kabul eder (alg=none/HS256 reddi), `iss/aud/exp/iat/sub/typ=at+jwt/kid` zorunlu. Roller yalnız veritabanından claim'e girer.
- **Oturum:** `auth_sessions` (= `sid`) + tek kullanımlık `refresh_tokens` (SHA-256 özeti). Satır kilidiyle eşzamanlı yenilemede yalnız biri döndürür; 10 sn içinde tekrar gelen eski token `409`, sonrası çalıntı sayılır ve aile iptal edilir. Mutlak oturum ömrü 30 gün.
- **Cookie/CSRF:** HttpOnly + Secure cookie; access `Lax`, refresh `Strict` ve dar path. SPA double-submit CSRF (`__Host-XSRF-TOKEN` + `X-XSRF-TOKEN`).
  - Geliştirme sırasında bulunan ve kapatılan açık: Spring Security, `BearerTokenResolver`'ın token bulduğu istekleri CSRF'den muaf tutar. Cookie okuyan resolver bu yüzden CSRF'yi devre dışı bırakıyordu. Çözüm: resolver yalnız başlık okur; cookie, CSRF filtresinden **sonra** başlığa çevrilir (`AccessTokenCookieFilter`). Testle doğrulandı.
- **2FA:** Secret AES-256-GCM (AAD = hesap kimliği, `AUTH_TOTP_ENCRYPTION_KEY`). Parola doğru + TOTP etkin → yalnız challenge (cookie yok). Challenge başına 5 deneme + hesap bazlı kilit. Kapatma için parola + kod. Zorunlu rolde TOTP yoksa oturum verilmez; rol verme (Faz 5) TOTP etkinliğini ön koşul olarak denetlemelidir.
- **D3:** Auth uçlarında oturum, hesap durumu ve `ver` her istekte denetlenir (anında iptal). Diğer servislerde iptal gecikmesi en fazla access token ömrüdür (5 dk); olay tabanlı iptal **karar kapısında**.

## 3. Migration ve ENV

- `V63__create_session_tables.sql`: `auth_sessions`, `refresh_tokens`, `attempt_throttles`, `accounts.token_version`, `security_events.request_id`.
- `V64__create_two_factor_tables.sql`: `totp_credentials`, `recovery_codes`, `mfa_challenges`. V65–V70 Faz 3–6 için boş.
- Kaldırılan bağımlılık: jjwt (kullanılmıyordu). Yeni bağımlılık yok.

| Değişken | Servis | Secret | Zorunlu | Eksikse |
| --- | --- | --- | --- | --- |
| `AUTH_JWT_PRIVATE_KEY` | yalnız auth | Evet | Evet | Servis başlamaz |
| `AUTH_TOTP_ENCRYPTION_KEY` | yalnız auth | Evet | Evet | Servis başlamaz |
| `JWT_ACCESS_TTL_SECONDS` | ortak blok | Hayır | Varsayılan 300 | — (900 → 300) |
| `JWT_AUDIENCE` | auth (varsayılan `ltz-api`) | Hayır | Hayır | Varsayılan kullanılır |

## 4. Test sonuçları

- `mvn -f backend/pom.xml -pl services/auth-service clean verify` (JDK 25, Testcontainers PostgreSQL 17 + Mailpit): **142/142**, satır kapsamı **%95,30**. Faz 1 testleri dahil.
- Test log'larında parola, token, e-posta ve üretilmiş parola izi yok.

## 5. Kalan riskler ve Faz 3 ön koşulları

- **IP bazlı limit yok (K8):** Gateway `trusted-proxies` ayarı ve güvenilir proxy zinciri onay bekliyor (ortak servis).
- **Diğer servislerde iptal:** D3 kararı (olay altyapısı) bekliyor; şimdilik ≤5 dk.
- **Gateway CORS ve cookie → Bearer:** Tarayıcıdan entegrasyon için gerekli, ortak değişiklik olarak onay bekliyor.
- **`attempt_throttles` büyümesi:** Bilinmeyen kimlikler için de satır oluşur; periyodik temizlik işi ileride eklenmeli.
- **Production e-posta teslimi PENDING** (Faz 1'den).
- **Faz 3 ön koşulu:** `.env.dev` dosyasına `AUTH_OTP_HMAC_KEY`, `AUTH_JWT_PRIVATE_KEY`, `AUTH_TOTP_ENCRYPTION_KEY` ve `COMPOSE_PROFILES=dev-mail` eklenmeli.
