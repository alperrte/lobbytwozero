# LTZ-UMT-MSG-05 Uygulama Planı — Yanıt, İlet, Tepki, Sil

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** messaging-service'e WhatsApp benzeri yanıt (MSG-19), iletme (MSG-20), tepki (MSG-23), benden sil (MSG-25), herkesten sil (MSG-26) ve menü bilgisi (MSG-24) eklemek.

**Architecture:** Mesajlar fiziksel silinmez; herkesten silme içerik alanlarını temizleyip `deleted_at` koyar. Görsel baytları `attachment_blobs` tablosuna taşınır, `message_attachments` satırları bloba referans verir; iletme yeni bir bağlantı satırı açar, bayt kopyalamaz. Okuma modeli (`MessageView`) izleyene göre süslenir (yanıt önizlemesi, tepki özeti, silme hakkı) — sayfa başına sabit sayıda sorgu. SSE `message` olayı alıcı başına yük taşır; yeni olaylar `message_deleted`, `reaction`, `message_hidden`.

**Tech Stack:** Java 25, Spring Boot (Web MVC, Data JPA, Security), PostgreSQL 17, Flyway, Testcontainers, MockMvc, Awaitility.

**Spec:** `docs-messaging/TASK-05-TASARIM.md`

## Global Constraints

- Çalışma dizini: `backend/services/messaging-service`. Test komutu: `.\mvnw.cmd test -Dtest=<Sınıf>` (Docker açık olmalı; Testcontainers).
- **Commit yok.** Claude commit/push/add yapmaz; task sonunda Umut commit atar. Adımlarda "Commit" yerine testleri koşmak vardır.
- Migration: yalnız `V135__message_interactions.sql` (MESSAGING aralığı V131–V140). Önceki migration'lar değiştirilmez.
- Yeni bağımlılık eklenmez (emoji doğrulaması JDK: `BreakIterator`, `Character.isExtendedPictographic`).
- Herkesten silme süresi: `ltz.messaging.messages.delete-for-everyone-window=PT48H`.
- İletme hedefi: 1–5, tekrar eden hedef `400`.
- "Çok kez iletildi" eşiği: `forward_count >= 5`.
- Tepki: kişi başına mesaj başına 1; en fazla 32 UTF-16 birimi; `ltz.messaging.reactions.max-per-minute=60`.
- Alıntı `snippet`: gövdenin ilk 100 UTF-16 birimi, vekil çifti bölünmez.
- Görülemeyen / başka sohbetin / olmayan mesaj her uçta aynı `404 MESSAGE_NOT_FOUND`.
- İçerik, emoji ve dosya baytı loglanmaz.
- Test adları mevcut stil: Türkçe camelCase cümle (`herkestenSilinenMesajinIcerigiHicbirYoldanDonmez`).
- Kalite: `pre-push messaging-service` geçmeli (JaCoCo kapsam ve değişen satır eşikleri, Checkstyle/PMD yeni ihlal 0).

## Review Focus

