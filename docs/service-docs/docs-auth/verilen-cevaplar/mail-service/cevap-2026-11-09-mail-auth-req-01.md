# MAIL-AUTH-REQ-01 — Auth cevabı (2026-11-09)

**Gönderen:** Kerem (auth-service) · **Hedef:** mail-service · **Durum:** Auth tarafı HAZIR (yerel testli); gerçek Mail ile uçtan uca **NOT VERIFIED**.

## 1. Adres çözme ucu

`GET /internal/v1/accounts/{accountId}/contact` — kapsam `account:contact:read`, yalnız `mail-service` istemcisine tanımlı.
Yanıt: `{ "accountId", "status", "email", "emailVerified" }`. Hesap yoksa `404`. Adresi olmayan (yalnız Steam) ya da anonimleştirilmiş
hesapta `email: null`, `emailVerified: false`. Parola, token, rol, kullanıcı adı dönmez; adres ve gövde loglanmaz.
Token: `typ=svc+jwt`, `aud=auth-service`, `sub=svc:mail-service`, `scope=account:contact:read` (Auth'tan `POST /internal/v1/service-tokens`, `audience` verilmez).

## 2. Mail istemcisi

`ltz.auth.service-clients.clients.mail-service` (`AUTH_MAIL_SERVICE_CLIENT_SECRET`, ≥32 karakter; boşsa istemci kapalı).
`.env.example` (boş), `docker-compose.yml` (auth-service **ve** mail-service ortamı) ve `ops/dev.mjs` anahtar üreticisine eklendi
(`node ops/dev.mjs keys` boş olanı üretir). Mail aynı değeri `AUTH_MAIL_SERVICE_CLIENT_SECRET` adıyla okuyabilir.

## 3. `mail:notify` hedefi

Mekanizma hazır: `...clients.<servis>.targets.mail-service=mail:notify` satırı eklenen servis `audience=mail-service` token'ı alır
(`sub=svc:<servis>`, `account_id` yok). Tanımsız istemci `403 SERVICE_AUDIENCE_FORBIDDEN`. Üretimde şimdilik hiçbir servise satır yok;
bir developer isteyince satır + `AUTH_<SERVIS>_SERVICE_CLIENT_SECRET` eklenir.

## 4. `WELCOME`

`POST /internal/v1/mail/security-notifications` ucuna `type=WELCOME` gider (`locale` yok → iki dilli). Tetik:

- E-posta doğrulaması başarılı olunca (Steam + e-posta akışı dahil), commit sonrası, en iyi çaba.
- Google ile **yeni** hesap ilk kez doğrulanmış e-postayla açılınca.

`idempotencyKey` hesap başına sabit: `UUID.nameUUIDFromBytes("WELCOME:" + accountId)`; tekrar doğrulama/bağlama ikinci mail üretmez.
Doğrulanmamış adrese gönderilmez; Mail hata verirse doğrulama/kayıt başarılı kalır.

**Göndermediğimiz durum (neden):** Mevcut hesaba Google/Steam **bağlama** `WELCOME` üretmez; hesap yeni değildir ve zaten
`EXTERNAL_IDENTITY_LINKED` güvenlik bildirimi gider. Steam ile ilk kayıt e-posta doğrulamasından geçtiği için orada gider.

## Doğrulama

Auth testleri: contact ucu (200/403/401/404/`email:null`/log), token testleri (`mail-service`, `mail:notify`), WELCOME (tek sefer,
yanlış kodda yok, Google yeni hesap). Beklenen: Mail `WELCOME` türünü kabul eder (`docs/api.md` MAIL bölümündeki "bilinmeyen tür 400"
notu V85 sonrası güncellenmelidir — Mail sahibinde).
