# TALEP 01 — MESSAGING → NOTIFICATION OLAY TESLİMİ İÇİN SERVİS TOKEN ÇİFTİ

**Kimden:** Umut (messaging-service) · **Kime:** Kerem (auth-service) · **Tarih:** 2026-11-13
**Durum:** AÇIK — Auth'ta yapılandırma; notification alıcı ucu ayrıca bekleniyor

---

## NE İSTİYORUM

Auth'ta şu çiftin tanımlanması:

```properties
ltz.auth.service-clients.clients.messaging-service.targets.notification-service=events:ingest
```

Mevcut `messaging-service` istemcisi ve gizli anahtarı (`AUTH_MESSAGING_SERVICE_CLIENT_SECRET`) yeterli; yeni ENV yok. Mevcut `social-service: blocks:check` hedefi aynen kalır.

## NEDEN

MSG-16: her yeni mesaj `MessageSent` v1 olayını outbox'a yazar (yük içerik taşımaz: `messageId`, `conversationId`, `conversationType`, `seq`, `senderId`, `recipientIds`). Messaging'de relay hazır (LTZ-UMT-MSG-08): `POST {NOTIFICATION_SERVICE_URL}/internal/events`, token isteği `{clientId: "messaging-service", clientSecret, audience: "notification-service"}`, beklenen token `aud=notification-service`, `scope=events:ingest`, `sub=svc:messaging-service`. Bu, Social → User/Community olay tesliminde kullanılan desenin aynısıdır.

## NE DEĞİŞMEZ

- Hesaba bağlı token gerekmez (Auth dışı üretici; `accountId` gönderilmez).
- Çift tanımlanana kadar Messaging relay'i hiçbir olay denemez (`ltz.messaging.outbox.adapter=unavailable`); mesajlaşma etkilenmez.

## SONRAKİ ADIM

Çift ve notification-service alıcı ucu hazır olunca Messaging ortamında `LTZ_MESSAGING_OUTBOX_ADAPTER=notification` verilip canlı doğrulanır (`docs-messaging/TASK-08.md` §6).

## İLGİLİ

`docs/api.md` → MESSAGING → "Giden olay sözleşmeleri" · `docs/architecture.md` §6.2 · `docs-auth/entegrasyon/messaging-service.md`
