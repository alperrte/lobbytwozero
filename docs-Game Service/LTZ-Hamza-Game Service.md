# Hamza — LTZ PDA Taskları

**PDA kullanımı:** Her numaralı başlık bir **ANA TASK**, altındaki checkbox satırları aynı ana taskın **ALT TASK** kayıtlarıdır. Kaynak kodları benzersiz korunmuştur.

**Teslim standardı:** Her ana task bağımsız anlamlı commit/PR ile kapanır; Flyway, DTO, validation, exception, sahiplik/rol, servis sözleşmesi ve birim/entegrasyon testleri uygulanır. Ortak SonarQube A hedefi geçerlidir.

## game-service

**Game Service uygulama sırası:** Bu bölümdeki **6 ana task (faz)** aşağıdaki sırayla yürütülür. `LTZ-HAM-GAME-01` gibi kodlar ana task/faz kimliğini; `GAME-08` gibi kodlar ise alt task/gereksinim kimliğini ifade eder. Alt task kodundaki sayı, geliştirme sırası değildir. **İlk uygulanacak alt task `GAME-08`'dir.**

| Uygulama fazı | Ana task | Alt taskların kesin uygulama sırası |
| --- | --- | --- |
| **Faz 1** | `LTZ-HAM-GAME-01` — Kanonik Oyun Modeli ve Steam Katalog İndeksi | `GAME-08` → `GAME-09` → `GAME-10` → `GAME-16` → `GAME-18` → `GAME-21` |
| **Faz 2** | `LTZ-HAM-GAME-02` — Oyun Detayı Zenginleştirme ve Otomatik Senkronizasyon | `GAME-17` → `GAME-19` → `GAME-22` |
| **Faz 3** | `LTZ-HAM-GAME-03` — Oyun Keşfi, Arama, Filtreleme ve Popüler Oyunlar | `GAME-01` → `GAME-02` → `GAME-03` → `GAME-04` → `GAME-05` → `GAME-06` → `GAME-20` → `GAME-25` |
| **Faz 4** | `LTZ-HAM-GAME-04` — Yetkili Oyun Yönetimi ve Geliştirici Alanı | `GAME-07` → `GAME-12` → `GAME-13` → `GAME-15` |
| **Faz 5** | `LTZ-HAM-GAME-05` — Gelişmiş Katalog Metadata, Fiyat ve Düzeltmeler | `GAME-23` → `GAME-24` → `GAME-26` |
| **Faz 6** | `LTZ-HAM-GAME-06` — Genişletilmiş Oyun İçeriği ve Geliştirici Geri Bildirimi | `GAME-11` → `GAME-14` |

---

### LTZ-HAM-GAME-01 — Kanonik Oyun Modeli ve Steam Katalog İndeksi

**Sorumlu:** Hamza  
**Servis:** `game-service`  
**Faz:** MVP/P0  
**İlgili gereksinimler:** GAME-08, GAME-09, GAME-10, GAME-16, GAME-18, GAME-21

**Ana task açıklaması:** Dahili/harici oyunlar tek gameId üzerinden yönetilir; izinli Steam katalog kaynağıyla temiz yerel indeks oluşturulur.

**Alt tasklar:**

- [ ] **GAME-08 — Oyun alt verileri:** Tür/kategori/etiket, platform, geliştirici, yayıncı, medya ve sistem gereksinimleri ilişkili olarak yönetilir. **Kabul:** Tür, kategori ve etiket kavramları API'de karıştırılmaz; sağlayıcının sunmadığı alanlar zorunlu sayılmaz. Ayrı `media-service` oluşturulmaz.

- [ ] **GAME-09 — Harici oyun veri sağlayıcıları:** Sağlayıcı soyutlamasıyla arama, liste ve detay entegrasyonları ilgili sağlayıcının gerçekten desteklediği yeteneklere göre tanımlanır. **Kabul:** Sağlayıcı değişikliği frontend DTO'sunu değiştirmeye zorlamaz; kullanım izni, timeout, kota, hata kodları ve erişilemeyen yetenekler ele alınır.

- [ ] **GAME-10 — Tutarlı oyun görüntüleme modeli:** `INTERNAL` ve `EXTERNAL` kaynaklı oyunları aynı kart ve detay sözleşmesiyle döndürme. **Kabul:** Her kartta tek `gameId` bulunur; `origin`, `provider` ve `metadataCompleteness` benzeri alanlar birbirine karıştırılmaz. Harici oyun yerel DB'ye alınınca dahiliye dönüşmez.

