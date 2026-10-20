# LTZ — Servis İçerikleri | Alper

**Sorumlu geliştirici:** Alper  
**İş alanı:** Donanım Yönetimi, Kişiselleştirilmiş Öneriler ve LTZ Performance (Backend + Web)  
**Servisler:** `hardware-service`, `recommendation-service`, `performance-service`  
**Veritabanları:** `hardware_db`, `recommendation_db`, `performance_db`  
**Web bileşeni:** Ortak Next.js frontend içindeki LTZ Performance modülü  
**Kapsam:** MVP. `ltz-performance-app` masaüstü istemcisi **MVP sonrası** ayrı dosyada planlanmıştır.

Bu belge Alper'e atanacak geliştirme görevlerini içerir. Her `HW-XX`, `REC-XX`, `PERF-XX` ve `PWEB-XX` maddesi ayrı görev olarak takip edilebilir. Backend servisleri Java Spring Boot, PostgreSQL, Flyway, Maven, Docker ve kararlaştırılan `api` / `application` / `domain` / `infrastructure` katmanlı yapısına uygun geliştirilecektir. Web bileşeni mevcut Next.js / TypeScript / Tailwind projesi içindedir. Backend ve web aynı LTZ Performance ürününü oluşturur; web ayrı mikroservis değildir.

---

## 1. Hardware Service

**Servis:** `hardware-service`  
**Veritabanı:** `hardware_db`  
**Amaç:** Kullanıcıların bilgisayar donanımlarını ve bileşenlerini yönetmek, donanım kataloğundan seçim yapabilmesini ve donanım deneyimlerini paylaşmasını sağlamak.

### Görevler

- [ ] **HW-01 — Donanım profili oluşturma:** Kullanıcının kendisine ait bilgisayar profili oluşturabilmesi.
- [ ] **HW-02 — Manuel donanım ekleme ve model/marka seçimi:** Kullanıcının CPU, GPU, RAM, anakart, disk, soğutma ve çevre birimi bilgilerini manuel girebilmesi veya piyasada bilinen modelleri **üretici markası belirtmeden** seçebilmesi. Örneğin ilgili ürün sınıfında önce genel model seçilip, **isteğe bağlı** marka/üretici listesinden Gigabyte gibi marka seçilebilmelidir. Marka seçimi zorunlu olmayacak; genel model seçimi marka bağımsız çalışacaktır. Katalogda bulunmayan donanım için manuel giriş korunacaktır.
- [ ] **HW-03 — Donanım bilgisi güncelleme:** Kullanıcının mevcut donanımını değiştirebilmesi veya yükseltebilmesi.
- [ ] **HW-04 — Birden fazla bilgisayar:** Masaüstü, laptop veya farklı bilgisayarlar için ayrı donanım profilleri oluşturulması.
- [ ] **HW-05 — Donanım detayları:** İşlemci, ekran kartı, RAM, depolama ve diğer teknik bilgilerin görüntülenmesi.
- [ ] **HW-06 — Otomatik donanım tanıma entegrasyonu:** MVP'de, gelecekteki masaüstü istemcisinden gelecek donanım bilgilerinin alınabilmesi için API desteğinin hazırlanması. Otomatik tarama istemcisi MVP kapsamına dahil değildir.
- [ ] **HW-07 — Donanım senkronizasyonu:** Kullanıcı iznine dayalı otomatik cihaz profili güncellemesi için backend altyapısının kurulması; masaüstü üzerinden gerçek senkronizasyon MVP sonrasında devreye alınacaktır.
- [ ] **HW-08 — Donanım gizliliği:** Kullanıcının profilinde hangi donanım alanlarının görüneceğini belirleyebilmesi; görünürlük kurallarının API üzerinde uygulanması.
- [ ] **HW-09 — Donanım karşılaştırma:** Bilgisayar profillerinin veya bileşenlerinin mevcut teknik özellikler üzerinden karşılaştırılması.
- [ ] **HW-10 — Donanım ürün kataloğu:** CPU, GPU, RAM ve diğer bileşenlerin genel model kayıtlarının; varsa bunlarla ilişkilendirilen **opsiyonel marka/üretici** seçeneklerinin yönetilmesi. Genel model bir marka seçilmeden kullanılabilir olmalıdır.
- [ ] **HW-11 — Donanım arama ve filtreleme:** Model, isteğe bağlı marka/üretici ve teknik özelliklere göre donanım arama ve filtreleme.
- [ ] **HW-12 — Donanım yorumları:** Kullanıcıların donanım ürünleri hakkında değerlendirme ve deneyim paylaşabilmesi.
- [ ] **HW-13 — Donanım yorumu moderasyonu:** Uygunsuz donanım yorumlarının raporlanarak ortak `admin-service` içindeki moderasyon sürecine iletilmesi.
- [ ] **HW-14 — Donanım kampanyaları:** Donanım ürünlerine ilişkin kampanya veya indirim bilgilerinin sunulması. Bu kapsam Hardware Service'e aittir; Content Service'in haber odaklı sınırı korunur.
- [ ] **HW-15 — Donanım profil istatistikleri:** Kullanıcının kayıtlı bilgisayar ve donanım profillerine ilişkin özet bilgilerin sunulması.
- [ ] **HW-16 — Donanım profili silme:** Kullanıcının kendi bilgisayar profilini kaldırabilmesi.
- [ ] **HW-17 — Donanım API entegrasyonları:** Performance ve Recommendation servislerinin ihtiyaç duyduğu donanım verilerinin servis sözleşmeleri üzerinden sunulması.

