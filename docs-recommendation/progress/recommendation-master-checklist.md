# Recommendation master checklist

Kaynak: bağlayıcı master v2 §4; görev adları ve açıklamaları aynen korunur.
Durumlar task kabul kapsamındadır; ürünün tüm entegrasyonları tamamlandı anlamına gelmez.

## 4. PDA MASTER CHECKLIST — 5 ana task / 23 alt task

### LTZ-ALP-REC-01 — Kural Tabanlı Öneri Motoru ve Analitik Temeli

**Sorumlu:** Alper
**Servis:** `recommendation-service`
**Faz:** MVP
**İlgili gereksinimler:** REC-20, REC-21, REC-23

**Ana task açıklaması:** Algoritmalar sürümlenebilir, test edilebilir ve ileride gelişmiş Python işlemleri eklenebilir şekilde yapılandırılır.

**Alt tasklar:**

- [x] **REC-20 — Kural tabanlı öneri motoru:** Açık ve test edilebilir karar kurallarıyla çalışan öneri altyapısının kurulması.

- [x] **REC-21 — Gelişmiş öneri algoritmaları:** Mevcut API sözleşmeleri korunarak daha gelişmiş Python algoritmalarının entegre edilebilmesini sağlayan yapı.

- [x] **REC-23 — Öneri analitiği:** Önerilerin kullanımının, geri bildirimlerin ve kalite ölçütlerinin gizlilik kurallarına uygun izlenmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-02 — Kişiselleştirilmiş Oyun Önerileri ve Geri Bildirim

**Sorumlu:** Alper
**Servis:** `recommendation-service`
**Faz:** MVP
**İlgili gereksinimler:** REC-01, REC-02, REC-03, REC-04, REC-05, REC-16, REC-17, REC-18, REC-19

**Ana task açıklaması:** Tür, favori, puan ve bitirilen oyunlardan öneri üret; filtreleme, neden, geçmiş ve geri bildirim sağla.

**Alt tasklar:**

- [x] **REC-01 — Kişiselleştirilmiş oyun önerileri:** Kullanıcının oyun tercihlerine göre oyun önerileri oluşturulması.

- [ ] **REC-02 — Favorilere dayalı öneriler:** Favori oyunlara benzeyen oyunların önerilmesi.

- [ ] **REC-03 — Puanlara dayalı öneriler:** Kullanıcının oyunlara verdiği puanların önerilerde değerlendirilmesi.

- [x] **REC-04 — Oyun türü tercihleri:** Aksiyon, RPG, strateji ve benzeri tür tercihlerine göre öneriler.

- [ ] **REC-05 — Tamamlanan oyunlar:** Kullanıcının bitirdiği oyunların öneri sisteminde değerlendirilmesi.

- [x] **REC-16 — Öneri filtreleme:** Oyun türü, platform, sistem uygunluğu ve ilgili ölçütlerle öneri filtreleme.

- [x] **REC-17 — Öneri nedenleri:** Kullanıcıya belirli bir oyunun veya oyuncunun neden önerildiğinin açıklanması.

- [x] **REC-18 — Öneri geçmişi:** Kullanıcının önceki önerilerini görüntüleyebilmesi.

- [x] **REC-19 — Öneri geri bildirimi:** Kullanıcının önerileri beğenmesi veya ilgilenmediğini belirtmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-03 — Donanıma Göre Oyun ve Yükseltme Tavsiyeleri

**Sorumlu:** Alper
**Servis:** `recommendation-service`
**Faz:** MVP
**İlgili gereksinimler:** REC-06, REC-07, REC-08, REC-09, REC-10, REC-11, REC-12

**Ana task açıklaması:** Oyun gereksinimleri, güvenilir FPS aralığı, grafik ayarı ve donanım uyumluluğu/yükseltme önerileri üret.

**Alt tasklar:**

- [x] **REC-06 — Donanıma uygun oyunlar:** Kullanıcının bilgisayarında çalışması beklenen oyunların önerilmesi.

- [x] **REC-07 — Minimum sistem gereksinimi:** Kullanıcı bilgisayarı ile oyunların minimum gereksinimlerinin karşılaştırılması.

- [x] **REC-08 — Önerilen sistem gereksinimi:** Kullanıcı bilgisayarı ile oyunların önerilen gereksinimlerinin karşılaştırılması.

- [x] **REC-09 — Tahmini FPS:** Yeterli ve güvenilir performans verisi varsa yaklaşık FPS aralığı sunulması; tahminin kesin ölçüm gibi gösterilmemesi.

- [x] **REC-10 — Grafik ayarı önerileri:** Kullanıcının donanımına uygun olabilecek grafik ayarı seviyelerinin önerilmesi.

