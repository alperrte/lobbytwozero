# Game Service — güncel kalan işler

Tarih: 5 Kasım 2026 · GAME-11 güncellemesi: 7 Kasım 2026 · Sahibi: Hamza. Bu belge mevcut kodu esas alır; eski faz
raporlarındaki bekleme ifadeleri tarihsel olabilir. API kaydı: `docs/api.md` → GAME.

## 1. GAME-04 / GAME-05 / GAME-15 — Review puanları

Kod hazır: AuthServiceTokenClient, ReviewRatingClient, V172 puan projeksiyonu,
kalıcı tarama/kiralama, minRating, RATING sıralaması ve rating durumları mevcut.
Game toplu POST çağırmaz; imleçli GET dışa aktarmayı tarar. Görünüm, filtre ve
sıralama aynı yerel projeksiyondan gelir. İlk başarılı taramadan önce puan
UNAVAILABLE; puan filtresi/sıralaması RATING_INTEGRATION_PENDING verir.

Auth'un mevcut çiftinde `rating-summaries:read,game-references:read` vardır;
ikincisi tek başına puan okuma yetkisi değildir. Game'in gerçek-Auth testindeki
tek kapsam varsayımı kaldırıldı. Alıcı sözleşmesinin uyumu Game kabul betiğinde
sınanır; Review test dosyalarında bu göreve ait değişiklik bırakılmadı.

Gerçek servis kabulü için `node ops/game-review-e2e.mjs` eklendi. Üç ayrı geçici
PostgreSQL ve gerçek Auth/Game/Review jar'ları kullanır; DEV veya ENV dosyası
okumaz. Kullanıcı/admin ve D4 kimlikleri yalnız fixture anahtarıyla imzalanır;
Game → Review servis token'ı gerçek Auth HTTP üreticisinden gelir. Bu test gerçek
kullanıcı login'i veya Auth silme worker'ının tüm orkestrasyonunu kanıtlamaz.
İlk gerçek servis koşusunda 11 kabul kontrolü geçti; Windows süreç ağacı temizliği düzeltildi. Çalıştırma sınırları api.md GAME puan projeksiyonu kaydındadır.

## 2. GAME-17 / GAME-23 — IGDB detay ve dil

Adaptör, Steam AppID eşlemesi, dil türleri, provenance ve manuel kilit koruması
mevcut. Kimlik bilgilerinin ikisi de yoksa IGDB kurulmaz. Yeni
IgdbEnrichmentIntegrationTest gerçek adaptör → yenileme → PostgreSQL → public API
zincirini, UNKNOWN dil semantiğini, TTL'yi, manuel kilidi ve kesintiyi sınar;
yalnız dış IGDB/Twitch sunucusu sahtedir.

7 Kasım 2026'da kullanıcı `.env` ve `.env.dev` okumasına açıkça izin verdi.
Yalnız gerekli IGDB kimlikleri süreç ortamına aktarılarak **gerçek IGDB → Game
zenginleştirme servisi → geçici PostgreSQL → güvenlik filtreleri etkin public API**
pilotu geçti. Steam AppID 570 sentetik katalog referansı kullanıldı; açıklama,
IGDB provenance ve dil desteği doğrulandı. İkinci okuma TTL nedeniyle FRESH döndü.
DEV/prod DB'ye yazılmadı; anahtarlar çıktı veya dosyaya kopyalanmadı.
Yeniden üretme: `node ops/game-provider-pilot.mjs --allow-env-files`.
Bu komut ENV okuması için her oturumda açık izin gerektirir; normal CI'ya dahil değildir.
Dağıtım ve ticari kullanım uygunluğu bu yerel pilotla onaylanmış sayılmaz.

## 3. GAME-11 — Game tüketicisi uygulandı (7 Kasım 2026)

