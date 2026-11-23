# REC-09 / PERF-12/13/21 — bağlamlı ölçüm ve FPS tahmini

2026-11-15 · Alper · Kullanıcı Performance değişikliklerini açıkça yetkilendirdi.
Durum: **TAMAMLANDI (2026-11-18) — hedefli testler PASS; native pre-push/Sonar çalıştırılmadı**.

Başlangıç worktree temiz, HEAD 2acf166f. Baseline: 148 Performance + 80 Recommendation test
PASS; iki modül lint/coverage/JAR dependency gate PASS. Log:
`logs/performance-evidence-baseline-2026-11-15T11-52-09-624Z-10776-a32eec45.log`.

## Uygulama kararı

V24 sıradaki boş Performance migration'ıdır (V21–V23 korunur). Oturum başında owner'ın
Hardware profil sürümü gerçek HW-17 ile doğrulanır. İsteğe bağlı immutable measurementContext:
hardwareProfileVersion, resolution, graphicsPreset, renderingMode=NATIVE_RASTER. Context'li
oturumda hardwareProfileId ve aktif owner deviceLink zorunludur. Context ID session ID'dir;
metadata/ID alanları bütünsel DB CHECK ve örnek FK'sıyla korunur.

Sync v1/v2 geriye uyumlu kalır; yalnız yeni v3 zarfı aynı context'i açıkça doğrulayan, aynı
owner cihaz bağlantısının örneklerini bağlamlı olarak işaretler. Hash context'i kapsar;
eski örnekler/manual raporlar backfill edilmez, duplicate mevcut satırı evidence'e yükseltmez.

Yeni account-bound reference:read endpoint'i performance-evidence; eski reference semantiği
korunur. Exact owner/game/profile/version/resolution/preset/renderingMode sorgusu yapılır.
Varsayım olarak yalnız son 30 gündeki en yeni 20 COMPLETED oturum, oturum başına son 600 FPS
örneği incelenir. Her oturum en az 60 inlier ve 60 saniye ölçüm yayılımı taşımalı; IQR 1.5
filtresi uygulanır. En az 3 oturum yeterlilik şartıdır. Eşikler mühendislik varsayımıdır;
kalibre edilmiş doğruluk veya istatistiksel güven yüzdesi iddia edilmez.

Performans kaynağı authenticated DEVICE'dır, donanım/ayar attestation'ı değildir. User-start
profile snapshot ve device confirmation provenance'ı açıkça belirtilir. Native raster dışı
RT/upscaling/frame generation bağlamları desteklenmez; geçmiş ölçümler gelecek garantisi değildir.
Recommendation tahmin aralığı session P10/P90 değerlerinin medyanından floor/ceil ile üretilir;
confidence LOW, yöntem/sayım/zaman/sınırlılıklar yanıtlanır. Eksik/eşleşmeyen/az/eski bağlam
INSUFFICIENT_DATA, null tahmin ve NONE güven olarak kalır.

Recommendation assessment'a opsiyonel renderingMode eklenir; yalnız tam çözünürlük/preset/
NATIVE_RASTER isteği yeni evidence'i kullanır. Eski istemciler mevcut çekimser davranışı korur.
Assistant hardware rehberi FPS vaadine dönüştürülmez. Auth/User/Game/Social/Event kodu ve ENV
değişmez. Native pre-push/Sonar önceki ortam engelleri ayrı tutulur; MERGE READY iddiası yoktur.

## Kabul sonucu (2026-11-18)

- **Performance:** 174 test, 0 hata (148 baseline + 26 yeni). Yeni:
  `PerformanceEvidenceIntegrationTest` (12) ve `PerformanceMeasurementContextIntegrationTest` (14).
  Gerçek PostgreSQL 17 (Testcontainers), Flyway V1–V24.
- **Recommendation:** 85 test, 0 hata (80 baseline + 5 yeni, `HardwareAssessmentApiIntegrationTest`):
  ESTIMATED/LOW + provenance, yetersiz kanıt, bozuk/erişilemeyen kanıt → 503, bağlamsız
  `renderingMode` → eski referans yolu, geçersiz `renderingMode` → 400. Ayrıca token
  (`svc:recommendation-service`, aud, scope, account_id) ve exact sorgu bağı doğrulanır.
- Test hatası düzeltmeleri: FPS DB kısıtı ≤ 1000 olduğundan aykırı değer örneği 900 yapıldı;
  yanıt alanı `producerSampleThresholdMet`.
- **Çalıştırma notu:** `node pre-push/ci/backend.mjs` bu makinede Windows'ta disposable
  PostgreSQL'i başlatamıyor: runner'ın env allow-list'inde `ProgramFiles` yok, `docker compose`
  eklentisi bulunamıyor (`unknown flag: --env-file`). Ortak altyapı olduğundan değiştirilmedi.
  Aynı `pre-push/ci/compose.test.yml` ile kendi disposable DB'm ayağa kaldırılıp
  `mvn -Pci -Dltz.test.postgres.port=<port>` ile modül testleri koşuldu (sonra `down -v`).
- **Koşulmadı / iddia yok:** Checkstyle/PMD ratchet, merkezi coverage gate, JAR dependency
  scan, Sonar, canlı çok servisli E2E. MERGE READY iddiası yoktur.
- Sözleşme: `docs/api.md` PERFORMANCE (performance-evidence, oturum başlat, sync v3) ve
  RECOMMENDATION (renderingMode, fps.evidence); şema: `docs/database.md`, `docs/er-diagram.md` (V24).
