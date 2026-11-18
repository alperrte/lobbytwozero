# TASK 3 / LTZ-ALP-REC-03 — checkpoint

2026-11-15 · Alper · **PARTIAL — 5/7 DONE (desteklenen teknik kapsam)**.

| Kod | Durum | Gerçek teslim / sınır |
| --- | --- | --- |
| REC-06 | PARTIAL | Owner profil + tek oyun değerlendirmesi var; katalog genelinde uygun oyun listesi için Game batch gereksinim sözleşmesi yok |
| REC-07 | DONE | Minimum CPU/GPU/RAM/depolama alanları ayrı değerlendirilir; karşılaştırılamayan alan UNKNOWN |
| REC-08 | DONE | Recommended gereksinimler minimumdan bağımsız değerlendirilir |
| REC-09 | PARTIAL | Güvenli çekimserlik testli; gerçek FPS tahmini için Performance ölçüm bağlamı eksik |
| REC-10 | DONE | Minimum compute alanları karşılanırsa ve bilinen disk/bileşen engeli yoksa koşullu LOW başlangıç önerisi; performans garantisi yok |
| REC-11 | DONE | Yalnız kanıtlanmış RAM/toplam depolama açıkları için yükseltme gerekçeleri |
| REC-12 | DONE | Bildirilmiş socket/DDR eşleşme ve uyuşmazlıkları; BIOS/GPU/güç/form factor eksikliğinde bütün sistem UNKNOWN |

## API ve güvenlik

`POST /api/v1/recommendation/hardware/assessments`: JWT kullanıcısının Hardware owner profilini,
Game minimum/recommended metadata ve hesap/profil/oyun bağlı Performance referansını kullanır.
Kimlik body'den alınmaz. Canonical role, User rızası, Auth ACTIVE grant ve paylaşılan 10/dakika
üretim kotası uygulanır. Yanıt private/no-store; hatalar ProblemDetail. Rıza geri çekilmesi
mevcut TASK 2 geçmişini de temizler. Bu endpoint profil snapshot'ı, run veya analitik yazmaz.

Marka zorunlu değildir; manual model kabul edilir. CPU/GPU yalnız tek ve normalize tam model
eşleşmesinde MEETS olabilir; model adından performans sırası üretilmez. GB/MB birim belirsizliği
ondalık/ikili byte sınırlarıyla ele alınır. Toplam disk boş disk sayılmaz; çoklu GPU VRAM'i
toplanmaz. Bütün sistem için asılsız MEETS üretilmez.

Performance reliable=true yalnız örnek eşiğidir. Çözünürlük, preset, ölçüm kaynağı ve ölçüm anı
profil sürümü yoktur: FPS her zaman INSUFFICIENT_DATA, confidence NONE, tahmin değerleri null.
İstek çözünürlüğü ölçüm bağlamına dönüştürülmez. [Kararlar](../decisions/task-03-decisions.md),
[Game talebi](../integration/dependencies/game-service-requests.md),
[Performance talebi](../integration/dependencies/done/performance-service-requests.md).

## Dosya envanteri

Servisin `src/main/java/com/ltz/recommendation/` köküne göre yeni production dosyaları:

- `api/controller/HardwareAssessmentController.java`
- `api/dto/request/HardwareAssessmentRequest.java`, `api/dto/response/HardwareAssessmentResponse.java`
- `application/port/HardwareAssessmentSource.java`
- `application/service/{HardwareAssessmentService,HardwareRequirementEvaluator,ComponentCompatibilityEvaluator}.java`
- `infrastructure/client/{HttpHardwareAssessmentSource,HardwareAssessmentResponseReader,PerformanceReferenceResponseReader,AssessmentValues}.java`

Mevcut PersonalizedRecommendationService rıza kontrolünü paylaşır; RecommendationHttpClient
yeni resource çağrılarında 404 semantiğini korur; RecommendationServiceTokenClient mevcut
Auth izniyle Performance token'ı alır; güvenlik yapılandırması yeni endpoint'i yetkilendirir.
ProductionAuthSupport test yardımcısı gerçek Auth issuer üzerinden bu token'ı üretir.
Üç yeni test sınıfı: HardwareRequirementEvaluatorTest, ComponentCompatibilityEvaluatorTest,
HardwareAssessmentApiIntegrationTest.

Yeni migration, bağımlılık, ENV/Compose değişikliği yoktur. Hardware/Performance/Game/Auth
üretici kodu değişmedi. DB/ER şemaları aynı kaldı. docs/api.md yeni endpoint'i ve mevcut
Performance tüketimini kaydeder; mimari, integration, checklist, karar/test/sorun/commit
belgeleri ve manuel güvenlik notları güncellendi.

## Doğrulama ve kapanış

**66 PASS / 0 failure / 0 error / 0 skipped** (45 regresyon + 21 yeni test).
Disposable PostgreSQL, mevcut V11/V12 ve gerçek Auth issuer/JWKS kullanıldı. Hardware/Game/
Performance yanıtları sözleşme fixture'larıdır; canlı çok servisli E2E NOT VERIFIED.
Toplam LINE **%97,48**; merkezi ek/değişen kod **%97,71**; Checkstyle/PMD 0 yeni ihlal,
dependency gate PASS. [Komut ve kanıt](../testings/task-03-tests.md).

- [x] Uygulanabilir minimum/recommended, grafik, yükseltme ve uyumluluk kapsamı testli.
- [x] Ownership, rıza geri çekme, kaynak kimliği/sürümü/sayfalama, malformed/timeout/503 testli.
- [x] Checklist, API, sözleşmeler, kararlar ve üçüncü commit taslağı güncel.
- [ ] REC-06 katalog listesi ve REC-09 gerçek FPS tahmini — eksik üretici sözleşmeleri nedeniyle açık.
- [ ] Final SonarQube / legacy pre-push — TASK 5'e ayrıldı; merge-ready iddiası yok.

TASK 2'nin REC-02/03/05 ve REC-16 açıkları kapanmadı. Başlangıç worktree temizdi;
TASK 2 kullanıcı commit'i `25ee6b44` görüldü. Git yazma/kimlik işlemi yapılmadı.

**DUR: TASK 4 başlatılmadı; sonraki açık devam talimatı beklenir.**