### Önemli mimari kurallar

- Donanım profilleri, bileşen kayıtları, genel model kataloğu ve varsa modele bağlı marka/üretici seçenekleri `hardware-service` tarafından yönetilir.
- **Marka seçimi opsiyoneldir.** Genel bir model, belirli bir kart/ürün üreticisi seçilmeden de profilde kullanılabilir; kullanıcı manuel donanım girişi de yapabilir.
- Kullanıcının eklediği veriler ile gelecekte masaüstü uygulamasından gelecek verilerin kaynağı ayırt edilir; otomatik senkronizasyon kullanıcı iznine bağlıdır.
- Kullanıcı yalnızca kendi donanım profillerini değiştirebilir; gizlilik kuralları API düzeyinde uygulanır.
- FPS, sıcaklık, ölçüm oturumları ve performans geçmişi `hardware-service` içinde tutulmaz; sahibi `performance-service`tir.
- Başka servislerin veritabanlarına doğrudan erişilmez.

### Servis entegrasyonları

| Servis | Bağlantı amacı |
|---|---|
| `auth-service` | Kimlik doğrulama ve yetki kontrolü |
| `user-service` | Kullanıcı profili ve görünürlük tercihleri |
| `game-service` | Oyunların sistem gereksinimleri |
| `performance-service` | Performans kayıtlarının ait olduğu donanım profilleri |
| `recommendation-service` | Donanım uyumluluğu ve tavsiyeler |
| `admin-service` | Donanım yorumlarının moderasyonu |

---

## 2. Recommendation Service

**Servis:** `recommendation-service`  
**Veritabanı:** `recommendation_db`  
**Amaç:** Oyun, donanım, oyuncu ve etkinlik verilerini değerlendirerek kişiselleştirilmiş öneriler üretmek; LTZ kullanımı, oyun ve donanım konusunda Python algoritmalarıyla kural tabanlı yardım sağlamak.

### Görevler

