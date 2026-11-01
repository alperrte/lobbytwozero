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
