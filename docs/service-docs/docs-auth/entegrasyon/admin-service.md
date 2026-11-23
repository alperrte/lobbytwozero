# ADMIN-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Ortak · **Kod durumu:** İskelet · **Güncelleme:** 2026-10-29

## ÖZET

Auth'ın admin için iç uçları hazır ve test edilmiş. Admin servisi yazılınca bağlanır. Ek olarak admin, Community'den rapor olayı alacak ve moderasyon kararını Community'ye gönderecek.

## AUTH'TAN HAZIR OLANLAR

- Servis token'ı: `clientId=admin-service`, gizli anahtar `AUTH_ADMIN_SERVICE_CLIENT_SECRET` (boşsa kapalı)
- İç uçlar (Gateway'den geçmez): `GET /internal/v1/accounts/{id}`, `POST .../status`, `POST .../roles/grant`, `POST .../roles/revoke`. Gövde `actorId` (işlemi yapan yönetici), `reason` (1 ile 500 karakter)
- Kurallar kullanıcı uçlarıyla aynı ve gevşetilmez: kendini değiştirme yasağı, durum geçişleri (`BANNED` hesap yeniden `ACTIVE` olabilir), ADMIN ve MODERATOR için TOTP şartı, denetim kaydı
- Alternatif: `/api/v1/auth/admin/**` kullanıcı token'ıyla (ADMIN) Gateway üzerinden

## SENİN YAPACAKLARIN

- [ ] Token al: `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` `{ "clientId": "admin-service", "clientSecret": "..." }` → 60 sn'lik token (`aud=auth-service`, kapsamlar `account:read`, `account:status:write`, `account:role:write`). Her çağrıdan önce süresi dolmuşsa yenile
- [ ] `actorId` olarak **kendi** doğruladığın yönetici hesabını gönder. Auth, o hesabın `ACTIVE` ve `ADMIN` olduğunu yeniden denetler, değilse `403 ACTING_ADMIN_INVALID`
- [ ] Community'ye moderasyon kararı: `audience: "community-service"` ile token al (`events:ingest`, `sub=svc:admin-service`), `POST {COMMUNITY_SERVICE_URL}/internal/events` ile `ModerationActionTaken` gönder
- [ ] Community'den rapor olayı al (`ContentReported`): `POST /internal/events` ucunu aç, README bölüm 4'e göre doğrula (`aud=admin-service`, `scope=events:ingest`, `sub=svc:community-service`)
- [ ] `DELETE /internal/v1/accounts/{accountId}` ucu (moderasyon kayıtları, raporlar). Yazılmadıkça Admin görevi `PENDING`
- [ ] Admin → Review (REV-07, REV-15) gibi yeni çağrılar için kendi klasörüne `talep-NN` yaz, çift Auth'ta tanımlı değil

## AUTH'TA BEKLEYEN

- Yok. Admin → başka servis çiftleri ihtiyaç yazılınca eklenir

## İLGİLİ BELGELER

`docs-auth/servis-sozlesmeleri.md` bölüm 3.4 ve 3.5, `docs/api.md` (`## AUTH`, `/internal/v1/accounts/**`), `docs-auth/verilen-cevaplar/admin-service/`
