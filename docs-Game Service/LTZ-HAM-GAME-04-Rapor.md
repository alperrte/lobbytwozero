# LTZ-HAM-GAME-04 — Faz 4 uygulama ve doğrulama raporu

Tarih: 2026-10-21 · Sorumlu: Hamza · Servis: game-service

**Durum: GAME-07, GAME-12 ve GAME-13 Game Service sınırı içinde uygulandı ve test edildi. GAME-15'in herkese açık toplu kart ucu uygulandı; Review Service puan tarafı sözleşme olmadığı için açık. Tüm yetkili uçlar GEÇİCİ, Auth'tan bağımsız JWT doğrulamasına dayanır ve Auth Service entegrasyonu TAMAMLANMADI (NOT VERIFIED). Faz 4 bütünü "üretimde kullanıma hazır" sayılmaz.** Faz 5 başlatılmadı. Commit veya push yapılmadı. **İkinci geçişte (doğrulama turu) gerçek eşzamanlılık, toplu uç rate limit'i (Game Service tarafı), medya host doğrulaması ve CSRF incelemesi tamamlandı; Gateway'den istemci IP iletimi onay bekliyor. Güncel sonuç: 182 test, %98,47 kapsam, SonarQube PASSED. Ayrıntı: "Faz 4 doğrulama turu".**

Kullanıcı kararları: Auth için "resource server + test JWT" (geçici ve fail-closed), batch için "herkese açık küçük POST", medya için "HTTPS + izinli host listesi". Ayrıntı: [Auth entegrasyonu](LTZ-HAM-GAME-04-Auth-Entegrasyonu.md).

## Görev sırası ve sohbet checklist'i

GAME-07 → GAME-12 → GAME-13 → GAME-15 sırasıyla ele alındı.

- [x] GAME-07 — Admin oyun oluşturma/güncelleme/arşivleme/geri alma; manuel düzeltmelerin senkronizasyonda korunması; denetim kaydı. Fiziksel silme bilinçli olarak yok.
- [x] GAME-12 — Oyun bazlı geliştirici yetkisi; token rolü ve oyun yetkisi ayrı ayrı denetlenir.
- [x] GAME-13 — Açıklama, görsel/video/demo bağlantıları, güncelleme notları; doğrulama, bağlantı güvenliği, moderasyon, sahiplik.
- [x] Doğrulama turu — gerçek commit'li transaction'larla eşzamanlılık testleri (13 test).
- [x] Doğrulama turu — toplu uç için 429 rate limit (Game Service tarafı). [ ] Gateway'den istemci IP iletimi: **ortak yapılandırma, onay bekliyor**.
- [x] Doğrulama turu — Steam CDN/medya host doğrulaması ve daraltılması. [x] CSRF istisnası incelemesi (JSON şartı eklendi).
- [ ] GAME-15 — `POST /games/batch` hazır (N+1 yok, herkese açık). **Review Service puan özeti, timeout/kısmi başarısızlık davranışı ve Library/Review'ın bu ucu tüketmesi açık.**

## GAME-07 — Yetkili oyun yönetimi

