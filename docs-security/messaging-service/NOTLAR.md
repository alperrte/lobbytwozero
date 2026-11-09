# messaging-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Umut.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- **Testler Docker ister (2026-11-01, LTZ-UMT-MSG-01).** Entegrasyon testleri Testcontainers ile `postgres:17-alpine`
  başlatır; Docker kapalıyken `IntegrationTestSupport`'tan türeyen testler koşmaz (PASS sayılmaz). JWT'ler testte üretilen
  RSA anahtarıyla gerçekten imzalanır, yerel JWKS sunucusundan doğrulanır; repoda anahtar yoktur.
- **Engel kontrolü fail-closed.** Engel verisi social-service'tedir; `AUTH_MESSAGING_SERVICE_CLIENT_SECRET` boşsa ya da
  Auth/Social ulaşılamazsa birebir sohbet açma, grup kurma ve gruba ekleme `503 BLOCK_CHECK_UNAVAILABLE` döner. DEV'de bu
  uçları denemek için anahtar `node ops/generate-auth-dev-keys.mjs` ile üretilmeli (Auth ile aynı değer). Testlerde engel
  `ScriptedBlockCheck` ile taklit edilir (`ltz.messaging.blocks.adapter=scripted`); gerçek istemci
  `SocialBlockCheckClientTest`'te MockRestServiceServer ile sınanır. Gerçek Auth + Social ile uçtan uca: NOT VERIFIED.
- **Hesap silme iç ucu yok.** Auth silme hedeflerinde `messaging` var; `DELETE /internal/v1/accounts/{accountId}` henüz
  yazılmadı (`/internal/**` şu an kapalı, 401). Messaging artık kişisel veri (üyelik) tuttuğu için ayrı görev olarak
  yazılmalı (docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md §4).
- **Oran sınırı.** Mesaj gönderimi kullanıcı başına dakikada 30 ile sınırlı (LTZ-UMT-MSG-02, DB sayımı, 429).
  Sohbet açma/grup kurma için oran sınırı hâlâ yok (SECURITY.md §12).
- **Gerçek zamanlı akış (SSE, ADR 0001).** Akış token bitişinde kapanır (askıya alınan hesap en geç token ömrü kadar
  akış alır). Kayıt bellektedir: tek örnek varsayımı. Gateway WebMVC üzerinden canlı akış ve 4 sn kalp atışının 5 sn
  proxy okuma zaman aşımını aştığı **NOT VERIFIED**. `SecurityConfig` `ASYNC` dispatch'e izin verir (yalnız zaten
  doğrulanmış SSE isteğinin devamı; yeni istek değildir).
- **Mesaj içeriği.** Loglanmaz; `MessageSent` outbox olayı içerik/önizleme taşımaz. Gövde düz metin olarak saklanır,
  sunucu HTML temizlemez — istemci düz metin göstermelidir (web-rules: `dangerouslySetInnerHTML` yok).
- **Bildirim teslimi yok.** `MessageSent` olayları `PENDING` birikir (relay yok; notification ucu ve Auth çifti yok).
  Saklama/temizlik görevi de yok — relay eklenirken birlikte ele alınmalı.
- ProblemDetail `instance` alanı istenen yolu taşır (Spring varsayılanı, social ile aynı); `detail`/`code` gönderilen
  değeri yansıtmaz.
- **Okundu bilgisi gizliliği (LTZ-UMT-MSG-03, MSG-11/12).** Kimin okuduğunu yalnız mesajın göndereni görür:
  `GET .../messages/{messageId}/reads` gönderen dışındaki aktif üyeye `403 NOT_MESSAGE_SENDER` verir. Sıra: üyelik
  (404 `CONVERSATION_NOT_FOUND`) → mesaj bu sohbette ve üyeye görünür (404 `MESSAGE_NOT_FOUND`) → gönderen (403);
  böylece gönderen olmayan biri, başka sohbetin ya da katılımından önceki bir mesajın varlığını öğrenemez (var olan ile
  olmayan aynı 404). `readStatus` alanı yalnız isteyenin kendi mesajında doludur, başkasında `null`.
