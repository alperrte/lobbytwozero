# TASK 3 — karşılaştırılabilir FPS kanıtı sözleşme boşluğu

2026-11-15 · Sahip: Alper · Tüketici: Recommendation · **REC-09 KAPANDI (2026-11-18).**

## Gerçek kaynak durumu

`GET /internal/v1/accounts/{accountId}/games/{gameId}/performance-reference?hardwareProfileId=...`
mevcuttur. Auth'ta `svc:recommendation-service`, audience=performance-service, scope=reference:read,
account_id bağı zaten izinlidir; yeni grant/ENV gerekmez. Task 3 gerçek tüketiciyi ekler.

`PerformanceHistoryService.gameReference` tamamlanmış oturumlardaki FPS örneklerini aggregate
eder; reliable yalnız fpsSampleCount≥60 şartıdır. Yanıtta gameId/profileId, session/sample count,
avg/min/max ve lastMeasuredAt bulunur. **Çözünürlük, grafik preset, ölçüm provenance'ı ve ölçüm
anındaki Hardware profileVersion yoktur.** Aynı profil ID'si zaman içinde başka bileşenlere
sahip olabilir. Min/max outlier'ları gösterir; bunlar tahmin aralığı veya confidence değildir.

PerformanceReport çözünürlük/preset taşısa da manuel rapordur; bu raporların değerlerini tüm
ham FPS örneklerine veya geçmiş donanım sürümüne mal etmek doğru değildir. Production'da
karşılaştırılabilir/güvenilir FPS kanıtı varmış gibi DTO doldurulmadı.

## İstenen sözleşme — uygulanmış API değildir

Mevcut read izni korunarak, yalnız kaynağı gerçekten kanıtlanabilen ölçümlerden şu sınırlı
kanıt sağlanmalı: canonical gameId, hardwareProfileId ve ölçümdeki profil sürümü/fingerprint,
çözünürlük, preset, provenance, completed session sayısı, örnek sayısı, ölçüm zamanı/yaşı,
uygulanan aykırı değer yöntemi, anlamı açık dağılım özeti ve yeterlilik durumu.
MANUAL beyan, cihazdan doğrulanmış ölçüm ve tahmin birbirine dönüştürülmemeli; unsupported
bağlamlar EMPTY/INSUFFICIENT_DATA olmalı. Oyun/donanım/ayar bağlamı eşleşmeden olumlu tahmin yok.

Gerekli bağlam mevcut ingestion/şemada yoksa yalnız DTO'ya alan eklemek çözüm değildir.
Ölçüm kabulü, kaynak doğrulaması ve migration tasarımı ayrıca değerlendirilmelidir. Bu nedenle
TASK 3'te geniş Performance feature/ingestion değişikliği veya yeni altyapı yapılmadı;
Hardware/Performance producer kodu ve uygulanmış migration'lar değiştirilmedi.

Kabul testleri: account/subject/scope/profile binding, 0/az/outlier örnek, farklı resolution/preset,
değişmiş profileVersion, MANUAL beyan ayrımı, bilinmeyen provenance, eski ölçüm, duplicate,
bounded query/body, timeout/503, yöntem ve gerçek sonuç/tahmin etiketlerinin ayrımı.

Task 3 mevcut tüketici yalnız örnek/oturum sayısı ve producer eşiğini tanı amaçlı bildirir;
**FPS alt/üst tahmini null, confidence=NONE, status=INSUFFICIENT_DATA** kalır.

## Kapanış (2026-11-18) — istek karşılandı

Yukarıdaki boşluk kapatıldı; ayrıntı ve test kanıtı:
[performance-evidence-followup.md](../../decisions/performance-evidence-followup.md).

| İstenen | Karşılık |
| --- | --- |
| Çözünürlük/preset/provenance/profil sürümü | V24: değiştirilemez `measurementContext` (profil sürümü HW-17 ile doğrulanır), sync v3 |
| Kaynak ayrımı (MANUAL ≠ cihaz ≠ tahmin) | Yalnız bağlamlı, cihazdan gelen FPS örnekleri; manuel rapor ve eski örnekler dışarıda; `source=AUTHENTICATED_DEVICE`, `contextVerification=OWNER_SNAPSHOT_DEVICE_DECLARATION` |
| Yöntem/sayım/zaman/sınırlılık | `SESSION_MEDIAN_P10_P90_IQR_1_5_V1`, sayımlar, first/lastMeasuredAt, `windowLimited`, 4 sabit limitation |
| Yetersizde olumlu tahmin yok | < 3 uygun oturum / veri yok → `INSUFFICIENT_DATA`, null, NONE |
| Auth/scope bağı | Mevcut `reference:read` + `account_id`; yeni grant/ENV yok |

Kabul testleri: Performance 174/174, Recommendation 85/85 PASS (hedefli). Açık kalan: canlı
çok servisli E2E ve native pre-push/Sonar çalıştırılmadı. Bu istek için açık Performance işi kalmadı.
