# LTZ-ALP-HW-04 — Donanım Yorumları ve Moderasyon

Tarih 2026-10-21; Alper. Durum READY_FOR_PDA — DEFERRED_AUTH / DEFERRED_CONTRACT.
2/2 alt görev, toplam 15/17; iki nihai kalite kapısı PASS. PDA kapanışı kullanıcıyı bekler.

- [x] HW-12 — Donanım yorumları
- [x] HW-13 — Donanım yorumu moderasyonu

## Başlangıç snapshot'ı

Kullanıcı HW-03 PDA kapanışını ve HW-04 geçişini onayladı. Commit 104524a ve
başlangıçta temiz çalışma ağacı doğrulandı. Önceki 174 hardware / 192 backend test,
full SAFE TO COMMIT ve Sonar A/A/A + gate PASS HW-03 raporunda korunur;
bunlar HW-04 kalite sonucu değildir. Toplam alt görev 13/17, PDA kapanışı 3/5.

AGENTS/CLAUDE/master, Alper görevleri, genel DOCX'in FR-HW-06 ve moderasyon
maddeleri, güvenlik/DB/mimari/API/folder/web kuralları incelendi.
LTZ_PDA_Alper.md yok; kök 5/17 checklist'i temel alınır. V1–V7 değişmeyecek;
sonraki boş migration V8. Admin/Notification API kaydı ve gerçek entegrasyon yok.
Başka ekip servisleri, ENV/Compose/dependency/gateway değişikliği yapılmayacak.

[Somut yorum/erişim/moderasyon ve erteleme önerisi](../decisions/HW04-review-moderation-proposal.md)
hazırlandı. Kullanıcı model ve güvenli ertelemeyi ayrı ayrı onayladı.
Henüz HW-04 production kodu/migration/endpoint yok; test/pre-push/Sonar NOT_RUN.
Onay sonrası HW-12 V8 review entity/text/DTO/repository/service/controller,
audience filtreleri, owner/version/tekillik/rate limit ve PostgreSQL testleri uygulanır.

## HW-12 uygulama

V8 review/audit/rate-bucket şeması, domain metin ve kopya-fingerprint doğrulaması,
owner create/update/delete, policy ile filtrelenen liste/detail ve mine uçları eklendi.
Production audience adapter deny eder. SQL tekillikleri, audit append-only trigger,
20 actor/120 socket-peer fixed-minute limitleri ve dakikalık TTL temizliği uygulanır.
İlk mevcut test regresyonu 174 PASS; yeni testli koşuda tek test fixture hatası:
Mockito thenThrow sonrası when ile yeniden stub kurarken sentetik exception yükseldi.
doReturn ile düzeltildi; üretim güvenlik davranışı değiştirilmedi. Taze verify sürüyor.

HW-12 final alt görev verify PASS / exit 0: 206 test (32 yeni), failure/error/skipped 0;
JaCoCo 1044/1060=%98,49; 2026-10-21 18:40:14 +03.
Log logs/hw04-review-verify-final.log. Gerçek PostgreSQL/Flyway V1–V8/JPA PASS.
Altı endpoint docs/api.md HARDWARE altında; DB/ER güncel. V8 artık değiştirilmez.
Sıradaki HW-13; final tüm backend pre-push/Sonar henüz NOT_RUN.

## HW-13 uygulama ve ara doğrulama

V9 rapor/outbox ve audit action genişletmesi, reporter-only sorgu, kaynak HIDE/RESTORE
izni ve atomik PENDING olaylar eklendi. Mevcut 206 test 18:48:01 +03'te PASS.
Yeni HW-13 testleri ilk derlemede Spring Security test API'sine String yerine
GrantedAuthority verilmesi gerektiğini gösterdi; fixture tipi düzeltildi, verify tekrar çalışıyor.
Outbox payload 4 KiB, PENDING/FAILED toplamı 10.000 ile sınırlı; kapasite doluysa
503 ve transaction rollback. Gerçek Admin/Notification/User/Social E2E DEFERRED_CONTRACT.

HW-13 ilk koşuda oversized internal event testinde Repository proxy'nin
IllegalArgumentException'ı InvalidDataAccessApiUsageException ile sarmaladığı görüldü;
test doğru DAO/root-cause sınırını doğrulayacak biçimde düzeltildi. Kapasite yarışı
ayrı socket peer'larla sınandı; peer rate kilidinin global outbox kilidi testini
yanlışlıkla seri hale getirmesi önlendi. Production davranışı gevşetilmedi.

HW-13 final yerel verify PASS / exit 0, 2026-10-21 18:56:40 +03:
239 test (33 yeni HW-13, toplam HW-04 65 yeni); failure/error/skipped 0.
JaCoCo 1176/1192=%98,66. V1–V9 PostgreSQL/Flyway/JPA PASS; V9 değiştirilmez.
Log logs/hw04-moderation-final-local.log. Dört endpoint docs/api.md HARDWARE
kaydında, DB/ER/mimari güncel. Final full dev + scan dev tüm backend için bekleniyor.

## İlk final kapılar ve Sonar düzeltmeleri

