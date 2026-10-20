# LTZ — Servis İçerikleri | Nisa

**Sorumlu geliştirici:** Nisa  
**İş alanı:** Topluluk, Haber İçerikleri ve Etkinlik Sistemleri  
**Servisler:** `community-service`, `content-service`, `event-service`  
**Veritabanları:** `community_db`, `content_db`, `event_db`

Bu belge, Nisa'ya atanacak geliştirme görevlerini içerir. Her `COM-XX`, `CONT-XX` ve `EVT-XX` maddesi ayrı bir görev olarak takip edilebilir. Tüm servisler Java Spring Boot, PostgreSQL, Flyway, Maven, Docker ve üzerinde anlaşılan `api` / `application` / `domain` / `infrastructure` katmanlı yapısına uygun geliştirilecektir.

---

## 1. Community Service

**Servis:** `community-service`  
**Veritabanı:** `community_db`  
**Amaç:** LTZ oyuncularının topluluk gönderileri paylaşmasını, yorum ve beğeni yoluyla etkileşime girmesini, gönderileri asıl sahibine atıf yaparak repost etmesini ve oyunlarla ilgili topluluk tartışmalarını yönetmek.

### Görevler

- [ ] **COM-01 — Topluluk gönderisi oluşturma:** Kayıtlı oyuncunun kendi topluluk gönderisini yayımlayabilmesi; gönderinin sahibi ve oluşturulma zamanı ile kaydedilmesi.
- [ ] **COM-02 — Gönderi düzenleme:** Kullanıcının yalnızca kendisine ait gönderinin içeriğini değiştirebilmesi.
- [ ] **COM-03 — Gönderi silme:** Kullanıcının kendi gönderisini silebilmesi; ilgili yorum ve etkileşimlerin tutarlılığının korunması.
- [ ] **COM-04 — Gönderi listeleme:** Topluluk gönderilerinin sayfalı bir akışta görüntülenebilmesi.
- [ ] **COM-05 — Gönderi detayları:** Bir gönderinin içeriği, sahibi, tarihi, yorumları ve etkileşimlerinin görüntülenebilmesi.
- [ ] **COM-06 — Gönderi beğenileri:** Kullanıcıların gönderileri beğenebilmesi ve beğenilerini geri alabilmesi; aynı kullanıcıdan mükerrer beğeni kaydı oluşmaması.
- [ ] **COM-07 — Gönderi yorumları:** Kullanıcıların gönderilere yorum yazabilmesi.
- [ ] **COM-08 — Yorum düzenleme ve silme:** Kullanıcının yalnızca kendi yorumlarını düzenleyebilmesi ve silebilmesi.
- [ ] **COM-09 — Yorumlara yanıt verme:** Bir yorumun başka bir yoruma yanıt olarak oluşturulabilmesi ve ilişkinin korunması.
- [ ] **COM-10 — Gönderi görselleri:** Gönderilere izin verilen görsellerin eklenebilmesi; dosya türü, boyutu ve erişim doğrulamalarının sunucu tarafında yapılması. Bağımsız `media-service` kullanılmaz.
- [ ] **COM-11 — Gönderi arama:** Topluluk gönderilerinin servis içinde aranabilmesi; ayrı `search-service` oluşturulmaması.
- [ ] **COM-12 — Gönderi filtreleme:** Gönderilerin tarih, ilişkili oyun ve kategori gibi desteklenen alanlara göre filtrelenebilmesi.
- [ ] **COM-13 — Gönderi sıralama:** Gönderilerin yenilik ve popülerlik gibi kriterlerle sıralanabilmesi.
- [ ] **COM-14 — Oyuna bağlı gönderiler:** Gönderinin isteğe bağlı olarak LTZ oyun kataloğundaki bir oyunla `gameId` üzerinden ilişkilendirilebilmesi.
- [ ] **COM-15 — Kullanıcı gönderi geçmişi:** Kullanıcının erişime açık gönderilerinin kendi profilinde görüntülenebilmesi.
- [ ] **COM-16 — Gönderi ve yorum raporlama:** Uygunsuz gönderi veya yorumun gerekçeyle raporlanabilmesi; raporun içerik referansı ve durum bilgisiyle moderasyon akışına aktarılması.
- [ ] **COM-17 — Moderasyon entegrasyonu:** Yetkili moderatörlerin `admin-service` üzerinden aldığı kararların ilgili topluluk içeriğine uygulanabilmesi.
- [ ] **COM-18 — Topluluk bildirimleri:** Gönderiye yorum, yanıt veya ilgili etkileşim geldiğinde ortak `notification-service` için bildirim olayı oluşturulması.
- [ ] **COM-19 — Oyun forumları / konuları:** Oyunlara özel tartışma başlıklarının ve bu başlıklar altındaki yorumların desteklenmesi; moderasyon ve oyun ilişkilerinin korunması.
- [ ] **COM-20 — Repost:** Kullanıcının yayımlanmış bir gönderiyi tekrar paylaşabilmesi; repost üzerinde **orijinal gönderiye ve gönderi sahibine açık atıf** bulunması. Repost, kaynak içeriği ve asıl sahipliği değiştirmemelidir; kaldırılmış veya erişim yetkisi bulunmayan gönderilerin repost üzerinden yetkisiz görüntülenmesine izin verilmemelidir.
- [ ] **COM-21 — Servis sınırları ve entegrasyon:** Gönderi, yorum, beğeni, repost ve forum verilerinin yalnızca Community Service'te tutulması. `auth-service`, `user-service`, `game-service`, `social-service`, ortak `notification-service` ve `admin-service` ile sözleşme bazlı entegrasyon kurulması; kullanıcı engelleme ve görünürlük kurallarının uygulanması. Başka servislerin veritabanlarına doğrudan erişilmez.