Kullanıcı talimatıyla Nisa'nın mevcut Community API'si tüketilir. Başarımlar Game'de;
diğer üç tür Community'de kalır. Game modül ucu `contractVersion=2` ile EXTERNAL ve
güvenli Gateway liste yolu verir. Varsayılan eski JSON korunur; dış modüllerde v1
deprecated'dır. Başarım yanıtı değişmez. Yeni `features/game` web modülü katalogdan
oyun detayına, modül listesine ve Community detayına erişir; kullanıcının çereziyle,
no-store ve düz metin gösterimiyle çalışır. İlk tüketici aşamasından sonra bulunan
Community bağlantı kesintisi hatası kullanıcının açık talimatıyla dar kapsamda düzeltildi.

GameModuleTest 8/8 geçti. Gerçek Game/Community/Gateway ve iki geçici PostgreSQL
ile ilk koşuda 15 ortak kontrol geçti; DB kesintisindeki 500/503 farkı giderildikten
sonra **17/17 ortak kabul PASS** oldu. Community tam doğrulaması **501/501 PASS**,
merkezi kapsam, yeni ihlal ve Trivy kapıları PASS. Game istemcisi hatayı boş listeye
çevirmiyor. Gerçek Auth login'i ve Social/Admin
olay teslimi bu fixture testinin kapsamında değildir. Detay ve yeniden üretme:
[GAME-11-Nisa-Karar-ve-Entegrasyon.md](GAME-11-Nisa-Karar-ve-Entegrasyon.md).

## 4. GAME-24 / GAME-04 indirim filtresi — IsThereAnyDeal seçildi

Kullanıcı bu oturumda ITAD'ı seçti; eski erteleme kararı bu seçimle değişti.
ItadPriceClient resmî toplu Steam kimliği eşleme ve fiyat uçlarını kullanır.
V173 fiyat snapshot, pazar yenileme durumu ve kalıcı tek worker kiralaması ekler.
Yeni public GET /api/v1/game/games/prices en çok 50 oyun için yerel fiyat sunar;
keşif/arama discountedOnly filtresini aynı SQL'de ve yalnız güncel tekliflerle uygular.
Ülke/para birimi açıkça belirlenir; ilk arka plan pazarı TR/TRY, dönüşüm yoktur.
NO_OFFER, UNAVAILABLE ve STALE fiyat=0 veya ücretsiz oyun anlamına gelmez.

Anahtar boşken dış fiyat çağrısı yoktur. Kullanıcı gerçek anahtarı daha sonra
sağladı ve kaydetmeyi istedi; yalnız `.env.dev` içine ITAD_API_KEY eklendi.
OAuth client bilgileri kullanılmadı. Gerçek anahtar belge, test veya loga yazılmadı.
Ülke/para birimi seçimi, atıf, mağaza bağlantısı ve ekrandayken TTL dolması web'de
mevcut; NO_OFFER/UNAVAILABLE/STALE ücretsiz fiyat gibi sunulmaz.

7 Kasım canlı pilotu **PASS**: Game gerçek ITAD adaptörü → geçici PostgreSQL →
güvenlik filtreleri etkin fiyat API'si; ikinci tur TTL nedeniyle yeniden çekmedi.
Steam→ITAD eşleme yönü düzeltildi, resmî itad.link bağlantısı doğrulamaya eklendi.
ITAD TR ülkesinde TRY döndürdüğünden worker/keşif/web ilk pazarı TR/TRY oldu;
kur dönüşümü yapılmadı. `node ops/game-provider-pilot.mjs --allow-env-files --itad`.
LTZ ticari kullanım uygunluğu, hacim kotası ve DEV/prod dağıtımı ayrı konulardır.

## 5. GAME-26 kapalı kabulü ve ayrı kimlik mutasyonu kapsamı

**5 Kasım 2026 kabul kapanışı geçerlidir** (`6578e3e`). Asıl GAME-26 yarım iş
olarak raporlanmaz. 7 Kasım kullanıcı isteğiyle gündeme gelen gerçek taşıma/ayırma,
ayrı bir kapsam genişletmesidir; şimdilik servis sahiplerine sözleşme taslağı teslim edilmiştir.

