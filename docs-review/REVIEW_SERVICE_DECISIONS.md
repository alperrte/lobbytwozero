# Review Service kararları

## Onaylı kapsam ve iş kuralları

- Kullanıcı Hamza; branch `feature/review-service`. Yalnız Review backend ve bu klasör değiştirilebilir.
- Faz 1 tamamlandı; sonraki açık onaylarla Faz 2 ve Faz 3 başlatıldı. Faz 3 Review backend kabulü tamamlandı; Faz 4 ayrı açık onay bekler. Faz 2 REV-07/REV-10 entegrasyonları hâlâ açıktır.

- Kullanıcı 31 Ekim 2026 tarihinde şu politikaları onayladı: puan 1–10 tam sayı; metin olmadan puan ve tavsiye verilebilir; metin silinince bağımsız puan ve tavsiye korunur; ikinci aktif metin oluşturma 409 döner.
- Puan, metin ve tavsiye ayrı tablolarda tutulur; başka servise FK veya SQL erişimi yoktur.
- Eski `database.md`/ER şeması öneridir; Review'de mevcut iş tablosu/migration yoktur. Gerçek şema yeni PostgreSQL migration'larıyla kurulur. Mevcut migration yeniden yazılmaz.
- Puan PUT işlemi doğal `(user_id,game_id)` tekilliğiyle upsert eder. Aynı değer tekrarında sürüm/zaman değişmez. Eşzamanlı farklı puanlarda DB sırası geçerlidir; mükerrer satır oluşmaz.
- Metin POST işlemi aynı aktif kullanıcı/oyun için 409 döner. PATCH yalnız body ve güncel version kabul eder; eksik/null body reddedilir. Aynı metin/güncel sürüm değişiklik üretmez; eski sürüm 409'dur. Pessimistic row lock ve JPA @Version kayıp güncellemeyi önler.
- Kullanıcı silmesi metni NULL yapar, DELETED durumu/deletedAt yazar; aynı sahibin tekrarı 204'tür. Kayıt yoksa 404, başka sahibin kaydıysa 403. Silme Game erişimine bağlı değildir. Yeniden metin yayımlama yeni reviewId oluşturur; eski kimlik geri açılmaz. Bağımsız puan/tavsiye korunur. Hesap silme ise tüm yerel kişisel kayıtları fiziksel siler.
- Rating/tavsiye atomik UPSERT için parametrik JDBC kullanır; metin JPA entity/@Version ve pessimistic lock kullanır. Her iki yol aynı Spring transaction/DataSource ile hesap silme kilidini paylaşır. JPA validate ve open-in-view=false korunur; V52 gerçek PostgreSQL/JPA eşlemesi test edilir.
- Game doğrulaması mevcut anonim `POST /api/v1/game/games/batch` ile, DB transaction'ından önce yapılır. Kesinti/bozuk yanıt 503; unavailable oyun 404. Otomatik retry yoktur.
- Kimlik yalnız Auth RS256/JWKS `at+jwt` token'ının kanonik UUID `sub` alanıdır. Mevcut Auth rol sözleşmesi kullanılır; servis token'ları ayrı decoder ve namespace ile korunur. Cookie doğrudan kabul edilmez; Gateway mevcut cookie/CSRF politikasının sahibidir.
- Auth iptal/hesap durumu kontrolü diğer servislerde anlık değildir; en fazla access token ömrü ve saat toleransı kadar gecikme vardır. Uydurma introspection kullanılmaz.
- Son kullanıcı talimatı nedeniyle merkezi `docs/api.md` değiştirilemez. API kaydı bu klasörde tutulur; merkezi kataloğa aktarım ayrıca kapsam izni gerektirir.

## Hesap silme yarışı ve TTL kanıtı — kullanıcı onaylı Faz 1 tamamlaması

Kullanıcı, eski access token ile silme sonrası yeniden veri üretimini engelleyen geçici yerel engeli onayladı; önce gerçek token süresinin kaynak koddan doğrulanmasını istedi. Yeni bir servis/özellik veya Auth değişikliği yapılmadı.

Salt okunur doğrulama kaynakları:

- `backend/services/auth-service/src/main/resources/application.properties`: `ltz.auth.jwt.access-token-ttl=${JWT_ACCESS_TTL_SECONDS:60}s` (varsayılan 60 saniye).
- `backend/services/auth-service/src/main/java/com/ltz/auth/infrastructure/config/JwtProperties.java`: `@DurationMin(minutes=1)`, `@DurationMax(minutes=15)` (bağlayıcı en yüksek TTL 900 saniye).
- `backend/services/auth-service/src/main/java/com/ltz/auth/infrastructure/security/AccessTokenIssuer.java`: `expiresAt = now.plus(properties.accessTokenTtl())`; iat ve nbf aynı now.
- Root Compose yalnız okundu: varsayılan JWT_ACCESS_TTL_SECONDS=60. Gerçek ENV değerleri okunmadı; ortamdaki seçilmiş TTL doğrulandığı iddia edilmez.

Review yerel kullanıcı doğrulamasında exp-iat en fazla 15 dakika, ileri iat toleransı 30 saniye, exp toleransı 30 saniyedir. `UserTokenPolicy` engeli **900 + 30 + 30 + 60 = 1020 saniye (17 dakika)** hesaplar. Ek 60 saniye güvenlik payıdır; doğrulanmamış 16 dakika varsayımı kullanılmadı. Auth gelecekte 15 dakikalık üst sınırı artırırsa Review önce güvenli biçimde uzun token'ı reddeder; TTL/politika/kontrat birlikte güncellenmelidir. Hesap ve veri tabanı saatlerinin işletim ortamında UTC ve senkronize olması gerekir; sınırsız saat kayması varsayılmaz.

Tüm yazıcılar `AccountWriteLock.requireWritable` ile aynı hesabın PostgreSQL transaction advisory lock'unu alır ve aktif engeli kontrol eder. Silme de aynı kilidi alır; engel upsert'i ve üç tablonun silinmesi tek transaction'dır. Yarışta önce biten yazma silinir; sonra sıralanan yazma 403 alır. Başka hesapların kilit anahtarları farklıdır (64-bit hash çakışması yalnız gereksiz seri çalışma yaratır, yetki vermez). Silme tekrarı expiresAt'i geriye çekmez.

V54 yalnız `account_hash` (SHA-256, 64 hex) ve `expires_at` tutar; açık UUID, metin, token veya profil verisi tutmaz. Bu teknik kısa ömürlü tablo entity/aggregate değildir; doğal hash PK ve atomik UPSERT kullanır, gereksiz UUID/version eklenmez. Dakikalık temizlik yalnız süresi dolan özetleri siler. Servis çalışırken yaklaşık 17–18 dakika saklama; servis kapalıysa sonraki başarılı temizliğe kadar. Mevcut migration dosyaları yeniden yazılmadı.

## Doğrulama durumu

REV-01/02/03/04/05 kabul testleri sırasıyla tamamlandı. REV-11 ve final kalite koşusu sonuçları faz raporunda kaydedilir. Gerçek ENV okunmadı, canlı DB'ye migration uygulanmadı. Gerçek HTTP testi yerel JWKS/Game sözleşme sunucuları kullanır; canlı Auth/Game/Gateway E2E veya Sonar kanıtı değildir.

## Faz 2 — REV-06 ve önceki kullanıcı onaylı durma noktası

