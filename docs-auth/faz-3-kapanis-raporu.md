# LTZ-KER-AUTH-03 — Faz 3 Kapanış Raporu (Steam ve Google ile Giriş)

> Tarih: 2026-10-21 · Sorumlu: Kerem · Kapsam: AUTH-05, AUTH-06

## 1. Durum

| Görev | Durum | Not |
| --- | --- | --- |
| AUTH-05 Steam | IMPLEMENTED (sahte sağlayıcıyla doğrulandı) | OpenID 2.0, `check_authentication`, replay, SteamID eşleştirme, e-postalı yeni hesap |
| AUTH-06 Google | IMPLEMENTED (sahte sağlayıcıyla doğrulandı) | Authorization Code + PKCE, state/nonce, iss/aud/imza, `sub` eşleştirme |

Gerçek Google/Steam ile uçtan uca deneme **yapılmadı**: `GOOGLE_CLIENT_ID/SECRET` yok (uydurulmadı). Bu iki değer eklenene kadar Google girişi `503 PROVIDER_NOT_CONFIGURED` döner; Steam anahtar gerektirmez.

## 2. Tasarım

- **Eşleştirme:** yalnız sağlayıcı kimliği (`external_identities`: UNIQUE(provider, subject), UNIQUE(account, provider)). E-posta ile sessiz birleştirme yok; kayıtlı e-postalı yeni Google kimliği `ACCOUNT_LINK_REQUIRED` alır.
- **State:** 256 bit rastgele, yalnız SHA-256 özeti saklanır, tek kullanımlık (atomik `UPDATE`), HttpOnly `__Host-ltz_oauth` cookie'siyle tarayıcıya bağlı. PKCE verifier ve nonce state'ten sunucu HMAC anahtarıyla türetilir (saklanmaz).
- **Sonuç iletimi:** sabit frontend adresine URL fragment'i (open redirect yok, token URL/log'a girmez); oturum token'ları yalnız HttpOnly cookie.
- **Steam yeni hesap:** doğrulanmış SteamID → 15 dk tek kullanımlık kayıt token'ı → e-posta + kullanıcı adı → `PENDING_VERIFICATION` hesap + AUTH-02 kodu. Kayıtlı e-postada hesap açılmaz, yanıt aynı kalır.
- **Faz 2 ile bütünleşme:** harici giriş `LoginService.loginVerifiedIdentity` üzerinden geçer; hesap durumu, TOTP challenge ve ADMIN/MODERATOR 2FA zorunluluğu atlanamaz. `amr=fed`; 2FA challenge'ı ilk faktörü (`first_factor`) taşır.
- **Dayanıklılık:** sağlayıcı çağrıları yönlendirme izlemez, 5 sn zaman aşımı; prod profilinde tüm adresler `https` değilse servis başlamaz; yalnız Google id veya yalnız secret verilirse servis başlamaz.

## 3. Migration ve ENV

- `V65__create_external_identity_tables.sql`: `external_identities`, `oauth_flows`, `openid_nonces`, `steam_registrations`, `mfa_challenges.first_factor`. V66–V70 boş.
- Yeni bağımlılık yok.

| Değişken | Servis | Secret | Zorunlu | Eksikse |
| --- | --- | --- | --- | --- |
| `GOOGLE_CLIENT_ID` | yalnız auth | Hayır (public) | Hayır | Google girişi kapalı (503) |
| `GOOGLE_CLIENT_SECRET` | yalnız auth | **Evet** | Hayır | Google girişi kapalı; yalnız biri verilirse servis başlamaz |
| `AUTH_PUBLIC_BASE_URL` | yalnız auth | Hayır | Varsayılan `http://localhost:5080` | Prod'da https zorunlu |
| `FRONTEND_PUBLIC_URL` | auth (mevcut anahtar) | Hayır | Varsayılan `http://localhost:5001` | — |

`.env` ve `.env.dev`: yalnız `JWT_ACCESS_TTL_SECONDS=300` yapıldı; yeni anahtar eklenmedi (Compose varsayılanları yeterli). Google'ı denemek için kullanıcı `GOOGLE_CLIENT_ID` ve `GOOGLE_CLIENT_SECRET` eklemeli; Google Console'da yetkili yönlendirme URI: `<AUTH_PUBLIC_BASE_URL>/api/v1/auth/oauth/google/callback`.

## 4. Açık maddeler (bu fazda kapatılmadı)

- **AUTH-13:** güvenilir istemci IP'si yok (K8); harici giriş uçlarında IP bazlı limit yok.
- **D3:** diğer servislerde iptal gecikmesi ≤5 dk (olay altyapısı yok).
- **Gateway:** CORS ve cookie → Bearer çevirisi yok.
- **Hesap bağlama:** giriş yapmış kullanıcının Google/Steam bağlaması ve bağlantıyı kaldırma ucu yok; kayıtlı e-postalı Google girişi bu yüzden `ACCOUNT_LINK_REQUIRED` ile durur. Faz 5/6 veya ayrı karar.
- **Tablo temizliği:** `oauth_flows`, `openid_nonces`, `steam_registrations` ve `attempt_throttles` büyür; periyodik temizlik işi ve start uçlarına IP limiti eklenmeli (`start` kimliksiz satır yazar).
- **Kullanıcı adı:** Google ile açılan hesaplar `g_<rastgele>` adı alır; Faz 5 profil/ad değiştirme ile birlikte ele alınmalı.
- **Production e-posta teslimi PENDING** (Mail Service).
- Gerçek Google/Steam ile entegrasyon testi yapılmadı.