Full dev 257 backend test / %95,98 / 18 HTTP PASS ve SAFE TO COMMIT verdi.
Sonar 19:06:26 +03'te exit 1: A/A/A olmasına rağmen new_violations=3 gate ERROR.
HardwareModerationOutbox lambda'sındaki gereksiz blok ve review testindeki iki
map assertion düzeltildi (java:S1602/S5838). Güvenlik/iş davranışı, eşik veya
exclusion değişmedi. İki kapı değişen kaynak için yeniden çalıştırılır.

İlk lambda düzeltmesi JdbcTemplate query overload belirsizliği oluşturdu;
full dev 19:11:53 +03'te compile FAIL / exit 1. Callback açık RowCallbackHandler
türüyle tanımlandı; scoped verify ve ardından full/scan tekrar çalıştırılacak.

Düzeltme sonrası scoped verify 19:13:07 +03'te exit 0: 239 PASS,
failure/error/skipped 0; JaCoCo 1176/1192=%98,66. Log
logs/hw04-sonar-fixes-verify.log. Tüm backend full dev yeniden başlatıldı;
sonrasında scan dev ile gerçek gate doğrulanacak. Henüz READY değildir.

Nihai full dev 19:16:18 +03'te PASS / exit 0: 18 modül / 257 test, backend
%95,98; Compose ve 18/18 HTTP PASS, SAFE TO COMMIT. Hardware 239 PASS/%98,66.
Nihai scan dev aynı kaynakla başlatıldı; gerçek gate sonucu bekleniyor.

## Dosya ve use-case envanteri

Hardware kapsamında 36 yeni production Java + iki mevcut production değişikliği,
V8/V9 ve dört yeni test + bir mevcut ortak fixture değişikliği: 45 kaynak dosyası.

- HTTP: HardwareReviewController (create/list/mine/detail/update/delete),
  HardwareReviewReportController (create/get), HardwareReviewModerationController
  (detail/apply). Controller yalnız service çağırır; socket peer servlet'ten gelir.
- Request record: CreateHardwareReviewRequest, UpdateHardwareReviewRequest,
  CreateHardwareReviewReportRequest, ModerateHardwareReviewRequest.
- Response record: HardwareReviewResponse, HardwareReviewPageResponse,
  HardwareOwnReviewPageResponse, HardwareReviewReportResponse,
  HardwareReviewModerationDetailResponse, HardwareReviewModerationResponse.
- Service/mapper: HardwareReviewService, HardwareReviewReportService,
  HardwareReviewModerationService, HardwareReviewMapper, HardwareReviewReportMapper.
- Domain: HardwareReview, HardwareReviewReport, HardwareReviewText;
  HardwareReviewStatus/ReportReason/ModerationAction, HardwareReviewEvent,
  HardwareDeliveryUnavailableException.
- Port/adapter: HardwareReviewAudiencePolicy, HardwareReviewModerationAccess,
  UnavailableHardwareReviewAudiencePolicy, SecurityContextHardwareReviewModerationAccess.
- Persistence/config: HardwareReviewRepository, HardwareReviewReportRepository,
  HardwareReviewSearch, HardwareReviewWriteRepository, HardwareModerationOutbox,
  HardwareSchedulingConfiguration.
- Mevcut HardwareSecurityConfiguration typed actor route/permission sınırını,
  HardwareExceptionHandler güvenli outbox-capacity 503 yanıtını ekler.
- Dört yeni test sınıfı kalite kaydındadır. Mevcut PostgresIntegrationSupport
  fixture'ı yeni FK bağımlılıklarını testten önce temizler; immutable audit korunur.

API, DB, ER ve mimari yalnız hardware bölümlerinde güncellendi. Önceki HW-03
progress/teslim raporunun PDA kapanışı kullanıcı bildirimiyle düzeltildi.
Başka servis/runner/ENV/Compose/dependency/gateway kaynağı değiştirilmedi.

## Nihai doğrulama ve duruş

Sonar scan dev 19:19:59 +03'te PASS / exit 0: A/A/A + Quality Gate OK,
overall %92,7 / new %94,7 / duplication %0. Analysis
9e8be6f7-b5c8-49df-b977-12232620fd7d; CE SUCCESS. 19:20:18 +03 API kontrolünde
açık issue/hotspot 0/0 ve new violations 0. Tam backend 257 test PASS;
hardware 239 PASS, 1176/1192=%98,66. Önceki başarısızlıklar kalite kaydında korunur.

[Kalite kaydı](../testing/LTZ-ALP-HW-04-quality.md) ve
[teslim raporu](../reports/LTZ-ALP-HW-04-delivery.md) aynı nihai kaynağı anlatır.
Kök checklist HW-12/HW-13 [x], toplam 15/17; ana HW-04 kutusu [ ] kullanıcı PDA
kapanışını bekler. Auth gerçek bağlantısı DEFERRED_AUTH; Admin/Notification ve
User/Social UGC DEFERRED_CONTRACT / NOT_VERIFIED, açık onaylı. Production-ready değildir.
Commit/push/PDA işlemleri kullanıcıya aittir. HW-05 NOT_STARTED; STOP.
