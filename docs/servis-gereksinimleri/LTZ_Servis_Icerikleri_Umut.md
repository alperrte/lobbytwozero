# LTZ — Servis İçerikleri | Umut

**Sorumlu geliştirici:** Umut  
**İş alanı:** Sosyal İlişkiler, Mesajlaşma ve Oyun Arkadaşı Bulma  
**Servisler:** `social-service`, `messaging-service`, `lfp-service`  
**Veritabanları:** `social_db`, `messaging_db`, `lfp_db`

Bu belge, Umut'a atanacak geliştirme görevlerini içerir. Her `SOC-XX`, `MSG-XX` ve `LFP-XX` maddesi ayrı bir görev olarak takip edilebilir. Tüm servisler Java Spring Boot, PostgreSQL, Flyway, Maven, Docker ve üzerinde anlaşılan `api` / `application` / `domain` / `infrastructure` katmanlı yapısına uygun geliştirilecektir.

---

## 1. Social Service

**Servis:** `social-service`  
**Veritabanı:** `social_db`  
**Amaç:** LTZ kullanıcılarının arkadaşlık ilişkilerini, arkadaşlık isteklerini, davet kodlarını ve kullanıcı engelleme işlemlerini güvenli biçimde yönetmek.

### Görevler

- [ ] **SOC-01 — Arkadaşlık isteği gönderme:** Kayıtlı kullanıcının başka bir kullanıcıya arkadaşlık isteği gönderebilmesi.
- [ ] **SOC-02 — Arkadaşlık isteği kabul etme:** Gelen isteğin kabul edilmesi ve karşılıklı arkadaşlık ilişkisinin oluşturulması.
- [ ] **SOC-03 — Arkadaşlık isteği reddetme:** Kullanıcının gelen arkadaşlık isteğini reddedebilmesi.
- [ ] **SOC-04 — Arkadaşlık isteği iptali:** Gönderilmiş ve henüz kabul edilmemiş arkadaşlık isteğinin iptal edilebilmesi.
- [ ] **SOC-05 — Gelen arkadaşlık istekleri:** Kullanıcının kendisine gelen bekleyen arkadaşlık isteklerini listeleyebilmesi.
- [ ] **SOC-06 — Gönderilen arkadaşlık istekleri:** Kullanıcının gönderdiği bekleyen arkadaşlık isteklerini listeleyebilmesi.
- [ ] **SOC-07 — Arkadaş listesi:** Kullanıcının arkadaşlık ilişkilerini listeleyebilmesi.
- [ ] **SOC-08 — Arkadaşlıktan çıkarma:** Kullanıcının mevcut arkadaşlık ilişkisini sonlandırabilmesi.
- [ ] **SOC-09 — Kullanıcı engelleme:** Kullanıcının başka bir hesabı engelleyebilmesi; engellenen kullanıcının kısıtlanan arkadaşlık ve iletişim işlemlerinin durdurulması.
- [ ] **SOC-10 — Engel kaldırma:** Daha önce engellenmiş kullanıcı için engelin kaldırılabilmesi.
- [ ] **SOC-11 — Engellenen kullanıcılar:** Kullanıcının engellediği hesapları listeleyebilmesi.
- [ ] **SOC-12 — Sosyal gizlilik:** Arkadaş listesinin `user-service` tarafından yönetilen görünürlük tercihlerine uygun sunulması.
- [ ] **SOC-13 — Arkadaşlık bildirimleri:** Arkadaşlık isteği ve kabul gibi olayların ortak `notification-service` ile sözleşme bazlı paylaşılması.
- [ ] **SOC-14 — Arkadaşlık davet kodu:** Kullanıcının başka kişilere paylaşabileceği bir arkadaşlık davet koduna sahip olması; kodu giren kişinin ilgili kullanıcıyı bulup arkadaşlık isteği başlatabilmesi. Normal arkadaşlık ve engelleme kuralları bu akışta da geçerli olmalıdır.
- [ ] **SOC-15 — Servis sınırları ve entegrasyon:** Arkadaşlık, davet kodu ve engelleme verilerinin yalnızca Social Service'te tutulması; kullanıcıların `userId` ile referanslanması. `auth-service`, `user-service`, `messaging-service`, `lfp-service` ve ortak `notification-service` ile sözleşme bazlı entegrasyon kurulması. Bir kullanıcı kendisine arkadaşlık isteği gönderemez; aynı ilişki için mükerrer aktif istek veya arkadaşlık kaydı oluşamaz. Başka servislerin veritabanlarına doğrudan erişilmez.

