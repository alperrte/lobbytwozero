# LTZ-ALP-HW-01 — Bilgisayar Profili Oluştur, Düzenle ve Sil

## 2026-10-21 — Hazırlık

- Kullanıcı Alper'in görevlerinde çalışıldığını doğruladı.
- AGENTS.md, CLAUDE.md ve kök master tamamen okundu; teknik kurallar ve mevcut modül incelendi.
- Başlangıç Git durumu: yalnız `?? LTZ_HARDWARE_SERVICE.md`; kullanıcı dosyası korunuyor.
- `LTZ_PDA_Alper.md` bulunamadı; masterdaki yedi alt görev esas alındı.
- Hardware kodu başlangıç iskeletinde; migration klasöründe yalnız `.gitkeep` var.
- Genel docx'te MSSQL/Vite/Java 21 ve hardware içinde performans verisi önerileri var.
  Repo/servis gereksinimleri gereği PostgreSQL 17/Next.js/Java 25 kullanılacak;
  FPS ve ölçümler performance-service sorumluluğunda kalacak.
- `database.md` hardware tablosu öneridir. İlk migration gerçek uygulama kapsamını belirleyecek;
  sonraki görevlerin tabloları oluşturulmayacak.
- PATH java 21 olsa da **Maven Java 25.0.4.1 kullanıyor** (`mvn -v` ile doğrulandı).
- Docker sunucusu sandbox dışındaki salt okunur kontrolde 28.5.1; PostgreSQL 17 imajı mevcut.
- Gerçek `.env` dosyaları okunmadı, Git yazma işlemi yapılmadı.

## HW-01 kısmi uygulama ve kanıt

- `HardwareProfile`: owner UUID REF, ad/tür doğrulaması, MANUAL kaynak, UTC zamanları,
  `@Version`; `DeviceType`: DESKTOP/LAPTOP/OTHER.
- `V1__create_hardware_profiles.sql`: profil tablosu, CHECK'ler ve sahip bazlı sıralama indeksi.
- `HardwareProfileTest`: dokuz başarılı test. Gerçek komut:
  `mvn -B -ntp -f backend/pom.xml -pl services/hardware-service -Dtest=HardwareProfileTest test`.
  Sonuç: exit 0, 9 test, 0 failure/error/skipped. Bu seçili test çalışmasıdır;
  mevcut contextLoads dahil bütün modül henüz çalıştırılmadı.
- İlk sandbox denemesi exit 1: JDK `conf/security/java.security` için AccessDeniedException.
  Aynı komut araç izniyle sandbox dışında çalıştırıldığında PASS oldu.
- `src/test/resources/db/profile-schema-check.sql`: sentetik kayıt, varsayılanlar,
  dış FK olmaması, UTC tipi, indeks, boş/uzun ad, geçersiz tür/kaynak, negatif version,
  NULL owner kontrolleri. İzole `postgres:17-alpine` container'ında V1 ardından
  `psql -U postgres -v ON_ERROR_STOP=1` ile PASS (exit 0):
  `CREATE TABLE`, `CREATE INDEX`, `BEGIN`, `INSERT 0 1`, `DO`, `ROLLBACK`.
  Bu doğrudan SQL smoke kontrolüdür; Flyway/JPA testi olarak sayılmaz.
- Test container'ı `--network none`, portsuz ve `--rm` ile başlatıldı;
  kontrol sonrası durduruldu. Mevcut uygulama verisi ve migration geçmişi okunmadı/değişmedi.
- `git diff --check`: exit 0. Yalnız izlenen diff'i kontrol eder; yeni dosyaların
  derlenme/test sonucu ayrıca yukarıdadır. Git staging/commit/push yapılmadı.
- Endpoint henüz yok; `docs/api.md` içine uygulanmış API kaydı eklenmedi.
- Güncel durum: **APPROVAL_BLOCKED**, 0/7; HW-01 checklist açık kalır.

## Sıradaki adım

