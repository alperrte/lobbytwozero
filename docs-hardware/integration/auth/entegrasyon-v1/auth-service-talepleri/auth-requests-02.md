# HW-AUTH-REQ-02 — Masaüstü cihaz kimliği ve dar senkronizasyon kapsamı

Tarih: 2026-10-24. Gönderen: Alper / hardware-service. Hedef: Kerem / auth-service;
aktif cihaz sahipliği eş sahibi: Alper / performance-service (PERF-01).
Öncelik: HIGH (desktop apply açılmadan önce). Durum: OPEN / DEFERRED_AUTH + DEFERRED_CONTRACT.

**Gerekçe:** POST /api/v1/hardware/profiles/{profileId}/sync/snapshots ayrı doğrulanmış cihaz ister.
Mevcut kullanıcı JWT'sindeki sid bir cihaz kimliği değildir. PLAYER/ADMIN ve scope string'i bu sınırı aşamaz.

**Mevcut/eksik:** Hardware typed AuthenticatedHardwareDevice, hardware:sync:write,
owner/profile/device/generation consent ve sequence/idempotency kontrolleri hazır.
Auth'ta yayımlanmış cihaz credential doğrulama/rotation/revocation ve PERF aktif sahiplik API'si yok.

**Beklenen sözleşme:** Ayrı credential tipi, issuer/audience/type, subject-owner-device bağları,
izin verilen scope, süre/yenileme/iptal ve anahtar dağıtımı Kerem tarafından tanımlanıp docs/api.md'ye yazılmalı.
PERF-01 deviceId-ownerId-active-revoked sürümlü kararı ve hata davranışı ayrıca mutabık olmalı.
Herhangi bir yeni endpoint adı veya claim bugün varmış gibi kabul edilmez.

**Güvenlik/RBAC:** Dar cihaz yetkisi, kullanıcı token'ıyla karıştırmama, sahiplik ve aktiflik,
credential replay/rotation sınırı; kullanıcı verisi/test anahtarı loglanmamalı. ADMIN cihaz impersonation yetkisi değildir.

**Başarı/hata/HTTP:** Auth/PERF endpoint'i henüz yok; HTTP kodu ve body uydurulmadı.
Hardware mevcut apply ucu kimliksiz 401, kullanıcı principal'ıyla 403; güvenilir aktif sahiplik yoksa fail-closed.
409 version/sequence/idempotency uyuşmazlığı; 429 kapasite/rate limiti korunur.

**Sahiplik/idempotency:** Auth credential sahibi; PERF aktif cihaz envanteri sahibi; Hardware yalnız
kendi profil consent ve 24 saatlik apply receipt'ini tutar. Aynı idempotency key aynı body ile tekrar aynı sonucu,
farklı body ile 409 verir; revoke/generation değişimi eski komutu açmaz.

**Uyumluluk:** Preview ve owner consent API'leri kullanıcı JWT'siyle kalır; grant aktif PERF kararı ister.
Üretim cihaz policy'si deny; apply güvenilir credential gelmeden açılmayacak.

**Kabul:** Gerçek desktop credential → Auth doğrulama → aktif PERF device → owner consent → apply E2E;
başka owner/cihaz, revoked, eksik scope, user JWT, duplicate/out-of-order ve silinmiş hesap negatif testleri.

**Referans:** HW05-desktop-sync-proposal.md, HardwareSyncDevicePolicy, HardwareSyncService,
auth-integration-03.md. Talep henüz dışarı gönderilmedi.