- [ ] **REC-01 — Kişiselleştirilmiş oyun önerileri:** Kullanıcının oyun tercihlerine göre oyun önerileri oluşturulması.
- [ ] **REC-02 — Favorilere dayalı öneriler:** Favori oyunlara benzeyen oyunların önerilmesi.
- [ ] **REC-03 — Puanlara dayalı öneriler:** Kullanıcının oyunlara verdiği puanların önerilerde değerlendirilmesi.
- [ ] **REC-04 — Oyun türü tercihleri:** Aksiyon, RPG, strateji ve benzeri tür tercihlerine göre öneriler.
- [ ] **REC-05 — Tamamlanan oyunlar:** Kullanıcının bitirdiği oyunların öneri sisteminde değerlendirilmesi.
- [ ] **REC-06 — Donanıma uygun oyunlar:** Kullanıcının bilgisayarında çalışması beklenen oyunların önerilmesi.
- [ ] **REC-07 — Minimum sistem gereksinimi:** Kullanıcı bilgisayarı ile oyunların minimum gereksinimlerinin karşılaştırılması.
- [ ] **REC-08 — Önerilen sistem gereksinimi:** Kullanıcı bilgisayarı ile oyunların önerilen gereksinimlerinin karşılaştırılması.
- [ ] **REC-09 — Tahmini FPS:** Yeterli ve güvenilir performans verisi varsa yaklaşık FPS aralığı sunulması; tahminin kesin ölçüm gibi gösterilmemesi.
- [ ] **REC-10 — Grafik ayarı önerileri:** Kullanıcının donanımına uygun olabilecek grafik ayarı seviyelerinin önerilmesi.
- [ ] **REC-11 — Donanım yükseltme önerileri:** Olası performans darboğazlarına göre yükseltme seçeneklerinin önerilmesi.
- [ ] **REC-12 — Donanım uyumluluk analizi:** Bilgisayar bileşenlerinin birbiriyle uyumunun desteklenen teknik bilgiler üzerinden değerlendirilmesi.
- [ ] **REC-13 — Benzer oyuncu önerileri:** Benzer oyun zevklerine sahip oyuncuların önerilmesi.
- [ ] **REC-14 — Arkadaş önerileri:** İzin verilen oyun tercihleri ve sosyal bilgilere göre arkadaş önerileri.
- [ ] **REC-15 — Etkinlik önerileri:** Kullanıcının ilgilenebileceği oyun etkinliklerinin ve turnuvaların önerilmesi.
- [ ] **REC-16 — Öneri filtreleme:** Oyun türü, platform, sistem uygunluğu ve ilgili ölçütlerle öneri filtreleme.
- [ ] **REC-17 — Öneri nedenleri:** Kullanıcıya belirli bir oyunun veya oyuncunun neden önerildiğinin açıklanması.
- [ ] **REC-18 — Öneri geçmişi:** Kullanıcının önceki önerilerini görüntüleyebilmesi.
- [ ] **REC-19 — Öneri geri bildirimi:** Kullanıcının önerileri beğenmesi veya ilgilenmediğini belirtmesi.
- [ ] **REC-20 — Kural tabanlı öneri motoru:** Açık ve test edilebilir karar kurallarıyla çalışan öneri altyapısının kurulması.
- [ ] **REC-21 — Gelişmiş öneri algoritmaları:** Mevcut API sözleşmeleri korunarak daha gelişmiş Python algoritmalarının entegre edilebilmesini sağlayan yapı.
- [ ] **REC-22 — Python algoritmalarıyla oyun/donanım/LTZ yardımcısı:** Oyun seçimi, donanım ve LTZ kullanımı konularında **yerel AI/LLM modeli kullanmadan**, Python tabanlı algoritmalar, karar kuralları ve gerektiğinde tanımlı yanıt/akış şablonlarıyla yardım sunulması. Bu yetenek `recommendation-service` kapsamındadır; ayrı bir AI mikroservisi oluşturulmaz.
- [ ] **REC-23 — Öneri analitiği:** Önerilerin kullanımının, geri bildirimlerin ve kalite ölçütlerinin gizlilik kurallarına uygun izlenmesi.

### Önemli mimari kurallar

