# LTZ-UMT-MSG-03 Okundu Durumları — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Hedef:** Gönderen, kendi mesajlarının `SENT`/`READ` durumunu görür (geçmiş, sohbet listesi, POST yanıtı, SSE).
Okuyanların listesini okuma saatiyle birlikte görür. Okumalar ve ayrılmalar canlı yansır.

**Mimari:** Mevcut okuma işaretçisi (`last_read_seq`) bu işin tek doğruluk kaynağıdır.

- Okuma saati için her gerçek ilerlemede bir aralık satırı yazılır (`message_read_ranges`).
- Durum hesabı tek bir saf fonksiyonda yapılır: su seviyesi `W`.
- İşaretçi ilerletmenin tek giriş noktası `ReadTracker`'dır.
- Alıcıya özel SSE yükleri transaction içinde `ReadReceiptService` tarafından hesaplanır. Bu yükler
  `AFTER_COMMIT` sonrasında, mevcut dispatcher deseniyle gönderilir.

**Teknoloji:** Java (sürüm `backend/pom.xml`'den), Spring Boot, Spring Data JPA/Hibernate, Flyway, PostgreSQL 17, SseEmitter,
Testcontainers, JUnit 5, AssertJ, Awaitility.

**Spec:** [TASK-03-TASARIM.md](TASK-03-TASARIM.md). Bu plan, spec'teki kararları uygular. Uygulayıcı planla
birlikte spec'i de okumalıdır.

## Global Kısıtlar

- Branch `feature/messaging-service`. Claude commit, push ya da merge yapmaz. Commit'i Umut atar; main'e
  merge edilmez.
- Migration numarası `V133` olacak. MESSAGING aralığı V131–V140'tır.
- Kullanıcı kimlikleri başka servise ait REF alanlardır, FK verilmez. Sohbet FK'si `on delete cascade`
  ile tanımlanır.
- Erişilemeyen sohbet için mevcut `404 CONVERSATION_NOT_FOUND` davranışı korunur, var olan ile olmayan
  arasında fark sızdırılmaz.
- SSE olay adı `read`. Yük mesaj içeriği taşımaz ve alıcının kendi mesajlarıyla kırpılır. Ham işaretçi
  ve ham `W` değeri gönderilmez.
- Durum formülü (spec §3.2):
  `W(A) = min over aktif m ≠ A of max(m.last_read_seq, m.visible_from_seq - 1)`. `A` dışında aktif üye
  yoksa `W = 0` olur. `seq ≤ W` ise mesaj `READ`, değilse `SENT` sayılır.
- Okuma saati yalnızca işaretçi gerçekten ilerlediğinde yazılır. `join` ve `rejoin` aralık yazmaz.
- Test sınıfları `IntegrationTestSupport`'u genişletir. Test metot adları Türkçe camelCase olur (mevcut
  desen).
- Javadoc ve yorumlar Türkçe yazılır, yorum yoğunluğu mevcut kodla aynı tutulur.
- Satır kapsamı %97 civarının altına düşmemelidir. Yeni Checkstyle, PMD ya da Sonar bulgusu olmamalıdır.

