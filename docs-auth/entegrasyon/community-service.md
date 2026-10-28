# COMMUNITY-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Nisa · **Kod durumu:** Aktif (main'de), JWT doğrulayıcı geçici · **Güncelleme:** 2026-10-29

## ÖZET

Auth'tan Community'nin ihtiyacı büyük ölçüde hazır: JWKS, roller, servis token'ı ve izin listesi. Eksik olanlar **Community kodunda**: gerçek JWT doğrulayıcı, kapsam adı, giden çağrıların token'ı ve silme ucu.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT: `README.md` bölüm 1. Rol adları `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN` (forum moderasyonu için `MODERATOR` ve `ADMIN`)
- Servis token'ı (olay gönderme ve alma): `README.md` bölüm 3 ve 4. Tanımlı çiftler:
  - Gelen: `admin-service` → community, `social-service` → community (`events:ingest`)
  - Giden: `community-service` → `admin-service`, `community-service` → `notification-service` (`events:ingest`)
- Silme: Auth `DELETE /internal/v1/accounts/{id}` çağırır. Community için olay (`AccountDeleted`) gerekmez, `DELETE` yeter
- Banlı ve askıdaki hesap için ek kontrol gerekmez: token en fazla 90 sn yaşar

## SENİN YAPACAKLARIN

- [ ] **Kapsam adını düzelt (kesin hata):** `SecurityConfig.java` satır 30 `SCOPE_internal.events` istiyor. Auth'un verdiği kapsam `events:ingest`, yani yetki `SCOPE_events:ingest` olmalı. Şu hâliyle Social ve Admin token'ları 403 alır. `docs/api.md` Community olay ucu kaydını da güncelle
- [ ] **Gerçek JWT doğrulayıcı:** geçici decoder her token'ı reddediyor. `README.md` bölüm 1'deki kuralları ekle (`at+jwt`, `iss`, `aud=ltz-api`, RS256) ve `roles` → `ROLE_` eşlemesini yap
- [ ] **Olay ucu doğrulaması:** `aud=community-service`, `typ=svc+jwt`, `scope=events:ingest`, ömür ≤ 60 sn; `ModerationActionTaken` yalnız `svc:admin-service`, `UserBlocked` ve `UserUnblocked` yalnız `svc:social-service` (README bölüm 4)
- [ ] **Giden çağrılarda token:** `EventDeliveryClient` hiç `Authorization` göndermiyor. Admin ve notification için `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` ile `audience` verip 60 sn'lik token al, her hedef için ayrı
- [ ] Ortama `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` ver (üretici betik doldurur: `node ops/generate-auth-dev-keys.mjs`)
- [ ] **Silme ucu:** `DELETE /internal/v1/accounts/{accountId}` (`README.md` bölüm 2). Yazılmadıkça Community görevi `PENDING`, hesap silme tamamlanmaz
- [ ] Silme kararı: Auth çizgisi "silinen kullanıcının kaydı tutulmaz". Raporlarda kimlik bağını koparıp anonim bırak ya da sil
- [ ] Görsel çerez akışı: `docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md`

## AUTH'TA BEKLEYEN

- `AccountStatusChanged` olayı: **Auth'ta hazır (2026-10-29, outbox)**, ama yalnız yönetici durum değişikliğinde çıkar (silme isteği ve iptal olay üretmez) ve hedef listesi varsayılan boş; alıcı ucu yazınca `community` hedef olarak eklenir. Askıdaki hesabın içeriğini gizleme Community'nin kararı. Olay gelene kadar durum bilgisi yalnız token'ın geçerliliği (en fazla 90 sn)
- `COM-04` raporundaki `AccountDeleted` beklentisi: Auth bunu olay olarak yayınlamıyor, senkron `DELETE` ile yapıyor

## NASIL DENERSİN

Main'e Auth girdi. Gerçek yığında `node ops/generate-auth-dev-keys.mjs`, `docker compose up -d`, sonra Auth'tan token al, Community olay ucunu `Authorization: Bearer` ile çağır. Dev'de silme bekleme süresini `AUTH_DELETION_GRACE_PERIOD=PT0S` ile kısaltabilirsin (yalnız dev)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/community-service/` (talep 01, 02), `docs-auth/servis-sozlesmeleri.md` bölüm 3.5
