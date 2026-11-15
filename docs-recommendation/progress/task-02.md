# TASK 2 / LTZ-ALP-REC-02 — checkpoint

2026-11-14 · Alper · branch `feature/recommendation-service` · **PARTIAL — 5/9 DONE**.

## Alt gereksinimlerin gerçek durumu

| Kod | Durum | Gerçek teslim / eksik |
| --- | --- | --- |
| REC-01 | DONE (tür sinyaliyle sınırlı) | User'ın gerçek kendi tür tercihleri + kanonik Game discovery; A/B ayrımı, boş sinyal, kısmi sonuç limitations |
| REC-02 | BLOCKED | Library favorileri gerçek alınır ve dışlanır; benzerlik için Game batch tür metadatası yok |
| REC-03 | BLOCKED | Kullanıcının rating batch/paging API'si yok; genel rating kişisel puan yerine kullanılmadı |
| REC-04 | DONE | User favoriteGenres, genre filtreli Game sorguları, bounded genre affinity ve rules-v1 ağırlığı |
| REC-05 | BLOCKED | Library completed gerçek alınır ve dışlanır; analiz Game metadata sözleşmesine bağlı |
| REC-16 | PARTIAL | Genre/platform/size doğrulaması ve stable kayıtlı sonuçlar var; sistem uygunluğu filtresi henüz yok |
| REC-17 | DONE (oyun) | Gerçek filtre üyeliği, GENRE_AFFINITY, contribution/score ve matchedGenres owner'a döner |
| REC-18 | DONE | Owner run/read/history, cursor, 30 gün/500 run, D4 cascade, rıza geri çekmede silme |
| REC-19 | DONE | Owner item feedback GET/PUT, LIKE/NOT_INTERESTED, version conflict, idempotency, concurrency ve kota |

Diğer servislerin eksik sözleşmeleri açık [Game](../integration/dependencies/game-service-requests.md)
ve [Review](../integration/dependencies/review-service-requests.md) talepleridir; sahip Hamza.
Başka servisin koduna veya veritabanına müdahale edilmedi. Sistem uygunluğu analizi burada
uydurulmadı; TASK 3 üzerinde çalışma yapılmadı.

## Gerçek API teslimi