- [x] **REC-11 — Donanım yükseltme önerileri:** Olası performans darboğazlarına göre yükseltme seçeneklerinin önerilmesi.

- [x] **REC-12 — Donanım uyumluluk analizi:** Bilgisayar bileşenlerinin birbiriyle uyumunun desteklenen teknik bilgiler üzerinden değerlendirilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-04 — Benzer Oyuncu, Arkadaş ve Etkinlik Önerileri

**Sorumlu:** Alper
**Servis:** `recommendation-service`
**Faz:** MVP
**İlgili gereksinimler:** REC-13, REC-14, REC-15

**Ana task açıklaması:** Kişisel/sosyal gizlilik ve engellemeleri gözeterek oyuncu ve etkinlik önerilerini oluştur.

**Alt tasklar:**

- [x] **REC-13 — Benzer oyuncu önerileri:** Benzer oyun zevklerine sahip oyuncuların önerilmesi.

- [x] **REC-14 — Arkadaş önerileri:** İzin verilen oyun tercihleri ve sosyal bilgilere göre arkadaş önerileri.

- [ ] **REC-15 — Etkinlik önerileri:** Kullanıcının ilgilenebileceği oyun etkinliklerinin ve turnuvaların önerilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-05 — Python Algoritmalarıyla LTZ Yardımcısı

**Sorumlu:** Alper
**Servis:** `recommendation-service`
**Faz:** MVP
**İlgili gereksinimler:** REC-22

**Ana task açıklaması:** Yerel LLM çalıştırmadan karar kuralları ve şablonlarla oyun/donanım/platform kullanım desteği ver.

**Alt tasklar:**

- [x] **REC-22 — Python algoritmalarıyla oyun/donanım/LTZ yardımcısı:** Oyun seçimi, donanım ve LTZ kullanımı konularında **yerel AI/LLM modeli kullanmadan**, Python tabanlı algoritmalar, karar kuralları ve gerektiğinde tanımlı yanıt/akış şablonlarıyla yardım sunulması. Bu yetenek `recommendation-service` kapsamındadır; ayrı bir AI mikroservisi oluşturulmaz.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

---


## TASK 1 doğrulama eşlemesi — 2026-11-14

Checkbox'lar ana task kabul kapsamını gösterir. REC-23 için TASK 1 motor çalışma/sonuç
analitiği ve olay sınırları tamamlandı; ürünün feedback/gösterim/tıklama ölçümleri henüz
uygulanmadı ve ölçülmüş gibi raporlanmaz. REC-21 çalışan Python anlamına gelmez.

| Kod | Hedef / gerçek dosya | Veri kaynağı ve bağımlılık | Doğrulama / durum |
| --- | --- | --- | --- |
| REC-20 | RuleBasedRecommendationEngine, RecommendationAlgorithm | Servis içi bounded aday/sinyal DTO; gerçek kaynak toplama bu taskta yok | 0/1/N, deterministik skor/rank/gerekçe/limit/sürüm; DONE (TASK 1) |
| REC-21 | RecommendationAlgorithm, RecommendationExecutionService | Java portu; yeni runtime/ENV yok | İkinci algoritma sözleşme testi ve hatalı adapter reddi; DONE (TASK 1) |
| REC-23 | RecommendationAnalytics, PostgresRecommendationAnalytics, V11 | recommendation_db; kimlik/ham private veri yok | Gerçek PostgreSQL, concurrent idempotency, CHECK/PK/index, retention/privacy; DONE (TASK 1 analitik temeli) |

Ortak kanıt: 29 PASS / 0 skipped; JaCoCo %98,57, yeni kod %100; Checkstyle/PMD 0 ihlal,
JAR dependency gate PASS. [Tam test komutu](../testings/task-01-tests.md),
[dosya ve kabul envanteri](task-01.md), [algoritma/analitik sözleşmesi](../decisions/task-01-decisions.md),
[gerçek servis bağımlılıkları](../integration/contracts.md).

TASK 1 kapanışında diğer 20 REC kodu: NOT STARTED; o checkpoint'te hedef API, production dosyası veya
veri kaynağı tüketimi uygulanmadı. İlgili ana task kaynak tanımları yukarıda aynen durur;
sonuç/test uydurulmadı. TASK 2–5 için ilerleme/test/PR raporu açılmadı.

TASK 1 checkpoint'i tamamlandı. Kullanıcı `devam` yazmadan TASK 2'ye geçilmez.

## TASK 2 güncel doğrulama eşlemesi — 2026-11-14

**PARTIAL — 5/9 DONE, 3 BLOCKED, 1 PARTIAL.** Önceki TASK 1 durumları tarihseldir.
Kaynak görev adı/açıklamaları korunmuştur; x yalnız aşağıdaki uygulanmış kabul kapsamıdır.

