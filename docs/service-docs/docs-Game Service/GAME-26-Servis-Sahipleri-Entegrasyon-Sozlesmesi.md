# GAME-26 kapsam genişletmesi — Kimlik taşıma/ayırma entegrasyon taslağı

Tarih: 7 Kasım 2026
Koordinasyon: Hamza / Game Service
İş kuralı: **Kullanıcı tarafından onaylandı — yalnız referanssız oyunlarda taşıma/ayırma.**
Teknik durum: **ÖNERİ / PLANNED — servis sahiplerinin uygulaması ve ortak kabulü bekleniyor.**

Bu belge, ilgili servis sahiplerine iletilmek üzere hazırlanmıştır. Aşağıdaki yeni
uçlar, kapsamlar ve tablolar henüz uygulanmış değildir. Bu belgeyi hazırlamak başka
servislerde değişiklik yapma yetkisi vermez. Mevcut HTTP koruması ortak kabul
tamamlanmadan kaldırılmayacaktır.

## 0. Geçerli durum ve 5 Kasım kapanışı

**Asıl GAME-26, 5 Kasım 2026 kabul kapsamıyla kapalıdır.** `6578e3e` commit'i ve
`docs/api.md` kaydı gerçek kimlik taşıma/ayırmanın bilerek kapalı bırakıldığını,
protokolün ihtiyaç halinde ayrıca ele alınacağını açıkça belirtir. Bu belge o
kapanışı geri almaz; unutulmuş/yarım bir işi tamamlıyormuş gibi yorumlanmamalıdır.

**Bu belgenin konusu 7 Kasım'da kullanıcı tarafından istenen yeni kapsamdır:**
referanssız oyunlarda gerçek REASSIGN/SPLIT yeteneğini güvenli biçimde açma.
Kullanıcının iş kuralı onaylıdır; teknik sözleşme ve uygulama PLANNED/TASLAK'tır.
Şimdilik istenen teslim bu sözleşmenin sahiplerle paylaşılmasıdır. Mevcut uçların
409 koruması sürer; servis sahipleri önce önerilen sözleşmeyi inceleyip uzlaşmalıdır.

Auth'ta mevcut `game-references:read`, Game → Library/Review için tanımlı bir
**okuma iznidir**. Referans endpoint'lerinin veya dondurma/sonlandırma protokolünün
uygulandığını göstermez. Bölüm 7'deki yeni `game-identity:*` yetkileri öneridir;
mevcut Auth izni bunlara otomatik dönüşmez. Bu belge Auth uygulandı iddiası taşımaz.

Bu durum açıklaması `docs/api.md` GAME kaydı ve `docs/decisions/0002-oyun-dis-kimlik-duzeltme.md`
ile birlikte geçerlidir. Kod mevcut davranışın doğruluk kaynağıdır.

## 1. Amaç ve kapsam

Yanlış oyuna bağlanmış bir Steam AppID'sini başka kanonik oyuna taşımak (REASSIGN)
veya yeni bir kanonik oyuna ayırmak (SPLIT) için güvenli koordinasyon sağlanacak.
Kanonik `gameId` değerleri yeniden yazılmayacak; değişen şey dış kimliğin bağlantısıdır.
Referanslı oyunların kullanıcı verisini başka oyuna taşıma kapsam dışıdır.

GAME-26'nın manuel metadata düzeltmesi, manuel kilitleri, arşivleme ve audit kısmı
mevcuttur. Bu sözleşme, halen korumalı olan dış kimlik taşıma/ayırma ek yeteneğine aittir.

Teknik öneri: REASSIGN sırasında kaynak ve mevcut hedef, SPLIT sırasında kaynak
oyun kontrol edilip dondurulsun. Mevcut hedefte de referans varsa REASSIGN reddedilsin.
SPLIT hedef UUID'si Game tarafından operasyon başında ayrılır; yeni oyun yalnız
başarılı yerel commit ile görünür hale gelir. Bu ayrıntılar sahiplerin teknik incelemesine tabidir.

## 2. Neden yalnız referans sayısı sorgulamak yeterli değil?

Bir servis önce `0 referans` döndürüp hemen ardından aynı oyuna yeni koleksiyon,
inceleme veya içerik kaydı kabul edebilir. Game bundan habersiz kimliği taşırsa
yeni kayıt yanlış anlamdaki oyuna bağlı kalır.