- **SSE `read` olayı kırpması.** Olay yalnız okunan aralıkta mesajı olan, hâlâ aktif göndericiye gider ve alıcının kendi
  mesajlarıyla kırpılır (`readUpToSeq`, `allReadUpToSeq`); ham işaretçi, ham su seviyesi ve içerik taşımaz. Aksi hâlde A,
  R'nin B'nin mesajını okuduğunu öğrenirdi. Yeni katılan okuyucunun aralığı görebildiği ilk mesaja kırpılır (görmediği
  mesajlar için alıcıya olay gitmez). Kanıt: `ReadReceiptStreamTest` (A seq 5, B seq 9, R `(0, 9]` okur → A
  `readUpToSeq = 5`). Olay en iyi çabadır; eşzamanlı iki okumada canlı `allReadUpToSeq` eski kalabilir, geçmiş/liste
  çekimi `readStatus`'u yeniden hesaplar (ADR 0001).
- **Mesaj görseli (LTZ-UMT-MSG-04, MSG-17, V134).** İçerik `messaging_db`'de `bytea` (SECURITY.md §13 Messaging kararı).
  Doğrulama JDK `ImageIO` ile, yeni bağımlılık yok: biçim magic byte'tan, PNG/JPEG yeniden kodlanır (EXIF/konum atılır),
  GIF bloklarına ayrılıp temizlenir (`GifCleaner`; yorum/uygulama blokları ve kapanış sonrası polyglot yük atılır).
  Kenar/piksel sınırı çözmeden önce, eşzamanlı işleme 2 ile sınırlı (`MEDIA_BUSY`). Kanıt: `AttachmentUploadTest`,
  `ImageIoInspectorTest`, `GifCleanerTest`, `JpegOrientationTest`. İndirme yetkisi mesaj görünürlüğüyle aynıdır;
  gönderilmemiş görseli yalnız yükleyen görür, diğer her durum aynı `404 ATTACHMENT_NOT_FOUND`. Bağlanmayan yükleme
  `AttachmentCleanupJob` ile 1 saat sonra silinir (yalnız sayı loglanır). Kötü amaçlı içerik/virüs taraması yok.
- **GIF platformu (MSG-22, GIPHY).** `GIPHY_API_KEY` yalnız sunucuda (platform sorgu dizgesinde istiyor); istek adresi
  hiçbir hata/log/kök hataya yazılmaz (`GiphyGifClientTest`). Anahtar boşsa arama ve GIF gönderme fail-closed `503`.
  Gönderilen GIF'in adresi istemciden alınmaz, `GET /v1/gifs/{id}` ile çözülür; yalnız GIPHY medya sunucularının HTTPS
  adresi, `pg-13` ve altı derece kabul edilir. Platforma kullanıcı kimliği gitmez. Arama sınırı (30/dk) bellektedir:
  birden çok örnekte örnek başına uygulanır. GIF görüntüsü istemcide GIPHY sunucusundan yüklenir; bu, kullanıcının IP
  adresini GIPHY'ye açar (gizlilik politikasında belirtilmeli; vekil sunucu ayrı karar). Gerçek anahtarla: NOT VERIFIED.
- **Emoji (MSG-18).** Gövde olduğu gibi saklanır; eşlenmemiş UTF-16 vekil reddedilir (bozuk emoji DB'ye girmez).
- **Okuma aralığı tablosu (`message_read_ranges`, V133).** Satır sayısı gerçek ilerleme sayısıyla büyür (mesaj sayısıyla
  değil; gönderim de bir ilerlemedir); ilerlemeyen çağrı satır yazmaz, böylece okuma çağrısı sınırsız satır üretemez. Eşzamanlı okumalar üye satırı
  kilidiyle sıralanır (çakışan ya da boşluklu aralık yok). Saklama/temizlik görevi yok; ayrılan üyenin satırları kalır
  (sohbet silinince `on delete cascade`). V133 öncesi okumalar için `readAt` `null`'dır.
