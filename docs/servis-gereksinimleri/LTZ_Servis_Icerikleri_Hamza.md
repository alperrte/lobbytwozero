# LTZ — Servis İçerikleri | Hamza (Güncellenmiş Görev Planı)

**Sorumlu geliştirici:** Hamza  
**İş alanı:** Oyun Kataloğu, Kişisel Oyun Kütüphanesi ve İnceleme Sistemi  
**Servisler:** `game-service`, `library-service`, `review-service`  
**Veritabanları:** `game_db`, `library_db`, `review_db`  
**Teknolojiler (mevcut görev belgesine göre):** Java Spring Boot, PostgreSQL, Flyway, Maven, Docker  
**Backend katmanları:** `api` / `application` / `domain` / `infrastructure`  
**Durum:** Planlama dokümanı; tamamlandı işaretleri mevcut kodun doğrulandığı anlamına gelmez.

Bu belge, Hamza'ya atanacak bağımsız geliştirme görevlerini içerir. `GAME-XX`, `LIB-XX` ve `REV-XX` kodları görev takip sisteminde korunacaktır. İlk dokümandaki sorumluluklar muhafaza edilmiş; harici/dahili oyun mimarisi, katalog performansı, veri bütünlüğü, servisler arası toplu sorgular ve test görevleri somutlaştırılmıştır.

> **Önemli mimari not:** Bu dokümanda PostgreSQL, gönderilen güncel MD dosyasındaki teknik karar olarak korunmuştur. Eski LTZ uygulaması ve bazı eski proje belgelerinde MSSQL kullanılmıştır. Yeni LTZ'nin veritabanı tercihi ekipçe kesinleştirilmeli; PostgreSQL'e geçilecekse eski Flyway migration'ları ve SQL sorguları ayrıca uyarlanmalıdır. Bu belge, mevcut kodun taşındığını varsaymaz.

## 0. Ortak tasarım kararları ve kapsam

### 0.1. MVP ve önceliklendirme

- **MVP:** Temel ürünün çalışır sürümünde hedeflenen görevler. Geliştirme sıralaması ile kapsam aynı şey değildir; bazı MVP görevleri daha geç aşamada yapılabilir.
- **MVP sonrası:** İlk sürümün yayınlanması için zorunlu görülmeyen genişletmeler.
- **P0:** Önce yapılması veya en erken mimari tasarıma dahil edilmesi gereken görev.
- **P1:** İlk sürümü güçlendiren, ancak P0 tamamlandıktan sonra ele alınabilecek görev. Bu sürümde yeni önerilen P1 görevler *MVP sonrası* planlanmıştır.
- **Görev durumu:** `[ ]` henüz doğrulanmamış / tamamlandı olarak işaretlenmemiş anlamındadır. Eski LTZ'deki gerçekleştirilmiş kod, yeni LTZ'de ayrıca doğrulanacaktır.

### 0.2. Dahili, harici ve yerel kavramları

- **Dahili oyun (`INTERNAL`):** LTZ üzerinden oluşturulan veya LTZ tarafından birincil kaynak olarak yönetilen oyun.
- **Harici oyun (`EXTERNAL`):** Steam veya ileride başka sağlayıcıdan kaynağını alan oyun. Harici bir oyun `game_db` içine kaydedilse bile `EXTERNAL` niteliği değişmez.
- **Yerel katalog:** `game_db` içinde bulunan dahili oyun kayıtları, harici oyunların hafif indeks kayıtları ve detayları zenginleştirilmiş harici kayıtların bütünü.
- **Hafif indeks kaydı:** Uygun veri kaynağından alınan minimum oyun kimliği, isim ve mevcut diğer arama alanları. Her kayıtta kapak görseli, dil desteği veya sistem gereksinimi bulunması zorunlu değildir.
- **Zenginleştirilmiş kayıt:** İzinli veri kaynağından daha kapsamlı detayları getirilip yerel katalogda saklanan kayıt.
- **Önbellek / veri güncelliği:** Bir kaydın saklanması tek başına verinin güncel olduğunu göstermez. Son güncelleme, veri kapsamı ve senkronizasyon durumu ayrıca izlenir.

**Temel ilke:** Kullanıcıya hızlı oyun listesi vermek için öncelikle yerel katalog sorgulanır; Steam'den bütün oyunların bütün detayları her sayfa açılışında istenmez.

### 0.3. Hibrit oyun kataloğu stratejisi

1. **Hızlı ana keşif:** Popüler ve öne çıkarılan oyunların yeterli kart/detay verisi önceden `game_db` içinde hazırlanır; ana keşif sayfası bunları harici servis yanıtını beklemeden sunar.
2. **Geniş hafif indeks:** İzinli ve teknik olarak desteklenen kaynakların sağladığı oyun listesi kademeli ve artımlı biçimde yerel indekse alınır. İndeksin gerçek kapsama oranı takip edilir; bütün Steam kataloğunun eksiksiz indekslendiği varsayılmaz.
3. **İhtiyaç halinde detay:** Kullanıcı indekste bulunan bir oyunun detayına girdiğinde eksik alanlar izinli sağlayıcıdan alınmaya çalışılır; başarılı yanıt kalıcı olarak saklanabilir. Sağlayıcı yavaşsa/erişilemiyorsa mevcut yerel bilgilerle kısmi detay gösterilir.
4. **Arka plan yenileme:** Popüler oyunlar öncelikli olmak üzere değişen veriler zamanlanmış görevlerle güncellenir. Başarısızlık durumunda son kullanılabilir verinin korunması ve veri güncelliğinin gösterilmesi esastır.
5. **Tek oyun kimliği:** Her oyunun LTZ genelinde tek `gameId` kimliği vardır. Steam AppID veya başka sağlayıcı ID'leri bu kimliğin harici referanslarıdır. Birden fazla sağlayıcıdaki aynı oyun yalnızca doğrulanmış eşleştirmeyle birleştirilir.
6. **Tek listeleme kuralı:** Popüler oyunları teknik olarak ilk sayfaya zorla ekleyip kalan oyunları ayrı bir sayfalama akışından birleştirmek yasaktır; bu yaklaşım sıralama, filtreleme ve toplam sonuç sayısını bozar. Popüler oyunlar ayrı bölüm/endpoint olarak sunulabilir; genel listeler tek ve deterministik sıralama kullanır.
7. **Arama kapsamı:** Yerel indeksteki geçerli oyunlar üzerinde arama hemen çalışır. İsteğe bağlı canlı harici arama sonuçları yalnızca ayrıca belirtildiğinde sunulur; yerel sonuçlar bekletilmez ve aynı oyun tekrar edilmez. Eksik indeks/eksik metadata kaynaklı kapsam sınırlamaları API ve UI davranışında tanımlanır.