- Uçlar `/api/v1/game/admin/**`, rol `ADMIN`. Oluşturma yalnız `INTERNAL` oyun üretir; köken, durum ve kimlik istemciden alınmaz (bilinmeyen alan 400).
- Güncelleme `PATCH` + iyimser kilit `version`: gönderilmeyen alan değişmez, boş metin/dizi açıkça temizler. Kısmi istek sağlayıcı verisini yanlışlıkla silmez.
- **Fiziksel silme yok:** Library/Review referansları başka servislerde olduğundan doğrulanamaz; "silme" işlemi arşivlemedir (ARCHIVED). Arşivlenen oyun public detay/kart/liste/batch'ten çıkar, alt verileri ve geçmişi korunur, editör seçimi temizlenir; geri alınabilir. İşlemler idempotenttir. MERGED oyun yönetim ucundan değiştirilemez (kimlik işlemidir).
- **Manuel düzeltme koruması:** EXTERNAL oyunda gerçekten değişen sağlayıcı alanları (`title`, `description`, `releaseDate`, `gameType`, `developers`, `publishers`) `game_field_overrides` ile kilitlenir; katalog aktarımı ve detay yenilemesi kilitli alanı yazmaz (testle doğrulandı). Kilit gerekçeyle kaldırılabilir. Bu, GAME-26'nın tam kapsamı (hatalı eşleme düzeltme, ayrı takip ekranı) değildir.
- Editör listesi için yetkili HTTP girişi eklendi (`PUT /admin/popular`); Faz 3'te yalnız iç komuttu.
- Her yönetim eylemi `game_admin_audits` kaydına yazılır: aktör, rol, eylem, gerekçe (8–1000 karakter), değişen **alan adları** (içerik metni değil). Tablo append-only (trigger UPDATE/DELETE'i reddeder, testli).
- Kapsam dışı/bilinçli: sistem gereksinimi düzenleme (GAME-08 modeli var, yönetim ucu yok), `releaseDate`/`earlyAccess` değerini null'a döndürme, terim sözlüğünün ayrı yönetimi (admin terimi kodla ekler).

## GAME-12 — Geliştirici yetkisi

- `game_developer_grants`: oyun bazlı yetki; verme/listeleme/geri alma yalnız ADMIN. Geri alma kaydı silmez. Aynı geliştirici/oyun için tek aktif kayıt DB kısmi UNIQUE ile korunur.
- Yönetim uçları (`/developer/**`) iki koşulu ayrı denetler: **token rolü `GAME_DEVELOPER`** (zincir seviyesi, 403) ve **o oyun için aktif yetki** (servis seviyesi, 403 `ACCESS_FORBIDDEN`). Yalnız rol, yalnız yetki ya da ADMIN/MODERATOR rolü yetmez. Arşivlenmiş oyun yönetilemez (404). Başka oyunun kimliğiyle içerik silme denemesi bulunmaz (IDOR testli).
- Sınır: Game Service, yetki verilen kullanıcının gerçekten `GAME_DEVELOPER` rolüne sahip olduğunu doğrulayamaz (Auth'un konusu); kullanım anında token rolü zorunludur.

## GAME-13 — Geliştirici içerikleri

- Katalog tablolarından ayrı: `game_developer_contents`, `game_developer_media`, `game_update_notes`. Sağlayıcı verisi ve geliştirici içeriği birbirini ezmez; içerik yazarı (REF) tutulur, public yanıtta `contributor=DEVELOPER` ile sahiplik belirgindir, yazar kimliği dönmez.
- Doğrulama: düz metin, uzunluk sınırları, kontrol karakteri reddi, tür başına medya sınırı (10/5/3), 100 not sınırı, iyimser kilit. Metin HTML olarak işlenmez; istemci kaçışlı göstermelidir.
- **Bağlantı güvenliği (kararlar):** yalnız bağlantı saklanır; sunucu hiçbir URL'ye istek atmaz ve yönlendirme izlemez (SSRF yok). HTTPS zorunlu; kullanıcı bilgisi, port, fragment, IP, boşluk/ASCII dışı karakter ve alt alan adı taklidi reddedilir; host **tam eşleşme** ve tür bazlı allowlist'tedir (görsel: Steam mağaza varlıkları, video: YouTube/Vimeo, demo: store.steampowered.com/app/) ve **yol önekiyle** sınırlıdır (ikinci geçişte gerçek Steam sayfası ve URL'lerle doğrulanıp daraltıldı, bkz. doğrulama turu). Liste `application.properties` içinde tek yerdedir; boş liste = hiçbir bağlantı kabul edilmez. Regex yerine kontrollü etiket doğrulaması kullanıldı (ReDoS riski yok). Dosya yükleme, medya servisi ve video işleme yoktur. Host listeleri 2026-10-21'de gerçek kullanımla doğrulandı; Steam CDN adları zamanla değişebilir.
- **Moderasyon politikası:** yayın öncesi onay yok (SECURITY.md §5); ADMIN/MODERATOR gerekçeyle gizler/geri alır. Gizlenen içerik public görünümden çıkar, sahibi düzenleyemez (409 `CONTENT_HIDDEN`) ama kaldırabilir ve gerekçeyi görür. İşlem rolle birlikte denetlenir. Sahibe bildirim ve Admin Service rapor iş akışı yoktur.

## GAME-15 — Servis sınırları ve toplu okuma

- `POST /api/v1/game/games/batch`: 1–50 `gameId`, tekrarlar tek sayılır, istek sırası korunur, yalnız ACTIVE herkese açık kartlar (`GameCardResponse`, tek kart ucuyla birebir aynı sözleşme). Bulunamayan/ARCHIVED/MERGED kimlikler ayırt edilmeden `unavailable` listesinde döner. Sabit sayıda SQL ile çalışır (oyunlar + dış referanslar). Puan, kullanıcıya özel veya yönetim verisi içermez ve gelecekte zorunlu JWT getirilmeyecektir; yetkili/kişiye özel toplu okumalar ayrı, korumalı sözleşme olarak tasarlanmalıdır.
- **CSRF:** Varsayılan CSRF koruması açık bırakıldı. Yalnız bu anonim, durumsuz, salt-okunur ve **JSON içerik türlü** isteği filtreden atlayan küçük bir filtre eklendi (`CsrfFilter.skipRequest`); kapsamı (aynı yola başka yöntem, benzer yol, kardeş yazma yolları) testle doğrulandı. SonarQube `csrf(...)` özelleştirmelerini güvenlik bulgusu (S4502) olarak işaretlediği için bu yol seçildi; bu bir bastırma değildir, istisna açıkça kodda ve belgede kayıtlıdır ve gözden geçirilmelidir.
- **Açık kalanlar:** Review Service'in toplu puan özeti (REV-12) sözleşmesi yok; bu yüzden `RATING_INTEGRATION_PENDING` davranışı (Faz 3) sürüyor ve puan alanı üretilmedi. Review'a istemci, timeout ve kısmi başarısızlık davranışı yazılmadı. Library/Review'ın batch ucunu tüketmesi kendi sahibinin işidir.

## Güvenlik özeti

- **Fail-closed:** JWT yapılandırması yokken tüm token'lar reddedilir; yanlış yapılandırmada uygulama başlamaz. Başlık dışı kimlik (çerez, query, `X-User-Id`) yok sayılır. `alg=none`, yanlış imza/issuer/audience, süresi dolmuş, `exp` yok, `nbf` gelecekte, `sub` UUID değil → 401.
- Rol–yol eşlemesi zincirde, sahiplik servis katmanında, kimlik yalnız merkezi `Actor` çözümleyicisinden gelir (controller claim okumaz).
- Hatalar ProblemDetail; 401 `WWW-Authenticate: Bearer`; 403 ve 409 gövdeleri SQL/kısıt adı sızdırmaz.
- Test anahtarları yalnız test sınıfında üretilir; hiçbir dosyada gizli değer, sabit token veya bypass yoktur. `.env*` dosyalarına dokunulmadı ve yeni ENV eklenmedi.
- **NOT IMPLEMENTED:** rate limit (toplu uç hariç; bkz. doğrulama turu), hesap durumu denetimi, Gateway'de JWT/CORS, sahibe moderasyon bildirimi, servisler arası kimlik modeli.

## Migration ve belgeler

- Yeni: `V38__add_admin_game_management.sql`, `V39__create_game_developer_grants.sql`, `V40__create_game_developer_content.sql`. V31–V37 değiştirilmedi.
- **Game Service migration aralığı (V31–V40) dolmuştur.** Faz 5 (GAME-23/24/26) şema değişikliği gerektirirse numara uydurulmayacak; ekipçe yeni aralık kararı gerekir.
- `docs/api.md` (14 yeni kayıt / 21 endpoint, 2 mevcut kayıt güncellendi), `docs/database.md`, `docs/er-diagram.md` ve ana faz checklist'i güncellendi. Yeni ENV, bağımlılık veya frontend değişikliği yoktur.

## Test ve kalite

- `.\pre-push\pre-push.cmd game-service`: **139 test, 0 hata/atlanan, %98,36 JaCoCo satır kapsamı, BACKEND TEST CHECK PASSED** (ilk teslim; Faz 3: 94 test). İkinci geçişten sonraki güncel sonuç: **182 test, %98,47** (bkz. doğrulama turu).
- Yeni testler: güvenlik sınırı (anonim, geçersiz token türleri, rol matrisi, claim biçimleri, başlık/çerez yok sayma, CSRF kapsamı), fail-closed ve yanlış yapılandırma, claim dönüştürücü, URL politikası (tablo testi), yönetim akışları (oluşturma, kısmi güncelleme, sürüm çakışması, arşiv/geri alma, editör listesi, denetim değişmezliği), manuel düzeltmenin katalog aktarımı ve detay yenilemesinde korunması, geliştirici yetki matrisi, içerik yaşam döngüsü, moderasyon, batch, DB kısıtları.
- İlk teslimde testler geri alınan transaction içinde çalışıyordu ve gerçek eşzamanlılık doğrulanmamıştı. **İkinci geçişte** gerçek commit'li transaction'larla eşzamanlılık testleri eklendi (bkz. doğrulama turu).
- Sonar bulguları: ilk taramada 31 bulgu çıktı (güvenlik C: dinamik SQL değil, CSRF yapılandırması S4502; ayrıca bilişsel karmaşıklık, tekrar eden sabitler, regex, test düzeni). Hepsi kodla giderildi, test atlanmadı, kapsam hariç tutulmadı, bastırma eklenmedi. Son tarama: **SONAR SCAN PASSED, 0 bulgu, 0 hotspot**.
- Keşif performansı (100.000 kayıt) bu faz değişikliklerinden sonra yeniden ölçüldü: liste p95 39,05 ms, arama 11,65 ms, birleşik filtre 151,27 ms, eşzamanlı arama 17,55 ms; 300 ms hedefi karşılandı (yerel test, üretim garantisi değil).
- Tam `full dev`/HTTP health ve frontend testleri çalıştırılmadı (frontend değişikliği yok). Geliştirme Game DB'sine migration aktarımı veya çalışan imajın yeniden kurulumu yapılmadı.

## Faz 4 doğrulama turu (2026-10-21, ikinci geçiş)

Kapsam: Faz 4'ün "doğrulanmayanlar" ve güvenlik notlarından, Auth ve Review Service'e bağlı olmayanlar. Auth ve Review entegrasyonuna dokunulmadı, Faz 5 başlatılmadı, ENV/Gateway/başka servis değiştirilmedi. Commit veya push yapılmadı.

### Sonuç özeti

| Konu | Önceki durum | Şimdiki durum |
| --- | --- | --- |
| Gerçek eşzamanlılık | NOT VERIFIED (testler geri alınan transaction'daydı) | **DOĞRULANDI** (gerçek commit'li transaction'lar, ayrı bağlantılar, 13 test); bir sağlamlık sorunu bulundu ve giderildi |
| Rate limit | NOT IMPLEMENTED | **Toplu uç için uygulandı ve test edildi** (429 + Retry-After). Gateway üzerinden **istemci başına** ayrım henüz **yok** (Gateway onayı bekliyor) |
| Medya host listesi | Doğrulanmamış varsayılanlar | **Gerçek Steam sayfası ve URL'lerle doğrulandı, daraltıldı**; yol öneki ve uzantı kuralları eklendi |
| CSRF istisnası | Gözden geçirilecekti | **İncelendi, yalnız JSON isteklerine daraltıldı**, negatif testler eklendi; istisna korunuyor |
| Test / kapsam / Sonar | 139 test, %98,36 | **182 test, %98,47, SONAR SCAN PASSED (0 bulgu, 0 hotspot)** |

### 1. Gerçek eşzamanlılık testleri

`ConcurrencyIntegrationTest` (13 test). Test-yönetimli transaction **kullanmaz**: her istek kendi transaction'ında commit/rollback olur, iş parçacıkları ayrı bağlantı kullanır, bağlantı havuzu üretimdeki gibi 3'te bırakıldı. Veri yalnız testin kendi tek kullanımlık Testcontainers PostgreSQL 17 örneğindedir (geliştirici veya üretim DB'sine dokunulmaz, DB sıfırlanmaz); her test sonunda yalnız kendi oluşturduğu kayıtları temizler (append-only denetim tablosu bu geçici DB'de TRUNCATE ile).

Doğrulanan davranışlar:
- Aynı sürümle 8 eşzamanlı güncelleme: **tam 1 kazanan (200), 7 sürüm çakışması (409)**; reddedilenler denetim kaydı veya kısmi yazma bırakmaz.
- 6 yazar taze sürümle yeniden deneyerek güncellerken: kabul edilen her güncelleme **benzersiz sürüm** üretir, denetim sayısı kabul sayısına eşit, kayıp güncelleme yok.
- Eşzamanlı arşiv/geri alma: tekrarlı istekler tek denetim kaydı bırakır; karışık yarışta arşiv/geri alma sayıları dengeli ve son durum sayım farkıyla tutarlı.
- Eşzamanlı geliştirici yetkisi verme: hepsi 200, **tek aktif kayıt**, tek denetim; eşzamanlı geri alma: **tam 1 başarılı (204)**, 7'si 404.
- Eşzamanlı içerik: DEMO sınırı 3 → 10 istekten **tam 3 kabul**; ilk açıklamayı 6 eşzamanlı oluşturma → 1 kabul, 5 sürüm çakışması, tek satır; eşzamanlı gizleme → tek denetim kaydı.
- Eşzamanlı editör listesi: 8 çağrı sonrası **tam olarak iki listeden biri** (karışık sıra yok, benzersiz 1..3 sıralar).
- **Harici referanslar:** 8 eşzamanlı katalog sayfası aynı Steam kimliğini işlediğinde tek oyun ve tek `(STEAM, 10)` referansı; 8 ayrı transaction aynı `(provider, external_id)` satırını eklemeye çalıştığında DB kısıtıyla **tam 1 başarılı**.
- İyimser kilit: eski sürümle yazan ikinci transaction `ObjectOptimisticLockingFailureException` alır, DB'de kazananın değeri ve tek sürüm artışı kalır (kaybedenin başlık/açıklaması yazılmaz); istisna HTTP'de 409 `CONCURRENT_MODIFICATION` olur.
- Rollback: yeni terim yazıldıktan sonra medya doğrulaması başarısız olan oyun oluşturma tümüyle geri alınır (oyun, terim, denetim kalmaz); başarısız güncelleme sonrası kısmi değişiklik ve terim kalmaz; commit'li veri başka bağlantılardan görünür.

**Bulunan ve giderilen sorun:** Yönetici düzeltmesi ile katalog senkronizasyonu aynı harici oyunda çakışınca (30 turlu yarışta turların yaklaşık yarısında, değişken başlangıç sırasıyla 13–18 tur) senkronizasyon sayfası `ObjectOptimisticLockingFailureException` ile geri alınıyordu ve zamanlayıcıda yığın izi basarak tüm tiki durduruyordu. **Veri kaybı yoktu** (yönetici başlığı her turda korundu, kilit tek satır), ancak sayfa tekrar çekiliyor ve ilgili tikte detay yenileme atlanıyordu. Zamanlayıcı bu çakışmayı artık uyarı olarak kaydedip aynı tikte detay yenilemeye devam ediyor; sayfa geri alındığı için checkpoint ilerlemez ve **5 sn sonraki turda yeniden denenir**. Çakışma sıklığı yalnız aynı oyunun aynı milisaniyelerde hem düzenlenmesi hem senkronizasyonu için geçerlidir. Bu davranış tasarım gereği kabul edildi (alternatif: sayfada oyun başına kilit, 1000 öğelik sayfalarda maliyetli); gerekirse sonradan sertleştirilir.

Kapsam dışı/sınır: Testler tek JVM, tek PostgreSQL örneği içindir; çok örnekli (birden çok Game Service replikası) dağıtım davranışı doğrulanmadı. Kilitleme veritabanı satır kilidi ve `@Version` ile olduğundan replikalarda da geçerlidir ama **NOT VERIFIED**.

### 2. Rate limit

**Değerlendirme:** Mevcut durumda toplu uç anonim ve sınırsızdı; Game Service bağlantı havuzu 3 bağlantı olduğundan tek bir istemci tüm servisi (yönetim uçları dahil) bloklayabilirdi. Gateway'de rate limit yoktur. Spring Cloud Gateway MVC (5.0.3) belgelerine göre `X-Forwarded-For` yalnız `trusted-proxies` ayarlanırsa eklenir; bu proje Gateway'inde ayarlı değildir. Sonuç: Gateway üzerinden gelen **tüm istemciler Game Service'e Gateway'in IP'siyle görünür**.

**Uygulanan (yalnız Game Service):** `POST /api/v1/game/games/batch` için istemci başına jeton kovası: ani yük 100, sürekli 20 istek/sn; aşımda **429 + `Retry-After` + ProblemDetail (`RATE_LIMITED`)**. Ayarlar `application.properties` içindedir (**yeni ENV yok**). Tasarım: süreç içi, bellek sınırlı (en fazla 10.000 anahtar; dolu/boşta kovalar temizlenir, yer yoksa yeni anahtarlar ortak taşma kovasını paylaşır — sahte anahtar üretimi belleği şişirmez ve sınırı atlatmaz); IPv6 /64 önekiyle gruplanır; geçersiz gövdeli istekler de jeton harcar; yalnız bu tek istek etkilenir (diğer uçlar, korumalı uçlar ve aynı uca GET etkilenmez). Normal kullanım (örn. Library'nin sayfa başı çağrısı) bu eşiklerin çok altındadır.

**Sahte başlık koruması:** `X-Forwarded-For` varsayılan olarak **yok sayılır** (`ltz.rate-limit.trusted-proxies` bilerek boş); aksi halde sahte başlıkla sınır atlatılabilirdi (Gateway gelen başlığı temizlemiyorsa). Güvenilir proxy tanımlanırsa zincir sağdan yürünür, istemcinin soldan yazdığı değerler yok sayılır, sayısal olmayan değerler çözümlenmez (DNS yok) ve başlık tümüyle atılır (testli; bozuk bir IPv6 metninin istisna fırlatmasına yol açan bir hata test sırasında bulunup giderildi).

**Sınırlama (açık, onay gerekiyor):** Gateway istemci IP'sini iletmediği sürece Gateway'den gelen tüm internet trafiği **tek bir kovayı paylaşır**; kötü niyetli bir istemci aynı yoldan gelen meşru istemcilerin hakkını tüketebilir. Doğrudan (Gateway'siz) çağıran iç servislerin (Library vb.) kendi anahtarı vardır ve bundan etkilenmez. Tam çözüm için **ortak Gateway yapılandırması** gerekir (`spring.cloud.gateway.server.webmvc.trusted-proxies` ile istemci IP'sinin iletilmesi; ardından Game Service'te `ltz.rate-limit.trusted-proxies=<gateway CIDR>`). Bu değişiklik **uygulanmadı**; onayınızı bekliyor. Uçtan uca (Gateway → Game Service) istemci IP iletimi **NOT VERIFIED**.

Diğer herkese açık okuma uçları (liste, arama, kart, detay) ve korumalı uçlar bu turda sınırlandırılmadı: **NOT IMPLEMENTED**.

### 3. Steam CDN ve medya host doğrulaması

Yöntem (tek seferlik, elle araştırma; sunucu kodu bu adreslere istek atmaz): gerçek bir Steam mağaza sayfasından (`/app/730/`) medya host adları çıkarıldı, aday URL'lere HEAD isteği atıldı, Steamworks belgeleri tarandı.

Bulgular:
- Mağaza sayfası görselleri için `shared.akamai.steamstatic.com/store_item_assets/steam/apps/…` kullanıyor; `shared.fastly.steamstatic.com` Steamworks belgelerinde ve aynı yolda 200; eski yol `cdn.akamai.steamstatic.com/steam/apps/…` 200 ve sayfada hâlâ geçiyor.
- `shared.cloudflare.steamstatic.com` aynı yolda **301** verdi (kanonik `shared.steamstatic.com`'a) → listeden çıkarıldı. `cdn.cloudflare…`, `cdn.fastly…`, `steamcdn-a.akamaihd.net` 200 verse de sayfada kullanılmıyor → gereksiz genişlik olarak çıkarıldı. `store.akamai.steamstatic.com` yalnız site arayüzü (CSS/JS) olduğundan eklenmedi.
- `i.ytimg.com` ve `i.vimeocdn.com` küçük resim servisleri görsel kaynağı olarak gereksiz genişlikti → çıkarıldı (görseller yalnız Steam varlıkları).
- Gerçek YouTube (`/watch`, `/embed/`, nocookie `/embed/`, `youtu.be/<id>`) ve Vimeo (`vimeo.com/<id>`, `player.vimeo.com/video/<id>`) biçimleri yanıt verdi.

Yeni politika: kayıtlar `host` veya `host/yol-öneki` (örn. `shared.akamai.steamstatic.com/store_item_assets/steam/apps/`, YouTube'da yalnız `/watch` ve `/embed/`, demo için `store.steampowered.com/app/`). Ek kurallar: görsellerde `.jpg/.jpeg/.png/.webp/.gif` uzantısı; yolda nokta segmenti, çift eğik çizgi, yüzde kodlaması, noktalı virgül yok; yol öneki sınırı (`/watchfoo` kabul edilmez); büyük/küçük harf duyarlı yol. `youtube.com/redirect`, `/results`, kanal sayfaları ve Steam topluluk/kullanıcı içeriği yolları artık **reddedilir**. Yanlış yapılandırma (joker, port, kimlik bilgisi, `..`, `%`) uygulamayı başlatmaz. Testler gerçek örnek URL'ler, alt alan adı taklidi, HTTP, IP (ondalık/onaltılık/IPv6 dahil), port, kullanıcı bilgisi, ters eğik çizgi, kaçış, yol atlatma ve bozuk URL senaryolarını kapsar (ağ erişimi kullanmaz).

Kalan sınır: Vimeo'da `vimeo.com/` önekinin tamamı kabul edilir (kanal/kullanıcı sayfaları dahil; açık yönlendirici yoktur). Steam'in CDN adları zamanla değişebilir; liste doğrulama tarihi `application.properties` içinde belirtilmiştir.

### 4. CSRF istisnasının incelenmesi

- **Kapsam doğrulandı:** İstisna yalnız `POST /api/v1/game/games/batch` içindir. Aynı yola farklı yöntem, benzer/büyük harfli/sondaki eğik çizgili yollar, kardeş yazma yolları ve **korumalı ve korumasız yollar için POST/PUT/PATCH/DELETE** (24 temsilci yol × 4 yöntem) CSRF veya kimlik yüzünden 401/403 alır (JSON gövdeli olsa bile).
- **Salt-okunur:** Uç yalnız SELECT çalıştırır; 25 çağrı öncesi/sonrası oyun, referans, medya, denetim, kilit, yetki ve içerik tablolarının sayıları, toplam sürüm ve en son `updated_at` aynı kalır (testli). Method-override parametre/başlıkları (`_method`, `X-HTTP-Method-Override`) etkisizdir.
- **Sertleştirme:** İstisna artık yalnız `Content-Type: application/json` isteklerine uygulanır. Tarayıcının başka bir siteden gönderebildiği "basit" içerik türleri (form, düz metin, multipart, `application/x-json`, bozuk tür, tür yok) istisnadan yararlanamaz ve CSRF filtresinde 403 alır. Uç çerez/oturum kimlik bilgisi kullanmadığından ve herkese açık veri döndürdüğünden CSRF'nin koruyacağı bir ortam yetkisi yoktur.
- **Alternatifler değerlendirildi:** (a) `csrf.ignoringRequestMatchers` ve `requireCsrfProtectionMatcher`: SonarQube S4502 bulgusu üretir (güvenlik puanı C); (b) uçu `GET`'e çevirmek: sözleşme POST olarak belirlendi ve 50 UUID'li sorgu URL'ye uygun değil; (c) token zorunlu kılmak: batch'in herkese açık kalması kararına aykırı; (d) mevcut yaklaşım: varsayılan CSRF açık, belgelenmiş `CsrfFilter.skipRequest` ile tek isteğe dar istisna + JSON şartı. (d) korundu. SonarQube bastırılmadı; istisna gözden geçirme için açıkça belgelidir.

### 5. Test, kapsam ve kalite sonuçları

- `.\pre-push\pre-push.cmd game-service`: **182 test, 0 hata, 0 atlanan; JaCoCo satır kapsamı %98,47; BACKEND TEST CHECK PASSED** (Faz 4 ilk teslim: 139 test, %98,36).
- SonarQube (`sonar.cmd scan dev`): bu turun ilk taramasında 11 bakım bulgusu çıktı (güvenlik bulgusu yok); hepsi kodla giderildi, bastırma yok. Son tarama **SONAR SCAN PASSED, 0 bulgu, 0 hotspot**.
- Başarısız test: 0. (Geliştirme sırasında bulunan hatalar: bozuk IPv6 metninin 500'e yol açabilmesi, eşzamanlı senkronizasyon çakışması tikin durması; ikisi de giderildi ve testle korunuyor.)
- Keşif performansı yeniden ölçüldü (100.000 kayıt): liste p95 36,93 ms, arama 10,58 ms, birleşik filtre 175,54 ms, eşzamanlı arama 16,06 ms; 300 ms hedefi karşılandı (yerel test, üretim garantisi değil).

### Düzeltilenler / hâlâ açık olanlar

**Düzeltilen:** gerçek eşzamanlılık doğrulaması; senkronizasyon–yönetici çakışmasında tikin durması; toplu uçta istek sınırı (Game Service tarafı); medya host listesi doğrulaması ve daraltılması (yol öneki, uzantı, geniş hostların çıkarılması); CSRF istisnasının JSON şartıyla daraltılması ve negatif testleri; bozuk IPv6 ile 500 hatası.

**Hâlâ açık:**
1. **Gateway'den istemci IP iletimi** (ortak yapılandırma, onay bekliyor) — o zamana kadar toplu uç sınırı Gateway trafiği için tek kovadır. Uçtan uca **NOT VERIFIED**.
2. Toplu uç dışındaki herkese açık uçlarda ve korumalı uçlarda rate limit **NOT IMPLEMENTED**.
3. Çok replikalı çalışma altında eşzamanlılık **NOT VERIFIED**.
4. Auth Service ile uyum (NOT VERIFIED) ve gerçek token entegrasyon testi; Review Service puan sözleşmesi (GAME-15 / GAME-04 / GAME-05): bilinçli olarak bekletiliyor.
5. Hesap durumu denetimi, sahibe moderasyon bildirimi, Gateway JWT/CORS, servisler arası kimlik modeli: önceki gibi **NOT IMPLEMENTED**.
6. Library/Review/Frontend tüketicilerinin bilgilendirilmesi (`Game-Service-Bekleyen-Tasklar.md`).

## Açık işler ve durma sınırı

- **Auth Service entegrasyonu** ve claim sözleşmesi doğrulaması (Kerem): [belge](LTZ-HAM-GAME-04-Auth-Entegrasyonu.md). ENV anahtarı onayı bekliyor.
- Review Service toplu puan sözleşmesi (GAME-04/05/15) ve GAME-17 gerçek detay sağlayıcısı: önceki fazlardaki gibi açık.
- Library/Review/Frontend tüketicilerinin bilgilendirilmesi. Gateway'den istemci IP iletimi ve diğer uçlarda rate limit için aşağıdaki doğrulama turuna bakın.
- Faz 5 için migration aralığı kararı ve kullanıcı onayı gerekir; başlatılmadı.
