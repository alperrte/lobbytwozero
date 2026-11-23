# LTZ-UMT-MSG-05 — Mesaja Yanıt, İlet, Tepki Ver ve Sil (TASK-05)

> Tarih: 2026-11-09 · Sorumlu: Umut · Servis: `messaging-service` · Dal: `feature/messaging-service` · Kapsam: MSG-19, MSG-20, MSG-23, MSG-24, MSG-25, MSG-26 · Durum: **kod, testler ve belgeler tamam; `pre-push messaging-service` PASSED; DEV stack'te V135 uygulandı ve yeni yollar Gateway'den geçiyor; `pre-push full dev` ve SonarQube sonuçları aşağıdaki tabloda; tarayıcıdan kimlikli uçtan uca doğrulanmadı**
>
> Tasarım: `docs-messaging/TASK-05-TASARIM.md` · Plan: `docs-messaging/TASK-05-PLAN.md` · Endpoint sözleşmeleri `docs/api.md` → `## MESSAGING`; şema `docs/database.md` §3.8, `docs/er-diagram.md` §9; güvenlik `docs/SECURITY.md` §13, `docs-security/messaging-service/NOTLAR.md`; canlı olaylar ADR 0001.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| MSG-19 Mesajı yanıtla | IMPLEMENTED | Aynı sohbette görülebilen, silinmemiş mesaja; alıntı önizlemesi okuyana göre (görüyorsa içerik, silinmişse "silindi", göremiyorsa "görüntülenemiyor") |
| MSG-20 Mesaj iletme | IMPLEMENTED | 1–5 hedef, hepsi ya da hiçbiri; "İletildi" / 5+ "Çok kez iletildi"; görsel baytları kopyalanmaz; idempotent |
| MSG-23 Mesaj tepkileri | IMPLEMENTED | Kişi başına tek emoji tepkisi (her emoji); değiştir/kaldır; liste; dakikada 60 |
| MSG-24 Mesaj işlem menüsü | IMPLEMENTED (backend kısmı) | Menü frontend'de; backend `deletableForEveryoneUntil`, `reactions.mine`, `forwarded` verir ve her yetkiyi uygular |
| MSG-25 Benden sil | IMPLEMENTED | Yalnız silenin geçmişi, `lastMessage`'ı, okunmamışı ve alıntıları; okundu bilgisi değişmez |
| MSG-26 Herkesten sil | IMPLEMENTED | Gönderen ya da grupta OWNER/ADMIN, 48 saat; içerik, görsel, GIF ve tepkiler gerçekten silinir; satır kalır |

