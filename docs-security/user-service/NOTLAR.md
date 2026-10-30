# user-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Kerem.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- **Testler Docker ister.** Entegrasyon testleri Testcontainers ile `postgres:17-alpine` başlatır; Docker Desktop kapalıysa
  testler başlamaz. Birim testi (`ProfileTextTest`) Docker gerektirmez.
- **Kalite kapısı:** `lint-ratchet` borcu sıfır; yeni ihlal CI'ı kırar. Checkstyle `MethodName` kuralı test metotlarında da
  `^[a-z][a-zA-Z0-9]*$` ister (alt çizgi yok); Social/Game testlerindeki alt çizgili adlar o servislerin dondurulmuş borcudur.
- **Gerçek Auth ile uçtan uca doğrulama yapılmadı (NOT VERIFIED):** token'lar testte geçici RSA anahtarıyla, Auth'un
  `ServiceTokenIssuer` biçimine göre üretilir. Auth `AUTH_EVENT_TARGETS`'a `user` eklenmedikçe olay gelmez.

## Alınan güvenlik kararları (2026-10-30)

- **`iat` zorunlu.** Spring, `iat` yoksa `exp-1 sn` atadığı için doğrulayıcılar eksikliği göremez; imza çözülürken
  `JWTClaimsSetVerifier` ile eksik `iat` reddedilir (Social'daki servis token'ı kuralından bir adım sıkı; rehber §2 "iat yoksa ret").