- [ ] **GAME-16 — Yerel Steam katalog indeksi (P0):** İzinli uygulama listesi kaynağından `provider`, `externalId`, oyun adı ve kaynakta mevcut temel alanlar ile arama yapılabilir hafif indeks kurulması. İlk aktarım ve artımlı güncelleme ayrı iş akışlarıdır. **Kabul:** Büyük liste tek HTTP isteğine zorlanmaz; işlem tekrarlandığında mükerrer kayıt oluşmaz; indeks kapsamı/son güncelleme raporlanır.

- [ ] **GAME-18 — Kanonik oyun kimliği ve mükerrer kayıt kontrolü (P0):** LTZ `gameId` ile `(provider, externalId)` referanslarını ayıran yapı ve benzersizlik kısıtları kurulması. **Kabul:** Aynı Steam AppID tek oyuna karşılık gelir; ada bakarak farklı sağlayıcılardaki oyunlar kontrolsüz birleştirilmez; doğrulanmış eşleme/ayırma işlemleri mümkündür.

- [ ] **GAME-21 — Oyun türü ve katalog temizliği (P0):** Ana oyun, DLC, demo, soundtrack, yazılım, video, paket gibi sağlayıcı kayıtlarını kaynağın sunduğu güvenilir tür sinyalleriyle ayırma. **Kabul:** Genel ana oyun listesine uygunsuz kayıtlar karışmaz; belirsiz türler yanlışlıkla oyun diye varsayılmaz; filtreleme kuralları test edilir.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-GAME-02 — Oyun Detayı Zenginleştirme ve Otomatik Senkronizasyon

**2026-10-21 durumu:** GAME-19 ve GAME-22 uygulama/test kapsamında tamamlandı. GAME-17 altyapısı hazır; gerçek detay sağlayıcısı için veri kaynağı kararı ve entegrasyon onayı bekleniyor. Faz 2 bütünü tamamlandı sayılmaz. [Faz 2 raporu](LTZ-HAM-GAME-02-Rapor.md) · [Sağlayıcı araştırması](LTZ-HAM-GAME-02-Saglayici-Arastirmasi.md). Faz 3 ve Faz 4 kullanıcı onayıyla uygulandı; durumları aşağıdadır.

**Sorumlu:** Hamza  
**Servis:** `game-service`  
**Faz:** MVP/P0  
**İlgili gereksinimler:** GAME-17, GAME-19, GAME-22

**Ana task açıklaması:** Eksik oyun verileri izinli sağlayıcıdan dayanıklı biçimde zenginleştirilir, senkronizasyon ilerlemesi ve güncellik izlenir.

**Alt tasklar:**

- [ ] **GAME-17 — İsteğe bağlı detay zenginleştirme (P0):** İndeks kaydının eksik detaylarını, kullanılan sağlayıcının izinli ve desteklenen API'lerinden talep edip yerel kayda işleme. **Kabul:** Aynı oyun için eş zamanlı gereksiz tekrar istekleri sınırlandırılır; başarısız istekte mevcut detay korunur; yalnızca gerçekten erişilebilen alanlar saklanır.

- [x] **GAME-19 — Otomatik katalog senkronizasyonu (P0):** Sağlayıcı bazında zamanlanmış ilk indeksleme, artımlı güncelleme, yeniden deneme ve kaldığı yerden sürdürme akışları kurulması. **Kabul:** Kısmi hata tüm kataloğu silmez; senkronizasyon ilerlemesi ve son başarılı zaman izlenir; kota/rate limit dikkate alınır.

- [x] **GAME-22 — Önbellek, veri güncelliği ve hata toleransı (P0):** Detay TTL/yenileme politikası, stale-while-revalidate benzeri davranış, timeout, sınırlı retry, backoff, rate limit ve geçici erişim hatası yönetimi. **Kabul:** Sağlayıcı kesintisi yerel katalog listelemesini durdurmaz; değişken fiyat gibi hassas zamanlı alanlar güncellik doğrulanmadan güncel gösterilmez.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-GAME-03 — Oyun Keşfi, Arama, Filtreleme ve Popüler Oyunlar

**2026-10-21 durumu:** Kullanıcı onayıyla uygulandı. GAME-01/02/03/06/20/25 doğrulandı; GAME-04 ve GAME-05'in LTZ puanına bağlı kısmı Review Service sözleşmesi beklediğinden açık. Faz bütünü 8/8 tamamlandı sayılmaz. [Faz 3 raporu](LTZ-HAM-GAME-03-Rapor.md). GAME-17 gerçek sağlayıcı entegrasyonu bekliyor. Faz 4 kullanıcı onayıyla uygulandı.

