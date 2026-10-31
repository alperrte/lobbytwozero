# Auth talepleri 03

Tarih: 2026-10-31 · Kapsam: Faz 3 (PERF-05..PERF-12)

## Yeni talep: YOK

Faz 3, Faz 1'de tanımlı cihaz senkronizasyon ucunu (`POST /sync/batches`) genişletir ve yalnız silme için mevcut kullanıcı token'ını (`at+jwt`) kullanır. Auth tarafında yeni uç, iddia, scope veya olay gerekmedi. Auth Service kaynak koduna dokunulmadı.

## Faz 1'den süren açık talepler — Faz 3 etkisi

| Talep | Durum | Faz 3 etkisi |
| --- | --- | --- |
| REQ-01-A — cihaz token'ı `aud=performance-service` / `scope=performance:sync:write` | BLOCKED | **Doğrudan etkili:** gerçek cihazdan ölçüm yazımı (PERF-05..12) bu talep karşılanana kadar gerçek Auth ile uçtan uca çalıştırılamaz. Testler yerel imzalı sözleşme token'ı kullanır; gerçek E2E değildir |
| REQ-01-B — cihaz iptalinin servislere bildirimi | REQUESTED / kabul edilen risk | İptal sonrası ≤ 60 sn token ömrü + `device_links` kontrolü ile sınırlı; Faz 3'te değişmedi |
| REQ-01-C — Hardware→Performance servis token'ı | BLOCKED | Yok |
| REQ-01-D — Auth silme işçisi DEV doğrulaması | NOT VERIFIED | `erase_performance_account` V23'te örnek temizliğiyle genişledi; ortak ortamda doğrulanmadı |

Ayrıntı: [auth-requests-01.md](auth-requests-01.md), [auth-requests-02.md](auth-requests-02.md).

## Not

Tüm fazlar bittikten sonra bu talepler (auth-requests-01..04) Auth sahibine iletilecek; yanıtlar gelince düzeltmeler yapılacaktır (kullanıcı kararı).
