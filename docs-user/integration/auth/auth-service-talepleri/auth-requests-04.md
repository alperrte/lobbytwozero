# USER-AUTH-REQ-04 — `privacy:read` kapsamı ve user-service'e giden servis token çiftleri (T3)

Tarih: 2026-10-30. Gönderen: Kerem / user-service. Hedef: Kerem / auth-service.
Öncelik: HIGH. Durum: AUTH HAZIR (2026-10-30) — çiftler: social/library `privacy:read`+`events:ingest`, hardware `privacy:read`, review `events:ingest` (aud=user-service). `access:decide` bırakıldı. Library/hardware/review gizli anahtarlarını ilgili servis sahipleri kendi ortamına vermeli; uçtan uca NOT VERIFIED.

**Gerekçe:** Diğer servisler User'a iki yoldan ulaşır ve ikisi de Auth'un `POST /internal/v1/service-tokens` ucundan alınan,
hedefe bağlı (`aud=user-service`) servis token'ı ister. Bugün `ltz.auth.service-clients.clients.*.targets` listesinde
`user-service` hedefi hiç yoktur (`auth-service/.../application.properties:88-100`).

**1) Yeni kapsam `privacy:read`** — `POST /internal/v1/privacy/decisions` (`docs/api.md` → USER — Gizlilik kararı).

| Çağıran (`sub`) | Hedef (`aud`) | Kapsam | Neden |
| --- | --- | --- | --- |
| `svc:social-service` | `user-service` | `privacy:read` | SOC-12: başkasının arkadaş listesi görünürlüğü |
| `svc:hardware-service` | `user-service` | `privacy:read` | HW-08: `HardwareVisibilityPolicy` |
| `svc:library-service` | `user-service` | `privacy:read` | LIB-11: koleksiyon gizliliği |

**2) Olay girişi `events:ingest`** — `POST /internal/events` (`docs/api.md` → USER — Servisler arası olay girişi).

| Çağıran (`sub`) | Hedef (`aud`) | Kapsam | Olaylar |
| --- | --- | --- | --- |
| `svc:social-service` | `user-service` | `events:ingest` | `FriendshipChanged`, `UserBlocked`, `UserUnblocked` |
| `svc:library-service` | `user-service` | `events:ingest` | `LibrarySummaryChanged` (önerilen) |
| `svc:review-service` | `user-service` | `events:ingest` | `ReviewSummaryChanged` (önerilen) |

**User tarafının denetimi (bilgi):** `typ=svc+jwt`, `aud=user-service`, kapsam; karar ucunda çağıran izinli listede olmalı,
olay ucunda `sub = svc:<producer>` ve olay türü ↔ üretici eşleşmeli. `account_id` yalnız Auth'un kendi olaylarında aranır.

**Bağımlılık:** library-service ve review-service için Auth'ta istemci kaydı ve gizli anahtar (`AUTH_LIBRARY_SERVICE_CLIENT_SECRET`,
`AUTH_REVIEW_SERVICE_CLIENT_SECRET`) gerekebilir; bu ENV değişikliği Auth sahibinin ve ilgili servis sahibinin onayıyla yapılır.
**Bu repoda ENV değiştirilmedi.**

**Kabul:** Çiftler `application.properties` + Auth testleri + `docs-auth/servis-sozlesmeleri.md` §3.5 tablosuna eklendi; DEV'de bir
servis token'ıyla karar ucu `200`, kapsamsız token `403`.