### 0.4. Harici veri kaynakları ve kullanım kuralları

- **İlk tercih:** Valve'ın belgelenmiş ve erişim izni bulunan resmi Steam Web API / Store Service işlemleri. Özellikle uygulama listesi ile ayrıntılı mağaza metadatası aynı şey olarak değerlendirilmez; her alanın gerçekten hangi API'de mevcut olduğu test edilir.
- **SteamDB:** Bağımsız araştırma ve manuel karşılaştırma için yararlı olabilir. Önceden açık izin ve kullanım şartları doğrulanmadan SteamDB'ye yönelik otomatik scraping/crawling veya belgelenmemiş bir API entegrasyonu yapılmaz.
- **Steam mağaza sayfaları / mağaza uç noktaları:** Resmî belgelenme, kullanım ve yeniden saklama koşulları doğrulanmadan kalıcı altyapı bağımlılığı oluşturulmaz.
- **IGDB / RAWG / diğerleri:** Erişim, lisans, atıf, kota, saklama ve ticari kullanım koşulları değerlendirildikten sonra ek sağlayıcı olarak planlanabilir. Bunların Steam kataloğunun birebir karşılığı olacağı varsayılmaz.
- **API anahtarları:** Yalnızca backend ortam değişkenlerinde/sır yönetiminde tutulur; frontend'e, repoya veya loglara yazılmaz.
- **Veri eksikliği:** Kaynak bir alanı sağlayamıyorsa oyun, alanı uydurulmuş şekilde gösterilmez. Fiyat/indirim verileri bölge ve zaman bağımlıdır; lisansı/erişimi netleşene kadar garanti özellik sayılmaz.

**Dış bağımlılık kararı:** Belirli Steam uç noktalarının fiili erişimi, alan kapsamı ve kullanım şartları geliştirmeye başlanmadan bir teknik doğrulama göreviyle kayıt altına alınır. İzinli detay kaynağı yoksa zenginleştirme görevi o alanlar için bloke kabul edilir; yasaklı scraping ile aşılmaz.

### 0.5. Servis veri sahipliği

| Veri / işlem | Yetkili servis | Diğer servislerin kullanım şekli |
|---|---|---|
| Oyun adı, tür, platform, harici referans, medya, detay ve katalog durumu | `game-service` | Sözleşmeli API veya izinli olay/okuma projeksiyonu |
| Favori, bitirildi ve kişisel koleksiyon kayıtları | `library-service` | Yetkilendirilmiş API |
| LTZ kullanıcı puanı, inceleme, beğeni, inceleme raporu | `review-service` | Toplu özet / rapor API'leri |
| Kullanıcı hesabı, roller, oturum kimliği | `auth-service` | Kimlik doğrulama / yetki bilgisi |
| Profil ve kütüphane görünürlük tercihleri | `user-service` | Yetkilendirilmiş gizlilik kontrolü |
| Moderasyon iş akışı | `admin-service` | Review kayıtlarına yalnızca sözleşmeli işlemle müdahale |

Hiçbir servis başka servisin veritabanı tablolarını doğrudan sorgulamaz. Performans için gerekli özetler, veri sahibinin API'sinden veya açıkça tanımlı senkronize okuma projeksiyonundan sağlanır; özgün veri sahipliği değişmez.

---

## 1. Game Service

**Servis:** `game-service`  
**Veritabanı:** `game_db`  
**Amaç:** LTZ oyun kataloğunu, oyun keşfini, harici sağlayıcıları, oyun detaylarını ve yetkili geliştirici oyun yönetimini yürütmek.

### 1.1. Mevcut görevler — MVP

