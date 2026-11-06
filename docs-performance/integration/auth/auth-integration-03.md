# Auth entegrasyonu 03 — Ölçüm örnekleri

Güncelleme: 2026-11-06 · Kapsam: PERF-05..PERF-12 · Güncel Auth durumu: [auth-integration-01.md](auth-integration-01.md).

## 1. Durum özeti

| Konu | Durum |
| --- | --- |
| Örnek yazımı (`POST /sync/batches`, v2) cihaz token'ıyla çalışır | Kod + test hazır |
| Cihaz token'ı doğrulaması (typ/iss/aud/scope/ömür/jti) | Olumlu testler artık Auth'un gerçek `DeviceTokenIssuer` çıktısını kullanır (`target=performance` sözleşmesi). Bozuk claim testleri yerel imzalıdır. Kaynak sözleşme testi çalışan Auth E2E'sinden ayrıdır |
| Örnek silme (`DELETE /sessions/{id}/samples`) kullanıcı token'ıyla | Kod + test hazır; Auth kaynak kodundan derlenen JWKS ile (kaynak sözleşme testi), çalışan Auth ile E2E **NOT RUN** |
| Yeni Auth talebi | **Yok** ([auth-requests-03.md](../auth-service-talepleri/auth-requests-03.md)) |

Auth Service kaynak koduna dokunulmadı. Sahte JWT, `permitAll` veya varsayılan izin yoktur; `anyRequest().denyAll()` geçerlidir.

## 2. Token ve sahiplik karşılaştırması

| Uç | Token türü | Sahiplik kuralı | Başkasının kaynağı / yanlış token |
| --- | --- | --- | --- |
| POST /sync/batches (v2) | Cihaz (`dev+jwt`, `aud=performance-service`) | `account_id` + aktif `device_links` + açık izin; `sessionId` çağıran hesabın oturumu olmalı | Başkasının/olmayan oturum → aynı 404; kullanıcı ve servis token'ı → 401; Hardware cihaz token'ı (`aud=hardware-service`) → 401 |
| DELETE /sessions/{id}/samples | Kullanıcı (`at+jwt`) | `WHERE id = :id AND user_id = :sub` | 404 aynı yanıt; cihaz/servis token'ı veya anonim → 401 |

## 3. Kabul edilen sınırlamalar

- Gerçek masaüstü istemcisi ve istemci sözleşmesi yoktur; ölçüm aralıkları geçicidir.
- REQ-01-B kararı kesinleşti: Auth iptal bildirimi/introspection sağlamaz. Cihaz token'ı `exp` anında reddedilir; kullanıcı token'larının 30 sn toleransı cihazın yazma süresini uzatmaz (sabit saatli test). Bağ kaldırıldığında veya kullanıcı cihaz listesini Auth'tan yenilediğinde iptal tespit edilirse mevcut token da reddedilir.
- Gerçek HTTP kabul testi v1 zarfını kullanır; v2 sensör örneklerinin çalışan Game/Hardware ve gerçek masaüstü istemcisiyle E2E kabulü yapılmadı. Ayrıntı: [auth-integration-05.md](auth-integration-05.md).