---

## 2. Content Service

**Servis:** `content-service`  
**Veritabanı:** `content_db`  
**Amaç:** LTZ'de oyun dünyasına ilişkin **haber ve duyuru tarzındaki içeriklerin** kullanıcılar tarafından oluşturulmasını, yayımlanmasını, aranmasını ve gerektiğinde moderatörler tarafından kaldırılmasını yönetmek.

### Görevler

- [ ] **CONT-01 — Oyun haberleri:** Oyun dünyasındaki gelişmelerin haber olarak yayımlanabilmesi ve haberlerin listelenebilmesi.
- [ ] **CONT-02 — Haber detayları:** Haberin başlık, içerik, özet, görsel, kaynak ve yayımlanma tarihiyle birlikte görüntülenebilmesi.
- [ ] **CONT-03 — Haber kategorileri:** Haberlerin oyun, platform veya konu kategorilerine göre sınıflandırılabilmesi.
- [ ] **CONT-04 — Platform duyuruları:** LTZ ile ilgili resmî sistem, özellik ve topluluk duyurularının yalnızca yetkili platform yöneticileri tarafından yayımlanabilmesi; kullanıcı haberleriyle resmî platform duyurularının ayırt edilmesi.
- [ ] **CONT-05 — Kullanıcı tarafından içerik oluşturma:** Kayıtlı oyuncuların haber/duyuru kapsamına uygun içerikler oluşturabilmesi; içerik yayımlama yetkisinin yalnızca yönetici ya da özel içerik üreticisi rolüyle sınırlandırılmaması.
- [ ] **CONT-06 — İçerik düzenleme:** Kullanıcının kendi içeriğini düzenleyebilmesi; diğer kullanıcılara ait içeriklerin yetkisiz değiştirilememesi.
- [ ] **CONT-07 — İçerik silme:** Kullanıcının kendi içeriğini kaldırabilmesi; silme işleminde ilişkili kayıtların tutarlılığının korunması.
- [ ] **CONT-08 — Taslak yönetimi:** İçeriğin yayımlanmadan önce taslak olarak kaydedilebilmesi ve sahibi tarafından düzenlenebilmesi.
- [ ] **CONT-09 — Kullanıcı tarafından yayımlama:** Kayıtlı kullanıcının yayın kurallarını sağlayan kendi haber/duyuru içeriğini yayımlayabilmesi. **Önceden moderatör onayı zorunlu tutulmaz.**
- [ ] **CONT-10 — Moderatör tarafından yayından kaldırma:** Moderatörün kurallara aykırı veya kaldırılmasını gerekli gördüğü yayımlanmış içeriği yetkili işlemle yayından kaldırabilmesi; işlem gerekçesi, kararı veren yetkili ve zamanının kaydedilmesi. Moderasyon akışı ortak `admin-service` üzerinden yürütülür.
- [ ] **CONT-11 — Öne çıkan içerikler:** Uygun haber ve duyuruların ana sayfa gibi alanlarda öne çıkarılabilmesi; öne çıkarma yetkilerinin kontrol edilmesi.
- [ ] **CONT-12 — İçerik arama:** Haber ve duyuruların servis içinde aranabilmesi; ayrı `search-service` kullanılmaması.
- [ ] **CONT-13 — İçerik filtreleme:** Haber ve duyuruların kategori, ilgili oyun ve yayımlanma tarihine göre filtrelenebilmesi.
- [ ] **CONT-14 — İçerik görselleri:** Haber ve duyurularda görsel kullanılabilmesi; dosya türü, boyut ve erişim güvenliği kurallarının uygulanması. Ayrı `media-service` kullanılmaz.
- [ ] **CONT-15 — Çoklu dil desteği:** Desteklenen haber ve duyuru içeriklerinin Türkçe ve İngilizce sunulabilmesi.
- [ ] **CONT-16 — İçerik kaynakları:** Kaynağa dayalı haberlerde kaynak bağlantısı ve tarih bilgisinin tutulabilmesi; kaynak gösterimi ve içerik kullanım hakkı kurallarının gözetilmesi.
- [ ] **CONT-17 — İçerik Üreticisi rolü:** `auth-service` tarafından yönetilen İçerik Üreticisi rolünün varsa ek görünürlük ve içerik yönetim izinlerinin uygulanması; **normal kayıtlı kullanıcıların haber/duyuru yayımlama hakkının korunması**.
- [ ] **CONT-18 — İçerik yayımlama kuralları:** Haber/duyuru içerikleri için sunucu tarafında içerik türü, zorunlu alanlar, uzunluk ve medya doğrulaması; spam/mükerrer paylaşım önlemleri; kullanıcı sahipliği ve RBAC kontrolleri uygulanması. Kurallara aykırı içerikler reddedilebilmeli veya yetkili moderatör tarafından yayından kaldırılabilmelidir. Resmî LTZ duyurusu yetkisi normal kullanıcıya verilmez. Kesin eşik ve moderasyon politikaları ortak proje kurallarıyla belirlenir.
- [ ] **CONT-19 — Servis sınırları ve entegrasyon:** Haber ve duyuruların yalnızca Content Service'te tutulması; topluluk gönderilerinin `community-service`, etkinlik ve katılım kayıtlarının `event-service` tarafından yönetilmesi. `auth-service`, `user-service`, `game-service`, ortak `notification-service`, `admin-service` ve gerektiğinde `event-service` ile sözleşme bazlı entegrasyon kurulması. Başka servislerin veritabanlarına doğrudan erişilmez.