**Sorumlu:** Hamza  
**Servis:** `game-service`  
**Faz:** MVP/P0  
**İlgili gereksinimler:** GAME-01, GAME-02, GAME-03, GAME-04, GAME-05, GAME-06, GAME-20, GAME-25

**Ana task açıklaması:** Yerel veriden hızlı, deterministik ve sayfalı oyun keşfi; detay, popüler kartlar ve ölçülen performans sunulur.

**Alt tasklar:**

- [x] **GAME-01 — Oyun listeleme:** Dahili oyunlar ve yerel indeksteki uygun harici oyunlar ortak katalog sözleşmesiyle, sunucu tarafında sayfalı listelenir. `page` / `size`, maksimum `size`, toplam sonuç ve deterministik ikincil sıralama tanımlanır. **Kabul:** Aynı sorgu/filtre için sayfalar arasında tekrarlanan veya atlanan kayıt üretilmez; genel liste Steam yanıtına bağımlı değildir.

- [x] **GAME-02 — Oyun detayları:** Açıklama, tür, platform, çıkış tarihi, geliştirici, yayıncı, dil desteği, sistem gereksinimleri ve mevcut medya bilgileri tek detay modeliyle sunulur. Harici veride bulunmayan alanlar isteğe bağlıdır. **Kabul:** Yalnızca hafif indeks kaydı olan oyun için kısmi yanıt verilebilir; eksik alanlar uydurulmaz ve GAME-17 üzerinden zenginleştirilebilir.

- [x] **GAME-03 — Oyun arama:** Oyun adına göre yerel katalogda arama yapılır; isim normalizasyonu, ilgili indeksler, boş arama davranışı, sayfalama ve mükerrer sonuç kontrolü tanımlanır. Ayrı `search-service` kurulmaz. **Kabul:** Önce yerel sonuç gösterilir; opsiyonel harici canlı arama temel deneyimi bloke etmez.

- [ ] **GAME-04 — Oyun filtreleme:** Tür, platform, LTZ puanı, popülerlik, çıkış tarihi, erken erişim ve dil desteği başta olmak üzere birden fazla filtre birlikte uygulanır. **Kabul:** Eksik metadata'nın filtre sonucu üzerindeki davranışı açıkça belirlenir; LTZ puanı Review Service'ten toplu API/okuma projeksiyonuyla alınır, doğrudan `review_db` okunmaz. İndirim filtresi GAME-24 koşullarına bağlıdır.

- [ ] **GAME-05 — Oyun sıralama:** Ada, çıkış tarihine, popülerliğe, uygun olduğu yerde LTZ puanına göre artan/azalan sıralama sunulur. **Kabul:** `gameId` gibi stabil bağlayıcı sıralama ile sayfalama tutarlıdır; eksik değerlerin konumu belgelenmiştir.

- [x] **GAME-06 — Popüler oyunlar:** Popüler oyunları ayrı endpoint/bölüm üzerinden sunma ve GAME-20'nin önceden hazırlanmış katalog verisini kullanma. **Kabul:** Popüler liste servis açılışında Steam'den tek tek detay beklemez; popülerlik sıralamasının hangi veriye dayandığı bilinir.

- [x] **GAME-20 — Popüler oyunları önceden hazırlama (P0):** İlk aşamada yönetilebilir editör listesi; daha sonra uygun veri mevcutsa LTZ etkileşimlerinden popülerlik puanı üretme ve bu oyunların detaylarını öncelikli zenginleştirme. **Kabul:** Popüler oyun kartları harici API beklemeden açılır; popülerlik ölçütü ve son hesaplama zamanı belirgindir.

- [x] **GAME-25 — Katalog performans ve entegrasyon testleri (P0):** Büyük indeks, eş zamanlı arama, sayfalama, eksik veri, tür temizliği, kısmi senkronizasyon ve sağlayıcı kesintisi için otomatik testler. **Kabul:** Hedeflenen veri hacmi ve test ortamı kaydedilir; yerel listeleme/arama için başlangıç ölçüm hedefi p95 ≤ 300 ms olarak değerlendirilir; hedefin karşılanması ölçümle doğrulanır, garanti varsayılmaz.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-GAME-04 — Yetkili Oyun Yönetimi ve Geliştirici Alanı

