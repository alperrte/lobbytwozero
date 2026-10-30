# User Service — diğer servislerden beklenenler

Güncelleme: 2026-10-30. Sorumlu: Kerem. User tarafı hazır ve testli (223 test); gerçek Auth ile Docker'da doğrulandı.
User **kimseyi çağırmaz**; dış veri yalnız olayla gelir, karar ucu yalnız çağrılır.

## Kim, ne, hangi dosya

| Kime | Ne istiyoruz | Öncelik | Durum | Dosya |
| --- | --- | --- | --- | --- |
| Auth (Kerem) | Steam bağlama/kaldırma ucu + `ExternalIdentityLinked/Unlinked` olayları | Orta | OPEN | [auth-requests-05](auth/auth-service-talepleri/auth-requests-05.md) |
| Auth (Kerem) | REQ-01..04: olay hedefi, eski hesaplar, silme durumu, token çiftleri | Yüksek | **Auth hazır** (REQ-02 karar B) | [01](auth/auth-service-talepleri/auth-requests-01.md) · [02](auth/auth-service-talepleri/auth-requests-02.md) · [03](auth/auth-service-talepleri/auth-requests-03.md) · [04](auth/auth-service-talepleri/auth-requests-04.md) |
| Umut (social) | Arkadaşlık/engel olaylarını `user`'a göndermek, başlangıç aktarımı, `FRIENDS_LIST` karar adaptörü | Yüksek | OPEN | [social-requests-01](services/social-service-talepleri/social-requests-01.md) |
| Hamza (library + review) | `LibrarySummaryChanged` / `ReviewSummaryChanged` olayları, LIB-11 için `LIBRARY` karar adaptörü | Orta | OPEN | [library-review-requests-01](services/library-review-talepleri/library-review-requests-01.md) |
| Alper (hardware) | `HARDWARE` / `PERFORMANCE` karar adaptörü | Orta | OPEN | [hardware-requests-01](services/hardware-service-talepleri/hardware-requests-01.md) |
| Gateway / Frontend | Gateway'de `/api/v1/user/**` rotası zaten var; şimdilik istek yok | — | — | — |

Ortak sözleşme: [docs/api.md](../../docs/api.md) → `## USER`. Auth cevapları: `docs-auth/verilen-cevaplar/user-service/`.

## Ortak kurallar (herkes için)
- Token: `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` ile `audience=user-service`. Çiftler Auth'ta tanımlı:
  social/library `privacy:read`+`events:ingest`, hardware `privacy:read`, review `events:ingest`.
- Gizli anahtarlar (`AUTH_<SERVİS>_SERVICE_CLIENT_SECRET`) kendi ortamınıza verilir; `node ops/generate-auth-dev-keys.mjs` dev için üretir.
- Fail-closed: karar ucuna ulaşılamaz ya da `allowed=false` ise gizli veri **gösterilmez**.
- `ltz.user.social.projection-enabled` Social başlangıç aktarımını bitirene kadar `false` kalır.

## Gönderilecek mesajlar (kopyala-yapıştır)

**Umut (social):**
```text
Umut, user-service hazır (testli, Docker'da gerçek Auth ile denendi). Senden 3 şey lazım, detay:
docs-user/integration/services/social-service-talepleri/social-requests-01.md
1) FriendshipChanged / UserBlocked / UserUnblocked olaylarını `user` hedefine de gönder (POST /internal/events, scope events:ingest;
   biçim değişmiyor). 2) Mevcut arkadaşlık ve engeller için tek seferlik yeniden yayın yap, bitince bana haber ver (ben projection'ı
   açacağım). 3) FriendsVisibilityPort için POST /internal/v1/privacy/decisions (field=FRIENDS_LIST), yanıttaki `setting` yeterli.
Auth çiftin (privacy:read + events:ingest) hazır, ek secret yok. Sözleşme: docs/api.md -> USER.
```

**Hamza (library + review):**
```text
Hamza, user-service hazır. Senden: docs-user/integration/services/library-review-talepleri/library-review-requests-01.md
1) library -> LibrarySummaryChanged {userId, collectionCount, completedCount, favoriteCount}, review -> ReviewSummaryChanged
{userId, reviewCount} olaylarını `user`'a gönder (POST /internal/events, mutlak sayılar). 2) Mevcut kullanıcılar için bir kez yayınla.
3) LIB-11: POST /internal/v1/privacy/decisions (field=LIBRARY), allowed=false ya da ulaşılamazsa gösterme.
Auth çiftleri hazır ama AUTH_LIBRARY_SERVICE_CLIENT_SECRET ve AUTH_REVIEW_SERVICE_CLIENT_SECRET'i kendi ortamına vermen lazım
(node ops/generate-auth-dev-keys.mjs üretir).
```

**Alper (hardware):**
```text
Alper, user-service hazır. Senden: docs-user/integration/services/hardware-service-talepleri/hardware-requests-01.md
HardwareVisibilityPolicy adaptörü: POST /internal/v1/privacy/decisions {viewerId, field: HARDWARE|PERFORMANCE, ownerIds}; yanıttaki
allowed/friends'i Decision'a eşle. Auth çifti hazır (yalnız privacy:read); AUTH_HARDWARE_SERVICE_CLIENT_SECRET'i kendi ortamına ver.
Açık soru: HardwareReviewAudiencePolicy engel listesi istiyor, User dökmüyor; toplu karar ucuyla çözülür mü bakalım.
```

**Auth (Kerem / Steam):**
```text
Auth: REQ-01..04 için teşekkürler, User tarafında hepsi işlendi ve gerçek Auth ile uçtan uca denendi (kayıt -> UserRegistered ->
username dolu). Kalan tek istek Steam: docs-user/integration/auth/auth-service-talepleri/auth-requests-05.md (oturumlu hesaba
bağlama/kaldırma + ExternalIdentityLinked/Unlinked olayları). User alıcısı hazır.
```