Manuel düzeltme kilitleri, audit ve arşivleme/geri alma mevcut kabul kapsamını
karşılar. HTTP reassign yalnız no-op durumda 200; gerçek reassign/split
409 FOREIGN_REFERENCE_SAFETY_UNVERIFIED verir. Auth'un okuma kapsamı mevcut olsa
da tüketicilerde eşzamanlı yazmaları durduracak protokol hazır değildir.

Tam tüketici envanteri, prepare/commit/telafi ve Auth mutasyon kapsamlarını içeren
[karar taslağı](../docs/decisions/0002-oyun-dis-kimlik-duzeltme.md) hazırlandı.
Bu protokol yalnız Library/Review sayımı eklenerek güvenli hale gelmez; başka
sahiplerin servisleri de gameId saklar. Koruma kaldırılmadı ve gerçek taşıma
uygulanmış sayılmadı. Kimlik mutasyonu için migration eklenmedi; fiyat V173 sonrası boş Game aralığı V174–V180.

## 6. Diğer sınırlar

- GAME-07/12/13/14 yerel iş akışları ve önceki canlı Bearer kontrolleri raporlarda
  mevcut. Bu çalışma tüm Gateway çerez yolculuklarını yeniden doğrulamadı.
- Game'in trusted-proxies listesi boş; Gateway'den gerçek istemci IP aktarımı ve
  Game tarafındaki güvenilir proxy ayarı birlikte doğrulanmalı. Gateway'de güncel
  CORS/çerez→Bearer/kimlik başlığı temizliği kodu var; eski “hiç uygulanmadı”
  ifadeleri geçerli değildir. Ortak Gateway kodu bu çalışmada değiştirilmedi.
- Game web yönetim/geliştirici/moderasyon ve oyuncu geri bildirim ekranları eklendi;
  Game'in mevcut API'lerini kullanır. Ayrı bir frontend sahibi beklenmiyor. Ortak
  Admin/Notification servislerinin ilave iş akışları bu göreve dahil edilmedi.
- Toplu kart en fazla 50 kimlik alır; LTZ puanı taşımaz. Puan keşif listelerindedir.
- GAME-25 büyük katalog ve puan performans testleri mevcut; üretim garantisi yok.

## 7. Güvenli doğrulama

Game kalite komutu: `node pre-push/ci/backend.mjs --modules services/game-service`.
Windows'ta daraltılmış ortam Docker Compose eklentisini bulamıyorsa yerel çalıştırma
uyarlaması gerekir. Maven Central 429 bu oturumda yerel Maven önbelleğiyle aşıldı;
bağımlılık sürümü değiştirilmedi. Başarısız/atlanmış test PASS sayılmaz.

Gerçek servis kabulü: güncel Auth/Game/Review jar'ları hazırken
`node ops/game-review-e2e.mjs`. Yalnız kendisinin etiketlediği geçici konteynerler
silinir; DEV/prod veritabanları ve çalışan servisler değiştirilmez.

## 8. Son doğrulama — 5 Kasım 2026

- Game: **425 test**, failure/error/skipped 0/0/0; Maven clean verify PASS.
  JaCoCo LINE **%98,00**, değişen executable satırlar **%98,04**; merkezi coverage
  ve Checkstyle/PMD yeni ihlal kapısı PASS. Günlük: logs/game-final-verify.log.
- Review belgeleri, testi ve api.md REVIEW bölümüne bu oturumda yapılan kapsam
  dışı değişiklikler kullanıcı uyarısıyla geri alındı. Review için bu görevde
  geliştirme veya güncel test sayısı iddiası tutulmaz.
- Gerçek Auth/Game/Review uygulamaları + ayrı geçici PostgreSQL: **11/11** kabul
  kontrolü PASS. Son koşuda 3 test süreç ağacı ve 3 konteyner temizlendi.
  Kullanıcı/admin/D4 fixture token'ı ile gerçek Auth'tan alınan servis token'ı
  ayrımı yukarıda belirtilmiştir; gerçek login veya tam hesap silme Saga'sı değildir.
- Node fixture kontrolleri: **10/10 PASS**. Secretsiz Compose şablonu, yalnız
  komuta verilen sahte Auth yer tutucularıyla config --quiet doğrulamasını geçti.