- [ ] **GAME-01 — Oyun listeleme:** Dahili oyunlar ve yerel indeksteki uygun harici oyunlar ortak katalog sözleşmesiyle, sunucu tarafında sayfalı listelenir. `page` / `size`, maksimum `size`, toplam sonuç ve deterministik ikincil sıralama tanımlanır. **Kabul:** Aynı sorgu/filtre için sayfalar arasında tekrarlanan veya atlanan kayıt üretilmez; genel liste Steam yanıtına bağımlı değildir.
- [ ] **GAME-02 — Oyun detayları:** Açıklama, tür, platform, çıkış tarihi, geliştirici, yayıncı, dil desteği, sistem gereksinimleri ve mevcut medya bilgileri tek detay modeliyle sunulur. Harici veride bulunmayan alanlar isteğe bağlıdır. **Kabul:** Yalnızca hafif indeks kaydı olan oyun için kısmi yanıt verilebilir; eksik alanlar uydurulmaz ve GAME-17 üzerinden zenginleştirilebilir.
- [ ] **GAME-03 — Oyun arama:** Oyun adına göre yerel katalogda arama yapılır; isim normalizasyonu, ilgili indeksler, boş arama davranışı, sayfalama ve mükerrer sonuç kontrolü tanımlanır. Ayrı `search-service` kurulmaz. **Kabul:** Önce yerel sonuç gösterilir; opsiyonel harici canlı arama temel deneyimi bloke etmez.
- [ ] **GAME-04 — Oyun filtreleme:** Tür, platform, LTZ puanı, popülerlik, çıkış tarihi, erken erişim ve dil desteği başta olmak üzere birden fazla filtre birlikte uygulanır. **Kabul:** Eksik metadata'nın filtre sonucu üzerindeki davranışı açıkça belirlenir; LTZ puanı Review Service'ten toplu API/okuma projeksiyonuyla alınır, doğrudan `review_db` okunmaz. İndirim filtresi GAME-24 koşullarına bağlıdır.
- [ ] **GAME-05 — Oyun sıralama:** Ada, çıkış tarihine, popülerliğe, uygun olduğu yerde LTZ puanına göre artan/azalan sıralama sunulur. **Kabul:** `gameId` gibi stabil bağlayıcı sıralama ile sayfalama tutarlıdır; eksik değerlerin konumu belgelenmiştir.
- [ ] **GAME-06 — Popüler oyunlar:** Popüler oyunları ayrı endpoint/bölüm üzerinden sunma ve GAME-20'nin önceden hazırlanmış katalog verisini kullanma. **Kabul:** Popüler liste servis açılışında Steam'den tek tek detay beklemez; popülerlik sıralamasının hangi veriye dayandığı bilinir.
- [ ] **GAME-07 — Yetkili oyun yönetimi:** Admin veya yetkili kullanıcılar oyun ekleyebilir, güncelleyebilir, arşivleyebilir/silebilir. Yetki, sahiplik ve doğrulamalar uygulanır. **Kabul:** Kütüphane veya inceleme referansı bulunan bir oyunun fiziksel silinmesi veri kaybına yol açmaz; uygun durumlarda görünürlük kapatma/arşivleme kullanılır. Harici kaynaktan gelen alanlara yapılan manuel düzeltmeler senkronizasyonda kontrolsüz ezilmez.
- [ ] **GAME-08 — Oyun alt verileri:** Tür/kategori/etiket, platform, geliştirici, yayıncı, medya ve sistem gereksinimleri ilişkili olarak yönetilir. **Kabul:** Tür, kategori ve etiket kavramları API'de karıştırılmaz; sağlayıcının sunmadığı alanlar zorunlu sayılmaz. Ayrı `media-service` oluşturulmaz.
- [ ] **GAME-09 — Harici oyun veri sağlayıcıları:** Sağlayıcı soyutlamasıyla arama, liste ve detay entegrasyonları ilgili sağlayıcının gerçekten desteklediği yeteneklere göre tanımlanır. **Kabul:** Sağlayıcı değişikliği frontend DTO'sunu değiştirmeye zorlamaz; kullanım izni, timeout, kota, hata kodları ve erişilemeyen yetenekler ele alınır.
- [ ] **GAME-10 — Tutarlı oyun görüntüleme modeli:** `INTERNAL` ve `EXTERNAL` kaynaklı oyunları aynı kart ve detay sözleşmesiyle döndürme. **Kabul:** Her kartta tek `gameId` bulunur; `origin`, `provider` ve `metadataCompleteness` benzeri alanlar birbirine karıştırılmaz. Harici oyun yerel DB'ye alınınca dahiliye dönüşmez.
- [ ] **GAME-12 — Oyun geliştiricisi yetkileri:** Doğrulanmış geliştiriciler yalnızca yetkili oldukları oyunları yönetebilir. **Kabul:** Auth rolü ve oyun bazlı sahiplik/yetki ayrı ayrı kontrol edilir; kullanıcı başkasının oyununa değişiklik uygulayamaz.
- [ ] **GAME-13 — Geliştirici oyun içerikleri:** Yetkili geliştirici oyun açıklaması, görselleri, video/demo URL'leri ve güncelleme notlarını oyun sayfasında yönetir. **Kabul:** Girdi doğrulaması, medya/bağlantı güvenliği ve moderasyon politikası uygulanır; içerik sahipliği belirgindir.
- [ ] **GAME-15 — Servis sınırları ve entegrasyon:** Game Service katalog verisinin sahibi olur; Library Service favori/koleksiyon, Review Service kullanıcı puanları/incelemeleri yönetir. `auth-service`, `library-service`, `review-service`, `content-service`, `hardware-service` ve gereken diğer servislerle sözleşme bazlı iletişim kurulur. **Kabul:** Çoklu oyun kartı sorgusu ve puan özetinde N+1 HTTP çağrısı önlenir; timeout, kısmi başarısızlık ve erişim yetkileri tanımlanır; başka veritabanına erişilmez.

### 1.2. Yeni önerilen görevler — MVP / P0

