# LTZ-ALP-HW-04 — Donanım Yorumları ve Moderasyon

Tarih 2026-10-21. Sorumlu Alper.
Durum **READY_FOR_PDA — DEFERRED_AUTH / DEFERRED_CONTRACT**.
HW-12 ve HW-13 sırayla uygulandı; 2/2 alt görev, toplam 15/17.
İki nihai kalite kapısı PASS. HW-04 ana kutusu kullanıcı PDA kapanışı gelene
kadar `[ ]`; daha önce kapanışı doğrulanan ana görev sayısı 3/5. HW-05 başlamadı.

## Kabul kapsamı ve onaylar

| Alt görev | Yerel sonuç | Kanıt |
|---|---|---|
| HW-12 donanım yorumları | DONE; altı endpoint, owner/version/validation/tekillik/görünürlük | V8, 32 yeni senaryo, ilk alt görev verify 206 PASS |
| HW-13 yorum moderasyonu | DONE; iki rapor + iki kaynak moderasyon endpoint'i, audit/outbox | V9, 33 yeni senaryo, final hardware 239 PASS |

Kullanıcı HW-03 PDA kapanışını ve HW-04 geçişini bildirdi; başlangıç HEAD 104524a
ve temiz çalışma ağacı doğrulandı. Yeni yorum/moderasyon modeli ve Auth dışındaki
güvenli ertelemeler ayrı ayrı açıkça onaylandı:
[karar kaydı](../decisions/HW04-review-moderation-proposal.md).
Auth'un önceki DEFERRED_AUTH onayı ve fail-closed sınırı korunur.
LTZ_PDA_Alper.md repo içinde yoktur; kök master 5/17 checklist'i temel alınır.

## Çalışan yerel davranış

Yorum yalnız ACTIVE genel katalog ürününe yazılır. Zorunlu body 1–5000 karakter
düz metin, rating opsiyonel 1–5 tam sayıdır. Ham ve NFKC sonrası HTML açı ayraçları
ve zararlı kontrol karakterleri reddedilir; CRLF, satır sonu ve tab tutarlı işlenir.
UGC HTML olarak işlenmez ve içerdiği URL'ye istek yapılmaz.

Her ürün/yazar için tek silinmemiş yorum vardır; HIDDEN kayıt da tekilliğe katılır.
Aynı yazarın başka ürünlere normalize metin kopyalaması SHA-256 fingerprint ve
partial unique indeksle önlenir. Owner yalnız VISIBLE yorumunu düzenler;
HIDDEN metni değiştirerek moderasyonu aşamaz. Silme soft-delete uygular:
DELETED/deletedAt, body/rating/hash null ve artan version. Silinmiş yorum geri
açılamaz; yeni yorum yazılabilir. Arşivli ürüne create/update ve genel okuma yoktur;
owner kendi geçmişini mine/detail üzerinden okuyabilir ve silebilir.

Kimlik yalnız doğrulanmış typed actor sınırından gelir; request userId/rol kabul
edilmez. HTTP yanıtları no-store/private, mutasyonlar CSRF korumalı, gövdeler 16 KiB
ile sınırlıdır. Genel DTO author/reporter UUID, ad/e-posta, IP, hash veya rapor
sayısını sızdırmaz. ownedByActor yalnız mevcut aktörle karşılaştırma sonucudur.

Non-owner UGC okuması ayrı HardwareReviewAudiencePolicy kararını ve iki yönlü
engel filtresini gerektirir. Karar null/hata/bilinmiyor ise diğer yazarlar deny;
owner kendi verisine erişir. Üretim adapter'ı deny eder, izin veren policy yalnız
testtedir. En fazla 1000 UUID içeren savunmalı kopyalanmış engel seti kullanılır.
Filtre DB sayfalaması, toplam ve AVG'den önce uygulanır; gizli yorumların sayısı
ve puanı sızmaz. Puanı olmayanlar ortalamaya girmez, hiç puan yoksa average null.
Sayfa 0–10000, size 1–100, createdAt/id ASC; okumalar REPEATABLE_READ snapshot'tır.

Rapor yalnız okunabilen, aktif ürüne bağlı VISIBLE yabancı yoruma verilir.
Self-report 400; reason altı enum değeri, details opsiyonel 1–500 düz metindir.
Review/reporter DB tekilliği tekrar raporu 409 yapar; ikinci audit/event oluşmaz.
Rapor detayı reporter-only; yabancı/olmayan kimlik aynı 404 döner.

