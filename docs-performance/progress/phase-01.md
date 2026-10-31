# Faz 01 ilerleme raporu — LTZ-ALP-PERF-01

Tarih: 2026-10-31 · Durum: **IN_PROGRESS — kod ve testler tamam, gerçek Auth entegrasyonu BLOCKED.** Faz 1 kapatılmadı; Faz 2'ye geçilmedi.

## Alt task durumu

| Task | Durum | Not |
| --- | --- | --- |
| PERF-01 Cihaz bağlantısı | PARTIAL / BLOCKED | `POST /devices` Auth cihaz listesiyle sahiplik doğrular; gerçek Auth ile E2E yok; Hardware aktif cihaz ucu hazır ama REQ-01-C bekliyor |
| PERF-02 Cihaz yönetimi | PARTIAL / BLOCKED | Liste/kaldır hazır (IDOR→404); Auth iptal yayılımı yok (REQ-01-B, kabul edilen risk) |
| PERF-17 Veri senkronizasyonu | PARTIAL / BLOCKED | Zarf sözleşmesi v1, izin + cihaz + token kontrolleri hazır; gerçek cihaz token'ı yok (REQ-01-A) |
| PERF-18 Tekrar gönderim koruması | Performance tarafı TAMAM | UQ + hash + eşzamanlı test PASS; gerçek cihaz token'ı gerektiren akış BLOCKED |
| PERF-22 Veri erişim ve gizlilik | Performance tarafı TAMAM | Varsayılan reddet, token türü ayrımı, D4 silme + çit; DEV silme işçisi doğrulaması NOT VERIFIED |

## Üretilen / değiştirilen

- Migration: `V21__create_performance_device_sync.sql` (5 tablo + `erase_performance_account`).
- Kod: domain (3 entity), 3 ayrı JWT doğrulama zinciri, Auth cihaz dizini istemcisi, 4 servis, 4 controller, `ProblemDetail` hata işleyici, hız sınırı/gövde sınırı/hesap kilidi, zamanlanmış temizlik.
- Test: 31 (bkz. [phase-01-tests.md](../testings/phase-01-tests.md)).
- Docs: `docs/api.md` (PERFORMANCE, 9 kayıt), `docs/database.md` §3.15 + saklama, `docs/er-diagram.md` §16, `docs/architecture.md`, `docs-performance/*`.
- `pom.xml`: yalnız test kapsamı bağımlılıkları (`spring-boot-starter-webmvc-test`, `testcontainers-postgresql`); `application.properties`: mevcut ENV adlarıyla yapılandırma; `.gitkeep` dosyaları kaldırıldı; `docs/folder-structure.md` güncellendi.
- Dokunulmadı: Auth, Hardware ve diğer servisler; `.env*` dosyaları; `coverage-policy.json`; `quality-baseline.json`.

## Açık işler (Faz 1'i kapatmak için)

1. Kerem: REQ-01-A (zorunlu), REQ-01-C, REQ-01-B kararı.
2. Alper: standart `pre-push` PASS. SonarQube analizi çalıştı, performance-service bulguları düzeltildi; repo genelindeki kapı başka servislerin bulguları yüzünden kırmızı olabilir (bkz. [phase-01-tests.md](../testings/phase-01-tests.md) §1).
3. Auth hazır olunca [auth-integration-01.md](../integration/auth/auth-integration-01.md) §4 kabul adımlarının gerçek ortamda koşulması.