---

## 3. Event Service

**Servis:** `event-service`  
**Veritabanı:** `event_db`  
**Amaç:** LTZ'deki turnuva, oyun gecesi ve diğer topluluk etkinliklerinin oluşturulmasını; etkinlik katılımlarını, takvimi, hatırlatmaları ve **katılım iptalinden doğan bir etkinliklik cezayı** yönetmek.

### Görevler

- [ ] **EVT-01 — Etkinlik oluşturma:** Yetkili kullanıcının yeni etkinlik oluşturabilmesi.
- [ ] **EVT-02 — Etkinlik türleri:** Turnuva, oyun gecesi, topluluk etkinliği ve oylama gibi türlerin desteklenmesi.
- [ ] **EVT-03 — Etkinlik detayları:** Etkinliğin adı, açıklaması, başlangıç/bitiş zamanı ve katılım bilgilerinin görüntülenebilmesi.
- [ ] **EVT-04 — Etkinlik listeleme:** Yaklaşan ve mevcut etkinliklerin listelenebilmesi.
- [ ] **EVT-05 — Etkinlik arama:** Etkinlik adına veya ilişkili oyuna göre arama yapılabilmesi.
- [ ] **EVT-06 — Etkinlik filtreleme:** Oyun, etkinlik türü, tarih ve platform kriterleriyle filtreleme yapılabilmesi.
- [ ] **EVT-07 — Etkinlik takvimi:** Yaklaşan etkinliklerin takvim görünümünde sunulabilmesi.
- [ ] **EVT-08 — Etkinliğe katılma:** Kullanıcının katılım kaydı oluşturabilmesi; aynı etkinliğe mükerrer katılımın önlenmesi ve geçerli katılım engellerinin kontrol edilmesi.
- [ ] **EVT-09 — Katılım iptali:** Kullanıcının kendi katılım kaydını iptal edebilmesi; iptal işleminin kalıcı kaydedilmesi ve **bir etkinliklik katılım cezasını** tetiklemesi.
- [ ] **EVT-10 — Bir sonraki etkinliğe katılım cezası:** Katılımını iptal eden kullanıcının **bir sonraki etkinliğe katılmasının engellenmesi**, bu tek etkinliklik ceza sonrasında ise daha sonraki etkinliklere yeniden katılabilmesi. Ceza tekrar tekrar uygulanmamalı; kontrol backend tarafında yapılmalıdır.
- [ ] **EVT-11 — Katılım cezasını takip etme:** Kullanıcının kendi cezasının durumunu, hangi etkinlik için katılım engeli bulunduğunu ve ceza geçmişini görüntüleyebilmesi. Cezaların servis veritabanında izlenebilir biçimde tutulması.
- [ ] **EVT-12 — Katılımcı listesi:** Etkinliğe kayıt olan kullanıcıların izin verilen bilgilerle listelenebilmesi.
- [ ] **EVT-13 — Katılımcı sınırı:** Etkinlik kapasitesi belirlenebilmesi; kapasite aşımlarının eşzamanlı katılım taleplerinde de engellenmesi.
- [ ] **EVT-14 — Etkinlik güncelleme:** Etkinlik sahibinin veya yetkili kullanıcının etkinlik bilgilerini güncelleyebilmesi.
- [ ] **EVT-15 — Etkinlik iptali:** Yetkili kullanıcının etkinliği iptal edebilmesi; iptal edilen etkinliğe yeni katılım alınmaması.
- [ ] **EVT-16 — Etkinlik durumları:** Planlandı, aktif, tamamlandı ve iptal edildi gibi durumların kurallı geçişlerle yönetilmesi.
- [ ] **EVT-17 — Oyun bağlantısı:** Etkinliğin ilgili LTZ oyunuyla `gameId` üzerinden ilişkilendirilebilmesi.
- [ ] **EVT-18 — Turnuva etkinlikleri:** Turnuvaların katılım, başlangıç/bitiş ve temel organizasyon bilgilerinin yönetilmesi.
- [ ] **EVT-19 — Oyun geceleri:** Belirli bir oyun ve zaman için oyun gecesi düzenlenebilmesi.
- [ ] **EVT-20 — Topluluk oylamaları:** Etkinlik kapsamındaki topluluk oylamalarının oluşturulabilmesi ve oyların yönetilmesi.
- [ ] **EVT-21 — Etkinlik hatırlatmaları:** Yaklaşan etkinliklerin katılımcılara bildirim tercihlerine uygun şekilde hatırlatılması.
- [ ] **EVT-22 — Etkinlik duyuruları:** Etkinliklerin Content Service ve Notification Service üzerinden duyurulabilmesi.
- [ ] **EVT-23 — Etkinlik geçmişi:** Kullanıcının katıldığı veya tamamladığı etkinliklerin listelenebilmesi.
- [ ] **EVT-24 — Servis sınırları ve entegrasyon:** Etkinlik, katılım, iptal ve katılım cezası kayıtlarının sahibi yalnızca Event Service'tir. `auth-service`, `user-service`, `game-service`, ortak `notification-service`, `mail-service`, `content-service` ve `admin-service` ile sözleşme bazlı entegrasyon kurulur. Zaman, kapasite, rol ve sahiplik kuralları backend tarafından uygulanır. Başka servislerin veritabanlarına doğrudan erişilmez.

