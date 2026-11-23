# LTZ-ALP-HW-01 kalite kanıtları

Tarih: 2026-10-21. Kapsam: hardware-service + onaylı ortak runner düzeltmesi.
Güncel görev durumu: **READY_FOR_PDA — DEFERRED_AUTH**. Alt görevler 7/7 ve iki final
kalite kapısı son kod üzerinde PASS. Aşağıdaki eski denemeler tarihsel kanıttır.

| Kontrol | Sonuç | Kanıt |
|---|---|---|
| Maven/JDK | PASS | `mvn -v`: Maven 3.9.16, Java 25.0.4.1 |
| Domain birim testleri | PASS | HardwareProfileTest: 9 |
| HTTP/DB testleri | PASS | HardwareProfileControllerTest: 16; HardwareComponentControllerTest: 32 |
| Context testi | PASS | HardwareServiceApplicationTests: 1; ayrı test DB'si |
| PostgreSQL SQL smoke | PASS | İzole PostgreSQL 17; V1 + profile-schema-check.sql, exit 0 |
| Flyway/JPA | PASS | PostgreSQL 17.11; V1/V2 uygulanması ve Hibernate validate |
| JaCoCo ≥ %80 | PASS | Son kod: 321/332 satır = %96,69; hariç tutma eklenmedi |
| Final pre-push | PASS | hardware-service; 58 test, %96,69, exit 0; 01:13:28 +03:00 |
| Sonar A / Quality Gate PASS | PASS | Son kod analizi A/A/A, gate/overall PASS, exit 0 |
| Sonar kritik bulgu incelemesi | PASS | Açık BLOCKER/CRITICAL 0, vulnerability 0, hotspot 0 |
| Gerçek Auth/E2E | DEFERRED_AUTH | Sözleşme yok |

Son tam modül doğrulaması (2026-10-21 00:32:11 +03:00):

```text
mvn -B -ntp -f backend/pom.xml -pl services/hardware-service clean verify
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS — exit 0
JaCoCo LINE: covered=320, missed=11, total=331, coverage=96.68%
```

Raporlar: `backend/services/hardware-service/target/surefire-reports/TEST-*.xml`,
`target/site/jacoco/jacoco.xml`. Bunlar üretilmiş kanıttır; Git'e eklenmez.
Sıralı modül test sonuçları 19 → 42 → 47 → 52 → 54 → 55 → 57 → 58 PASS.
Son artış, Content-Length olmadan da istek gövdesini sınırlayan 16 KiB güvenlik testidir.
İki paralel writer testi bir 201/bir 409 ve tek kayıtla PASS. Sahiplik, mass assignment,
markasız/opsiyonel marka, 12 kategori, teknik null, pagination, istatistik ve CASCADE PASS.

İlk HTTP derlemesi iki ProblemDetail int/HttpStatusCode tip hatasıyla FAIL oldu;
API imzası düzeltilip tüm testler tekrar PASS oldu. Test atlanmadı, security kapatılmadı.

Önceki seçili domain testi (tarihsel kanıt):

```text
mvn -B -ntp -f backend/pom.xml -pl services/hardware-service -Dtest=HardwareProfileTest test
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS — exit 0
```

İlk sandbox denemesi JDK dosyasına erişim engeliyle exit 1; araç izniyle tekrar PASS.
Bu ilk seçili çalışma sonrasında contextLoads dahil tüm modül ve `verify` yukarıda doğrulandı.

