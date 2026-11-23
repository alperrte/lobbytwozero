# Faz 02 test raporu — LTZ-ALP-PERF-02

Tarih: 2026-10-31 · Ortam: JDK 25.0.4.1, Windows 11, PostgreSQL 17 (Testcontainers). Sonuçlar gerçek çalıştırmalardan alınmıştır.
Durum değerleri: PASS · FAIL · BLOCKED · NOT RUN.

## 1. Özet

| Kontrol | Sonuç | Kanıt / not |
| --- | --- | --- |
| Birim + entegrasyon testleri (77) | **PASS** | 0 başarısız, 0 atlanan (Faz 1: 31 + Faz 2: 46) |
| JaCoCo kapsamı | **PASS** | Toplam %96,02; değişen kod %96,23; politika baseline PASS. Eşik ≥ %80 |
| Checkstyle / PMD ratchet | **PASS** | Yeni ihlal 0 (CI runner "Sonuç: yeşil"). İki PMD bulgusu (CyclomaticComplexity, CloseResource) kural kapatmadan kodda düzeltildi |
| CI backend runner (`node pre-push/ci/backend.mjs --modules services/performance-service`) | **PASS** | EXIT 0 |
| Standart repo pre-push (`.\pre-push\pre-push.cmd performance-service`) | **PASS** | BACKEND TEST CHECK PASSED, EXIT 0; Tests PASS, Coverage PASS (77 test / %96,02), Maven Verify PASS, Code Quality (yalnız yeni) PASS. Script ENV'i kendisi yükler; değerler okunmadı/yazdırılmadı |
| SonarQube — ilk tarama (`.\sonarqube\sonar.cmd scan dev`, `AUTH_SERVICE_URL` süreç değişkeniyle) | **FAIL (kapı)** | Analiz çalıştı (araç hatası yok); Faz 1 Sonar bulguları listede **yok** (düzeltmeler doğrulandı). Kapı `new_violations > 0` ile kırmızı. performance-service'in 12 yeni bulgusu: S6218 (`BoundedJsonClient.Reply` içinde `byte[]`), S6353 (`ReportRequest` regex), S5838, S2925 (`Thread.sleep`), S5778 ×8 (test lambdaları). Hepsi bu görevde düzeltildi (aşağıda). Diğer servislerin bulguları (hardware, social, user, game, review, library) kapsam dışıdır, dokunulmadı |
| SonarQube — ikinci tarama (düzeltmelerden sonra) | **FAIL (kapı, performance-service dışı bulgular)** | Analiz çalıştı; **performance-service için açık yeni bulgu 0**. Kapı repo genelinde 35 yeni bulgu nedeniyle kırmızı: hardware-service (S2259 ×3, S3358, S3776 — `HardwareDeviceJwtValidator`, `HardwareSecurityConfiguration`), social, user, game, review, library. Bu bulgulara dokunulmadı (kapsam dışı; hardware kodu değişmeden önce bildirim gerekir). Düzeltme sonrası CI runner: 77 test PASS, %96,02, yeni ihlal 0 |
| Gerçek Game GAME-15 ile E2E | **NOT RUN** | Yerel mock kullanıldı |
| Gerçek Hardware HW-17 ile E2E | **NOT RUN** | Yerel mock; sözleşme `DEFERRED_CONTRACT / NOT_VERIFIED` |
| Çalışan Auth ile oturum/rapor akışı | **NOT RUN** | Kullanıcı token'ı Auth kaynak kodundan derlenen imza/JWKS ile üretildi (kaynak sözleşme testi) |

## 2. Test dağılımı (77)

| Sınıf | Tür | Adet |
| --- | --- | --- |
| `PerformanceSessionIntegrationTest` | Entegrasyon (MockMvc + gerçek güvenlik zinciri + PostgreSQL) | 32 |
| `PerformanceSessionDomainTest` | Birim | 14 |
| `PerformanceSyncIntegrationTest` | Entegrasyon (Faz 1) | 23 |
| `PerformanceDomainTest` | Birim (Faz 1) | 7 |
| `PerformanceServiceApplicationTests` | Bağlam yükleme + Flyway `validate` (V21 + V22) | 1 |

## 3. Kapsanan senaryolar (Faz 2)

