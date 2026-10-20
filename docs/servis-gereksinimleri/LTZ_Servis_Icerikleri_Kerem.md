# LTZ — Servis İçerikleri | Kerem

**Sorumlu geliştirici:** Kerem  
**İş alanı:** Kimlik Doğrulama, Kullanıcı Yönetimi ve E-posta Altyapısı  
**Servisler:** `auth-service`, `user-service`, `mail-service`  
**Veritabanları:** `auth_db`, `user_db`, `mail_db`

Bu belge, Kerem'e atanacak geliştirme görevlerini içerir. Her `AUTH-XX`, `USER-XX` ve `MAIL-XX` maddesi ayrı bir görev olarak takip edilebilir. Tüm servisler Java Spring Boot, PostgreSQL, Flyway, Maven, Docker ve üzerinde anlaşılan `api` / `application` / `domain` / `infrastructure` katmanlı yapısına uygun geliştirilecektir.

---

## 1. Auth Service

**Servis:** `auth-service`  
**Veritabanı:** `auth_db`  
**Amaç:** LTZ kullanıcılarının hesap oluşturmasını, güvenli biçimde giriş yapmasını ve sistemdeki yetkilerinin merkezi olarak yönetilmesini sağlamak.

### Görevler

- [ ] **AUTH-01 — Kullanıcı kaydı:** E-posta, kullanıcı adı ve parola ile hesap oluşturma. Mükerrer e-posta/kullanıcı adı engellenir.
- [ ] **AUTH-02 — E-posta doğrulama:** Kayıt olan kullanıcıya **6 haneli doğrulama kodu** gönderilmesini sağlama. Kullanıcı kodu girerek e-postasını doğrular ve kayıt akışına devam eder. **E-postasını doğrulayamayan kullanıcı kayıt akışında ilerleyemez.** Kodun üretilmesi ve doğrulanması Auth Service'e, gönderilmesi Mail Service'e aittir.
- [ ] **AUTH-03 — Kullanıcı girişi:** E-posta **veya kullanıcı adı** ile parola kullanarak güvenli giriş yapma.
- [ ] **AUTH-04 — 2FA ile giriş:** İki faktörlü kimlik doğrulama için giriş akışının ve gerekli endpointlerin sağlanması.
- [ ] **AUTH-05 — Steam ile giriş:** Steam hesabı üzerinden giriş yapılabilmesi için gerekli kimlik doğrulama endpointlerinin sağlanması.
- [ ] **AUTH-06 — Google (Gmail) ile giriş:** Google hesabı ile giriş yapılabilmesi için gerekli kimlik doğrulama endpointlerinin sağlanması.
- [ ] **AUTH-07 — JWT altyapısı:** JWT access token oluşturma, refresh token ile yenileme, refresh token rotation, süre sonu ve iptal kontrollerinin tek bir kimlik doğrulama altyapısı altında yönetilmesi.
- [ ] **AUTH-08 — Oturum kapatma:** Kullanıcının çıkış yapabilmesi; ilgili refresh tokenın geçersizleştirilmesi ve iptal edilmiş tokenla yeniden oturum açılamaması.
- [ ] **AUTH-09 — Şifre değiştirme:** Mevcut parolanın doğrulanmasıyla yeni parola belirlenmesi.
- [ ] **AUTH-10 — Şifremi unuttum:** Tek kullanımlık, süreli parola sıfırlama doğrulaması üzerinden yeni parola belirlenmesi. E-posta gönderimi Mail Service üzerinden yapılır.
- [ ] **AUTH-11 — Rol yönetimi:** Aşağıdaki rol/aktör tanımlarının merkezi yönetimi. Diğer servisler yeni rol ihtiyacını Auth Service sorumlusuna iletir; rol ekleme ve yetkilendirme değişiklikleri Auth Service ile koordineli yürütülür.
- [ ] **AUTH-12 — Hesap durumları:** Aktif, askıya alınmış, yasaklanmış ve devre dışı hesapların yönetimi; kısıtlı hesapların korumalı işlemlerinin engellenmesi.
- [ ] **AUTH-13 — Hesap güvenliği:** Hatalı giriş sınırlaması, brute-force koruması, güvenli parola hashleme. Ayrıca **`SECURITY.md` içerisindeki güvenlik kurallarına uyulması**.
- [ ] **AUTH-14 — Yetkilendirme (RBAC):** Korunan endpointlerde rol ve izin kontrolü yapılması; yetkisiz kimlik için 401, yetersiz yetki için 403 davranışının uygulanması. Kontroller yalnızca arayüze veya Gateway'e bırakılmaz.
- [ ] **AUTH-15 — Güvenlik olayları:** Başarılı/başarısız girişler, parola değişiklikleri ve kritik oturum olaylarının hassas bilgi sızdırmadan kaydedilmesi.
- [ ] **AUTH-16 — Hesap ve veri silme:** Kullanıcının hesabını ve kendisine ait verilerin silinmesini talep edebilmesi. **GitHub'da repository silmeye benzer ek güvenlikli**, yeniden kimlik doğrulama ve açık kullanıcı onayı içeren bir silme süreci. Diğer servislerdeki kullanıcı verilerinin silinmesi için koordinasyon sağlanır.
- [ ] **AUTH-17 — Servis sınırları ve entegrasyon:** Parola/token sahipliğinin Auth Service'te tutulması; profil verisinin User Service'te, e-posta tesliminin Mail Service'te kalması. `user-service`, `mail-service`, `admin-service` ve gerektiğinde `notification-service` ile sözleşme bazlı entegrasyonların kurulması. Diğer servislerin Auth veritabanına doğrudan erişmemesi.