| REC | Hedef / gerçek dosya | Kaynak / bağımlılık | Kanıt / durum |
| --- | --- | --- | --- |
| REC-01 | POST games; PersonalizedRecommendationService, HttpPersonalizationSource | User kendi türleri + Game canonical discovery | A/B/empty/limitations; DONE (tür sinyaliyle sınırlı) |
| REC-02 | Production benzerlik algoritması bağlanmadı | Library veri var, Game batch metadata yok | BLOCKED; yalnız dışlama var, [Game talebi](../integration/dependencies/game-service-requests.md) |
| REC-03 | Production kişisel rating tüketicisi yok | Review kişisel rating batch/paging eksik | BLOCKED; [Review talebi](../integration/dependencies/review-service-requests.md) |
| REC-04 | HttpPersonalizationSource + rules-v1 | İzinli User favoriteGenres / Game filtre üyeliği | Gerçek HTTP contract, neden/skor; DONE |
| REC-05 | Production completed benzerlik analizi bağlanmadı | Library completed var, Game metadata batch yok | BLOCKED; dışlama analiz yerine sayılmadı |
| REC-16 | GenerateRecommendationsRequest / Filters | Genre/platform/size + donanım uygunluğu (hardware-compatible) | DONE (2026-11-18); unsupported alan 400, cursor testli |
| REC-17 | RecommendationRunResponse | Gerçek matchedGenres ve katkı; yalnız sahibine | Score/reason, owner GET; DONE (oyun) |
| REC-18 | V12 + PostgresRecommendationHistory | Yalnız recommendation_db; User consent / Auth ACTIVE / D4 | Cursor, BOLA, retention, silme; DONE |
| REC-19 | Feedback GET/PUT + V12 | Gerçek owner item; UNIQUE/version/account lock | Duplicate/concurrent12/409/NOT_INTERESTED/kota; DONE |

45 PASS / 0 skipped; LINE %96,83; merkezi ek/değişen kod %97,15; Checkstyle/PMD 0,
dependency gate PASS, Compose syntax ve whitespace PASS. [Tam checkpoint](task-02.md),
[testler](../testings/task-02-tests.md), [kararlar](../decisions/task-02-decisions.md).

TASK 3–5 üzerinde çalışma yapılmadı. Kullanıcıdan açık devam talimatı gelmeden TASK 3'e geçilmez.

## TASK 3 güncel doğrulama eşlemesi — 2026-11-15

**PARTIAL — 5/7 DONE (desteklenen teknik kapsam), 2 PARTIAL.** TASK 1/2 metinleri tarihsel
checkpoint'lerdir. REC-06/16 donanım filtresi 2026-11-18 itibarıyla kapandı (hardware-compatible).

| REC | Gerçek uygulama | Kaynak / sınır | Durum |
| --- | --- | --- | --- |
| REC-06 | HardwareCompatibleGameController / HardwareCompatibleGameService | Owner Hardware + Game toplu metadata (game-metadata:read); BELOW elenir, UNKNOWN dürüst etiketlenir | DONE (2026-11-18) |
| REC-07 | HardwareRequirementEvaluator minimum | Tam CPU/GPU model eşleşmesi, bounded RAM/disk; eksikte UNKNOWN | DONE |
| REC-08 | HardwareRequirementEvaluator recommended | Minimumdan bağımsız aynı dürüst alan semantiği | DONE |
| REC-09 | PerformanceReferenceResponseReader / Fps | Performance `performance-evidence` (V24 bağlamlı ölçüm) + HardwareFpsEstimator; yalnız NATIVE_RASTER tam bağlamda LOW güvenli geçmiş aralık, aksi halde abstention | DONE (2026-11-18; Performance 174 + Recommendation 85 test PASS) |
| REC-10 | HardwareAssessmentService Graphics | Minimum compute MEETS ve bilinen engel yoksa koşullu LOW; FPS garantisi yok | DONE |
| REC-11 | HardwareRequirementEvaluator upgrades | Kanıtlı RAM/disk açığı; CPU/GPU sıralaması veya satıcı yok | DONE |
| REC-12 | ComponentCompatibilityEvaluator | Socket/DDR bildirim eşleşmesi; BIOS/GPU/güç/form factor eksikse bütün profil UNKNOWN | DONE |

66 PASS; toplam LINE %97,48, merkezi ek/değişen kod %97,71; Checkstyle/PMD ve dependency
gate PASS. Auth issuer/JWKS gerçek, diğer HTTP kaynakları contract fixture; canlı E2E NOT VERIFIED.
[Checkpoint](task-03.md), [test kanıtı](../testings/task-03-tests.md),
[kararlar](../decisions/task-03-decisions.md), [sözleşmeler](../integration/contracts.md).