- **Kullanıcı token'ında `sub` UUID olmalı** (profil anahtarı olduğu için).
- **Profil metni düz metin saklanır.** Biyografi HTML'e çevrilmez; bidi/sıfır genişlik/denetim karakterleri sunucuda
  reddedilir. İstemci yalnız metin olarak göstermelidir (frontend'de `dangerouslySetInnerHTML` yasak).
- **Olay yükü ve hesap kimliği inbox'a yazılmaz** (kişisel veri yok); silme ucu `inbox_events`'e dokunmaz.

## Bilinen sınırlar / izlenecekler

- **Silme sonrası yeniden oluşma:** `GET /me` profili yoksa oluşturur. Hesap silindikten sonra, token ömrü (en çok 60 sn +
  30 sn tolerans) içinde gelen bir istek yalnız `userId` içeren boş bir satırı yeniden açabilir (kişisel veri yoktur).
  Auth'un olay tablosu hesaba `ON DELETE CASCADE` bağlı olduğundan, silinen hesabın bekleyen olayları da silinir.
- **`inbox_events` temizlenmiyor.** Satırlar küçüktür; saklama süresi kararı verilene kadar silinmez. Üretici yeniden
  deneme penceresinden kısa bir süreyle silinmemelidir.
## Gizlilik, açık profil ve arama (faz 2)

- **Tek kural:** `PrivacyRules` (sahip/arkadaş/üye/misafir × `PUBLIC/FRIENDS/PRIVATE`). Açık profil, toplu kart, arama ve
  servis karar ucu aynı kuralı çağırır; tam matris `PrivacyRulesTest` ile sınanır.
- **Gizleme yanıtları ayırt edilemez:** olmayan profil, yasaklı/devre dışı/silinmekte/doğrulanmamış hesap ve engel (iki yön)
  aynı `404 PROFILE_NOT_FOUND`. Toplu kartta bunlar sessizce atlanır.
- **Misafir:** yalnız açık profil ve görsel okunabilir; arama ve toplu kart `401`. Geçersiz token misafir sayılmaz (`401`).
- **İç karar ucu fail-closed:** `ltz.user.social.projection-enabled=false` (varsayılan) iken engel/arkadaşlık bilinmez ve
  sahibi olmayan izleyiciye hiçbir izin verilmez. Social olayları (T6) gelmeye başlayıp geçmiş ilişkiler aktarılmadan bu
  değer `true` yapılmamalıdır; aksi halde eski engeller bilinmeden izin verilir.
- **`viewerId` doğrulanamaz:** karar ucu izleyiciyi çağıran servisin token doğrulamasına güvenir; bu yüzden yalnız
  `privacy:read` kapsamlı ve izinli listedeki (social, hardware, library) servislere yanıt verir.
- **Yansıma gecikmesi:** engel/arkadaşlık olayla gelir; Social'daki değişiklik birkaç saniye gecikmeyle yansır. Açık profil
  engel tablosunu her zaman denetler (yansıma boşken engel yok sayılır, misafirle aynı görünüm).
- **Arama:** yalnız `ACTIVE`, en çok 20 sonuç, önek; LIKE özel karakterleri kaçırılır; Türkçe `ı/I/İ/i` aynı sayılır.
  Kullanıcı başına arama oran sınırı **yok** (giriş zorunlu ve sonuç 20 ile sınırlı); kötüye kullanım görülürse eklenmeli.
- **Bilinen sınır:** özet sayıları (Library/Review) için üretici olaylar henüz yok; bölümler `UNAVAILABLE` döner.

## Profil görselleri (faz 1b)

- **Yükleme ham gövdeyle** (`PUT`, `Content-Type: image/*`), multipart değil: ayrıştırıcı ve geçici dosya yükü yok; bayt sınırı
  akış sırasında tür başına uygulanır (avatar 2 MB, kapak 4 MB; JSON uçları 16 KB).
- **Güven sınırı:** biçim yalnız ilk baytlardan (magic byte) belirlenir, bildirilen tür yalnız tutarlılık için karşılaştırılır.
  SVG/HTML/tanınmayan reddedilir. JPEG/PNG sunucuda **yeniden kodlanır** (EXIF/konum ve gizli içerik temizlenir); GIF
  çözülmeden yapısı doğrulanıp yorum/uygulama blokları atılır. Dosya adı sunucu üretimi 128 bitlik rastgele anahtardır;
  kullanıcı adı hiçbir yerde kullanılmaz. Yol atlatma iki katmanla engellenir (ad deseni + çözülen yolun kök altında kalması).
- **Dekompresyon bombası:** boyut görsel çözülmeden başlıktan okunur (kenar ≤ 8000, ≤ 40 MP); JPEG/PNG alt örnekleyerek
  açılır; aynı anda en çok 2 görsel işlenir; kullanıcı başına saatte 10 yükleme (DB'den sayılır, çok örnekli kurulumda doğru).
- **Tutarlılık:** yeni dosya → tek DB transaction'ı (eski satır `PENDING_DELETE`, yenisi `CURRENT`) → commit sonrası eski dosya
  silinir. DB başarısız olursa yeni dosyalar geri silinir; dosya silme başarısız olursa satır kalır ve temizleyici (5 dk) yeniden
  dener. Profilde dosyası olmayan `CURRENT` satır oluşmaz.
- **Hesap silme:** önce hesabın tüm dosyaları (bekleyenler dahil) silinir; silinemezse `503 ACCOUNT_ERASURE_PENDING` ve hiçbir
  satır silinmez (Auth yeniden dener).
- **Herkese açık okuma veritabanına gitmez**; adres değişmezdir (`immutable`, 1 yıl). Bunun sonucu: kaldırılan görsel, adresi
  daha önce önbelleğe alan istemcide süre dolana kadar görünebilir. Banlı hesabın görseli de adresi bilinen biri için erişilebilir
  kalır (adres tahmin edilemez, listelenemez); gerekirse sonraki fazda sunum durum denetimine bağlanır.
- **Disk dolması:** kota yok; kullanıcı başına en çok 2 aktif görsel (+ pencere içindeki bekleyenler) ve boyut sınırları üst
  sınırı belirler. Volume doluluğu izlenmelidir.
- **NOT VERIFIED:** Docker imajında/volume'de (root olmayan `ltz` kullanıcısı, `/data/user-media`) gerçek yazma ve
  `docker-compose.yml` doğrulaması bu çalışmada yapılmadı; yalnız yerel disk ve Testcontainers ile sınandı.

## Bağlı hesaplar: Steam (faz 3)

- **Kimlik kanıtı User'da üretilmez.** Steam kimliği yalnız Auth'un imzalı `ExternalIdentityLinked` olayıyla gelir
  (`servis-sozlesmeleri.md` §1); kullanıcı uçları yalnız `showOnProfile` iznini değiştirir, kimlik yazamaz.
- **Varsayılan gizli:** bağlantı ilk geldiğinde `showOnProfile=false`; farklı bir Steam kimliği bağlanırsa izin sıfırlanır
  (eski kimliğe verilen onay yenisine geçmez). Açık profilde yalnız etkin bağlantı + sahibin izni + `details` hakkı olan
  izleyici görür; misafire yalnız sahip `DETAILED` açtıysa.
- **Token saklanmaz** (Steam OpenID access token vermez); yalnız 17 haneli SteamID64 ve herkese açık profil adresi.
- **Sırası bozuk olay:** son-durum upsert `occurredAt` ile korunur; kaldırılan bağlantı `active=false` satırı olarak kalır
  (kimlik ve izin silinmiş), böylece gecikmiş eski bir "bağlandı" kaldırmayı geri alamaz.
- **Tekillik:** aynı SteamID64 iki hesapta etkin olamaz (DB `UQ(provider, external_id)` + olay öncesi denetim); ihlal
  `400 INVALID_EVENT` olur ve Auth'ta görünür. Tekillik asıl olarak Auth'ta uygulanmalıdır.
- **Bilinen sınır:** bağlama/kaldırma olayı profil yoksa profili `ACTIVE` açar (`UserRegistered` gibi). Hesap silindikten
  sonra gecikmiş bir olay profili yeniden oluşturabilir; Auth'un silinen hesabın bekleyen olaylarını sildiği (REQ-03) için
  pratikte beklenmez.
- **NOT VERIFIED:** olay sözleşmesi (T4) Auth tarafından henüz kabul edilmedi ve uygulanmadı.