**2026-10-21 durumu:** Kullanıcı onayıyla uygulandı. GAME-07/12/13 Game Service sınırında uygulandı ve test edildi; GAME-15'in herkese açık toplu kart ucu hazır, Review Service puan tarafı sözleşme beklediğinden açık. **Yetkili uçlar geçici, Auth'tan bağımsız ve fail-closed JWT doğrulamasına dayanır; Auth Service entegrasyonu TAMAMLANMADI (NOT VERIFIED).** Faz bütünü üretime hazır sayılmaz. İkinci geçişte gerçek eşzamanlılık testleri, toplu uç rate limit'i, medya host doğrulaması ve CSRF incelemesi tamamlandı (182 test, %98,47, SonarQube PASSED); Gateway'den istemci IP iletimi onay bekliyor. [Faz 4 raporu](LTZ-HAM-GAME-04-Rapor.md) · [Auth entegrasyonu](LTZ-HAM-GAME-04-Auth-Entegrasyonu.md). Faz 5 kullanıcı onayıyla uygulandı; yerel kapsam ve bekleyen entegrasyonlar [Faz 5 raporunda](LTZ-HAM-GAME-05-Rapor.md) ayrılmıştır. Ek V171–V190 tahsisinden V171 kullanıldı.

**Sorumlu:** Hamza  
**Servis:** `game-service`  
**Faz:** MVP  
**İlgili gereksinimler:** GAME-07, GAME-12, GAME-13, GAME-15

**Ana task açıklaması:** Yetkili admin/geliştirici kendi kapsamındaki oyunları ve içeriklerini güvenle yönetir; servis sözleşmeleri korunur.

**Alt tasklar:**

- [x] **GAME-07 — Yetkili oyun yönetimi:** Admin veya yetkili kullanıcılar oyun ekleyebilir, güncelleyebilir, arşivleyebilir/silebilir. Yetki, sahiplik ve doğrulamalar uygulanır. **Kabul:** Kütüphane veya inceleme referansı bulunan bir oyunun fiziksel silinmesi veri kaybına yol açmaz; uygun durumlarda görünürlük kapatma/arşivleme kullanılır. Harici kaynaktan gelen alanlara yapılan manuel düzeltmeler senkronizasyonda kontrolsüz ezilmez.

- [x] **GAME-12 — Oyun geliştiricisi yetkileri:** Doğrulanmış geliştiriciler yalnızca yetkili oldukları oyunları yönetebilir. **Kabul:** Auth rolü ve oyun bazlı sahiplik/yetki ayrı ayrı kontrol edilir; kullanıcı başkasının oyununa değişiklik uygulayamaz.

- [x] **GAME-13 — Geliştirici oyun içerikleri:** Yetkili geliştirici oyun açıklaması, görselleri, video/demo URL'leri ve güncelleme notlarını oyun sayfasında yönetir. **Kabul:** Girdi doğrulaması, medya/bağlantı güvenliği ve moderasyon politikası uygulanır; içerik sahipliği belirgindir.

- [ ] **GAME-15 — Servis sınırları ve entegrasyon:** Game Service katalog verisinin sahibi olur; Library Service favori/koleksiyon, Review Service kullanıcı puanları/incelemeleri yönetir. `auth-service`, `library-service`, `review-service`, `content-service`, `hardware-service` ve gereken diğer servislerle sözleşme bazlı iletişim kurulur. **Kabul:** Çoklu oyun kartı sorgusu ve puan özetinde N+1 HTTP çağrısı önlenir; timeout, kısmi başarısızlık ve erişim yetkileri tanımlanır; başka veritabanına erişilmez.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-GAME-05 — Gelişmiş Katalog Metadata, Fiyat ve Düzeltmeler

**Sorumlu:** Hamza  
**Servis:** `game-service`  
**Faz:** MVP sonrası / P1, koşullu  
**İlgili gereksinimler:** GAME-23, GAME-24, GAME-26

**Ana task açıklaması:** Kaynak/yerelleştirme izlenebilirliğini, izinli fiyat/indirim verisini ve manuel metadata düzeltmelerini geliştir.

**2026-10-21 durumu:** GAME-23 yerel/admin kapsamı uygulandı; gerçek detay sağlayıcısı bekler. GAME-24 bölgesel fiyat portu hazır; izinli sağlayıcı ve erişim olmadan fiyat sunulmaz. GAME-26 mevcut arşivleme/kilit/audit akışlarını yeniden kullanır; yeni ADMIN eşleme/ayırma girişleri dış referans koordinasyonu olmadan mutasyonu engeller. GAME-24 ve GAME-26 bu nedenle bütünüyle tamamlandı olarak işaretlenmedi. [Faz 5 raporu](LTZ-HAM-GAME-05-Rapor.md). Faz 6 başlamadı.

