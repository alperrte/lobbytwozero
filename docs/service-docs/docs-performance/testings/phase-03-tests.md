# Faz 03 test raporu — LTZ-ALP-PERF-03

Tarih: 2026-10-31 · Ortam: JDK 25.0.4.1, Windows 11, PostgreSQL 17 (Testcontainers). Sonuçlar gerçek çalıştırmalardan alınmıştır.
Durum değerleri: PASS · FAIL · BLOCKED · NOT RUN.

## 1. Özet

| Kontrol | Sonuç | Kanıt / not |
| --- | --- | --- |
| Birim + entegrasyon testleri (111) | **PASS** | 0 başarısız, 0 atlanan (Faz 1: 31 + Faz 2: 46 + Faz 3: 34) |
| JaCoCo kapsamı | **PASS** | Toplam %96,55; değişen kod %96,72. Eşik ≥ %80 |
| Checkstyle / PMD ratchet (`node pre-push/ci/lint-ratchet.mjs check --modules services/performance-service`) | **PASS** | Eski borç 0, şimdi 0, yeni ihlal 0 |
| CI backend runner (`node pre-push/ci/backend.mjs --modules services/performance-service`) | **PASS** | EXIT 0, BUILD SUCCESS |
| Trivy (bağımlılık/yapılandırma) | **PASS** | Bulgu 0 |
| Standart repo pre-push (`.\pre-push\pre-push.cmd performance-service`) | **PASS** | BACKEND TEST CHECK PASSED, EXIT 0; Tests PASS, Coverage PASS (111 test / %96,55), Maven Verify PASS, Code Quality (yalnız yeni) PASS. Script ENV'i kendisi yükler; ENV dosyaları okunmadı |
| SonarQube | **performance-service: 0 bulgu (PASS)** | `.\sonarqube\sonar.cmd scan dev`: ilk taramalarda 1 akış testi + 12 yeni bulgu çıktı (S2583, S1075, S5838, S5778 ×8, ...) ve düzeltildi; son taramada performance-service bulgusu 0. **Genel kapı FAIL**: yalnız diğer servislerin bulguları (social 27, hardware 6, game 2, user 2, library 1, review 1; bkz. I-14/I-15) — bu servislere dokunulmadı | |
| Gerçek Auth cihaz token'ı ile örnek yazımı E2E | **BLOCKED** | REQ-01-A (Auth `aud=performance-service` üretmiyor). Testler yerel imzalı sözleşme token'ı kullanır |
| Gerçek masaüstü istemcisi ile E2E | **NOT RUN** | İstemci sözleşmesi yok |
| Gerçek PostgreSQL'de V23 | **PASS** | Flyway `validate` + migration, CHECK'ler, `unnest`/`ON CONFLICT` ekleme, `erase_performance_account` testle doğrulandı |

## 2. Test dağılımı (111)

| Sınıf | Tür | Adet |
| --- | --- | --- |
| `PerformanceSampleIntegrationTest` | Entegrasyon (MockMvc + gerçek güvenlik zinciri + PostgreSQL) — **Faz 3** | 27 |
| `SampleRulesTest` | Birim — **Faz 3** | 7 |
| `PerformanceSessionIntegrationTest` | Entegrasyon (Faz 2) | 32 |
| `PerformanceSessionDomainTest` | Birim (Faz 2) | 14 |
| `PerformanceSyncIntegrationTest` | Entegrasyon (Faz 1; 64 KiB gövde ve makbuz sınırı testleri güncellendi) | 23 |
| `PerformanceDomainTest` | Birim (Faz 1; 5 parametreli yapıcı, `canonicalV1()`) | 7 |
| `PerformanceServiceApplicationTests` | Bağlam yükleme + Flyway `validate` (V21 + V22 + V23) | 1 |

## 3. Kapsanan senaryolar (Faz 3)

