# HARDWARE-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Alper · **Kod durumu:** Aktif · **Güncelleme:** 2026-10-29

## ÖZET

Kullanıcı JWT doğrulaması ve hesap silme ucu hazır ve gerçek Auth ile denenmiş. Masaüstü senkronu (`sync/snapshots`) için gereken **cihaz kimliği Auth'ta hazır** (2026-10-29): bağlama, listeleme, iptal ve `dev+jwt` token ucu uygulandı. Hardware tarafında `dev+jwt` doğrulayıcısı bekliyor.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı token'ı: JWKS, `at+jwt`, `aud=ltz-api`, `roles`. Hardware uygun doğruluyor (`HardwareJwtValidators`)
- Hesap silme: `DELETE /internal/v1/accounts/{accountId}` yazılmış, Auth worker'ı gerçek token'la çağırıyor ve `204` alıyor (E2E ile denendi)
- Alper'in E2E betiği Auth, Gateway ve Hardware'i birlikte deniyor: `ops/hardware-auth-e2e.mjs`

## SENİN YAPACAKLARIN

- [ ] E2E betiğinde Auth'a `AUTH_DELETION_GRACE_PERIOD: 'PT0S'` ver (silme artık 14 gün bekleme süresiyle başlıyor, betik aksi halde silme adımında takılır). Kesin ortam değişkeni adı bu; `PT0S` yalnız dev/test içindir
- [ ] Betikteki "15 ACK" ifadesini "11 ACK", "15 dk"yı "90 sn" yap (Auth artık 60 sn token, 11 servisin silme ucu hâlâ yok)
- [ ] `dev+jwt` için ayrı doğrulayıcı yaz: `typ=dev+jwt`, `aud=hardware-service`, `sub=deviceId`, `account_id`, `scope` içinde `hardware:sync:write`, ömür ≤ 60 sn. Mevcut `svc:` önek kuralına dokunma. Uç sözleşmesi: `docs/api.md` "Masaüstü cihaz kimliği"
- [ ] Re-test: Google/Steam testlerini DEBUG logda bir kez çalıştır (Auth artık hassas logları INFO'ya sabitledi)

## AUTH'TA BEKLEYEN

- **Masaüstü cihaz kimliği (B yolu): UYGULANDI.** Token: `typ=dev+jwt`, `aud=hardware-service`, `sub=deviceId`, `account_id=hesap`, `scope=hardware:sync:write`, 60 sn. Hardware karşılığı `AuthenticatedHardwareDevice(userId=account_id, deviceId=sub)`. Tasarımdan farklar: en fazla 5 etkin cihaz ve günde 10 bağlama, 365 gün, etiket yalnız harf/rakam/boşluk/`._()-`, token ucunda hesap kilidi yok (IP sınırı Gateway'de), `DEVICE_TOKEN_REJECTED` olayı yok
- Hesap durumu olayı (`AccountStatusChanged`): Auth'ta hazır (outbox), yalnız yönetici durum değişikliğinde çıkar, hedef listesi varsayılan boş. Hardware için gerekli değil: askı ve yasak en fazla 90 sn içinde token ömrüyle yayılır. İstersen alıcı uç yazarsın, `hardware` hedef olarak eklenir

## CİHAZ KİMLİĞİ: UÇTAN UCA

Kısa sözleşme `docs/api.md` "Masaüstü cihaz kimliği" bölümünde. Burada senin doğrulayıcıyı ve masaüstü akışını yazman için gereken ayrıntı var.

### 1. Akış (kim ne yapar)

1. **Kullanıcı oturum açar.** Giriş (`POST /api/v1/auth/login`) çerez döndürür, gövdede token yoktur. Önce `GET /api/v1/auth/csrf` ile CSRF çerezi alınır, sonra giriş yapılır
2. **Kullanıcı cihazı bağlar:** `POST /api/v1/auth/devices` (Gateway üzerinden). Başlıklar: `Origin` (izinli origin) ve `X-XSRF-TOKEN` (= `__Host-XSRF-TOKEN` çerezinin değeri). Gövde: `{ "label": "Desk PC", "password": "<hesap parolası>" }`, TOTP açıksa ek olarak `code` ya da `recoveryCode`. Yanıt `201`: `{ deviceId, deviceSecret, label, createdAt, expiresAt }`. **`deviceSecret` yalnız bu yanıtta görünür**, cihaz onu güvenli yerde saklamalı; kaybolursa cihaz silinip yeniden bağlanır
3. **Cihaz token alır:** `POST /api/v1/auth/devices/token`, gövde `{ "deviceId": "...", "deviceSecret": "..." }`. Çerez ve CSRF gerekmez, Gateway üzerinden çalışır (gerçek yığında denendi). Yanıt `200`, `Cache-Control: no-store`: `{ accessToken, tokenType: "Bearer", expiresIn: 60, scope: "hardware:sync:write" }`
4. **Cihaz Hardware'e gönderir:** `POST /api/v1/hardware/profiles/{profileId}/sync/snapshots` ve `Authorization: Bearer <accessToken>`. Token 60 sn yaşar, her senkron öncesi yenisi alınır; süresi dolmuş token'ı yenilemek için yeniden `devices/token` çağrılır (refresh yok)

### 2. Cihaz token'ı (çözülmüş)

```json
header:  { "alg": "RS256", "typ": "dev+jwt", "kid": "<JWKS'teki kid>" }
payload: { "iss": "ltz-auth", "sub": "<deviceId, UUID>", "aud": ["hardware-service"],
           "account_id": "<hesap, UUID>", "scope": "hardware:sync:write",
           "jti": "<her token'da yeni>", "iat": 1000, "nbf": 1000, "exp": 1060 }
```

Rol, `sid`, `ver`, `amr` yoktur. İmza anahtarı kullanıcı token'ıyla aynı JWKS'ten gelir (`/api/v1/auth/.well-known/jwks.json`, `kid` ile eşleştir).

### 3. Hardware doğrulayıcı kontrol listesi

Yeni ve ayrı doğrulayıcı yaz; kullanıcı doğrulayıcısını (`at+jwt`) ve `svc:` önek kuralını değiştirme. Hepsi zorunlu:

- İmza (RS256, JWKS, `kid`) ve `iss=ltz-auth`
- Başlık `typ` tam olarak `dev+jwt` (başka tür kabul edilmez, `at+jwt` ve `svc+jwt` reddedilir)
- `aud` içinde `hardware-service`
- `sub` ve `account_id` geçerli UUID
- `scope` içinde `hardware:sync:write`; başka kapsamla bu uç açılmaz
- `exp - iat ≤ 60 sn`, `nbf` ve saat sapması için en çok 30 sn tolerans
- Principal: `AuthenticatedHardwareDevice(userId = account_id, deviceId = sub)`. Cihaz kullanıcı yerine geçemez, ADMIN cihaz yerine geçemez
- Hesabın aktif olması, cihaz sahipliği ve "aktif cihaz" politikası senin ve Performance'ın kararı (PERF-01); Auth bunları söylemez

### 4. Auth'tan gelebilecek hatalar

| Uç | Durum | Kod | Anlamı |
|---|---|---|---|
| `devices/token` | 401 | `INVALID_DEVICE_CREDENTIALS` | Yanlış anahtar, bilinmeyen, iptal edilmiş ya da süresi dolmuş cihaz, pasif hesap (hepsi aynı yanıt) |
| `devices` (bağlama) | 400 | `CURRENT_PASSWORD_INVALID`, `INVALID_TWO_FACTOR_CODE` | Yeniden doğrulama başarısız |
| `devices` (bağlama) | 409 | `PASSWORD_REQUIRED` | Hesapta parola yok (yalnız Google/Steam hesabı) |
| `devices` (bağlama) | 409 | `DEVICE_LIMIT_REACHED` | Hesapta 5 etkin cihaz var, birini iptal et |
| `devices` (bağlama) | 429 | `TOO_MANY_ATTEMPTS` | Günlük bağlama sınırı (10) ya da deneme sınırı |
| `devices/{id}` | 404 | `DEVICE_NOT_FOUND` | Cihaz yok ya da başkasının |

### 5. Cihaz ne zaman otomatik iptal olur

Parola değişimi ya da sıfırlama, e-posta değişimi, hesabın askıya alınması/yasaklanması/devre dışı kalması ve hesap silme onayı cihazları iptal eder. Yeni token alınamaz; elindeki token en çok 60 sn daha yaşar. İptal olayı yoktur, Hardware'in ek bir şey yapması gerekmez.

### 6. Geliştirirken limit

Hesap başına 5 etkin cihaz ve günde 10 bağlama var. Denemeler arasında `DELETE /api/v1/auth/devices/{deviceId}` (204) ile cihazı iptal et ya da yeni test hesabı aç, yoksa 409/429 alırsın.

## NASIL DENERSİN

- Bütün Auth tarafı (cihaz bağlama, token, iptal, Gateway dahil): `node ops/auth-smoke.mjs` (Docker, JDK 25 ve güncel jar gerekir). Cihaz adımları "Device ..." satırlarında görünür
- Silme ve Hardware: `node ops/hardware-auth-e2e.mjs` (Docker, JDK 25 ve güncel jar gerekir)
- Jar'lar kaynaktan eskiyse betikler "stale artifact" diye durur, o zaman ilgili servisi yeniden derle

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/hardware-service/` (talep 01 ile 04), `docs-auth/entegrasyon/cihaz-kimligi-tasarimi.md`