- 100.000 oyun: liste p95 43,30 ms, arama 11,55 ms, birleşik filtre 142,70 ms,
  eşzamanlı arama 17,72 ms; puanlı sorgularda en yüksek p95 137,84 ms. Test
  ortamı önceki GAME-25 ile aynı; üretim garantisi ve yeni fiyatlar için hacim SLO'su değildir.
- **Tam CI kapısı PASS değildir:** Trivy bağımlılık taraması `Trivy execution failed;
  raw output withheld` hatasıyla tamamlanamadı. Uygulama testlerinin başarılı olması
  HIGH/CRITICAL bağımlılık taramasını geçmiş sayılmaz; scanner/registry erişimi ayrıca çözülmelidir.
- Auth kaynağı değiştirilmedi. İmaj hazırlamak için yapılan ilk koşuda bir Mailpit
  testi 15 sn timeout verdi; tekrarında **533/533 test PASS** oldu. O tekrarın
  coverage adımı çalışma sürerken değişen global Git bağlamını reddetti; bu koşu
  tam Auth verify PASS olarak raporlanmaz. Game ve Review'ın son kapsamları ayrı doğrulandı.

## 9. GAME-11 son doğrulama — 7 Kasım 2026

- Game backend: **427 test**, failure/error/skipped 0/0/0; Maven clean verify PASS.
  Toplam LINE kapsamı **%97,96**; değişen executable satırlar **%90 (9/10)**;
  merkezi kapsam/baseline kapısı PASS. Checkstyle/PMD mevcut ve yeni ihlal **0**.
  `logs/game-community-verify.log`. Windows Compose yolu ve Maven'in mevcut yerel
  önbelleği geçici çalıştırma uyarlamasıyla kullanıldı; ortak CI kodu değiştirilmedi.
- Frontend lint PASS; Vitest **343 PASS, 1 mevcut SKIP**. Game testlerinde atlama yok.
  TypeScript PASS; Next tipleri ve Chromium, gerçek ENV dosyaları alınmadan hazırlanan
  izole frontend kopyasında çalıştırıldı. TR/EN mesajları, kaynak yolları, düz metin,
  401/404/429/500/503 ayrımı, yeniden deneme ve odakta eski veriyi kaldırma doğrulandı.
- Playwright Game senaryoları **2/2 PASS** (1440 px ve 390 px); katalog → oyun detayı
  → rehber listesi → detay, kesinti/yeniden deneme, yatay taşma, HTML'in metin olarak
  kalması ve aynı oyun kimliğiyle TR→EN geçişi. API yanıtları bu tarayıcı testinde
  fixture'dır; gerçek servis kabulünden ayrı tutulur. Günlük: `logs/game-browser-e2e.log`.
- Gerçek Game/Community/Gateway: **15 kabul kontrolü PASS, ilk DB kesinti yanıtı FAIL**.
  Community doğrudan ilk okumada **500** döndürüyor; sözleşme **503** istiyor.
  Sonraki okumada 503 alınabilmesi ilk hatayı geçerli kılmaz. Bağlantı kaybından
  sonraki Hibernate rollback hatası Nisa'nın servisinde incelenmeli. Detay önceki
  karar belgesine işlendi. İki geçici PostgreSQL ve test JVM süreç ağaçları temizlendi.
- **Tam CI PASS değil:** Trivy bağımlılık taraması `Trivy execution failed; raw output withheld`
  ile tamamlanamadı. Yerel tarama çalıştırıcısında bekleme 60 saniyeyle sınırlandı;
  tarama atlanmadı veya PASS sayılmadı. `BACKEND TEST CHECK PASSED` / `SAFE TO COMMIT`
  iddiası yoktur.
- Community, Gateway, Auth, Library ve Review kaynakları değiştirilmedi. `docs/api.md`
  içinde yalnız GAME bölümü değişti. Yeni ENV, migration veya gerçek DB/deploy işlemi yok.

## 10. GAME-11 kesinti düzeltmesi ve ortak kabul — 7 Kasım 2026