- [ ] **GAME-16 — Yerel Steam katalog indeksi (P0):** İzinli uygulama listesi kaynağından `provider`, `externalId`, oyun adı ve kaynakta mevcut temel alanlar ile arama yapılabilir hafif indeks kurulması. İlk aktarım ve artımlı güncelleme ayrı iş akışlarıdır. **Kabul:** Büyük liste tek HTTP isteğine zorlanmaz; işlem tekrarlandığında mükerrer kayıt oluşmaz; indeks kapsamı/son güncelleme raporlanır.
- [ ] **GAME-17 — İsteğe bağlı detay zenginleştirme (P0):** İndeks kaydının eksik detaylarını, kullanılan sağlayıcının izinli ve desteklenen API'lerinden talep edip yerel kayda işleme. **Kabul:** Aynı oyun için eş zamanlı gereksiz tekrar istekleri sınırlandırılır; başarısız istekte mevcut detay korunur; yalnızca gerçekten erişilebilen alanlar saklanır.
- [ ] **GAME-18 — Kanonik oyun kimliği ve mükerrer kayıt kontrolü (P0):** LTZ `gameId` ile `(provider, externalId)` referanslarını ayıran yapı ve benzersizlik kısıtları kurulması. **Kabul:** Aynı Steam AppID tek oyuna karşılık gelir; ada bakarak farklı sağlayıcılardaki oyunlar kontrolsüz birleştirilmez; doğrulanmış eşleme/ayırma işlemleri mümkündür.
- [ ] **GAME-19 — Otomatik katalog senkronizasyonu (P0):** Sağlayıcı bazında zamanlanmış ilk indeksleme, artımlı güncelleme, yeniden deneme ve kaldığı yerden sürdürme akışları kurulması. **Kabul:** Kısmi hata tüm kataloğu silmez; senkronizasyon ilerlemesi ve son başarılı zaman izlenir; kota/rate limit dikkate alınır.
- [ ] **GAME-20 — Popüler oyunları önceden hazırlama (P0):** İlk aşamada yönetilebilir editör listesi; daha sonra uygun veri mevcutsa LTZ etkileşimlerinden popülerlik puanı üretme ve bu oyunların detaylarını öncelikli zenginleştirme. **Kabul:** Popüler oyun kartları harici API beklemeden açılır; popülerlik ölçütü ve son hesaplama zamanı belirgindir.
- [ ] **GAME-21 — Oyun türü ve katalog temizliği (P0):** Ana oyun, DLC, demo, soundtrack, yazılım, video, paket gibi sağlayıcı kayıtlarını kaynağın sunduğu güvenilir tür sinyalleriyle ayırma. **Kabul:** Genel ana oyun listesine uygunsuz kayıtlar karışmaz; belirsiz türler yanlışlıkla oyun diye varsayılmaz; filtreleme kuralları test edilir.
- [ ] **GAME-22 — Önbellek, veri güncelliği ve hata toleransı (P0):** Detay TTL/yenileme politikası, stale-while-revalidate benzeri davranış, timeout, sınırlı retry, backoff, rate limit ve geçici erişim hatası yönetimi. **Kabul:** Sağlayıcı kesintisi yerel katalog listelemesini durdurmaz; değişken fiyat gibi hassas zamanlı alanlar güncellik doğrulanmadan güncel gösterilmez.
- [ ] **GAME-25 — Katalog performans ve entegrasyon testleri (P0):** Büyük indeks, eş zamanlı arama, sayfalama, eksik veri, tür temizliği, kısmi senkronizasyon ve sağlayıcı kesintisi için otomatik testler. **Kabul:** Hedeflenen veri hacmi ve test ortamı kaydedilir; yerel listeleme/arama için başlangıç ölçüm hedefi p95 ≤ 300 ms olarak değerlendirilir; hedefin karşılanması ölçümle doğrulanır, garanti varsayılmaz.

### 1.3. Yeni önerilen görevler — MVP sonrası / P1

- [ ] **GAME-23 — Yerelleştirme ve veri kaynağı izlenebilirliği (P1):** Oyun metadata dilini, dil destek türlerini (arayüz/ses/altyazı), kaynağı, son doğrulama zamanını ve boş/yanlış veri durumlarını ayırt etme. **Kabul:** Türkçe desteği yanlış `false` kabul etmeye yol açan metin/parsing senaryoları test edilir; API bilinmeyen değeri `false` gibi göstermez.
- [ ] **GAME-24 — İndirim ve fiyat bilgileri (P1, koşullu):** İzinli ve sürdürülebilir veri kaynağı bulunursa mağaza, ülke/bölge, para birimi, indirim ve fiyat güncelliğinin yönetilmesi. **Kabul:** Lisans/erişim uygun değilse görev ertelenir; fiyatlar bölge ve güncellik bilgisi olmadan evrensel doğruymuş gibi gösterilmez.
- [ ] **GAME-26 — Manuel metadata düzeltme ve arşivleme (P1):** Admin'in hatalı indeks/veri eşlemelerini düzeltmesi, oyunları arşivlemesi ve harici veriye göre yapılan manuel değişiklikleri ayrı takip etmesi. **Kabul:** Geçerli manuel değişiklikler sonraki otomatik güncellemede sessizce kaybolmaz; işlem kaydı vardır.

### 1.4. Önceden mevcut görevler — MVP sonrası

- [ ] **GAME-11 — Genişletilebilir oyun detayları (MVP sonrası):** Başarımlar, rehberler, easter egg'ler ve kullanıcı deneyimleri için modüler detay alanları. İçeriğin sahibi başka servis ise API üzerinden entegre edilir; veri sahipliği taşınmaz.
- [ ] **GAME-14 — Geliştirici geri bildirimleri (MVP sonrası olarak yeniden planlandı):** Oyuncuların geliştiriciye oyun bağlamında geri bildirim, hata raporu ve erken erişim geri bildirimi göndermesi; ayrı durum takibi ve yetki kurallarının tasarlanması. **Gerekçe:** Rapor yönetimi ve iş akışı kapsamı çekirdek oyun kataloğundan bağımsız ve geniştir.

### 1.5. Önerilen katalog veri modeli (uygulama öncesi netleştirilecek)

| Mantıksal tablo/yapı | Temel amaç |
|---|---|
| `games` | Tekil `gameId`, isim, `origin`, görünürlük/arşiv durumu ve uygun ortak metadata |
| `game_external_refs` | `gameId`, `provider`, `externalId`; `(provider, externalId)` için `UNIQUE` |
| `game_metadata_state` veya `games` içi alanlar | `INDEX_ONLY` / `PARTIAL` / `FULL` gibi veri kapsamı; `lastSyncedAt`, `syncStatus`, hata bilgisi |
| `game_genres`, `game_tags`, `game_platforms` | Normalleştirilmiş oyun sınıflandırmaları |
| `game_media`, `game_system_requirements` | Yetkili ve izinli kaynaklardan elde edilen ilgili detaylar |
| `game_provider_sync_state` | Sağlayıcı bazında son başarılı çalışma, cursor/sayfa, hata ve yeniden deneme durumu |
| `game_popularity` veya türetilmiş özet | Popülerlik puanı ve hesaplama tarihi |