- Öneri kurallarının, algoritmalarının ve sonuçlarının sahibi `recommendation-service`tir.
- Oyunlar `game-service`, favori/tamamlanan oyunlar `library-service`, puanlar `review-service`, donanımlar `hardware-service`, performans verileri `performance-service` kaynaklıdır.
- Oyuncu önerilerinde kullanıcı gizliliği ve sosyal engelleme kuralları dikkate alınır.
- FPS tahminleri güvenilir dayanak olmadan kesin sonuç olarak sunulmaz; ölçüm yoksa yokluğu açıklanır.
- **REC-22 için yerel üretken AI modeli/LLM çalıştırılmaz.** Yardım ve yönlendirme Python algoritmalarıyla sağlanır; yetkisiz otomatik işlem yapılamaz.
- Kural tabanlı ve gelişmiş algoritmalar modüler tutulur; başka servislerin veritabanlarına doğrudan erişilmez.

### Servis entegrasyonları

| Servis | Bağlantı amacı |
|---|---|
| `user-service` | Oyuncu tercihleri ve görünürlük |
| `game-service` | Oyun kataloğu ve sistem gereksinimleri |
| `library-service` | Favoriler ve tamamlanan oyunlar |
| `review-service` | Kullanıcı puanları |
| `hardware-service` | Donanım profilleri |
| `performance-service` | FPS ve performans raporları |
| `social-service` | Sosyal ilişkiler ve engelleme kuralları |
| `event-service` | Etkinlik ve turnuva önerileri |

---

## 3. LTZ Performance — Backend

**Servis:** `performance-service`  
**Veritabanı:** `performance_db`  
**Amaç:** Kullanıcının manuel performans raporlarını, oturumlarını, FPS/sensör ölçümü kayıtlarını ve bunların web API'lerini yönetmek; gelecekte masaüstü istemcisiyle senkronizasyona hazır olmak.

### Görevler

- [ ] **PERF-01 — Cihaz bağlantısı:** İleride masaüstü uygulamasından gelecek cihazların LTZ hesabıyla eşleştirilmesi için backend desteği.
- [ ] **PERF-02 — Cihaz yönetimi:** Kullanıcının bağlantılı cihazları listeleyebilmesi ve bağlantılarını kaldırabilmesi için API desteği.
- [ ] **PERF-03 — Performans oturumu oluşturma:** Kullanıcının performans oturumu kaydı oluşturabilmesi.
- [ ] **PERF-04 — Performans oturumu sonlandırma:** Kaydedilmiş performans oturumunun tamamlanması.
- [ ] **PERF-05 — CPU sıcaklık ölçümleri:** Desteklenen kaynaktan gelen CPU sıcaklık ölçümlerinin alınması ve saklanması.
- [ ] **PERF-06 — GPU sıcaklık ölçümleri:** Desteklenen kaynaktan gelen GPU sıcaklık ölçümlerinin alınması ve saklanması.
- [ ] **PERF-07 — CPU kullanım ölçümleri:** İşlemci kullanım oranlarının kaydedilmesi.
- [ ] **PERF-08 — GPU kullanım ölçümleri:** Ekran kartı kullanım oranlarının kaydedilmesi.
- [ ] **PERF-09 — RAM ve VRAM ölçümleri:** Bellek kullanım bilgilerinin saklanması.
- [ ] **PERF-10 — Disk performans verileri:** Desteklenen disk kullanım ve performans ölçümlerinin yönetilmesi.
- [ ] **PERF-11 — Fan hızı verileri:** Desteklenen sensörlerin fan RPM verilerinin saklanması.
- [ ] **PERF-12 — FPS ölçümleri:** Desteklenen kaynaktan gelen FPS ölçümlerinin kaydedilmesi.
- [ ] **PERF-13 — Oyun performans raporu:** Oyun, donanım, çözünürlük ve grafik ayarlarıyla ilişkili performans raporlarının oluşturulması.
- [ ] **PERF-14 — Ölçüm geçmişi:** Kullanıcının geçmiş performans kayıtlarının listelenmesi.
- [ ] **PERF-15 — Performans istatistikleri:** Ortalama, minimum ve maksimum değerlerin hesaplanması.
- [ ] **PERF-16 — Performans grafikleri API'si:** Web modülü için zaman serisi verilerinin sunulması.
- [ ] **PERF-17 — Veri senkronizasyonu:** Gelecekteki masaüstü istemcisinden izinli veri kabulü için backend sözleşmesi ve işleme altyapısı.
- [ ] **PERF-18 — Tekrar gönderim koruması:** Tekrarlanan ölçüm isteklerinin mükerrer kayda yol açmaması.
- [ ] **PERF-19 — Ölçüm filtreleme:** Cihaz, oyun, tarih ve ölçüm türüne göre filtreleme.
- [ ] **PERF-20 — Performans verisi silme:** Kullanıcının kendi ölçüm ve performans raporlarını silebilmesi.
- [ ] **PERF-21 — Servis entegrasyonu:** Hardware ve Recommendation servislerine gerekli verilerin API sözleşmeleriyle sunulması.
- [ ] **PERF-22 — Veri erişim ve gizlilik:** Ölçüm ve performans kayıtlarına yalnızca yetkili kişilerin erişebilmesi.