Test komutu `backend/` dizininden çalıştırılır (Windows'ta `mvnw.cmd`):
`./mvnw -pl services/messaging-service test -Dtest=<Sınıf>`.

## Review Focus

1. **Eski ya da tekrarlanan `seq` ile `POST .../read`.** Örnek: işaretçi 10'dayken `seq = 7`, `10` ya da
   `999` gelirse ve son mesaj 10 ise. Beklenen: aralık satırı yazılmaz ve olay gitmez. Yanıt bugünkü gibi
   kalır. Test: Task 2.
2. **Birebir sohbette iç içe mesajlar.** Örnek: A 1 ve 3'ü, B 2 ve 4'ü gönderir, B `(0, 4]` okur. A'ya
   giden yükte `readUpToSeq = 3` ve `allReadUpToSeq = 3` olmalıdır, 4 olmamalıdır. Test: Task 5.
3. **Tek okumada büyük birikim.** Örnek: 150 mesaj birikmişken tek bir `read` çağrısı yapılır. Beklenen:
   tek aralık satırı yazılır ve alıcı başına tek olay gider. Test: Task 2 (satır sayısı) ve Task 5 (olay
   sayısı).
4. **Gruptan çıkarılmış eski gönderen.** Örnek: eski gönderen kendi eski mesajının `/reads` ucunu çağırır.
   Beklenen: `404 CONVERSATION_NOT_FOUND`. Aktif üye iken başkasının mesajında `403`. Test: Task 4.
5. **Grupta tek başına kalan gönderen.** Beklenen: mesajları `SENT` kalır; sohbet listesindeki son mesaj da
   `SENT` görünür. Test: Task 3.

---

### Task 1: V133 şeması ve okuma aralığı entity'si

**Dosyalar:**
- Oluştur: `src/main/resources/db/migration/V133__create_message_read_ranges.sql`
- Oluştur: `src/main/java/com/ltz/messaging/domain/entity/MessageReadRange.java`
- Oluştur: `src/main/java/com/ltz/messaging/infrastructure/persistence/repository/MessageReadRangeRepository.java`
- Değiştir: `src/test/java/com/ltz/messaging/infrastructure/persistence/FlywayMigrationTest.java`

(Yollar `backend/services/messaging-service/` altındadır; aşağıdaki tasklarda da öyle.)

**Arayüzler:**
- Üretir:
  - Tablo `message_read_ranges(id uuid pk, conversation_id uuid fk→conversations on delete cascade,
    user_id uuid, from_seq bigint, to_seq bigint, read_at timestamptz)`.
  - CHECK kısıtları: `from_seq >= 1` ve `from_seq <= to_seq`.
  - İndeks: `ix_message_read_ranges_member_to (conversation_id, user_id, to_seq)`.
  - `MessageReadRange.of(UUID conversationId, UUID userId, long fromSeq, long toSeq, Instant readAt)`, alan
    getter'larıyla birlikte.
  - `MessageReadRangeRepository extends JpaRepository<MessageReadRange, UUID>`, şu sorguyla:
    `List<MessageReadRange> findCovering(UUID conversationId, Collection<UUID> userIds, long seq)`.
    Koşul `from_seq <= seq <= to_seq`.

- [ ] **Adım 1: Başarısız testi yaz.** `FlywayMigrationTest` içine
  `okumaAraligiKurallariVeSohbetSilinceSilinir` eklenir. Test şunları doğrular:
  - `from_seq = 0` ekleme denemesi `DataIntegrityViolationException` fırlatır.
  - `from_seq = 5, to_seq = 4` ekleme denemesi `DataIntegrityViolationException` fırlatır.
  - Geçerli bir satır eklendikten sonra sohbet silinince satır sayısı 0 olur.

  `migrationUygulanmisVeTablolarVar` testine `message_read_ranges` eklenir. Sınıf Javadoc'u
  "V131/V132/V133" olarak güncellenir.
- [ ] **Adım 2: Çalıştır.** `-Dtest=FlywayMigrationTest`. Beklenen: FAIL, tablo yok.
- [ ] **Adım 3: Kodu yaz.** Migration, entity ve repository yazılır. Migration başlık yorumu V132'deki
  gibi olur ("LTZ-UMT-MSG-03: ..."). Entity `Message.java` desenini izler: fabrika metodu kullanılır,
  setter yazılmaz.
- [ ] **Adım 4: Çalıştır.** Beklenen: PASS.

### Task 2: ReadTracker — işaretçi ilerletmenin tek giriş noktası

**Dosyalar:**
- Oluştur: `src/main/java/com/ltz/messaging/application/service/ReadTracker.java`
- Oluştur: `src/main/java/com/ltz/messaging/application/service/ReadAdvance.java`
- Değiştir: `src/main/java/com/ltz/messaging/application/service/MessageService.java`
  (`markRead` ve `insert`)
- Değiştir: `ConversationMemberRepository.java`. `advanceLastRead` artık kullanılmayacağı için silinir.
- Test: `src/test/java/com/ltz/messaging/api/controller/ReadRangeTest.java`

**Arayüzler:**
- Tüketir: Task 1'deki `MessageReadRange.of` ve `MessageReadRangeRepository`.
- Üretir:
  - `record ReadAdvance(UUID conversationId, UUID readerId, long fromSeq, long toSeq, Instant readAt)`.
  - `ReadTracker.advance(ConversationMember member, long targetSeq, Instant now): Optional<ReadAdvance>`.
    Çağıranın transaction'ına katılır.
  - Task 5 bu metodun içine `ReadReceiptService.onReadAdvanced(ReadAdvance)` çağrısını ekleyecek.

- [ ] **Adım 1: Başarısız testleri yaz** (`ReadRangeTest`).
  - `okumaIlerleyinceTekAralikYazilir`: A 3 mesaj atar, B `markRead(3)` yapar. Beklenen:
    `message_read_ranges` içinde B için tek satır, `(1, 3)`.
  - `ilerlemeyenOkumaSatirYazmaz` (Review Focus 1): B `markRead(3)` yapar, ardından `markRead(2)`,
    `markRead(3)` ve `markRead(999)` yapar (son mesaj 3). Beklenen: B için satır sayısı 1, yanıtlarda
    `lastReadSeq = 3`.
  - `buyukBirikimTekSatir` (Review Focus 3): A 150 mesaj atar. Bunun için `JdbcTemplate` ile doğrudan ekleme
    yapılabilir, ancak `conversations.last_message_seq` da güncellenmelidir. B `markRead(150)` yapar.
    Beklenen: tek satır, `(1, 150)`.
  - `gonderimGonderenIcinAralikYazar`: A 2 mesaj atar, B 1 mesaj atar. Beklenen: B için `(1, 3)` satırı.
    B'nin yanıtladığı mesajlar okunmuş sayılır.
  - `yenidenEklenmeAralikYazmaz`: Grupta B çıkarılır, iki mesaj gelir, B yeniden eklenir. Beklenen: B için
    satır yok. `last_read_seq` değeri ise `rejoin` kuralı nedeniyle artmış olabilir.
  - `eszamanliOkumalarCakismazAralikUretir`: A 20 mesaj atar. B 8 iş parçacığıyla eşzamanlı olarak
    `markRead(1..20)` çağırır; desen MSG-02'deki eşzamanlı gönderim testiyle aynıdır. Beklenen: B'nin
    aralıkları kesişmez, boşluk bırakmaz ve birleşimleri tam olarak `1..20` olur.
- [ ] **Adım 2: Çalıştır.** `-Dtest=ReadRangeTest`. Beklenen: FAIL.
- [ ] **Adım 3: `ReadTracker.advance`'ı yaz.** Adımlar şöyle:
  1. Önce `EntityManager.refresh(member, LockModeType.PESSIMISTIC_WRITE)` çağrılır. Gönderim yolunda üye
     daha önce kilitsiz yüklenmiş olduğundan kilitli sorgu güncel değeri getirmez; bu yüzden `refresh`
     şarttır.
  2. `targetSeq <= member.getLastReadSeq()` ise boş döner.
  3. Değilse `from = eski + 1` alınır, `member.markRead(targetSeq)` çağrılır ve aralık kaydedilir.

  Değişikliklerin `MessageService`'e etkisi:
  - `markRead`: `members.advanceLastRead` yerine `tracker.advance` kullanılır. Yanıt sözleşmesi değişmez.
  - `insert`: `senderMember.markRead(seq)` yerine `tracker.advance(senderMember, seq, now)` kullanılır.
  - Sınıf Javadoc'una MSG-11 eklenir.
- [ ] **Adım 4: Çalıştır.** `-Dtest=ReadRangeTest+MessageSendTest+ConversationListTest`. Beklenen: PASS.
  MSG-13'te regresyon olmamalıdır.

### Task 3: Durum hesabı ve `readStatus` alanı

**Dosyalar:**
- Oluştur: `src/main/java/com/ltz/messaging/domain/enums/ReadStatus.java` (`SENT`, `READ`)
- Oluştur: `src/main/java/com/ltz/messaging/application/service/ReadWatermark.java`
- Değiştir: `MessageView.java`, `MessageResponse.java`, `MessageService.java` (`history` ve `send`),
  `ConversationListService.java`, `ConversationMemberRepository.java`, `SseMessageHub.java` (`deliver`)
- Test: `src/test/java/com/ltz/messaging/application/service/ReadWatermarkTest.java` (birim testi)
  ve `src/test/java/com/ltz/messaging/api/controller/ReadStatusTest.java`

**Arayüzler:**
- Üretir:
  - `final class ReadWatermark` iki statik metotla:
    - `long of(UUID owner, Collection<ConversationMember> activeMembers)`
    - `ReadStatus status(long seq, long watermark)`
  - `MessageView` sonuna `ReadStatus readStatus` alanı eklenir; değer `null` olabilir.
    `MessageView.of(Message)` bu alanı `null` olarak üretir. Yeni metot:
    `MessageView withReadStatus(ReadStatus)`.
  - `MessageResponse` sonuna `ReadStatus readStatus` alanı eklenir.
  - `ConversationMemberRepository.findActiveMembersOf(Collection<UUID> conversationIds)`: verilen
    sohbetlerin tüm aktif üyelerini tek sorguda döner.

- [ ] **Adım 1: Başarısız birim testini yaz** (`ReadWatermarkTest`). Üyeler `ConversationMember.join` ile
  kurulur, `markRead` ile işaretçi ayarlanır.
  - `digerUyeYoksaSifir`: Beklenen `0`.
  - `enGeridekiUyeBelirler`: A sahip. B'nin işaretçisi 5, C'ninki 3. Beklenen `3`.
  - `sonradanKatilanEskiMesajiBloklamaz`: C `visibleFrom = 8` ile katılmış, işaretçisi 0. B'nin işaretçisi
    10. Beklenen `7`.
  - `yenidenEklenenUyeEskiyiOkumusSayilmaz`: Bu durum `rejoin(now, 8)` sonrası `max(7, 7)` ile ölçülür.
    Beklenen `7`.
  - `statusSinirda`: `status(7, 7) = READ`, `status(8, 7) = SENT`, `status(1, 0) = SENT`.
- [ ] **Adım 2: Başarısız API testlerini yaz** (`ReadStatusTest`).
  - `birebirdeOkununcaReadOlur`: A mesaj atar. Geçmişte A'nın mesajı `SENT`, B'nin gözünden
    `readStatus` `null`. B okuyunca A'da `READ` olur.
  - `grupKismiOkumaSentKalir`: A, B ve C var; yalnız B okur. A'da `SENT`, C de okuyunca `READ`.
  - `ayrilanUyeHesaptanDuser`: A, B ve C var; B okur, C gruptan ayrılır. A'da `READ`.
  - `tekBasinaKalanGonderenSent` (Review Focus 5): A ve B var, B ayrılır, A mesaj atar. Geçmişte ve sohbet
    listesinde `lastMessage.readStatus` `SENT`.
  - `listedeYalnizKendiSonMesajimdaDurumVar`: Son mesaj B'nin ise A'nın listesinde `readStatus` `null`.
  - `gonderimYanitiSentDoner`: Yeni gönderim yanıtında `SENT`. Aynı `clientMessageId` ile, okunduktan
    sonra yeniden deneme yapılırsa yanıt `READ`.
  - `uyariliUcDurumOkunduGorunur` (spec §3.2 uç durumu): A ve B var, B okumadan ayrılır, C sonradan
    eklenir. A'nın eski mesajı `READ` görünür.
- [ ] **Adım 3: Çalıştır.** `-Dtest=ReadWatermarkTest+ReadStatusTest`. Beklenen: FAIL.
- [ ] **Adım 4: Kodu yaz.** Değişiklikler:
  - `history`: Sayfada isteyenin kendi mesajı varsa aktif üyeler bir kez çekilir. `W` hesaplanır ve
    yalnızca isteyenin kendi mesajlarına `withReadStatus` uygulanır.
  - `ConversationListService`: Son mesajı isteyene ait olan sohbetler için `findActiveMembersOf` tek sorgu
    olarak çağrılır. Sınıf Javadoc'undaki sabit sorgu listesine bu sorgu eklenir.
  - `send`: Yeni mesajda `SENT` döner. Yeniden denemede durum `W` ile hesaplanır. Bu hesap `findRetry`
    transaction'ı içinde, aynı `ReadWatermark` ile yapılır.
  - `SseMessageHub.deliver`: Alıcı gönderen ise `message.withReadStatus(SENT)`, değilse `message` (alan
    `null`).
- [ ] **Adım 5: Çalıştır.** Bu sınıflar ve `MessageStreamTest`, `MessageHistoryTest` birlikte çalıştırılır.
  Beklenen: PASS.

### Task 4: Mesaj bilgisi ucu (MSG-12)

**Dosyalar:**
- Oluştur: `src/main/java/com/ltz/messaging/application/service/MessageReadService.java`
- Oluştur: `src/main/java/com/ltz/messaging/application/service/MessageReadsView.java`
- Oluştur: `src/main/java/com/ltz/messaging/api/dto/response/MessageReadsResponse.java`
- Değiştir: `MessagingApiPaths.java`, `MessageController.java`, `MessagingErrorCode.java`,
  `MessageRepository.java`
- Değiştir: `src/test/java/com/ltz/messaging/IntegrationTestSupport.java` (yardımcı metot)
- Test: `src/test/java/com/ltz/messaging/api/controller/MessageReadsTest.java`

**Arayüzler:**
- Tüketir: Task 1'deki `findCovering`, Task 3'teki `ReadWatermark`.
- Üretir:
  - Path sabiti `MessagingApiPaths.MESSAGE_READS = MESSAGES + "/{messageId}/reads"`.
  - Yeni hata kodları:
    - `MESSAGE_NOT_FOUND(404, "Message was not found.")`
    - `NOT_MESSAGE_SENDER(403, "Only the sender can see who read this message.")`
  - `MessageReadService.reads(UUID viewer, UUID conversationId, UUID messageId): MessageReadsView`,
    `@Transactional(readOnly = true)` olarak çalışır.
  - Görünüm kayıtları:
    - `record MessageReadsView(UUID messageId, long seq, ReadStatus readStatus, List<Reader> readers,
      List<UUID> pending)`
    - `record Reader(UUID userId, Instant readAt)`; `readAt` `null` olabilir.
  - Yanıt JSON'u spec §4.3'teki gibidir: `pending` alanı `[{userId}]` biçimindedir.
  - Test yardımcısı: `IntegrationTestSupport.messageReads(UUID viewer, UUID conversationId, UUID messageId):
    ResultActions`.

- [ ] **Adım 1: Başarısız testleri yaz** (`MessageReadsTest`).
  - `okuyanlarSaatleOkumayanlarKatilmaSirasiyla`: A, B, C ve D var. Saat `Clock` ya da test saati ile
    ilerletilir. Bu yoksa sıralama `readAt`'in artan sırası ile doğrulanır. B önce, C sonra okur, D okumaz.
    Beklenen:
    - `readers` listesinde `[C, B]` (yeniden eskiye), her birinin `readAt` değeri dolu.
    - `pending` listesinde `[D]`.
    - `readStatus` `SENT`.
  - `sonradanKatilanVeYenidenEklenenListedeYok`: E mesajdan sonra eklenir, B çıkarılıp yeniden eklenir.
    Beklenen: ikisi de ne `readers` ne `pending` listesinde.
  - `gonderenDegilse403`: B, A'nın mesajını sorar. Beklenen: `403` ve kod `NOT_MESSAGE_SENDER`.
  - `uyeDegilseVeCikarilmisGonderen404` (Review Focus 4): Üye olmayan kişi ve kendi eski mesajını soran
    çıkarılmış A için beklenen: `404 CONVERSATION_NOT_FOUND`.
  - `baskaSohbetinMesajiVeGorunmeyenMesaj404`:
    - Başka sohbetin `messageId`'si ile istek yapılır.
    - Var olmayan bir `messageId` ile istek yapılır.
    - Katılmadan önceki bir mesaj istenir. Bu durum gönderen olamayacağı için mantıksal olarak `403`'ten
      önce gelir.

    Üçü için de beklenen: `404 MESSAGE_NOT_FOUND`.
  - `migrationOncesiOkumaSaatsiz`: B'nin aralık satırları `JdbcTemplate` ile silinir. Beklenen: B
    `readers`'ta görünür, `readAt` `null` olur ve liste sonundadır.
  - `birebirdeTekKisi`: Birebir sohbette `readers` ve `pending` toplamı 1 olmalıdır.
- [ ] **Adım 2: Çalıştır.** `-Dtest=MessageReadsTest`. Beklenen: FAIL.
- [ ] **Adım 3: Kodu yaz.** Sıra şöyledir:
  1. `access.requireMember` ile üyelik doğrulanır (404 `CONVERSATION_NOT_FOUND`).
  2. Mesaj bu sohbette yoksa ya da `!member.canSee(seq)` ise 404 `MESSAGE_NOT_FOUND` döner.
  3. Gönderen değilse 403 döner.
  4. Uygun okuyucular aktif üyelerden filtrelenir: `≠ gönderen` ve `visibleFrom ≤ seq`.
  5. `findCovering` tek sorguyla çağrılır.

  Controller'a `GET` eşlemesi eklenir. `SecurityConfig` zaten tüm uçlarda kimlik istiyorsa değişiklik
  gerekmez; bu kontrol edilmelidir.
- [ ] **Adım 4: Çalıştır.** Beklenen: PASS.

### Task 5: SSE `read` olayı (okuma ve gönderimle ilerleme)

**Dosyalar:**
- Oluştur: `src/main/java/com/ltz/messaging/application/service/ReadReceiptService.java`
- Oluştur: `src/main/java/com/ltz/messaging/application/service/ReadReceiptView.java`
- Oluştur: `src/main/java/com/ltz/messaging/application/service/ReadReceiptsCreated.java`
- Oluştur: `src/main/java/com/ltz/messaging/application/service/ReadReceiptDispatcher.java`
- Değiştir: `ReadTracker.java`, `MessageStreamPort.java`, `SseMessageHub.java`, `MessageRepository.java`
- Test: `src/test/java/com/ltz/messaging/api/controller/ReadReceiptStreamTest.java` ve
  `SseMessageHubTest` (yeni olay adı)

**Arayüzler:**
- Tüketir: Task 2'deki `ReadAdvance`, Task 3'teki `ReadWatermark`.
- Üretir:
  - `record ReadReceiptView(UUID conversationId, UUID readerId, Long readUpToSeq, Instant readAt,
    long allReadUpToSeq)`. `readerId`, `readUpToSeq` ve `readAt` alanları yalnızca ayrılma olayında
    `null` olur (Task 6).
  - `record ReadReceiptsCreated(Map<UUID, ReadReceiptView> byRecipient)`.
  - `ReadReceiptService.onReadAdvanced(ReadAdvance advance)`: Transaction içinde çalışır. Alıcıları
    hesaplar ve `ReadReceiptsCreated` olayını yayınlar. Alıcı yoksa olay yayınlanmaz.
  - `MessageStreamPort.publishReadReceipt(UUID recipient, ReadReceiptView receipt)`.
  - `SseMessageHub.READ_EVENT = "read"`.
  - `MessageRepository`'ye iki sorgu eklenir:
    - `List<Object[]> findOwnMaxSeqInRange(UUID conversationId, long fromExclusive, long toInclusive,
      UUID excludedSender)`. Sonuç `[senderId, max(seq)]` biçimindedir.
    - `Optional<Long> findOwnMaxSeqUpTo(UUID conversationId, UUID senderId, long upTo)`.

- [ ] **Adım 1: Başarısız testleri yaz** (`ReadReceiptStreamTest`).
  - `okumaYalnizAraliktaMesajiOlanGondereneGider`: A, B, C ve R var. A 1'i, B 2'yi gönderir. R `(0, 1]`
    okur. Beklenen:
    - A'nın akışında `event:read` var.
    - B'nin ve C'nin akışında yok. B'nin mesajı aralıkta değildir; C'nin hiç mesajı yoktur.
  - `yukAlicininKendiMesajlarinaKirpilir` (spec §5): A 5'i, B 9'u gönderir. Seq'leri oluşturmak için başka
    mesajlar da atılabilir. R `(0, 9]` okur. Beklenen:
    - A'ya `"readUpToSeq":5` gider, `9` hiçbir alanda görünmez.
    - B'ye `"readUpToSeq":9` gider.
    - Herkes okumadıysa `allReadUpToSeq` her alıcı için kendi mesajı ya da `0` olur.
  - `birebirdeIcIceMesajlar` (Review Focus 2): A 1 ve 3'ü, B 2 ve 4'ü gönderir. B `(0, 4]` okur. Beklenen:
    A'ya `readUpToSeq = 3` ve `allReadUpToSeq = 3`.
  - `buyukBirikimAliciBasinaTekOlay` (Review Focus 3): A 150 mesaj atar, B tek seferde okur. Beklenen: A'nın
    akışında tam bir `event:read`.
  - `gonderimdeIlerlemeOlayUretir`: A mesaj atar, B yanıt yazar. Beklenen: A'ya `read` olayı, `readerId = B`.
  - `ilerlemeyenOkumaOlayUretmez`: Aynı `seq` ikinci kez okunur. Beklenen: ikinci olay yok.
  - `geriAlinanIslemdeOlayGitmez`: `TransactionTemplate` içinde `tracker.advance` çağrılır, ardından
    `status.setRollbackOnly()`. Beklenen: A'nın akışında `read` yok.
- [ ] **Adım 2: Çalıştır.** `-Dtest=ReadReceiptStreamTest`. Beklenen: FAIL.
- [ ] **Adım 3: Kodu yaz.** Akış şöyledir:
  1. `ReadTracker.advance`, aralık kaydını yazdıktan sonra `readReceipts.onReadAdvanced(advance)` çağırır.
  2. Alıcılar `findOwnMaxSeqInRange(c, from - 1, to, reader)` ile bulunur ve aktif olmayanlar elenir.
  3. Her alıcı için `readUpToSeq` değeri bu sorgudaki `max` olur.
  4. `allReadUpToSeq = findOwnMaxSeqUpTo(c, alıcı, W(alıcı)).orElse(0)`.

  Dispatcher `MessageStreamDispatcher` desenini izler: `@TransactionalEventListener(AFTER_COMMIT)`. Hub
  tarafında `publishReadReceipt` mevcut yürütücüye `publish` ile aynı şekilde verilir. Kuyruk doluysa
  alıcının akışları kapatılır. Olay `id` taşımaz. `ADR 0001` dosyasında akış olay listesine `read`
  eklenir.
- [ ] **Adım 4: Çalıştır.** `-Dtest=ReadReceiptStreamTest+SseMessageHubTest+MessageStreamTest`.
  Beklenen: PASS.

### Task 6: Ayrılma ve çıkarmada canlı durum

**Dosyalar:**
- Değiştir: `ReadReceiptService.java`, `GroupMembershipService.java` (`leave` ve `removeMember`)
- Test: `ReadReceiptStreamTest.java` (yeni testler)

**Arayüzler:**
- Tüketir: Task 5'teki `ReadReceiptsCreated`, `ReadReceiptView`, `findOwnMaxSeqUpTo` ve Task 3'teki
  `ReadWatermark`.
- Üretir: `ReadReceiptService.onMemberDeparted(UUID conversationId, List<ConversationMember> activeBefore,
  UUID departedUserId)`.

- [ ] **Adım 1: Başarısız testleri yaz.**
  - `ayrilanUyeDurumuReadeCevirirseGondereneGider`: A, B ve C var. A mesaj atar, B okur, C okumadan ayrılır.
    Beklenen:
    - A'ya `event:read` gider. Yükte `"readerId":null` ve `"allReadUpToSeq":<A'nın mesajı>` var.
  - `cikarilanUyeIcinDeAyni`: Yukarıdaki senaryo, `removeMember` ile.
  - `durumDegismeyeneGitmez`: Aynı senaryoda B'nin mesajı yoksa B olay almaz. B'nin mesajı varsa ve C
    zaten okumuşsa da olay gitmez.
  - `ayrilanKisiOlayAlmaz`: C'nin akışında `read` olmamalıdır.
- [ ] **Adım 2: Çalıştır.** Beklenen: FAIL.
- [ ] **Adım 3: Kodu yaz.** Değişiklikler:
  - `leave` ve `removeMember`, kilit altında ve ayrılmadan önce `members.findActiveMembers` ile
    anlık görüntü alır. Ayrılma uygulandıktan sonra `onMemberDeparted` çağrılır.
  - Her kalan aktif üye `A` için önce ve sonra su seviyeleri hesaplanır:
    `W_önce = ReadWatermark.of(A, activeBefore)`, `W_sonra` ise ayrılan çıkarılarak hesaplanır.
  - `W_sonra > W_önce` ise `findOwnMaxSeqUpTo(c, A, W_sonra)` sorgulanır. Sonuç
    `findOwnMaxSeqUpTo(c, A, W_önce)` sonucundan büyükse olay üretilir.
  - Sahip devri ile çağrı sırası arasında bir bağımlılık yoktur, çünkü roller formüle girmez.
- [ ] **Adım 4: Çalıştır.** `-Dtest=ReadReceiptStreamTest+GroupMembershipTest`. Beklenen: PASS.

### Task 7: Belgeler, tam doğrulama ve teslim

**Dosyalar:**
- Değiştir:
  - `docs/api.md` (MESSAGING: `GET .../reads`, `readStatus` alanı, SSE `read` olayı)
  - `docs/database.md` §3.8
  - `docs/er-diagram.md` §9
  - kök `migrations.md`
  - `docs/decisions/0001-gercek-zamanli-mesajlasma.md`
  - `docs-security/messaging-service/NOTLAR.md` (gizlilik kırpması, 403/404 kuralı)
  - `docs/folder-structure.md`, yeni dosyalar listeleniyorsa
- Oluştur: `docs-messaging/TASK-03.md`. Biçim ve bölümler `TASK-02.md` ile aynıdır: gereksinim tablosu,
  akış, kararlar, test eşlemesi, açık noktalar.

- [ ] **Adım 1:** Belgeler güncellenir. `Durum` ve `Test Durumu` gerçeği yansıtmalıdır (AGENTS.md).
  Gateway üzerinden canlı doğrulama yapılmadıysa `NOT VERIFIED` yazılır.
- [ ] **Adım 2:** Servisin tüm testleri ve kapsam ölçümü `./mvnw -pl services/messaging-service verify`
  ile çalıştırılır. Beklenen: tüm testler PASS ve JaCoCo satır kapsamı ≥ %97.
- [ ] **Adım 3:** `.\pre-push\pre-push.cmd` çalıştırılır. Beklenen: `SAFE TO COMMIT`. Docker kapalıysa
  Testcontainers testlerinin koşmadığı açıkça raporlanır.
- [ ] **Adım 4:** SonarQube çalıştırılır. Beklenen: messaging-service bulgusu 0.
- [ ] **Adım 5:** Commit mesajı kod bloğunda önerilir. Biçim `feature: LTZ-UMT-MSG-03 ...` ve madde
  listesidir; atıf satırı eklenmez. Commit atılmaz, merge yapılmaz.
