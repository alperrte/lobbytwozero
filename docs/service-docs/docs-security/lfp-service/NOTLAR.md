# lfp-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Umut.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- **Testler Docker ister.** Entegrasyon testleri Testcontainers ile `postgres:17-alpine` açar; Docker Desktop kapalıysa bağlam açılmaz ve testlerin çoğu çalışmaz (bu kod hatası değildir).
- **Engel kontrolü fail-closed.** `AUTH_LFP_SERVICE_CLIENT_SECRET` boşsa ya da Auth/Social ulaşılamazsa ilan listesi, detayı ve iletişim `503 BLOCK_CHECK_UNAVAILABLE` döner. DEV'de anahtar `node ops/dev.mjs` ile üretilir; Auth ve LFP aynı değeri almalıdır.
- **Oyun doğrulaması fail-closed.** game-service yanıt vermezse ilan açılmaz (`503 GAME_CATALOG_UNAVAILABLE`). Kart ucu kimliksizdir; istek sınırı yalnız game'in toplu kart ucundadır.
- **Test adlarında `_` yok.** Servisin Checkstyle borcu sıfır; `MethodName` kuralı test metotlarına da uygulanır (social'daki alt çizgili adlar dondurulmuş borçtur, örnek alınmaz).

## Ertelenen kararlar

- **Hesap durumu (askı/yasak):** `AccountStatusChanged` olayı tüketilmiyor (broker/iç olay ucu kararı bekliyor). Askıya alınan kullanıcının açık ilanları süresi dolana kadar listede kalır; yeni ilan açması Auth token'ı iptal edildiğinde zaten engellenir.
- **Bildirim:** ilan süresi dolunca sahibe bildirim yok (notification-service alıcı ucu tanımsız).
- **Metin araması** (`q`) indeks kullanmaz; ölçek büyürse `pg_trgm` (yeni eklenti, onay gerekir).
