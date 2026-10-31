# Faz 01 test raporu — LTZ-ALP-PERF-01

Tarih: 2026-10-31 · Ortam: JDK 25.0.4.1, Windows 11, PostgreSQL 17 (Testcontainers). Sonuçlar gerçek çalıştırmalardan alınmıştır.
Durum değerleri: PASS · FAIL · BLOCKED · NOT RUN.

## 1. Özet

| Kontrol | Sonuç | Kanıt / not |
| --- | --- | --- |
| Birim + entegrasyon testleri (31) | **PASS** | 0 başarısız, 0 atlanan |
| JaCoCo kapsamı | **PASS** | Toplam %94,25 (541 kapsanan / 33 kaçan satır); değişen kod %94,57; politika baseline PASS; 5 arayüz dosyası COVERAGE-EXEMPT. Eşik ≥ %80 |
| Checkstyle / PMD ratchet | **PASS** | Baseline 0 / şimdi 0 / yeni 0 (`node pre-push/ci/lint-ratchet.mjs check --modules services/performance-service`) |
| Bağımlılık taraması (Trivy) | **PASS** | CI runner içinde |
| CI backend runner (`node pre-push/ci/backend.mjs --modules services/performance-service`) | **PASS** | EXIT 0, BUILD SUCCESS. ENV okumaz |
| Standart repo pre-push (`.\pre-push\pre-push.cmd performance-service`) | **PASS** | BACKEND TEST CHECK PASSED, EXIT 0; Tests PASS, Coverage PASS (31 test / %94,25), Maven Verify PASS, Code Quality (yalnız yeni) PASS. Script ENV'i kendisi yükler; değerler okunmadı/yazdırılmadı |
| SonarQube — ilk tarama | **FAIL (araç)** | Maven `clean verify` başka sahiplerin `library-service`/`review-service` `contextLoads` testlerinde düştü (`AUTH_SERVICE_URL` çözülemedi); Sonar analizi çalışmadı |
| SonarQube — ikinci tarama (`AUTH_SERVICE_URL` süreç değişkeniyle) | **FAIL (kapı)** | Analiz çalıştı, `new_violations > 0` nedeniyle kapı kırmızı. performance-service bulguları: S2259, S1075, S3358, S107, S1192 (×2), S5778 (×4). Hepsi bu görevde düzeltildi. Diğer servislerin bulguları kapsam dışıdır, dokunulmadı |
| SonarQube — düzeltme sonrası tarama | **Aşağıdaki not** | Sonuç bu satırın altına işlenir; repo genelindeki kapı başka servislerin bulguları yüzünden kırmızı kalabilir. Bu performance-service'in başarısı sayılmaz, hatası da sayılmaz |
| Gerçek Auth cihaz token'ı ile E2E | **BLOCKED** | REQ-01-A (Auth değişikliği bekleniyor) |
| Hardware→Performance gerçek servis token'ı | **BLOCKED** | REQ-01-C |
| Auth silme işçisi ile DEV doğrulaması | **NOT RUN** | Ortak ortam yok (REQ-01-D) |

## 2. Test dağılımı (31)

| Sınıf | Tür | Adet |
| --- | --- | --- |
| `PerformanceSyncIntegrationTest` | Entegrasyon (MockMvc + gerçek güvenlik filtre zinciri + PostgreSQL) | 23 |
| `PerformanceDomainTest` | Birim | 7 |
| `PerformanceServiceApplicationTests` | Bağlam yükleme + Flyway `validate` | 1 |

## 3. Kapsanan senaryolar (entegrasyon)

- **Kimlik doğrulama / varsayılan reddet:** anonim 401 (GET/PUT/DELETE/POST, CSRF dahil), tanımsız yol reddi, gerçek Auth `aud=hardware-service` cihaz token'ı 401, token türü karışması (kullanıcı↔cihaz↔servis), değiştirilmiş/yabancı anahtarlı token, yanlış scope / uzun ömür / çoklu audience cihaz token'ı, JWKS kesintisinde fail-closed.
- **PERF-17 izin:** izin yok → okuma `granted=false`; ver/iptal idempotent; izin yokken senkronizasyon 403 `SYNC_CONSENT_REQUIRED`; iptalden sonra reddedilir.
- **PERF-01/02 cihaz:** yalnız çağıranın Auth listesindeki cihaz bağlanır; idempotent bağlama/listeleme; başkasının bağlantısını kaldırma 404 (IDOR); başka hesapta bağlı cihaz `DEVICE_LINK_CONFLICT`; hesap başına 5 sınırı; kaldırılan/devre dışı cihaz senkronizasyon yapamaz; Auth kesinti/bozuk yanıt → 503 `AUTH_UNAVAILABLE` + `Retry-After: 30`.
- **PERF-18 idempotency:** ilk kabul, aynı anahtar+içerik tekrarı (`replayed=true`), aynı anahtar farklı içerik 409; 6 iş parçacığıyla eşzamanlı aynı anahtar → tek kayıt; 500 canlı makbuz sınırı 409.
- **Girdi doğrulama:** geçersiz/eksik anahtar, bilinmeyen alan, aralık dışı `sampleCount`/`clientSentAt` (422/400); aşırı büyük gövde 413.
- **PERF-22 gizlilik/silme:** `DELETE /internal/v1/accounts/{id}` token bağlaması (aud, hesap, scope, sub), idempotent 204 + yazma çiti; silme yalnız hedef hesabı temizler; aktif cihaz ucu (yalnız `sub=svc:hardware-service` → 204/404, gerçek Auth servis token'ı 403).

## 4. Dürüstlük notları

- `ProductionAuthSupport` gerçek Auth **üretim kaynağını** derler: **kaynak sözleşme testi**, çalışan Auth ile E2E değildir.
- `PerformanceTestAuth` JWKS + `GET /api/v1/auth/devices` yanıtını **mock** eder; performans-audience cihaz token'ı yerel imzalıdır. Bu testler Auth entegrasyonunun yerine geçmez.
- 31 testin geçmesi PERF-01/02/17'nin tamamlandığı anlamına **gelmez**; checklist'te bu işler `PARTIAL / BLOCKED` işaretlidir.
- Testler sırasında JDK uyarıları (dinamik ajan yükleme vb.) yazdırılır; başarısızlık değildir.

## 5. Çalıştırma komutları (Alper için)

```powershell
# JDK 25 ile
$env:JAVA_HOME = "C:\Users\alper\Tools\jdk-25.0.4.1+1"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
node pre-push/ci/backend.mjs --modules services/performance-service
node pre-push/ci/lint-ratchet.mjs check --modules services/performance-service
.\pre-push\pre-push.cmd performance-service   # standart pre-push — PASS (script ENV'i kendisi yükler)
.\sonarqube\sonar.cmd scan dev                 # SonarQube; token'ı script yükler
```

Not: Sonar taraması tüm backend'i derler; başka servislerin testleri `AUTH_SERVICE_URL` bekliyorsa süreç değişkeni olarak verilmelidir (değer repo dışı, sahiplerin işi).