Kaynak HIDE/RESTORE işlemi hem HTTP hem service sınırında typed actor ve
hardware:reviews:moderate uygulama izni ister. Katalog yazma izni veya ROLE_ADMIN
tek başına yeterli değildir; gerçek rol/claim eşlemesi uygulanmadı. Komut güncel
reviewVersion ve 1–500 karakter gerekçe ister. HIDE yalnız VISIBLE, RESTORE yalnız
HIDDEN + aktif ürün; DELETED geri getirilemez. Kaynak servis merkezi Admin case,
queue, hesap veya rol yönetimi kurmaz. Yetkili detail raporları sayfalı döndürür;
25 rapor testinde üç ORM statement + tek bounded JDBC status sorgusu, N+1 yoktur.

Normal başarılı yazma/raporlar actor 20/dakika, moderasyon actor 60/dakika ve ortak
socket peer 120/dakika DB fixed-minute bucket'larıyla sınırlanır. Sayaçlar iş
transaction'ıyla atomiktir; rollback olmuş denemeler kotayı tüketmez. Ham IP
saklanmaz/loglanmaz; yalnız hash bucket'ı, iki dakikadan eski kayıtların dakikalık
temizliği vardır. X-Forwarded-For güven kaynağı değildir; gerçek proxy/client-IP
zinciri ayrıca NOT_VERIFIED kalır.

## Atomik outbox ve teslim gerçeği

Rapor ContentReported v1, moderasyon HardwareReviewModerated v1 olayı üretir.
UUID/type/version/UTC/producer/aggregateId zarfı ve asgari kimlik/enum payload'ı
yerel öneridir; gerçek consumer kontratı sayılmaz. UGC metni/ham IP event veya
audit'e kopyalanmaz. Moderasyon gerekçesi append-only audit'te tutulur.

İş değişikliği, immutable audit ve PENDING event aynı transaction'dadır.
Olay 4 KiB, toplam PENDING/FAILED bütçesi 10.000; transaction advisory lock
farklı actor/peer/hedefler arasındaki kapasite yarışını korur. Doluysa güvenli
503 MODERATION_DELIVERY_UNAVAILABLE ile hedef değişikliği, audit ve quota rollback
olur. Review kilidi + @Version eski sürüm ve owner/moderasyon yarışlarını korur.

Relay/broker, uydurma servis URL'si veya service-token modeli eklenmedi.
Üretim yazıcısı yalnız PENDING üretir; gerçek rapor/bildirim gönderilmez.
DTO PENDING için NOT_DELIVERED; yerel SENT/FAILED bile NOT_VERIFIED, event purge
edilmişse NOT_AVAILABLE/NOT_VERIFIED döndürür. ON DELETE SET NULL raporu korur.
SENT/purge yalnız test SQL fixture'ında sınandı; gerçek delivery veya retention
uygulaması değildir. Consumer/retention mutabakatı ve gerçek E2E sonraki talimatı bekler.

## Yeni API envanteri

Toplam 41 business endpoint'in bu ana görevde eklenen onu aşağıdadır.
Hepsi `/api/v1/hardware` tam prefix'ini karşılar; anonim business uç yoktur.

| Method | Tam path | Erişim / request → response |
|---|---|---|
| POST | /api/v1/hardware/catalog/products/{productId}/reviews | Actor + CSRF; CreateHardwareReviewRequest → 201 HardwareReviewResponse |
| GET | /api/v1/hardware/catalog/products/{productId}/reviews | Actor + audience/engel; page,size → HardwareReviewPageResponse |
| GET | /api/v1/hardware/reviews/mine | Owner; page,size → HardwareOwnReviewPageResponse |
| GET | /api/v1/hardware/reviews/{reviewId} | Owner veya görünür hedef için audience → HardwareReviewResponse |
| PUT | /api/v1/hardware/reviews/{reviewId} | Owner + CSRF; UpdateHardwareReviewRequest → HardwareReviewResponse |
| DELETE | /api/v1/hardware/reviews/{reviewId} | Owner + CSRF; reviewVersion query → 204 |
| POST | /api/v1/hardware/reviews/{reviewId}/reports | Actor + audience + CSRF; CreateHardwareReviewReportRequest → 201 HardwareReviewReportResponse |
| GET | /api/v1/hardware/review-reports/{reportId} | Reporter-only → HardwareReviewReportResponse |
| GET | /api/v1/hardware/moderation/reviews/{reviewId} | Moderator permission; page,size → HardwareReviewModerationDetailResponse |
| POST | /api/v1/hardware/moderation/reviews/{reviewId}/actions | Moderator + CSRF; ModerateHardwareReviewRequest → HardwareReviewModerationResponse |

