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
- **Profil görselleri (Faz 1b) henüz yok:** hesap silmede dosya temizliği o fazda eklenecek.