Kullanıcının bekleyen güvenlik sınırı ve test bağımlılığı sorularını yanıtlamasından
sonra HW-01 aktör portu, güvenlik zinciri, profil oluşturma HTTP akışı ve
Flyway/JPA/HTTP testleri. Onay gerektiren güvenlik uygulamasına başlanmadı.
Hiçbir alt görev tamamen doğrulanmış değildir; sonraki alt göreve geçilmedi.

## HW-01 tamamlandı — 2026-10-21

Önceki APPROVAL_BLOCKED kaydı tarihsel hazırlık durumudur; kullanıcı güvenlik sınırını
ve test bağımlılıklarını onayladı. Aktör portu, güvenli SecurityContext adapter'ı,
CSRF'yi koruyan/stateless/fail-closed güvenlik zinciri, DTO/mapper/service/repository,
profil oluşturma controller'ı ve ProblemDetail handler eklendi. Üretimde token/cookie
çözücü yok; sahte header veya Basic erişim sağlamaz. Health probe mevcut erişimini korur.

İlk HTTP derlemesi ProblemDetail'ın HttpStatusCode imzası nedeniyle FAIL oldu; düzeltildi.
`mvn -B -ntp -f backend/pom.xml -pl services/hardware-service test`: exit 0,
19 test, 0 failure/error/skipped. PostgreSQL 17.11 üzerinde Flyway V1 ve JPA validate
başarılı. DTO validation, bilinmeyen alan/owner/source atama reddi, anonim/Basic/Bearer/
cookie/header reddi, CSRF ve güvenilmeyen principal testleri PASS. API kaydı güncellendi.
HW-01 [x]; gerçek Auth DEFERRED_AUTH. Sıradaki alt görev HW-02.

## HW-02 uygulama — test bekliyor

Manuel/katalog bileşen ekleme, marka bağımsız genel ürün, opsiyonel ürün-marka ilişkisi,
typed teknik alanlar, quantity sınırı, parent profileVersion ve PESSIMISTIC_WRITE kilidi
eklendi. V1 değiştirilmedi; V2 ayrılan aralıktaki sonraki boş numaradır. Katalog CRUD/arama
yoktur; test seed'i yalnız test sınıfında sentetik kayıtlarla kurulur. Yeni endpoint merkezi
API kataloğuna kaydedildi. HW-02 checkbox test doğrulanana kadar açık.

HW-02 doğrulaması: aynı modül Maven test komutu exit 0; **42 test PASS**, 0 failure/error/skipped.
V1/V2 Flyway ve JPA validate PostgreSQL 17.11 üzerinde PASS. Genel model tekilliği markadan
bağımsızdır; opsiyonel marka ayrı join ilişkisidir. 12 kategori manuel giriş, markasız/markalı
seçim, teknik alanlar, kategori/marka uyuşmazlığı, IDOR, stale version ve rollback test edildi.
HW-02 [x]; sıradaki alt görev HW-03.

HW-03 kodlandı: profil adı/türü PUT, bileşen PUT/DELETE; mevcut kimlik/sahip/source korunur.
Tüm mutasyonlar aynı parent kilidi ve profileVersion sözleşmesini kullanır.
Testler: owner/stale version, çapraz profil bileşeni, invalid replacement rollback ve
iki paralel writer (aynı profileVersion için tek başarı) eklendi. Yeni migration yok.
Merkezi API kaydı yazıldı; test sonucu bekleniyor.

HW-03 doğrulandı: modül Maven `test` exit 0; **47 PASS**, 0 failure/error/skipped.
Paralel iki istek aynı profileVersion ile gerçek PostgreSQL'e yazdığında tam bir 201 ve
bir 409 döndü; tek bileşen ve version=1 kaldı. IDOR, çapraz profil ve rollback PASS.
HW-03 [x]; HW-04 owner-scoped sayfalama kod/test/API kaydı eklendi, doğrulama bekliyor.

HW-04 doğrulandı: modül Maven `test` exit 0; **52 PASS**, 0 failure/error/skipped.
Üç bilgisayar türü, farklı owner, eşit createdAt ile UUID tie-break ve page size sınırları PASS.
HW-04 [x]. HW-05 GET detay, owner kontrolü, bounded component page ve REPEATABLE_READ
snapshot eklendi; null/teknik özellik/IDOR/sayfalama testleri çalıştırılacak.

