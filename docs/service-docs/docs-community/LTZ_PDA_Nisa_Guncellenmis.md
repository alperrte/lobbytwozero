# Nisa — LTZ PDA Taskları

**PDA kullanımı:** Her numaralı başlık bir **ANA TASK**, altındaki checkbox satırları aynı ana taskın **ALT TASK** kayıtlarıdır. Kaynak kodları benzersiz korunmuştur.

**Teslim standardı:** Her ana task bağımsız anlamlı commit/PR ile kapanır; Flyway, DTO, validation, exception, sahiplik/rol, servis sözleşmesi ve birim/entegrasyon testleri uygulanır. Ortak SonarQube A hedefi geçerlidir.

## community-service

**Uygulama planı:** `community-service` dört ardışık fazdan oluşur. Her faz bir ana task olarak yürütülür; checkbox maddeleri o fazın alt tasklarıdır. Geliştirmeye **Faz 1 / LTZ-NIS-COM-01** ile başlanır. Kaynak gereksinim kodları (COM-01–COM-21) aynen korunur.

**Faz ve ana task sırası:**

1. **Faz 1 — LTZ-NIS-COM-01:** Topluluk Gönderisi Oluştur/Düzenle/Sil ve Görsel Ekle
2. **Faz 2 — LTZ-NIS-COM-02:** Topluluk Akışı, Arama ve Profil Geçmişi
3. **Faz 3 — LTZ-NIS-COM-03:** Gönderi Yorumları, Beğeni, Repost ve Oyun Forumları
4. **Faz 4 — LTZ-NIS-COM-04:** Topluluk Raporlama, Moderasyon ve Bildirim

### LTZ-NIS-COM-01 — Topluluk Gönderisi Oluştur/Düzenle/Sil ve Görsel Ekle

**Sorumlu:** Nisa  
**Servis:** `community-service`  
**Faz:** Faz 1  
**İlgili gereksinimler:** COM-01, COM-02, COM-03, COM-10, COM-14

**Ana task açıklaması:** Oyuna bağlanabilen güvenli gönderiler, görsel doğrulamalarıyla yayımlanıp yönetilir.

**Alt tasklar:**

- [ ] **COM-01 — Topluluk gönderisi oluşturma:** Kayıtlı oyuncunun kendi topluluk gönderisini yayımlayabilmesi; gönderinin sahibi ve oluşturulma zamanı ile kaydedilmesi.

- [ ] **COM-02 — Gönderi düzenleme:** Kullanıcının yalnızca kendisine ait gönderinin içeriğini değiştirebilmesi.

- [ ] **COM-03 — Gönderi silme:** Kullanıcının kendi gönderisini silebilmesi; ilgili yorum ve etkileşimlerin tutarlılığının korunması.

- [ ] **COM-10 — Gönderi görselleri:** Gönderilere izin verilen görsellerin eklenebilmesi; dosya türü, boyutu ve erişim doğrulamalarının sunucu tarafında yapılması. Bağımsız `media-service` kullanılmaz.

- [ ] **COM-14 — Oyuna bağlı gönderiler:** Gönderinin isteğe bağlı olarak LTZ oyun kataloğundaki bir oyunla `gameId` üzerinden ilişkilendirilebilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-COM-02 — Topluluk Akışı, Arama ve Profil Geçmişi

**Sorumlu:** Nisa  
**Servis:** `community-service`  
**Faz:** Faz 2  
**İlgili gereksinimler:** COM-04, COM-05, COM-11, COM-12, COM-13, COM-15

**Ana task açıklaması:** Sayfalı gönderi akışı, detay, arama/filtre/sıralama ve profil gönderi geçmişi sunulur.

**Alt tasklar:**

- [ ] **COM-04 — Gönderi listeleme:** Topluluk gönderilerinin sayfalı bir akışta görüntülenebilmesi.

- [ ] **COM-05 — Gönderi detayları:** Bir gönderinin içeriği, sahibi, tarihi, yorumları ve etkileşimlerinin görüntülenebilmesi.

- [ ] **COM-11 — Gönderi arama:** Topluluk gönderilerinin servis içinde aranabilmesi; ayrı `search-service` oluşturulmaması.

- [ ] **COM-12 — Gönderi filtreleme:** Gönderilerin tarih, ilişkili oyun ve kategori gibi desteklenen alanlara göre filtrelenebilmesi.

- [ ] **COM-13 — Gönderi sıralama:** Gönderilerin yenilik ve popülerlik gibi kriterlerle sıralanabilmesi.