Tabloların fiziksel olarak nasıl ayrılacağı teknik tasarımda belirlenir; her mantıksal alan için ayrı tablo açma zorunluluğu yoktur. `origin` (oyunun kökeni), `provider` (harici sağlayıcı), `metadataCompleteness` (yerel ayrıntı düzeyi) ve `syncStatus` (yenileme durumu) ayrı kavramlardır.

**Örnek kimlik:** `gameId=1001`, `origin=EXTERNAL`, `provider=STEAM`, `externalId=1091500`, `metadataCompleteness=FULL`. Bu oyunun yerel DB'de bulunması kaynağının `INTERNAL` olarak değiştirilmesine neden olmaz.

### 1.6. Önerilen API sözleşmesi başlıkları

Bunlar nihai endpoint kararı değil; frontend ve diğer servislerle sözleşme tasarımına temel olabilecek örneklerdir:

- `GET /api/games?page=&size=&sort=` — yerel birleşik katalog, sayfalama, sıralama ve uygun filtreler.
- `GET /api/games/search?q=&page=&size=` — yerel katalog araması.
- `GET /api/games/popular` — önceden hazırlanmış popüler oyun listesi.
- `GET /api/games/{gameId}` — kanonik kimlikle oyun detayı; indeks verisi eksikse kısmi detay.
- `POST /api/games/batch` veya uygun toplu okuma endpointi — Library gibi servisler için toplu oyun kartı/özet verisi; yalnızca yetkili çağrılar.
- `GET /api/games/metadata-status/{gameId}` gibi operasyonel alanlar — gerekli ise detay zenginleştirme durumunu sunma.

**Frontend sunum notu:** Kartlarda Steam/LTZ gibi kullanıcıya anlamlı kaynak bilgisi gösterilebilir. `cached` / `indexed` gibi iç teknik durumlar zorunlu etiket değildir. Genel oyun kataloğunda popüler bölüm ile normal arama/listenin filtre-sıralama anlamı ayrı tutulur.

---

## 2. Library Service

**Servis:** `library-service`  
**Veritabanı:** `library_db`  
**Amaç:** Kullanıcının kişisel koleksiyonunu, favorilerini ve bitirilen oyun kayıtlarını yönetmek. Koleksiyon sahip olunan oyunlar listesi değildir.

### 2.1. Mevcut görevler — MVP

- [ ] **LIB-01 — Kişisel oyun kütüphanesi:** Kullanıcı geçerli LTZ `gameId` ile koleksiyonuna oyun ekleyebilir. Favori veya bitirildi işareti zorunlu değildir; sahiplik ispatı anlamına gelmez.
- [ ] **LIB-02 — Koleksiyondan oyun kaldırma:** Kullanıcı kendi kaydını silebilir; bu işlem favori/bitirildi alanlarıyla tutarlı ve atomik yürür. Kaldırılan oyun için aynı kullanıcıya ait artık koleksiyon durumu kalmaz.
- [ ] **LIB-03 — Favori oyunlar:** Kullanıcı oyunları favoriye ekleyebilir, favoriden çıkarabilir ve listeleyebilir. Koleksiyon kaydı yoksa LIB-15'e göre otomatik oluşturma yapılır; kullanıcıdan ayrı bir 'önce kütüphaneye ekle' adımı istenmez.
- [ ] **LIB-04 — Bitirilen oyunlar:** Kullanıcı oyunları bitirildi olarak işaretleyebilir, geri alabilir ve listeleyebilir. Koleksiyon kaydı yoksa LIB-15'e göre otomatik oluşturma yapılır.
- [ ] **LIB-05 — Kütüphane listeleme:** Kullanıcının koleksiyon kayıtları, favori ve bitirildi bilgileriyle listelenir. Görsel/oyun adı gerekiyorsa LIB-16 ile toplu Game Service sorgusu yapılır; oyun başına ayrı HTTP isteği gönderilmez.
- [ ] **LIB-06 — Kütüphane filtreleme:** Favori ve bitirildi durumları tek başına veya birlikte uygulanabilir; filtrelerin davranışı açıkça belgelenir.
- [ ] **LIB-07 — Kütüphane sıralama:** Oyun adına ve koleksiyona ekleme tarihine göre artan/azalan sıralama sunulur. Oyun adı gibi Game Service'ten gelen alanla sıralama gerekiyorsa N+1 üretmeyen plan kullanılır.
- [ ] **LIB-08 — Sayfalama:** Liste/filtre/sıralama işlemleriyle tutarlı `page` / `size` desteği sağlanır; stabil ikinci sıralama kullanılır.
- [ ] **LIB-09 — Kütüphane istatistikleri:** Toplam oyun, favori ve bitirilen oyun sayıları tutarlı hesaplanır; idempotent tekrarlar sayıları artırmaz.
- [ ] **LIB-10 — Profil entegrasyonu:** User Service'in profil akışına sözleşme bazlı, yetkili ve görünürlük kurallarına uygun koleksiyon verisi sunulur.
- [ ] **LIB-11 — Gizlilik kontrolleri:** User Service tarafından sahiplenilen görünürlük tercihleri Library Service'in sunucu taraflı erişim denetiminde uygulanır. **Kabul:** Bir kişinin gizli koleksiyonu yalnızca frontend'de saklanmış olmaz; yetkisiz istek ve User Service'e erişilememe senaryosu gizli veriyi açığa çıkarmaz.
- [ ] **LIB-12 — Mükerrer işlem kontrolü:** `(userId, gameId)` benzersizliği ve favori/bitirildi işlemlerinde idempotent davranış sağlanır; eş zamanlı istekler yinelenen kayıt oluşturmaz.
- [ ] **LIB-13 — Kimlik ve oyun doğrulama:** İşlemler JWT/rol ve sahiplik kontrolünden geçer; `gameId` Game Service üzerinden doğrulanır. İstemcinin gönderdiği farklı bir `userId` ile başkasının koleksiyonu değiştirilemez.
- [ ] **LIB-14 — Recommendation entegrasyonu:** Recommendation Service yalnızca izin verilen favori ve bitirilmiş oyun verisini sözleşmeli API üzerinden alır; Library Service veri sahibi olarak kalır.