- **Kimlik/yetki:** anonim 401 (10 rota), cihaz token'ı 401, tanımsız alt yol 403.
- **Başlatma (PERF-03):** 201 `ACTIVE`/`MANUAL`, sunucu zamanı, `Cache-Control: no-store`; geçersiz gövde, istemci zamanı/durumu → 400; bilinmeyen oyun 422; profil sahipliği (Bearer iletilir / başkasının / yok → 422); bağlantı kimliği (sahip+aktif / başkasının / yok / devre dışı → 422, RESTRICT silme); tek aktif oturum + 8 iş parçacıklı eşzamanlı başlatma → tam 1×201, 7×409; 24 sa'yi aşan ACTIVE → ABANDONED; 1000 oturum sınırı 409; hız sınırı 429.
- **Game/Hardware kesintisi (kapalı başarısızlık):** 400/401/403/429/500/503 ve bozuk gövde → 503 + `Retry-After: 30`; Hardware zaman aşımı → 503; profil verilmediyse Hardware çağrılmaz.
- **Bitirme (PERF-04):** varsayılan COMPLETED, idempotent tekrar (`version` değişmez), çelişkili bitirme 409, geçersiz gövde 400; 8 eşzamanlı bitirme (aynı durum: hepsi 200, `version`+1; farklı durum: 4×200/4×409); bitirme–silme yarışı ×5; iki ayrı kopyayla iyimser kilit çakışması.
- **Okuma:** sayfalama/sıralama/`size` doğrulaması, aktif oturum.
- **IDOR:** başkasının get/end/delete/report put-get-delete → 404, veri bozulmadı; toplu silme başka hesabı etkilemedi.
- **Rapor (PERF-13):** PUT 201 → aynı PUT 200 (değişmedi) → farklı değer 200; COMPLETED değilken 409; geçersiz alan/aralık 400; ölçümsüz/yalnız notlu 422 `REPORT_EMPTY`; `p1 > avg` 422 `REPORT_INCONSISTENT`; not temizleme.
- **Silme (PERF-20):** rapor silme oturumu korur; oturum silme raporu CASCADE siler; `confirm=true` zorunlu; hesap silme yalnız hedef hesabı temizler.
- **DB kısıtları (ham SQL):** oturum durum/zaman CHECK'leri, tek aktif indeks, rapor CHECK'leri (çözünürlük, ön ayar, FPS, sıcaklık, kullanım, en az bir ölçüm), `UNIQUE(session_id)`.

## 4. Ortamda yaşanan sorunlar ve çözümleri

- Tek iş parçacıklı Game/Hardware mock sunucusu 2,5 sn gecikmeli Hardware testi sırasında Game çağrısını bekletti (`sessionCapIsEnforced` 503 verdi). Mock sunucuya çok iş parçacıklı yürütücü verildi; üretim kodu değişmedi.
- İlk CI çalıştırmasında iki yeni PMD bulgusu çıktı (`GameServiceCatalog.parse` karmaşıklığı, testte `ExecutorService` kapanışı). `parse` küçük yardımcılara bölündü, test `try-with-resources` kullandı; baseline/kural değiştirilmedi.
- IDE tanılamasındaki Lombok/`never used` uyarıları eski önbellek gürültüsüdür; derleme Maven ile doğrulandı.

## 5. Dürüstlük notları

- `PerformancePeerDoubles` Game ve Hardware sözleşmelerinin **yerel sapmasıdır**; gerçek entegrasyon değildir.
- `ProductionAuthSupport` Auth üretim kaynağını derler; çalışan Auth ile E2E değildir.
- 77 testin geçmesi Faz 1'in Auth bağımlı işlerini (PERF-01/02/17) tamamlamaz.
- Testlerde JDK uyarıları (dinamik ajan yükleme vb.) yazdırılır; başarısızlık değildir.

## 6. Çalıştırma komutları

```powershell
$env:JAVA_HOME = "C:\Users\alper\Tools\jdk-25.0.4.1+1"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
node pre-push/ci/backend.mjs --modules services/performance-service
node pre-push/ci/lint-ratchet.mjs check --modules services/performance-service
.\pre-push\pre-push.cmd performance-service
.\sonarqube\sonar.cmd scan dev   # tarama sürerken çalışma ağacını değiştirme
```