Kullanıcının açık talimatıyla önceki yalnız-Game sınırına bu hata için istisna verildi.
Community'de yalnız GlobalExceptionHandler, controller testleri ve yeni gerçek
PostgreSQL kesinti testi değişti. Auth, Gateway, Library ve Review kodu değiştirilmedi.
Yeni ENV veya migration yoktur. API kataloğunun bu düzeltmeye ait COMMUNITY liste/detay
test kanıtları ve GAME ortak kabul kaydı güncellendi.

- Community: **501 test**, failure/error/skipped 0/0/0; clean verify PASS.
  LINE **%99,13**; değişen executable satırlar **%94,12 (16/17)**; baseline PASS.
  Checkstyle 0, PMD mevcut 6, yeni ihlal 0. Günlük: `logs/community-outage-verify.log`.
- Community Trivy: paketlenmiş runtime envanteri tam, HIGH/CRITICAL **0**, PASS.
  Merkezi servis kontrolü **BACKEND TEST CHECK PASSED** verdi.
- Önceki Game bağımlılık taraması mevcut doğrulanmış JAR üzerinde yeniden çalıştırıldı:
  runtime envanteri tam, HIGH/CRITICAL **0**, PASS. `logs/game-dependency-recheck.log`.
  Yukarıdaki eski Trivy başarısızlığı tarihsel sonuçtur; açık tarama engeli giderildi.
- Gerçek Game/Community/Gateway: **17/17 PASS**, ilk doğrudan DB kesintisi yanıtı ve
  Gateway üzerinden 503 dahil. Geçici kaynaklar temizlendi. `logs/game-community-e2e.log`.
- Bu sonuç GAME-11'in yerel Game tüketici entegrasyonunu doğrular; gerçek Auth login'i,
  Social/Admin olay teslimi ve DEV/prod dağıtımı için doğrulama iddiası değildir.

## 11. Kalan Game web işleri ve canlı pilot — 7 Kasım 2026

- Katalog: birleşik filtreler, puan sıralaması, sıralama yönü ve URL'de saklanan
  seçimler eklendi. Boş `q` gönderiminin 400 üretmesi giderildi.
- Detay: kategori/etiket, dil desteği (UNKNOWN korunur), gereksinimler, güvenli medya,
  kaynak/doğrulama bilgisi, geliştirici içeriği ve ITAD fiyat durumları eklendi.
- `/tr/oyunlar/yonetim` / `/en/games/manage`: rol bazlı Game yönetimi;
  oyun oluşturma/düzenleme, arşivleme/geri alma, manuel kilit kaldırma, geliştirici
  yetkisi, popüler seçim, başarım yönetimi ve içerik gizleme/geri alma eklendi.
- Geliştirici: yalnız yetkili oyunlar, açıklama/medya/not yönetimi ve sürümlü geri
  bildirim durum geçişi. Oyuncu: kendi geri bildirimini oluşturma ve filtreli liste.
  Formlar normal alan kullanır; CSRF alınır, bekleyen istek tekrar gönderilmez,
  409'da taslak korunur, silme/gizleme/arşivleme için açık onay gerekir.
- Auth/Gateway/Game gerçek HTTP kabulü: **8/8 PASS**. İki sentetik hesap, gerçek
  SMTP doğrulaması/login, `/auth/me`, CSRF'siz yazmanın reddi, doğru CSRF ile
  feedback yazması, kullanıcılar arası görünürlük ve PLAYER rol sınırı sınandı.
  `node ops/game-auth-e2e.mjs`; yalnız iki geçici PostgreSQL ve Mailpit kullanır,
  kaynakları temizler. HTTP CookieJar kabulüdür; tarayıcı HTTPS deploy kabulü değildir.
- IGDB canlı uygulama/DB/API pilotu PASS; `logs/game-igdb-live-pilot.log`.
- ITAD canlı kabulü **NOT VERIFIED**: her iki izin verilen ENV dosyasında anahtar
  yok. Kimlik taşıma/ayırma koruması da ekip protokolü kararı olmadan kaldırılmadı
  (bölüm 5). Bu iki konu açıkken “her şey eksiksiz bitti” denmez.