PostgreSQL kontrolü: `--network none`, portsuz, geçici `postgres:17-alpine`.
V1 ve test SQL'i stdin üzerinden `docker exec --interactive ltz-hw01-schema-check
psql -U postgres -v ON_ERROR_STOP=1` ile çalıştırıldı. Çıktı `CREATE TABLE`,
`CREATE INDEX`, `BEGIN`, `INSERT 0 1`, `DO`, `ROLLBACK`; exit 0. Container durduruldu.
SQL smoke assert'leri: MANUAL/version varsayılanı, owner indeksi, dış FK yokluğu,
timestamptz; boş/uzun ad, tür/kaynak, negatif sürüm ve NULL owner reddi.

Ana görev tamamlanabilir değildir; iki final kalite komutunun güncel sonucu aşağıdadır.

## Final kalite kapıları — kullanıcı onayıyla çalıştırıldı

Yerel Git dalı `chore/architecture`; Sonar betiği ayrıca branch parametresi göndermez.
Profil/token değerleri okunmadı/yazdırılmadı; yalnız onaylanan betik bunları içeride kullandı.

Pre-push: `.\pre-push\pre-push.cmd hardware-service`, 2026-10-21
00:47:52–00:48:13 +03:00, exit **0**. Gerçek çıktı:

```text
hardware-service        PASS        PASS        58       96.68%
Maven Verify             PASS
BACKEND TEST CHECK PASSED
```

Kapsam yalnız hardware-service backend test/kapsam kontrolüdür; full Compose/health kontrolü değildir.
Log: `logs/pre-push-2026-10-20T21-47-52-758Z-41472-c8f36518.log` (dosya adındaki tarih UTC).

Sonar: `.\sonarqube\sonar.cmd scan dev`, 2026-10-21 00:48:21 +03:00,
exit **1**. Gerçek çıktı:

```text
[CHECK] SonarQube Access
[ERROR] fetch failed
SonarQube Access           FAIL
Backend Reports            SKIPPED
Backend Analysis           SKIPPED
Quality Gate Backend       SKIPPED
Overall Targets Backend    SKIPPED
SONAR SCAN FAILED
```

Log: `logs/sonarqube-scan-2026-10-20T21-48-21-953Z-36012-11c55d1d.log`.
Gerçek analiz/Quality Gate/rating sonucu yoktur; analiz PASS sayılmaz. Salt okunur teşhis:
localhost:5199 erişilemiyor; `ltz-sonar` Compose etiketli container bulunamadı.
Ana görev QUALITY_BLOCKED kalır; commit mesajı/PDA kapatma önerisi verilmez.
Ek teşhis: yalnız farklı bir uygulamaya ait, kapalı Sonar container'ları var; bunlara
dokunulmadı. Repo `sonar.cmd up dev`, mevcut `docker-compose.sonar.yml` ile yalnız
`ltz-sonar` sunucu/veritabanını başlatır. Bu ek komut için kullanıcı onayı soruldu.

## Token sonrası tarama — 2026-10-21

Kullanıcı token'ı yerel profile kaydettiğini bildirdi. Sunucu hazır hâle geldikten sonra
önceden onaylı `.\sonarqube\sonar.cmd scan dev` tekrar çalıştırıldı.
Log: `logs/sonarqube-scan-2026-10-20T21-59-58-457Z-1156-c21be161.log`.
Başlangıç 2026-10-21 00:59:58 +03:00; **exit 1**. Son tablo:

```text
SonarQube Access           PASS
Backend Reports            FAIL
Backend Analysis           FAIL
Quality Gate Backend       SKIPPED
Overall Targets Backend    SKIPPED
SONAR SCAN FAILED
```

Token/sunucu erişimi doğrulandı; gerçek analiz gönderilmedi. Backend Analysis satırı,
başarılı test raporları bulunmadığı için betiğin koruma adımında FAIL oldu.
16 servis contextLoads hatası aldı. İlk testler PostgreSQL kapalı olduğu için connection
refused; mevcut container başlatılıp healthy olduktan sonra kalanlarda authentication
failed. Hardware'in ayrı Testcontainers DB testleri 58 PASS kaldı.

Kaynak kodda doğrulanan neden: ortak profil loader yalnız SONAR_* alanlarını yükler;
Maven testlerinin POSTGRES_* bağlantı ayarlarını profilden almaz. Değerler okunmadan
teşhis edildi. [Dar düzeltme önerisi](../decisions/quality-runner-env-proposal.md)
onay bekliyor; başka servis testi/DB'si veya ortak runner değiştirilmedi.

## Onaylı runner düzeltmesinin regresyon testleri

Kullanıcı HW01-QG-01 düzeltmesini onayladı. Mevcut DB ayarlarının yalnız Maven
test sürecine aktarımı, host/container port ayrımı, terminal önceliği, geçersiz
port/eksik kimlik reddi ve secret maskeleme kodlandı. Yeni ENV anahtarı veya gerçek
ENV dosyası değişmedi; Sonar scanner/Compose ortamına test DB secret'ı eklenmedi.

```text
node --test sonarqube/sonar.test.mjs pre-push/menu.test.mjs pre-push/quality.test.mjs pre-push/runtime-checks.test.mjs ops/env.test.mjs
tests 44; pass 44; fail 0; skipped 0; exit 0
```

Sentetik fixture testleridir; gerçek Sonar analizi yerine geçmez. Güncel gerçek scan
logu `logs/sonarqube-scan-2026-10-20T22-08-54-867Z-16252-6e6ca89b.log`;
başlangıç 2026-10-21 01:08:54 +03:00, sonuç bekleniyor.

## İlk başarılı analiz ve son bulgu düzeltmesi

Yukarıdaki 01:08:54 taraması **exit 0 / SONAR SCAN PASSED** ile tamamlandı.
Bütün backend'de 76 test PASS. Sonar analiz `25c1f882-ab7a-428c-8f08-5b665cb497f5`
(2026-10-21 01:11:34 +03:00), CE task `c0e36f38-6e58-49d4-9393-2dc21de87e66`.
Proje `ltz-backend`, Sonar branch `main`; yerel Git `chore/architecture`.
Security/Reliability/Maintainability **A**, Quality Gate **PASS**, overall coverage
**%84,3**, duplicated lines **%0**. Atanmış Sonar way gate politikası **VERIFIED**.
Hotspot toplamı **0**. Açık BLOCKER/CRITICAL sorgusunda tek CRITICAL CODE_SMELL:
HardwareComponentService.resolve karmaşıklık 16 > 15 (`java:S3776`). Güvenlik açığı değildir.

Bu bulgu `resolveBrandName` ayrıştırmasıyla düzeltildi; manuel/katalog/opsiyonel marka
davranışı değişmedi. Son kod için yeniden pre-push çalıştırıldı:

```text
.\pre-push\pre-push.cmd hardware-service
hardware-service        PASS        PASS        58       96.69%
Maven Verify             PASS
BACKEND TEST CHECK PASSED
exit 0 — 2026-10-21 01:13:28 +03:00
```

Log: `logs/pre-push-2026-10-20T22-13-08-268Z-41784-a352df7e.log`.
Son kodun Sonar taraması tekrar başladı; log
`logs/sonarqube-scan-2026-10-20T22-13-44-831Z-24048-199ccbc3.log`.
Bu taramanın sonucu doğrulanana kadar önceki analiz son kodun teslim kanıtı sayılmaz.

## Son kodun nihai doğrulaması — PASS

2026-10-21 01:13:44 +03:00 başlangıçlı tarama **exit 0** ile tamamlandı.
Son analiz: **9be6f62f-5bf5-44f8-9d1d-53ca6d9350fc**,
2026-10-21 **01:16:26 +03:00**; CE task **6a037410-68d3-48ca-b4fc-2d6b14e3e6c5**.
Proje `ltz-backend`, Sonar branch `main`, yerel Git `chore/architecture`.

```text
SonarQube Access           PASS
Backend Reports            PASS
Backend Analysis           PASS
Quality Gate Backend       PASS
Overall Targets Backend    PASS
SONAR SCAN PASSED
```

Sonar ölçümleri: Security=1/A, Reliability=1/A, Maintainability=1/A;
overall coverage=%84,3, duplication=%0. Mevcut Sonar yeni kod döneminde
coverage=%100, duplication=%0; yeni kod notları da A. Atanmış Sonar way politikası VERIFIED.
Yerel API ile son analizden sonra açık BLOCKER/CRITICAL toplamı 0,
vulnerability toplamı 0, hotspot toplamı 0 doğrulandı; karmaşıklık bulgusu kapandı.

Surefire root metadata doğrulaması: bütün backend **76 test**, 0 failure/error/skipped.
Hardware son XML: **321 covered / 11 missed**, toplam 332, **%96,69**.
Pre-push aynı son kodda 58 test PASS; Node runner regresyonları 44 PASS.
Bu kanıtlarla iki kapı doğrulandı. Gerçek Auth/E2E DEFERRED_AUTH ve üretim hazırlığı
iddiası yoktur. [Teslim raporu](../reports/LTZ-ALP-HW-01-delivery.md).