### 2.2. Yeni önerilen görevler — MVP / P0

- [ ] **LIB-15 — Favori/bitirildi işleminde otomatik koleksiyon kaydı (P0):** Kütüphanede bulunmayan geçerli bir oyunun favorilenmesi veya bitirildi işaretlenmesi koleksiyon kaydını idempotent şekilde oluşturur. **Kabul:** Tek kullanıcı aksiyonu yeterlidir; otomatik eklenen kayıtta yalnızca istenen durum işaretlenir.
- [ ] **LIB-16 — Toplu oyun özeti entegrasyonu (P0):** Kütüphane ekranı için çoklu `gameId` değerinin Game Service'ten tek/belirli sayıda toplu API çağrısıyla alınması. **Kabul:** 50 oyun görüntülemek 50 ayrı Game Service HTTP çağrısı gerektirmez; arşivlenmiş veya geçici bulunamayan oyunlar kütüphane kaydını bozmaz.
- [ ] **LIB-18 — Yetki, gizlilik ve tutarlılık testleri (P0):** Kullanıcı izolasyonu, gizli profil, mükerrer ekleme, eş zamanlı favorileme, bitirildi işaretleme ve koleksiyon silme senaryolarını test etme. **Kabul:** Yetkisiz erişim API düzeyinde engellenir ve DB benzersizlik kuralları sınanır.

### 2.3. Yeni önerilen görev — MVP sonrası / P1

- [ ] **LIB-17 — Katalog değişikliklerine dayanıklılık (P1):** Game Service'te oyun arşivlendiğinde, harici sağlayıcıdan kaldırıldığında veya metadata eksildiğinde kişisel koleksiyon durumunun korunması ve uygun fallback görüntüleme yapılması. **Kabul:** Arşivleme, kullanıcının favori/bitirildi geçmişini sessizce silmez.

**Kapsam dışı (korundu):** Wishlist, satın alınmış/sahip olunan oyunlar, oynanan oyunlar ve oynanacak oyunlar için ayrı durum veya listeler oluşturulmayacaktır. Oyun koleksiyonu **Steam hesabındaki sahip olunan oyunların otomatik senkronizasyonu** anlamına gelmez.

---

## 3. Review Service

**Servis:** `review-service`  
**Veritabanı:** `review_db`  
**Amaç:** LTZ kullanıcı puanlarını, oyun incelemelerini, inceleme etkileşimlerini ve inceleme raporlarını yönetmek.

### 3.1. Mevcut görevler — MVP

- [ ] **REV-01 — Oyun puanlama:** Kullanıcı LTZ oyun kimliğine sayısal puan verir/günceller; puan aralığı ve tek aktif kullanıcı-oyun puanı kuralı REV-11'de açıkça tanımlanır. Puanın metinsel incelemeden bağımsız verilip verilemeyeceği sözleşmede belirlenir (öneri: verilebilir).
- [ ] **REV-02 — İnceleme oluşturma:** Kullanıcı bir oyun için metinsel inceleme yayımlar. İnceleme `gameId` ve `userId` ile ilişkilendirilir; başkasının adına inceleme oluşturulamaz. Önerilen kural: kullanıcı başına oyun başına en fazla bir aktif inceleme.
- [ ] **REV-03 — İnceleme güncelleme:** Kullanıcı kendi incelemesini düzenleyebilir; düzenleme tarihi, varsa puan/tavsiye alanının geçerliliği ve sahiplik kontrolü korunur.
- [ ] **REV-04 — İnceleme silme:** Kullanıcı kendi incelemesini silebilir; yönetici/moderasyon işlemleri ayrı yetkilerle yürütülür. İçeriğin silinmesi ile puanın silinmesi arasındaki kural açıkça belirlenir.
- [ ] **REV-05 — Olumlu / olumsuz değerlendirme:** Steam benzeri öneriyorum/önermiyorum görüşü desteklenir ve oyun sayfasında gösterilir. **Kabul:** Bu görüş sayısal LTZ puanından ayrı alandır; 7/10 puanı otomatik olumlu/olumsuz kabul edilmez.
- [ ] **REV-06 — İnceleme beğenileri:** Beğenme/beğeniyi kaldırma sunulur; `(userId, reviewId)` benzersizliği ve idempotent işlem uygulanır.
- [ ] **REV-07 — Uygunsuz inceleme raporlama:** Kullanıcı gerekçe belirterek rapor oluşturabilir; rapor durumları ve yetkili işlem akışı Admin Service ile sözleşmeli olarak yürütülür. Mükerrer/spam raporlar REV-13 kapsamına alınır.
- [ ] **REV-08 — Ortalama oyun puanı:** Her oyun için LTZ kullanıcılarının geçerli sayısal puanlarından ortalama ve puan sayısı sunulur. **Kabul:** Steam mağaza değerlendirme oranları bu alanla karıştırılmaz; çoklu oyun özeti REV-12'den sağlanır.
- [ ] **REV-09 — Öne çıkan incelemeler:** En faydalı incelemeler belgelenmiş sıralama sinyalleriyle listelenir; beğeni sayısı dışında oluşturulma tarihi, şikâyet ve moderasyon durumu gibi etkiler değerlendirilir.
- [ ] **REV-10 — Servis sınırları ve entegrasyon:** İnceleme, puan ve beğeniler Review Service'e; oyun kimliği Game Service'e, kullanıcı profili User Service'e aittir. `game-service`, `user-service`, `admin-service` ve gerektiğinde `notification-service` ile sözleşmeli entegrasyon yapılır. Başka servis veritabanına doğrudan erişilmez.

### 3.2. Yeni önerilen görevler — MVP / P0