**Alt tasklar:**

- [x] **GAME-23 — Yerelleştirme ve veri kaynağı izlenebilirliği (P1):** Oyun metadata dilini, dil destek türlerini (arayüz/ses/altyazı), kaynağı, son doğrulama zamanını ve boş/yanlış veri durumlarını ayırt etme. **Kabul:** Türkçe desteği yanlış `false` kabul etmeye yol açan metin/parsing senaryoları test edilir; API bilinmeyen değeri `false` gibi göstermez.

- [ ] **GAME-24 — İndirim ve fiyat bilgileri (P1, koşullu):** İzinli ve sürdürülebilir veri kaynağı bulunursa mağaza, ülke/bölge, para birimi, indirim ve fiyat güncelliğinin yönetilmesi. **Kabul:** Lisans/erişim uygun değilse görev ertelenir; fiyatlar bölge ve güncellik bilgisi olmadan evrensel doğruymuş gibi gösterilmez.

- [ ] **GAME-26 — Manuel metadata düzeltme ve arşivleme (P1):** Admin'in hatalı indeks/veri eşlemelerini düzeltmesi, oyunları arşivlemesi ve harici veriye göre yapılan manuel değişiklikleri ayrı takip etmesi. **Kabul:** Geçerli manuel değişiklikler sonraki otomatik güncellemede sessizce kaybolmaz; işlem kaydı vardır.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-GAME-06 — Genişletilmiş Oyun İçeriği ve Geliştirici Geri Bildirimi

**Sorumlu:** Hamza  
**Servis:** `game-service`  
**Faz:** MVP sonrası  
**İlgili gereksinimler:** GAME-11, GAME-14

**Ana task açıklaması:** Başarım/rehber gibi modüler detaylar ve ayrı geri bildirim iş akışlarını hayata geçir.

**Alt tasklar:**

- [ ] **GAME-11 — Genişletilebilir oyun detayları (MVP sonrası):** Başarımlar, rehberler, easter egg'ler ve kullanıcı deneyimleri için modüler detay alanları. İçeriğin sahibi başka servis ise API üzerinden entegre edilir; veri sahipliği taşınmaz.

- [ ] **GAME-14 — Geliştirici geri bildirimleri (MVP sonrası olarak yeniden planlandı):** Oyuncuların geliştiriciye oyun bağlamında geri bildirim, hata raporu ve erken erişim geri bildirimi göndermesi; ayrı durum takibi ve yetki kurallarının tasarlanması. **Gerekçe:** Rapor yönetimi ve iş akışı kapsamı çekirdek oyun kataloğundan bağımsız ve geniştir.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.


## library-service

### LTZ-HAM-LIB-01 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü

**Sorumlu:** Hamza  
**Servis:** `library-service`  
**Faz:** MVP/P0  
**İlgili gereksinimler:** LIB-01, LIB-02, LIB-12, LIB-13, LIB-15

**Ana task açıklaması:** Geçerli oyunlar tek kullanıcı-oyun kaydına eklenir/kaldırılır; sahiplik, atomiklik ve tekrar koruması sağlanır.

**Alt tasklar:**

- [ ] **LIB-01 — Kişisel oyun kütüphanesi:** Kullanıcı geçerli LTZ `gameId` ile koleksiyonuna oyun ekleyebilir. Favori veya bitirildi işareti zorunlu değildir; sahiplik ispatı anlamına gelmez.

- [ ] **LIB-02 — Koleksiyondan oyun kaldırma:** Kullanıcı kendi kaydını silebilir; bu işlem favori/bitirildi alanlarıyla tutarlı ve atomik yürür. Kaldırılan oyun için aynı kullanıcıya ait artık koleksiyon durumu kalmaz.

- [ ] **LIB-12 — Mükerrer işlem kontrolü:** `(userId, gameId)` benzersizliği ve favori/bitirildi işlemlerinde idempotent davranış sağlanır; eş zamanlı istekler yinelenen kayıt oluşturmaz.

- [ ] **LIB-13 — Kimlik ve oyun doğrulama:** İşlemler JWT/rol ve sahiplik kontrolünden geçer; `gameId` Game Service üzerinden doğrulanır. İstemcinin gönderdiği farklı bir `userId` ile başkasının koleksiyonu değiştirilemez.