- Önceki kullanıcı kararı REV-06'yı bitirme ve `docs-review/request/` altında servis bazlı ihtiyaç belgeleme sınırıydı. Sonraki açık talimat yalnız REV-09'a geçişi onayladı (aşağıda). REV-07/outbox/relay/geçici teslim altyapısı kodlanmayacak; REV-07/REV-10 ENTEGRASYON BEKLİYOR kalır.
- V55: `review_likes` gerçek review FK'si ve `(review_id,user_id)` PK ile korunur. PUT/DELETE yalnız token sahibinin durumunu değiştirir; tekrarlar aynı sayıyı verir. Sayaç ayrı sütun değildir, aynı transaction görünümünde gerçek satırlardan okunur.
- Beğeni mutasyonları önce hesabın mevcut silme engelini, sonra review satır kilidiyle görünürlüğü kontrol eder. GET REPEATABLE_READ snapshot kullanır. Silinen/olmayan hedefte 404; kullanıcı metin silmesinde beğeniler aynı transaction'da kaldırılır.
- Kullanıcı politikayı açıkça onayladı: kendi incelemesine beğeni ve gelecekte rapor oluşturma yasak. REV-06'da kendi incelemesine PUT 403'tür ve gerçek JWT/HTTP dahil testlidir. Rapor oluşturma henüz uygulanmadığından kendi raporunu engelleme kabulü gelecekte REV-07 testinde doğrulanacak; bugün test edilmiş sayılmaz.
- Kendi incelemesinin sayaçlarını okumak mümkündür. DELETE yalnız kendi oyunu kaldırır; başkasının oyunu silemez. Kendi review'ına hiç oy veremeyen kişinin DELETE'i doğal olarak no-op'tur.
- Hesap silme hem verilen oyları hem silinen yazarın incelemelerine ait oyları temizler. Karşılıklı oyların FK CASCADE kilit çevrimi oluşturmaması için hesap silmeleri kısa bir PostgreSQL transaction advisory lock ile sıralanır; normal yazmalar hesap/review kilitlerini kullanmayı sürdürür.
- Auth/Admin/Notification talepleri öneridir; hedef servis tasarımlarını bağlamaz. Mevcut olmayan endpoint, izin, teslim veya moderasyon durumu mevcutmuş gibi kullanılmaz. Game batch ve User özet alıcısı/Review→User izni bulunduğundan bunlar için yeni eksik özellik talebi üretilmedi.

## REV-09 — Mevcut verilerle öne çıkan incelemeler

- Kullanıcı dış entegrasyonları bekleyen REV-07/REV-10'u açık tutarak yalnız REV-09'a geçişi açıkça onayladı. Bu istisna Faz 3/4 onayı değildir; REV-09 sonrasında durulur.
- Faydalılık sıralaması gerçek beğeni sayısı azalan, oluşturulma tarihi azalan, eşitlikte PostgreSQL UUID artan şeklindedir. Ağırlıklı zaman skoru/şikâyet katsayısı uydurulmaz. Düzenleme tarihi sıralamayı yükseltmez. Tüm görünür sıfır beğenili kayıtlar da varsayılan listede yer alabilir.
- Oyun UUID'si ve isteğe bağlı minimum beğeni filtresi; sıfır tabanlı sayfa, varsayılan 20/en çok 50 kayıt ve en çok 10000 sayfa indeksi kullanılır. Sayısal sınırlar HTTP validation ile uygulanır; SQL alanı/sort ifadesi istemciden alınmaz.
- Görünürlük ve sinyal toplama tek FeaturedReviewRepository sorgu sınırında tutulur. Gelecekte onaylı rapor/moderasyon sözleşmesi geldiğinde bu sorgu ve kabul testleri genişletilebilir. Bugün yalnız VISIBLE/DELETED şeması vardır; rapor tablosu, moderasyon alanı, sahte sıfır rapor sayısı veya outbox eklenmedi. REV-07/REV-15 uygulanmış sayılmaz.
- Salt okunur REPEATABLE_READ transaction toplam ve öğeleri aynı snapshot'tan okur; eşzamanlı commit'in yalnız bir sorguya yansıması önlenir. Ayrı HTTP sayfa istekleri arasında sabit sonuç garantisi yoktur; yaşayan beğeni/veri değişiklikleri sayfa kaymasına yol açabilir. Cursor veya geçmiş snapshot saklama bu görevin kapsamı değildir.
- Mevcut ReviewResponse korunur; liste DTO'su bunu likeCount ile sarar. Yazarın UUID'si yalnız ownedByActor hesabında kullanılır, yanıtla açılmaz. Oy veren kimlikleri dönülmez. Liste kullanıcı JWT'si ister; mevcut rol/güvenlik/önbellek politikası korunur.
- Okuma yalnız Review DB'sini kullanır. Game erişim hatası yerel tarihsel inceleme okumalarını engellemez. Boş oyun sonucu katalog doğrulaması değildir; başka servis özelliği veya yeni entegrasyon talebi gerekmemiştir.
- V51–V55 korunur; sıradaki boş numara V56 ile görünür kayıtlar için (game_id, created_at DESC, id ASC) kısmi indeks eklenir. Mevcut review_likes PK'si review_id ile başlar; yinelenen beğeni indeksi eklenmez. Beğeni toplulaştırması ve son sıralama çalışma zamanında hesaplanır; önceden hesaplanan sayaç/ranking tablosu yoktur.
- Kullanıcının bu göreve özel açık talimatıyla yalnız Review Maven test aşaması çalıştırıldı. `verify` aşamasının ortak coverage/pre-push çalıştırıcısını tetiklediği görüldüğünden `test` aşaması kullanıldı. Pre-push, backendCheck, Trivy, Sonar Scanner ve Quality Gate: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**. Önceki kalite sonuçları REV-09'a ait güncel sonuç değildir.

