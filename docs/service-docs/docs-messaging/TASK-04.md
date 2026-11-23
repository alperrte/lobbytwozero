# LTZ-UMT-MSG-04 — Sohbette Görsel, GIF ve Emoji (TASK-04)

> Tarih: 2026-11-09 · Sorumlu: Umut · Servis: `messaging-service` · Dal: `feature/messaging-service` · Kapsam: MSG-17, MSG-18, MSG-22 · Durum: **kod, testler ve belgeler tamam; `pre-push messaging-service` PASSED; `pre-push full dev` ve SonarQube bu teslimde koşulmadı (yerel Node 22, repo Node 24.7+ istiyor); gerçek GIPHY anahtarıyla ve tarayıcıdan uçtan uca doğrulanmadı**
>
> Endpoint sözleşmeleri `docs/api.md` → `## MESSAGING`; depolama kararı `docs/SECURITY.md` §13 (Messaging); şema `docs/database.md` §3.8, `docs/er-diagram.md` §9.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| MSG-17 Görsel ve GIF yükleme | IMPLEMENTED | PNG/JPEG/GIF, dosya başına **1 MB**; tür (magic byte) ve boyut sunucuda içerikten doğrulanır; metadata atılır; içerik `messaging_db`'de `bytea` (ayrı medya servisi yok) |
| MSG-18 Mesajlarda emoji | IMPLEMENTED | Gövde olduğu gibi saklanır (ZWJ, ten rengi, bayrak); bozuk UTF-16 (eşlenmemiş vekil) reddedilir; uzunluk UTF-16 birimiyle (JS `length`) |
| MSG-22 GIF platformu | IMPLEMENTED (gerçek anahtarla NOT VERIFIED) | GIPHY arama/öne çıkanlar backend üzerinden; gönderilen GIF'in adresi istemciden değil platformdan çözülür; anahtar yoksa fail-closed 503 |

Doğrulama (2026-11-09):

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, PostgreSQL 17) | PASS — 227 test, 0 hata, 0 atlanan (bu görevde 63 yeni test) |
| `.\pre-push\pre-push.cmd messaging-service` (`AUTH_SERVICE_URL=http://auth-service:8080` ile) | **BACKEND TEST CHECK PASSED** — 227 test, JaCoCo %97,21 (1464 / 1506), değişen satır %97,34 (914 / 939; `origin/main`'e göre), Checkstyle/PMD yeni ihlal 0. İlk koşularda iki bulgu giderildi: kapsam politikası (`SendMessageRequest`'te ölçülebilir değişen satır yoktu → `toDraft()`), PMD `PreserveStackTrace` (`GiphyGifClient`, kök hata okunmadan türe göre sabit ileti) |
| `.\pre-push\pre-push.cmd full dev` | **NOT RUN** — yerel Node 22.20; `ops/dev.test.mjs` `crypto.argon2Sync` için Node 24.7+ ister (CI Node 24). Bilinen ek engel: main'den gelen 6 hardware-service Checkstyle/PMD bulgusu Code Quality satırını FAIL yapar (bu görevde dokunulmadı, Umut'un kararı) |
| SonarQube (`sonar.cmd scan dev`) | **NOT RUN** |
| DEV stack: yeni imaj, V133–V134 gerçek `messaging_db`'ye uygulandı; Gateway yeni yolları geçirir (kimliksiz `401`: `/gifs/search`, `/gifs/trending`, `POST .../attachments`) | PASS |
| Gerçek GIPHY anahtarıyla arama/gönderim; Gateway + tarayıcı ile kimlikli görsel yükleme/indirme | NOT VERIFIED |

## 2. Akış