TASK 4 başlatılmadı. Sonraki açık kullanıcı devam talimatı beklenir.

## TASK 4 güncel doğrulama eşlemesi — 2026-11-15

**BLOCKED — 0/3 DONE.** Yukarıdaki REC-13/14/15 kaynak ad/açıklamaları ve checkbox'ları
değişmedi. Önceki taskların açıkları ve DONE kapsamları korunur.

| REC | Gerçek durum | Eksik kaynak / owner | Güvenle teslim edilen |
| --- | --- | --- | --- |
| REC-13 | DONE (2026-11-18) | — | `GET /api/v1/recommendation/players` (player-affinity-v1, stateless, ortak sinyal çıktısı, 88 Recommendation testi PASS); User aday ucu + Social geçmiş aktarımı (kod/test hazır; `projection-enabled` açılması ENV onayı bekler) |
| REC-14 | DONE (2026-11-18) | — | `GET /api/v1/recommendation/friends` (ortak tür+platform şartı, `excludeFriends=true`, iki yönlü engel User/Social yansımasında); bekleyen istek dışlama kuralı varsayım olarak api.md'de |
| REC-15 | BLOCKED / öneri NOT IMPLEMENTED | Event discovery ve viewer eligibility iş API'si yok (Nisa) | Negatif event probe; sahte event veya başarılı boş öneri endpoint'i yok |

Üç boundary testi özellik filtre/sıralama testinin yerine sayılmaz. Self/friend/iki yönlü
block/private/deleted/disabled ve private/full/cancelled/timezone/ranking senaryoları,
uygulanmış akış ve gerçek owner sözleşmesi olmadığı için NOT IMPLEMENTED kalır.
69 test PASS; toplam LINE %97,48 ve merkezi ek/değişen kod %97,71; Checkstyle/PMD 0 yeni
ihlal ve dependency gate PASS. Coverage önceki taskları kapsar; yeni production kodu yoktur.
[Checkpoint](task-04.md), [test raporu](../testings/task-04-tests.md),
[karar ve owner talepleri](../decisions/task-04-decisions.md).

TASK 5 başlatılmadı; sonraki açık devam talimatı beklenir. Genel sayaç belgelendirilmiş
desteklenen kapsamla 13/23 DONE; kalanlar bu checkpoint'te kapatılmadı.

## TASK 5 ve final doğrulama — 2026-11-15

**REC-22 DONE (1/1); TASK 5 PARTIAL — final gate BLOCKED.** Önceki task metinleri kendi
checkpoint tarihlerinin durumudur. Güncel sayaç **15/23 DONE** (REC-09 2026-11-18) (belgelenmiş kabul kapsamı).

| REC | Gerçek dosya / API | Kaynak ve güvenlik | Kanıt / durum |
| --- | --- | --- | --- |
| REC-22 | POST assistant; RecommendationAssistantService, AssistantEngine, PythonAssistantEngine, assistant_rules.py | GAME tür filtre rehberi, HARDWARE owner assessment durumları, LTZ_USAGE history/feedback rehberi; mevcut JWT/rıza/ACTIVE/kota | Gerçek CPython, 3 konu, kapalı DTO/şablon, negatif process/API testleri; DONE |

80 Java + 7 Python test PASS; LINE %97,51, merkezi ek/değişen kod %97,70; yeni lint ihlali 0,
JAR ve final image dependency gate PASS. Onaylı aynı-servis runtime, non-root ağsız/read-only
packaged smoke testli; ENV/pip/sidecar/migration yok. GAME modu gerçek öneri listesi üretmez;
HARDWARE FPS/uyum garantisi vermez. Canlı çok servis E2E NOT VERIFIED.

- [x] Beş checkpoint, ayrı commit taslakları ve PR title/description hazır.
- [x] REC-22 runtime kararı açık kullanıcı onayıyla uygulandı; gerçek Python testli.
- [ ] 23/23 REC: REC-02/03/05/06/13/14/15/16 hâlâ açık (REC-09 kapandı).
- [ ] Native pre-push: güvenli ENV/DB korumasında BLOCKED; mevcut CI kapısı yerine sayılmadı.
- [ ] Sonar scanner/gate ve A'lar: sunucu/token ve profil politikası nedeniyle BLOCKED/UNKNOWN.
- [ ] 5/5 tam özellik teslimi ve MERGE READY — sağlanmadı.

[TASK 5 checkpoint](task-05.md), [testler](../testings/task-05-tests.md),
[final kalite raporu](../testings/pre-merge-quality.md), [PR taslağı](../reports/pr-description.md).
**NOT READY / BLOCKED — gerçek commit/push/PR/merge yapılmadı. DUR.**