### MVP / MVP sonrası ayrımı

MVP'de kullanıcı manuel performans raporları oluşturabilir, kendi kayıtlarını ve mevcut ölçümleri web üzerinden görüntüleyebilir. **Web tarayıcısı doğrudan CPU/GPU sıcaklığı veya oyun FPS'si ölçemez.** `PERF-01/02`, `PERF-05–12` ve `PERF-17` gibi maddelerde sunucu tarafı API/veri modeli ve test edilebilir kabul altyapısı MVP'ye dahildir; gerçek zamanlı otomatik sensör akışının masaüstü istemcisiyle uçtan uca kullanımı ise **MVP sonrasında** etkinleşir. Hazır olmayan ölçümler için gerçek veri varmış gibi gösterim yapılmaz.

### Önemli mimari kurallar

- Oturumların, FPS raporlarının ve sensör ölçümlerinin sahibi `performance-service`tir; donanım özelliklerinin sahibi `hardware-service`tir.
- Manuel girilen rapor ile otomatik ölçülen/senkronize edilen verinin kaynağı ayırt edilir.
- Kullanıcı izni olmadan otomatik ölçüm verisi sunucuya aktarılmaz; cihaz erişimi ve kimliği doğrulanır.
- Desteklenmeyen sensörler için ölçüm üretilmez; büyük zaman serileri aralık ve sayfalama üzerinden sunulur.
- Başka servislerin veritabanlarına doğrudan erişilmez.

### Servis entegrasyonları

| Servis | Bağlantı amacı |
|---|---|
| `auth-service` | Kimlik doğrulama ve cihaz yetkilendirme |
| `hardware-service` | Donanım profili ve cihaz bilgileri |
| `game-service` | Oyun kimlikleri ve detayları |
| `recommendation-service` | Gerçek performans verilerinin önerilerde kullanımı |

---

## 4. LTZ Performance — Web

**Bileşen:** Mevcut Next.js frontend içindeki `performance` modülü (**mikroservis değildir**)  
**Amaç:** Kullanıcının donanımını, performans raporlarını, ölçüm geçmişini ve önerilerini LTZ web arayüzünde görüntülemesini sağlamak.

### Görevler