## Faz 3 — REV-08 ve REV-12

- Kullanıcı önce REV-08, test/kapanış/checklist; sonra REV-12 sırasını açıkça onayladı. Bu sıra uygulandı: 92/92 ve ardından 102/102 Review testi PASS. Faz 4 onaylı değildir; REV-07/REV-10 beklemeye devam eder.
- Yalnız game_ratings üzerinden PostgreSQL numeric round(avg(rating),2) ve count(*) hesaplanır. Java BigDecimal kullanılır; pozitif 1–10 değerlerde iki basamak HALF_UP (1.125 → 1.13). Tek kullanıcı/oyun kaydı V51 UNIQUE ile korunduğu için count(*) benzersiz puan veren kullanıcı sayısıdır. Kayıt statüsü sütunu yoktur; geçerli satırların tümü katılır. Metin/tavsiye/beğeni veya Steam verisi bu sayıya/ortalamaya girmez.
- Puan yoksa averageRating=null/ratingCount=0 ve HTTP 200. Bilinmeyen UUID ile puansız oyun ayrılmaz; Game'e callback veya katalog/görünürlük doğrulaması yapılmaz. Bu davranış Game'in görünmeyen oyunları göstermesi için izin değildir. Game kendi katalog kimlik/görünürlük politikasının sahibidir.
- GET /api/v1/review/games/{gameId}/rating-summary ve POST /api/v1/review/games/rating-summaries aynı aggregate sorgusunu kullanır. Toplu API zorunlu gameIds dizisi, en fazla 50 ham UUID, null eleman reddi, ilk görülme sırasında tekilleştirme sunar. Boş liste 200/items=[] ve sıfır SQL; dolu liste tek IN/GROUP BY SQL. Yanıt her benzersiz istenen oyun için öğe içerir; eksik gruplar null/0 ile bellekte tamamlanır.
- İki API de tek statement snapshot'ıyla AVG/count ve bütün oyun gruplarını tutarlı okur; readOnly/4 saniye transaction kullanılır. Ayrı isteklerde veri değişebilir. Kalıcı sayaç/cache veya istatistik tablosu eklenmedi. V51–V56 ve silme politikaları değişmedi; yeni migration gerekmedi. Mevcut game_id indeksi EXPLAIN ile doğrulandı; yeni yinelenen indeks eklenmedi.
- Kullanıcı ayrıca açıkça mevcut kullanıcı JWT güvenliğinin korunmasını istedi. Anonim veya yeni svc+jwt erişimi yok; AccountErasureSecurityConfiguration ve Auth kodu değiştirilmedi. Kullanıcı JWT'si Game servis kimliği yerine taşınmayacak. Game→Review erişimi NOT IMPLEMENTED/NOT VERIFIED olarak kalır.
- [Game talebi](request/game-review-integration.md) yeni 13 başlıklı ihtiyaç belgesidir; [Auth talebi](request/auth-review-integration.md) hedefe bağlı dar okuma izniyle genişletildi. Örnek scope adı kesinleşmiş değildir; ilgili sahipler nihai yolu/izinleri belirler. Bu API'nin Review kabulünün tamamlanması canlı Game entegrasyonunu veya global katalog minRating/RATING sıralamasını tamamlamaz.
- Histogram, tavsiye oranı, spam/rate-limit ve moderasyon etkileri eklenmedi; Faz 4 görevleri saklıdır. Pre-push/backendCheck/Trivy/Sonar **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**; verify/merkezi coverage/PMD/ratchet sonucu yoktur. Yalnız modül testleri, PostgreSQL/Flyway ve API hata senaryoları çalıştırıldı. Ayrıntı [Faz 3 raporunda](REVIEW_SERVICE_PHASE_03_REPORT.md).
