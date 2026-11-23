# MESSAGING-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Umut · **Kod durumu:** Uygulandı (LTZ-UMT-MSG-01..08; gerçek Auth ile uçtan uca PASS) · **Güncelleme:** 2026-11-13

## ÖZET

Kullanıcı token'ı (JWKS) her Messaging ucunda doğrulanıyor; hesap silme iç ucu Auth'un gerçek servis token'ıyla canlı doğrulandı (`messaging` görevi `CONFIRMED`); engel kontrolü Social'ın `blocks:check` ucuyla yapılıyor. Tek eksik: yeni mesaj bildirimi (MSG-16) için `messaging-service → notification-service: events:ingest` çifti (talep-01). Sözleşmeler: `docs/api.md` → `## MESSAGING`; kanıt: `docs-messaging/TASK-08.md`.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT, `sub`, `roles`: `README.md` bölüm 1
- Silme ucu sözleşmesi: `README.md` bölüm 2. Messaging silme hedeflerinde (`ltz.auth.deletion.targets` içinde `messaging`)
- Servis token mekanizması: `README.md` bölüm 3; `messaging-service → social-service: blocks:check` tanımlı

## YAPILANLAR

- [x] JWT doğrulayıcı (JWKS, RS256, `typ=at+jwt`, `iss`/`aud`/`exp`/`nbf`/`iat`/`sub`); kimlik yalnız `sub`'dan, gövdedeki kimlik alanı `400`
- [x] `DELETE /internal/v1/accounts/{accountId}`: `svc+jwt`, `aud=messaging-service`, `scope=account:erase`, `sub=svc:auth-service` tam, `account_id` = yol, ömür ≤ 60 sn; idempotent `204`. **Canlı PASS (2026-11-13):** silme onayından sonra görev ilk denemede `CONFIRMED`, `messaging_db`'de hesabın izi kalmadı
- [x] Engel kontrolü (MSG-15): `{clientId: "messaging-service", clientSecret, audience: "social-service"}` ile 60 sn'lik `blocks:check` token'ı → `POST /internal/v1/blocks/check`. Gizli anahtar `AUTH_MESSAGING_SERVICE_CLIENT_SECRET`; boşsa fail-closed `503`. **Canlı PASS** (iki yön, grup)
- [x] Yeni mesaj bildirimi (MSG-16): `MessageSent` outbox + relay yazıldı (`POST {NOTIFICATION_SERVICE_URL}/internal/events`, `audience: "notification-service"`, `scope=events:ingest`). Varsayılan adaptör `unavailable`: Auth çifti ve notification alıcı ucu gelene kadar olay gönderilmez

## AUTH'TA BEKLEYEN

- `messaging-service → notification-service: events:ingest` çifti: [talep-01](../verilen-cevaplar/messaging-service/talep-01-notification-events-ingest.md). Notification alıcı ucu da henüz yok (ortak servis, sahibi belirlenmedi)
- Kullanıcı adı, avatar, çevrimiçi durumu: Auth'ta yok ve olmayacak (User Service; frontend `POST /api/v1/user/profiles/batch`)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/messaging-service/`, `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md`, `docs-messaging/TASK-07.md`, `docs-messaging/TASK-08.md`
