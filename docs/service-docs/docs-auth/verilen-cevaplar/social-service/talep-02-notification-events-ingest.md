# TALEP 02 — SOCIAL → NOTIFICATION OLAY TESLİMİ İÇİN SERVİS TOKEN ÇİFTİ

**Kimden:** Umut (social-service) · **Kime:** Kerem (auth-service) · **Tarih:** 2026-11-20
**Durum:** AÇIK — Auth'ta yapılandırma; notification alıcı ucu ayrıca bekleniyor

---

## NE İSTİYORUM

Auth'ta şu çiftin tanımlanması:

```properties
ltz.auth.service-clients.clients.social-service.targets.notification-service=events:ingest
```

Mevcut `social-service` istemcisi ve gizli anahtarı (`AUTH_SOCIAL_SERVICE_CLIENT_SECRET`) yeterli; yeni ENV yok. Mevcut `community-service`, `user-service` (`privacy:read`, `events:ingest`) ve gelen `blocks:check` çağıranları aynen kalır.

## NEDEN

SOC-13: Social, `FriendRequestSent` v1 ve `FriendRequestAccepted` v1 olaylarını iş verisiyle aynı transaction'da outbox'a yazıyor (yük: `requestId`, `senderId`, `receiverId`; kişisel veri yok). Teslim için Social relay'i Auth'tan `{clientId: "social-service", clientSecret, audience: "notification-service"}` ile token alıp `POST {NOTIFICATION_SERVICE_URL}/internal/events` çağıracak; beklenen token `aud=notification-service`, `scope=events:ingest`, `sub=svc:social-service`. Community ve User'a teslimde kullanılan desenin aynısıdır.

`docs-auth/entegrasyon/social-service.md` ve `notification-service.md` bu çiftin "ihtiyaç yazılırsa karar" olduğunu söylüyor; ihtiyaç budur.

## NE DEĞİŞMEZ

- Hesaba bağlı token gerekmez (Auth dışı üretici; `accountId` gönderilmez).
- Çift tanımlanana kadar Social bu iki türü teslim etmez; `FriendRequest*` olayları outbox'ta bekler. Arkadaşlık ve engel akışları etkilenmez.

## İSTEĞE BAĞLI (karar bekliyor)

Notification, engel bastırma için `UserBlocked/UserUnblocked` tüketecekse aynı çift yeterlidir (ek kapsam yok). Bu karar [Notification talebi](../../../docs-social/notification-service/talep/req-01.md) §3.3'te sorulur; Auth'ta ek iş çıkmaz.

## SONRAKİ ADIM

Çift ve notification-service alıcı ucu hazır olunca Social'da `FanOutEventPublisher`'a notification hedefi eklenir, `SOCIAL_OUTBOX_RELAY_ENABLED=true` ile canlı doğrulanır.

## İLGİLİ

`docs/api.md` → SOCIAL → "Giden olay sözleşmeleri" · `docs/architecture.md` §6.2–6.3 · `docs-social/TASK-04.md` §4 madde 3 · [talep-01 (Messaging, aynı çift)](../messaging-service/talep-01-notification-events-ingest.md)