Bu nedenle her katılımcı, **referans kontrolü ile yeni referans yazımını engellemeyi
aynı yerel transaction/ortak kilit düzeninde** yapmalıdır. Bir defalık COUNT,
Game API'sinden varlık doğrulama, süreç içi kilit veya sadece optimistic version
bu güvenceyi tek başına sağlamaz.

## 3. Katılımcılar ve sorumlular

- **Hamza — game-service:** kalıcı operasyon/karar kaydı, yerel referans kontrolü,
  katılımcı çağrıları, dış eşleme mutasyonu, audit, sonuç sorgusu ve kurtarma işçisi.
- **Hamza — library-service:** `library_entries` dahil koleksiyon/favori/bitirildi
  ilişkileri ve yeni ilişki oluşturan tüm akışlarda yazma engeli. `game_snapshots`
  türetilmiş önbellektir; kullanıcı referansından ayrı ele alınmalı ve commit sonrası geçersizleştirilmelidir.
- **Hamza — review-service:** `game_ratings`, `reviews`, `game_recommendations` ve
  bu ilişkileri oluşturan/yeniden etkinleştiren tüm yolların korunması.
- **Nisa — community-service:** `posts.game_id`, `forums.game_id`; rehber, easter
  egg ve deneyim gönderileri de posts kapsamındadır. Forum/gönderi oluşturma ve
  mevcut gönderiyi oyuna bağlama yollarının tamamı korunmalıdır.
- **Nisa — content-service:** `contents.game_id`; taslak dahil içerik oluşturma,
  oyun bağlama/değiştirme ve yeniden yayınlama gibi ilişki doğuran yollar korunmalıdır.
- **Alper — performance-service:** `performance_sessions.game_id`; manuel oturum
  başlatma, cihaz senkronu, yeniden oynatma/import gibi mevcut bütün kayıt yolları
  korunmalıdır. Oturumla ilişkili örnek/raporlar oturum referansıyla birlikte değerlendirilir.
- **Kerem — auth-service:** aşağıdaki dar servis-token hedef/kapsam çiftleri ve
  gerekiyorsa mevcut client kayıtlarının genişletilmesi. Auth referans katılımcısı değildir.

Mevcut kaynak taramasında Event, LFP, Hardware ve Recommendation için kalıcı
`gameId` ilişkisi bulunmadı. Şimdilik katılımcı değiller. İlgili sahipler bu sonucu
doğrulamalı; JSON payload, inbox/outbox, import kuyruğu ve ileride eklenecek yazıcılar
envantere dahil edilmelidir. Eksik veya devre dışı katılımcı `0 referans` kabul edilmez.

Admin, Notification, User, Mail, Social ve Messaging için bu sözleşmede kod değişikliği
önerilmiyor. Yeni bir kalıcı oyun ilişkisi bulunursa liste uygulama açılmadan güncellenir.

## 4. Referansın anlamı

Önerilen güvenli ilk sürümde görünürlük/durum filtresi uygulanmadan kalıcı iş ilişkileri
sayılır. Gizli, arşivlenmiş veya soft-delete edilmiş kayıt fiziksel olarak saklanıyorsa
otomatik biçimde yok kabul edilmez. İçerik metni, kullanıcı kimliği veya kişisel veri
Game'e dönmez; yalnız oyun başına toplam sayı döner.

Game'in kendi geliştirici yetkileri, geliştirici içeriği/medyası/notları, başarım
tanımları ve feedback kayıtları da yerel kontroldedir. Metadata satırları, taşınacak
dış kimliğin kendisi, operasyon/audit kayıtları ve yeniden üretilebilir cache'ler
kullanıcı iş ilişkisiyle karıştırılmaz. Türetilmiş metadata/fiyat/puan önbelleklerinin
temizlenmesi ya da güvenli yeniden üretilmesi Game commit planının parçasıdır.

Her sahip saydığı tabloları ve koruduğu yazma yollarını tesliminde açıkça listelemelidir.
Yazımı bekleyen bir iş/kuyruk varsa iş ilişkisi sayılmalı veya işçi kalıcı engeli
uygulamalıdır; eski bir mesajın sonradan engeli aşarak referans yaratmasına izin verilmez.

## 5. Önerilen iç API — beş referans katılımcısında aynı sözleşme

Library, Review, Community, Content ve Performance doğrudan servis iç ağında bu uçları
sunacak. Gateway'e public route eklenmeyecek. Mevcut kullanıcı uçları kaldırılmayacak.

### 5.1 Hazırlama ve dondurma

