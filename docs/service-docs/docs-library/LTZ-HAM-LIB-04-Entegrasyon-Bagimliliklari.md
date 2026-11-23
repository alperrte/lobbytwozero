# LTZ-HAM-LIB-04 — Entegrasyon bağımlılıkları

Tarih: 30 Ekim 2026. Sorumlu: Hamza.
Durum: Library altyapısı ve testleri hazır; gerçek servis bağımlılıkları PENDING.

## Doğrulanan mevcut durum

- User Service `src/main/java` altında yalnız UserServiceApplication bulunuyor.
  Görünürlük tercihi veya erişim kararı sunan controller/port/istemci sözleşmesi yok.
  docs/api.md USER bölümünde kayıt yok.
- Recommendation Service `src/main/java` altında yalnız RecommendationServiceApplication
  bulunuyor. Library verisini tüketen adaptör/sözleşme yok; API kataloğu boş.
- Auth ServiceClientConfig.ALLOWED_TARGET_SCOPES yalnız events:ingest ve blocks:check
  kapsamlarını kabul ediyor. Library okuma veya User görünürlük kararı kapsamı tanımlı değil.
- Auth application.properties servis istemci kayıtlarında User/Recommendation → Library
  ve Library → User hedef çiftleri bulunmuyor. Gerçek ENV dosyaları okunmadı.
- Library'deki mevcut servis decoder'ı Auth D4 hesap silme sözleşmesine özeldir.
  Kullanıcı token'ları kişisel uçlarda, D4 token'ı hesap silme ucunda kullanılmaktadır.
  D4 yetkisi koleksiyon okuma yetkisi olarak kullanılamaz.
- User gereksiniminde bütün profilin gizlenmesi yoktur; USER-06, oyun geçmişi gibi
  alanların görünürlüğünü yönetir. LIB-18'deki gizli profil senaryosu koleksiyon/oyun
  geçmişi görünürlüğü olarak ele alınmalıdır; profilin tamamı için yeni kural eklenmemelidir.

## Tasklara etkisi

- LIB-10: User profil akışına gerçek, yetkili koleksiyon sunumu için çağıran servis
  kimliği ve görüntüleyen kullanıcı bağlamının nasıl doğrulanacağı belirlenmelidir.
- LIB-11: User'ın gerçek görünürlük tercihi mevcut API'den okunamıyor. PRIVATE/PUBLIC/
  FRIENDS tercihlerini Library DB'sine taşımak veya varsayılanı PUBLIC saymak uygun değildir.
  FRIENDS varsa güvenilir arkadaşlık/engelleme kararının sahibi ve API'si de belirlenmelidir.
- LIB-14: Recommendation istemcisinin Library için geçerli token edinme yetkisi yok.
  Servis kimliği tek başına herhangi bir kullanıcının özel koleksiyonunu okuma izni sayılmaz.
  Recommendation kullanım izninin profil görünürlüğüyle aynı mı, ayrı mı olduğu netleştirilmelidir.
- LIB-18: Var olan sahiplik/tekillik/eşzamanlılık testleri korunabilir. Gerçek gizlilik
  entegrasyonu için yukarıdaki sözleşmeler ve hata/ret testleri gerekir; sahte adaptör
  testleri gerçek servis entegrasyonu tamamlandı anlamına gelmez.

## Hazırlanan Library tarafı

Kullanıcının onayıyla Library tarafı geliştirildi. Yeni Library uçları docs/api.md
LIBRARY bölümünde kayıtlıdır; karşı servislerin aşağıdaki önerileri uyguladığı varsayılmaz.

1. Library application katmanında açık izin/ret/karar alınamıyor durumlarını ayıran
   görünürlük portu ve erişim servisi hazırlandı. Üretim adaptörü sözleşme gelene kadar
   başkasının verisini reddeder. Kişisel owner uçları mevcut davranışını sürdürür.
2. Açık izin alınmadan koleksiyon, sayaç, Game metadata çağrısı veya diğer kullanıcıya
   ait varlık bilgisi döndürülmez. Hata/timeout/eksik karar eski bir izinle geçiştirilmez.
3. Profil için koleksiyon sayfası/sayaç; Recommendation için yalnız gerekli gameId ve
   favori/bitirildi alanları sunan DTO'lar eklendi. Kalıcı veri sahipliği Library'de kalır.
4. Yeni servis okuma yollarının typ/sub/aud/scope doğrulama sözleşmesi, mevcut D4
   decoder'ından ayrı ele alınmalıdır. Scope adları ve token alma yetkileri ilgili
   sahiplerle kesinleşmeden çalışan servis entegrasyonu ilan edilmez.
5. İzin/ret/ulaşılamama, owner izolasyonu, özel koleksiyonun sayaç dahil sızmaması ve
   kullanıcı token'ı/servis token'ı ayrımı otomatik testlerle sınandı. İzin üreten adaptör
   yalnız test kapsamındadır; üretimde açık erişim seçeneği/ENV anahtarı yoktur.

## Önerilen karşı servis sözleşmeleri — uygulanmadı

### User erişim kararı

Kerem tarafından değerlendirilmek üzere öneri:

`POST /internal/v1/users/{ownerId}/library-access-check`

İç servis kimliği: Library → User; RS256 svc+jwt, sub=svc:library-service,
aud=user-service, scope=privacy:library:check, en fazla 60 saniye, issuer/kid/jti/zamanlar.
Bu scope bugün Auth allowlist'inde yoktur. Yol yalnız User servisinde uygulanabilir.

