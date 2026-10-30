# User Service — kararlar

Tarih: 2026-10-30. Karar sahibi: Kerem (proje sahibi). Bu belge [docs/decisions/](../../docs/decisions/) altındaki
ADR'lerin yerine geçmez; User'a özgü kararların kaydıdır.

## Alınan kararlar

| # | Karar | Gerekçe |
| --- | --- | --- |
| K1 | Auth kimlik kaynağıdır; User `userId` ile profil sahibidir ve herkese veri sağlayabilir | Servis sınırı (AUTH-17, USER-13) |
| K2 | Profil görselleri (avatar, kapak, GIF) servis içinde, bayt sınırlı ve hafif tutulur; ayrı Media Service yok | USER-13, SECURITY.md §13 |
| K3 | Medya sınırları: avatar JPEG/PNG ≤ 2 MB (128 ve 512 px), kapak ≤ 4 MB (800 ve 1600 px), GIF avatar ≤ 2 MB / kapak ≤ 4 MB / ≤ 100 kare, GIF yeniden kodlanmaz. Yeni bağımlılık yok (JDK ImageIO); WebP/AVIF ertelendi | Sunucu yükünü azaltma |
| K4 | Varsayılan gizlilik: arkadaş listesi `FRIENDS`, kütüphane/oyun geçmişi `PUBLIC`, donanım `FRIENDS`, performans `PRIVATE`; kullanıcı değiştirebilir | |
| K5 | Misafir başkasının profilini görebilir ama sınırlı: varsayılan ad+avatar+kapak; profil sahibi `DETAILED` ile biyografi vb. alanları açabilir; misafir arama yapamaz | USER-05/06 |
| K6 | `SUSPENDED` hesap: profil görünür, aramada çıkmaz. `BANNED`/`DISABLED`: 404 | |
| K7 | Şimdilik yalnız backend; frontend profil sayfaları sonra | |
| K8 | Steam önce; **Discord kapsam dışı** | Discord'a Türkiye'de erişim engeli bildirildi (doğrulanmadı); resmi/güvenli bağlama yolu garanti değil. Tablo ve API Discord'a açık tasarlanır, ekip kararıyla sonra eklenebilir |
| K9 | `player_type` değerleri: `CASUAL`, `COMPETITIVE`, `STORY`, `SOCIAL`, `COLLECTOR` (en çok 2); favori tür/platform kodları biçimce doğrulanır (en çok 10), Game'e senkron çağrı yok | |
| K10 | `country` dışında kişisel bilgi alanı yok (KVKK yükü, gereksinimde tanımlı değil) | |
| K11 | Düz string listeleri `jsonb` yerine `text[]` | Hibernate JSON eşlemesine bağımlılık yok; CHECK ile kardinalite sınırı |

## Süreç kararı (2026-10-30)

- **Testler en sona:** fazlarda yalnız üretim kodu ve belge yazılır; testler tüm fazlar bitince toplu yazılır (bağlam ve token
  tasarrufu). Her fazın ilerleme dosyası "sonda yazılacak testler" listesini taşır. Faz 1a, 1b ve 2'nin testleri zaten yazıldı.
- **Migration numarası:** Faz 4 `V73` aldı (planda V74); Faz 3 `linked_accounts` `V74` olacak, böylece sıra artarak kalır.
- **Auth'a bağlı işler en sona:** Steam bağlama (Faz 3) ve talepler REQ-01..04, T6-T8.

## Uygulama kararları (faz 1b, 2026-10-30)

- **Ham gövde yükleme:** `PUT /me/avatar|cover`, gövde görselin kendisi (multipart yok). Plan multipart diyordu; ham gövde,
  ayrıştırıcı ve geçici dosya yükünü kaldırır ve bayt sınırını akışta uygular.
- **İki boyutlu çıktı:** büyük (512 / 1600) ve küçük (128 / 800). Liste ve arama sonuçları küçüğü kullanır.
- **Okuma DB'siz:** dosya adı anahtarı taşır; `GET /media/{dosya}` yalnız diske gider, `immutable` 1 yıl önbellek.
- **Alt örnekleyerek açma:** 40 MP'lik fotoğraf tam boyutta açılmaz; bellek ~160 MB yerine birkaç MB.
- **Eşzamanlılık sınırı 2, saatlik yükleme sınırı 10:** sırasıyla CPU ve kötüye kullanım koruması; değerler
  `application.properties` içinde `ltz.user.media.*` (ENV değil).
- **WebP/AVIF ertelendi:** native kütüphane gerektirir (güvenlik/lisans incelemesi); `MediaStoragePort` ve URL sözleşmesi
  eklemeye uygun.
- **Kota yok:** disk üst sınırı boyut sınırlarından gelir; volume izlenmeli.

## Uygulama notları (faz 1a)

- `GET /me` profil yoksa oluşturur: Auth'un `UserRegistered` olayı hedef açılana kadar ya da gecikirse kullanıcı 404 görmez.
- `AccountStatusChanged` profil yokken yalnız kısıtlayıcı durumlar için kayıt açar; eski olay yeni durumu ezmez.
- `iat` claim'i zorunludur; Spring varsayılan değer atamadan önce imza çözümünde denetlenir.