HW-05 doğrulandı: modül Maven `test` exit 0; **54 PASS**, 0 failure/error/skipped.
Kaydedilmeyen teknik değerler null, boş profil bileşenleri boş; owner ve sayfa sınırları PASS.
HW-05 [x]. HW-15 istatistik DTO/service/aggregation sorguları ve endpoint eklendi.
Kayıt sayısı/adet ayrımı ve yalnız kayıtlı kapasite toplamı; eksik kapasiteli parça sayısı
ayrı döner. Başka owner verisinin dışlanması testi doğrulanacak.

HW-15 doğrulandı: modül Maven `test` exit 0; **55 PASS**, 0 failure/error/skipped.
Owner filtreli DB aggregation, kayıt/adet ayrımı, eksik kapasite ve foreign owner izolasyonu PASS.
HW-15 [x]. HW-16 profil DELETE, güncel sürüm şartı ve CASCADE testleri eklendi.
Veri silen migration yok; yalnız kullanıcının isteğiyle kendi profilini silen use-case vardır.

## Güncel sonuç — 7/7 yerel kabul; QUALITY_BLOCKED

HW-16 doğrulandı: `mvn -B -ntp -f backend/pom.xml -pl services/hardware-service clean verify`
ilk çalışmada 57 PASS ve %97,68 kapsam. Son sınır kontrolünde SECURITY.md §10 gereği
POST/PUT gövdeleri 16 KiB ile sınırlandı; Content-Length yokken de çalışan test eklendi.
Son `clean verify`: **58 PASS**, 0 failure/error/skipped, exit 0;
**320/331 satır = %96,68 JaCoCo** (2026-10-21 00:32:11 +03:00).
HW-16 [x], master 7/17. Final pre-push ve Sonar NOT_RUN; ana task QUALITY_BLOCKED.
Bu kayıt önceki kısmi durumların yerine geçer; eski kayıtlar tarihsel olay günlüğüdür.

## Değişiklik envanteri

Tüm kod `backend/services/hardware-service/` içinde:

- `api/controller`: HardwareProfileController (create/update/list/detail/statistics/delete),
  HardwareComponentController (add/update/remove). Dokuz iş endpoint'i.
- `api/dto/request`: CreateHardwareProfileRequest, UpdateHardwareProfileRequest,
  SaveHardwareComponentRequest, SpecificationsRequest.
- `api/dto/response`: HardwareProfileResponse, HardwareProfilePageResponse,
  HardwareProfileDetailResponse, HardwareComponentResponse, ComponentMutationResponse,
  SpecificationsResponse, ComponentCountResponse, HardwareStatisticsResponse.
- `api/exception/HardwareExceptionHandler`: validation/not-found/conflict/genel ProblemDetail.
- `application/service`: HardwareProfileService, HardwareComponentService, HardwareStatisticsService;
  `application/mapper`: HardwareProfileMapper, HardwareComponentMapper;
  `application/port`: CurrentActor, AuthenticatedActor.
- `domain/entity`: HardwareProfile, HardwareComponent, HardwareSpecifications,
  HardwareProduct, HardwareBrand; `domain/enums`: DeviceType, ComponentCategory;
  `domain/exception`: HardwareNotFoundException, HardwareConflictException.
- `infrastructure/persistence/repository`: dört entity repository'si;
  `infrastructure/security`: HardwareSecurityConfiguration, SecurityContextCurrentActor,
  HardwareRequestBodyLimitFilter. Gerçek Auth provider eklenmedi.
- `src/main/resources/db/migration`: V1/V2. V1 uygulandıktan sonra değiştirilmedi.
- `src/test/java`: iki controller integration suite'i, HardwareProfileTest,
  PostgresIntegrationSupport ve izole DB ile güncellenen contextLoads.
- `src/test/resources/db/profile-schema-check.sql`: önceki izole SQL smoke kanıtı.
- `pom.xml`: yalnız onaylı, parent tarafından yönetilen iki test dependency'si.