---

## 2. Messaging Service

**Servis:** `messaging-service`  
**Veritabanı:** `messaging_db`  
**Amaç:** LTZ kullanıcılarının birebir ve grup sohbetleri üzerinden gerçek zamanlı mesaj, görsel, GIF ve isteğe bağlı sesli mesaj paylaşmasını; mesaj etkileşimlerini ve erişim yetkilerini yönetmek.

### Görevler

- [ ] **MSG-01 — Birebir mesajlaşma:** İki kullanıcının özel sohbet üzerinden iletişim kurabilmesi.
- [ ] **MSG-02 — Grup sohbeti oluşturma:** Birden fazla kullanıcının katılabileceği grup sohbetlerinin oluşturulması.
- [ ] **MSG-03 — Grup üyeliği yönetimi:** Grup sohbetine üye ekleme ve üyeliklerin yönetilmesi; grup erişim kurallarının uygulanması.
- [ ] **MSG-04 — Grup sohbetinden ayrılma:** Kullanıcının katıldığı grup sohbetinden ayrılabilmesi.
- [ ] **MSG-05 — Grup yöneticisinin üye çıkarması:** Grup yöneticisinin grup sohbetindeki üyeleri çıkarabilmesi; işlemin yalnızca yetkili grup yöneticisi tarafından yapılabilmesi.
- [ ] **MSG-06 — Metin mesajı gönderme:** Kullanıcıların sohbetlere metinsel mesaj gönderebilmesi.
- [ ] **MSG-07 — Mesaj geçmişi:** Kullanıcının erişebildiği sohbetlerde geçmiş mesajların sayfalı olarak listelenmesi.
- [ ] **MSG-08 — Sohbet listesi:** Birebir ve grup sohbetlerinin kullanıcıya özel olarak listelenmesi.
- [ ] **MSG-09 — Gerçek zamanlı mesajlaşma:** Mesajların sayfa yenilemeden alıcıya iletilmesi; bağlantı kopsa da kaydedilmiş mesajların kaybolmaması.
- [ ] **MSG-10 — Mesaj bilgileri:** Mesajların gönderici ve oluşturulma zamanı gibi bilgilerle saklanması ve gösterilmesi.
- [ ] **MSG-11 — Okundu / görüldü durumu:** Mesajların okunma/görülme durumlarının takip edilmesi ve ilgili kullanıcıya gösterilmesi.
- [ ] **MSG-12 — Okuyan kişileri görüntüleme:** Özellikle grup sohbetlerinde mesajı kimlerin okuduğunun/gördüğünün, erişim yetkisi olan kullanıcı tarafından görüntülenebilmesi.
- [ ] **MSG-13 — Okunmamış mesaj sayısı:** Kullanıcının sohbet bazlı okunmamış mesaj sayılarını görüntüleyebilmesi.
- [ ] **MSG-14 — Sohbet erişim kontrolü:** Yalnızca ilgili birebir sohbetin taraflarının ve grup sohbetinin üyelerinin mesajları okuyabilmesi/yazabilmesi.
- [ ] **MSG-15 — Engelleme kontrolü:** `social-service` tarafından yönetilen engelleme durumlarının birebir iletişimde ve ilgili etkileşimlerde uygulanması.
- [ ] **MSG-16 — Mesaj bildirimleri:** Yeni mesaj olaylarının ortak `notification-service` ile paylaşılması.
- [ ] **MSG-17 — Görsel ve GIF yükleme:** Sohbetlere PNG, GIF ve izin verilen benzeri görsel dosyalarının yüklenebilmesi. **Yüklenen her dosyanın azami boyutu 1 MB** olmalı; dosya türü ve boyutu sunucu tarafında doğrulanmalıdır. Ayrı Media Service kullanılmaz.
- [ ] **MSG-18 — Mesajlarda emoji:** Kullanıcıların mesaj metninde emoji kullanabilmesi.
- [ ] **MSG-19 — Mesajı yanıtla:** Kullanıcının belirli bir mesajı seçip WhatsApp benzeri yanıt oluşturabilmesi; yanıtın ilişkili olduğu mesajın sohbet içinde gösterilmesi.
- [ ] **MSG-20 — Mesaj iletme:** Kullanıcının erişim yetkisi bulunan bir mesajı başka bir uygun sohbete iletebilmesi.
- [ ] **MSG-21 — Sesli mesaj (opsiyonel):** Kullanıcının ses kaydı alıp sohbet içinde sesli mesaj olarak gönderebilmesi ve alıcının kaydı dinleyebilmesi. **Bu özellik kullanıcı tarafından olası ek özellik olarak belirtilmiştir; uygulanması ayrıca kesinleştirilecektir.**
- [ ] **MSG-22 — GIF platformu entegrasyonu:** GIPHY veya uygun bir GIF platformu ile entegrasyon kurularak kullanıcıların sohbet içinde GIF arayıp gönderebilmesi.
- [ ] **MSG-23 — Mesaj tepkileri:** Kullanıcının mesajlara kalp, beğeni ve benzeri emoji tepkileri bırakabilmesi; mevcut tepkilerini yönetebilmesi.
- [ ] **MSG-24 — Mesaj işlem menüsü:** Mesaj üzerine gelindiğinde açılan ok/menü aracılığıyla WhatsApp benzeri mesaj işlemlerine ve mesaj bilgilerine erişilebilmesi; dokunmatik ve klavye kullanımında da işlemlerin erişilebilir olması.
- [ ] **MSG-25 — Mesajı benden sil:** Kullanıcının seçilen mesajı yalnızca kendi sohbet görünümünden kaldırabilmesi; diğer katılımcıların mesaj kaydının etkilenmemesi.
- [ ] **MSG-26 — Mesajı herkesten sil:** Mesajı gönderen kullanıcının uygun yetki ve kurallarla mesajı bütün sohbet katılımcılarının görünümünden kaldırabilmesi.
- [ ] **MSG-27 — Servis sınırları ve entegrasyon:** Sohbet, mesaj, grup üyeliği, okundu bilgisi, medya referansları ve mesaj etkileşimlerinin sahibi Messaging Service olur. `auth-service`, `user-service`, `social-service`, ortak `notification-service` ve yetkili moderasyon akışları için `admin-service` ile sözleşme bazlı entegrasyon kurulur. Mükerrer mesaj üretimi önlenir; mesaj kaydı bildirim tesliminden bağımsız olarak güvenilir biçimde korunur. Başka servislerin veritabanlarına doğrudan erişilmez.

