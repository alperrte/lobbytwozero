# Review Service kararları

## Onaylı kapsam ve iş kuralları

- Kullanıcı Hamza; branch `feature/review-service`. Yalnız Review backend ve bu klasör değiştirilebilir.
- Faz 1 tamamlandı; sonraki açık onaylarla Faz 2, Faz 3 ve Faz 4 başlatıldı. Faz 3 Review backend kabulü tamamlandı. Faz 4 (REV-13/14/15) kullanıcının açık onayıyla geliştirilir; durum [PROGRESS](REVIEW_SERVICE_PROGRESS.md) içindedir. Faz 2 REV-07/REV-10 entegrasyonları hâlâ açıktır.

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

## Faz 4 — REV-13 spam ve kötüye kullanım kontrolü (kullanıcı onaylı politika)

- **Onay kaydı:** Kullanıcı 31 Ekim 2026'da Faz 4 talimatıyla geliştirmeyi, ardından önerilen oran sınırı politikasını açıkça onayladı: inceleme oluşturma kullanıcı başına 60 saniyede 5 deneme; beğenme ve beğeni kaldırma ortak kotada kullanıcı başına 60 saniyede toplam 60 deneme; tekrarlanan ve iş kuralı açısından geçersiz istekler kotaya dahil; anonim/yetkisiz istekler mevcut 401/403 davranışını korur; aşımda 429 + doğru `Retry-After`; PostgreSQL'de atomik, çok örnekli tutarlı sayaç; başarısız iş transaction'ı kota tüketimini geri almaz; sınırlar merkezi ve değiştirilebilir; kullanılmayan sayaçlar sınırsız birikmez. Bu sayısal değerler yalnız bu onaya dayanır; başka uca genişletilmez.
- **Algoritma:** Epoch'a hizalı sabit pencere + PostgreSQL `INSERT … ON CONFLICT (hesap, eylem, pencere) DO UPDATE … RETURNING` ile tek ifadede atomik sayaç. Satır kilidi eşzamanlı istekleri sıralar; aynı anda gelen N istekten tam `limit` tanesi geçer. Sayaç `limit+1`'de durur (taşma/şişme yok). Kayan pencere/token-bucket seçilmedi: tek satırlı basit ve denetlenebilir yapı yeterli görüldü; bedeli pencere sınırında kısa süreli `2×limit` patlamasıdır (belgelenmiş sınır).
- **Sayım noktası:** Spring MVC `HandlerInterceptor` (yalnız `POST …/games/*/reviews`, `PUT|DELETE …/reviews/*/like`). Güvenlik zinciri 401/403 verdiğinde veya gövde filtresi 413 verdiğinde istek buraya gelmez; sayaç yazılmaz. Interceptor bağlama/doğrulamadan önce çalıştığı için JSON/alan doğrulama hataları ve geçersiz path UUID'si de deneme sayılır (onaylı "geçersiz istekler de sayılsın" kuralının doğal sonucu); Game kesintisi (503) de sayılır. Kullanıcı kimliği yalnız token `sub` alanıdır.
- **Transaction:** Sayaç `REQUIRES_NEW` ile iş transaction'ından önce kesinleşir; iş transaction'ı 409/403/404/503 ile geri alınsa da deneme geri verilmez. Kota deposu çalışmazsa istek güvenli tarafta 500 alır ve iş mantığı çalışmaz (kota atlanmaz/fail-open değildir).
- **Depolama (V57):** `review_rate_limits(account_hash, action, window_start, expires_at, attempts)`, doğal PK `(account_hash, action, window_start)`, `expires_at` indeksi. V54 ile aynı gerekçeyle teknik/kısa ömürlü tablodur: UUID PK/`version` kolonu taşımaz; ham kullanıcı kimliği değil SHA-256 özeti saklanır; kişisel profil/metin/token yoktur. Hesap silme sayaç satırlarını da siler ve silinen hesap için yeni satır yazılmaz (bkz. "Auth entegrasyonu kararları"; bu, önceki "silme tabloya dokunmaz" kararının yerine geçer); kalan satırlar en geç pencere (en çok 1 gün, varsayılan 60 sn) + 5 dakika sonra kendiliğinden silinir. Temizlik dakikada bir (`AbuseQuotaCleanup`); çok örnekte aynı anda çalışması zararsızdır (idempotent DELETE).
- **Saat varsayımı:** Pencere sınırı servis örneğinin UTC saatinden (epoch bölümü) hesaplanır; sayaç satırı ortaktır. Örnek saatleri NTP ile senkron olmalıdır (Faz 1 hesap silme engeliyle aynı işletim varsayımı). Küçük saat farkı yalnız pencere sınırını kaydırır, sayımı çift saymaz veya sıfırlamaz. 5 dakikalık temizlik payı bu farkı karşılar.
- **Yapılandırma:** `ltz.review.abuse.review-create|review-like.limit|window` (`application.properties`). `limit` 1–100000, `window` tam saniye ve 1 sn–1 gün; geçersiz değer uygulamayı başlatmaz. Yeni ENV tanımlanmadı; ENV üzerinden geçersiz kılma Spring relaxed binding ile mümkündür ve ENV değişikliği kullanıcı onayı gerektirir.
- **Sınırsız kalanlar (karar bekliyor):** Puan PUT, tavsiye PUT, inceleme PATCH/DELETE ve okumalar için sayısal sınır onaylanmadı; uygulanmadı. Bunlar zaten tekil/idempotent yazımlardır (aynı kayıt güncellenir) ve satır üretmez; sınır istenirse ayrı ürün kararı gerekir.
- **Korunan davranışlar:** Kendi incelemesini beğenme yasağı (403), silinmiş/görünmez hedefte 404, bir kullanıcı/oyun için tek aktif metin (409), idempotent PUT/DELETE, hesap silme guard'ı değişmedi. Normal kullanım (ör. 5 inceleme, 30 beğeni + 30 kaldırma, çok sayıda okuma) testle doğrulandı ve engellenmedi.
- **REV-07 bağımlılığı (açık):** Rapor oluşturma API'si olmadığından rapor kotası ve mükerrer rapor engeli uygulanmadı. REV-07 tasarlanırken `REPORT_CREATE` eylemi aynı tabloyla eklenebilir (action sütunu biçim CHECK'idir, yeni migration gerekmez); mükerrer rapor engeli `(review_id, reporter)` benzersizliği olarak Admin sözleşmesiyle birlikte kararlaştırılmalıdır. Kendi incelemesini raporlayamama kararı REV-07 kabul koşulu olarak durur. Bunlar uygulanmış sayılmaz.
- **Test notu:** `ReviewApiTest` tek kullanıcıyla 11–12 inceleme isteği atan doğrulama/eşzamanlı tekillik testleri içerir; bu sınıfta `review-create.limit=1000` verildi. Kota davranışı varsayılan 5/60 ve 60/60 değerleriyle `AbuseQuotaApiTest`'te doğrulanır.

