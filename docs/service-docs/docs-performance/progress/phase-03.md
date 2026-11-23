# Faz 03 ilerleme raporu — LTZ-ALP-PERF-03

Tarih: 2026-10-31 · Durum: **KOD/TEST/DOKÜMAN TAMAM — gerçek cihaz/Auth E2E bekliyor.** Faz 4'e geçilecek.

## Kapsam

| Görev | Durum | Not |
| --- | --- | --- |
| PERF-05 CPU sıcaklık | **PARTIAL** | `CPU_TEMP_C`; 0 < v ≤ 150 |
| PERF-06 GPU sıcaklık | **PARTIAL** | `GPU_TEMP_C`; 0 < v ≤ 150 |
| PERF-07 CPU kullanım | **PARTIAL** | `CPU_USAGE_PERCENT`; 0..100 |
| PERF-08 GPU kullanım | **PARTIAL** | `GPU_USAGE_PERCENT`; 0..100 |
| PERF-09 RAM / VRAM | **PARTIAL** | `RAM_USED_MB`, `VRAM_USED_MB` |
| PERF-10 Disk | **PARTIAL** | Yalnız `DISK_READ_MBPS` / `DISK_WRITE_MBPS` (I-18) |
| PERF-11 Fan RPM | **PARTIAL** | `FAN_RPM`; hangi fan belirsiz (I-17) |
| PERF-12 FPS | **PARTIAL** | `FPS`; 0 < v ≤ 1000 |

"PARTIAL" nedeni: servis içi kod, veri modeli ve testler tamamdır; ancak gerçek Auth cihaz token'ı (REQ-01-A) ve gerçek masaüstü istemci sözleşmesi olmadığı için gerçek uçtan uca doğrulama yapılamadı ve metrik aralıkları geçicidir. Checklist'te `[ ]` bırakıldı.

## Teslim edilenler

- **Migration:** `V23__create_performance_samples.sql` (`performance_samples`, `performance_sessions.sample_count`, `sync_batches.session_id/stored_count/duplicate_count`, CHECK'ler, `erase_performance_account` genişletmesi). Uygulanmış V21/V22'ye dokunulmadı.
- **API:** `POST /api/v1/performance/sync/batches` zarf v1 + örnek v2 (geriye uyumlu); yeni `DELETE /api/v1/performance/sessions/{id}/samples`; oturum yanıtına `sampleCount`. Kayıt: `docs/api.md` → `## PERFORMANCE`.
- **Katmanlar:** `SampleMetric`, `TelemetrySample`, `SampleBatchRules`, `SampleItemRequest`, `StrictNumberDeserializer`, `SampleIngestion`, `SyncBatchService` (genişletildi), `PerformanceSampleService`, `PerformanceSampleRepository`, `SyncBatchRepository` (genişletildi), `PerformanceRequestBodyLimitFilter` (sync yolunda 64 KiB), güvenlik yapılandırması (`/sessions/*/samples`).
- **Güvenlik:** hep-ya-hiç, katı sayı ayrıştırma, aralık/zaman/kota doğrulaması (uygulama + DB CHECK), IDOR (aynı 404), cihaz token'ı yalnız sync'te, kullanıcı token'ı yalnız silmede, hesap advisory kilidi altında kota.
- **Belgeler:** `docs/api.md`, `docs/database.md` §3.15, `docs/er-diagram.md` §16, [phase-03-decisions.md](../decisions/phase-03-decisions.md), [phase-03-tests.md](../testings/phase-03-tests.md), [auth-integration-03.md](../integration/auth/auth-integration-03.md), [auth-requests-03.md](../integration/auth-service-talepleri/auth-requests-03.md), [issues.md](../reports/issues.md) (I-16..I-23).

## Kalite kapısı (özet; ayrıntı phase-03-tests.md)

| Kontrol | Sonuç |
| --- | --- |
| Testler (111) | PASS |
| JaCoCo toplam / değişen kod | PASS — %96,55 / %96,72 |
| Checkstyle/PMD ratchet | PASS — yeni ihlal 0 |
| Trivy | PASS — 0 bulgu |
| Standart pre-push | PASS — BACKEND TEST CHECK PASSED |
| SonarQube | performance-service bulgusu 0 (PASS); genel kapı FAIL — yalnız diğer servislerin bulguları (I-14/I-15), dokunulmadı |

## Bilinen eksikler / riskler

- Gerçek Auth cihaz token'ıyla ve gerçek istemciyle E2E yok (I-16, I-21).
- Geçici metrik aralıkları ve kotalar (I-16, I-19); saklama/bölümleme yok (I-20).
- Faz 1'in Auth bağımlı işleri (REQ-01-A/C/D) açık; Faz 1 kapatılmadı.