Dokümanlar: kök master, docs/api.md (yalnız HARDWARE), database.md/er-diagram.md hardware
gerçek şeması, architecture.md hardware sınırı, folder-structure.md onaylı rapor kökü,
docs-hardware README/progress/decisions/testing. Teslim raporu iki kapı geçince yazılacak.
ENV/Compose/gateway/başka servis kaynakları değiştirilmedi. Git yazma/PDA kapatma yapılmadı.

Kesin sonraki adım: master §8 uyarınca kullanıcı final kalite komutlarını çalıştırmalı
veya ajan için açık izin vermeli. Tam komutlar `.\pre-push\pre-push.cmd hardware-service`
ve `.\sonarqube\sonar.cmd scan dev`; Sonar betiği bütün backend'i tarar.

## Final kalite denemesi — 2026-10-21 00:48 +03:00

Kullanıcı iki kalite komutunu açıkça onayladı. `pre-push.cmd hardware-service`
exit 0: 58 PASS, %96,68, `BACKEND TEST CHECK PASSED`.
`sonar.cmd scan dev` exit 1: `SonarQube Access FAIL / fetch failed`;
backend build/analiz/gate SKIPPED. Gerçek Sonar analizi/rating henüz yok.
Komut/log/zaman kanıtı [kalite raporunda](../testing/LTZ-ALP-HW-01-quality.md).

Teşhis: localhost:5199 erişilemiyor, `ltz-sonar` projesi container'ı bulunamadı.
Diğer uygulamanın kapalı Sonar container'ları değiştirilmedi. Ek `sonar.cmd up dev`
ile mevcut LTZ sunucu/DB altyapısını başlatma ve taramayı tekrarlama onayı soruldu.
Kapsam yalnız aynı ana taskın eksik kalite kapısıdır; yeni ana taska geçilmedi.

Kullanıcı sonraki yanıtta önce token kurulması gerektiğini bildirdi. Token kurulum
sırası repo betiği ve resmi Sonar token belgesiyle kontrol edildi: sunucu UP,
ltz-backend projesi, My Account > Security üzerinden uygun User Token, mevcut yerel
SONAR_TOKEN alanına kullanıcı kaydı. Ek up komutu çalıştırılmadı; secret dosyaları
okunmadı. Token hazır bildirimi ve erişilebilir sunucu sonrasında izinli tarama tekrarlanacak.

## Token kurulduktan sonra Sonar devamı

Kullanıcı token'ı `.env.dev` içine kaydettiğini bildirdi. Önceden izinli scan dev tekrar
başlatıldı; **SonarQube Access aşaması geçti**, token/sunucu erişimi çalışıyor.
Ancak bütün backend testlerinde ilk servisler `localhost:5542 Connection refused`
nedeniyle contextLoads hatası aldı. Hardware testleri kendi Testcontainers DB'sini kullanır.
Salt okunur Docker teşhisinde LTZ Sonar sağlıklı, mevcut `ltz-dev-postgres-1` kapalıydı.
Yalnız bu mevcut geliştirme PostgreSQL container'ı `docker start` ile başlatıldı;
yeni servis/DB tanımı, ENV veya başka servis kaynak değişikliği yapılmadı. İlk denemenin
sonucu ve PostgreSQL hazırken yeniden tarama aşağıya kaydedilecek.

Deneme exit 1: Access PASS, Backend Reports FAIL, Backend Analysis koruma adımı FAIL,
gate/overall SKIPPED. 16 servis contextLoads hatası; hardware 58 PASS. PostgreSQL
başlatıldıktan sonraki testlerde authentication failed görüldü. Kaynak kod incelemesi:
ortak `loadProfileEnv` yalnız SONAR_* alanlarını yükler; gerekli Maven DB ortamı yoktur.
Şablondaki POSTGRES_HOST/PORT container içindir, host testinde HOST_PORT gerekir.
Ortak runner'ın dar/sır maskeleyen düzeltmesi [HW01-QG-01](../decisions/quality-runner-env-proposal.md)
olarak somutlaştırıldı; onay alınmadan kapsam dışı ortak kaynak kodu değiştirilmedi.
Alternatif kullanıcı terminalinde test DB ortamını hazırlamaktır. Ana görev QUALITY_BLOCKED.