Yeni çalışma Game alanıyla sınırlıdır. Auth ve Gateway'in mevcut kaynakları yalnız
kabul testi için yeniden paketlendi; test atlanarak üretilen bu JAR'lar o servislerin
kalite kapısını geçmiş sayılmaz. Gerçek ENV, başka servis kodu veya migration değişmedi.

### Bölüm 11 doğrulama sonucu

- Game merkezi kontrolü: **BACKEND TEST CHECK PASSED**; **427/427** standart test,
  failure/error/skipped **0/0/0**, clean verify PASS. LINE **%97,96**,
  değişen executable satırlar **%90 (9/10)**; merkezi kapsam/baseline PASS.
  Checkstyle/PMD yeni ve mevcut ihlal **0**. Runtime Trivy HIGH/CRITICAL **0**.
  `logs/game-remaining-verify.log`. Windows Compose yolu ve mevcut Maven önbelleği
  yerel çalıştırıcıda uyarlandı; merkezi politika veya kaynak kodu değiştirilmedi.
- Frontend: lint PASS, typecheck PASS; **395 PASS, 1 mevcut SKIP**, Game testlerinde
  atlama yok. Next tipleri gerçek ENV alınmayan izole kopyada doğrulandı.
  `logs/game-remaining-lint.log`, `logs/game-remaining-types.log`,
  `logs/game-remaining-unit.log`. Pencere odağında taslak kaybı önce kırmızı
  testle yeniden üretildi; yetki listesi yenilenirken mevcut taslak korunarak düzeltildi.
- Playwright: **5/5 PASS**; 1440/390 px katalog→Community yolculuğu, birleşik filtre
  ve sıralama, admin sürümlü PATCH/CSRF/409 taslak koruma, arşivleme onayı,
  geliştirici sürümü ve yetki kaybı. Yanıtlar browser fixture'ıdır.
  `logs/game-remaining-browser-final.log`. Masaüstü/mobil görsel kontrol ve taşma kontrolü yapıldı.
- Frontend bağımlılık taraması: tam npm paket envanteri, HIGH/CRITICAL **0**, PASS;
  `logs/game-web-dependencies.log`. Sağlayıcı çalıştırıcı testleri **2/2 PASS**.
- Tüm depo için `SAFE TO COMMIT` iddiası yoktur: yukarıdaki doğrulama Game ve
  frontend kapsamıdır. ITAD anahtarı/kullanım uygunluğu ve kimlik mutasyonu ekip
  protokolü halen açık dış bağımlılıklardır; normal uygulama/fixture testleri bunların
  canlı kabulü yerine geçmez.

## 12. ITAD anahtarı ve gerçek fiyat kabulü — 7 Kasım 2026

Bölüm 11'deki “anahtar eksik / canlı ITAD NOT VERIFIED” sonucu tarihsel kaldı.
Kullanıcının açık talimatıyla yalnız .env.dev güncellendi; dosya Git ignore kontrolü
geçti. Diğer gerçek ENV dosyaları ve OAuth client bilgileri değiştirilmedi.
Gerçek ITAD → uygulama adaptörü → PostgreSQL → HTTP controller pilotu PASS;
`logs/game-itad-live-pilot.log`. DEV DB ve kalıcı katalog verisine dokunulmadı.
Fiyat kimliği eşleme yönü, resmî kısa URL alanı ve TR/TRY varsayılanları düzeltildi;
mock testleri de gerçek sözleşmeyle uyumlu hale getirildi. Normal testler sağlayıcıya
çıkmaz. Bu kabul GAME-26 koordinasyon sözleşmesinin uygulandığı anlamına gelmez.

### Bölüm 12 son doğrulaması

- Game: **428/428**, failure/error/skipped 0/0/0; merkezi kontrol
  **BACKEND TEST CHECK PASSED**. LINE %97,96; değişen satırlar %94,12 (16/17),
  baseline ve yeni ihlal kapısı PASS; Trivy HIGH/CRITICAL 0.
  `logs/game-itad-verify.log`. İlk koşuda yalnız anotasyon değişikliği nedeniyle
  ölçülebilir satır kontrolü durdu; TR/TRY pazar ayarı application.properties'e
  taşındı. Kalite politikası değiştirilmeden son tam koşu geçti.
