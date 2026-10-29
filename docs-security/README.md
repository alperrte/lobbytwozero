# docs-security: servis başına güvenlik ve kalite durumu

> **Bu dosya otomatik üretilir** (kaynak: `backend/config/quality-baseline.json`, 2026-10-29). Yenilemek için: `node pre-push/ci/lint-ratchet.mjs docs`.

Her servisin klasöründe iki dosya var:

- `README.md`: otomatik üretilir. O servisteki eski borç (Checkstyle/PMD), dosya dosya liste, nasıl düzeltileceği ve doğrulama komutları.
- `NOTLAR.md`: **elle** tutulur. Bilinen test/ortam sorunları ve servise özgü güvenlik notları.

**Bir servise dokunmadan önce (insan veya yapay zekâ ajanı) kendi servisinin klasörünü oku.**

| Servis | Sahip | Eski borç | Klasör |
|---|---|---:|---|
| auth-service | Kerem | ✅ 0 | [auth-service](auth-service/README.md) |
| user-service | Kerem | ✅ 0 | [user-service](user-service/README.md) |
| mail-service | Kerem | ✅ 0 | [mail-service](mail-service/README.md) |
| game-service | Hamza | ✅ 0 | [game-service](game-service/README.md) |
| library-service | Hamza | ✅ 0 | [library-service](library-service/README.md) |
| review-service | Hamza | ✅ 0 | [review-service](review-service/README.md) |
| social-service | Umut | 137 | [social-service](social-service/README.md) |
| messaging-service | Umut | ✅ 0 | [messaging-service](messaging-service/README.md) |
| lfp-service | Umut | ✅ 0 | [lfp-service](lfp-service/README.md) |
| community-service | Nisa | 6 | [community-service](community-service/README.md) |
| content-service | Nisa | ✅ 0 | [content-service](content-service/README.md) |
| event-service | Nisa | ✅ 0 | [event-service](event-service/README.md) |
| hardware-service | Alper | 213 | [hardware-service](hardware-service/README.md) |
| recommendation-service | Alper | ✅ 0 | [recommendation-service](recommendation-service/README.md) |
| performance-service | Alper | ✅ 0 | [performance-service](performance-service/README.md) |
| admin-service | Ortak (sahip atanmadı) | ✅ 0 | [admin-service](admin-service/README.md) |
| notification-service | Ortak (sahip atanmadı) | ✅ 0 | [notification-service](notification-service/README.md) |
| api-gateway | Ortak (sahip atanmadı) | ✅ 0 | [api-gateway](api-gateway/README.md) |

**Toplam donmuş borç: 356.** Borç yalnızca azalabilir; yeni ihlal PR'ı kırmızı yapar. Ayrıntı: [docs/engineering.md](../docs/engineering.md).