- [ ] **PWEB-01 — Performance ana sayfası:** LTZ Performance özelliklerinin anlatıldığı web sayfası.
- [ ] **PWEB-02 — Masaüstü uygulaması indirme alanı:** Masaüstü sürümü yayımlandığında kurulum dosyası bağlantısının sunulacağı alan. **MVP'de gerçek indirme bağlantısı aktif olmayacak**; yayımlanmamış dosyaya yönlendirme yapılmayacak.
- [ ] **PWEB-03 — Cihaz yönetimi:** Kullanıcının hesabına bağlı cihazları görüntüleyebileceği alan; masaüstü bağlantısı yokken uygun boş durum gösterilmesi.
- [ ] **PWEB-04 — Donanım paneli:** CPU, GPU, RAM ve diğer kayıtlı donanım özelliklerinin görüntülenmesi.
- [ ] **PWEB-05 — Performans geçmişi:** Kayıtlı performans oturumlarının listelenmesi.
- [ ] **PWEB-06 — Performans grafikleri:** Mevcut FPS, sıcaklık ve kullanım ölçümlerinin grafiklerle gösterilmesi; veri olmadığında boş durumun doğru sunulması.
- [ ] **PWEB-07 — Oyun performans raporları:** Kullanıcının oyun bazlı manuel/kayıtlı performans sonuçlarını görüntülemesi.
- [ ] **PWEB-08 — Donanım uyumluluğu:** Kullanıcı bilgisayarı ile oyun sistem gereksinimlerinin karşılaştırılması.
- [ ] **PWEB-09 — Kişiselleştirilmiş öneriler:** Oyun ve donanım önerilerinin görüntülenmesi.
- [ ] **PWEB-10 — Gizlilik ve veri yönetimi:** Kullanıcının saklanan veya ileride senkronize edilecek performans verilerini görüntüleme ve silme işlemleri.

### Önemli mimari kurallar

- Web modülü LTZ'nin mevcut Next.js projesinin parçasıdır; ayrı backend veya veritabanı açılmaz.
- Backend çağrıları API Gateway üzerinden ilgili servislerin API'lerine yönlendirilir.
- Web tarayıcısından doğrudan CPU/GPU sensör okuması beklenmez; mevcut kayıtlar gösterilir.
- Masaüstü uygulaması bulunmadan manuel donanım ve performans raporu akışları çalışır.
- Masaüstü kurulum dosyası MVP sonrasında yayımlandığında indirme bağlantısı etkinleştirilir.

---

## 5. Ortak teslim kriterleri

- [ ] Üç Spring Boot mikroservisi kendi PostgreSQL veritabanıyla çalışır; şema değişiklikleri Flyway ile yönetilir.
- [ ] API / Application / Domain / Infrastructure katmanları, request/response DTO'lar, hata yönetimi ve validasyon proje mimarisiyle uyumludur.
- [ ] Donanım modeli/opsiyonel marka seçimi, manuel giriş, profil yönetimi, öneriler, manuel performans raporları ve web gösterimleri uygun unit/integration/UI testleriyle doğrulanır.
- [ ] `REC-22` yerel AI modeli çalıştırmadan Python algoritmalarıyla uygulanır ve servis sözleşmesi korunur.
- [ ] Masaüstü uygulaması MVP'ye dahil değildir; masaüstü bağlantılı backend uçları test edilebilir hazırlanır, otomatik ölçümün uçtan uca çalıştığı iddia edilmez.
- [ ] Mikroservisler başka servislerin veritabanına bağlanmaz; gerekli HTTP iletişiminde Spring RestClient, çok adımlı dağıtık işlemlerde kararlaştırılan Saga yaklaşımı kullanılır.
- [ ] Kimlik doğrulama, veri sahipliği ve gizlilik kontrolleri proje `SECURITY.md` gerekliliklerine uygun uygulanır.
- [ ] SonarQube Security, Reliability ve Maintainability alanlarında en az **A** hedeflenir.

**Görev sayısı:** 17 HW + 23 REC + 22 PERF + 10 PWEB = **72 numaralı MVP görevi**. `PWEB-02` masaüstü uygulaması yayımlanana kadar pasif/duyuru durumunda kalır. Otomatik sensör verisini üretecek masaüstü istemcisi ayrıca MVP sonrasında geliştirilecektir.

> `SECURITY.md` içeriği bu dosya hazırlanırken doğrulanamadığından yalnızca uyum yükümlülüğü belirtilmiştir; içeriğinde bulunmayan güvenlik kuralları o dosyaya atfedilmemiştir. Desktop geliştirmeleri için ayrı `LTZ_Performance_Desktop_Tasks.md` dosyasına bakınız.