---

## 3. LFP Service

**Servis:** `lfp-service`  
**Veritabanı:** `lfp_db`  
**Amaç:** LTZ kullanıcılarının süreli oyun arkadaşı ilanları oluşturmasını ve oyun, platform, rol, zaman gibi tercihlere göre uygun oyuncularla bağlantı kurmasını sağlamak.

### Görevler

- [ ] **LFP-01 — Oyun arkadaşı ilanı oluşturma:** Kullanıcının belirli bir oyun için oyuncu/takım arkadaşı arama ilanı oluşturabilmesi.
- [ ] **LFP-02 — Oyun seçimi:** İlanın LTZ oyun kataloğundaki bir oyunla ilişkilendirilmesi.
- [ ] **LFP-03 — Platform seçimi:** PC, PlayStation, Xbox veya oyun tarafından desteklenen diğer platformların belirtilebilmesi.
- [ ] **LFP-04 — Oyuncu seviyesi (opsiyonel):** İlan sahibinin rank, seviye veya deneyim beklentisini **isteğe bağlı** olarak belirtebilmesi; bu alan boş bırakıldığında ilan oluşturulabilmesi.
- [ ] **LFP-05 — Oyuncu rolü:** Oyuna özel rol veya oyuncu tipi beklentisinin belirtilebilmesi.
- [ ] **LFP-06 — Aranan oyuncu sayısı:** İlanda kaç oyuncu arandığının belirtilebilmesi.
- [ ] **LFP-07 — Oyun zamanı:** Planlanan oyun tarihi ve saatinin ilana eklenebilmesi.
- [ ] **LFP-08 — Mikrofon tercihi:** Sesli iletişim/mikrofon gerekliliğinin belirtilebilmesi.
- [ ] **LFP-09 — İlan açıklaması:** İlan sahibinin oyun tarzını ve aradığı oyuncuların özelliklerini açıklayabilmesi.
- [ ] **LFP-10 — Aktif ilanları listeleme:** Kullanıcıların aktif oyun arkadaşı ilanlarını görüntüleyebilmesi.
- [ ] **LFP-11 — İlan arama ve filtreleme:** Oyun, platform, seviye (belirtilmişse), rol ve zaman bilgilerine göre ilanların aranıp filtrelenebilmesi. Arama bu servis içinde yapılır.
- [ ] **LFP-12 — İlan güncelleme:** İlan sahibinin ilan bilgilerini değiştirebilmesi.
- [ ] **LFP-13 — İlan kapatma:** İlan sahibinin artık oyuncu aramadığında ilanını kapatabilmesi.
- [ ] **LFP-14 — İlan durumları:** İlanların aktif, kapalı ve süresi dolmuş durumlarının yönetilmesi.
- [ ] **LFP-15 — Süreli ilanlar:** Her ilanın bir geçerlilik süresinin olması; süre dolduğunda ilanın otomatik olarak **süresi dolmuş** durumuna geçmesi ve aktif ilan listelerinden çıkarılması.
- [ ] **LFP-16 — İlan sahibi ile iletişim:** İlanı görüntüleyen kullanıcının ilan sahibiyle `messaging-service` üzerinden iletişim başlatabilmesi; LFP içinde ikinci bir mesajlaşma altyapısı oluşturulmaması.
- [ ] **LFP-17 — Gizlilik ve engelleme:** `social-service` tarafından yönetilen engelleme ve ilgili gizlilik kurallarının ilan etkileşimlerine uygulanması.
- [ ] **LFP-18 — Servis sınırları ve entegrasyon:** İlan verilerinin yalnızca LFP Service'te tutulması; oyun ve kullanıcıların `gameId` ve `userId` ile referanslanması. İlanı yalnızca sahibinin güncelleyebilmesi veya kapatabilmesi, bağlı oyunun `game-service` üzerinden doğrulanması. `auth-service`, `user-service`, `game-service`, `social-service`, `messaging-service` ve ortak `notification-service` ile sözleşme bazlı entegrasyon kurulması. Başka servislerin veritabanlarına doğrudan erişilmez.

