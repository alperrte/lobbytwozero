# Game Service — bekleyen tasklar ve diğer servislerden beklenenler

**Tarih:** 21 Ekim 2026 · **Sahibi:** Hamza · **Servis:** `game-service` (Faz 1–6 yerel geliştirmeleri; V171–V173 DEV'de uygulandı; dış entegrasyonlar bekliyor)

Bu belge, Game Service'te **yapılamayan veya yarım kalan** işleri ve bunların **hangi servise/kişiye/karara** bağlı olduğunu söyler. Kendi servisini ilgilendiren bölümü (§3) oku; yapman gereken bir şey varsa orada yazar. Ayrıntılı API sözleşmesi: `docs/api.md` → `## GAME`.

> AI asistanlar için: Bu belgede yazmayan bir Game Service davranışını varsayma. Game Service'in veritabanına (`game_db`) asla erişme; yalnız HTTP API kullan. Bir uç noktanın kaydı `docs/api.md` içinde yoksa mevcut değildir.

## 1. Özet: neyi, neden yapamadık, neyi bekliyor

| Task | Durum | Neden | Bekleyen |
| --- | --- | --- | --- |
| **GAME-07 / 12 / 13** (admin yönetimi, geliştirici yetkisi, geliştirici içeriği) | Kod ve testler hazır; **Auth ile doğrulanmadı** | Auth Service henüz JWT üretmiyor. Geçici, fail-closed bir doğrulama kuruldu: yapılandırma yokken **tüm yönetim uçları 401 döner**, yani şu an gerçek kullanımda çalışmaz | **auth-service (Kerem)**: JWT sözleşmesi (§3.1) · ENV onayı |
| **GAME-04 / 05** (LTZ puanına göre filtre ve sıralama) | Puan dışındaki filtre/sıralamalar çalışıyor | LTZ puanı Review Service'e ait ve toplu puan API'si yok. `minRating` ve `sort=RATING` şimdilik **400 `RATING_INTEGRATION_PENDING`** döner | **review-service**: toplu puan özeti (REV-12) |
| **GAME-15** (servis sınırları) | Yarım: herkese açık toplu kart ucu hazır | Review'dan puan özeti alınamıyor (sözleşme yok); diğer servisler henüz batch ucunu kullanmıyor | **review-service** (REV-12) · **tüketici servisler** (§3.3) |
| **GAME-17** (oyun detayı zenginleştirme) | Altyapı hazır, **gerçek sağlayıcı bağlı değil** | Steam resmî API'leri kapsamlı detay vermiyor; kaynak (öneri: IGDB) ve lisans/atıf koşulları kararlaştırılmadı | **Ekip kararı** (§4) |
| **Toplu uç istek sınırı (istemci başına)** | Game Service tarafı hazır (429), ama Gateway trafiği tek kovayı paylaşıyor | Gateway istemci IP'sini Game Service'e iletmiyor | **api-gateway (ortak yapılandırma, onay)**: §3.5 |
| **GAME-04 indirim filtresi, GAME-24** (fiyat/indirim) | Bölgesel fiyat portu ve testleri hazır; gerçek veri bekliyor | İzinli sağlayıcı/erişim ve kullanım-saklama koşulları kesinleşmedi | Sağlayıcı ve ENV onayı (§4) |
| **GAME-23** | Yerel/admin kapsamı tamamlandı; V171 | Gerçek dış dil/detail verisi GAME-17 sağlayıcısını bekliyor | Gerçek detay sağlayıcısı |
| **GAME-26** | Arşivleme/kilit/audit korunuyor; ADMIN girişleri hazır | Gerçek eşleme/ayırma dış referans güvenliği doğrulanana kadar 409 ile engellenir | Library/Review referans koordinasyon sözleşmesi |
| **GAME-11** | KISMEN TAMAMLANDI: başarım tanımları ve sayfalı modül okuma hazır | GUIDES/EASTER_EGGS/EXPERIENCES sahibinin gerçek API'si yok; INTEGRATION_PENDING döner | UGC servis sahipliği kararı ve gerçek sözleşme |
| **GAME-14** | Game Service yerel iş akışı hazır; gerçek Auth ile doğrulanmadı | Özel geri bildirim/rol/oyun grant'i test JWT'leriyle doğrulandı; gerçek JWT yok | Gerçek Auth entegrasyonu |

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

V171 DEV'de başarıyla uygulandı; migration veya dağıtım onayı artık bekleyen iş
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

### 3.1 auth-service — Kerem (bloklayıcı)
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
`POST`/toplu bir uç sağla: çoklu `gameId` için ortalama LTZ puanı ve puan sayısı (REV-12), puanı olmayan oyunlar için açık boş değer. Gelince Game Service'e bir RestClient adaptörü eklenecek; o zamana kadar puan filtresi/sıralaması kapalıdır. Review'ın oyun doğrulaması için Game Service'in `card`/`batch` ucunu kullan.

### 3.3 Oyun kimliği kullanan servisler — library (Hamza), lfp (Umut), event (Nisa), community (Nisa), performance ve recommendation (Alper)

- `gameId`'yi doğrularken ve adı/kapağı gösterirken **`POST /games/batch`** kullan; bir sayfa için tek çağrı. Çağrı başına en çok 50 kimlik.
- `unavailable` içindeki kimlik **hata değildir**: oyun arşivlenmiş veya kaldırılmış olabilir. Kendi kaydını silme; elindeki başlık/kapak anlık görüntüsünü göster (library: LIB-17; lfp/performance: `*_title_snapshot`).
- Timeout/erişilemezlik durumunda kısmi veriyle devam et; Game Service'e senkron bağımlı kalıp kendi sayfanı kırma.
- **429 (istek sınırı):** `POST /games/batch` istemci başına sınırlıdır (ani yük 100, sürekli 20 istek/sn). 429 alırsan `Retry-After` kadar bekle; sıkı döngüyle yeniden deneme. Normal sayfa başı çağrı bu sınırın çok altındadır.
- **Alper (recommendation):** sistem gereksinimi ve tür/platform için yalnız tek oyunluk `GET /{gameId}/metadata` var; **toplu metadata ucu yok**. Toplu ihtiyacın varsa Hamza'ya söyle, uç tasarlanacak (N+1 yapma).

### 3.4 Ortak servisler — admin-service, notification-service

- Game Service içerik moderasyonunu kendi `ADMIN`/`MODERATOR` uçlarıyla yapıyor ve kendi denetim kaydını tutuyor. **Admin Service iş akışı ve sahibe bildirim yok.** Admin Service'in Game Service'i çağırması gerekecekse servisler arası kimlik modeli önce kararlaştırılmalı (SECURITY.md'de "karar bekliyor"); şimdilik böyle bir yol yoktur.
- Notification: "içeriğin gizlendi" bildirimi için olay/sözleşme tanımlı değil.

### 3.5 api-gateway
Game Service'e `Authorization` başlığının değiştirilmeden iletildiğini doğrula; istemciden gelen `X-User-Id`/`X-Role` gibi iç kimlik başlıklarının temizlenmesi (architecture.md §4 önerisi) ve CORS henüz yapılmadı. Game Service bu başlıklara **güvenmez**.

**Rate limit için ek istek (ortak yapılandırma, onay gerekli):** Spring Cloud Gateway MVC, `X-Forwarded-For`'u yalnız `spring.cloud.gateway.server.webmvc.trusted-proxies` ayarlıysa ekler; şu an ayarlı değil. Bu yüzden internetten gelen tüm istemciler Game Service'e Gateway'in IP'siyle görünür ve toplu uçtaki istek sınırı bu trafik için tek kovadır. Gateway bu ayarla istemci IP'sini iletirse, doğrulandıktan sonra Game Service'te `ltz.rate-limit.trusted-proxies=<gateway CIDR>` tanımlanacak. Hamza onaylamadan Gateway'e dokunma; ayrıca Gateway'in gelen `X-Forwarded-For`'u temizlediği doğrulanmalı (aksi halde sahte başlıkla sınır atlatılır).

### 3.6 Frontend
Yapılmadı: yönetim paneli, geliştirici alanı, moderasyon ekranı. Geliştirici içeriği `contributor=DEVELOPER` ile gelir (yayın öncesi onaysız kullanıcı içeriği); metinler **düz metindir, kaçışlı göstermelisin** (`dangerouslySetInnerHTML` yok). Medya bağlantıları yalnız izinli hostlardan gelir.

## 4. Ekipçe verilmesi gereken kararlar

Son kalite ve yarış düzeltmeleri: [Faz 5 öncesi rapor](LTZ-HAM-GAME-Faz5-Oncesi-Kalite-Raporu.md).
Yetki iptali/yazma ve rate limit anahtar kabulü yarışları bu turda düzeltildi;
Auth, Review ve Gateway entegrasyonlarının bekleme durumu değişmedi.

1. **GAME-17 kaynağı:** Öneri — Steam indeksini koru, **IGDB**'yi yalnız detay zenginleştirme için değerlendir. Önce projenin ticari kullanım durumu, lisans/atıf koşulları ve Steam AppID eşleme yöntemi netleşmeli; sonra küçük bir pilot. (Kaynak API anahtarı yeni ENV gerektirir → onay.) Ayrıntı: `docs-Game Service/LTZ-HAM-GAME-02-Saglayici-Arastirmasi.md`.
2. **Fiyat/indirim (GAME-24):** İzinli veri kaynağı var mı? Yoksa görev ertelenir; fiyat bilgisi uydurulmaz.
3. **Game Service migration aralığı:** V31–V40 doldu. Ek V171–V190 aralığı 2026-10-21 tarihinde onaylandı ve `docs/decisions/migrations.md` içine işlendi. Tahsis ortak branch'e aktarılmalıdır. Faz 5 onayı alındı; V171 kullanıldı. V172–V190 kullanılmadı.
4. **Servisler arası kimlik modeli** (servis JWT'si mi, başka mı?) — korumalı toplu/servisler arası uçlar bunu bekliyor.

## 5. Yapma listesi

- `game_db`'ye veya `review_db`/`library_db` gibi başka servis DB'lerine erişme.
- Game Service yönetim uçlarını (`/admin`, `/developer`, `/moderation`) token olmadan veya sahte başlıkla çağırmaya çalışma; şu an hepsi 401.
- Toplu karttan LTZ puanı bekleme; puan alanı yok ve uydurulmayacak.
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
dağıtım için ayrı kullanıcı onayı alındı. Gerçek Auth entegrasyonu bekler. Ayrıntılar: [Faz 6 raporu](LTZ-HAM-GAME-06-Rapor.md).