- [ ] **LIB-15 — Favori/bitirildi işleminde otomatik koleksiyon kaydı (P0):** Kütüphanede bulunmayan geçerli bir oyunun favorilenmesi veya bitirildi işaretlenmesi koleksiyon kaydını idempotent şekilde oluşturur. **Kabul:** Tek kullanıcı aksiyonu yeterlidir; otomatik eklenen kayıtta yalnızca istenen durum işaretlenir.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-LIB-02 — Favori ve Bitirildi İşlemleri

**Sorumlu:** Hamza  
**Servis:** `library-service`  
**Faz:** MVP  
**İlgili gereksinimler:** LIB-03, LIB-04

**Ana task açıklaması:** Oyunun önce koleksiyona eklenmesi gerekmeden favori/bitirildi işaretlenebilir, geri alınabilir ve listelenebilir.

**Alt tasklar:**

- [ ] **LIB-03 — Favori oyunlar:** Kullanıcı oyunları favoriye ekleyebilir, favoriden çıkarabilir ve listeleyebilir. Koleksiyon kaydı yoksa LIB-15'e göre otomatik oluşturma yapılır; kullanıcıdan ayrı bir 'önce kütüphaneye ekle' adımı istenmez.

- [ ] **LIB-04 — Bitirilen oyunlar:** Kullanıcı oyunları bitirildi olarak işaretleyebilir, geri alabilir ve listeleyebilir. Koleksiyon kaydı yoksa LIB-15'e göre otomatik oluşturma yapılır.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-LIB-03 — Kütüphane Listeleme, Filtreleme ve İstatistik

**Sorumlu:** Hamza  
**Servis:** `library-service`  
**Faz:** MVP/P0  
**İlgili gereksinimler:** LIB-05, LIB-06, LIB-07, LIB-08, LIB-09, LIB-16

**Ana task açıklaması:** Oyunlar toplu Game Service sorgularıyla listelenir; doğru filtre/sıra/sayfa ve istatistik döndürülür.

**Alt tasklar:**

- [ ] **LIB-05 — Kütüphane listeleme:** Kullanıcının koleksiyon kayıtları, favori ve bitirildi bilgileriyle listelenir. Görsel/oyun adı gerekiyorsa LIB-16 ile toplu Game Service sorgusu yapılır; oyun başına ayrı HTTP isteği gönderilmez.

- [ ] **LIB-06 — Kütüphane filtreleme:** Favori ve bitirildi durumları tek başına veya birlikte uygulanabilir; filtrelerin davranışı açıkça belgelenir.

- [ ] **LIB-07 — Kütüphane sıralama:** Oyun adına ve koleksiyona ekleme tarihine göre artan/azalan sıralama sunulur. Oyun adı gibi Game Service'ten gelen alanla sıralama gerekiyorsa N+1 üretmeyen plan kullanılır.

- [ ] **LIB-08 — Sayfalama:** Liste/filtre/sıralama işlemleriyle tutarlı `page` / `size` desteği sağlanır; stabil ikinci sıralama kullanılır.

- [ ] **LIB-09 — Kütüphane istatistikleri:** Toplam oyun, favori ve bitirilen oyun sayıları tutarlı hesaplanır; idempotent tekrarlar sayıları artırmaz.

- [ ] **LIB-16 — Toplu oyun özeti entegrasyonu (P0):** Kütüphane ekranı için çoklu `gameId` değerinin Game Service'ten tek/belirli sayıda toplu API çağrısıyla alınması. **Kabul:** 50 oyun görüntülemek 50 ayrı Game Service HTTP çağrısı gerektirmez; arşivlenmiş veya geçici bulunamayan oyunlar kütüphane kaydını bozmaz.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-LIB-04 — Kütüphane Gizliliği ve Servis Entegrasyonları

**Sorumlu:** Hamza  
**Servis:** `library-service`  
**Faz:** MVP/P0  
**İlgili gereksinimler:** LIB-10, LIB-11, LIB-14, LIB-18

**Ana task açıklaması:** Profil gizliliği, Recommendation özet erişimi ve eşzamanlı işlemler API seviyesinde test edilerek güvenceye alınır.

**Alt tasklar:**

- [ ] **LIB-10 — Profil entegrasyonu:** User Service'in profil akışına sözleşme bazlı, yetkili ve görünürlük kurallarına uygun koleksiyon verisi sunulur.