### AUTH-11 — Rol / aktör tanımları

1. **Misafir Kullanıcı:** Hesap oluşturmadan erişime açık oyun ve içerikleri görüntüler; kayıt/giriş akışına erişir.
2. **Kayıtlı Oyuncu:** Profil, oyun keşfi, favori, inceleme, arkadaşlık, mesajlaşma, bildirim ve kişiselleştirme işlevlerini kullanır.
3. **İçerik Üreticisi:** Rehber, liste, inceleme ve topluluk içeriği üretir; normal kullanıcı yetkilerine ek görünürlük işlevlerinden yararlanabilir.
4. **Oyun Geliştiricisi:** Kendi oyununa ilişkin tanıtım, güncelleme, demo/bağlantı ve geri bildirim alanlarını yönetir.
5. **Moderatör:** Raporlanan inceleme, gönderi ve kullanıcı davranışlarını inceler; politika kapsamında moderasyon işlemi yapar.
6. **Sistem Yöneticisi:** Kullanıcı, içerik, oyun, rapor ve sistem yönetim alanlarına yetkili olarak erişir.
7. **Harici Oyun Veri Sağlayıcısı:** Oyun detayları, görseller, platform/kategori veya benzeri harici verileri sağlar.
8. **Bağlı Platform Servisleri:** Steam/Epic/Discord gibi kullanıcı izniyle bağlanabilecek hesap/platform servislerini temsil eder.
9. **E-posta / Bildirim Sağlayıcısı:** Hesap veya sistem bildirimlerinin dış kanaldan iletilmesini sağlar.
10. **Sistem / Zamanlayıcı:** Otomatik bildirim, bakım, senkronizasyon, görev veya sistem olaylarını tetikleyen teknik aktördür.

> Bu liste gereksinim dokümanındaki aktör tanımlarını korur. **User Service'teki Epic Games hesap bağlama özelliği bu çalışma kapsamında kaldırılmıştır**; 8. satırdaki ifade yalnızca orijinal aktör tanımının korunması içindir. Anonim kullanıcı, insan kullanıcı rolü ve sistem/sağlayıcı kimliği teknik yetkilendirme modelinde doğru şekilde ayrıştırılmalıdır.

---

## 2. User Service

**Servis:** `user-service`  
**Veritabanı:** `user_db`  
**Amaç:** LTZ oyuncularının kişisel profillerini, tercihlerini, izin verilen gizlilik ayarlarını ve bağlı hesaplarını yönetmek.

### Görevler