- [ ] **REV-11 — Tekil değerlendirme ve puanlama kuralları (P0):** Sayısal puan ölçeği, puan güncelleme, öneriyorum/önermiyorum alanı ve kullanıcı-oyun başına maksimum aktif kayıt kuralları tasarlanır. **Kabul:** Aynı kullanıcının tekrar isteği ortalamayı mükerrer etkilemez; metin incelemesi, tavsiye ve sayısal puanın birbirinden bağımsızlığı test edilir.
- [ ] **REV-12 — Toplu oyun puanı özetleri (P0):** Çoklu `gameId` için ortalama LTZ puanı, puan sayısı ve uygun temel istatistiği tek istekte sunma. **Kabul:** Game Service'in oyun listelemesi oyun başına tek tek Review Service sorgusu yapmaz; yorum/puan olmayan oyunların boş değerleri açıkça tanımlanır.

### 3.3. Yeni önerilen görevler — MVP sonrası / P1

- [ ] **REV-13 — Spam ve kötüye kullanım kontrolü (P1):** Rapor/beğeni ve inceleme oluşturma işlemlerinde uygun oran sınırı, mükerrer rapor kuralı ve kötüye kullanım denetimleri. **Kabul:** Sıradan kullanıcı faaliyetlerini bozmadan tekrar/spam senaryoları test edilir.
- [ ] **REV-14 — İnceleme ve puan istatistikleri (P1):** Sayısal puan dağılımı, toplam aktif inceleme sayısı ve olumlu/olumsuz tavsiye oranlarının ayrı hesaplanması. **Kabul:** İnceleme adedi ile sayısal puan veren kullanıcı adedi karıştırılmaz.
- [ ] **REV-15 — Moderasyon sonrası içerik davranışı (P1):** İncelemenin görünür/gizli/arşivli durumları, raporun kapatılması ve moderasyon eylemlerinin sonuçları netleştirilir. **Kabul:** Gizlenmiş inceleme genel listede gösterilmez; kullanıcı puanı ve ortalama üzerindeki etki açık politika ile tutarlı uygulanır.

---

## 4. Entegrasyon ve teknik kabul kriterleri

Aşağıdaki koşullar bütün servislerde uygulanmalıdır:

- [ ] Üç servis kendi PostgreSQL veritabanıyla çalışır; şema değişiklikleri Flyway ile sürümlenir. Yeni LTZ veritabanı kararı ekipçe doğrulanmadan eski MSSQL migration'ları çalışır kabul edilmez.
- [ ] `api` / `application` / `domain` / `infrastructure` katman ayrımı, request/response DTO yapısı, doğrulama, merkezi hata yanıtları ve loglama standartlarına uyulur.
- [ ] Frontend tüm servis API'lerine API Gateway üzerinden erişir; harici Steam API anahtarları frontend'e açılmaz.
- [ ] Servisler diğer servislerin DB'sini sorgulamaz; Spring RestClient ile HTTP iletişimi ve gerçekten dağıtık çok adımlı işlem gereken yerde kararlaştırılan Saga ilkeleri uygulanır. Basit toplu okumalar için Saga kurulmaz.
- [ ] Yetki denetiminde Auth Service'in kimlik/rol altyapısı ve proje `SECURITY.md` kuralları uygulanır. `SECURITY.md` erişilebilir olmadığından bu belgede içeriği varsayılmamıştır.
- [ ] Oyun kimliği doğrulama ve harici referans benzersizliği, kütüphane sahiplik kontrolü, inceleme/puan tekilliği ve idempotent işlemler hem uygulama hem veritabanı seviyesinde uygun şekilde korunur.
- [ ] Game Service'e bir sayfada çok sayıda oyun için Library/Review verisi gerekli olduğunda N+1 HTTP çağrıları yapılmaz; toplu API veya sözleşmeli özet kullanılmalıdır.
- [ ] Oyun indekslemesi ve harici veri senkronizasyonu önceden onaylanmış API kullanım ve lisans koşulları içinde çalışır; SteamDB scraping varsayılan veri kaynağı olarak eklenmez.
- [ ] Sağlayıcı kesintisi durumunda yerel oyun listeleme ve mevcut oyun detayları kullanılabilir kalır; eksik alanlar ve güncelliği doğrulanmamış alanlar yanlış gösterilmez.
- [ ] Sorguların performansı gerçekçi kayıt ve yük koşullarında ölçülür; yerel katalog listeleme ve arama için **başlangıç hedefi p95 ≤ 300 ms**'dir (test ortamı, veri hacmi ve eş zamanlı kullanıcı sayısı raporlanarak). İlk görünür kartların yüklenmesinde uygun test ortamında **≤ 1 saniye** hedeflenir; bu bir ölçüm hedefidir, garanti değildir.
- [ ] Oyun listeleme/arama/filtreleme/sıralama, kütüphane işlemleri, gizlilik, puanlama, inceleme, raporlama, toplu sorgu ve sağlayıcı hataları unit/integration testleriyle doğrulanır.
- [ ] SonarQube Security, Reliability ve Maintainability kategorilerinde en az **A** hedeflenir.

### Örnek senkronizasyon başlangıç politikası

Bunlar kesin sağlayıcı kuralları değil; doğrulanmış kota, kullanım şartları ve ölçümler sonrasında ayarlanacak taslak değerlerdir.

| Veri | Örnek güncelleme periyodu | Açıklama |
|---|---|---|
| Hafif katalog indeksi | Günlük artımlı | Sağlayıcı destekliyorsa yalnız değişen kayıtlar |
| Popüler oyun detayları | Yaklaşık 24 saat | Önceden hazırlama; API kotasına göre toplu plan |
| Diğer oyun detayları | Yaklaşık 7 gün veya istek üzerine | Gereksiz bütün-katalog detay güncellemesinden kaçınma |
| Popülerlik sıralaması | 1–6 saat | Yeterli LTZ verisi yoksa editör listesi |
| Fiyat/indirim | Kaynağın izinlerine ve kullanım amacına göre | Güncellik ve bölge açıkça gösterilir |

---

## 5. Önerilen geliştirme sırası