Doğrulama (2026-11-09):

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, PostgreSQL 17) | PASS — 305 test, 0 hata, 0 atlanan (bu görevde 78 yeni test) |
| `.\pre-push\pre-push.cmd messaging-service` (`AUTH_SERVICE_URL=http://auth-service:8080`) | **BACKEND TEST CHECK PASSED** — 305 test, JaCoCo satır %97,61 (1878 / 1924), değişen satır %97,67 (1218 / 1247; `origin/main`'e göre, MSG-04 dahil), Checkstyle/PMD yeni ihlal 0. İlk koşuda iki bulgu giderildi: kapsam politikası (`MessageProperties`'te ölçülebilir değişen satır yoktu → silme süresinin pozitif olmasını denetleyen kurucu + `MessagePropertiesTest`), PMD `CyclomaticComplexity` (`ReactionEmoji.validate` → yardımcılara bölündü) |
| `.\pre-push\pre-push.cmd full dev` | Sonuç commit öncesi bu tabloya işlenecek (bkz. Claude'un teslim mesajı) |
| SonarQube (`sonar.cmd scan dev`) | Sonuç commit öncesi bu tabloya işlenecek |
| DEV stack: yeni messaging imajı; V135 gerçek `messaging_db`'ye uygulandı (Flyway "now at version v135"); Gateway yeni yolları geçirir (kimliksiz `401`: `PUT .../reaction`, `GET .../reactions`, `POST .../forward`, `DELETE .../messages/{id}`) | PASS |
| Tarayıcı + Gateway ile kimlikli yanıt/iletme/tepki/silme | NOT VERIFIED |

## 2. Akış

**Ortak ön koşul** (`VisibleMessages`): mesaj bu sohbette, isteyenin görünürlük sınırında (`visible_from_seq`) ve isteyen onu benden silmemiş olmalı; aksi her durum aynı `404 MESSAGE_NOT_FOUND`. Herkesten silinmiş mesaja yanıt, iletme ve tepki `422 MESSAGE_DELETED`.

**Yanıt** `POST .../messages {replyToMessageId}`: hedef, gönderim transaction'ında sohbet satırı kilitliyken doğrulanır; mesaj hedefe kalıcı referans (`reply_to_message_id`) tutar. Okurken `MessageViews` tüm sayfanın hedeflerini tek sorguyla, izleyenin gizlediklerini tek sorguyla çeker ve önizlemeyi izleyene göre kurar. Yeni mesajın SSE yükü alıcı başına kurulur (`MessageViews.forRecipients`).

**Herkesten sil** `DELETE .../messages/{id}?scope=everyone`: tek transaction, mesaj satırı `FOR UPDATE`. Yetki (`MessageViews.canDeleteForEveryone`) → süre (48 saat) → gövde ve GIF kaydı temizlenir, `deleted_at/by` → görsel bağlantısı silinir, içeriğe başka bağlantı yoksa içerik (`attachment_blobs`) silinir → tepkiler silinir → commit sonrası `message_deleted`.

**Benden sil** `DELETE ...?scope=me`: `message_hidden_for_users`'a idempotent ekleme; geçmiş (`findOlder/findNewer`), son mesaj (`findLastVisibleMessages`, LATERAL) ve okunmamış (`countUnread`) sorguları `not exists` ile gizlenenleri atlar. Commit sonrası yalnız silenin akışlarına `message_hidden`.

**Tepki** `PUT/DELETE .../reaction`: tek emoji doğrulaması (`ReactionEmoji`) → oran sınırı → mesaj `FOR SHARE` (silme ile sıralanır) → upsert/sil → güncel özet ve alıcı başına `mine` ile `reaction` olayı.

**İletme** `POST .../forward`: yeniden deneme kontrolü (hedef başına türetilmiş istemci kimliği) → her hedefte `requireCanWrite` (engel, transaction dışında; biri başarısızsa hiçbir şey yazılmaz) → tek transaction: kaynak `FOR SHARE`, hedefler kimlik sırasıyla `FOR UPDATE`, oran sınırı (son 1 dk + hedef sayısı), her hedefte `MessageAppender.append` (seq, kayıt, okuma işaretçisi, outbox `MessageSent`, alıcı başına `message` olayı); görselde `MessageAttachment.forwardCopy` aynı `blob_id`'ye yeni bağlantı açar.

## 3. Kararlar ve varsayımlar

| Konu | Karar | Neden |
| --- | --- | --- |
| Davranış | WhatsApp gibi (Umut'un kararı) | Kullanıcıların tanıdığı model |
| Ölçek | Binlerce kullanıcı (Umut); sayfa başına sabit sorgu, satır sayısı kullanıcı eylemiyle sınırlı | Yayına çıkış hedefi |
| Herkesten silme süresi | 48 saat; grupta OWNER/ADMIN başkasının (yönetici ve sahibin dahil) mesajını da siler (Claude'un kararı, Umut onayladı) | WhatsApp davranışı |
| Benden silinen mesaj alıntıda | "görüntülenemiyor" (Claude'un kararı, Umut onayladı) | "Benden sil"in amacı o içeriği görmemek |
| İletme hedefleri | En çok 5; biri uygun değilse hiçbiri (onaylandı) | Kısmi başarı kafa karıştırır |
| İletilen kopya | Orijinal gönderen tutulmaz; `forward_count` (öneri tablosundaki `forwarded_from_id` yerine); 5+ "Çok kez iletildi" (onaylandı) | Gizlilik; silinen kaynağa referans kalmaz |
| Görsel paylaşımı | İçerik `attachment_blobs`'a taşındı; bağlantılar içeriği paylaşır | İletme her seferinde 1 MB kopyalamasın (DB büyümesi) |
| Tepki | Kişi başına tek (öneri PK'si `(message_id, user_id, emoji)` yerine `(message_id, user_id)`); her tek emoji; liste herkese açık; 60/dk bellekte (onaylandı) | WhatsApp davranışı |
| Benden sil | Geri alınamaz; okundu bilgisini değiştirmez (onaylandı) | Gizlemek okumak değildir |
| Kaçırılan değişiklik olayları | İstemci yeniden bağlanınca son sayfayı yeniden çeker; olaylar `id` taşımaz (onaylandı) | Kalıcı değişiklik akışı gereksiz karmaşıklık |
| SSE `message` yükü | `MessageResponse` ile birebir aynı, alıcı başına | Alıntı ve silme hakkı alıcıya göre değişir |
| Ortak kod | `VisibleMessages`, `MessageViews`, `MessageAppender`, `MessageChanged` + `MessageChangeDispatcher`, `FixedWindowRateLimiter` | Tekrar yok; GIF arama sınırlayıcısı da ortak sınıfa geçti (davranış aynı) |
| Mesaj düzenleme | Kapsam dışı | PDA'da yok |
| Bildirim | Tepki/silme outbox'a yazılmaz | notification-service hazır değil |

## 4. Güvenlik

| Konu | Uygulama |
| --- | --- |
| Var/yok sızıntısı | Başka sohbetin, görülemeyen ve olmayan mesaj aynı `404`; alıntıda göremediği hedefin göndereni ve içeriği dönmez |
| Silinen içerik | Gövde, GIF kaydı, görsel bağlantısı/içeriği ve tepkiler aynı transaction'da silinir; hiçbir uçtan ve olaydan dönmez (testli). DB yedeklerinde yedek saklama süresi kadar kalabilir |
| Yarış | Silme `FOR UPDATE`, tepki/iletme `FOR SHARE`: silme sonrası tepki/iletme `422`, silinmiş mesajda tepki satırı kalmaz; iletme hedefleri kimlik sırasıyla kilitlenir |
| Engel | İletme her birebir hedefte engel kontrolünü uygular; atlatma yolu değildir; öğrenilemezse `503` (fail-closed) |
| Kaynak sınırları | İletme gönderim oranına sayılır (30/dk); tepki 60/dk; iletilen görsel bağlantıları yükleme oranına sayılmaz ama bayt da üretmez |
| Girdi | Tepki tek emoji (tek grafem; harf/boşluk/kontrol/eşlenmemiş vekil yok, ≤ 32 birim, DB CHECK); `scope` yalnız `me`/`everyone` |
| Gizlilik | İçerik ve emoji loglanmaz; `message_deleted`/`message_hidden` içerik taşımaz; `MessageSent` içerik taşımaz |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| Şema (V135) ve blob ayrımı | `FlywayMigrationTest` (+2), `AttachmentUploadTest` (+1), `ServiceBoundaryTest` | `V135__message_interactions.sql`, `AttachmentBlob`, `MessageAttachment`, `MessageReaction`, `HiddenMessage`, `AttachmentService` |
| MSG-19 yanıt, MSG-24 silme hakkı | `MessageReplyTest` (11), `MessageViewsTest` (3) | `MessageViews`, `ReplyPreview`, `MessageService`, `SendMessageRequest`, `MessageResponse` |
| Alıcı başına SSE | `MessageReplyTest.yanitAkistaAliciyaGoreAlintiTasir`, `SseMessageHubTest` (+2) | `MessageCreated`, `MessageStreamPort`, `SseMessageHub`, `MessageChangeDispatcher` |
| MSG-25 benden sil | `MessageHideTest` (8) | `MessageHidingService`, `MessageActionController`, `MessageRepository`, `ConversationMemberRepository`, `HiddenMessageRepository` |
| MSG-26 herkesten sil | `MessageDeleteForEveryoneTest` (9), `MessagePropertiesTest` (1) | `MessageDeletionService`, `Message.deleteForEveryone`, `AttachmentBlobRepository` |
| MSG-23 tepki | `MessageReactionTest` (8), `ReactionEmojiTest` (23) | `ReactionService`, `ReactionEmoji`, `ReactionRateLimiter`, `FixedWindowRateLimiter`, `ReactionController` |
| MSG-20 iletme | `MessageForwardTest` (10) | `MessageForwardService`, `MessageAppender`, `Message.forwardOf`, `ForwardController` |

Öne çıkan kanıtlar: silinen görsel+açıklamalı mesajın metni geçmiş yanıtında hiç geçmez ve içerik baytları silinir (`herkestenSilinenMesajinIcerigiHicbirYoldanDonmez`); iletilen görselin orijinali silinince kopya indirilebilir kalır, kopya da silinince içerik gider (`gorselIletilinceBaytKopyalanmaz`); sonradan katılan alıntıda eski içeriği ve göndereni göremez (`sonradanKatilanEskiMesajaYanitVeremezAlintiyiGoremez`); son mesajlar gizlenince liste bir öncekini gösterir (`gizlenenSonMesajYerineOncekiGorunurHepsiGizliyseNull`); engelli hedef varsa hiçbir hedefe yazılmaz (`hedeflerdenBiriUygunDegilseHicbiriGonderilmez`); sayfa süslemesi sabit sorgu (`decorateSayfaBasinaSabitSorguYapar`).

## 6. Değişen dosyalar

- `backend/services/messaging-service`: `V135__message_interactions.sql`; domain `Message` (yanıt, iletme sayacı, herkesten silme, `forwardOf`), `GifSnapshot` (`copy`), `MessageAttachment` (`blobId`, `forwarded`, `forwardCopy`), yeni `AttachmentBlob`, `MessageReaction(Id)`, `HiddenMessage(Id)`, `MessagingErrorCode`; uygulama yeni `VisibleMessages`, `MessageViews`, `ReplyPreview`, `ReactionsView`, `ReactionCount`, `MessageAppender`, `MessageHidingService`, `MessageDeletionService`, `ReactionService`, `ReactionEmoji`, `ReactionRateLimiter`, `FixedWindowRateLimiter`, `MessageForwardService`, `ForwardResult`, `MessageChanged`, `MessageChangeDispatcher`, olay yükleri (`MessageDeletedView`, `MessageHiddenView`, `ReactionEventView`, `MessageReactionsView`); değişen `MessageService`, `MessageView`, `MessageDraft`, `MessageCreated`, `MessageStreamDispatcher`, `ConversationListService`, `MessageReadService`, `AttachmentService`, `AttachmentCleanupJob`, `GifSearchRateLimiter`; port `MessageStreamPort`; API yeni `MessageActionController`, `ReactionController`, `ForwardController`, `ReactionRequest`, `ForwardMessageRequest`, `ReplyPreviewResponse`, `ReactionsResponse`, `MessageReactionsResponse`, `ForwardResponse`; değişen `MessagingApiPaths`, `SendMessageRequest`, `MessageResponse`; altyapı `SseMessageHub`, `MessageProperties`, yeni `ReactionProperties`, `MessagingApplicationConfig`, repository'ler (`AttachmentBlobRepository`, `MessageReactionRepository`, `HiddenMessageRepository`, `MessageRepository`, `MessageAttachmentRepository`, `ConversationMemberRepository`); `application.properties`; testler.
- `docs/api.md` MESSAGING (ortak kurallar, gönderim, liste, geçmiş, okuyanlar, akış; yeni: sil, tepki ver/kaldır, tepki listesi, ilet), `docs/database.md` §3.8, `docs/er-diagram.md` §9, `docs/SECURITY.md` §13, `docs/decisions/0001-gercek-zamanli-mesajlasma.md`, `docs-security/messaging-service/NOTLAR.md`, `docs-messaging/TASK-05-TASARIM.md`, `docs-messaging/TASK-05-PLAN.md`.
- Yeni ENV anahtarı yok (`ltz.messaging.messages.delete-for-everyone-window`, `ltz.messaging.reactions.max-per-minute` yalnız `application.properties` varsayılanı).

## 7. Açık noktalar

1. **Frontend yok:** mesaj menüsü (fareyle üzerine gelince ok, dokunmatikte uzun basma, klavyeyle erişim), alıntı görünümü, tepki seçici, iletme hedef seçici, "Bu mesaj silindi" görünümü. İstemci fotoğrafı göndermeden önce ~1600 px'e küçültmeli (sunucu işi ve kullanıcının veri kullanımı azalır).
2. **Tarayıcı + Gateway ile kimlikli** uçtan uca akış doğrulanmadı.
3. **Tepki oran sınırı bellekte** (GIF arama gibi): birden çok örnekte örnek başına. Yayına hazırlık görevinde (çoklu örnek SSE, "99+" okunmamış, bağlantı havuzu, depolama arayüzü) ele alınacak.
4. **Kaçırılan silme/tepki olayları** kalıcı değil; istemci yeniden bağlanınca son sayfayı yeniden çeker. Daha eski sayfalardaki değişiklik o sayfa yeniden açılınca görünür.
5. **Silinen içerik DB yedeklerinde** yedek saklama süresi boyunca kalabilir (yedek politikası ayrı).
6. **Hesap silme iç ucu** hâlâ yok (TASK-02'den): yeni tablolar (`message_reactions.user_id` indeksli, `message_hidden_for_users`) silinecek şekilde tasarlandı.
7. **Görsel depolama** hâlâ DB'de (`attachment_blobs`); nesne depolamaya (S3/MinIO) geçiş ekiple konuşulacak (Umut). Ayrım sayesinde geçiş tek tabloyu etkiler.
8. **Sesli mesaj (LTZ-UMT-MSG-06)** sıradaki görev: Opus (WebM/Ogg) yalnız, ≤ 2 dk, ≤ 1 MB, sunucuda yeniden paketleme (Umut onayı).
