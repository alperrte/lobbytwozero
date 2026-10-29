# AUTH ENTEGRASYONU — SERVİS SERVİS

**Sahip:** Kerem (auth-service) · **Tarih:** 2026-10-29 · **Durum:** Auth main'de (PR #19), bu klasör her servisin Auth'tan ne alacağını ve ne yapacağını tek yerde toplar

Servis sahibisen kendi dosyanı aç, "SENİN YAPACAKLARIN" başlığına git. Kopyala yapıştır istekler ve kontrol listesi orada.
Her servisin Auth'a sorduklarına verilmiş cevaplar ayrıca `docs-auth/verilen-cevaplar/<servis>/` altında duruyor. Bu klasör onların üstüne, tüm servisleri yan yana koyan özet.

---

## NASIL BULURUM

| Servis | Sahip | Kod durumu | Dosya |
|---|---|---|---|
| hardware-service | Alper | Aktif | [hardware-service.md](hardware-service.md) |
| performance-service | Alper | İskelet | [performance-service.md](performance-service.md) |
| recommendation-service | Alper | İskelet | [recommendation-service.md](recommendation-service.md) |
| game-service | Hamza | Aktif | [game-service.md](game-service.md) |
| library-service | Hamza | Silme ucu var, gerisi iskelet | [library-service.md](library-service.md) |
| review-service | Hamza | Silme ucu var, gerisi iskelet | [review-service.md](review-service.md) |
| community-service | Nisa | Aktif, JWT doğrulayıcı geçici | [community-service.md](community-service.md) |
| content-service | Nisa | İskelet | [content-service.md](content-service.md) |
| event-service | Nisa | İskelet | [event-service.md](event-service.md) |
| social-service | Umut | Aktif | [social-service.md](social-service.md) |
| messaging-service | Umut | İskelet | [messaging-service.md](messaging-service.md) |
| lfp-service | Umut | İskelet | [lfp-service.md](lfp-service.md) |
| user-service | Kerem | İskelet | [user-service.md](user-service.md) |
| mail-service | Kerem | İskelet | [mail-service.md](mail-service.md) |
| admin-service | Ortak | İskelet | [admin-service.md](admin-service.md) |
| notification-service | Ortak | İskelet | [notification-service.md](notification-service.md) |
| api-gateway ve frontend | Alper / frontend | Gateway tamam, frontend Auth çağırmıyor | [gateway-ve-frontend.md](gateway-ve-frontend.md) |

Diğer dosyalar:

- [bosluk-raporu-2026-10-29.md](bosluk-raporu-2026-10-29.md): Boş bıraktığımız her şey, kimde, ne zaman kapanır
- [cihaz-kimligi-tasarimi.md](cihaz-kimligi-tasarimi.md): Masaüstü cihaz kimliği (B yolu) tasarım kaydı, uygulandı

---

## ORTAK PARÇALAR (tüm dosyalar buraya bakar)

### 1. Kullanıcı token'ını doğrulamak