1. **Herkesten silinmiş mesaja yanıt veren eski istemci** — `replyToMessageId` silinmiş mesajı gösteriyor → `422 MESSAGE_DELETED`, mesaj ve `seq` oluşmaz. (Task 2'ye test)
2. **İletilen görselin orijinali herkesten silinir** — kopyanın görseli hâlâ indirilebilir, orijinal sohbetteki URL `404`. (Task 6'ya test)
3. **Silme ile tepki/iletme yarışı** — silme sonrası gelen tepki/iletme `422`; silinmiş mesajda tepki satırı kalmaz. (Task 4 ve 5'e test: silindikten sonra `PUT reaction` → 422 ve `message_reactions` satırı 0)
4. **Kullanıcı son mesajlarını benden siler** — sohbet listesi `lastMessage` gizlenmemiş bir önceki mesajı gösterir, hepsi gizliyse `null`; okunmamış sayısı gizlenenleri saymaz. (Task 3'e test)
5. **Ayrılıp yeniden katılan üye** — eski mesajlara tepki veremez, iletemez, alıntı önizlemesi `unavailable`. (Task 2 ve 5'e test)

---

### Task 1: Şema V135 ve görsel blob ayrımı

Davranış değişmez; mevcut 227 test geçmeye devam eder.

**Files:**
- Create: `src/main/resources/db/migration/V135__message_interactions.sql`
- Create: `src/main/java/com/ltz/messaging/domain/entity/AttachmentBlob.java`
- Create: `src/main/java/com/ltz/messaging/domain/entity/MessageReaction.java`, `MessageReactionId.java`
- Create: `src/main/java/com/ltz/messaging/domain/entity/HiddenMessage.java`, `HiddenMessageId.java`
- Create: `src/main/java/com/ltz/messaging/infrastructure/persistence/repository/AttachmentBlobRepository.java`, `MessageReactionRepository.java`, `HiddenMessageRepository.java`
- Modify: `domain/entity/Message.java`, `domain/entity/GifSnapshot.java`, `domain/entity/MessageAttachment.java`
- Modify: `application/service/AttachmentService.java`, `application/service/AttachmentCleanupJob.java`
- Modify: `infrastructure/persistence/repository/MessageAttachmentRepository.java`
- Test: `src/test/java/com/ltz/messaging/infrastructure/persistence/FlywayMigrationTest.java`, `ServiceBoundaryTest.java`, `AttachmentUploadTest.java`

**Interfaces:**
- Produces (DB):
  - `attachment_blobs(id uuid PK, size_bytes int, content bytea, created_at timestamptz)`; CHECK `size_bytes between 1 and 1048576 and octet_length(content) = size_bytes`. Mevcut içerik `id = message_attachments.id` ile taşınır.
  - `message_attachments`: `content` ve `ck_message_attachments_size`'ın `octet_length` kısmı kalkar (`size_bytes` 1–1048576 CHECK'i kalır); `blob_id uuid not null` FK → `attachment_blobs(id)` **ON DELETE RESTRICT**, `forwarded boolean not null default false`; `create index ix_message_attachments_blob on message_attachments (blob_id)`. Yükleme oranı indeksi `ix_message_attachments_uploader_created` kısmi olur: `where forwarded = false`.
  - `messages`: `reply_to_message_id uuid` FK → `messages(id)` ON DELETE SET NULL; `forward_count int not null default 0` CHECK ≥ 0; `deleted_at timestamptz`, `deleted_by uuid` (CHECK ikisi birlikte dolu/boş). `ck_messages_text_body` → `type <> 'TEXT' or body is not null or deleted_at is not null`. `ck_messages_gif` → GIF alanları yalnız `type = 'GIF' and deleted_at is null` iken dolu ve geçerli, aksi halde hepsi NULL. Yeni CHECK `ck_messages_deleted_content`: `deleted_at is null or body is null`.
  - `message_reactions(message_id uuid FK → messages ON DELETE CASCADE, user_id uuid, emoji varchar(32) not null, reacted_at timestamptz not null, PK (message_id, user_id))`, CHECK `char_length(emoji) between 1 and 32`; `create index ix_message_reactions_user on message_reactions (user_id)` (hesap silme için).
  - `message_hidden_for_users(user_id uuid, message_id uuid FK → messages ON DELETE CASCADE, hidden_at timestamptz not null, PK (user_id, message_id))`; `create index ix_message_hidden_message on message_hidden_for_users (message_id)`.
- Produces (Java):
  - `Message`: yeni alanlar `UUID replyToMessageId`, `int forwardCount`, `Instant deletedAt`, `UUID deletedBy`; `boolean isDeleted()`. `body` ve `GifSnapshot` sütunları `updatable = true` olur (Task 4 temizler). Fabrikalar şimdilik değişmez.
  - `AttachmentBlob.store(byte[] content, Instant now)`; `MessageAttachment.upload(..., UUID blobId, ...)` (artık `content` almaz), `MessageAttachment.forwardCopy(UUID conversationId, UUID uploaderId, UUID messageId, AttachmentMeta source, Instant now)` → `forwarded = true`, aynı `blobId`.
  - `AttachmentMeta`'ya `UUID blobId` eklenir.
  - `MessageAttachmentRepository.findContent(UUID id)` blob join ile aynı imzayla kalır; `countByUploaderIdAndForwardedFalseAndCreatedAtAfter(UUID, Instant)` oran sınırında eskisinin yerine.
  - `AttachmentBlobRepository.deleteIfOrphan(UUID blobId) : int` (native: `delete from attachment_blobs b where b.id = :blobId and not exists (select 1 from message_attachments a where a.blob_id = b.id)`), `deleteOrphansBefore(Instant cutoff) : int`.
  - `MessageReactionRepository`, `HiddenMessageRepository`: şimdilik boş `JpaRepository`; sorgular sonraki tasklarda eklenir.

- [ ] **Step 1: Failing testleri yaz** — `FlywayMigrationTest`'e: `v135TablolarVeKisitlarKurulur` (üç yeni tablo var; `messages` üzerinde `deleted_at` dolu + `body` dolu satır eklemek `ck_messages_deleted_content` ile reddedilir; aynı `(message_id, user_id)` ile ikinci tepki PK ile reddedilir; `attachment_blobs` satırı referans varken silinemez). `AttachmentUploadTest`'e: `yuklenenGorselBlobTablosundaTekKezSaklanir` (yüklemeden sonra `select count(*) from attachment_blobs` = 1 ve `message_attachments.blob_id` o satır). `ServiceBoundaryTest`'teki tablo listesine yeni tablolar.
- [ ] **Step 2: Koş, düştüğünü gör** — `.\mvnw.cmd test -Dtest=FlywayMigrationTest+AttachmentUploadTest+ServiceBoundaryTest` → FAIL (tablolar yok).
- [ ] **Step 3: Migration + entity + repository değişikliklerini uygula.** Yükleme aynı transaction'da önce blob'u sonra bağlantı satırını yazar. `AttachmentCleanupJob`, bağlanmamış satırları sildikten sonra `deleteOrphansBefore(now - pendingTtl)` çağırır ve yalnız sayıları loglar.
- [ ] **Step 4: Tüm messaging testlerini koş** — `.\mvnw.cmd test` → hepsi PASS (227 + yeni).

---

### Task 2: İzleyene göre okuma modeli, alıntılı yanıt (MSG-19) ve alıcı başına SSE yükü

**Files:**
- Create: `application/service/MessageViews.java`, `ReplyPreview.java`, `ReactionsView.java`, `ReactionCount.java`
- Modify: `application/service/MessageView.java`, `MessageDraft.java`, `MessageService.java`, `ConversationListService.java`, `MessageCreated.java`, `MessageStreamDispatcher.java`
- Modify: `application/port/MessageStreamPort.java`, `infrastructure/messaging/realtime/SseMessageHub.java`
- Modify: `api/dto/request/SendMessageRequest.java`, `api/dto/response/MessageResponse.java`; Create: `api/dto/response/ReplyPreviewResponse.java`, `ReactionsResponse.java`
- Modify: `domain/entity/Message.java`, `domain/exception/MessagingErrorCode.java`, `infrastructure/config/MessageProperties.java`, `src/main/resources/application.properties`
- Modify: `infrastructure/persistence/repository/MessageReactionRepository.java`, `HiddenMessageRepository.java`
- Test: Create `src/test/java/com/ltz/messaging/api/controller/MessageReplyTest.java`, `src/test/java/com/ltz/messaging/application/service/MessageViewsTest.java`; Modify `MessageStreamTest.java`, `SseMessageHubTest.java`, `IntegrationTestSupport.java`

**Interfaces:**
- Consumes: Task 1 şeması ve `Message` alanları.
- Produces:
  - `record ReplyPreview(UUID messageId, Long seq, UUID senderId, MessageType type, String snippet, boolean deleted, boolean unavailable)` — fabrikalar `visible(Message)`, `deleted(Message)`, `unavailable(UUID)`; `static String snippet(String body)` (100 birim, vekil bölünmez).
  - `record ReactionCount(String emoji, long count)`; `record ReactionsView(List<ReactionCount> summary, String mine)`, `ReactionsView.EMPTY`.
  - `MessageView` yeni bileşenler: `ReplyPreview replyTo, int forwardCount, boolean deleted, ReactionsView reactions, Instant deletableForEveryoneUntil`; yeni `withReplyTo`, `withReactions`, `withDeletableUntil`; mevcut `with*` yöntemleri yeni alanları korur. `MessageView.of(Message)` `forwardCount` ve `deleted`'ı mesajdan alır, `reactions = ReactionsView.EMPTY`.
  - `MessageViews` (@Component): `List<MessageView> decorate(UUID viewer, Map<UUID, ConversationMember> membershipByConversation, List<MessageView> views)` — sırayla (1) `MessageAttachments.attachTo`, (2) alıntılar: `replyToMessageId`'ler tek `findAllById` + izleyenin gizlediklerinden tek sorgu (`HiddenMessageRepository.findHiddenAmong(UUID userId, Collection<UUID> messageIds) : Set<UUID>`), (3) tepkiler: `MessageReactionRepository.summarize(Collection<UUID> messageIds) : List<Object[]> [messageId, emoji, count, min(reactedAt)]` ve `findMine(UUID userId, Collection<UUID> messageIds) : List<Object[]> [messageId, emoji]`, (4) `deletableForEveryoneUntil`. Silinmiş mesajda alıntı/tepki/görsel eklenmez. Sorgu sayısı mesaj sayısından bağımsızdır.
  - `MessageView` alıntı ve silme hakkı için `Message`'a erişim gerektirir: `MessageView`'a `UUID replyToMessageId` bileşeni de eklenir (JSON'a çıkmaz).
  - Silme hakkı kuralı (tek yer): `static Instant MessageViews.deletableUntil(MessageView m, ConversationMember viewerMembership, Duration window, Instant now)` → silinmişse `null`; gönderen izleyense ya da izleyenin rolü `OWNER`/`ADMIN` ise (bu roller yalnız grupta vardır) `createdAt + window`, ama bu an `now`'dan önceyse `null`.
  - `MessageDraft` yeni bileşen `UUID replyToMessageId` (sona eklenir); `MessageDraft.text(String)` `null` geçer. `SendMessageRequest`'e `UUID replyToMessageId`.
  - `Message.text/image/gif` fabrikalarına `UUID replyToMessageId` parametresi (sohbet kimliğinden sonra değil, `clientMessageId`'den sonra); `isRetryOf`'a `UUID otherReplyTo` eklenir ve eşitlik ister.
  - `MessageCreated(Map<UUID, MessageView> perRecipient)` — alıcı → yük. `MessageStreamPort.publish(Map<UUID, MessageView> perRecipient)` eski `publish(Collection, MessageView)`'in yerine; hub gönderene `SENT` eklemeye devam eder. Yeni genel yöntem `void publishEvent(String eventName, Map<UUID, ?> payloadByRecipient)` (Task 3–5 kullanır); olay adları `MessageStreamPort` sabitleri: `DELETED_EVENT = "message_deleted"`, `REACTION_EVENT = "reaction"`, `HIDDEN_EVENT = "message_hidden"`. Bu olaylar `id` taşımaz.
  - `MessagingErrorCode.MESSAGE_DELETED(422, "This message was deleted.")`.
  - `MessageProperties` yeni bileşen `@NotNull Duration deleteForEveryoneWindow`.
  - `MessageResponse` yeni alanlar: `replyTo` (`ReplyPreviewResponse`: `messageId, seq, senderId, type, snippet, deleted, unavailable`; `null` değerli alanlar JSON'da yok), `forwarded` (`forwardCount >= 1`), `forwardedManyTimes` (`forwardCount >= 5`), `deleted`, `reactions` (`ReactionsResponse(List<{emoji,count}> summary, String mine)`), `deletableForEveryoneUntil`.
  - `IntegrationTestSupport.sendReply(UUID sender, UUID conversationId, UUID replyTo, String body) : ResultActions`.

- [ ] **Step 1: Failing testleri yaz** — `MessageReplyTest`:
  - `yanitAlintiOnizlemesiyleDoner`: alice "Merhaba"; bob ona yanıt → 201, `$.replyTo.messageId`, `$.replyTo.snippet == "Merhaba"`, `$.replyTo.senderId == alice`; geçmişte ve listede aynı.
  - `uzunGovdeninOnizlemesi100BirimdeKesilirVekilBolunmez`: 99 `a` + "😀" + 10 `b` → `snippet` 99 `a` (101. birim emoji'nin ikinci yarısı olacağından emoji alınmaz).
  - `baskaSohbetinMesajinaYanitVerilemez` → 404 `MESSAGE_NOT_FOUND`, `seq` ilerlemez.
  - `sonradanKatilanEskiMesajaYanitVeremezAlintiyiGoremez`: grupta carol katılmadan önceki mesaja carol yanıtı → 404; alice'in eski mesaja yanıtı carol'ın geçmişinde `$.replyTo.unavailable == true` ve `senderId`/`snippet` yok.
  - `herkestenSilinmisMesajaYanitVerilemez` (Review Focus 1): mesajı doğrudan SQL ile silinmiş işaretle (`update messages set deleted_at=now(), deleted_by=?, body=null`) → yanıt 422 `MESSAGE_DELETED`, mesaj sayısı değişmez.
  - `yanitYenidenDenemesindeFarkliHedefCakisir`: aynı `clientMessageId`, farklı `replyToMessageId` → 409 `CLIENT_MESSAGE_ID_REUSED`.
  - `gonderenIcinSilmeHakkiKirkSekizSaatlikSonuDoner`: gönderim yanıtında `deletableForEveryoneUntil == createdAt + 48h`; alıcının geçmişinde `null`; grupta ADMIN alıcı için dolu.
  - `MessageViewsTest.decorateSayfaBasinaSabitSorguYapar` (birim, `src/test/java/com/ltz/messaging/application/service/`): Mockito repository'leriyle 50 mesajlık sayfada `summarize`, `findMine`, `findAllById`, `findHiddenAmong` birer kez çağrılır.
  - `MessageStreamTest.yanitAkistaAliciyaGoreAlintiTasir`: sonradan katılan alıcının akışında `"unavailable":true`, eski üyenin akışında `"snippet"`.
- [ ] **Step 2: Koş, düştüğünü gör** — `.\mvnw.cmd test -Dtest=MessageReplyTest+MessageStreamTest` → FAIL.
- [ ] **Step 3: Uygula.** `MessageService.insert` hedefi aynı transaction'da `findByIdAndConversationId` ile yükler; görünmüyorsa (`!member.canSee(seq)` ya da gönderen gizlemişse) `MESSAGE_NOT_FOUND`, silinmişse `MESSAGE_DELETED`. Alıcı başına yük: alıcıların üyelikleri zaten çekiliyor; yanıt hedefi için `findHiddenAmong` yerine hedefin gizleyenleri tek sorgu (`HiddenMessageRepository.findUsersWhoHid(UUID messageId) : Set<UUID>`), sonra her alıcı için `ReplyPreview` ve `deletableUntil` bellekte hesaplanır. `history`, `findRetry`, `ConversationListService.visibleLast` `MessageViews.decorate` kullanır (liste için `membershipByConversation` = mevcut `memberships`).
- [ ] **Step 4: Tüm testleri koş** — `.\mvnw.cmd test` → PASS.

---

### Task 3: Benden sil (MSG-25)

**Files:**
- Create: `application/service/MessageHidingService.java`, `api/controller/MessageActionController.java`, `application/service/MessageHiddenView.java`
- Modify: `api/controller/MessagingApiPaths.java`, `MessageRepository.java`, `ConversationMemberRepository.java`, `HiddenMessageRepository.java`, `MessageService.java` (geçmiş), `ConversationListService.java` (son mesaj), `MessageReadService.java`, `AttachmentService.java`
- Test: Create `src/test/java/com/ltz/messaging/api/controller/MessageHideTest.java`; Modify `IntegrationTestSupport.java`

**Interfaces:**
- Consumes: `MessageStreamPort.publishEvent`, `HIDDEN_EVENT` (Task 2).
- Produces:
  - `MessagingApiPaths.MESSAGE = MESSAGES + "/{messageId}"`.
  - `DELETE /conversations/{id}/messages/{messageId}?scope=me|everyone` → `MessageActionController.delete(...)`: `scope` yoksa ya da başka değerse `400` (`MessagingErrorCode.INVALID_DELETE_SCOPE(400, "scope must be 'me' or 'everyone'.")`). Bu task `me`'yi bağlar; `everyone` Task 4'te.
  - `MessageHidingService.hide(UUID user, UUID conversationId, UUID messageId) : void` — idempotent; görünmeyen mesaj 404; native `insert ... on conflict do nothing` (`HiddenMessageRepository.hide(UUID userId, UUID messageId, Instant at) : int`); 1 satır eklendiyse commit sonrası `message_hidden` → yalnız `user`, yük `MessageHiddenView(UUID conversationId, UUID messageId)`.
  - `MessageRepository.findOlder/findNewer` yeni parametre `UUID viewer` ve `not exists (select 1 from HiddenMessage h where h.id.userId = :viewer and h.id.messageId = m.id)`.
  - `MessageRepository.findLastVisibleMessages(UUID userId, Collection<UUID> conversationIds) : List<Message>` — native, `conversation_members` üzerinden `join lateral (... where m.conversation_id = cm.conversation_id and m.seq >= cm.visible_from_seq and not exists (gizli) order by m.seq desc limit 1)`; `ConversationListService` `findLastMessages` yerine bunu kullanır (taraması kullanıcının kendi gizlediği satır sayısıyla sınırlıdır).
  - `ConversationMemberRepository.countUnread` gizlenenleri saymaz (aynı `not exists`).
  - `MessageReadService.reads` ve `AttachmentService.download` (bağlı görselde) gizleyene `404`.
  - `IntegrationTestSupport.deleteMessage(UUID actor, UUID conversationId, UUID messageId, String scope) : ResultActions`.

- [ ] **Step 1: Failing testleri yaz** — `MessageHideTest`:
  - `bendenSilinenMesajYalnizSileninGecmisindenKalkar`: bob alice'in mesajını gizler → 204; bob'un geçmişinde yok, alice'in geçmişinde var; alice'in `readStatus`'u değişmez.
  - `bendenSilmeIdempotentVeKendiMesajindaDaCalisir`: iki kez 204; kendi mesajı için de 204.
  - `gizlenenSonMesajYerineOncekiGorunurHepsiGizliyseNull` (Review Focus 4): 3 mesaj; bob son ikisini gizler → listede `lastMessage` ilk mesaj; hepsini gizler → `lastMessage` yok.
  - `gizlenenOkunmamisSayilmaz`: alice 3 mesaj; bob 1'ini gizler → `unreadCount == 2`.
  - `gizlenenGorselVeOkuyanlarGizleyeneKapanir`: bob görsel mesajını gizler → `download` 404; alice kendi mesajını gizler → `reads` 404.
  - `gorulemeyenMesajGizlenemez`: başka sohbetin mesajı → 404; geçersiz `scope=x` → 400 `INVALID_DELETE_SCOPE`.
  - `gizlemeYalnizSileninAkislarinaGider`: bob'un akışında `event:message_hidden`, alice'in akışında yok.
- [ ] **Step 2: Koş, düştüğünü gör** — `.\mvnw.cmd test -Dtest=MessageHideTest` → FAIL.
- [ ] **Step 3: Uygula.**
- [ ] **Step 4: Tüm testleri koş** — `.\mvnw.cmd test` → PASS.

---

### Task 4: Herkesten sil (MSG-26)

**Files:**
- Create: `application/service/MessageDeletionService.java`, `MessageDeletedView.java`, `MessageDeleted.java` (iç olay), `MessageDeletionDispatcher.java`
- Modify: `domain/entity/Message.java`, `MessageRepository.java`, `MessageAttachmentRepository.java`, `MessageReactionRepository.java`, `HiddenMessageRepository.java`, `MessageActionController.java`, `MessagingErrorCode.java`
- Test: Create `src/test/java/com/ltz/messaging/api/controller/MessageDeleteForEveryoneTest.java`

**Interfaces:**
- Consumes: Task 1 blob ayrımı, Task 2 `deletableUntil` kuralı ve `DELETED_EVENT`, Task 3 `DELETE ...?scope=`.
- Produces:
  - `Message.deleteForEveryone(UUID actor, Instant now)` — `body = null`, `gif = null`, `deletedAt`, `deletedBy`.
  - `MessageRepository.findByIdAndConversationIdForUpdate(UUID id, UUID conversationId) : Optional<Message>` (`@Lock(PESSIMISTIC_WRITE)`), `findByIdAndConversationIdForShare(...)` (`@Lock(PESSIMISTIC_READ)`; Task 5–6 kullanır).
  - `MessageAttachmentRepository.findBlobIdByMessageId(UUID) : Optional<UUID>`, `deleteByMessageId(UUID) : int`.
  - `MessageReactionRepository.deleteByMessageId(UUID) : int`.
  - `HiddenMessageRepository.findUsersWhoHid(UUID messageId) : Set<UUID>` (Task 2'de eklendi).
  - `MessageDeletionService.deleteForEveryone(UUID actor, UUID conversationId, UUID messageId) : void`: tek transaction — üye; mesaj `FOR UPDATE`; görünmüyor/gizlemiş → 404; zaten silinmiş → dönüş (204); `MessageViews.deletableUntil(...) == null` ise: gönderen ya da yönetici değilse `403 NOT_ALLOWED_TO_DELETE`, süre dolmuşsa `422 DELETE_WINDOW_EXPIRED`; `deleteForEveryone`; görsel bağlantısı silinir ve `AttachmentBlobRepository.deleteIfOrphan`; tepkiler silinir; commit sonrası `message_deleted` → mesajı görebilen ve gizlememiş aktif üyeler, yük `MessageDeletedView(UUID conversationId, UUID messageId, long seq, Instant deletedAt)`.
  - Hata kodları: `NOT_ALLOWED_TO_DELETE(403, "You cannot delete this message for everyone.")`, `DELETE_WINDOW_EXPIRED(422, "This message can no longer be deleted for everyone.")`.

- [ ] **Step 1: Failing testleri yaz** — `MessageDeleteForEveryoneTest`:
  - `herkestenSilinenMesajinIcerigiHicbirYoldanDonmez`: görsel+açıklamalı mesaj, bob tepki vermiş, carol yanıt vermiş; alice siler → 204; bob'un geçmişinde `deleted == true`, `body`/`attachment`/`gif` yok, `reactions.summary` boş; carol'ın yanıtında `replyTo.deleted == true` ve `snippet` yok; `download` 404; `select count(*) from attachment_blobs` 0; `message_reactions` 0; listede `lastMessage.deleted == true`.
  - `gifMesajiSilinceGifAlanlariTemizlenir`.
  - `siraVeOkunmamisBozulmaz`: silmeden önce/sonra bob'un `unreadCount` ve sonraki mesajın `seq`'i aynı.
  - `yalnizGonderenYaDaGrupYoneticisiSilebilir`: birebirde karşı taraf → 403; grupta MEMBER başkasınınkini → 403; ADMIN ve OWNER başkasınınkini → 204; ADMIN sahibin mesajını → 204.
  - `kirkSekizSaatSonraSilinemez`: `update messages set created_at = now() - interval '49 hours'` → 422 `DELETE_WINDOW_EXPIRED`; gönderim yanıtındaki `deletableForEveryoneUntil` ile tutarlı.
  - `tekrarSilmeIdempotent` → ikinci kez 204.
  - `silinenMesajaYanitVerilemez`: API ile silindikten sonra yanıt → 422 `MESSAGE_DELETED` (tepki ve iletme karşılıkları Task 5 ve 6'da).
  - `silmeOlayiGorebilenlereGider`: bob'un akışında `event:message_deleted` ve `"messageId"`; mesajı gizlemiş üyeye gitmez.
- [ ] **Step 2: Koş, düştüğünü gör** — `.\mvnw.cmd test -Dtest=MessageDeleteForEveryoneTest` → FAIL.
- [ ] **Step 3: Uygula.**
- [ ] **Step 4: Tüm testleri koş** — `.\mvnw.cmd test` → PASS.

---

### Task 5: Tepkiler (MSG-23)

**Files:**
- Create: `application/service/ReactionService.java`, `ReactionEmoji.java`, `ReactionRateLimiter.java`, `FixedWindowRateLimiter.java`, `ReactionEventView.java`, `MessageReactionsView.java`, `ReactionChanged.java`, `ReactionDispatcher.java`
- Create: `api/controller/ReactionController.java`, `api/dto/request/ReactionRequest.java`, `api/dto/response/MessageReactionsResponse.java`
- Create: `infrastructure/config/ReactionProperties.java`
- Modify: `GifSearchRateLimiter.java` (ortak `FixedWindowRateLimiter`'ı kullanır, davranış aynı), `MessageReactionRepository.java`, `MessagingApiPaths.java`, `MessagingErrorCode.java`, `MessagingApplicationConfig.java` (properties kaydı), `application.properties`
- Test: Create `src/test/java/com/ltz/messaging/api/controller/MessageReactionTest.java`, `src/test/java/com/ltz/messaging/application/service/ReactionEmojiTest.java`; Modify `GifSearchRateLimiterTest.java` (geçmeye devam eder)

**Interfaces:**
- Consumes: Task 2 `ReactionsView`, `REACTION_EVENT`; Task 4 `findByIdAndConversationIdForShare`.
- Produces:
  - `MessagingApiPaths.MESSAGE_REACTION = MESSAGE + "/reaction"`, `MESSAGE_REACTIONS = MESSAGE + "/reactions"`.
  - `ReactionEmoji.validate(String raw) : String` — geçerliyse aynı değeri döner, değilse `MessagingException(INVALID_REACTION)`. Kural: null değil; uzunluk 1–32 UTF-16 birimi; eşlenmemiş vekil yok; `BreakIterator.getCharacterInstance(Locale.ROOT)` ile tam bir grafem; harf/rakam (keycap dışında), boşluk, kontrol karakteri yok; en az bir kod noktası `Character.isExtendedPictographic` ya da bölgesel gösterge (U+1F1E6–U+1F1FF) ya da U+20E3 (keycap).
  - `FixedWindowRateLimiter(IntSupplier limit, Clock clock)` — `boolean tryAcquire(UUID)`, `void evictExpired()`; `GifSearchRateLimiter` ve `ReactionRateLimiter` (@Component, `@Scheduled` tahliye) sarar.
  - `ReactionProperties(@Min(1) int maxPerMinute)` — `ltz.messaging.reactions.max-per-minute=60`.
  - `MessageReactionRepository.upsert(UUID messageId, UUID userId, String emoji, Instant at) : int` (native `on conflict (message_id, user_id) do update`), `deleteByMessageIdAndUserId(UUID, UUID) : int`, `findByMessageIdOrderByReactedAtDesc(UUID) : List<MessageReaction>`.
  - `ReactionService.react(UUID user, UUID conversationId, UUID messageId, String emoji) : ReactionsView`, `unreact(...) : void`, `list(...) : MessageReactionsView(UUID messageId, List<{UUID userId, String emoji, Instant reactedAt}>)`. Sıra: emoji doğrula → oran sınırı (`429 TOO_MANY_REACTIONS`) → transaction: üye, mesaj `FOR SHARE`, görünürlük/gizleme → 404, silinmiş → 422 `MESSAGE_DELETED` → yaz → commit sonrası `reaction` olayı: mesajı görebilen ve gizlememiş aktif üyelere, yük `ReactionEventView(UUID conversationId, UUID messageId, List<ReactionCount> summary, String mine)` alıcı başına `mine` ile. Kaldırmada değişiklik yoksa olay yok. `list` silinmiş mesajda boş liste döner.
  - Hata kodları: `INVALID_REACTION(400, "A reaction must be a single emoji.")`, `TOO_MANY_REACTIONS(429, "Too many reactions. Wait a moment and try again.")`.
  - Uçlar: `PUT .../reaction {emoji}` → 200 `ReactionsResponse`; `DELETE .../reaction` → 204; `GET .../reactions` → 200.

- [ ] **Step 1: Failing testleri yaz** —
  - `ReactionEmojiTest` (birim): geçerli `"❤️"`, `"👍🏽"`, `"👨‍👩‍👧‍👦"`, `"🇹🇷"`, `"1️⃣"`; geçersiz `"a"`, `"👍👍"`, `" "`, `"❤️ "`, `""`, `"\uD83D"`, 33 birimlik dizi, `"<b>"`.
  - `MessageReactionTest`:
    - `tepkiVerilirDegistirilirKaldirilir`: bob ❤️ → özet `[{❤️,1}]`, `mine ❤️`; bob 👍 → özet `[{👍,1}]`; kaldır → boş; geçmişte aynı.
    - `ozetSayiyaGoreSiralanirKendiTepkimAyridir`: 3 kişi 👍, 1 kişi ❤️ → ilk 👍 3; carol için `mine` kendi tepkisi.
    - `tepkiListesiniGorebilenHerUyeGorur`: liste 3 satır, `reactedAt` azalan; üye olmayan 404.
    - `gecersizEmoji400`: `"ab"` → 400 `INVALID_REACTION`.
    - `dakikadaAltmisSiniri`: 60 değişiklik 200, 61. → 429 `TOO_MANY_REACTIONS` (test properties'te sınır düşürülebilir).
    - `silinmisMesajaTepkiVerilemezSatirKalmaz` (Review Focus 3): herkesten sil sonra `PUT` → 422 `MESSAGE_DELETED`; `message_reactions` 0.
    - `sonradanKatilanEskiMesajaTepkiVeremez` (Review Focus 5) → 404.
    - `tepkiOlayiAliciyaGoreMineTasir`: alice'in akışında `event:reaction` ve `"mine":null`, bob'un akışında `"mine":"❤️"`.
- [ ] **Step 2: Koş, düştüğünü gör** — `.\mvnw.cmd test -Dtest=ReactionEmojiTest+MessageReactionTest` → FAIL.
- [ ] **Step 3: Uygula.**
- [ ] **Step 4: Tüm testleri koş** — `.\mvnw.cmd test` → PASS (`GifSearchRateLimiterTest` dahil).

---

### Task 6: İletme (MSG-20)

**Files:**
- Create: `application/service/MessageForwardService.java`, `api/controller/ForwardController.java` (ya da `MessageActionController`'a), `api/dto/request/ForwardMessageRequest.java`, `api/dto/response/ForwardResponse.java`
- Modify: `domain/entity/Message.java`, `MessageService.java` (ortak ekleme yardımcıları paylaşılır, çoğaltılmaz), `MessagingApiPaths.java`, `MessagingErrorCode.java`, `MessageAttachmentRepository.java`
- Test: Create `src/test/java/com/ltz/messaging/api/controller/MessageForwardTest.java`

**Interfaces:**
- Consumes: Task 1 `MessageAttachment.forwardCopy`, Task 2 alıcı başına `MessageCreated`, Task 4 `findByIdAndConversationIdForShare`, `ConversationAccessPolicy.requireCanWrite`.
- Produces:
  - `MessagingApiPaths.MESSAGE_FORWARD = MESSAGE + "/forward"`.
  - `ForwardMessageRequest(@NotBlank @Size(max=64) @Pattern("[A-Za-z0-9_-]+") String clientMessageId, @NotNull @Size(min=1, max=5) List<@NotNull UUID> targetConversationIds)`; tekrar eden hedef → `400 DUPLICATE_FORWARD_TARGET`.
  - `Message.forwardOf(Message source, UUID conversationId, long seq, UUID sender, String clientMessageId, Instant now)` — tür/gövde/GIF kopyalanır, `replyToMessageId = null`, `forwardCount = source.forwardCount + 1`.
  - Hedef başına istemci kimliği: `"fw-" + UUID.nameUUIDFromBytes((clientMessageId + ":" + sourceMessageId + ":" + targetId).getBytes(UTF_8))`.
  - `MessageForwardService.forward(UUID user, UUID sourceConversationId, UUID sourceMessageId, String clientMessageId, List<UUID> targets) : ForwardResult(List<MessageView> messages, boolean created)`. Sıra: (1) transaction: kaynak görünür/gizli değil (404), silinmemiş (422 `MESSAGE_DELETED`); tüm hedeflerde türetilmiş kimlikle mesaj varsa onları döner (`created=false`, 200); (2) her hedef için `access.requireCanWrite(target, user)` transaction dışında — biri başarısızsa hiçbir şey yazılmaz; (3) tek transaction: kaynak `FOR SHARE` ve silinmemiş; hedefler kimliğe göre sıralı kilitlenir (`findByIdForUpdate`); her hedefte üyelik, oran sınırı (son 1 dk + bu istekteki hedef sayısı > `maxPerMinute` → 429 `TOO_MANY_MESSAGES`), yeni `seq`, mesaj, IMAGE ise `forwardCopy` bağlantısı (bayt kopyası yok), `tracker.advance`, outbox `MessageSent`, alıcı başına `MessageCreated`.
  - Yanıt: yeni oluşturulduysa 201, yeniden denemeyse 200; gövde `ForwardResponse(List<MessageResponse> messages)` hedef sırasıyla.
  - Hata kodları: `DUPLICATE_FORWARD_TARGET(400, "Each target conversation can be used once.")`.

- [ ] **Step 1: Failing testleri yaz** — `MessageForwardTest`:
  - `mesajBesSohbeteIletilirIletildiEtiketiyle`: alice'in metni bob'a; bob 2 sohbete iletir → 201, iki mesaj, `forwarded == true`, `forwardedManyTimes == false`, `senderId == bob`, `replyTo` yok; hedef sohbetlerin geçmişinde görünür.
  - `besIletimdenSonraCokKezIletildi`: zincir 5 kez iletilir → 5. kopya `forwardedManyTimes == true`.
  - `altiHedefVeTekrarEdenHedef400`.
  - `hedeflerdenBiriUygunDegilseHicbiriGonderilmez`: hedeflerden biri bob'un üye olmadığı sohbet → 404, diğer hedefte mesaj yok; engelli birebir hedef → 403 `MESSAGING_NOT_ALLOWED`, hiç mesaj yok.
  - `gorselIletilinceBaytKopyalanmaz` (Review Focus 2): görsel mesajını ilet → `attachment_blobs` sayısı 1; hedefteki `attachment.url` hedef sohbet yolu ve indirilebilir; orijinal herkesten silinir → kopya hâlâ indirilebilir, orijinal URL 404, `attachment_blobs` hâlâ 1; kopya da silinir → 0.
  - `iletmeYenidenDenemesiCiftMesajUretmez`: aynı istek iki kez → ikinci 200, aynı mesaj kimlikleri.
  - `silinmisYaDaGorulemeyenMesajIletilemez` (Review Focus 3, 5): silinmiş kaynak → 422; sonradan katılanın eski mesajı → 404.
  - `iletmeOranSinirinaSayilir`: test sınırıyla `maxPerMinute` aşılırsa 429 ve hiç mesaj yok.
  - `iletilenGorselYuklemeOranSinirinaSayilmaz`: 10 görsel iletildikten sonra iletenin yeni yüklemesi 201 (iletilen satırlar `forwarded = true`).
- [ ] **Step 2: Koş, düştüğünü gör** — `.\mvnw.cmd test -Dtest=MessageForwardTest` → FAIL.
- [ ] **Step 3: Uygula.** `MessageService.insert` içindeki "seq al → kaydet → işaretçi → outbox → olay" adımları paylaşılan bir yardımcıya taşınır; iletme onu kullanır.
- [ ] **Step 4: Tüm testleri koş** — `.\mvnw.cmd test` → PASS.

---

### Task 7: Belgeler ve teslim kontrolü

**Files:**
- Modify: `docs/api.md` (MESSAGING: gönderim kaydına `replyToMessageId` ve yeni yanıt alanları; yeni kayıtlar: mesaj sil, tepki ver/kaldır, tepki listesi, ilet; SSE olayları), `docs/database.md` §3.8 (V135; öneri satırlarını gerçek tablolarla değiştir, `forwarded_from_id` yerine `forward_count` notu), `docs/er-diagram.md` §9, `docs/SECURITY.md` §13 (herkesten silme, yedek saklama notu, tepki oran sınırı), `docs/decisions/0001-gercek-zamanli-mesajlasma.md` (yeni olaylar; kaçırılan silme/tepki için son sayfayı yeniden çekme), `docs-security/messaging-service/NOTLAR.md`, `docs/folder-structure.md` (yeni dosya yoksa dokunma)
- Create: `docs-messaging/TASK-05.md` (TASK-04.md biçimi: sonuç, doğrulama tablosu, akış, kararlar, güvenlik, gereksinim→test→dosya, değişen dosyalar, açık noktalar — frontend menüsü ve istemcide ~1600 px küçültme notu dahil)

- [ ] **Step 1:** Belgeleri güncelle; uç yolları `MessagingApiPaths` ile birebir.
- [ ] **Step 2:** `.\pre-push\pre-push.cmd messaging-service` (repo kökünden; `AUTH_SERVICE_URL=http://auth-service:8080`) → `BACKEND TEST CHECK PASSED`. Sonucu `TASK-05.md` doğrulama tablosuna dürüstçe yaz.
- [ ] **Step 3:** `pre-push full dev` ve SonarQube: Node 24.7+ varsa koş; yoksa "NOT RUN" ve nedeni yaz. Umut'a commit mesajını öner (kod bloğu, `feature: LTZ-UMT-MSG-05 ...` + madde listesi, atıf satırı yok).
