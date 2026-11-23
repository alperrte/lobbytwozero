# LTZ-ALP-HW-04 kalite kanıtı

Tarih 2026-10-21. Durum READY_FOR_PDA — DEFERRED_AUTH / DEFERRED_CONTRACT.
Nihai iki kalite kapısı PASS; aşağıda başarısız denemeler de korunur.

## Alt görev testleri ve düzeltmeler

Komut mvn -f backend/pom.xml -pl services/hardware-service -am verify -B -ntp.
Java 25 / Spring Boot 4.1.1 / Testcontainers PostgreSQL 17 + gerçek Flyway/JPA.

| Aşama | Sonuç | Zaman +03 | Log |
|---|---|---|---|
| HW-12 eski regresyon/V8 | 174 PASS / exit 0 | 18:34:57 | logs/hw04-review-initial.log |
| HW-12 yeni testler ilk | FAIL / exit 1; tek Mockito restub fixture hatası | 18:38:44 | logs/hw04-review-verify.log |
| HW-12 son | 206 PASS / exit 0 | 18:40:14 | logs/hw04-review-verify-final.log |
| HW-13 eski regresyon/V9 | 206 PASS / exit 0 | 18:48:01 | logs/hw04-moderation-initial.log |
| HW-13 test compile | FAIL / exit 1; String yerine GrantedAuthority gerekli | 18:54:12 | logs/hw04-moderation-verify.log |
| HW-13 yeni testler ilk | FAIL / exit 1; Repository DAO exception wrapper beklentisi | 18:55:01 | logs/hw04-moderation-verify-retry.log |
| HW-13 son | 239 PASS / exit 0 | 18:56:40 | logs/hw04-moderation-final-local.log |
| Sonar düzeltmeleri son verify | 239 PASS / exit 0 | 19:13:07 | logs/hw04-sonar-fixes-verify.log |

65 yeni senaryo: HW-12 32; HW-13 33. Önceki 174 regresyon korundu.
Yeni sınıflar: HardwareReviewTextTest, HardwareReviewControllerTest,
HardwareReviewModerationControllerTest, SecurityContextHardwareReviewModerationAccessTest.
Son koşu failure/error/skipped 0; JaCoCo 1176/1192=%98,66, exclusion yok.
V1–V9 PostgreSQL/Flyway/JPA validate PASS. V8/V9 canlı DB'ye uygulanmadı.

Fixture hataları testte giderildi; üretim güvenliği değiştirilmedi. Kapasite yarışı
iki ayrı socket peer ve iki ayrı hedefle sınandı; peer rate kilidinin outbox
kapasite kilidi kontrolünü yanlışlıkla seri hale getirmesi önlendi.

## Doğrulanan sınırlar

- Owner-only değişiklik/silme; typed actor/CSRF/mass assignment/body limit.
- Ham/NFKC düz metin/rating; normalleştirilmiş kopya ve ürün/yazar DB tekilliği.
- HIDDEN/DELETED/arşivli/görünmez/engelli kaynaklar, sayaç/AVG ve sayfalama filtresi.
- Soft-delete içerik temizliği; silinmiş yorum tekrar yayımlanamaz.
- 20 actor/120 peer ve 60 moderator sınırı; sahte forwarding başlığı limit aşamaz.
- Rapor doğal tekilliği; outbox/audit/quota ile transaction rollback.
- HIDE/RESTORE izni HTTP ve service sınırında; katalog yetkisi yeterli değil.
- Review version yarışları, 10.000 global pending kapasitesi ve 4 KiB payload sınırı.
- PENDING teslim edilmedi; yerel SENT/purge kaydı gerçek E2E doğrulaması sayılmaz.
- 25 raporun detail sayfasında üç ORM statement + tek bounded JDBC status sorgusu.

## Final kapılar

- İlk pre-push .\pre-push\pre-push.cmd full dev: PASS / exit 0;
  18 modül, 257 test, 0 failure/error/skipped; backend satır kapsamı %95,98.
  Git/secret filename/whitespace, test/build/coverage, Compose ve 18/18 HTTP health
  PASS; gerçek SAFE TO COMMIT. Maven bitişi 2026-10-21 19:02:35 +03.
  Log logs/pre-push-2026-10-21T15-59-46-503Z-44984-b2e7ff29.log ve
  logs/hw04-pre-push-full-console.log. Final clean raporda hardware 239 PASS/%98,66.
- İlk Sonar .\sonarqube\sonar.cmd scan dev: FAIL / exit 1, 19:06:26 +03.
  A/A/A, overall coverage %92,7, new coverage %94,7 ve duplication %0;
  ancak gerçek gate new_violations=3 nedeniyle ERROR.
  Analiz e600d52d-a78f-46f9-9e13-0d8061088422; java:S1602 (outbox lambda)
  ve iki java:S5838 (test map assertions) MINOR bulgusu dar biçimde düzeltildi.
  Kapı/eşik/exclusion değiştirilmedi. Taze full dev ve scan dev gerekli.
