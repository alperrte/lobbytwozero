# LFP-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Umut · **Kod durumu:** Uygulandı (LTZ-UMT-LFP-01..03; gerçek Auth ile uçtan uca NOT VERIFIED) · **Güncelleme:** 2026-11-13

## ÖZET

Kullanıcı token'ı (JWKS) her LFP ucunda doğrulanıyor; hesap silme iç ucu yazıldı; engel kontrolü Social'ın `blocks:check` ucuyla yapılıyor. Oyun doğrulaması game-service'in herkese açık kart ucuyla (token'sız) yapılıyor; Auth'tan yeni bir çift gerekmedi. Sözleşmeler: `docs/api.md` → `## LFP`.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT, `sub` (ilan sahipliği), `roles`: `README.md` bölüm 1
- Silme ucu sözleşmesi: `README.md` bölüm 2. LFP silme hedeflerinde
- Servis token mekanizması: `README.md` bölüm 3

## YAPILANLAR

- [x] JWT doğrulayıcı (JWKS, RS256, `typ=at+jwt`, `iss`/`aud`/`exp`/`nbf`/`iat`/`sub`); ilan güncelleme ve kapatma `sub` sahipliğiyle (başkası `403`)
- [x] `DELETE /internal/v1/accounts/{accountId}`: `svc+jwt`, `aud=lfp-service`, `scope=account:erase`, `sub=svc:auth-service` tam, `account_id` = yol, ömür ≤ 60 sn; hesabın tüm ilanları silinir, idempotent `204`
- [x] Engel ve gizlilik (LFP-17): `{clientId: "lfp-service", clientSecret, audience: "social-service"}` ile 60 sn'lik `blocks:check` token'ı (en fazla 45 sn yeniden kullanılır) → `POST /internal/v1/blocks/check`. Gizli anahtar `AUTH_LFP_SERVICE_CLIENT_SECRET` compose ile lfp-service'e aktarıldı; boşsa ilan okuma/iletişim fail-closed `503`
- [x] Oyun doğrulama (LFP-18): game-service `GET /api/v1/game/{gameId}/card` herkese açık; kimlik gönderilmez, servis token'ı gerekmedi (talep yok)

## AUTH'TA BEKLEYEN

- Yok. LFP → messaging çağrısı yapılmaz: istemci, LFP'nin döndürdüğü `recipientId` ile messaging'in birebir sohbet ucunu kendi token'ıyla çağırır (kullanıcı token'ı servisler arasında iletilmez).

## DOĞRULANMAYANLAR

- Gerçek Auth servis token'ıyla hesap silme (`lfp` görevinin `CONFIRMED` olması) ve gerçek Auth + Social ile engel sorgusu: NOT VERIFIED.

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/lfp-service/`, `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md`, `docs-lfp/TASK-03.md`