**Görsel yükleme** `POST /conversations/{id}/attachments` (gövde dosyanın kendisi; bu yol için gövde sınırı 32 KB yerine 1 MB):
1. Kısa transaction: aktif üye + oran (10/dk) + bekleyen yükleme (≤ 10) denetimi.
2. Transaction **dışında** `ImageIoInspector`: magic byte ↔ `Content-Type`; PNG/JPEG kenar/piksel sınırı başlıktan (çözmeden), sonra çözme + EXIF yönü + aynı biçimde yeniden kodlama (1 MB'ı aşarsa 4 adıma kadar %75 küçültme); GIF `GifCleaner` ile blok blok temizlenir (yeniden kodlanmaz). Eşzamanlı işleme 2 ile sınırlı (`MEDIA_BUSY`).
3. İkinci transaction: denetimler yeniden yapılır, `message_attachments` satırı `message_id = null` ile yazılır.
4. 1 saat içinde mesaja bağlanmayan satırı `AttachmentCleanupJob` (10 dk'da bir) siler.

**Görsel mesajı** `POST .../messages {attachmentId}`: mesaj eklendikten sonra aynı transaction'da koşullu `UPDATE … WHERE id = ? AND conversation_id = ? AND uploader_id = ? AND message_id IS NULL`; 1 satır güncellenmezse `422 ATTACHMENT_NOT_AVAILABLE` ve transaction geri alınır (mesaj, `seq` ve outbox olayı da yok).

**GIF mesajı** `POST .../messages {gif:{provider,id}}`: yeniden deneme kontrolü → yazma yetkisi (engel; transaction dışında) → `GifService.resolve` (GIPHY `GET /v1/gifs/{id}`, transaction dışında) → mesaj, platform yanıtının anlık kaydıyla (`gif_*` sütunları) yazılır. Yeniden denemede platform çağrılmaz.

**İndirme** `GET .../attachments/{id}`: aktif üye → görsel bu sohbette → bağlıysa mesaj üyeye görünür (`visible_from_seq`), bağlı değilse yalnız yükleyen; aksi her durum aynı `404 ATTACHMENT_NOT_FOUND`.

**Okuma yolları:** geçmiş, sohbet listesi `lastMessage`, gönderim yanıtı ve SSE `message` olayı aynı `MessageView`'ı taşır; `MessageAttachments` bir sayfa için görsel bilgisini tek sorguyla ekler (içerik belleğe alınmaz).

## 3. Kararlar ve varsayımlar

| Konu | Karar | Neden |
| --- | --- | --- |
| Kapsam | Yalnız backend (Umut'un kararı) | MSG-01..03 ile aynı; emoji seçici, GIF seçici ve görsel önizleme arayüzü ayrı bir mesajlaşma-web işi |
| Depolama | `messaging_db.message_attachments.content` `bytea` (Umut'un kararı) | Yeni volume/ENV yok, yedekleme DB ile, sohbet silinince cascade; community/content ile aynı yaklaşım. Öneri tablosundaki `storage_key` kullanılmadı |
| Yükleme biçimi | Ham gövde (`Content-Type: image/*`), çok parçalı form değil | Ayrıştırıcı ve dosya adı yok; 1 MB sınırı gövde filtresinde tek yerde |
| İki adımlı akış | Önce yükle (kimlik al), sonra mesajla gönder | Mesaj gövdesi JSON ve 32 KB kalır; yeniden deneme (`clientMessageId`) görselle birlikte idempotent |
| GIF işleme | Yeniden kodlanmaz, blok temizliği | Animasyonu korumak; polyglot/yorum/uygulama blokları atılır, sınırlar istemci çözme bombasını keser |
| Görsel kütüphanesi | JDK `ImageIO`, yeni bağımlılık yok | Bağımlılık yüzeyi büyümez; WebP/HEIC desteklenmez |
| GIF sağlayıcı | GIPHY, `GIPHY_API_KEY` boş varsayılanla (Umut'un onayı) | Anahtar yoksa GIF 503, diğer her şey çalışır |
| GIF adresi | İstemciden alınmaz; kimlikle platformdan çözülüp mesajda saklanır | Keyfi URL/izleme pikseli/SSRF'e kapı yok; yalnız GIPHY medya sunucusu HTTPS, sorgu dizgesi atılır |
| GIF derecesi | `pg-13` ve altı; derecesi bilinmeyen kabul edilmez | Genel kitle |
| Arama oran sınırı | 30/dk/kullanıcı, bellekte | Arama kalıcı kayıt üretmez; çoklu örnekte örnek başına (açık nokta) |
| Emoji | Şema değişikliği yok; eşlenmemiş vekil reddedilir | `text` UTF-8 zaten saklar; bozuk dizgi DB'ye ve istemciye gitmesin |
| Mesaj türü | İçerikten türetilir (`TEXT`/`IMAGE`/`GIF`); görsel ile GIF birlikte olamaz | İstemcinin bildirdiği türe güvenilmez |
| ENV kaydı | `.env.example`, `docker-compose.yml`, `ops/dev.mjs` (`blankDefaults`), `ops/ops.md` | Ekip kuralı: yeni anahtar `dev.mjs keys` ile yeni klonda boş eklenir. Gerçek `.env`/`.env.dev` dosyalarına dokunulmadı |
| Kök `migrations.md` | Değiştirilmedi | V134 MESSAGING aralığı V131–V140 içinde |

## 4. Güvenlik

| Konu | Uygulama |
| --- | --- |
| Dosya doğrulama | Magic byte ↔ `Content-Type`; SVG/HTML/WebP 415/422; boyut gövde filtresi + işlem sonrası + DB CHECK; piksel sınırı çözmeden önce; EXIF/konum ve gömülü veri atılır |
| Sunum | Yetki kontrollü uç; `nosniff`, `Content-Disposition: inline`, `Content-Security-Policy: default-src 'none'; sandbox`, `no-store, private` |
| Erişim | Görsel yalnız o mesajı görebilen aktif üyeye; gönderilmemiş görsel yalnız yükleyene; başka sohbetin kimliği o sohbet yolundan bulunmaz (aynı 404) |
| Kaynak sınırları | 10 yükleme/dk, ≤ 10 bekleyen, 1 saat TTL + temizlik; eşzamanlı işleme 2; GIF 2048 px / 300 kare / 150 M; 30 GIF arama/dk |
| GIF anahtarı | Yalnız sunucu; istek adresi hata/log/kök hataya yazılmaz; platforma kullanıcı kimliği gitmez |
| Gizlilik | Görsel içeriği ve kimlikler loglanmaz (temizlik yalnız sayı loglar); `MessageSent` outbox olayı içerik taşımaz; SSE olayı baytları değil indirme yolunu taşır |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| Şema (V134) | `FlywayMigrationTest` (8), `ServiceBoundaryTest` (3) | `V134__add_message_media.sql`, `MessageAttachment`, `GifSnapshot`, `Message` |
| MSG-17 doğrulama/temizleme | `ImageIoInspectorTest` (9), `GifCleanerTest` (5), `JpegOrientationTest` (3), `RasterImagesTest` (10) | `ImageIoInspector`, `GifCleaner`, `JpegOrientation`, `RasterImages` |
| MSG-17 yükleme/indirme/sınırlar/temizlik | `AttachmentUploadTest` (10), `RequestBodySizeLimitTest` (3) | `AttachmentService`, `AttachmentController`, `AttachmentCleanupJob`, `MessageAttachmentRepository`, `RequestBodySizeLimitFilter` |
| MSG-17 görsel mesajı | `MediaMessageTest` (8; görsel 5), `MessageStreamTest` (8; +1) | `MessageService`, `MessageDraft`, `MessageAttachments`, `ConversationListService`, `SendMessageRequest` |
| MSG-18 emoji | `MediaMessageTest` (emoji 3), `MessageSendTest` (12) | `SendMessageRequest.BODY_PATTERN` |
| MSG-22 GIF | `GifMessageTest` (8), `GiphyGifClientTest` (7), `GifSearchRateLimiterTest` (2) | `GifService`, `GifController`, `GiphyGifClient`, `GifSearchRateLimiter`, `GifProperties` |

Öne çıkan kanıtlar: başarısız görsel gönderimi `seq` tüketmez (`gonderilmisBaskasininYaDaBaskaSohbetinGorseliKullanilamazSiraIlerlemez`), sonradan katılan eski görseli, ayrılan yeni görseli göremez (`sonradanKatilanEskiGorseliAyrilanYeniGorseliGoremez`), saklanan JPEG'de EXIF ve gizli işaret yok, GIF'te `<script>` yorum bloğu atılır, istemci `gif.url` gönderemez (400), engelde GIPHY hiç çağrılmaz, eşlenmemiş vekil 400.

## 6. Değişen dosyalar

- `backend/services/messaging-service`: `V134__add_message_media.sql`; domain `Message` (görsel/GIF fabrikaları, `isRetryOf`), `MessageType` (`IMAGE`, `GIF`), `GifProvider`, `GifSnapshot`, `MessageAttachment`, `MessagingErrorCode`; uygulama `AttachmentService`, `AttachmentCleanupJob`, `AttachmentContent`, `AttachmentView`, `GifService`, `GifView`, `GifSearchRateLimiter`, `MessageAttachments`, `MessageDraft`, `MessageService`, `MessageView`, `ConversationListService`, portlar `ImageInspectorPort`, `GifProviderPort`, `GifProviderUnavailableException`; API `AttachmentController`, `GifController`, `MessageController`, `MessagingApiPaths`, `SendMessageRequest` (`toDraft`), `GifReferenceRequest`, `AttachmentResponse`, `GifResponse`, `GifSearchResponse`, `MessageResponse`, `GlobalExceptionHandler` (415); altyapı `infrastructure/storage/*`, `GiphyGifClient`, `AttachmentProperties`, `GifProperties`, `MessagingApplicationConfig`, `SecurityConfig`, `RequestBodySizeLimitFilter`, `AttachmentMeta`, `MessageAttachmentRepository`; `application.properties`; testler.
- `.env.example`, `docker-compose.yml` (messaging ortamına `GIPHY_API_KEY`), `ops/dev.mjs` + `ops/dev.test.mjs` (`blankDefaults`), `ops/ops.md`.
- `docs/api.md` MESSAGING (gönderim kaydı genişletildi; yeni yükleme, indirme, GIF arama, öne çıkan GIF kayıtları), `docs/database.md` §3.8, `docs/er-diagram.md` §9, `docs/SECURITY.md` §13, `docs/folder-structure.md` (`storage/` messaging), `docs/decisions/0001-gercek-zamanli-mesajlasma.md`, `docs-security/messaging-service/NOTLAR.md`.

## 7. Açık noktalar

1. **Gerçek GIPHY ile doğrulanmadı** (anahtar yok). GIPHY'nin üretim anahtarı ve kullanım şartları (atıf "Powered by GIPHY" logosu arayüzde gösterilmeli) canlıya çıkmadan teyit edilmeli.
2. **GIF görüntüsü istemcide GIPHY sunucusundan yüklenir**: kullanıcının IP adresi GIPHY'ye gider. Gizlilik politikasında belirtilmeli; vekil sunucu ayrı karar.
3. **Arama oran sınırı bellekte**: birden çok örnekte örnek başına uygulanır (SSE akış kaydıyla aynı tek örnek varsayımı, ADR 0001).
4. **Virüs/kötü amaçlı içerik taraması yok**; görseller yeniden kodlanır (PNG/JPEG) ya da blok temizlenir (GIF).
5. **WebP/HEIC desteklenmez** (JDK `ImageIO`); telefonlardan gelen HEIC istemcide JPEG'e çevrilmeli.
6. **Gateway + tarayıcı ile** görsel yükleme (1 MB gövde) ve `<img>` ile indirme (çerez → Bearer) canlı doğrulanmadı.
7. **Saklama**: görseller sohbet/mesaj silinince cascade ile gider; hesap silme iç ucu hâlâ yok (TASK-02 açık nokta). MSG-25/26 (benden/herkesten sil) LTZ-UMT-MSG-05'te görselin silinmesini de ele almalı.
8. **Frontend** bu görevde yok (yükleme, görsel/GIF gösterimi, GIF seçici, emoji seçici).
