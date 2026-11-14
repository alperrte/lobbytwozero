# TASK 1 / LTZ-ALP-REC-01 — checkpoint

Tarih: 2026-11-14 · Sorumlu: Alper · Dal: `feature/recommendation-service`

**Durum: DONE — TASK 1'in temel altyapı kabul kapsamı (REC-20, REC-21, REC-23).**
Bu durum kişisel öneri/feedback ürün akışının veya gerçek Auth/consumer entegrasyonlarının
tamamlandığı anlamına gelmez. TASK 2–5 uygulanmadı; `devam` gelmeden başlanmaz.

## Gereksinim / dosya / kanıt

| Gereksinim | Gerçek teslim | Doğrulama |
| --- | --- | --- |
| REC-20 | `application/service/RuleBasedRecommendationEngine`: rules-v1, dört bounded sinyal, katkı skorları, stable UUID tie-breaker, limit, boş sinyalde boş sonuç | `RuleBasedRecommendationEngineTest`, gerçek motoru kullanan execution ve PostgreSQL testi |
| REC-21 | `application/port/RecommendationAlgorithm`: değişmez input/output ve sürüm; `RecommendationExecutionService`: seçim ve çıktı sınırı doğrulaması | Alternatif algoritma uygulaması aynı servisle çalışır; sürüm uyuşmazlığı/bozuk adapter/eksik dependency testleri |
| REC-23 | `RecommendationAnalytics`, `PostgresRecommendationAnalytics`: gerçek motor gözlemleri, aggregate read, idempotency, retention, privacy minimization | Concurrent 12 teslim/tek kayıt; farklı içerik conflict; constraint/index/schema; 30 günlük aralık ve purge testleri |

REC-23'ün bu tasktaki kabulü, master §5.1 uyarınca **gerçekte gözlenebilen motor analitiği
ve gelecekteki olayların tasarım sınırıdır**. Henüz var olmayan gösterim/tıklama/feedback
sayacı veya kullanıcı memnuniyet ölçümü tamamlandı olarak işaretlenmez.

## Kabul kontrolü

- [x] Minimal motor ve algoritma portu; gereksiz factory/manager/bağımlılık yok.
- [x] Determinizm, 0/1/N aday, eksik sinyal, exact score/reason, sürüm ve limit testleri.
- [x] Çalışma kimliği/sürüm/mikrosaniye UTC bağlamı sonuçla taşınır; kişisel geçmiş saklanmaz.
- [x] Analitik yalnız doğrulanmış çalışma sonucudur; diğer ürün olaylarının yokluğu açık belgelenir.
- [x] DB PK tekilleştirme ve eşzamanlı delivery; conflict, bounded sorgu ve retention.
- [x] Algoritma unavailable ve analitik DB hatasında sahte başarı yok.
- [x] V11; gerçek PostgreSQL 17.11 üzerinde Flyway + Hibernate validate, CHECK/PK/index testleri.
- [x] Başlangıç HTTP yüzeyinde sahte header/cookie 401 ve CSRF 403; JWT/ownership kabulü gibi sunulmaz.
- [x] API, DB, ER, mimari, klasör, migration, sözleşme ve test belgeleri güncel.
- [x] `git diff --check`; gerçek test/coverage/quality kanıtı ve commit taslağı kaydedildi.

## Değişiklik envanteri

Tüm Java yolları `backend/services/recommendation-service/src/` altındadır:

- `main/java/com/ltz/recommendation/application/port/{RecommendationAlgorithm,RecommendationAnalytics}.java`
- `main/java/com/ltz/recommendation/application/service/{RuleBasedRecommendationEngine,RecommendationExecutionService}.java`
- `main/java/com/ltz/recommendation/domain/entity/RecommendationObservation.java`
- `main/java/com/ltz/recommendation/domain/enums/RecommendationOutcome.java`
- `main/java/com/ltz/recommendation/domain/exception/RecommendationUnavailableException.java`
- `main/java/com/ltz/recommendation/infrastructure/persistence/repository/{RecommendationObservationRepository,PostgresRecommendationAnalytics}.java`
- `main/resources/db/migration/V11__create_recommendation_observations.sql`
- `test/java/com/ltz/recommendation/application/service/{RuleBasedRecommendationEngineTest,RecommendationExecutionServiceTest,RecommendationObservationTest}.java`
- `test/java/com/ltz/recommendation/infrastructure/persistence/{DisposableDatabaseInitializer,RecommendationAnalyticsIntegrationTest,RecommendationSecuritySurfaceTest}.java`
- Mevcut `RecommendationServiceApplicationTests.java`: yalnız disposable DB guard annotation'ı eklendi.

Belgeler: `docs/{api,architecture,database,er-diagram,folder-structure}.md`,
`docs/decisions/migrations.md`; bu kökün `decisions/task-01-decisions.md`,
`integration/contracts.md`, `progress/`, `testings/task-01-tests.md`, `reports/` kayıtları.
Başlangıçta izlenmeyen kullanıcı master dosyası değişmedi; mevcut launcher, pom,
application.properties ve başka servisler değişmedi. ENV/Git kimliği değişmedi.

## Testler ve sınırlar

Tam komut, günlük ve son ölçüm: [TASK 1 test kanıtı](../testings/task-01-tests.md).
HTTP endpoint eklenmedi; bu nedenle yeni iş DTO/ProblemDetail API kaydı yoktur.
Auth/Game/Library/Review/Hardware/Performance/User/Social/Event tüketici entegrasyonu yapılmadı;
gerçek mevcut izinler ve eksikler [matriste](../integration/contracts.md) kayıtlıdır.
Başka DB erişimi, mevcut migration değişikliği veya canlı deploy yapılmadı.

Pre-push ve ayrı SonarQube final gate'i master protokolü gereği TASK 5 kapanışındadır;
bu checkpoint `MERGE READY` kararı değildir. TASK 1 izole Maven/coverage/quality kontrolleri ayrıdır.

[Commit title/body](../reports/commit-messages.md) yalnız bu checkpoint'e aittir; gerçek commit yoktur.
Sonraki tasklarda aynı dosya değişirse kullanıcı manuel commit için hunk bazında ayırabilir.

**DUR: kullanıcı `devam` yazmadan TASK 2'ye geçilmez.**