Detaylı parametre/JSON/HTTP/test/bağımlılık kayıtları kanonik
[docs/api.md HARDWARE](../../docs/api.md#hardware) bölümündedir.
Hatalar güvenli ProblemDetail: 400 validation/self-report; 401 actor yok;
403 principal/permission/CSRF; 404 sahiplik/görünürlük/kaynak; 409 sürüm/tekillik/
transition; 413 gövde limiti; 429 RATE_LIMITED; 503 outbox kapasitesi.

## Veri modeli ve migration

| Dosya | Gerçek ekleme |
|---|---|
| V8__create_hardware_reviews_and_write_limits.sql | hardware_reviews, hardware_review_audit, hardware_write_limits; rating/status/deleted içerik CHECK; partial unique ve sayfa/TTL indeksleri; append-only UPDATE/DELETE/TRUNCATE trigger |
| V9__create_hardware_review_reports_and_outbox.sql | hardware_review_reports, ortak biçimde outbox_events; report tekillikleri ve event FK; event type/version/status/payload CHECK; audit action CHECK genişletmesi |

Product→review ve review→report servis içi gerçek FK; outbox bağlantısı nullable
FK ON DELETE SET NULL. User/reporter/actor UUID'leri yalnız dış REF, cross-DB FK
yoktur. UUID PK, UTC timestamptz, aggregate/entity version bigint kullanılır.
Teknik bucket composite key ve immutable audit kendi lifecycle'ına sahiptir.
V1–V9 izole PostgreSQL 17.11 + gerçek Flyway + JPA ddl-auto=validate PASS.
Mevcut migration'lar yeniden yazılmadı; test edilmiş V8/V9 da artık değiştirilmez.
V8/V9 canlı DB'ye uygulanmadı. Sonraki boş hardware sürümü V10'dur; HW-05 başlamadı.

## Dosya ve katman envanteri

Hardware kapsamında **45 kaynak dosyası**: 36 yeni production Java, iki mevcut
production Java değişikliği, iki migration, dört yeni test ve bir mevcut test fixture.
Dosya adları/use-case listesi [ilerleme kaydında](../progress/LTZ-ALP-HW-04.md#dosya-ve-use-case-envanteri).

- Üç controller; dört request ve altı response record.
- HardwareReviewService (create/update/delete/list/detail/mine),
  HardwareReviewReportService (create/get), HardwareReviewModerationService
  (detail/apply); HardwareReviewMapper ve HardwareReviewReportMapper.
- HardwareReview/HardwareReviewReport entity'leri, HardwareReviewText invariant'ları,
  üç enum, HardwareReviewEvent ve HardwareDeliveryUnavailableException.
- HardwareReviewAudiencePolicy + HardwareReviewModerationAccess portları ve
  fail-closed/security-context adapter'ları.
- İki JPA repository, HardwareReviewSearch, HardwareReviewWriteRepository,
  HardwareModerationOutbox ve HardwareSchedulingConfiguration.
- Mevcut HardwareSecurityConfiguration route/permission sınırı ile
  HardwareExceptionHandler kapasite için güvenli 503 eşlemesi.
- PostgresIntegrationSupport yeni FK test fixture temizliği; immutable audit silinmez.

Controller repository/client çağırmaz, domain HTTP/altyapı DTO'su import etmez.
API/DB/ER/mimari yalnız hardware bölümlerinde güncellendi; docs-hardware karar,
progress, quality, teslim ve kök checklist güncel. HW-03 kapanış raporları kullanıcı
bildirimiyle tamamlandı. Başka servis, gateway, ENV, Compose, dependency veya runner
kaynağı değiştirilmedi. Git staging/commit/push, deploy veya PDA işlemi yapılmadı.

## Nihai test ve kalite kanıtı

| Kontrol | Sonuç |
|---|---|
| Yeni HW-12 testleri | HardwareReviewControllerTest 25 + HardwareReviewTextTest 7 = 32 PASS |
| Yeni HW-13 testleri | HardwareReviewModerationControllerTest 32 + SecurityContextHardwareReviewModerationAccessTest 1 = 33 PASS |
| Hardware toplamı | 239 PASS; failure/error/skipped 0; 65 yeni, 174 regresyon |
| Hardware JaCoCo | 1176/1192=%98,66; exclusion yok |
| Tam backend | 18 modül / 257 test PASS, yerel ağırlıklı line coverage %95,98 |
| Pre-push full dev | PASS / exit 0; SAFE TO COMMIT; Git/secret filename/whitespace/build/tests/coverage PASS |
| Compose / HTTP | Configuration PASS, 18/18 HTTP health PASS |
| Sonar scan dev | PASS / exit 0; SONAR SCAN PASSED; A/A/A |
| Sonar kalite kapısı | Sonar way VERIFIED; Quality Gate OK/PASS, new violations 0 |
| Sonar coverage / duplication | Overall %92,7 / new %94,7; overall/new duplication %0 |
| Açık issue / hotspot | 0 / 0; gerçek sunucu API'siyle doğrulandı |

Komutlar `.\pre-push\pre-push.cmd full dev` ve `.\sonarqube\sonar.cmd scan dev`;
ikisi de kullanıcının tüm servisler için verdiği açık izinle çalıştırıldı.
Nihai pre-push log kapanışı 19:16:18 +03; Sonar kapanışı 19:19:59 +03.
Proje ltz-backend; yerel branch feature/hardware-service, runner ayrı Sonar branch
parametresi vermediği için sunucuda main. Analysis
9e8be6f7-b5c8-49df-b977-12232620fd7d (19:19:35 +03), CE task
2cccba9a-4ebf-497f-91ef-bbeca0fe9ac9 SUCCESS (19:19:57 +03).
19:20:18 +03 API sorgusu bu analysisId için gate OK ve issue/hotspot 0/0 doğruladı.
Sonar revision 104524a mevcut HEAD'dir; HW-04 çalışma ağacı değişikliklerini kapsar,
yeni commit SHA'sı değildir. Log adları ve ilk Sonar/compile/fixture başarısızlıkları
[kalite kaydında](../testing/LTZ-ALP-HW-04-quality.md) korunur.

Testler IDOR/izin/CSRF/mass assignment, NFKC validation, arşivli/gizli/silinmiş
kaynak, filtreli AVG/sayfalama, DB tekillikleri ve eşzamanlı create/report/moderation,
tam outbox rollback/global kapasite yarışı, limit/TTL/immutable audit ve sınırlı
query sayısını kapsar. Güvenlik/eşik gevşetmesi veya test skip/exclusion yapılmadı.
HTTP health mevcut image'ları kontrol eder; yeni kaynakların deploy/E2E kanıtı
değildir. Yeni kod host Maven/MockMvc/izole PostgreSQL ile sınandı.
Frontend repo içinde yoktur; frontend kontrolleri uygulanmadı.

## Açık kalan gerçek entegrasyonlar

| Bağımlılık | Gerçek durum / sınır | Sonraki doğrulama |
|---|---|---|
| Auth — Kerem | DEFERRED_AUTH / NOT_VERIFIED; production fail-closed | Issuer/audience/JWKS/claim, actor ve permission eşlemesi; gerçek token/CSRF E2E |
| User/Social — Kerem/Umut | DEFERRED_CONTRACT / NOT_VERIFIED; production non-owner UGC deny | Güvenilir global UGC görünürlüğü, iki yönlü block kontratı ve adapter/error testleri |
| Admin — ortak, sorumluluk henüz atanmadı | Yerel ContentReported PENDING; kabul/case E2E DEFERRED_CONTRACT | Sahip ve API/event/güven/receipt/idempotency kontratı, gerçek kabul doğrulaması |
| Notification — ortak, sorumluluk henüz atanmadı | Yerel owner notice PENDING; teslim E2E DEFERRED_CONTRACT | Sahip ve notice/relay/receipt/retention kontratı, gerçek delivery doğrulaması |
| Performance/Recommendation — Alper | HW-03 owner batch DTO TESTED; consumer E2E NOT_VERIFIED | Önceki onaylı erteleme korunur; gerçek consumer bağlantısı bu görevde yapılmadı |

Gerçek Auth entegrasyonu doğrulanmadı; servis production-ready değildir.
Kullanıcının açık erteleme onayı sayesinde yerel işlevler ve iki kalite kapısı
tamamlanan bu ana görev PDA teslimine hazırdır. Outbox tüketicisi gelmezse 10.000
bekleyen kayıt sonrası rapor/moderasyon 503 ile güvenli kapanır; teslim garantisi yoktur.
Yeni gerçek adapter, consumer/retention politikası veya HW-05 ancak sonraki açık
kullanıcı talimatıyla ele alınır.

## Kullanıcı aksiyonu ve duruş

HW-04 alt görevleri, pre-push ve SonarQube kalite kapıları doğrulandı.
Kullanıcı PDA üzerinden LTZ-ALP-HW-04'ü kapatabilir; commit/push işlemleri kullanıcıya
aittir. Ana checkbox kapanış bildirimi gelene kadar boş kalır. HW-05 NOT_STARTED.
Tek feature mesajı sunulur ve STOP yapılır; final servis raporu yalnız HW-05 sonunda yazılır.