- [ ] **LIB-11 — Gizlilik kontrolleri:** User Service tarafından sahiplenilen görünürlük tercihleri Library Service'in sunucu taraflı erişim denetiminde uygulanır. **Kabul:** Bir kişinin gizli koleksiyonu yalnızca frontend'de saklanmış olmaz; yetkisiz istek ve User Service'e erişilememe senaryosu gizli veriyi açığa çıkarmaz.

- [ ] **LIB-14 — Recommendation entegrasyonu:** Recommendation Service yalnızca izin verilen favori ve bitirilmiş oyun verisini sözleşmeli API üzerinden alır; Library Service veri sahibi olarak kalır.

- [ ] **LIB-18 — Yetki, gizlilik ve tutarlılık testleri (P0):** Kullanıcı izolasyonu, gizli profil, mükerrer ekleme, eş zamanlı favorileme, bitirildi işaretleme ve koleksiyon silme senaryolarını test etme. **Kabul:** Yetkisiz erişim API düzeyinde engellenir ve DB benzersizlik kuralları sınanır.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-LIB-05 — Arşivlenen Oyunlarda Kütüphane Dayanıklılığı

**Sorumlu:** Hamza  
**Servis:** `library-service`  
**Faz:** MVP sonrası / P1  
**İlgili gereksinimler:** LIB-17

**Ana task açıklaması:** Katalogdan kaldırılan/arşivlenen oyunlar kişisel koleksiyon geçmişini kaybettirmez.

**Alt tasklar:**

- [ ] **LIB-17 — Katalog değişikliklerine dayanıklılık (P1):** Game Service'te oyun arşivlendiğinde, harici sağlayıcıdan kaldırıldığında veya metadata eksildiğinde kişisel koleksiyon durumunun korunması ve uygun fallback görüntüleme yapılması. **Kabul:** Arşivleme, kullanıcının favori/bitirildi geçmişini sessizce silmez.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.


## review-service

### LTZ-HAM-REV-01 — Oyun Puanlama ve İnceleme Oluştur/Düzenle/Sil

**Sorumlu:** Hamza  
**Servis:** `review-service`  
**Faz:** MVP/P0  
**İlgili gereksinimler:** REV-01, REV-02, REV-03, REV-04, REV-05, REV-11

**Ana task açıklaması:** Sayısal LTZ puanı, metin incelemesi ve olumlu/olumsuz tavsiye ayrı kurallarla yönetilir.

**Alt tasklar:**

- [ ] **REV-01 — Oyun puanlama:** Kullanıcı LTZ oyun kimliğine sayısal puan verir/günceller; puan aralığı ve tek aktif kullanıcı-oyun puanı kuralı REV-11'de açıkça tanımlanır. Puanın metinsel incelemeden bağımsız verilip verilemeyeceği sözleşmede belirlenir (öneri: verilebilir).

- [ ] **REV-02 — İnceleme oluşturma:** Kullanıcı bir oyun için metinsel inceleme yayımlar. İnceleme `gameId` ve `userId` ile ilişkilendirilir; başkasının adına inceleme oluşturulamaz. Önerilen kural: kullanıcı başına oyun başına en fazla bir aktif inceleme.

- [ ] **REV-03 — İnceleme güncelleme:** Kullanıcı kendi incelemesini düzenleyebilir; düzenleme tarihi, varsa puan/tavsiye alanının geçerliliği ve sahiplik kontrolü korunur.

- [ ] **REV-04 — İnceleme silme:** Kullanıcı kendi incelemesini silebilir; yönetici/moderasyon işlemleri ayrı yetkilerle yürütülür. İçeriğin silinmesi ile puanın silinmesi arasındaki kural açıkça belirlenir.

- [ ] **REV-05 — Olumlu / olumsuz değerlendirme:** Steam benzeri öneriyorum/önermiyorum görüşü desteklenir ve oyun sayfasında gösterilir. **Kabul:** Bu görüş sayısal LTZ puanından ayrı alandır; 7/10 puanı otomatik olumlu/olumsuz kabul edilmez.

- [ ] **REV-11 — Tekil değerlendirme ve puanlama kuralları (P0):** Sayısal puan ölçeği, puan güncelleme, öneriyorum/önermiyorum alanı ve kullanıcı-oyun başına maksimum aktif kayıt kuralları tasarlanır. **Kabul:** Aynı kullanıcının tekrar isteği ortalamayı mükerrer etkilemez; metin incelemesi, tavsiye ve sayısal puanın birbirinden bağımsızlığı test edilir.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-REV-02 — İnceleme Beğenileri, Raporlama ve Öne Çıkarma

