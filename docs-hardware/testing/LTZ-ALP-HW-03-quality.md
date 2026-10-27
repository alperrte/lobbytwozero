# LTZ-ALP-HW-03 kalite kanıtı

Tarih 2026-10-21. Durum READY_FOR_PDA — DEFERRED_AUTH / DEFERRED_CONTRACT.
Son kaynak için iki final kapı PASS; aşağıda önce ara koşuların geçmişi korunur.

## Yerel alt görev testleri

Komut: mvn -f backend/pom.xml -pl services/hardware-service -am verify -B -ntp.
Java 25 / Maven / Spring Boot 4.1.1 / izole PostgreSQL 17 + gerçek Flyway/JPA.

| Aşama | Test | Sonuç | Zaman +03 | Log |
|---|---:|---|---|---|
| HW-08 ilk | 150 | PASS / exit 0 | 06:00:18 | logs/hw03-privacy-verify.log |
| HW-08 sürüm yarışı | 151 | PASS / exit 0 | 06:01:40 | logs/hw03-privacy-final.log |
| HW-09 | 160 | PASS / exit 0 | 06:04:01 | logs/hw03-comparison-verify.log |
| HW-17 ilk | 174 | PASS / exit 0 | 06:06:53 | logs/hw03-batch-verify.log |
| HW-17 sorgu sınırı | 174 | PASS / exit 0 | 06:09:00 | logs/hw03-batch-final.log |
| Sabit SQL / son yerel | 174 | PASS / exit 0 | 06:10:20 | logs/hw03-final-local.log |

40 yeni senaryo: privacy 17, comparison 9, batch 14; önceki 134 regresyon korundu.
Son belge tutarlılığı kontrolünde V6 otomatik constraint/index adlarının repo
standardıyla uyuşmadığı görüldü. Uygulanmış V6 değiştirilmeden V7 yalnız adları
düzeltir. V7 eklendiği sırada ikinci full test koşusu hardware aşamasını geçmişti;
o koşu V7 için kanıt değildir. Sonar sonrası final full dev yeniden çalıştırılacak.
Hiç failure/error/skipped yok. Önceki coverage 889/903=%98,45; final kod raporu
pre-push ile tazelenir. Coverage exclusion/suppression eklenmedi.

## Runner regresyonları

node --test pre-push/menu.test.mjs pre-push/quality.test.mjs
pre-push/runtime-checks.test.mjs sonarqube/sonar.test.mjs ops/env.test.mjs

47 PASS / exit 0; logs/hw03-runner-regressions.log.
Oturum başında değiştirilmiş beş pre-push dosyası kullanıcıya ait; korunmuştur.

## Final kapılar

İlk full dev denemesi FAIL / exit 1: ortak PostgreSQL durmuş olduğundan
127.0.0.1:5542 bağlantısı reddedildi; shared DB kullanan context testleri başarısız.
Log logs/pre-push-2026-10-21T03-11-21-690Z-46260-e2323e28.log
ve logs/hw03-pre-push-full-console.log. Kaynak/test atlama veya güvenlik gevşetmesi
yapılmadı. Mevcut LTZ PostgreSQL, 18 backend ve Sonar DB/server container'ları
mevcut image/volume ile başlatıldı; rebuild/deploy/ENV değişikliği/veri silme yok.
İlk koşunun sonunda Compose ve 18/18 HTTP health PASS; önceki test hataları nedeniyle
sonuç NOT SAFE TO COMMIT kaldı. Tüm full dev temiz raporlarla yeniden çalıştırılıyor.
HTTP health mevcut container image'larının sağlığıdır; yeni kaynak kodun runtime
deploy testi değildir. HW-03 yeni kodu host Maven/MockMvc/PostgreSQL ile sınanır.

- İkinci pre-push full dev: PASS / exit 0, 2026-10-21 06:17:06 +03
  (Maven bitişi); 192 test, 18 modül, toplam satır kapsamı %94,94, 18/18 HTTP PASS,
  Git/secret filename/whitespace ve Compose PASS; gerçek SAFE TO COMMIT.
  Log logs/pre-push-2026-10-21T03-14-30-033Z-45260-10618692.log ve
  logs/hw03-pre-push-full-retry-console.log. V7 bu koşudan sonra ayrıca doğrulanacak;
  final pre-push bunun yerine taze koşuyu kullanacak.