- `POST /api/v1/recommendation/games`
- `GET /api/v1/recommendation/runs`
- `GET /api/v1/recommendation/runs/{runId}`
- `GET /api/v1/recommendation/runs/{runId}/items/{itemId}/feedback`
- `PUT /api/v1/recommendation/runs/{runId}/items/{itemId}/feedback`
- `DELETE /internal/v1/accounts/{accountId}` (yalnız Auth D4; Gateway'e açılmaz)

Tam DTO, yetki, sınır ve hata sözleşmeleri `docs/api.md` Recommendation bölümündedir.
Algoritma seçimi rules-v1 kalır; diğer üç sinyal eksikliği PARTIAL/EMPTY yanıtında bildirilir.

## Güvenlik ve entegrasyon

Gerçek Auth/JWKS üretim sözleşmesi benimsenir: RS256, typ/iss/aud/zaman/kid/sub, kullanıcı
ve servis decoder ayrımı; yerel Basic/form/user fallback yoktur. Kullanıcı kimliği yalnız JWT
sub'dan alınır. Canonical role, User rızası ve Auth account-bound ACTIVE grant her kişisel işlemde
kontrol edilir. Gateway cookie/CSRF modeli değişmedi; servis çerezi kimlik saymaz.
Logout/revocation için yeni introspection ucu uydurulmadı; kısa access TTL + saat toleransı sürer.

Mevcut `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET` aktarımı kullanıcı tarafından açıkça
onaylandı; yalnız `.env.example` yorumu, Recommendation property binding'i ve kendi Compose
environment girdisi değişti. Gerçek ENV okunmadı/değiştirilmedi. Yeni bağımlılık/altyapı yoktur.

User/Game/Library gerçek sözleşmeli RestClient adaptörleri vardır. User/Game kullanıcı token'ını,
Library Auth'tan alınan hesap bağlı servis token'ını görür. Origin/redirect/body/timeout sınırları
uygulanır; en çok 26 doğrudan downstream çağrı ve 500 aday/sinyal penceresi vardır.
Review tüketicisi eksik API'ye karşı yapılmış gibi gösterilmedi. Tam canlı servis E2E NOT VERIFIED.

## Veri ve dosya envanteri

V11 değişmedi. V12 yeni `recommendation_runs`, `recommendation_items`, `recommendation_feedback`
tablolarıdır. Yerel FK/CASCADE ve unique/check/index; owner advisory lock, atomik telemetry+history,
cursor/retention/quota ve idempotent D4 silme uygulanır. Yeni history tabloları JdbcTemplate,
mevcut gözlem entity'si JPA ile aynı transaction'dadır. DEV/production migration/deploy yapılmadı.

Yeni production dosyaları (servisin `src/main/java/com/ltz/recommendation/` köküne göre):

- `api/controller/{PersonalizedRecommendationController,RecommendationAccountErasureController}.java`
- `api/dto/request/{GenerateRecommendationsRequest,RecommendationFeedbackRequest}.java`
- `api/dto/response/RecommendationRunResponse.java`, `api/exception/RecommendationExceptionHandler.java`
- `application/port/{PersonalizationSource,RecommendationHistory}.java`
- `application/service/{PersonalizedRecommendationService,RecommendationRateLimiter}.java`
- `domain/exception/RecommendationRequestException.java`
- `infrastructure/client/{RecommendationHttpClient,RecommendationServiceTokenClient,HttpPersonalizationSource}.java`
- `infrastructure/security/{RecommendationAuthProperties,RecommendationJwksConfiguration,RecommendationJwtValidators,RecommendationSecurityConfiguration,RecommendationBodyLimitFilter}.java`
- `infrastructure/persistence/repository/PostgresRecommendationHistory.java`

Yeni testler: `infrastructure/security/{ProductionAuthSupport,RecommendationApiIntegrationTest,RecommendationJwtContractTest}.java`.
Task 1 kaynakları ve commit taslağı korunur. Başlangıç worktree temizdi; TASK 1 kullanıcının
commit'i `90857ceb` olarak görüldü; bu aşamada Git yazma/kimlik işlemi yapılmadı.
Mevcut dosya değişiklikleri: application.properties, Compose, örnek ENV yorumu, docs/api.md,
architecture/database/ER/migrations, manuel güvenlik notu, merkezi checklist/integration/issues/commit kayıtları.

## Doğrulama ve kapanış

**45 PASS / 0 failure / 0 error / 0 skipped**; gerçek disposable PostgreSQL/Flyway/JPA,
Auth issuer/JWKS, ownership/feedback/rıza/timeout/constraint/cursor/retention/kota testleri.
JaCoCo toplam **%96,83**, merkezi ek/değişen executable kapsam **%97,15**; Checkstyle/PMD 0
ihlal, JAR dependency gate PASS. Compose syntax (interpolation/gerçek ENV olmadan) ve
`git diff --check` PASS. [Tam kanıt](../testings/task-02-tests.md).

- [x] Uygulanabilen kişisel tür önerisi, geçmiş, feedback ve D4 teslimi testli.
- [x] Her endpoint docs/api.md içinde kayıtlı; DB/ER/migration ve sözleşmeler güncel.
- [x] Eksik Game/Review sözleşmeleri owner talebi olarak yazıldı; N+1/DB bypass yok.
- [x] Gerçek sonuçlar, dosya envanteri ve [ikinci commit taslağı](../reports/commit-messages.md) kaydedildi.
- [ ] 9/9 REC tamamlandı — 3 BLOCKED ve 1 PARTIAL olduğu için işaretlenmedi.
- [ ] Gerçek çok servisli E2E / final pre-push / Sonar — bu checkpoint'te yapılmadı.

**DUR: TASK 3'e geçilmedi. Kullanıcıdan sonraki açık devam talimatı beklenir.**
