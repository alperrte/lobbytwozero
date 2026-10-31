# Auth talepleri 02

Tarih: 2026-10-31 · Kapsam: Faz 2 (PERF-03/04/13/20)

## Yeni talep: YOK

Faz 2'nin manuel oturum ve rapor uçları yalnız mevcut kullanıcı token'ını (`at+jwt`, `aud=ltz-api`, JWKS) kullanır. Auth tarafında yeni uç, iddia, scope veya olay gerekmedi. Auth Service kaynak koduna dokunulmadı.

## Faz 1'den süren açık talepler (değişmedi)

| Talep | Durum | Faz 2 etkisi |
| --- | --- | --- |
| REQ-01-A — cihaz token'ı `aud=performance-service` / `scope=performance:sync:write` | BLOCKED | Cihaz kaynaklı oturum (`DEVICE`) ve cihazdan gelen ölçüm Faz 3'te bu talebe bağlıdır; Faz 2 etkilenmez |
| REQ-01-B — cihaz iptalinin servislere bildirimi | REQUESTED / kabul edilen risk | Yok |
| REQ-01-C — Hardware→Performance servis token'ı | BLOCKED | Yok (Hardware doğrulaması Faz 2'de Hardware'e yönelir, tersi değil) |
| REQ-01-D — Auth silme işçisi DEV doğrulaması | NOT VERIFIED | `erase_performance_account` V22'de oturum/rapor temizliğiyle genişledi; ortak ortam doğrulaması hâlâ yok |

Ayrıntı: [auth-requests-01.md](auth-requests-01.md).

## Diğer servislere (Auth dışı) not

- **Hardware (sahibi: Alper):** HW-17 `POST /api/v1/hardware/profiles/batch` tüketici tarafı gerçek Hardware ile doğrulanmadı. Hardware kodu değiştirilmedi.
- **Game (sahibi: Hamza):** GAME-15 `POST /api/v1/game/games/batch` anonim kullanılır; gerçek Game ile E2E yapılmadı. Game kodu değiştirilmedi.