- İlk Sonar scan dev: FAIL / exit 1, 2026-10-21 06:20:30 +03.
  192 taze backend testi PASS, V1–V7 PostgreSQL/Flyway/JPA PASS.
  Analysis 1b1a10ea-2009-4651-bc81-8a649a95d18a,
  CE 98957631-badc-4d2a-8e32-246d69fd1141 SUCCESS; gate ERROR.
  A/A/A, overall %91,2, duplication %0; new coverage %93,7 / duplication %0.
  Gate yalnız üç MINOR yeni bulgu yüzünden FAIL: HardwareProfile iki enum-key Map
  için java:S1640; HardwarePrivacyService kullanılmayan catch adı için java:S7467.
  Hotspot yok. EnumMap ve Java 25 unnamed catch değişkeniyle düzeltildi;
  kural/coverage exclusion veya gate gevşetmesi yapılmadı. Taze full ve Sonar bekliyor.
  Log logs/sonarqube-scan-2026-10-21T03-17-31-410Z-46724-e71abcd1.log.
- Gerçek Auth: DEFERRED_AUTH / NOT_VERIFIED.
- User/Social policy ve Performance/Recommendation consumer E2E:
  DEFERRED_CONTRACT / NOT_VERIFIED; açık kullanıcı onaylı.

MockMvc typed actor ve test-only policy gerçek token/E2E bağlantısı sayılmaz.

## Son kaynak kod için final pre-push

Komut .\pre-push\pre-push.cmd full dev — PASS / exit 0.
Maven bitişi 2026-10-21 06:23:50 +03; sonuç 06:24:19 +03 kontrol edildi.
18 backend modülü, 192 başarılı test, sıfır failure/error/skipped;
hardware 174 test, V1–V7 PostgreSQL/Flyway/JPA validate PASS.
Ağırlıklı toplam backend satır kapsamı %94,94.
Git/secret filename/whitespace, backend test/build/coverage, Compose PASS;
18/18 HTTP health PASS. Gerçek çıktı SAFE TO COMMIT.
Log logs/pre-push-2026-10-21T03-21-23-603Z-17788-6f62af74.log
ve logs/hw03-pre-push-final-console.log. Sonar düzeltmeleri ve V7 bu koşuda dahildir.
Log kapanışı 06:23:51.518 +03; aşağıdaki final Sonar da PASS olarak doğrulandı.

## Final Sonar — gerçek analiz PASS

Komut .\sonarqube\sonar.cmd scan dev — PASS / exit 0; SONAR SCAN PASSED.
Log kapanışı 2026-10-21 06:27:35.639 +03; 06:28:18 +03 API kontrolü.
Tüm 18 backend modülünde taze clean verify: 192 PASS, failure/error/skipped 0;
hardware 174 PASS. Java 25; gerçek PostgreSQL 17.11, Flyway V1–V7 ve JPA validate PASS.
Hardware JaCoCo 884/898=%98,44; ağırlıklı backend satır kapsamı %94,94.

- Proje ltz-backend; yerel branch feature/hardware-service; scanner ayrı branch
  parametresi vermez, Sonar sunucu dalı main (API ile doğrulandı).
- Analysis ad6e17fe-a77a-4269-92b7-9a64fda8e8c9; analiz tarihi
  2026-10-21 06:27:15 +03; CE df8d4cdf-a7e7-4c8b-a24f-1c705ef247c5
  SUCCESS / 06:27:33 +03. Önceki başarısız analizden ayrı kimliktir.
- Security A, Reliability A, Maintainability A; overall hedefler PASS.
- Overall coverage %91,2, duplication %0; new coverage %93,7, duplication %0.
- Gerçek Quality Gate OK/PASS; Sonar way politika kontrolü VERIFIED;
  new violations 0. Koşullar ignored değildir.
- Açık issue 0 / hotspot 0; bu CE/analysis kimliğiyle API'den doğrulandı.
- Sonar revision mevcut HEAD 6756210'dır; HW-03 çalışma ağacının taranması yeni
  commit atıldığı anlamına gelmez. Ajan Git yazma işlemi yapmadı.

Log logs/sonarqube-scan-2026-10-21T03-24-35-941Z-21548-f319191e.log,
console logs/hw03-sonar-final-console.log; sır içermeyen API snapshot
logs/hw03-sonar-final-evidence.json. Gerçek ENV/token/parola rapora alınmadı.
İlk üç bulgu kodla çözüldü; suppress/ignore, test atlama veya gate gevşetme yok.
Teslim [raporu](../reports/LTZ-ALP-HW-03-delivery.md) aynı değerleri kullanır.