HW01-QG-01 kullanıcı tarafından “Dar runner düzeltmesini uygula” ile onaylandı.
Ortak loader'da yalnız Maven test süreci için sınırlı DB anahtarları, host portu
eşlemesi ve fail-fast doğrulaması eklendi. Sonar runner test alt sürecine bu ortamı
geçirir ve DB parolasını maskeler; scanner/Compose ortamına profilden DB secret'ı eklemez.
Sentetik regresyon testleri ve rehber güncellendi. Gerçek ENV veya servis kodu değişmedi.

Ortak runner regresyonları 44 PASS. İlk düzeltilmiş gerçek tarama exit 0:
76 backend testi, Sonar Quality Gate PASS; Security/Reliability/Maintainability A,
overall coverage %84,3 ve duplication %0. Analiz `25c1f882-ab7a-428c-8f08-5b665cb497f5`,
Sonar branch main; yerel Git chore/architecture. Sonar hotspot toplamı 0.
Ek bulgu incelemesinde hardware `HardwareComponentService.resolve` için tek CRITICAL
CODE_SMELL görüldü (bilişsel karmaşıklık 16 > 15); güvenlik açığı olarak raporlanmadı.
Marka doğrulaması `resolveBrandName` metoduna çıkarıldı; API/iş kuralı değişmedi.
Son kod için pre-push ve Sonar yeniden doğrulanacak; henüz READY_FOR_PDA işaretlenmedi.

## Nihai teslim — READY_FOR_PDA — DEFERRED_AUTH

Son kod pre-push exit 0: 58 PASS, %96,69, 2026-10-21 01:13:28 +03:00.
Son kod Sonar exit 0: bütün backend 76 PASS, A/A/A, Quality Gate/overall PASS;
overall coverage %84,3, duplication %0. Analiz
`9be6f62f-5bf5-44f8-9d1d-53ca6d9350fc` (2026-10-21 01:16:26 +03:00),
Sonar main / yerel Git chore/architecture. Son API incelemesinde açık kritik,
vulnerability ve hotspot toplamları 0. Son hardware XML 321/332 satır, %96,69.

[Teslim raporu](../reports/LTZ-ALP-HW-01-delivery.md) oluşturuldu; API/DB/ER/mimari,
kalite ve master checkpoint eşleştirildi. 7/7 alt görev ve final kalite checklist'i [x].
Ana task kutusu kullanıcı PDA kapanışını doğrulamadığı için [ ] kalır. Gerçek Auth
DEFERRED_AUTH; production-ready değildir. Git add/commit/push veya PDA durum değişikliği
yapılmadı. Sonraki ana taska başlanmadı; tek feature commit metni teslim edilip STOP.

## PDA kapanışı — 2026-10-21

Kullanıcı “PDA üzerinden LTZ-ALP-HW-01 taskını kapatıyorum” bildirimiyle kapanışı
onayladı. [Kök master](../../LTZ_HARDWARE_SERVICE.md) ana task kutusu [x], durum
**DONE — DEFERRED_AUTH** olarak güncellendi. Toplam 1/5 ana görev, 7/17 alt görev.

Kullanıcının feature/hardware-service branch'indeki `fc23563` commit'i salt okunur
kontrol edildi: 64 dosya dahil, test edilen sürüme göre kapsam içi fark yok;
çalışma ağacı temiz ve origin takip referansı aynı commit'teydi. Gerçek ENV dosyaları
commit'e dahil değildi. Bu kapanış kaydı yalnız dokümantasyon değişikliğidir;
kod veya önceki pre-push/Sonar sonuçları değiştirilmedi, testler tekrar çalıştırılmadı.

Gerçek Auth entegrasyonu **DEFERRED_AUTH**; production-ready değildir.
PDA ve Git yazma işlemlerini kullanıcı yaptı; ajan PDA'ya erişmedi ve Git yazma
komutu çalıştırmadı. Sonraki ana task için izin verilmedi: **WAITING_USER / STOP**.