- [ ] **COM-15 — Kullanıcı gönderi geçmişi:** Kullanıcının erişime açık gönderilerinin kendi profilinde görüntülenebilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-COM-03 — Gönderi Yorumları, Beğeni, Repost ve Oyun Forumları

**Sorumlu:** Nisa  
**Servis:** `community-service`  
**Faz:** Faz 3  
**İlgili gereksinimler:** COM-06, COM-07, COM-08, COM-09, COM-19, COM-20

**Ana task açıklaması:** Yorum/yanıt, beğeni, oyuna özel tartışmalar ve kaynağa atıflı repost sosyal kurallarla yönetilir.

**Alt tasklar:**

- [ ] **COM-06 — Gönderi beğenileri:** Kullanıcıların gönderileri beğenebilmesi ve beğenilerini geri alabilmesi; aynı kullanıcıdan mükerrer beğeni kaydı oluşmaması.

- [ ] **COM-07 — Gönderi yorumları:** Kullanıcıların gönderilere yorum yazabilmesi.

- [ ] **COM-08 — Yorum düzenleme ve silme:** Kullanıcının yalnızca kendi yorumlarını düzenleyebilmesi ve silebilmesi.

- [ ] **COM-09 — Yorumlara yanıt verme:** Bir yorumun başka bir yoruma yanıt olarak oluşturulabilmesi ve ilişkinin korunması.

- [ ] **COM-19 — Oyun forumları / konuları:** Oyunlara özel tartışma başlıklarının ve bu başlıklar altındaki yorumların desteklenmesi; moderasyon ve oyun ilişkilerinin korunması.

- [ ] **COM-20 — Repost:** Kullanıcının yayımlanmış bir gönderiyi tekrar paylaşabilmesi; repost üzerinde **orijinal gönderiye ve gönderi sahibine açık atıf** bulunması. Repost, kaynak içeriği ve asıl sahipliği değiştirmemelidir; kaldırılmış veya erişim yetkisi bulunmayan gönderilerin repost üzerinden yetkisiz görüntülenmesine izin verilmemelidir.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-COM-04 — Topluluk Raporlama, Moderasyon ve Bildirim

**Sorumlu:** Nisa  
**Servis:** `community-service`  
**Faz:** Faz 4  
**İlgili gereksinimler:** COM-16, COM-17, COM-18, COM-21

**Ana task açıklaması:** İçerik raporları Admin Service üzerinden sonuçlandırılır; etkileşim bildirimleri doğru servise aktarılır.

**Alt tasklar:**

- [ ] **COM-16 — Gönderi ve yorum raporlama:** Uygunsuz gönderi veya yorumun gerekçeyle raporlanabilmesi; raporun içerik referansı ve durum bilgisiyle moderasyon akışına aktarılması.

- [ ] **COM-17 — Moderasyon entegrasyonu:** Yetkili moderatörlerin `admin-service` üzerinden aldığı kararların ilgili topluluk içeriğine uygulanabilmesi.

- [ ] **COM-18 — Topluluk bildirimleri:** Gönderiye yorum, yanıt veya ilgili etkileşim geldiğinde ortak `notification-service` için bildirim olayı oluşturulması.

- [ ] **COM-21 — Servis sınırları ve entegrasyon:** Gönderi, yorum, beğeni, repost ve forum verilerinin yalnızca Community Service'te tutulması. `auth-service`, `user-service`, `game-service`, `social-service`, ortak `notification-service` ve `admin-service` ile sözleşme bazlı entegrasyon kurulması; kullanıcı engelleme ve görünürlük kurallarının uygulanması. Başka servislerin veritabanlarına doğrudan erişilmez.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.


## content-service

### LTZ-NIS-CONT-01 — Haber Oluştur, Taslak Kaydet ve İçeriği Düzenle

**Sorumlu:** Nisa  
**Servis:** `content-service`  
**Faz:** MVP  
**İlgili gereksinimler:** CONT-05, CONT-06, CONT-07, CONT-08, CONT-14, CONT-18

**Ana task açıklaması:** Kayıtlı kullanıcılar haber/duyuru içeriği yazar, medya ekler ve yayın kurallarına uygun taslak yönetir.

**Alt tasklar:**

- [ ] **CONT-05 — Kullanıcı tarafından içerik oluşturma:** Kayıtlı oyuncuların haber/duyuru kapsamına uygun içerikler oluşturabilmesi; içerik yayımlama yetkisinin yalnızca yönetici ya da özel içerik üreticisi rolüyle sınırlandırılmaması.