- [ ] **USER-01 — Oyuncu profili:** Her kayıtlı kullanıcı için benzersiz bir oyuncu profili oluşturulması.
- [ ] **USER-02 — Profil düzenleme:** Görünen ad, biyografi, oyuncu türü, favori oyun türleri ve kişisel bilgilerin güncellenmesi.
- [ ] **USER-03 — Profil fotoğrafı:** Avatar yükleme, değiştirme, kaldırma ve güvenli dosya doğrulama.
- [ ] **USER-04 — Kapak görseli:** Profil banner/kapak görseli yönetimi.
- [ ] **USER-05 — Herkese açık profil:** Oyuncu profilinin izin verilen alanlarının diğer kullanıcılar tarafından görüntülenmesi. **Profilin tamamını gizliye alma seçeneği bulunmaz.**
- [ ] **USER-06 — Gizlilik ayarları:** Arkadaş listesi, oyun geçmişi ve donanım gibi **izin verilen profil alanlarının** görünürlüğünün yönetilmesi. Kullanıcı profilini bütünüyle gizleyemez; gizlilik kuralları API seviyesinde uygulanır.
- [ ] **USER-07 — Hesap bağlantıları:** **Steam ve Discord** hesaplarının kullanıcı izniyle bağlanması. **Epic Games bağlantısı bu servisin kapsamından çıkarılmıştır.**
- [ ] **USER-08 — Bağlı hesap yönetimi:** Steam/Discord bağlantı durumunu görüntüleme, bağlantıyı kaldırma ve izinleri yönetme.
- [ ] **USER-09 — Onboarding:** Yeni üyenin favori oyun türlerini, platformlarını ve oyuncu tercihlerini seçebileceği ilk kurulum akışı.
- [ ] **USER-10 — Kullanıcı tercihleri:** Türkçe/İngilizce dil, açık/koyu tema ve kullanıcıya özel arayüz tercihleri.
- [ ] **USER-11 — Oyuncu arama:** Kullanıcı adı veya izin verilen profil bilgileriyle oyuncu arama. Ayrı Search Service kullanılmaz.
- [ ] **USER-12 — Profil özetleri:** Oyun koleksiyonu, incelemeler ve arkadaşlık gibi alanların diğer servislerden gelen, erişim iznine uygun özetlerini gösterme.
- [ ] **USER-13 — Servis sınırları ve entegrasyon:** Parola ve refresh token saklamama; kimlik bilgisini Auth Service'ten alma. Steam/Discord bağlantılarında izin verilen resmi yöntemleri kullanma ve gerekli erişim bilgilerini güvenli tutma. Profil görsellerinin yönetimini servis içinde yürütme; ayrı Media Service kullanmama. `auth-service`, `library-service`, `social-service`, `review-service` ve `hardware-service` ile sözleşme bazlı bağlantı kurma. Başka servislerin tablolarına doğrudan erişmeme.

**Kapsam dışına çıkarılan madde:** Önceki listede bulunan **“Profil devre dışı bırakma”** görevi kaldırılmıştır. Tam hesap ve veri silme süreci `auth-service` kapsamındadır. Profil alanları için izinli gizlilik seçenekleri devam eder.

---

## 3. Mail Service

**Servis:** `mail-service`  
**Veritabanı:** `mail_db`  
**Amaç:** LTZ sistem e-postalarının güvenli, şablonlu, izlenebilir ve hata toleranslı biçimde gönderilmesini sağlamak.

### Görevler