- Gerçek ITAD pilotu son yapılandırmayla PASS: `logs/game-itad-live-pilot.log`.
- Frontend lint/typecheck PASS; **395 PASS, 1 mevcut SKIP**. ITAD çalıştırıcı
  testleri **3/3 PASS**; seçili sağlayıcının dışındaki anahtarlar aktarılmaz.
- Yeni migration veya başka servis kodu değişikliği yok. Yeni ayarın çalışan Game
  sürecine alınması yeniden başlatma gerektirir; kalıcı DEV/prod dağıtımı yapılmadı.

## 13. Gerçek DEV açılışı — 7 Kasım 2026

Kullanıcı Game yeniden başlatmasını, ardından tüm LTZ proje konteynerlerinin
başlatılmasını açıkça istedi. `.env.dev` profili ve dev-mail profiliyle mevcut
volume'lar korunarak 20 konteyner başlatıldı. Güncel olmayan 12 servis JAR'ı
kaynak değiştirilmeden, testler tekrarlanmadan paketlendi; 18 runtime imajı güncel
JAR'lardan, mevcut Dockerfile'ın root olmayan runtime düzeniyle üretildi.
Bu paketleme diğer servislerin tam kalite kapısını geçmiş sayılmaz.

- **18/18 Java servis** actuator health HTTP 200 / UP; PostgreSQL ve Mailpit healthy.
- Çalışan Game paketinin SHA-256 değeri güncel JAR ile eşleşti; runtime kullanıcı
  ltz, ITAD anahtarı .env.dev ile eşleşti. Gizli değerler çıktıya yazılmadı.
- Gerçek Gateway üzerinden Game katalog ve katalog durum uçları HTTP 200.
- DEV kataloğunda 2 aktif oyun var; Steam bağlantılı MAIN_GAME adayı 0, güncel
  TR/TRY teklifi 0. Bu nedenle mevcut oyun için fiyat ucu UNAVAILABLE dönüyor;
  fiyat veya uygun oyun uydurulmadı, kalıcı test verisi eklenmedi.
- ITAD'ın gerçek fiyat getirdiği zincir bölüm 12'deki geçici DB pilotuyla zaten
  doğrulandığından dış sağlayıcı kabulü gereksiz yere tekrarlanmadı.
- Sadece LTZ Compose projesi başlatıldı. Kaynak kodu, gerçek ENV dosyaları,
  geçmiş migration'lar veya volume'lar bu açılışta değiştirilmedi/sıfırlanmadı.

Secretsiz kanıtlar: `logs/ltz-dev-start.log`, `logs/ltz-dev-runtime-health.json`,
`logs/ltz-game-dev-check.log`. Başlatma profili: `docker compose --env-file .env.dev
--profile dev-mail up -d --no-build`. Frontend Compose içinde yer almıyor.
GAME-24'ün yerel geliştirme/canlı sağlayıcı kabulü tamamdır; bu sonuç tüm oyunların
fiyatının indekslendiğini, production çıkışını veya GAME-26 protokolünün uygulandığını göstermez.

## 14. GAME-26 durum/kapsam düzeltmesi — 7 Kasım 2026

Önceki sohbetlerde gerçek kimlik mutasyonunun “GAME-26'nın kalan işi” olarak
sunulması doğru değildi. 5 Kasım kapanışı, güvenli HTTP engeliyle yapılan kabul
kapsamı için geçerlidir. Yeni kullanıcı talebi ayrı kapsam genişletmesidir;
asıl taşınacağına ilişkin iş kuralı onaylı, teknik protokol PLANNED/TASLAK'tır.
API kataloğu, karar taslağı ve servis sahipleri sözleşmesi bu ayrımı açıklar.
Auth'taki okuma izni çalışan referans/dondurma protokolü veya yeni yetkiler demek değildir.
Bu düzeltmede kod/izin/migration/servis çalışma davranışı değiştirilmedi.