`POST /internal/v1/game-identity/operations/{operationId}/prepare`

```json
{
  "contractVersion": 1,
  "kind": "REASSIGN",
  "sourceGameId": "10000000-0000-4000-8000-000000000001",
  "targetGameId": "10000000-0000-4000-8000-000000000002"
}
```

- `operationId`: UUID; Game tarafından üretilir ve tüm denemelerde aynıdır.
- `kind`: yalnız REASSIGN veya SPLIT. Kaynak/hedef UUID'leri farklı olmalıdır.
- SPLIT'te targetGameId, Game'in önceden ayırdığı yeni UUID'dir; katılımcı bu UUID
  için Game'de görünür kayıt bulunmasını şart koşmaz. Çağrıyı yalnız yetkili Game yapabilir.
- Katılımcı oyun kilitlerini UUID sırasıyla alır, referansları sayar ve sonucu kalıcı
  kaydeder. Kaynak/hedef için başka açık operasyon varsa bu isteği reddeder.
- Tüm sayılar sıfırsa engeller ve PREPARED kaydı aynı transaction'da commit edilir.
  Referans varsa REJECTED sonucu kaydedilir; o katılımcıda gereksiz engel bırakılmaz.
- Aynı operationId + aynı gövde aynı sonucu verir; farklı gövde 409'dur.

Başarılı hazırlama: HTTP 200.

```json
{
  "contractVersion": 1,
  "operationId": "20000000-0000-4000-8000-000000000001",
  "participant": "community-service",
  "state": "PREPARED",
  "references": [
    { "gameId": "10000000-0000-4000-8000-000000000001", "count": 0 },
    { "gameId": "10000000-0000-4000-8000-000000000002", "count": 0 }
  ]
}
```

Yanıtta beklenen iki oyun tam birer kez bulunmalı, sayaçlar negatif olmamalıdır.
Yalnız PREPARED ve tüm sayılar 0 ise Game ilerleyebilir. Eksik/bozuk yanıt başarı değildir.
Referans varsa 409 `GAME_REFERENCES_EXIST`; toplamlar ProblemDetail ek alanında
verilebilir. Game'in katılımcı sonucu sorgulayabilmesi için REJECTED kaydı saklanır.

### 5.2 Kesin kararı uygulama ve engeli kaldırma

`POST /internal/v1/game-identity/operations/{operationId}/finalize`

```json
{
  "contractVersion": 1,
  "kind": "REASSIGN",
  "sourceGameId": "10000000-0000-4000-8000-000000000001",
  "targetGameId": "10000000-0000-4000-8000-000000000002",
  "decision": "ABORTED",
  "decisionVersion": 1
}
```

- `decision`: yalnız COMMITTED veya ABORTED; Game'in **kalıcı kesin kararıdır**.
- Karar operasyon gövdesine bağlıdır. Önceki gövdeyle uyumsuzluk veya karşıt kesin
  karar 409 verir; ilk karar sessizce değiştirilmez.
- COMMITTED yalnız o operasyon için PREPARED olan katılımcıda kabul edilir.
  Gerekli cache geçersizleştirme ve engel kaldırma yerel transaction'da tamamlanır.
- ABORTED, prepare hiç ulaşmamış olsa da kabul edilir: gövdeyle birlikte terminal
  iptal kaydı oluşturulur. Böylece ağda gecikmiş prepare sonradan engel oluşturamaz.
- Tekrar gelen aynı finalize, aynı terminal sonucu 200 döndürür. Geç gelen prepare
  terminal kaydı değiştiremez veya yeniden engel koyamaz.
- Yanıt, operationId/participant/state alanlarını taşır; state COMMITTED veya ABORTED'dır.
- Bir katılımcının finalize ACK'i gelmeden Game operasyonu tamamen teslim edilmiş
  saymaz. Yerel mutasyon COMMITTED olmuşsa ACK eksikliği nedeniyle geri alınmaz.

### 5.3 Katılımcı durum sorgusu

`GET /internal/v1/game-identity/operations/{operationId}`

HTTP 200: operasyonun gövdesi, durumu ve kaydedilmiş referans toplamları.
Gerçekten bilinmiyorsa 404. Game, timeout sonrası 404 görünce gecikmiş prepare
olamayacağını varsayamaz; iptalde her katılımcıya finalize ABORTED gönderir.

### 5.4 Yazma engeli ve HTTP hataları

