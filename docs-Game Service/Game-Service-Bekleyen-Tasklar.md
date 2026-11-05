# Game Service — bekleyen tasklar ve diğer servislerden beklenenler

**Tarih:** 21 Ekim 2026 (Review puan entegrasyonu güncellemesi: 5 Kasım 2026) · **Sahibi:** Hamza · **Servis:** `game-service` (Faz 1–6 yerel geliştirmeleri; DEV `game_db` 2026-10-21'de sıfırlanıp yeni V31–V40 zinciriyle kuruldu; dış entegrasyonlar bekliyor)

Bu belge, Game Service'te **yapılamayan veya yarım kalan** işleri ve bunların **hangi servise/kişiye/karara** bağlı olduğunu söyler. Kendi servisini ilgilendiren bölümü (§3) oku; yapman gereken bir şey varsa orada yazar. Ayrıntılı API sözleşmesi: `docs/api.md` → `## GAME`.

> AI asistanlar için: Bu belgede yazmayan bir Game Service davranışını varsayma. Game Service'in veritabanına (`game_db`) asla erişme; yalnız HTTP API kullan. Bir uç noktanın kaydı `docs/api.md` içinde yoksa mevcut değildir.

## 1. Özet: neyi, neden yapamadık, neyi bekliyor

| Task | Durum | Neden | Bekleyen |
| --- | --- | --- | --- |
| **GAME-07 / 12 / 13** (admin yönetimi, geliştirici yetkisi, geliştirici içeriği) | **Kod hazır; Auth sözleşmesi çözüldü ve gerçek Auth üreticisiyle testli (5 Kasım 2026 kontrolü).** Game Auth'un RS256 / `at+jwt` / JWKS token'ını doğrular; `RealAuthIntegrationTest` Auth'un güncel kaynağını derleyip çalıştırır. DEV'de JWKS erişimi, anonim ve sahte token'ın 401 alması doğrulandı | **Canlı doğrulama (Auth sahibi Kerem bildirdi, 5 Kasım 2026): GAME-07/12/13 için gerçek Auth + Game jar'ıyla 22/22.** ADMIN token'ıyla oyun açma, `GAME_DEVELOPER` verme, yetkisiz/rolsüz 401/403 ve yetki geri alma doğrulandı. **Game tarafında da bağımsız canlı doğrulama yapıldı (5 Kasım 2026, DEV, gerçek Auth + Game imajları, doğrudan servis portları, Bearer): 42/42 PASS** — ADMIN ile oyun açma, `roles/grant` ile `GAME_DEVELOPER`, rol VE oyun yetkisinin ayrı denetimi (rol var ama yetki yok 403; ADMIN tek başına geliştirici ucunda 403; yetkisiz başka geliştirici 403), geliştirici açıklama/not, izinsiz medya hostu 400, moderasyon HIDE/RESTORE (PLAYER ve GAME_DEVELOPER 403, gizliyken sahibi düzenleyemez 409), GAME-14 (yazar IDOR 404, ADMIN başkasının kaydını açamaz, geçişler, yeniden açma 409), yetki geri alınınca aynı token anında 403. **2. tur (aynı gün): 40/40 PASS** — `MODERATOR` rolüyle (TOTP'li hesap, Auth `roles/grant` ile) açıklama/medya/not moderasyonu HIDE ve RESTORE, MODERATOR'ın admin ve geliştirici uçlarında 403, izinli Steam görsel hostuyla medya ekleme, oyun güncelleme (eski sürüm 409, sürümsüz 400), arşivleme/geri alma (fiziksel silme yok, idempotent, toplu kartta `unavailable`), GAME-14 RESOLVED akışı. **Kapsam dışı kaldı:** Gateway üzerinden çerez akışı (Gateway eski imajda), EXTERNAL oyunda manuel kilidin senkronizasyonda korunması ve kilit kaldırmanın başarılı yolu (katalog Steam'den dolmadı). İlk ADMIN yolu (Auth B7) için Auth kararı: şimdilik değişiklik yok; DEV'de geçici sıra: hesabı kaydet ve doğrula, PLAYER olarak giriş yap, TOTP'yi kur ve etkinleştir, `account_roles`'a ADMIN'i SQL ile ekle, yeniden giriş yap (TOTP istenir), diğer rolleri ADMIN token'ıyla `roles/grant` ucundan ver | **Game:** GAME-14 ve moderasyon uçlarının canlı denemesi · **auth-service (Kerem):** ilk ADMIN yolu (B7) üretim için hâlâ karar bekliyor |
| **GAME-04 / 05** (LTZ puanına göre filtre ve sıralama) | **Kod ve yerel testler hazır (5 Kasım 2026); canlı doğrulanmadı.** `minRating` ve `sort=RATING` Game'in kendi puan projeksiyonu (V172) üzerinde çalışır; listelerde `rating` alanı (`RATED`/`UNRATED`/`UNAVAILABLE`) ve `ratingsSyncedAt` döner | Review iç uçları hazır (REV-12). Projeksiyon, ilk hatasız tarama tamamlanana kadar boştur: o zamana dek `minRating`/`sort=RATING` **400 `RATING_INTEGRATION_PENDING`**, puan durumu `UNAVAILABLE` | **Game ortamına** `AUTH_GAME_SERVICE_CLIENT_SECRET` verilmesi (gizli değeri kullanıcı verir; compose'a geçirme eklendi) · gerçek Auth token'ı ve gerçek Review ile uçtan uca doğrulama |
| **GAME-15** (servis sınırları) | Kısmen tamam: herkese açık toplu kart ucu hazır; **puan listelerde projeksiyondan sunulur** (istek başına Review çağrısı, N+1 yok); Review tüketicisi (Auth token + RestClient, timeout, 401/403'te yeniden deneme yok) hazır | `POST /games/batch` kartı hâlâ puan alanı taşımaz (karar: puan yalnız keşif listelerinde). Diğer servisler henüz batch ucunu kullanmıyor | **tüketici servisler** (§3.3) · gerçek Auth/Review ile uçtan uca doğrulama |
| **GAME-17** (oyun detayı zenginleştirme) | **Kod ve sahte sunucu testleri hazır (5 Kasım 2026): IGDB adaptörü** (Steam AppID ile tam eşleme, açıklama/tarih/şirket/dil; belirsiz eşleme birleştirilmez). **Varsayılan KAPALI** | Kaynak kararı verildi (IGDB). **Twitch anahtarları alındı ve `.env.dev`/`.env`'e yazıldı; ENV satırları compose ve `.env.example`'a eklendi.** Gerçek IGDB sözleşmesi salt okunur canlı doğrulandı (token, Steam kaynağı, eşleme, alanlar). Konteynerde uçtan uca **NOT VERIFIED**: Game imajının derlenmesi (Maven Central 429) ve Steam kataloğunun dolması gerekir | **Kullanıcı:** imaj derlemesi sonrası doğrulama · **ticari kullanım kararı:** IGDB belgesine göre API yalnızca ticari olmayan kullanım için ücretsiz, atıf arayüzde gösterilmeli |
| **Toplu uç istek sınırı (istemci başına)** | Game Service tarafı hazır (429), ama Gateway trafiği tek kovayı paylaşıyor | Gateway istemci IP'sini Game Service'e iletmiyor | **api-gateway (ortak yapılandırma, onay)**: §3.5 |
| **GAME-04 indirim filtresi, GAME-24** (fiyat/indirim) | **Bilerek ertelendi (karar: 5 Kasım 2026, Hamza: "fiyatı şimdilik yapma").** Bölgesel fiyat portu ve testleri hazır; fiyat verisi, tablo, endpoint ve indirim filtresi yoktur, sahte fiyat üretilmez | Veri kaynağı seçilmedi (IGDB fiyat vermez) | Fiyat kaynağı kararı geldiğinde yeniden açılır |
| **GAME-23** | **Kod ve sahte sunucu testleri hazır (5 Kasım 2026):** IGDB'den dil desteği (arayüz/ses/altyazı ayrı; listelenmeyen UNKNOWN), provenance kaynağı `IGDB`, manuel kilit korunur. Yerel/admin kapsamı zaten tamamdı (V171). Aynı IGDB adaptörünü kullanır, **varsayılan KAPALI** | GAME-17 ile aynı: anahtarlar yazıldı; IGDB'nin dil alanları (tür adları `Interface`/`Audio`/`Subtitles`, yerel ayarlar `en-US` vb.) canlı doğrulandı; konteynerde uçtan uca **NOT VERIFIED** | **Kullanıcı:** imaj derlemesi sonrası doğrulama |
| **GAME-26** | **Görevin kabul ölçütü karşılanıyor:** manuel düzeltmeler (kilit), arşivleme/geri alma ve işlem kaydı (audit) var, canlı doğrulandı (arşiv, güncelleme, kilit 404 yolu). Gerçek dış kimlik taşıma/ayırma görevin kabulünün **ötesinde** bir uçtur ve bilerek 409 verir | Güvenli kimlik taşıma için Game'in Library ve Review'dan "bu `gameId` için referans var mı" sorabilmesi, taşıma sırasında yeni yazmaları durdurması (iki aşamalı koruma) ve **Auth'ta yeni servis kimliği çifti** (örn. `game-service → library-service`) gerekir; Library ve Review aynı sahibin servisleridir, Auth çifti Kerem'in değişikliğidir. Kararla ayrıca açılır | **KAPATILDI — kabulle (karar: 5 Kasım 2026, Hamza).** Auth sahibi Kerem iki çifti hazır bildirdi (`game-service → library-service` ve mevcut review çiftine ek: `game-references:read`; yeni secret/ENV yok); bu dalda henüz yok, Auth main'e girince gelir. Kimlik taşıma/ayırma **protokolü yapılmadı** ve uçlar bilerek 409 vermeye devam eder. İhtiyaç doğarsa (üretimde gerçek veri varken yanlış AppID eşlemesi) Library ve Review'a referans sayısı uçları, Game'de iki aşamalı dondurma/taşıma ve bir migration ile ayrıca açılır |
| **GAME-11** | KISMEN TAMAMLANDI: başarım tanımları ve sayfalı modül okuma hazır | GUIDES/EASTER_EGGS/EXPERIENCES sahibinin gerçek API'si yok; INTEGRATION_PENDING döner | UGC servis sahipliği kararı ve gerçek sözleşme |
| **GAME-14** | Game Service yerel iş akışı hazır; Auth'un gerçek üreticisiyle testli (PLAYER feedback, geliştirici grant) | Canlı doğrulandı (5 Kasım 2026, DEV, doğrudan servis portları): PLAYER ile oluşturma/okuma, yazar IDOR 404, ADMIN başkasının kaydını açamaz (404), yetkili geliştirici listeleme ve OPEN -> IN_PROGRESS, yeniden açma 409, yetkisiz geliştirici ve ADMIN tek başına 403; 2. turda IN_PROGRESS -> RESOLVED, OPEN -> RESOLVED atlaması 409, yanlış sürüm 409. Gateway üzerinden çerez akışı denenmedi | Gateway üzerinden doğrulama |

### 1.1 Faz 5: hazır olanlar ve entegrasyon bekleyen parçalar

**GAME-23 — Yerel/admin işlevi tamamlandı; dış veri entegrasyonu bekliyor.**
Mevcut admin oluşturma/güncelleme uçları metadata dili, bağımsız arayüz/ses/altyazı
desteği ve kaynak/doğrulama izini kaydeder. UNKNOWN bilgisi desteklenmiyor sayılmaz;
manuel kilit ve audit kullanılır. Yapılmayan: gerçek dış sağlayıcıdan dil bilgisi
alma/ayrıştırma ve dış kaynak doğrulaması. GAME-17 için izinli gerçek detay sağlayıcısı
seçilip erişim/lisans koşulları onaylanmadan bu veri üretilemez. Mevcut sağlayıcı
portu ve kayıt modeli hazırdır; gerçek sağlayıcıya uygun adaptör/eşleme ve testler
entegrasyon sırasında tamamlanacaktır. Admin'in doğrulama beyanı dış doğrulama değildir.

**GAME-24 — Yalnız fiyat sözleşmesi ve testleri hazır; gerçek fiyat özelliği bekliyor.**
GamePriceProvider; oyun/mağaza, ülke, para birimi, normal/güncel fiyat, kaynak ve
geçerlilik zamanlarını tanımlar. Yapılmayan: gerçek sağlayıcı adaptörü, veri çekme,
fiyat saklama/sunma ve GAME-04 indirim filtresi. İzinli sağlayıcı, LTZ kullanımına
uygun lisans/saklama koşulları, erişim ve gerekiyorsa ENV onayı beklenir. Fiyat
tablosu veya HTTP fiyat endpoint'i eklenmedi; uçtan uca çalışan fiyat sistemi değildir.
Bağlama sırasında sağlayıcıya uygun adaptör, güncellik/kota yönetimi ve gereken
saklama/API kararları ayrıca uygulanıp test edilmelidir. Sahte fiyat yoktur.

**GAME-26 — Manuel düzeltme/arşivleme hazır; gerçek HTTP kimlik mutasyonu bekliyor.**
Faz 4'teki archive/restore, manuel kilit ve audit yeniden kullanılır. ADMIN korumalı
POST /api/v1/game/admin/identity/reassign ve /split girişleri eklendi. Yapılmayan:
HTTP üzerinden gerçek dış kimlik taşıma/ayırma. Library/Review ve diğer gameId
tüketicilerindeki referansların anlamı ve yeni yazma yarışları güvenle korunmadan
bu işlem açılamaz. Koordinasyon sözleşmesi beklenir; yalnız referans sayısının sıfır
olması veya operatör gerekçesi yeterli değildir. Gerçek mutasyon 409
FOREIGN_REFERENCE_SAFETY_UNVERIFIED ile engellenir; reassign yalnız kesin no-op
durumunda 200 döner. Mevcut iç GameIdentityService ve başarı audit'i korunur;
güvenlik engeli entegrasyon tamamlanmadan kaldırılmamalıdır.

**Ortak Auth bağımlılığı:** GAME-23 admin yazmaları ve GAME-26 yönetim uçları,
GAME-07/12/13 ile aynı JWT doğrulama altyapısını kullanır. Gerçek Auth token'ıyla
entegrasyon henüz doğrulanmadı; Auth sözleşmesi/erişim yapılandırması ve onayları
beklenir. İzole testlerde 401/403 ve ADMIN 409 doğrulandı; bunlar canlı Auth
entegrasyonu yerine geçmez. Ayrıntı: [Auth entegrasyon notu](LTZ-HAM-GAME-04-Auth-Entegrasyonu.md).

V171 (tarihsel eski numara; şema artık aktif V38'dedir) DEV'de başarıyla uygulanmıştı ve
DEV sonradan sıfırlanıp yeni V31–V40 zinciriyle kuruldu; migration veya dağıtım onayı artık bekleyen iş
değildir. DEV kataloğu boş olduğundan dolu GAME-23 yanıtı ve geçerli DEV JWT'leri
olmadığından canlı rol senaryoları doğrulanamadı; bu senaryolar izole ortamda geçti.
Faz 5'in tamamı entegrasyonlarıyla bitmiş sayılmaz. Ayrıntılı kabul kriterleri ve
test/DEV kanıtları: [Faz 5 raporu](LTZ-HAM-GAME-05-Rapor.md).

## 2. Game Service şu an ne sunuyor (tüketiciler için)

Hepsi Gateway üzerinden `/api/v1/game/...`; **kimlik gerektirmez** (Public):

| İhtiyaç | Uç nokta |
| --- | --- |
| Birden çok oyun kartı (tercih edilen) | `POST /api/v1/game/games/batch` — gövde `{"gameIds":[…]}`, **en fazla 50**, tekrarlar tek sayılır. Yanıt: `items` (istek sırasıyla kartlar) ve `unavailable` (bulunamayan, **arşivlenmiş** veya birleştirilmiş kimlikler; nedenler ayırt edilmez) |
| Tek oyun kartı / doğrulama | `GET /api/v1/game/{gameId}/card` → 200 veya 404 |
| Ayrıntı, alt veri | `GET /api/v1/game/games/{gameId}` ve `GET /api/v1/game/{gameId}/metadata` (tür, platform, geliştirici, sistem gereksinimi vb.) |
| Liste, arama, popüler | `GET /api/v1/game/games`, `/games/search`, `/games/popular` |
| Geliştirici içeriği | `GET /api/v1/game/games/{gameId}/developer-content` |

Kurallar: Her oyunun tek `gameId`'si vardır (Steam AppID bunun dış referansıdır). Oyun başına ayrı çağrı yapma, toplu uç kullan (N+1 yasak). Toplu uç **LTZ puanı döndürmez**. Game Service henüz olay (outbox/event) yayınlamıyor; arşiv/değişiklikleri öğrenmek için `unavailable` sonucuna bak.

## 3. Servis sahibine göre yapılacaklar

### 3.1 auth-service — Kerem
**Güncel (5 Kasım 2026): sözleşme soruları cevaplandı ve bloklayıcı değildir.** Auth'un kodu ve belgeleri aşağıdaki 7 maddeyi karşılar: (1) `sub` kullanıcı UUID'si; (2) `iss=ltz-auth`, `aud=ltz-api`; (3) RS256, JWKS `…/api/v1/auth/.well-known/jwks.json`; (4) `roles` metin listesi, kodlar birebir `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`; (5) kimlik doğrulama Gateway'in çerezden Bearer'a çevirmesiyle yapılır, Game yalnız Bearer okur; (6) iptal anlık değildir: access token ömrü 60 sn + 30 sn tolerans, yani en çok ~90 sn (introspection yok, Game'in kısa ömre güvenmesi bu politikayla uyumlu); (7) `GAME_DEVELOPER` rolünü ADMIN, `POST /api/v1/auth/admin/accounts/{id}/roles/grant` ile verir (rol değişince hesabın oturumları iptal edilir, yeniden giriş gerekir; `ADMIN`/`MODERATOR` için ACTIVE hesap ve etkin TOTP şarttır). **Açık kalan tek Auth bağımlılığı:** ilk ADMIN'i oluşturan yol yoktur (Auth borcu B7). Auth sahibinin kararı (5 Kasım 2026): şimdilik değişiklik yok; DEV'de geçici sıra: kayıt ve e-posta doğrulama, PLAYER olarak giriş, TOTP kur ve etkinleştir, `account_roles`'a ADMIN'i SQL ile ekle, yeniden giriş (TOTP istenir), sonra diğer roller ADMIN token'ıyla `roles/grant` ucundan verilir. Bu sıra, canlı doğrulama için yeterlidir; üretim için kalıcı yol ayrıca karar bekler. Auth sahibi ayrıca eski şemayla kurulu yerel `auth_db`'lerin (V61–V64 checksum) sıfırlanması gerektiğini teyit etti. Aşağıdaki özgün metin tarihsel isteği anlatır.

Game Service tokenı kendisi doğrular. Aşağıdakileri yayınla veya teyit et; farklıysa yalnız Game Service yapılandırması/tek bir dönüştürücü sınıfı güncellenir:

1. `sub` = kullanıcı UUID'si mi?
2. `iss` ve `aud` değerleri (Game Service için hedef kitle adı).
3. İmza algoritması (RS256 varsayıldı) ve anahtar dağıtımı (JWKS adresi?).
4. Rol claim'i adı/biçimi (`roles` dizisi varsayıldı); rol kodları birebir **`ADMIN`, `MODERATOR`, `GAME_DEVELOPER`** mi?
5. Token yalnız `Authorization: Bearer` ile mi taşınacak? (Çereze geçilirse Game Service'te CSRF tasarımı yeniden yapılmalı.)
6. Askıya alma/yasaklamanın token'a yansıma süresi (Game Service hesap durumu sorgulamaz, yalnız kısa token ömrüne güvenir).
7. `GAME_DEVELOPER` rolünü kimin, nasıl verdiği. Game Service kullanıcının bu role sahip olduğunu doğrulayamaz; token rolü ayrıca zorunludur.

Game Service'e eklenecek ENV anahtarları için **önerim** (henüz hiçbir `.env*` değiştirilmedi, onay bekliyor): `JWT_AUDIENCE`, `JWT_JWK_SET_URI` (+ mevcut `JWT_ISSUER`). Ayrıntı: `docs-Game Service/LTZ-HAM-GAME-04-Auth-Entegrasyonu.md`.

**Bağlama günü Game Service'te ne değişir:** Yalnız yapılandırma değerleri (`ltz.security.jwt.*`) ve gerekirse tek bir claim dönüştürücü sınıf (`LtzJwtAuthenticationConverter`). Controller, servis, DTO ve veritabanı şeması aynı kalır; yeniden geliştirme gerekmez.

**Bağlama günü yapılacak test:** Mevcut testler yerel üretilmiş token'larla koşuyor ve yalnız Game Service'in kendi davranışını doğruluyor. Auth hazır olunca **gerçek Auth token'ıyla bir entegrasyon testi** yazılmalı (kimlik, rol ve `iss`/`aud` doğrulaması, askıya alınmış hesap davranışı); bu yapılmadan yönetim uçları "Auth ile doğrulandı" sayılmaz.

### 3.2 review-service — Hamza
**Güncel (5 Kasım 2026):** Review iç uçları hazır (`POST` toplu özet ve imleçli `GET` dışa aktarma; bkz. `docs-review/REVIEW_GAME_SERVICE_DEVIR.md`). Game tarafı eklendi: Auth token istemcisi + Review RestClient adaptörü (1 sn / 2 sn zaman aşımı; 401/403'te yeniden deneme yok), V172 puan projeksiyonu, periyodik tarama (varsayılan 5 dk, tek tarayıcı kiralaması) ve `minRating` / `sort=RATING`. Game Review'ın toplu `POST` ucunu çağırmaz: puan filtre/sıralama/gösterim aynı projeksiyondan gelir ki sayfa tutarlı kalsın ve Game, Review'a senkron bağımlı olmasın. Kalan: Game ortamına Auth istemci gizli anahtarı, gerçek Auth token'ı ve gerçek Review ile uçtan uca doğrulama, sorgu planı ölçümü. Sözleşme ve eksik değer kuralları: `docs/api.md` → GAME. Review'ın oyun doğrulaması için Game Service'in `card`/`batch` ucunu kullan.

**Review sahibine bildirim (Review belgeleri Review sahibi tarafından güncellenir; Game çalışmasında bu belgelere dokunulmadı):**
- Review belgelerindeki "Game tüketicisi yok / NOT IMPLEMENTED / bekliyor" ve "Game projeksiyonu NOT IMPLEMENTED" ifadeleri eskidir: Game tüketicisi ve projeksiyonu Game'de uygulandı (5 Kasım 2026). Etkilenen yerler: `docs/api.md` REVIEW bölümündeki ortak güvenlik notu, REV-12 kullanıcı kaydı ve iki "Game → Review" kaydı; `docs-review/REVIEW_SERVICE_API.md`, `REVIEW_SERVICE_PROGRESS.md`, `REVIEW_GAME_SERVICE_DEVIR.md` (durum tablosu), `REVIEW_AUTH_INTEGRATION_REPORT.md`, `request/game-review-integration.md` (§5, §6, §12) ve `request/auth-review-integration.md` (satır 9).
- Bildirilecek gerçek durum: Game, Review'ın toplu `POST /internal/v1/games/rating-summaries` ucunu **çağırmaz**; puan filtresi, sıralaması ve gösterimi tek kaynaktan, imleçli `GET` dışa aktarmadan beslenir. DEV'de Auth token ve ilk hatasız tarama doğrulandı (Review'da puan yokken); gerçek puanlı veriyle uçtan uca **NOT VERIFIED**.
- Auth çifti iki kapsamlı oldu (`game-references:read rating-summaries:read`, GAME-26 için). Review yalnız `rating-summaries:read` ister. Kerem'in istediği iki kapsamlı token denemesi tek seferlik yapıldı (Review `InternalRatingSummaryTest` 20/20; iki kapsamlı token kabul, yalnız `game-references:read` taşıyan token 403) ve **kalıcı test bırakılmadı**; Review sahibi isterse bunu kalıcı test olarak ekleyebilir.
- GAME-26 kabulle kapatıldı: Review'da "referans var mı" ucu şimdilik gerekmez.

### 3.3 Oyun kimliği kullanan servisler — library (Hamza), lfp (Umut), event (Nisa), community (Nisa), performance ve recommendation (Alper)

- `gameId`'yi doğrularken ve adı/kapağı gösterirken **`POST /games/batch`** kullan; bir sayfa için tek çağrı. Çağrı başına en çok 50 kimlik.
- `unavailable` içindeki kimlik **hata değildir**: oyun arşivlenmiş veya kaldırılmış olabilir. Kendi kaydını silme; elindeki başlık/kapak anlık görüntüsünü göster (library: LIB-17; lfp/performance: `*_title_snapshot`).
- Timeout/erişilemezlik durumunda kısmi veriyle devam et; Game Service'e senkron bağımlı kalıp kendi sayfanı kırma.
- **429 (istek sınırı):** `POST /games/batch` istemci başına sınırlıdır (ani yük 100, sürekli 20 istek/sn). 429 alırsan `Retry-After` kadar bekle; sıkı döngüyle yeniden deneme. Normal sayfa başı çağrı bu sınırın çok altındadır.
- **Alper (recommendation):** sistem gereksinimi ve tür/platform için yalnız tek oyunluk `GET /{gameId}/metadata` var; **toplu metadata ucu yok**. Toplu ihtiyacın varsa Hamza'ya söyle, uç tasarlanacak (N+1 yapma).
- **Library (güncel, 5 Kasım 2026):** Game çalışmasından Library için yapılacak yeni iş çıkmadı; Library Game'in toplu ucunu kullanmaya devam eder. GAME-26 kabulle kapatıldığı için referans sayısı ucu şimdilik gerekmez; kimlik taşıma protokolü ileride açılırsa Library'de `game-references:read` kapsamıyla yalnız sayı döndüren bir iç uç istenecektir.

### 3.4 Ortak servisler — admin-service, notification-service

- Game Service içerik moderasyonunu kendi `ADMIN`/`MODERATOR` uçlarıyla yapıyor ve kendi denetim kaydını tutuyor. **Admin Service iş akışı ve sahibe bildirim yok.** Admin Service'in Game Service'i çağırması gerekecekse servisler arası kimlik modeli önce kararlaştırılmalı (SECURITY.md'de "karar bekliyor"); şimdilik böyle bir yol yoktur.
- Notification: "içeriğin gizlendi" bildirimi için olay/sözleşme tanımlı değil.

### 3.5 api-gateway
Game Service'e `Authorization` başlığının değiştirilmeden iletildiğini doğrula; istemciden gelen `X-User-Id`/`X-Role` gibi iç kimlik başlıklarının temizlenmesi (architecture.md §4 önerisi) ve CORS henüz yapılmadı. Game Service bu başlıklara **güvenmez**.

**Rate limit için ek istek (ortak yapılandırma, onay gerekli):** Spring Cloud Gateway MVC, `X-Forwarded-For`'u yalnız `spring.cloud.gateway.server.webmvc.trusted-proxies` ayarlıysa ekler; şu an ayarlı değil. Bu yüzden internetten gelen tüm istemciler Game Service'e Gateway'in IP'siyle görünür ve toplu uçtaki istek sınırı bu trafik için tek kovadır. Gateway bu ayarla istemci IP'sini iletirse, doğrulandıktan sonra Game Service'te `ltz.rate-limit.trusted-proxies=<gateway CIDR>` tanımlanacak. Hamza onaylamadan Gateway'e dokunma; ayrıca Gateway'in gelen `X-Forwarded-For`'u temizlediği doğrulanmalı (aksi halde sahte başlıkla sınır atlatılır).

### 3.6 Frontend
Yapılmadı: yönetim paneli, geliştirici alanı, moderasyon ekranı. Geliştirici içeriği `contributor=DEVELOPER` ile gelir (yayın öncesi onaysız kullanıcı içeriği); metinler **düz metindir, kaçışlı göstermelisin** (`dangerouslySetInnerHTML` yok). Medya bağlantıları yalnız izinli hostlardan gelir.

## 4. Ekipçe verilmesi gereken kararlar

**Güncel kararlar (5 Kasım 2026, Hamza):** (1) GAME-17/23 detay ve dil kaynağı **IGDB** olarak seçildi; Steam katalog kaynağı olarak kalır. Adaptör yazıldı ve varsayılan olarak kapalıdır; açmak için `IGDB_CLIENT_ID` ve `IGDB_CLIENT_SECRET` (Twitch geliştirici hesabından ücretsiz) ortama verilmelidir. **Anahtarlar alındı ve yazıldı; `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` ve `GAME_CATALOG_SYNC_ENABLED` için `.env.example` ve `docker-compose.yml` satırları eklendi.** Ticari kullanım için IGDB/Twitch koşulu teyit edilmeli ve IGDB atfı arayüzde gösterilmelidir. (2) Fiyat/indirim (GAME-24 ve GAME-04 indirim filtresi) **şimdilik yapılmayacak**; veri kaynağı seçilince yeniden açılır. Aşağıdaki özgün maddeler tarihsel bağlamı anlatır.

Son kalite ve yarış düzeltmeleri: [Faz 5 öncesi rapor](LTZ-HAM-GAME-Faz5-Oncesi-Kalite-Raporu.md).
Yetki iptali/yazma ve rate limit anahtar kabulü yarışları bu turda düzeltildi;
Auth, Review ve Gateway entegrasyonlarının bekleme durumu değişmedi.

1. **GAME-17 kaynağı:** Öneri — Steam indeksini koru, **IGDB**'yi yalnız detay zenginleştirme için değerlendir. Önce projenin ticari kullanım durumu, lisans/atıf koşulları ve Steam AppID eşleme yöntemi netleşmeli; sonra küçük bir pilot. (Kaynak API anahtarı yeni ENV gerektirir → onay.) Ayrıntı: `docs-Game Service/LTZ-HAM-GAME-02-Saglayici-Arastirmasi.md`.
2. **Fiyat/indirim (GAME-24):** İzinli veri kaynağı var mı? Yoksa görev ertelenir; fiyat bilgisi uydurulmaz.
3. **Game Service migration aralığı:** Aktif zincir kesintisiz V31–V40 (10 migration) ve aralık doldu. Ek V171–V190 aralığı 2026-10-21 tarihinde onaylanmış, sonra kaldırılmıştır (`docs/decisions/migrations.md`); eski zincirde kullanılan V171–V173 tarihsel kayıttır. Ek V171–V190 aralığı ve V174–V190 rezervasyonu sonradan kaldırıldı; Game Service yeni migration gerektirirse numara aralığı ekipçe belirlenmelidir. Tahsis ortak branch'e aktarılmalıdır. **Güncel (5 Kasım 2026):** `docs/decisions/migrations.md` içindeki 2026-10-24 tahsisi (V171–V180) geçerlidir; V171 hesap silme, V172 LTZ puan projeksiyonu için kullanıldı, V173–V180 boştur. Yukarıdaki "aralık doldu / V171–V190 kaldırıldı" anlatımı bu tahsisten önceki durumdur.
4. **Servisler arası kimlik modeli** (servis JWT'si mi, başka mı?) — korumalı toplu/servisler arası uçlar bunu bekliyor.

## 5. Yapma listesi

- `game_db`'ye veya `review_db`/`library_db` gibi başka servis DB'lerine erişme.
- Game Service yönetim uçlarını (`/admin`, `/developer`, `/moderation`) token olmadan veya sahte başlıkla çağırmaya çalışma; şu an hepsi 401.
- Toplu karttan (`POST /games/batch`) LTZ puanı bekleme; puan alanı yok. Puan yalnız `GET /games` ve `/games/search` yanıtında `rating` ile gelir; `UNAVAILABLE` durumu 0 puan değildir.
- Oyun başına döngüyle `GET /card` çağırma.

Faz 5 güncel kapsam ve kanıtlar: [Faz 5 raporu](LTZ-HAM-GAME-05-Rapor.md).

## 6. Faz 6 entegrasyonları ve DEV durumu

Faz 6 geliştirme onayı alındı. GAME-11'de Game Service yalnız başarım tanımlarını
yönetir; kişisel ilerleme yoktur. GUIDES/EASTER_EGGS/EXPERIENCES için veri sahipliği
kararı ve veri sahibinin görünürlük/moderasyon/sayfalama/erişim sözleşmesi gerekir.
Eski docx rehberleri Content'e bağlasa da güncel Nisa görevleri Content'i haber/duyuruyla
sınırlar; Content veya Community'ye yeni sahiplik atanmadı. Bu modüller için tablo,
gerçek adaptör veya sahte veri oluşturulmadı. INTEGRATION_PENDING boş/başarılı veri değildir.

GAME-14'te GENERAL/BUG_REPORT/EARLY_ACCESS özel iş akışı Game Service'tedir;
Review incelemesi veya kullanıcılar arası mesajlaşma değildir. Yazar yalnız kendi
kaydını, aktif oyun grant'i ve GAME_DEVELOPER rolü olan geliştirici yalnız ilgili
oyunun kayıtlarını okur. ADMIN/MODERATOR tek başına özel içeriği açamaz.
OPEN -> IN_PROGRESS -> RESOLVED akışı hazırdır; yeniden açma/yorum/dosya/bildirim yoktur.
Bildirim bu fazın kapsamı değildir; bildirim işlevi tamamlandı sayılmamalıdır.

Gerçek Auth token'ıyla uçtan uca erişim doğrulaması bekler. Rate limit kullanıcıya
ve IP'ye uygulanır; süreç içidir, Gateway'de gerçek IP/dağıtık koordinasyon eksikleri
önceki fazlardan devam eder. V172/V173 DEV'e uygulandı; yalnız Game Service imajı güncellendi;
dağıtım için ayrı kullanıcı onayı alındı. **Güncel (2026-10-21):** `ltz-dev` ortamı
sıfırlanıp yeni V31–V40 zinciriyle yeniden kuruldu (V171–V173 artık yok; ayrıntı:
[yedek/restore raporu §10](LTZ-HAM-GAME-DEV-Yedek-Restore-Hazirlik.md)). Gerçek Auth entegrasyonu bekler. Ayrıntılar: [Faz 6 raporu](LTZ-HAM-GAME-06-Rapor.md).