- JWKS adresi: `${AUTH_SERVICE_URL}/api/v1/auth/.well-known/jwks.json`
- Kurallar: RS256, başlıkta `typ=at+jwt`, `iss=ltz-auth`, `aud` içinde `ltz-api`, `exp`/`nbf`/`iat`/`sub` zorunlu, saat farkı payı 30 sn
- `sub` hesap UUID'si. `roles` öneksiz liste: `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`. Spring'de `ROLE_` önekiyle eşle
- Token'da e-posta, kullanıcı adı, hesap durumu ve scope **yok**
- Ömür 60 sn. Banlı veya askıdaki hesap en fazla 90 sn görünür
- JWKS önbelleği en fazla 5 dk. Tanımadığın `kid` gelirse bir kez yenile
- Kod örneği: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` bölüm 3.4. Çalışan örnek: game-service (`JwtDecoderConfiguration`)

### 2. Hesap silme ucunu yazmak

Auth bir hesabı silerken her servisin ucunu çağırır. Sözleşme:

- `DELETE /internal/v1/accounts/{accountId}`, Gateway'den **geçmez**
- Doğrulanacaklar: imza ve `kid`, `typ=svc+jwt`, `iss=ltz-auth`, `aud` yalnız kendi adın (`<servis>-service`), `sub` **tam** `svc:auth-service`, `scope=account:erase`, `account_id` yoldaki `accountId` ile aynı, `jti`, ömür en fazla 60 sn
- Başarı `204` (veya `200`). İdempotent: kayıt yoksa da `204`. `202`, `3xx`, `404` onay sayılmaz
- Token'ı ve hesap verisini loglama. Yanıt gövdesi dönme
- Ayrıntı: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`, `docs-auth/servis-sozlesmeleri.md` bölüm 3.1
- Doğru yazılmış örnek: game-service `AccountErasureController` (hem `sub` hem `account_id` denetliyor)

### 3. Başka servise olay göndermek için token almak

```
POST {AUTH_SERVICE_URL}/internal/v1/service-tokens
{ "clientId": "<servis-adı>", "clientSecret": "<ortam değişkeninden>", "audience": "<hedef-servis>" }
```

- Yanıt: `accessToken`, 60 sn ömürlü, `aud=<hedef>`, `scope=events:ingest`, `sub=svc:<servis-adı>`. Her hedef için ayrı token al, önbelleğe uzun süre alma
- Gizli anahtar ortam değişkeninden gelir, koda ve repoya yazılmaz. Üreticisi: `node ops/generate-auth-dev-keys.mjs`
- Hata: yanlış anahtar `401 INVALID_CLIENT`, izinli olmayan hedef `403 SERVICE_AUDIENCE_FORBIDDEN`
- Şu an tanımlı çiftler (kimseye varsayılan yetki yok):

| Çağıran | Hedef | Kapsam | Gizli anahtar değişkeni |
|---|---|---|---|
| admin-service | community-service | `events:ingest` | `AUTH_ADMIN_SERVICE_CLIENT_SECRET` |
| social-service | community-service | `events:ingest` | `AUTH_SOCIAL_SERVICE_CLIENT_SECRET` |
| community-service | admin-service, notification-service | `events:ingest` | `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` |

- Yeni (çağıran, hedef, kapsam) çifti lazımsa: kendi klasörüne `docs-auth/verilen-cevaplar/<servis>/talep-NN-*.md` yaz, ne istediğini ve neden istediğini açıkla. Ben eklerim, kod ve test gerekir

### 4. Servis token'ı kabul etmek (olay alıcısı)

- Doğrula: imza (JWKS), `typ=svc+jwt`, `aud` kendi adın, `scope=events:ingest`, ömür en fazla 60 sn
- Spring'de kapsam yetkisi `SCOPE_events:ingest`. **`internal.events` değil**: Auth'un verdiği kapsam adı `events:ingest`
- `sub` ile olay türünü eşleştir (örn. `ModerationActionTaken` yalnız `svc:admin-service`)

### 5. Hata ve güvenlik

- Hatalar `ProblemDetail`, alt kodlar `code` alanında. Uçların tamamı `docs/api.md`, `## AUTH` bölümünde
- Token, gizli anahtar, e-posta ve kayıt değerleri loglanmaz
- Auth'un kendi uçlarında CSRF'i Auth uygular. Tarayıcıdan gelen isteklerde `X-XSRF-TOKEN` başlığı gerekir

---

## DENEME

- Auth + Gateway tek komutla: `node ops/auth-smoke.mjs` (Docker, JDK 25 ve güncel jar gerekir)
- Gerçek yığın: `docker compose up -d`, sonra Gateway `http://127.0.0.1:5080`, Mailpit `http://127.0.0.1:8025`
- İlk kurulumda bir kez: `node ops/generate-auth-dev-keys.mjs`
