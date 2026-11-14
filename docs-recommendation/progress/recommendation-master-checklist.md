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

- [ ] **REC-01 — Kişiselleştirilmiş oyun önerileri:** Kullanıcının oyun tercihlerine göre oyun önerileri oluşturulması.

- [ ] **REC-02 — Favorilere dayalı öneriler:** Favori oyunlara benzeyen oyunların önerilmesi.

- [ ] **REC-03 — Puanlara dayalı öneriler:** Kullanıcının oyunlara verdiği puanların önerilerde değerlendirilmesi.

- [ ] **REC-04 — Oyun türü tercihleri:** Aksiyon, RPG, strateji ve benzeri tür tercihlerine göre öneriler.

- [ ] **REC-05 — Tamamlanan oyunlar:** Kullanıcının bitirdiği oyunların öneri sisteminde değerlendirilmesi.

- [ ] **REC-16 — Öneri filtreleme:** Oyun türü, platform, sistem uygunluğu ve ilgili ölçütlerle öneri filtreleme.

- [ ] **REC-17 — Öneri nedenleri:** Kullanıcıya belirli bir oyunun veya oyuncunun neden önerildiğinin açıklanması.

- [ ] **REC-18 — Öneri geçmişi:** Kullanıcının önceki önerilerini görüntüleyebilmesi.

- [ ] **REC-19 — Öneri geri bildirimi:** Kullanıcının önerileri beğenmesi veya ilgilenmediğini belirtmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-03 — Donanıma Göre Oyun ve Yükseltme Tavsiyeleri

**Sorumlu:** Alper
**Servis:** `recommendation-service`
**Faz:** MVP
**İlgili gereksinimler:** REC-06, REC-07, REC-08, REC-09, REC-10, REC-11, REC-12

**Ana task açıklaması:** Oyun gereksinimleri, güvenilir FPS aralığı, grafik ayarı ve donanım uyumluluğu/yükseltme önerileri üret.

**Alt tasklar:**

- [ ] **REC-06 — Donanıma uygun oyunlar:** Kullanıcının bilgisayarında çalışması beklenen oyunların önerilmesi.

- [ ] **REC-07 — Minimum sistem gereksinimi:** Kullanıcı bilgisayarı ile oyunların minimum gereksinimlerinin karşılaştırılması.

- [ ] **REC-08 — Önerilen sistem gereksinimi:** Kullanıcı bilgisayarı ile oyunların önerilen gereksinimlerinin karşılaştırılması.

- [ ] **REC-09 — Tahmini FPS:** Yeterli ve güvenilir performans verisi varsa yaklaşık FPS aralığı sunulması; tahminin kesin ölçüm gibi gösterilmemesi.

- [ ] **REC-10 — Grafik ayarı önerileri:** Kullanıcının donanımına uygun olabilecek grafik ayarı seviyelerinin önerilmesi.

- [ ] **REC-11 — Donanım yükseltme önerileri:** Olası performans darboğazlarına göre yükseltme seçeneklerinin önerilmesi.

- [ ] **REC-12 — Donanım uyumluluk analizi:** Bilgisayar bileşenlerinin birbiriyle uyumunun desteklenen teknik bilgiler üzerinden değerlendirilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-04 — Benzer Oyuncu, Arkadaş ve Etkinlik Önerileri

**Sorumlu:** Alper
**Servis:** `recommendation-service`
**Faz:** MVP
**İlgili gereksinimler:** REC-13, REC-14, REC-15

**Ana task açıklaması:** Kişisel/sosyal gizlilik ve engellemeleri gözeterek oyuncu ve etkinlik önerilerini oluştur.

**Alt tasklar:**

- [ ] **REC-13 — Benzer oyuncu önerileri:** Benzer oyun zevklerine sahip oyuncuların önerilmesi.

- [ ] **REC-14 — Arkadaş önerileri:** İzin verilen oyun tercihleri ve sosyal bilgilere göre arkadaş önerileri.

- [ ] **REC-15 — Etkinlik önerileri:** Kullanıcının ilgilenebileceği oyun etkinliklerinin ve turnuvaların önerilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-05 — Python Algoritmalarıyla LTZ Yardımcısı

**Sorumlu:** Alper
**Servis:** `recommendation-service`
**Faz:** MVP
**İlgili gereksinimler:** REC-22

**Ana task açıklaması:** Yerel LLM çalıştırmadan karar kuralları ve şablonlarla oyun/donanım/platform kullanım desteği ver.

**Alt tasklar:**

- [ ] **REC-22 — Python algoritmalarıyla oyun/donanım/LTZ yardımcısı:** Oyun seçimi, donanım ve LTZ kullanımı konularında **yerel AI/LLM modeli kullanmadan**, Python tabanlı algoritmalar, karar kuralları ve gerektiğinde tanımlı yanıt/akış şablonlarıyla yardım sunulması. Bu yetenek `recommendation-service` kapsamındadır; ayrı bir AI mikroservisi oluşturulmaz.


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

Diğer 20 REC kodu: NOT STARTED; bu checkpoint'te hedef API, production dosyası veya
veri kaynağı tüketimi uygulanmadı. İlgili ana task kaynak tanımları yukarıda aynen durur;
sonuç/test uydurulmadı. TASK 2–5 için ilerleme/test/PR raporu açılmadı.

TASK 1 checkpoint'i tamamlandı. Kullanıcı `devam` yazmadan TASK 2'ye geçilmez.