- [ ] **MAIL-01 — E-posta doğrulama mesajları:** Yeni kullanıcı kayıtlarında Auth Service tarafından oluşturulan **6 haneli doğrulama kodunun** e-posta ile gönderilmesi.
- [ ] **MAIL-02 — Şifre sıfırlama e-postası:** Auth Service tarafından oluşturulan parola sıfırlama bağlantılarının veya doğrulama bilgilerinin gönderilmesi.
- [ ] **MAIL-03 — Güvenlik e-postaları:** Parola değişikliği, şüpheli giriş ve kritik hesap olaylarına ilişkin bilgilendirme.
- [ ] **MAIL-04 — Hesap durumu e-postaları:** Hesap askıya alma veya yasaklama işlemlerine ilişkin bilgilendirme.
- [ ] **MAIL-05 — Hoş geldin e-postası:** Yeni üyelere LTZ platformuna katılım mesajı gönderme.
- [ ] **MAIL-06 — Etkinlik e-postaları:** Kullanıcının izin verdiği etkinlik davetlerinin ve hatırlatmalarının gönderilmesi.
- [ ] **MAIL-07 — Bildirim e-postaları:** Kullanıcının bildirim tercihlerine göre uygun uygulama bildirimlerinin e-posta ile iletilmesi.
- [ ] **MAIL-08 — HTML e-posta şablonları:** LTZ logosunu ve kurumsal tasarımını kullanan responsive e-posta şablonlarının hazırlanması.
- [ ] **MAIL-09 — Çoklu dil desteği:** Türkçe ve İngilizce e-posta içeriklerinin desteklenmesi.
- [ ] **MAIL-10 — SMTP entegrasyonu:** Seçilecek e-posta sağlayıcısı üzerinden güvenli e-posta gönderimi.
- [ ] **MAIL-11 — Gönderim kuyruğu:** E-posta taleplerinin kalıcı olarak kaydedilmesi ve gönderim işleyicisi tarafından işlenmesi.
- [ ] **MAIL-12 — Retry mekanizması:** Geçici gönderim hatalarında kontrollü tekrar deneme.
- [ ] **MAIL-13 — Gönderim durumları:** Bekliyor, işleniyor, gönderildi ve başarısız gibi durumların takip edilmesi.
- [ ] **MAIL-14 — Idempotency:** Tekrarlanan gönderim taleplerinin gereksiz mükerrer e-postaya dönüşmesinin önlenmesi.
- [ ] **MAIL-15 — Gönderim geçmişi:** Mesaj türü, alıcı, zaman, deneme sayısı ve sağlayıcı sonuçlarının güvenli biçimde kaydedilmesi.
- [ ] **MAIL-16 — Servis sınırları ve entegrasyon:** SMTP yapılandırmasının Mail Service'te tutulması; doğrulama tokenı üretmeme ve parola değiştirmeme. E-posta gönderiminin ana iş akışını gereksiz yere bloke etmemesi, SMTP hatalarında kayıp yaşanmaması, hassas tokenların loglanmaması. `auth-service`, `notification-service`, `admin-service` ve `event-service` ile sözleşme bazlı entegrasyonların kurulması. Uygulama içi bildirimlerin `notification-service` sorumluluğunda kalması.

---

## 4. Ortak teslim kriterleri

Aşağıdaki koşullar, yukarıdaki görevlerin tamamlanmış kabul edilebilmesi için geçerlidir:

- [ ] `auth-service`, `user-service` ve `mail-service` kendi PostgreSQL veritabanlarıyla çalışır; şema değişiklikleri Flyway ile yönetilir.
- [ ] Servislerin API / Application / Domain / Infrastructure katman ayrımı, request/response DTO kullanımı ve hata yönetimi proje mimarisiyle uyumludur.
- [ ] Her servisin yetki, doğrulama, hata ve kritik iş akışları uygun unit/integration testleriyle doğrulanır.
- [ ] Servisler birbirlerinin veritabanına doğrudan erişmez; gerekli HTTP iletişiminde Spring RestClient, çok adımlı dağıtık işlemlerde kararlaştırılan Saga yaklaşımı uygulanır.
- [ ] Güvenlik uygulamaları `SECURITY.md` kurallarına uygun olur.
- [ ] SonarQube üzerinde Security, Reliability ve Maintainability kategorilerinde en az **A** hedeflenir.

**Görev sayısı:** 17 AUTH + 13 USER + 16 MAIL = **46 numaralı görev**.

> `SECURITY.md` dosyasının içeriği bu belge hazırlanırken erişilebilir değildi; burada yalnızca dosyaya uyum şartı korunmuştur, dosyada bulunmayan ek kurallar uydurulmamıştır.