---

## 4. Ortak teslim kriterleri

Aşağıdaki koşullar, yukarıdaki görevlerin tamamlanmış kabul edilebilmesi için geçerlidir:

- [ ] `social-service`, `messaging-service` ve `lfp-service` kendi PostgreSQL veritabanlarıyla çalışır; şema değişiklikleri Flyway ile yönetilir.
- [ ] API / Application / Domain / Infrastructure katman ayrımı, request/response DTO kullanımı, validasyon ve hata yönetimi proje mimarisiyle uyumludur.
- [ ] Arkadaşlık, davet kodu, engelleme, birebir/grup sohbetleri, mesaj etkileşimleri, dosya boyutu sınırı ve süreli LFP ilanları uygun unit/integration testleriyle doğrulanır.
- [ ] Mesajlaşmada yalnızca yetkili sohbet üyeleri erişebilir; grup yöneticisi yetkileri, okundu bilgisi ve iki ayrı silme davranışı test edilir.
- [ ] Servisler birbirlerinin veritabanlarına doğrudan erişmez; gerekli HTTP iletişiminde Spring RestClient, çok adımlı dağıtık işlemlerde kararlaştırılan Saga yaklaşımı uygulanır.
- [ ] Korunan işlemler Auth Service'in kimlik/rol altyapısına ve proje `SECURITY.md` kurallarına uygun yürütülür.
- [ ] SonarQube üzerinde Security, Reliability ve Maintainability kategorilerinde en az **A** hedeflenir.

**Görev sayısı:** 15 SOC + 27 MSG + 18 LFP = **60 numaralı görev** (MSG-21 sesli mesaj özelliği opsiyoneldir).

> `notification-service` ve `admin-service` ortak geliştirme kapsamındadır. Ayrı bir Search, Media veya Moderation Service oluşturulmaz; ilgili işlevler belirlenen servis sınırlarında kalır. `SECURITY.md` içeriği bu belge hazırlanırken doğrulanmadığından yalnızca uyum şartı belirtilmiştir.