Önerilen gövde: doğrulanmış `viewerId` (UUID) ve `purpose` (PROFILE veya RECOMMENDATION).
Viewer değeri dış kullanıcı header/query'sinden alınmaz; Library doğrulanmış JWT/delegasyon
bağlamından üretir. RECOMMENDATION için viewer owner'dır; bu eşitlik izin sayılmaz.
Önerilen 200 yanıtı ownerId, viewerId, purpose ve decision=ALLOW|DENY içerir;
yanıt kimlikleri/amaç istekle tam eşleşmelidir. Kullanıcı/tercih yoksa veya karar
belirsizse ALLOW verilmez. Hata/timeout/3xx/bozuk yanıt Library'de UNAVAILABLE olmalıdır.
Bu HTTP adaptörü henüz yazılmadı; çalışan olmayan bir API'ye çağrı yapılmaz.

User, PROFILE için library_visibility ve varsa arkadaşlık/engelleme kuralını kendi
veri sahipliği altında değerlendirir. PRIVATE non-owner erişimi reddeder. FRIENDS için
güvenilir Social kararı yoksa izin verilemez. RECOMMENDATION için profil görünürlüğünden
ayrı amaç/kullanım izni dayanağı kararlaştırılmalıdır; PUBLIC tek başına yeterli değildir.
Tercihlerin varsayılanı, izin geri alma ve kullanıcı yokluğu User'ın kararıdır.
Library karar veya tercihi kalıcı/cache olarak saklamaz.

### Auth hedef/kapsam ve delegasyon

Kerem tarafından uygulanması gereken hedef çiftleri önerisi:

- user-service → library-service: library:profile:read.
- recommendation-service → library-service: library:recommendation:read.
- library-service → user-service: privacy:library:check.

Library'nin iç okuma sınırı `typ=svc+jwt`, tek `aud=library-service`, `iss=ltz-auth`,
RS256/JWKS, dolu kid/jti, zorunlu iat/nbf/exp, en fazla 60 saniye ömür ve mevcut
30 saniye saat toleransını uygular. D4 account:erase kapsamı okuma yetkisi değildir.

User profil okumasında `sub=svc:user-service`, scope=library:profile:read,
`target_user_id`=yoldaki kanonik UUID ve kanonik UUID `viewer_id` claim'i zorunludur.
Auth bu görüntüleyen kimliğini gerçekten doğrulanmış kullanıcı bağlamına bağlamalıdır;
servisin doğrulanmamış body/header alanından istediği viewer_id'yi imzalaması kabul edilmez.
Hedef kullanıcı ve amaç sınırı token edinme sözleşmesinde tanımlanmalıdır. Mevcut
POST /internal/v1/service-tokens bu ek claim/delegasyon işlemini sunmaz; gövdesine
keyfi claim alanları eklenmiş gibi kullanılmamalıdır. Kesin token edinme endpoint/gövdesi
Auth sahibiyle kararlaştırılmalıdır. Library kabul sözleşmesi bu karar sonrası uyarlanabilir.

Recommendation okumasında `sub=svc:recommendation-service`,
scope=library:recommendation:read ve imzalı `target_user_id` gerekir. Bunun yanında
Library her istek için RECOMMENDATION amaçlı güncel User iznini arar.
İstemci kaydı, scope allowlist'i, target yetkileri ve anahtarların işletimi Auth sahibine
aittir. Gerçek ENV anahtarı veya değerleri bu çalışmada tanımlanmadı/değiştirilmedi.

### Tüketici çağrıları

User → Library iç yolları:

- GET /internal/v1/library/users/{userId}/entries?page=0&size=20.
- GET /internal/v1/library/users/{userId}/entries/statistics.

Alper'in Recommendation adaptörü → Library:

- GET /internal/v1/library/users/{userId}/recommendation-signals?page=0&size=20.
- DTO yalnız items[{gameId,favorite,completed}], page, size, totalElements taşır.
- favorite OR completed filtresi, addedAt DESC/id DESC, size 1..50.
- 401/403/503 boş koleksiyon/izin olarak yorumlanmamalı; izin reddi/ulaşılamama ayrılmalı.
- İç yollar Gateway'e açılmamalı; başka servisin DB'si okunmamalı.

## Diğer sahiplerden gerekenler

- **Kerem — User:** Oyun geçmişi görünürlüğü/erişim kararı API'si; hedef kullanıcı,
  doğrulanmış görüntüleyen bağlamı, amaç, yanıt ve hata sözleşmesi. Tercihler User'da kalmalı.
- **Kerem — Auth:** İhtiyaç duyulan servis istemcisi/hedef/scope çiftlerinin tanımı;
  kısa ömürlü token edinme, doğrulama ve istemci anahtarı işletim sözleşmesi.
- **Alper — Recommendation:** Kullanım amacı/izin modeli, gerekli alanlar, sayfalama
  ve Library tüketici sözleşmesi; özel kullanıcı verisi için izin dayanağı.
- **Gerekirse Umut — Social:** FRIENDS görünürlüğü ve engelleme için güvenilir karar
  API'si; bunun User tarafından mı Library tarafından mı tüketileceği kesinleştirilmeli.

Bu incelemede diğer servisler, ortak yapılandırmalar, frontend veya veritabanları
değiştirilmedi. Yeni ENV, migration veya bağımlılık eklenmedi. Library uçları ve yalnız
docs/api.md LIBRARY bölümü güncellendi. Pre-push ve SonarQube çalıştırılmadı.

LIB-10/11/14 gerçek bağımlılıklar nedeniyle açık kalır. LIB-18 otomatik güvenlik ve
veri bütünlüğü testleri tamamlandı; gerçek karşı servis E2E doğrulaması bunların yerine
geçmiş sayılmaz. Faz 4: **1/4**. Genel ilerleme: **14/18**. Faz 5'e geçilmedi.