- **Tüm metrikler:** 10 metriğin hepsi doğru birim ve sunucu alanlarıyla (`source=DEVICE`, `receivedAt`) saklanır; oturum `sampleCount` artar.
- **Sıra ve zaman:** sırasız gönderim kabul; milisaniye kesme; pencere dışı (çok eski / gelecek) → 422 `SAMPLE_TIME_INVALID` + `sampleIndex`; COMPLETED oturum 10 dk sonrasına kadar kabul; eski ACTIVE / ABANDONED → 409.
- **Ölçülmemiş değer / 0:** 0 yalnız kullanım/RAM/VRAM/disk/fan için geçerli; sıcaklık ve FPS için 0 reddedilir; eksik değer hiçbir yerde 0'a çevrilmez.
- **Aralık dışı:** her metrik için üst sınır, negatif, NaN/sonsuz; `sampleIndex` doğru; hep-ya-hiç (hiçbir örnek yazılmaz, makbuz oluşmaz). Sınır değerleri (150, 1000, 100…) kabul.
- **Bozuk sayı/şekil:** `"60"` (metin), bool, null, dizi, nesne → 400 ve asla saklanmaz; `1e999` → 422; desteklenmeyen / küçük harfli metrik → 422.
- **Mükerrer:** gönderim içi aynı (metrik, an) → 422 `SAMPLE_DUPLICATE_IN_BATCH`; farklı anahtarla aynı nokta → ilk değer korunur, `duplicateSamples` sayılır.
- **İdempotency:** aynı anahtar+içerik → `replayed=true`; farklı içerik → 409; başarısız gönderim makbuz bırakmaz; eşzamanlı (aynı anahtar ve çakışan gönderimler) tek sonuç.
- **Geriye uyum:** Faz 1 v1 zarfı değişmeden çalışır; v1/v2 alan karışımı → 400; 500 örnek sınırı.
- **Yetki / IDOR:** başkasının/olmayan oturum aynı 404; kullanıcı token'ı sync ucunda 401; cihaz token'ı/anonim silme ucunda 401; izin geri alınmış / cihaz bağı kaldırılmış → 403.
- **Kota:** oturum başına 100.000 ve hesap başına 500.000 sınırı → 409 (advisory kilit altında).
- **Silme:** `DELETE /sessions/{id}/samples` 204, idempotent, yalnız sahibi, sayaç sıfırlanır, aynı aralık yeniden gönderilebilir; oturum silinince CASCADE; hesap silme fonksiyonu örnekleri temizler.
- **DB savunması:** ham SQL ile CHECK kısıtları (metrik, birim, kaynak, değer aralığı), cihaz bağı RESTRICT.
- **Birim (`SampleRulesTest`):** `SampleMetric.parse/unit/bounds`, `TelemetrySample` ms kesme, `requireAccepting`, `validate` indeksleri, üst sınırın `bitiş + 5 dk` ile sınırlanması, `SyncBatchRequest.requireShape/effectiveSampleCount`.

## 4. Çalıştırma sırasında bulunan hata

- `"value":"60"` Jackson tarafından sessizce `60.0` yapılıp saklanıyordu (test 200 döndü, 400 beklenirdi). `StrictNumberDeserializer` ile düzeltildi; sonra 111 test geçti.
- `sessionWritesAreRateLimited` (Faz 2 testi) dakika sınırını geçince kovası sıfırlandığı için aralıklı başarısız oluyordu (Sonar taramasında yakalandı); ilk 429'a kadar döngüye çevrilerek kararlı hale getirildi, 32/32 geçti.
- Sonar düzeltmeleri: `PerformanceSampleRepository` ölü null denetimi (S2583) `Objects.requireNonNull` ile; `PerformanceRequestBodyLimitFilter` sabit URI alanı (S1075) yöntem içine alındı; testlerde S5838/S5778 (tek riskli çağrı kuralı) düzeltildi. Düzeltmelerden sonra 111/111 test, lint ratchet yeni ihlal 0.
- CI runner `EBUSY` (kabuğun çalışma dizini `target/surefire-reports` içindeydi); depo kökünden çalıştırınca giderildi — kod sorunu değil.

## 5. Bilinen eksikler

- Gerçek cihaz/istemci ve gerçek Auth E2E yok (bkz. [issues.md](../reports/issues.md) I-16, I-21).
- Yük/performans testi yapılmadı (500 örnek gönderim ve kota testleri işlevseldir, yük testi değildir).
- Saklama/temizlik politikası ve bölümleme test edilmedi çünkü uygulanmadı (I-20).