**Sorumlu:** Hamza  
**Servis:** `review-service`  
**Faz:** MVP  
**İlgili gereksinimler:** REV-06, REV-07, REV-09, REV-10

**Ana task açıklaması:** İnceleme etkileşimleri, moderasyona bildirim ve yetkili servis entegrasyonları tutarlı işler.

**Alt tasklar:**

- [ ] **REV-06 — İnceleme beğenileri:** Beğenme/beğeniyi kaldırma sunulur; `(userId, reviewId)` benzersizliği ve idempotent işlem uygulanır.

- [ ] **REV-07 — Uygunsuz inceleme raporlama:** Kullanıcı gerekçe belirterek rapor oluşturabilir; rapor durumları ve yetkili işlem akışı Admin Service ile sözleşmeli olarak yürütülür. Mükerrer/spam raporlar REV-13 kapsamına alınır.

- [ ] **REV-09 — Öne çıkan incelemeler:** En faydalı incelemeler belgelenmiş sıralama sinyalleriyle listelenir; beğeni sayısı dışında oluşturulma tarihi, şikâyet ve moderasyon durumu gibi etkiler değerlendirilir.

- [ ] **REV-10 — Servis sınırları ve entegrasyon:** İnceleme, puan ve beğeniler Review Service'e; oyun kimliği Game Service'e, kullanıcı profili User Service'e aittir. `game-service`, `user-service`, `admin-service` ve gerektiğinde `notification-service` ile sözleşmeli entegrasyon yapılır. Başka servis veritabanına doğrudan erişilmez.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-REV-03 — Oyun Puan Ortalaması ve Toplu Özet API

**Sorumlu:** Hamza  
**Servis:** `review-service`  
**Faz:** MVP/P0  
**İlgili gereksinimler:** REV-08, REV-12

**Ana task açıklaması:** Tekli/çoklu oyunlara ait geçerli LTZ puan ortalamaları Game Service tarafından N+1 oluşturmadan okunur.

**Alt tasklar:**

- [ ] **REV-08 — Ortalama oyun puanı:** Her oyun için LTZ kullanıcılarının geçerli sayısal puanlarından ortalama ve puan sayısı sunulur. **Kabul:** Steam mağaza değerlendirme oranları bu alanla karıştırılmaz; çoklu oyun özeti REV-12'den sağlanır.

- [ ] **REV-12 — Toplu oyun puanı özetleri (P0):** Çoklu `gameId` için ortalama LTZ puanı, puan sayısı ve uygun temel istatistiği tek istekte sunma. **Kabul:** Game Service'in oyun listelemesi oyun başına tek tek Review Service sorgusu yapmaz; yorum/puan olmayan oyunların boş değerleri açıkça tanımlanır.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-HAM-REV-04 — Gelişmiş İnceleme Moderasyonu ve Analitik

**Sorumlu:** Hamza  
**Servis:** `review-service`  
**Faz:** MVP sonrası / P1  
**İlgili gereksinimler:** REV-13, REV-14, REV-15

**Ana task açıklaması:** Spam önlemleri, istatistik dağılımları ve moderasyon sonrası görünürlük politikası uygulanır.

**Alt tasklar:**

- [ ] **REV-13 — Spam ve kötüye kullanım kontrolü (P1):** Rapor/beğeni ve inceleme oluşturma işlemlerinde uygun oran sınırı, mükerrer rapor kuralı ve kötüye kullanım denetimleri. **Kabul:** Sıradan kullanıcı faaliyetlerini bozmadan tekrar/spam senaryoları test edilir.

- [ ] **REV-14 — İnceleme ve puan istatistikleri (P1):** Sayısal puan dağılımı, toplam aktif inceleme sayısı ve olumlu/olumsuz tavsiye oranlarının ayrı hesaplanması. **Kabul:** İnceleme adedi ile sayısal puan veren kullanıcı adedi karıştırılmaz.

- [ ] **REV-15 — Moderasyon sonrası içerik davranışı (P1):** İncelemenin görünür/gizli/arşivli durumları, raporun kapatılması ve moderasyon eylemlerinin sonuçları netleştirilir. **Kabul:** Gizlenmiş inceleme genel listede gösterilmez; kullanıcı puanı ve ortalama üzerindeki etki açık politika ile tutarlı uygulanır.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