| Aşama | Öncelikli işler | Hedef |
|---|---|---|
| **1 — Kanonik katalog ve sağlayıcı temeli** | `GAME-08`, `GAME-09`, `GAME-10`, `GAME-16`, `GAME-18`, `GAME-21` | Tek kimlik, geçerli Steam indeks kaydı, temiz ana oyun listesi |
| **2 — Hızlı keşif ve detaylar** | `GAME-01`, `GAME-02`, `GAME-03`, `GAME-06`, `GAME-17`, `GAME-19`, `GAME-20`, `GAME-22` | Yerelden hızlı listeleme, yerel arama, ihtiyaç halinde detay ve yenileme |
| **3 — Filtreleme, sıralama, test** | `GAME-04`, `GAME-05`, `GAME-25` | Tutarlı filtreleme/sayfalama ve ölçülmüş performans |
| **4 — Kütüphane ve incelemeler** | `LIB-01`–`LIB-16` içindeki MVP görevleri, `LIB-18`; `REV-01`–`REV-12` içindeki MVP görevleri | Kişisel koleksiyon, favori, bitirildi, LTZ inceleme/puan ve toplu sorgular |
| **5 — Yetkili oyun yönetimi ve geliştirici alanı** | `GAME-07`, `GAME-12`, `GAME-13` | Admin ve geliştirici tarafından güvenli oyun yönetimi |
| **MVP sonrası genişletme** | `GAME-11`, `GAME-14`, `GAME-23`, `GAME-24`, `GAME-26`, `LIB-17`, `REV-13`, `REV-14`, `REV-15` | Genişletilmiş içerik, rapor iş akışı, fiyat, ileri istatistik ve moderasyon |

> **Yatay işler:** `GAME-15` ile `REV-10`, güvenlik, gerekli entegrasyon ve ilgili testler tek bir aşamaya bırakılmaz; bütün geliştirme sürecine yayılır. Aşama 4'teki `LIB-01–LIB-16` ifadesi numara aralığındaki tüm görevlerin otomatik olarak P0 olduğu anlamına gelmez; ilgili bölümlerdeki MVP/P0 statüleri esastır.

---

## 6. Karara bağlanması gereken noktalar

Aşağıdaki konular nihai teknik uygulamadan önce ekip tarafından netleştirilmelidir:

1. **Veritabanı:** Yeni LTZ'de PostgreSQL kesin karar mı? Eski MSSQL verisi/migration'ları taşınacak mı, yoksa yeni şemayla mı başlanacak?
2. **Resmi Steam erişimi:** Kullanılacak resmi endpointlerin erişimi, sağladığı alanlar, verinin saklanması/yeniden kullanımı ve kota şartları doğrulandı mı? Detaylar için hangi izinli sağlayıcı kullanılacak?
3. **Katalog kapsamı:** Yerel Steam indeksi sadece `game` türünü mü kapsayacak? DLC/demo gibi türler detay sayfası veya arama için ayrı biçimde erişilebilir olacak mı?
4. **Popülerlik ölçütü:** İlk sürümde editör listesi mi kullanılacak? LTZ etkileşimleri yeterli olduğunda puanlama modeli nasıl değişecek?
5. **Sayısal puan ölçeği:** LTZ inceleme puanı `1–10`, `0–10` ya da başka bir ölçek mi? Puan verme metinsel incelemeden bağımsız mı?
6. **Harici oyun eşlemesi:** Aynı oyunun farklı mağaza versiyonları hangi şartlarla tek kanonik oyun sayılacak? İlk sürümde elle onaylı eşleme yeterli mi?
7. **Geliştirici yönetimi:** GAME-12/13 MVP'de kalacak mı? GAME-14'ün MVP sonrasına taşınması ekipçe kabul edildi mi?
8. **Fiyat/indirim:** Bu alanlar veri kaynağı şartları kesinleşince mi açılacak? İndirim filtresinin MVP ekranındaki yeri buna göre belirlenmeli.

Bu kararlar doğrulanana kadar dış veri erişimi veya bilinmeyen alanlar hakkında gerçekte sağlanamayan garanti verilmez.

---

## 7. Görev sayısı ve kapsam özeti

| Servis | Mevcut görev | Yeni görev | Toplam | MVP | MVP sonrası |
|---|---:|---:|---:|---:|---:|
| Game Service | 15 | 11 | **26** | 21 | 5 |
| Library Service | 14 | 4 | **18** | 17 | 1 |
| Review Service | 10 | 5 | **15** | 12 | 3 |
| **TOPLAM** | **39** | **20** | **59** | **50** | **9** |

**Değişiklik özeti:**

- Orijinal 39 görevden hiçbiri silinmedi, görev kodları korundu.
- `GAME-14`, iş akışının kapsamı nedeniyle MVP sonrasına taşındı; `GAME-11` MVP sonrası kalmaya devam etti.
- Hibrit katalog mimarisine uygun **11 yeni GAME**, **4 yeni LIB** ve **5 yeni REV** görevi eklendi.
- Yeni 20 görevin **13'ü P0/MVP**, **7'si P1/MVP sonrası** olarak önerildi.
- İlk sürümde **50**, MVP sonrasında **9** görev hedefleniyor. Bu bir iş planı önerisidir; ekip takvimine göre revize edilebilir.

> **Gereksinim eşlemesi:** Kaynak görev listesindeki oyun kataloğu, koleksiyon ve inceleme sorumlulukları LTZ gereksinim belgesinin `FR-GAME-*`, `FR-REV-*` ve geliştirici alanındaki `FR-DEV-*` başlıklarından türetilmiştir. `library-service` bağımsız bir mikroservis olarak yeni mimari kararına göre ayrılmıştır. Bağımsız `search-service`, `media-service` veya `moderation-service` eklenmemiştir. Bu güncelleme, ayrıca araştırılarak önerilen önbellek, indeks ve entegrasyon görevlerini gereksinimlerden türetilmiş mevcut işlevlerden ayrı olacak biçimde ekler.