- [ ] **CONT-06 — İçerik düzenleme:** Kullanıcının kendi içeriğini düzenleyebilmesi; diğer kullanıcılara ait içeriklerin yetkisiz değiştirilememesi.

- [ ] **CONT-07 — İçerik silme:** Kullanıcının kendi içeriğini kaldırabilmesi; silme işleminde ilişkili kayıtların tutarlılığının korunması.

- [ ] **CONT-08 — Taslak yönetimi:** İçeriğin yayımlanmadan önce taslak olarak kaydedilebilmesi ve sahibi tarafından düzenlenebilmesi.

- [ ] **CONT-14 — İçerik görselleri:** Haber ve duyurularda görsel kullanılabilmesi; dosya türü, boyut ve erişim güvenliği kurallarının uygulanması. Ayrı `media-service` kullanılmaz.

- [ ] **CONT-18 — İçerik yayımlama kuralları:** Haber/duyuru içerikleri için sunucu tarafında içerik türü, zorunlu alanlar, uzunluk ve medya doğrulaması; spam/mükerrer paylaşım önlemleri; kullanıcı sahipliği ve RBAC kontrolleri uygulanması. Kurallara aykırı içerikler reddedilebilmeli veya yetkili moderatör tarafından yayından kaldırılabilmelidir. Resmî LTZ duyurusu yetkisi normal kullanıcıya verilmez. Kesin eşik ve moderasyon politikaları ortak proje kurallarıyla belirlenir.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-CONT-02 — Haber Yayınlama, Keşif ve Çoklu Dil

**Sorumlu:** Nisa  
**Servis:** `content-service`  
**Faz:** MVP  
**İlgili gereksinimler:** CONT-01, CONT-02, CONT-03, CONT-09, CONT-11, CONT-12, CONT-13, CONT-15, CONT-16

**Ana task açıklaması:** Haberler ön onaysız yayımlanır; başlık/detay/kategori/öne çıkan/aramanın TR-EN ve kaynak özellikleri sağlanır.

**Alt tasklar:**

- [ ] **CONT-01 — Oyun haberleri:** Oyun dünyasındaki gelişmelerin haber olarak yayımlanabilmesi ve haberlerin listelenebilmesi.

- [ ] **CONT-02 — Haber detayları:** Haberin başlık, içerik, özet, görsel, kaynak ve yayımlanma tarihiyle birlikte görüntülenebilmesi.

- [ ] **CONT-03 — Haber kategorileri:** Haberlerin oyun, platform veya konu kategorilerine göre sınıflandırılabilmesi.

- [ ] **CONT-09 — Kullanıcı tarafından yayımlama:** Kayıtlı kullanıcının yayın kurallarını sağlayan kendi haber/duyuru içeriğini yayımlayabilmesi. **Önceden moderatör onayı zorunlu tutulmaz.**

- [ ] **CONT-11 — Öne çıkan içerikler:** Uygun haber ve duyuruların ana sayfa gibi alanlarda öne çıkarılabilmesi; öne çıkarma yetkilerinin kontrol edilmesi.

- [ ] **CONT-12 — İçerik arama:** Haber ve duyuruların servis içinde aranabilmesi; ayrı `search-service` kullanılmaması.

- [ ] **CONT-13 — İçerik filtreleme:** Haber ve duyuruların kategori, ilgili oyun ve yayımlanma tarihine göre filtrelenebilmesi.

- [ ] **CONT-15 — Çoklu dil desteği:** Desteklenen haber ve duyuru içeriklerinin Türkçe ve İngilizce sunulabilmesi.

- [ ] **CONT-16 — İçerik kaynakları:** Kaynağa dayalı haberlerde kaynak bağlantısı ve tarih bilgisinin tutulabilmesi; kaynak gösterimi ve içerik kullanım hakkı kurallarının gözetilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-CONT-03 — Resmi Duyurular ve Haber Moderasyonu

**Sorumlu:** Nisa  
**Servis:** `content-service`  
**Faz:** MVP  
**İlgili gereksinimler:** CONT-04, CONT-10, CONT-17, CONT-19

**Ana task açıklaması:** Resmî LTZ duyurusu yalnız yetkili kişi tarafından çıkar; yayın kaldırma ve rol/servis entegrasyonları izlenebilir.

**Alt tasklar:**

- [ ] **CONT-04 — Platform duyuruları:** LTZ ile ilgili resmî sistem, özellik ve topluluk duyurularının yalnızca yetkili platform yöneticileri tarafından yayımlanabilmesi; kullanıcı haberleriyle resmî platform duyurularının ayırt edilmesi.

