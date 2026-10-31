# Faz 04 ilerleme raporu — LTZ-ALP-PERF-04

Tarih: 2026-10-31 · Durum: **KOD/TEST/DOKÜMAN TAMAM — gerçek Auth servis kimliği ve gerçek Hardware/Recommendation tüketimi bekliyor.**

## Kapsam

| Görev | Durum | Not |
| --- | --- | --- |
| PERF-14 Ölçüm geçmişi | **PARTIAL** | Filtreli/sayfalı `GET /sessions` + `totalElements`; `GET /sessions/{id}/samples` |
| PERF-15 İstatistikler | **PARTIAL** | `GET /sessions/{id}/stats`, `GET /stats` (gerçek örneklerden avg/min/max/count) |
| PERF-16 Grafik API'si | **PARTIAL** | `GET /sessions/{id}/series` (ham ya da açıkça belirtilmiş kova ortalaması) |
| PERF-19 Filtreleme | **PARTIAL** | Oyun, donanım profili, cihaz, durum, tarih, metrik |
| PERF-21 Servis entegrasyonu | **PARTIAL / BLOCKED** | Hardware özeti ve Recommendation FPS referansı uçları var; gerçek Auth servis kimliği yok (REQ-04-A), tüketici kodu değiştirilmedi |

"PARTIAL" nedeni: servis içi kod ve testler tamamdır; ancak gerçek Auth servis token'ı, gerçek cihaz verisi ve gerçek tüketiciler olmadan uçtan uca doğrulama yapılamadı. Checklist'te `[ ]` bırakıldı.

## Teslim edilenler

- **Migration:** yok (mevcut indeksler yeterli).
- **API:** `GET /api/v1/performance/sessions` (filtre + `totalElements`), `/sessions/{id}/samples|stats|series`, `/stats`; iç uçlar `GET /internal/v1/accounts/{accountId}/performance-summary` ve `/games/{gameId}/performance-reference`. Kayıt: `docs/api.md` → `## PERFORMANCE`.
- **Katmanlar:** `PerformanceHistoryController`, `PerformanceHistoryService`, `PerformanceStatsRepository` (JdbcTemplate, bağlı parametre), `SessionSpecifications`, `TimeWindow`, `SessionFilter`, yanıt kayıtları (`MetricStatsResponse`, `SessionStatsResponse`, `AccountStatsResponse`, `SamplePageResponse`, `SeriesResponse`, `PerformanceSummaryResponse`, `PerformanceReferenceResponse`, …), `PerformanceInternalController` (iki yeni uç), `PerformanceSecurityConfiguration` (kullanıcı rotaları + `summary:read`/`reference:read`).
- **Güvenlik:** tüm sorgular `user_id` ile sınırlı (IDOR → aynı 404), kullanıcı uçlarında yalnız kullanıcı token'ı, iç uçlarda konu + kapsam + `account_id` bağı, iç kimlik/oyun adı sızıntısı yok, hesaplar arası toplama yok.
- **Belgeler:** `docs/api.md`, [phase-04-decisions.md](../decisions/phase-04-decisions.md), [phase-04-tests.md](../testings/phase-04-tests.md), [auth-requests-04.md](../integration/auth-service-talepleri/auth-requests-04.md), [auth-integration-04.md](../integration/auth/auth-integration-04.md), [issues.md](../reports/issues.md) (I-24..I-30), [final-report.md](../reports/final-report.md). `database.md` / `er-diagram.md` şema değişmediği için güncellenmedi.

## Kalite kapısı (özet; ayrıntı phase-04-tests.md)

| Kontrol | Sonuç |
| --- | --- |
| Testler (134) | PASS |
| JaCoCo toplam / değişen kod | PASS — %97,07 / %97,22 |
| Checkstyle/PMD ratchet | PASS — yeni ihlal 0 |
| Trivy | PASS — 0 bulgu |
| Standart pre-push | PASS — BACKEND TEST CHECK PASSED |
| SonarQube | Performance-service bulgusu **0** (S5961 düzeltildi: 26 assertion'lı test ikiye bölündü). Genel kapı FAIL — yalnız diğer servislerin bulgularıyla (social, hardware, game, user, review, library; I-14/I-15), bu servislere dokunulmadı |

## Bilinen eksikler / riskler

- Gerçek Auth servis token'ı yok (REQ-04-A, I-28); Hardware/Recommendation tüketimi doğrulanmadı.
- Güvenilirlik eşiği varsayımdır (I-24); hesaplar arası toplam yapılmadı (I-25).
- Okuma hız sınırı ve büyük hesap istatistik maliyeti (I-26, I-27).
- Faz 1'in Auth bağımlı işleri (REQ-01-A/C/D) açık; Faz 1 kapatılmadı.