Yeni ilişki oluşturan kullanıcı/cihaz/işçi yolu engeli aynı transaction'da denetler.
Engelli oyunda kullanıcı yazması: 409 `GAME_IDENTITY_OPERATION_IN_PROGRESS`;
isteğe bağlı güvenli Retry-After, fakat otomatik POST tekrarı yoktur. İşçiler kalıcı
olarak erteler veya iş akışına uygun açık hata üretir; işi sessizce düşürmez.

Ortak ProblemDetail kod önerileri:

- 400: `INVALID_GAME_IDENTITY_OPERATION` — UUID/gövde/contractVersion geçersiz.
- 401: eksik, geçersiz veya süresi geçmiş token.
- 403: yanlış subject/audience/type/scope.
- 404: `GAME_IDENTITY_OPERATION_NOT_FOUND` — yalnız durum sorgusunda bilinmeyen işlem.
- 409: `GAME_REFERENCES_EXIST`, `GAME_IDENTITY_OPERATION_CONFLICT`,
  `GAME_IDENTITY_DECISION_CONFLICT`, `GAME_IDENTITY_OPERATION_IN_PROGRESS`.
- 503: `GAME_IDENTITY_COORDINATION_UNAVAILABLE` — DB/kilit/bağımlılık geçici hatası.

Timeout, 5xx veya ulaşılamayan servis boş liste/sıfır referans biçiminde dönmez.
Kilit zaman aşımı süreleri sınırlanır; HTTP açıkken süresiz DB transaction tutulmaz.

## 6. Game koordinatörünün yükümlülükleri — Hamza

1. ADMIN, gerekçe ve beklenen mevcut Steam eşlemesi doğrulanır. Aynı dış kimliğe
   eşzamanlı ikinci operasyon engellenir. Girişte kalıcı idempotency anahtarı gerekir;
   aynı anahtar/farklı gövde reddedilir, yanıtı kaybolan çağrı yeni SPLIT oluşturmaz.
2. Operasyon, katılımcı listesi/sürümü ve ilgili gameId engelleri kalıcı kaydedilir.
   Bütün katılımcılar aynı operationId/gövdeyle prepare edilir. Tüm ACK'ler ve Game'in
   yerel referansları doğrulanmadan mutasyon yapılmaz.
3. Hata veya referans varsa önce ABORTED kararı Game DB'ye commit edilir; ardından
   **bütün katılımcılara**, çağrısı timeout olanlar dahil, finalize ABORTED gönderilir.
4. Başarı halinde aynı yerel transaction'da güncel eşleme ve engel sahipliği yeniden
   doğrulanır; dış kimlik bağlantısı, gerekiyorsa yeni oyun, cache yenileme durumu,
   değişmez audit ve COMMITTED kararı birlikte commit edilir. Yeni metadata kaynağı
   doğrulanmadan kaynağın zengin metadatası hedefe kopyalanmaz.
5. Commit sonrası finalize COMMITTED kalıcı teslim kuyruğuyla bütün katılımcılara
   gönderilir. Ağ çağrıları uzun Game DB transaction'ı içinde yapılmaz.
6. Worker çökmesi/yeniden başlatma, lease kaybı veya kaybolan yanıt halinde kalıcı
   karardan devam edilir. Eski worker'ın yeni worker kararını değiştirmesini önleyen
   DB kilidi/fencing uygulanır. Karar yokken kurtarma önce işlemi sahiplenir ve
   atomik olarak commit veya abort kararı üretir; sadece geçen süreye bakmaz.

Game'in sağlayıcı import/refresh akışları, geliştirici içerik/feedback/başarım/grant
yazmaları ve kimlik değiştiren bütün iç çağrı yolları da yerel engeli uygulamalıdır.
Şimdiki `reassign`/`split` iç metotlarına denetimsiz ikinci bir giriş bırakılamaz.

Önerilen kurtarma sorgusu, yalnız katılımcı servis token'larına açık:

`GET /internal/v1/game-identity/operations/{operationId}/decision`

Yanıt operationId, değişmez operasyon gövdesi, katılımcı listesi, `decisionVersion`
ve karar durumunu taşır. Kesin karar yoksa PENDING; kalıcı karar varsa COMMITTED
veya ABORTED. Katılımcı yalnız son ikisine göre finalize edebilir. Game'e erişilemiyorsa
veya karar PENDING/404 ise engel tutulur. Süre doldu diye kendiliğinden açılmaz.

