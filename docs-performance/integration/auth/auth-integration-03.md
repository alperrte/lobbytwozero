# Auth entegrasyonu 03 — Ölçüm örnekleri

Tarih: 2026-10-31 · Kapsam: PERF-05..PERF-12 · Faz 1 bağımlılıkları ([auth-integration-01.md](auth-integration-01.md)) geçerlidir.

## 1. Durum özeti

| Konu | Durum |
| --- | --- |
| Örnek yazımı (`POST /sync/batches`, v2) cihaz token'ıyla çalışır | Kod + test hazır |
| Cihaz token'ı doğrulaması (typ/iss/aud/scope/ömür/jti) | **Yerel imzalı sözleşme token'ı ile test edildi**; gerçek Auth cihaz token'ı ile E2E **BLOCKED** (REQ-01-A: Auth bugün yalnız `aud=hardware-service` üretir) |
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
- Cihaz iptali sonrası yazma penceresi: ≤ 60 sn token ömrü; `device_links` kaldırılınca bağ anında reddedilir (testli). REQ-01-B bildirimi gelmedi (kabul edilen risk).