## Faz 3 — REV-08 ve REV-12

- Kullanıcı önce REV-08, test/kapanış/checklist; sonra REV-12 sırasını açıkça onayladı. Bu sıra uygulandı: 92/92 ve ardından 102/102 Review testi PASS. Faz 4 onaylı değildir; REV-07/REV-10 beklemeye devam eder.
- Yalnız game_ratings üzerinden PostgreSQL numeric round(avg(rating),2) ve count(*) hesaplanır. Java BigDecimal kullanılır; pozitif 1–10 değerlerde iki basamak HALF_UP (1.125 → 1.13). Tek kullanıcı/oyun kaydı V51 UNIQUE ile korunduğu için count(*) benzersiz puan veren kullanıcı sayısıdır. Kayıt statüsü sütunu yoktur; geçerli satırların tümü katılır. Metin/tavsiye/beğeni veya Steam verisi bu sayıya/ortalamaya girmez.
- Puan yoksa averageRating=null/ratingCount=0 ve HTTP 200. Bilinmeyen UUID ile puansız oyun ayrılmaz; Game'e callback veya katalog/görünürlük doğrulaması yapılmaz. Bu davranış Game'in görünmeyen oyunları göstermesi için izin değildir. Game kendi katalog kimlik/görünürlük politikasının sahibidir.
- GET /api/v1/review/games/{gameId}/rating-summary ve POST /api/v1/review/games/rating-summaries aynı aggregate sorgusunu kullanır. Toplu API zorunlu gameIds dizisi, en fazla 50 ham UUID, null eleman reddi, ilk görülme sırasında tekilleştirme sunar. Boş liste 200/items=[] ve sıfır SQL; dolu liste tek IN/GROUP BY SQL. Yanıt her benzersiz istenen oyun için öğe içerir; eksik gruplar null/0 ile bellekte tamamlanır.
- İki API de tek statement snapshot'ıyla AVG/count ve bütün oyun gruplarını tutarlı okur; readOnly/4 saniye transaction kullanılır. Ayrı isteklerde veri değişebilir. Kalıcı sayaç/cache veya istatistik tablosu eklenmedi. V51–V56 ve silme politikaları değişmedi; yeni migration gerekmedi. Mevcut game_id indeksi EXPLAIN ile doğrulandı; yeni yinelenen indeks eklenmedi.
- Kullanıcı ayrıca açıkça mevcut kullanıcı JWT güvenliğinin korunmasını istedi. Anonim veya yeni svc+jwt erişimi yok; AccountErasureSecurityConfiguration ve Auth kodu değiştirilmedi. Kullanıcı JWT'si Game servis kimliği yerine taşınmayacak. Game→Review erişimi NOT IMPLEMENTED/NOT VERIFIED olarak kalır.
- [Game talebi](request/game-review-integration.md) yeni 13 başlıklı ihtiyaç belgesidir; [Auth talebi](request/auth-review-integration.md) hedefe bağlı dar okuma izniyle genişletildi. Örnek scope adı kesinleşmiş değildir; ilgili sahipler nihai yolu/izinleri belirler. Bu API'nin Review kabulünün tamamlanması canlı Game entegrasyonunu veya global katalog minRating/RATING sıralamasını tamamlamaz.
- Histogram, tavsiye oranı, spam/rate-limit ve moderasyon etkileri eklenmedi; Faz 4 görevleri saklıdır. Pre-push/backendCheck/Trivy/Sonar **KULLANICI TALEBİYLE ÇALIŞTIRILMADI**; verify/merkezi coverage/PMD/ratchet sonucu yoktur. Yalnız modül testleri, PostgreSQL/Flyway ve API hata senaryoları çalıştırıldı. Ayrıntı [Faz 3 raporunda](REVIEW_SERVICE_PHASE_03_REPORT.md).

