# LTZ-ALP-HW-05 kalite kanıtı

Tarih 2026-10-21; READY_FOR_PDA — DEFERRED_AUTH / DEFERRED_CONTRACT.
İki nihai kapı PASS; ilk başarısız Sonar koşusu aşağıda geçmiş olarak korunur.

| Aşama | Komut / sonuç | Zaman +03 | Log |
|---|---|---|---|
| HW-06 | mvn -f backend/pom.xml -pl services/hardware-service -am verify -B -ntp; exit 0, 269 PASS | 20:21:49 | logs/hw05-preview-verify.log |
| HW-07 V10 eski regresyon | Aynı verify, exit 0, 269 PASS; PostgreSQL/Flyway/JPA V1–V10 PASS | 20:29:27 | logs/hw05-sync-initial-verify.log |
| HW-07 ilk yeni testler | Aynı verify, exit 0, 289 PASS | 20:34:31 | logs/hw05-sync-tests-verify.log |
| HW-07 son yerel | Aynı verify, exit 0, 293 PASS | 20:36:36 | logs/hw05-sync-final-local-verify.log |

HW-06 30 yeni test (HardwareScanControllerTest 28, HardwareScanTest 2);
önceki 239 regresyon PASS. Failure/error/skipped 0. JaCoCo 1243/1261=%98,57;
exclusion yok. Java 25, Spring Boot 4.1.1, izole PostgreSQL 17/Testcontainers,
gerçek Flyway V1–V9 ve ddl-auto=validate, MockMvc kullanıldı. HW-06 DB değiştirmez.

Owner/IDOR, typed principal, anonim/forged headers, CSRF, 16 KiB, açık onay,
markasız model, ham/NFKC metin, teknik range, 32/33 bileşen, tekil component key,
yaş/future timestamp, bilinmeyen seri/MAC/hostname/FPS alanları ve user/peer limitleri
testli. Preview hardware/source/version değiştirmez, yalnız TTL rate bucket'ı yazar.

HW-07 24 yeni senaryo: HardwareSyncControllerTest 21, HardwareSyncDomainTest 2,
SecurityContextHardwareSyncDeviceAccessTest 1. HW-05 toplamı 54 yeni; hardware
293 PASS, failure/error/skipped 0. Son JaCoCo 1420/1438=%98,75; exclusion yok.
İzin/iptal/generation/sequence, ayrı cihaz principal/scope/service guard,
policy deny/hata, normalleştirilmiş/sıralı retry hash, eski sürüm, manuel override,
snapshot küme farkı, receipt kapasitesi/TTL/anti-replay, actor/device/peer limitleri,
DB CHECK/unique ve profil CASCADE testli. Concurrent aynı key 200/200 tek mutasyon;
owner edit/apply ve revoke/apply yarışlarında 200/409 ve tek aggregate artışı doğrulandı.
V10 mevcut testlerde uygulandı ve artık değiştirilmeyecek; canlı DB'ye uygulanmadı.

Final full dev PASS / exit 0: 18 modül, 311 test, failure/error/skipped 0;
backend satır kapsamı %96,51. Maven bitişi 2026-10-21 20:41:01 +03,
log kapanışı 20:41:03 +03. Git/secret filename/whitespace, build/tests/coverage,
Compose ve 18/18 HTTP PASS; SAFE TO COMMIT.
Log logs/pre-push-2026-10-21T17-37-56-751Z-52212-99d26ecc.log ve
logs/hw05-pre-push-full-console.log. Bu ilk PASS, sonraki Sonar düzeltmelerinden öncedir.
Önceki HW-04 sonuçları devralınmaz. HTTP health mevcut image'ları sınar;
yeni kaynakların deploy/E2E kanıtı değildir. Frontend repo içinde yoktur.