Bu yeni Game iç ucu ile dış ADMIN operasyon/durum sözleşmesi uygulanırken `docs/api.md`
GAME bölümüne PLANNED/IMPLEMENTED ve gerçek test durumu ayrı ayrı yazılacaktır.

## 7. Auth hedef/kapsam önerisi — Kerem

İsimler öneridir; uygulamadan önce ortak biçimde kesinleştirilmelidir.

- Game → her beş katılımcı: `game-identity:prepare`, `game-identity:finalize`,
  `game-identity:read`; her token yalnız çağrılacak servisin audience değerine sahip.
- Katılımcı → Game karar sorgusu: `game-identity:decision:read`, audience `game-service`.
- Token türü `svc+jwt`; Game çağrılarında subject `svc:game-service`, karar sorgusunda
  yalnız kayıtlı katılımcı subject'leri kabul edilir. İmza/issuer/exp/nbf ve tek hedef
  servisin audience kontrolü mevcut Auth güven modeliyle yapılır.
- Kullanıcı JWT'si, sıradan ADMIN rolü veya mevcut `game-references:read` bu yeni
  mutasyon uçlarına erişim vermez. Servis token'ı kullanıcı endpoint'lerine yetki vermez.
- İlgili subject'in bu operasyonun kayıtlı katılımcısı olduğu Game'de ayrıca denetlenir.
- Yeni client secret/servis URL/ENV sözleşmesi gerekiyorsa yalnız boş şablon önerisi
  sunulur; gerçek değerler yazılmaz ve mevcut kullanıcı onayı dışında değişiklik yapılmaz.

## 8. Yerel kalıcılık ve yarış koşulları

Her katılımcının kendi DB'sinde kalıcı operasyon kaydı ve gameId bazında engel kaydı
gerekir. Operasyon gövdesi/fingerprint, state, kesin karar/sürüm, referans snapshot'ı,
UTC zamanları ve optimistic version saklanır. Aktif engelin tek sahibi olmalıdır.
Sahipler tablo adlarını kendi servis kurallarına göre seçebilir.

- Prepare ve yeni referans yazımı aynı gameId için **ortak kalıcı kilit** kullanır.
  İlişki henüz yokken de kilitlenebilir bir guard satırı veya transaction kapsamlı
  DB advisory kilidi gerekir. Sadece mevcut iş satırını kilitlemek yeterli değildir.
- Birden fazla gameId için kilit sırası deterministiktir. Aynı operationId'nin
  prepare/finalize yarışları da tekilleştirilir.
- Engelli kaydı silmek yerine terminal operasyon bilgisi korunur. Geçmiş operasyon
  kimliğiyle gelen mesajların tekrar etkili olmaması için tombstone/idempotency
  kayıtları ilk sürümde otomatik silinmez. Saklama/GC ayrıca kararlaştırılır.
- Katılımcı ve koordinatör yeniden başlatılınca engel/karar kaybolmaz. Eksik ACK'ler
  kalıcı görev, sınırlı HTTP timeout, backoff ve gözlemlenebilir hata durumuyla izlenir.
- Kullanıcı silme/anonymization akışlarıyla kilit sırası birlikte incelenir; veri
  silme görevleri bloke edilip unutulmaz. Salt referans azaltma işinin istisna olup
  olamayacağını sahip testle kanıtlamalıdır; yeni ilişki oluşturma istisnası yoktur.
- Her servis yalnız kendi DB'sini okur. Yeni migration kendi ayrılan aralığında
  açılır; eski migration değiştirilmez. Aralık doluysa ekipten yeni aralık kararı gerekir.

## 9. Servis sahiplerinin teslim listesi

Her sahip aşağıdakileri cevaplayıp kendi servisinin kodu, migration ve API kaydını
kendi sorumluluğunda uygulasın:

1. Sayılacak kalıcı ilişkiler, türetilmiş cache'ler ve bekleyen yazma işleri hangileri?
2. Referans oluşturan bütün controller/service/consumer/import/device-sync yolları hangileri?
3. Prepare ve bu yolların ortak DB kilidi nasıl sağlanıyor?
4. Yukarıdaki üç katılımcı ucu ve gerekli JWT kontrolleri uygulandı mı?
5. Tekrarlı/ters sıralı çağrılar, tombstone ve kalıcı engel davranışı kanıtlandı mı?
6. Migration numarası, `docs/api.md` kendi servis kaydı ve kalite sonucu nedir?
7. Gerçek servis ortak kabulünde kullanılacak sürüm/commit nedir? Yeni ENV gereksinimi var mı?