> **Katılım cezasının uygulanma kuralı:** Kullanıcının isteği doğrultusunda ceza *bir sonraki etkinliği* kapsar, daha sonraki etkinliklere katılım serbest olur. Kodlama öncesinde «bir sonraki etkinlik» tanımının hangi etkinlik kapsamına ve tarih sırasına göre belirleneceği netleştirilmelidir; bu belgede ek bir ceza türü veya süresi tanımlanmamıştır.

---

## 4. Ortak teslim kriterleri

Aşağıdaki koşullar, yukarıdaki görevlerin tamamlanmış kabul edilebilmesi için geçerlidir:

- [ ] `community-service`, `content-service` ve `event-service` kendi PostgreSQL veritabanlarıyla çalışır; şema değişiklikleri Flyway ile yönetilir.
- [ ] API / Application / Domain / Infrastructure katman ayrımı, request/response DTO kullanımı, validasyon ve hata yönetimi proje mimarisiyle uyumludur.
- [ ] Gönderi, yorum, beğeni, repost, haber/duyuru yayını, moderasyon kaldırması, etkinlik katılımı, katılım iptali ve bir etkinliklik ceza akışları uygun unit/integration testleriyle doğrulanır.
- [ ] Kullanıcı yalnızca yetkili olduğu kayıtları düzenler; moderasyon işlemleri yetkili rollerle ve izlenebilir biçimde yapılır.
- [ ] Etkinlik kapasitesi, aynı etkinliğe mükerrer katılımın önlenmesi, cezanın yalnız bir sonraki etkinliği engellemesi ve ceza takibi test edilir.
- [ ] Servisler birbirlerinin veritabanlarına doğrudan erişmez; gerekli HTTP iletişiminde Spring RestClient, çok adımlı dağıtık işlemlerde kararlaştırılan Saga yaklaşımı uygulanır.
- [ ] Korunan işlemler Auth Service'in kimlik/rol altyapısına ve proje `SECURITY.md` kurallarına uygun yürütülür.
- [ ] SonarQube üzerinde Security, Reliability ve Maintainability kategorilerinde en az **A** hedeflenir.

**Görev sayısı:** 21 COM + 19 CONT + 24 EVT = **64 numaralı görev**.

> `notification-service` ve `admin-service` ortak geliştirme kapsamındadır. Bağımsız Search, Media veya Moderation Service oluşturulmaz; ilgili işlevler belirlenen servis sınırlarında kalır. `SECURITY.md` içeriği bu belge hazırlanırken doğrulanmadığından yalnızca uyum şartı belirtilmiştir.