- [ ] **CONT-10 — Moderatör tarafından yayından kaldırma:** Moderatörün kurallara aykırı veya kaldırılmasını gerekli gördüğü yayımlanmış içeriği yetkili işlemle yayından kaldırabilmesi; işlem gerekçesi, kararı veren yetkili ve zamanının kaydedilmesi. Moderasyon akışı ortak `admin-service` üzerinden yürütülür.

- [ ] **CONT-17 — İçerik Üreticisi rolü:** `auth-service` tarafından yönetilen İçerik Üreticisi rolünün varsa ek görünürlük ve içerik yönetim izinlerinin uygulanması; **normal kayıtlı kullanıcıların haber/duyuru yayımlama hakkının korunması**.

- [ ] **CONT-19 — Servis sınırları ve entegrasyon:** Haber ve duyuruların yalnızca Content Service'te tutulması; topluluk gönderilerinin `community-service`, etkinlik ve katılım kayıtlarının `event-service` tarafından yönetilmesi. `auth-service`, `user-service`, `game-service`, ortak `notification-service`, `admin-service` ve gerektiğinde `event-service` ile sözleşme bazlı entegrasyon kurulması. Başka servislerin veritabanlarına doğrudan erişilmez.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.


## event-service

### LTZ-NIS-EVT-01 — Etkinlik Oluştur, Güncelle, İptal Et

**Sorumlu:** Nisa  
**Servis:** `event-service`  
**Faz:** MVP  
**İlgili gereksinimler:** EVT-01, EVT-02, EVT-03, EVT-14, EVT-15, EVT-16, EVT-17

**Ana task açıklaması:** Etkinlik türü, detayları, bağlı oyun ve durum geçişleriyle yetkili yönetim akışı sağlanır.

**Alt tasklar:**

- [ ] **EVT-01 — Etkinlik oluşturma:** Yetkili kullanıcının yeni etkinlik oluşturabilmesi.

- [ ] **EVT-02 — Etkinlik türleri:** Turnuva, oyun gecesi, topluluk etkinliği ve oylama gibi türlerin desteklenmesi.

- [ ] **EVT-03 — Etkinlik detayları:** Etkinliğin adı, açıklaması, başlangıç/bitiş zamanı ve katılım bilgilerinin görüntülenebilmesi.

- [ ] **EVT-14 — Etkinlik güncelleme:** Etkinlik sahibinin veya yetkili kullanıcının etkinlik bilgilerini güncelleyebilmesi.

- [ ] **EVT-15 — Etkinlik iptali:** Yetkili kullanıcının etkinliği iptal edebilmesi; iptal edilen etkinliğe yeni katılım alınmaması.

- [ ] **EVT-16 — Etkinlik durumları:** Planlandı, aktif, tamamlandı ve iptal edildi gibi durumların kurallı geçişlerle yönetilmesi.

- [ ] **EVT-17 — Oyun bağlantısı:** Etkinliğin ilgili LTZ oyunuyla `gameId` üzerinden ilişkilendirilebilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-EVT-02 — Etkinlik Keşfi, Takvim ve Geçmiş

**Sorumlu:** Nisa  
**Servis:** `event-service`  
**Faz:** MVP  
**İlgili gereksinimler:** EVT-04, EVT-05, EVT-06, EVT-07, EVT-23

**Ana task açıklaması:** Etkinlik listesi, arama/filtreleme, takvim ve kullanıcı katılım geçmişi sunulur.

**Alt tasklar:**

- [ ] **EVT-04 — Etkinlik listeleme:** Yaklaşan ve mevcut etkinliklerin listelenebilmesi.

- [ ] **EVT-05 — Etkinlik arama:** Etkinlik adına veya ilişkili oyuna göre arama yapılabilmesi.

- [ ] **EVT-06 — Etkinlik filtreleme:** Oyun, etkinlik türü, tarih ve platform kriterleriyle filtreleme yapılabilmesi.

- [ ] **EVT-07 — Etkinlik takvimi:** Yaklaşan etkinliklerin takvim görünümünde sunulabilmesi.

- [ ] **EVT-23 — Etkinlik geçmişi:** Kullanıcının katıldığı veya tamamladığı etkinliklerin listelenebilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-EVT-03 — Etkinliğe Katıl ve Kapasite Kontrolü

**Sorumlu:** Nisa  
**Servis:** `event-service`  
**Faz:** MVP  
**İlgili gereksinimler:** EVT-08, EVT-12, EVT-13