İlk Sonar scan dev FAIL / exit 1, 20:44:57 +03. Analysis
852dad20-85bf-4157-8de4-6cc706603078, CE SUCCESS; gerçek gate ERROR, new_violations=9.
A/A/A, overall coverage %93,4 / new %95,0, duplication %0 koşulları geçti.
Bir java:S1192 CRITICAL bakım bulgusu (GRANTED sabiti) ve sekiz java:S5778 MAJOR
test bulgusu (assertThrows lambda'sında birden fazla çağrı) kodda giderildi.
Security bulgusu olarak raporlanmaz; severity/type ayrımı korunur. Eşik/exclusion/
güvenlik kuralı değiştirilmedi. Kanıt logs/hw05-sonar-first-evidence.json ve
logs/hw05-sonar-console.log. Düzeltme sonrası full dev RUNNING, ardından scan dev.
Nihai full dev PASS / exit 0; 311 test, backend %96,51, hardware 293/%98,75;
failure/error/skipped 0, Compose ve 18/18 HTTP PASS, SAFE TO COMMIT.
Maven bitişi 20:49:02 +03, log kapanışı 20:49:04 +03.
Log logs/pre-push-2026-10-21T17-45-54-225Z-6992-f46ca51b.log ve
logs/hw05-pre-push-full-final-console.log. Nihai scan dev aynı kaynakla RUNNING.
Gerçek kullanıcı/cihaz Auth DEFERRED_AUTH; PERF cihaz kontratı ve desktop E2E
DEFERRED_CONTRACT / NOT_VERIFIED (kullanıcı onaylı), production fail-closed.

## Nihai Sonar ve sunucu kanıtı

.\sonarqube\sonar.cmd scan dev PASS / exit 0, SONAR SCAN PASSED,
2026-10-21 20:53:34 +03. Backend yeniden clean verify: 311 PASS/%96,51;
hardware 293 PASS, 1420/1438=%98,75. Failure/error/skipped 0, exclusion yok.
Security A / Reliability A / Maintainability A; Sonar way VERIFIED,
Quality Gate OK/PASS, new violations 0. Overall coverage %93,4, new %95,0,
overall/new duplication %0; açık issue/hotspot 0/0.

- Log logs/sonarqube-scan-2026-10-21T17-49-25-535Z-49424-ac8b5ee3.log
  ve logs/hw05-sonar-final-console.log.
- Analysis 5494a478-d3da-4e12-9703-c6fdc06e5817, 20:53:05 +03.
- CE task 749310d1-c50b-443c-94dd-cec5eac1a95e SUCCESS, 20:53:31 +03.
- API doğrulaması 20:53:53 +03: bu analysisId gate OK, new_coverage=95.0,
  new_duplication=0.0, new_violations=0; açık issue/hotspot 0/0.
  Kanıt logs/hw05-sonar-final-evidence.json; secret/token/ENV değeri içermez.
- Proje ltz-backend/main; yerel Git feature/hardware-service. Runner ayrı Sonar
  branch parametresi göndermez. Revision f558a69 önceki HEAD; HW-05 çalışma ağacı
  kaynaklarını kapsar, yeni commit SHA'sı değildir.

Son production/test kaynak değişikliği 20:45:51 +03; iki nihai kapıdan öncedir.
Sonrasında yalnız rapor/API/checkpoint belgeleri güncellendi. Ana HW-05 kutusu
kullanıcı PDA kapanışını bekler. [Teslim](../reports/LTZ-ALP-HW-05-delivery.md) ve
[final servis raporu](../reports/HARDWARE_SERVICE_FINAL_REPORT.md) aynı kanıtı taşır.

Son doküman/kapsam kontrolü PASS: final rapor 46 gerçek controller mapping'ini
içerir, görev/rapor yerel linkleri geçerli; master 17/17 alt görev ve 4/5 kullanıcı
PDA kapanışıyla tutarlı. Ana HW-05 [ ] kalır. Master §0–11 checkbox dışındaki
kurallar HEAD ile aynı; git diff --check ve yeni dosyalarda whitespace/conflict
kontrolü PASS. Kapsam 37 hardware kaynak + 13 belge = 50 dosya; beklenmeyen yol yok.
Sonrasında yalnız belgeler değişti; Git staging/commit/push veya PDA işlemi yapılmadı.