- İlk düzeltme sonrası full dev FAIL / exit 1, 19:11:53 +03:
  lambda expression JdbcTemplate RowCallbackHandler/ResultSetExtractor overload
  belirsizliği oluşturdu. Açık RowCallbackHandler yerel değişkeniyle giderildi.
  Log logs/pre-push-2026-10-21T16-09-25-758Z-42916-64d3ede8.log;
  önce hardware verify, ardından iki final kapı yeniden doğrulanacak.
- Nihai pre-push .\pre-push\pre-push.cmd full dev: PASS / exit 0;
  Maven bitişi 19:16:17 +03, log kapanışı 19:16:18 +03. 18 modül / 257 test,
  failure/error/skipped 0; backend %95,98, hardware 239 PASS/%98,66.
  Git/secret filename/whitespace, test/build/coverage, Compose ve 18/18 HTTP PASS;
  SAFE TO COMMIT. Log logs/pre-push-2026-10-21T16-13-28-796Z-41920-7e4094af.log
  ve logs/hw04-pre-push-full-final-console.log.
- Nihai Sonar .\sonarqube\sonar.cmd scan dev: PASS / exit 0;
  SONAR SCAN PASSED, 19:19:59 +03. Tüm backend yeniden clean verify:
  257 PASS, yerel satır kapsamı %95,98. Security A / Reliability A /
  Maintainability A, Quality Gate OK/PASS; atanmış Sonar way VERIFIED.
  Overall coverage %92,7 / new %94,7; overall/new duplication %0;
  new violations 0, açık issue 0, hotspot 0. Eşik/exclusion gevşetilmedi.
  Log logs/sonarqube-scan-2026-10-21T16-16-31-951Z-35132-192e196f.log
  ve logs/hw04-sonar-final-console.log.
- Gerçek Auth: DEFERRED_AUTH / NOT_VERIFIED; üretim fail-closed.
- Admin/Notification/User-Social UGC: DEFERRED_CONTRACT / NOT_VERIFIED, açık onaylı.

## Sunucudaki gerçek analiz kanıtı

- Proje ltz-backend; yerel Git branch feature/hardware-service. Runner ayrı Sonar
  branch parametresi göndermez; sunucuda main dalına tüm backend analizi yazılır.
- Analysis 9e8be6f7-b5c8-49df-b977-12232620fd7d, 19:19:35 +03.
- CE task 2cccba9a-4ebf-497f-91ef-bbeca0fe9ac9 SUCCESS, 19:19:57 +03.
- API doğrulaması 19:20:18 +03: bu analysisId için gate OK,
  new_coverage=94.7, new_duplicated_lines_density=0.0, new_violations=0;
  güncel açık issue/hotspot 0/0. Kanıt logs/hw04-sonar-final-evidence.json.
- Sonar revision 104524a5d4f845d8c7fadd9faae70bc1d4e882a0 mevcut HEAD'dir;
  HW-04 değişiklikleri çalışma ağacındadır, bu HW-04 commit SHA'sı değildir.

İki final koşu aynı production/test kaynaklarını kapsar. Sonraki değişiklikler
yalnız kalite/teslim/checkpoint belgeleridir. Ana HW-04 kutusu kullanıcı PDA kapanış
bildirimi gelene kadar boş kalır; HW-05 NOT_STARTED. [Teslim](../reports/LTZ-ALP-HW-04-delivery.md).

Önceki HW-03 pre-push/Sonar PASS bu taskın sonucu olarak kullanılmaz.
HTTP health mevcut container image'larının sağlığıdır; yeni kod deploy/E2E kanıtı değildir.
Yeni iş akışları host Maven/MockMvc/izole PostgreSQL ile doğrulanır; gerçek token ve
consumer bağlantıları test mock'undan ayrı tutulur. ENV/Compose/dependency/gateway
değişikliği veya Git yazma işlemi yapılmaz.

Son tutarlılık kontrolü: 41 controller business mapping, 239 test (yeni sınıflar
25+32+7+1=65), 1176 covered/16 missed satır doğrulandı. Master 15/17 alt görev;
ana 01/02/03 [x], 04/05 [ ]. Görev raporlarının yerel linkleri geçerli;
git diff --check ve yeni dosyaların whitespace/conflict taraması PASS.
Son production/test kaynak değişikliği 19:12:27 +03, iki nihai kapıdan öncedir;
sonrasında yalnız dokümantasyon güncellendi. Kapsam 45 hardware kaynak + 12 belge,
toplam 57 dosya; başka servis veya gerçek ENV dosyası değişikliği yoktur.