Başlangıçta bakılacak mevcut dosyalar (yeni dosya adı dayatması değildir):

- Game: `application/service/GameIdentityService.java`, `AdminGameIdentityService.java`,
  `infrastructure/security`, `infrastructure/persistence` ve Game yazma servisleri.
- Library: `domain/entity/LibraryEntry.java`, `GameSnapshot.java`, Library yazma servisleri.
- Review: puan/inceleme/tavsiye yazma servisleri ve repository'leri.
- Community: `domain/entity/Post.java`, `Forum.java` ve bu ilişkilerin yazma servisleri.
- Content: `domain/entity/Content.java` ve oluşturma/oyun bağlama servisleri.
- Performance: `domain/entity/PerformanceSession.java`, manuel oturum ve cihaz senkron servisleri.
- Auth: `infrastructure/security/TokenClaims.java`, servis client yapılandırması ve token kapsam testleri.

Yollar ilgili `backend/services/<servis>/src/main/java/com/ltz/<alan>/` köküne göredir.
Henüz hiçbir katılımcı IMPLEMENTED/PASS olarak işaretlenmemiştir.

## 10. Zorunlu kabul testleri

- Mevcut görünür/gizli/arşivli/soft-delete ilişki: taşıma reddedilir, veri değişmez.
- Prepare ile aynı anda yeni referans yazma: ya yazma önce commit edip prepare
  reddedilir ya prepare önce commit edip yazma engellenir; ikisi birlikte başarı veremez.
- Aynı anda iki prepare, aynı operasyonu iki kez gönderme, farklı gövdeli aynı
  operationId, ters sırada prepare/finalize, önce abort sonra gecikmiş prepare.
- Her katılımcı için ayrı ayrı timeout, DB hatası, 401/403 ve bozuk/eksik yanıt.
- Prepare sonrası koordinatör/katılımcı restart; commit sonrası ACK kaybı; worker
  lease kaybı; kalıcı finalize teslimi ve başarısız abort telafisi.
- PENDING/404/ulaşılamayan karar sorgusunda otomatik engel kaldırılmaması.
- Aynı idempotency anahtarıyla SPLIT tekrarında tek hedef oyun ve tek audit.
- Kullanıcı JWT'si, yanlış subject/audience/scope ve yetkisiz doğrudan iç çağrı reddi.
- Gerçek Auth + Game + beş katılımcı, ayrı geçici PostgreSQL'ler ile ortak kabul.
  Yalnız sıfır dönen mock sunucular bu kabulün yerine geçmez.
- Her servisin merkezi kapsam/yeni ihlal/bağımlılık kapısı ve uygun pre-push kontrolü.

## 11. Uygulama ve açılış sırası

1. Servis sahipleri envanteri ve bu teknik taslağı gözden geçirir; Auth kapsamları
   ve cache/kalıcı ilişki ayrımı kesinleşir.
2. Katılımcı uçları, kalıcı guard'lar, bütün yazıcılar ve Auth yetkileri uygulanır.
3. Game koordinatörü, kalıcı karar/kurtarma ve ADMIN idempotent operasyon akışı uygulanır.
4. Sahipler kendi API kayıtlarını/test kanıtlarını teslim eder; gerçek ortak kabul yapılır.
5. Ancak bütün katılımcılar hazırsa mevcut `FOREIGN_REFERENCE_SAFETY_UNVERIFIED`
   koruması koordinasyonla değiştirilir. Bilinmeyen/eksik katılımcıda kapalı davranış sürer.

Bu sözleşme hazırlandı diye GAME-26 kimlik mutasyonu tamamlandı veya dağıtıldı denmez.
Başka servis kodu, gerçek ENV dosyası ya da uygulanmış migration bu belgeyle değiştirilmez.

## 12. Dayanaklar

- `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Hamza.md` — GAME-26 / GAME-07.
- `docs/decisions/0002-oyun-dis-kimlik-duzeltme.md` — önceki koordinasyon taslağı.
- `docs/api.md` — mevcut GAME kimlik düzeltme koruması ve Auth servis-token sözleşmeleri.
- `AGENTS.md`, `docs/SECURITY.md`, `docs/database.md`, `docs/architecture.md`.
- 7 Kasım 2026 kullanıcı kararı: yalnız referanssız oyunlar; şimdilik sahiplerine
  sözleşme hazırlanması, diğer servis kodlarının değiştirilmemesi.
