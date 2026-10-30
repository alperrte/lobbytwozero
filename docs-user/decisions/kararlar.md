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

## Uygulama notları (faz 1a)

- `GET /me` profil yoksa oluşturur: Auth'un `UserRegistered` olayı hedef açılana kadar ya da gecikirse kullanıcı 404 görmez.
- `AccountStatusChanged` profil yokken yalnız kısıtlayıcı durumlar için kayıt açar; eski olay yeni durumu ezmez.
- `iat` claim'i zorunludur; Spring varsayılan değer atamadan önce imza çözümünde denetlenir.