**Ana task açıklaması:** Katılımcı listesi güvenli sunulur; kapasite/mükerrer katılım eşzamanlı taleplerde de engellenir.

**Alt tasklar:**

- [ ] **EVT-08 — Etkinliğe katılma:** Kullanıcının katılım kaydı oluşturabilmesi; aynı etkinliğe mükerrer katılımın önlenmesi ve geçerli katılım engellerinin kontrol edilmesi.

- [ ] **EVT-12 — Katılımcı listesi:** Etkinliğe kayıt olan kullanıcıların izin verilen bilgilerle listelenebilmesi.

- [ ] **EVT-13 — Katılımcı sınırı:** Etkinlik kapasitesi belirlenebilmesi; kapasite aşımlarının eşzamanlı katılım taleplerinde de engellenmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-EVT-04 — Katılım İptali ve Tek Etkinliklik Ceza

**Sorumlu:** Nisa  
**Servis:** `event-service`  
**Faz:** MVP (kural kararı gerekli)  
**İlgili gereksinimler:** EVT-09, EVT-10, EVT-11

**Ana task açıklaması:** Katılım iptali yalnız bir sonraki etkinliğe giriş engeli yaratır; geçmiş/durum izlenir ve tekrar uygulanmaz.

**Alt tasklar:**

- [ ] **EVT-09 — Katılım iptali:** Kullanıcının kendi katılım kaydını iptal edebilmesi; iptal işleminin kalıcı kaydedilmesi ve **bir etkinliklik katılım cezasını** tetiklemesi.

- [ ] **EVT-10 — Bir sonraki etkinliğe katılım cezası:** Katılımını iptal eden kullanıcının **bir sonraki etkinliğe katılmasının engellenmesi**, bu tek etkinliklik ceza sonrasında ise daha sonraki etkinliklere yeniden katılabilmesi. Ceza tekrar tekrar uygulanmamalı; kontrol backend tarafında yapılmalıdır.

- [ ] **EVT-11 — Katılım cezasını takip etme:** Kullanıcının kendi cezasının durumunu, hangi etkinlik için katılım engeli bulunduğunu ve ceza geçmişini görüntüleyebilmesi. Cezaların servis veritabanında izlenebilir biçimde tutulması.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-EVT-05 — Turnuvalar, Oyun Geceleri ve Oylamalar

**Sorumlu:** Nisa  
**Servis:** `event-service`  
**Faz:** MVP  
**İlgili gereksinimler:** EVT-18, EVT-19, EVT-20

**Ana task açıklaması:** Turnuva, oyun gecesi ve topluluk oylaması etkinlik türleri kendi temel işlevleriyle çalışır.

**Alt tasklar:**

- [ ] **EVT-18 — Turnuva etkinlikleri:** Turnuvaların katılım, başlangıç/bitiş ve temel organizasyon bilgilerinin yönetilmesi.

- [ ] **EVT-19 — Oyun geceleri:** Belirli bir oyun ve zaman için oyun gecesi düzenlenebilmesi.

- [ ] **EVT-20 — Topluluk oylamaları:** Etkinlik kapsamındaki topluluk oylamalarının oluşturulabilmesi ve oyların yönetilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-NIS-EVT-06 — Etkinlik Hatırlatma, Duyuru ve Entegrasyon

**Sorumlu:** Nisa  
**Servis:** `event-service`  
**Faz:** MVP  
**İlgili gereksinimler:** EVT-21, EVT-22, EVT-24

**Ana task açıklaması:** Kullanıcı tercihine uygun hatırlatma ve duyuru akışları ortak servislere sözleşmeli bağlanır.

**Alt tasklar:**

- [ ] **EVT-21 — Etkinlik hatırlatmaları:** Yaklaşan etkinliklerin katılımcılara bildirim tercihlerine uygun şekilde hatırlatılması.

- [ ] **EVT-22 — Etkinlik duyuruları:** Etkinliklerin Content Service ve Notification Service üzerinden duyurulabilmesi.

- [ ] **EVT-24 — Servis sınırları ve entegrasyon:** Etkinlik, katılım, iptal ve katılım cezası kayıtlarının sahibi yalnızca Event Service'tir. `auth-service`, `user-service`, `game-service`, ortak `notification-service`, `mail-service`, `content-service` ve `admin-service` ile sözleşme bazlı entegrasyon kurulur. Zaman, kapasite, rol ve sahiplik kuralları backend tarafından uygulanır. Başka servislerin veritabanlarına doğrudan erişilmez.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.