## Faz 4 — REV-14 istatistik kararları

- **Kaynaklar ayrıdır:** Puan dağılımı yalnız `game_ratings`, aktif inceleme sayısı yalnız `reviews` (`status='VISIBLE'`), tavsiye sayıları yalnız `game_recommendations` tablosundan gelir. Birbirinin yerine kullanılmaz; "puan veren kullanıcı sayısı" (`ratings.ratingCount`) ile "aktif inceleme sayısı" (`reviews.activeCount`) bilerek ayrı alanlardır.
- **Ortalama tek politikadır:** Dağılımla birlikte dönen `averageRating`, REV-08/REV-12 ile aynı `round(avg(rating),2)` SQL ifadesidir; ikinci bir hesap yolu yazılmadı. Test, aynı veride `rating-summary` yanıtıyla eşitliği doğrular.
- **Payda ve yuvarlama:** Tavsiye yüzdesinin paydası `recommendedCount + notRecommendedCount`'tur; tavsiye vermeyen kullanıcılar paydaya girmez. İki ondalık, HALF_UP, 0–100 ölçeği (Java `BigDecimal`, kesin tam sayı oranı). Payda sıfırsa yüzdeler `null`'dır (0 yazmak "herkes olumsuz" gibi okunurdu). Yüzdeler bağımsız yuvarlanır; yalnız tam eşitlik (ör. 1/800) durumlarında toplam 100,01 olabilir ve bu belgelenmiştir.
- **Boş veri:** Bilinmeyen veya hiç verisi olmayan oyun 200 döner; 10 sıfırlı kova, sıfır sayılar, `null` ortalama/yüzdeler. Game kataloğu çağrılmaz (REV-08 ile aynı: görünürlük politikası Game'indir).
- **Tutarlılık:** Tüm değerler tek SQL ifadesinde (bağımsız skaler alt sorgular) tek snapshot'tan okunur; `sum(distribution) = ratingCount` ve `recommended + notRecommended = total` eşzamanlı yazıcılar altında testle doğrulandı. N+1, kalıcı sayaç, materialized view, yeni tablo ve migration yoktur.
- **Uç adı:** `GET /api/v1/review/games/{gameId}/statistics`. Frontend/Game tüketicisi yoktur, ad bu görevde sabitlendi; tüketici belgesi/talebi gerekmedi. Yalnız kullanıcı JWT'si; anonim ve servis token erişimi eklenmedi.
- **Moderasyon ilişkisi:** `activeCount`, `VISIBLE` beyaz listesidir; ileride `HIDDEN/ARCHIVED` eklenirse otomatik dışlanır. Bu davranışın puan/tavsiye için de geçerli olup olmadığı REV-15 kararıdır (aşağıda); bugün puan ve tavsiye hiçbir moderasyon durumuna bağlı değildir.

## Faz 4 — REV-15 değerlendirmesi (UYGULANMADI — ENTEGRASYON VE ÜRÜN KARARI BEKLİYOR)

**Doğrulanan mevcut durum (kod incelemesi):**

- Şemada yalnız `VISIBLE` ve `DELETED` vardır (`ck_reviews_status`, V52). `HIDDEN`/`ARCHIVED`, moderasyon alanı, rapor tablosu, karar sürümü ve rapor kapanışı yoktur. Admin Service'te moderasyon kararını Review'a ileten onaylı sözleşme yoktur ([admin talebi](request/admin-review-integration.md) §12: TALEP/ÖNERİ).
- Görünürlük beyaz listedir: tek inceleme okuma, güncelleme, beğenme/kaldırma/okuma, öne çıkanlar (V56 kısmi indeks dâhil) ve REV-14 `activeCount` yalnız `status='VISIBLE'` kabul eder. `VISIBLE` dışındaki herhangi bir gelecekteki durum bu yollarda otomatik olarak 404/dışlanmış olur (fail-closed). Bu, "gizlenmiş içerik genel listede görünmez" kabul koşulunun yerel yarısını yapısal olarak karşılar; ancak şemada gizli durum olmadığından davranış testi yazılamaz ve kabul edilmiş sayılmaz.
- Bağımsız sayısal puan (`game_ratings`) ve tavsiye hiçbir inceleme durumuna bağlı değildir; metin silme onları etkilemez (REV-11, REV-14 testleri). Hesap silme tüm yerel kayıtları fiziksel siler.
- `uq_reviews_active_user_game` kısmi indeksi `status <> 'DELETED'` koşulludur; yani gelecekte gizlenmiş/arşivlenmiş bir inceleme kullanıcının tek aktif inceleme hakkını **tutmaya devam eder** (gizlenen yazarın yeni metinle moderasyonu aşması engellenir). Bu istenen sonuç olabilir ama bir ürün kararıdır.

**Neden kod yazılmadı:** Kalan işler ya Admin/Auth/Notification sözleşmesine ya da kesinleşmemiş ürün politikasına bağlıdır. Sahte moderasyon ucu, kullanıcıya moderatör yetkisi, gövdedeki `actorId` ile karar, yeni servis token yetkisi, yerel karar taklidi, rapor kapanışı ve kullanılmayacak `HIDDEN/ARCHIVED` şeması eklenmedi. Karar verilmeden eklenen şema/statü, ileride Admin sözleşmesini kısıtlayan ölü bir kontrat olurdu.

**Ürün kararı gereken konular (öneri yalnız yön gösterir, karar sahibi Hamza/ekip):**

1. *Gizlenen/arşivlenen incelemenin yazarının sayısal puanı ortalamaya ve dağılıma girer mi?* Öneri: girer — puan metinden bağımsızdır (REV-11) ve moderasyon metin içeriğine yönelir; puanı düşürmek kötü niyetli bir kullanıcıyı değil ortalamayı cezalandırır. Alternatif: kullanıcı moderasyonla puanından da düşürülür (kampanya/spam puanlaması için). Kabul: Hamza görev dosyası "açık politika ile tutarlı" der, politika seçilmedi, kod değişmedi.
2. *Tavsiye (olumlu/olumsuz) aynı kuralı izler mi?* Öneri: puanla aynı.
3. *`HIDDEN` ile `ARCHIVED` farkı:* geçici moderasyon (geri alınabilir) ve kalıcı/emekli içerik ayrımı; her ikisinde yazar kendi içeriğini görebilir mi? Yazar gizli incelemesini silebilir/düzenleyebilir mi (bugün `delete` VISIBLE olmayan kayıtta da çalışır; gizliyken silmek moderasyon kanıtını yok edebilir)?
4. *Beğeniler:* gizlenince korunur ve geri alınca döner mi, yoksa silinir mi? Öne çıkanlarda ve `likeCount`'ta gizli kayıt zaten dışlanır.
5. *Tek aktif inceleme hakkı* gizliyken tutulur mu (mevcut indeks tutar)?
6. *Rapor kapanışı:* kimin otoritesi (Admin vaka durumu mu, Review uygulama ACK'i mi), kapanış hangi karar/sürümle bağlanır; Review'da yalnız moderasyon sonucu mu saklanır, rapor mu (rapor REV-07'dir).
7. *Sıra/idempotency:* karar `actionId` + monoton sürüm/`decidedAt` ile gelmeli; eski kararın yeni durumu ezmemesi alıcı kuralıdır (admin talebi §9–§10 ile uyumlu). Hesap silmede kararlar ve izler fiziksel silinmeli, eski olay silinen veriyi geri oluşturmamalıdır.

Bu maddeler [Admin talebinin](request/admin-review-integration.md) §13'üne işlendi. REV-15, hem Admin sözleşmesi hem de yukarıdaki politika kararı gelene kadar `[ ] ENTEGRASYON BEKLİYOR` kalır.

## Faz 4 sonrası kullanıcı kararları — REV-15 politikası (31 Ekim 2026)

Kullanıcı Faz 4 raporunu kabul etti ve aşağıdaki kararları verdi. Bunlar yukarıdaki "Ürün kararı gereken konular" listesinin 1–2. maddelerinin karşılığıdır; kalan maddeler Admin entegrasyonuyla netleşir.

**Karara bağlananlar:**

1. **Sayısal puan ve ortalama:** Gizlenen veya arşivlenen metinsel incelemelerin bağımsız sayısal puanları, puanın kendisi ayrıca geçersiz kılınmadığı sürece LTZ ortalamasına, dağılıma ve puan sayısına dahil edilmeye devam eder. Metinsel incelemenin görünürlük durumu sayısal puanı otomatik silmez veya dışlamaz. (Puanın ayrıca geçersiz kılınması ayrı bir moderasyon eylemi olarak tanımlanmamıştır ve bu kararla uygulanmamıştır.) REV-08/REV-12/REV-14 mevcut davranışı bu karara zaten uyar: bu hesaplar `game_ratings` üzerindedir ve inceleme durumuna bağlı değildir. Kod değişmedi.
2. **Görünürlük:** Gizlenen veya arşivlenen incelemeler herkese açık listelerde ve öne çıkan incelemelerde gösterilmez. Mevcut `VISIBLE` beyaz listesi (tek okuma, güncelleme, beğeni, öne çıkanlar, `activeCount`) bu kuralı gelecekteki her gizli durum için yapısal olarak karşılar; ancak şemada gizli durum olmadığından davranış testi yoktur ve kabul tamamlanmış sayılmaz.
3. **Kapsam:** `docs/api.md` ve `docs-security/` değiştirilmez; değişiklik sınırı yalnız `backend/services/review-service/` ve `docs-review/`'dir. (Önceki "merkezi kataloğa aktarım için kapsam izni gerekir" notu, bu kararla aktarımın yapılmayacağı şeklinde güncellenmiştir.)
4. **Kod yasağı:** Onaylanmamış moderasyon sözleşmeleri için kod geliştirilmez; `HIDDEN/ARCHIVED` durumu, karar alıcı, rapor kapanışı ve Admin uçları eklenmedi.

**Admin entegrasyonuyla birlikte netleştirilecekler (karar verilmedi):** beğenilerin saklanması veya silinmesi, yazarın gizlenmiş/arşivlenmiş içeriğe müdahalesi (görme, düzenleme, silme), tavsiyenin gizlenmiş incelemeyle ilişkisi, tek aktif inceleme hakkının tutulması, rapor kapanışı otoritesi, karar sırası/idempotency ve hesap silme izleri. Bunlar [Admin talebinin](request/admin-review-integration.md) §13'ündedir.

**Açık kalanlar:** REV-07, REV-10, REV-13'ün raporlama kısmı (rapor kotası, mükerrer rapor engeli) ve REV-15'in entegrasyon bağımlılıkları tamamlanmış sayılmaz.

## Entegrasyon gereksinimlerinin belgelenmesi (31 Ekim 2026)

REV-13 (raporlamaya bağlı) ve REV-15 (moderasyon) için dış servis gereksinimleri yeni dosya açılmadan mevcut [Admin](request/admin-review-integration.md), [Auth](request/auth-review-integration.md) ve [Notification](request/notification-review-integration.md) taleplerine, her satırda sorumlu servis belirtilerek eklendi. Yeni karar verilmedi: raporlama oran sınırı sayısı, tekrar rapor yanıtı (200/409), kapanış durum adları, beğenilerin akıbeti, yazarın gizli içeriğe müdahalesi, gizle/arşivle/yeniden görünür geçişleri, bildirim üreticisi, moderasyon komutu kapsamı ve insan moderatör yetki aktarım modeli talep belgelerinde `KARAR BEKLİYOR` olarak kalır. Bu belgeleme REV-07, REV-10, REV-13 (raporlama kısmı) ve REV-15'i tamamlanmış yapmaz.

## Auth entegrasyonu kararları (31 Ekim 2026)

- **İlke:** Review, Auth'un mevcut sözleşmesine uyar; Auth kodu, izin listesi ve belgeleri değiştirilmedi. Kullanılan kaynaklar: `docs-auth/servis-sozlesmeleri.md`, `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`, Auth kaynağı ve salt okunur Library örneği.
- **Roller:** Yetki yalnız `roles` listesindeki Auth'un beş kanonik rolünden gelir; metin olarak gelen, bilinmeyen veya metin dışı öğeli `roles` yetki vermez. Review `MODERATOR`/`ADMIN` rollerine ek yetki tanımaz; moderasyon yetkisi servis token'ından veya kullanıcı rolünden türetilmez.
- **Eksik `aud`:** İmzalı ama `aud` claim'i olmayan kullanıcı token'ı geçersiz token'dır (401); NPE/500 olmaz.
- **Hesap silme × REV-13:** Kota sayacı yazılmadan önce hesap kilidi alınıp silme engeli denetlenir (403); silme sayaç satırlarını da siler. Böylece silinen hesabın kimlik içeren tek kalıcı izi kısa ömürlü V54 engeli kalır (Auth'un "silinen kullanıcının kaydı tutulmaz" çizgisi). Önceki karar ("hesap silme sayaç tablosuna dokunmaz, satırlar kendiliğinden silinir") bu kararla **değiştirildi**.
- **Test yaklaşımı:** Auth'un gerçek üretim kaynakları (`AccessTokenIssuer`, `ServiceTokenIssuer`, `JwksController`, `HttpAccountDataEraser`) geçici dizine derlenir; testin kendi geçici anahtarı kullanılır. Bu, canlı Auth kanıtı değildir (NOT VERIFIED).
- **Yapılmayanlar:** Review→User `ReviewSummaryChanged` üreticisi/token istemcisi (REV-10 onayı ve `AUTH_REVIEW_SERVICE_CLIENT_SECRET` için ENV onayı gerekir; tüketicisiz kod yazılmadı), Admin/Game/Notification çiftleri, yeni scope veya servis yetkisi.

## AbuseQuotaInterceptor sözleşmesi (31 Ekim 2026, Sonar BLOCKER sonrası kalıcı düzeltme)

- `preHandle`: kota uygun/uygulanmayan istekte `true`; kota aşıldığında 429 `application/problem+json` yanıtını (`Retry-After`, gövdede `status=429`) **interceptor kendisi tek kez yazar ve `false` döner**. İstek denetçiye, servise ve transaction'a ulaşmaz; Spring'in ikinci bir hata yanıtı üretmesi için istisna fırlatılmaz.
- Sayaç deposu hatası gibi beklenmeyen istisnalar yakalanmaz; yanıt yazılmadan yayılır ve genel işleyiciyle 500 olur (fail-closed). Önceki geçici çözüm (`return !response.isCommitted()`) Sonar kuralını yalnız biçimsel geçtiği ve kota aşımında hiç `false` dönmediği için kaldırıldı. `AccountErasureExceptionHandler` içindeki özel 429 işleyicisi ölü kod olduğundan silindi.
- Doğrulama: `AbuseQuotaInterceptorTest` (4) ve `AbuseQuotaApiTest` (engellenen istekte Game doğrulaması 5 kez çağrılır, 6. istek servise ulaşmaz; engellenen beğeni oyu değiştirmez; `Retry-After` tek değer, `application/problem+json`).
