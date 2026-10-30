# USER-AUTH-REQ-01 — `user` olay hedefinin açılması

Tarih: 2026-10-30. Gönderen: Kerem / user-service. Hedef: Kerem / auth-service.
Öncelik: HIGH. Durum: OPEN — alıcı uç hazır, Auth ortam ayarı bekleniyor.

**Gerekçe:** Auth `UserRegistered`, `AccountStatusChanged` ve `AccountEmailChanged` olaylarını üretir ama
`ltz.auth.events.targets` boşken hiçbir olay yazılmaz (`AuthEventPublisher.publish` erken döner). User Service'in alıcı ucu
artık hazırdır: `POST /internal/events` (`docs/api.md` → USER).

**Alıcı sözleşmesi (Auth kodundaki gönderici ile karşılaştırıldı):** Zarf `eventId, eventType, eventVersion=1, occurredAt,
producer="auth-service", aggregateId, payload`. Token `typ=svc+jwt`, `sub=svc:auth-service`, `aud=user-service`,
`scope=events:ingest`, `account_id=aggregateId`. Payload'lar: `UserRegistered {accountId, username}`,
`AccountStatusChanged {accountId, status}`, `AccountEmailChanged {accountId}`. Tanımsız zarf alanı reddedilir.

**İstenen:**
1. Ortamlarda `AUTH_EVENT_TARGETS` değerine `user` eklenmesi (örn. `community,user`). `USER_SERVICE_URL` zaten Auth'un
   `deletion.service-urls` listesindedir; yeni adres ENV'i gerekmez. Yeni (çağıran, hedef) çifti gerekmez: Auth bu olayları kendi
   adıyla gönderir. **ENV değişikliği bu repoda yapılmadı; onay Auth sahibindedir.**
2. Yalnız DEV'de açılıp `UserRegistered` ile oluşan profilin `GET /api/v1/user/me` yanıtında `username` ile görüldüğü birlikte
   doğrulanmalı.

**Uyumluluk:** `AccountStatusChanged`'ın bilinmeyen durum adı User'da profili değiştirmez; `eventVersion` ≠ 1 ise `400` döner
(Auth bu durumda yeniden dener ve `stuck-attempts` uyarısı verir). Sürüm yükseltilirse önce User güncellenmelidir.

**Kabul:** DEV'de kayıt + e-posta doğrulama → User'da profil ve kullanıcı adı; yönetici durum değişikliği → profil durumu;
aynı olayın tekrarı `DUPLICATE`.

**Referans:** `HttpAuthEventSender`, `AuthEventRelay`, `docs-auth/servis-sozlesmeleri.md` §3.5.
