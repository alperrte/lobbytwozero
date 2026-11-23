# LTZ-UMT-MSG-03 — Okundu Durumları ve Mesajı Görenler (TASK-03)

> Tarih: 2026-11-02 · Sorumlu: Umut · Servis: `messaging-service` · Dal: `feature/messaging-service` · Kapsam: MSG-11, MSG-12 · Durum: **kod, testler ve belgeler tamam; `pre-push full dev` SAFE TO COMMIT; SonarQube messaging-service bulgusu 0 (Quality Gate yalnız mail/hardware bulgularından dolayı FAIL); Gateway üzerinden canlı akış doğrulanmadı**
>
> Endpoint sözleşmeleri `docs/api.md` → `## MESSAGING`; `read` olayı kararı `docs/decisions/0001-gercek-zamanli-mesajlasma.md`; tasarım `docs-messaging/TASK-03-TASARIM.md`, uygulama planı `docs-messaging/TASK-03-PLAN.md`.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| MSG-11 Okundu durumu | IMPLEMENTED (servis içi) | `SENT`/`READ`; geçmiş, sohbet listesi ve gönderim yanıtında yalnız isteyenin kendi mesajında; canlı güncelleme SSE `read` olayıyla. Gateway üzerinden canlı: NOT VERIFIED |
| MSG-12 Mesajı görenler | IMPLEMENTED | `GET .../messages/{messageId}/reads`: okuyanlar okuma saatiyle, okumayanlar; yalnız mesajın göndereni görür |

Doğrulama (`mvn -pl services/messaging-service verify`, `backend/` dizininden, 2026-11-02):

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, PostgreSQL 17) | PASS — 164 test, 0 hata, 0 atlanan (MSG-01/02 testleri dahil; bu görevde 48 yeni test) |
| JaCoCo | PASS — genel satır kapsamı %97,87 (871 / 890 satır) |
| Değişen satır kapsamı (coverage-policy) | PASS — %98,53 (334 / 339 satır) |
| Checkstyle | PASS — 0 ihlal |
| PMD | PASS — yeni ihlal yok (yalnız `AvoidCatchingNPE` kural kaldırma uyarıları, önceden de var) |
| `.\pre-push\pre-push.cmd messaging-service` (2026-11-02) | **BACKEND TEST CHECK PASSED** — 164 test, JaCoCo %97,87, Checkstyle/PMD yeni ihlal 0 |
| `.\pre-push\pre-push.cmd full dev` (2026-11-02, `AUTH_SERVICE_URL=http://auth-service:8080` ile) | **SAFE TO COMMIT** — Git/Secret, Backend Tests, Build, Coverage, Code Quality (yeni ihlal), Docker Configuration ve Backend HTTP Health (18 servis) PASS. Ön koşullar: DEV stack `docker compose --env-file .env.dev build` (paralellik 3) + `up -d`; yerel `auth_db` V61–V64 birleştirmesi nedeniyle `docs-auth/migration-yazim-kurallari.md`'ye göre yedeklenip sıfırlandı; yerel `.env.dev`'e `MAIL_PAYLOAD_ENCRYPTION_KEY` üretildi (yalnız yerel ortam, repo değişikliği yok) |
| SonarQube (`sonar.cmd scan dev`, 2026-11-02, `AUTH_SERVICE_URL=http://auth-service:8080` ile) | Tüm backend testleri PASS, analiz yapıldı. **messaging-service açık bulgu 0** (ilk taramadaki `S1905` gereksiz `(UUID)` dönüşümü ve iskelet testteki `S2699`/`S1186` giderildi). social-service'teki 2 `S6831` hatası (`JwtConfig` gereksiz `@Qualifier`) da giderildi; social'da hata/açık 0, 25 eski kod kokusu kaldı. Quality Gate **FAIL** — yalnız diğer servislerden: güvenlik D (mail-service S4502), güvenilirlik C (hardware-service S2259, mail-service S2583); genel kapsam %92,8, yeni kod %93,4, tekrar %3,0. Atanmış gate yapılandırması HTTP 403 (NOT VERIFIED) |
| Gateway üzerinden SSE `read` olayı, gerçek Auth + tarayıcı ile uçtan uca | NOT VERIFIED |

## 2. Akış

**Okuma** `POST /conversations/{id}/read {seq}` (sözleşme değişmedi):
1. Aktif üye denetlenir; `seq` sohbetin son mesajıyla sınırlanır.
2. `ReadTracker.advance` (tek giriş noktası): üye satırı `PESSIMISTIC_WRITE` ile (PostgreSQL'de `FOR NO KEY UPDATE`; `FOR UPDATE` olsaydı `message_read_ranges` FK eklemesinin aldığı `KEY SHARE` ile çakışıp kilitlenme çıkarırdı) kilitlenip yeniden okunur; işaretçi ilerlemiyorsa (aynı `seq`, geri gitme, son mesajın ötesi) **hiçbir şey yazılmaz**.
3. İlerlediyse aynı transaction'da `message_read_ranges` satırı `[eski last_read_seq + 1, yeni last_read_seq]` + okuma saati yazılır ve işaretçi güncellenir.
4. `ReadReceiptService` alıcı başına kırpılmış `read` yüklerini hazırlar, `ReadReceiptsCreated` olayı yayınlanır.
5. Commit sonrası (`AFTER_COMMIT`, `ReadReceiptDispatcher`): yük, ilgili göndericilerin açık SSE akışlarına iletilir. Geri alınan transaction'da olay gitmez.

**Gönderim:** gönderenin işaretçisi kendi mesajına ilerlerken de aynı `ReadTracker.advance` kullanılır (aralık + `read` olayı: yanıt yazan, önündeki mesajları okumuş sayılır). `join`/`rejoin` işaretçiyi taşısa da aralık yazmaz ve olay üretmez; bunlar gerçek okuma değildir.

**Durum hesabı:** gönderen `A` için su seviyesi `W(A) = min over aktif m ≠ A of max(m.last_read_seq, m.visible_from_seq - 1)`; `A` dışında aktif üye yoksa `W = 0`. `seq ≤ W` ise `READ`, değilse `SENT`. Formül tek yerde (`ReadWatermark`) yazılıdır; geçmiş, sohbet listesi, gönderim yanıtı, `/reads` ve `read` olayı aynı hesabı kullanır.

**Mesaj bilgisi** `GET /conversations/{id}/messages/{messageId}/reads`: üyelik (404 `CONVERSATION_NOT_FOUND`) → mesaj bu sohbette ve üyeye görünür (404 `MESSAGE_NOT_FOUND`) → gönderen (403 `NOT_MESSAGE_SENDER`). Uygun okuyucular (aktif, gönderen değil, `visible_from_seq ≤ seq`) `readers` (okuma saatiyle, yeniden eskiye, saati bilinmeyenler sonda) ve `pending` (katılma sırasıyla) olarak ayrılır; `readAt`, mesajı kapsayan aralığın zamanıdır.

**Ayrılma/çıkarma:** `GroupMembershipService.leave`/`removeMember` kilit altında aktif üye görüntüsünü alır, ayrılmayı uygular ve `onMemberDeparted` ile her kalan göndericinin su seviyesini önce/sonra karşılaştırır. Mesajı `SENT`'ten `READ`'e geçen göndericilere `read` olayı gider (`readerId`, `readUpToSeq`, `readAt` `null`; yalnız `allReadUpToSeq` dolu). Ayrılan kişi olay almaz.

## 3. Kararlar ve varsayımlar

| Konu | Karar | Neden |
| --- | --- | --- |
| Durumlar | `SENT` ve `READ`; "görüldü" = "okundu" (Umut'un kararı) | Cihaza iletim takip edilmiyor; çift tik hedef dışı |
| Grupta `READ` koşulu | O anda aktif ve mesajı görebilen herkes okuyunca | Sonradan katılan ve ayrılan kişi mesajı bloklamamalı |
| Okuma saati | Mesaj başına satır yerine **okuma aralığı** (`message_read_ranges`, V133) | Satır sayısı gerçek ilerleme sayısıyla büyür; 150 mesajlık birikim tek satır. Öneri tablosundaki `message_reads` (mesaj başına) kullanılmadı |
| Aralık yazımı | Yalnız işaretçi gerçekten ilerlediğinde (`markRead`, gönderim); `join`/`rejoin` yazmaz | `rejoin` işaretçiyi yapay ileri taşır, okuma sayılmaz (anti-hedef) |
| Eşzamanlılık | `ReadTracker.advance` üye satırını `PESSIMISTIC_WRITE` ile kilitleyip yeniden okur | Aynı kullanıcının eşzamanlı okumaları çakışan/boşluklu aralık üretmez; kilitsiz sürümde test 3/3 başarısız oldu |
| Sonradan katılan üye | `visible_from_seq - 1` terimi formüle girer | `rejoin`/`join` işaretçiyi taşısa bile mesajı göremeyen biri okuyucu sayılmaz |
| Alıcı kırpması | `read` yükü alıcının kendi mesajlarıyla kırpılır; ham işaretçi ve ham su seviyesi gönderilmez | Aksi hâlde A, R'nin B'nin mesajını okuduğunu öğrenir (anti-hedef #1) |
| Aralık alt sınırı kırpması | Alıcı aralığı `fromExclusive = max(fromSeq, visibleFromSeq) - 1` ile kırpılır | Yeni katılan üyenin ilk aralığı 1'den başlar; görmediği mesajlar için gönderene sahte makbuz gitmesin |
| `/reads` durumu | `readStatus`, geçmişle aynı `ReadWatermark` formülünden; listelerden türetilmez | Uç durumda (tüm uygun okuyucular ayrılmış, yerlerine yalnız sonradan katılanlar gelmiş; spec §3.2) `READ` ve boş listeler, geçmişle çelişmez; testle sabit |
| Eski veriler | V133 öncesi okunmuş mesajlarda `readAt` `null`; geçmişe dönük doldurma yok | Geliştirme verisi; `null`'lar `readers` listesinde en sonda |
| Ayrılan üyenin satırları | Aralık satırları silinmez | Listede kullanılmaz (aktif değil); sohbet silinince cascade |
| Hata kodları | Yeni `NOT_MESSAGE_SENDER` (403) ve `MESSAGE_NOT_FOUND` (404) | Gönderen olmayan, görmediği/başka sohbetin mesajının varlığını öğrenemez |
| `read` olayı kanalı | Mevcut SSE akışı, olay adı `read`, outbox/notification yok | Okundu bilgisi bildirim değil; ADR 0001 |
| Kök `migrations.md` | **Değiştirilmedi** | Yalnız sürüm aralıklarını listeler; V133 MESSAGING aralığı V131–V140 içinde. MSG-02 de dokunmadı. Asıl kural belgesi `docs/decisions/migrations.md` değişmedi |

## 4. Güvenlik

| Konu | Uygulama |
| --- | --- |
| Erişim | `/reads` yalnız aktif üye + mesajın göndereni; gönderen olmayan aktif üye 403, üye olmayan (eski üye dahil) 404, başka sohbetin/yok/görünmeyen mesaj 404 (fark sızdırılmaz) |
| Başkasının okuması | `readStatus` yalnız isteyenin kendi mesajında dolu; `message` olayında gönderen `SENT`, diğerleri `null` |
| Akış gizliliği | `read` olayı yalnız aralıkta mesajı olan, aktif göndericiye gider; yük alıcı başına kırpılır, içerik taşımaz, `id` yoktur |
| Kaynak sınırları | Okuma çağrısı en çok bir satır üretir ve yalnız işaretçi ilerlerse; sayfa için üyeler tek sorguyla çekilir (mesaj başına sorgu yok); kuyruk dolarsa yalnız o alıcının akışları kapanır (MSG-02 deseni) |
| Gizlilik | Mesaj içeriği loglanmaz; `read` yükü içerik taşımaz |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| Şema (V133) | `FlywayMigrationTest` (8; +1), `MessageReadRangeRepositoryTest` (1) | `V133__create_message_read_ranges.sql`, `MessageReadRange`, `MessageReadRangeRepository` |
| MSG-11 aralık yazımı, eşzamanlılık | `ReadRangeTest` (6) | `ReadTracker`, `ReadAdvance`, `MessageService.markRead/insert` |
| MSG-11 durum hesabı | `ReadWatermarkTest` (5), `ReadStatusTest` (7), `MessageStreamTest` (7; +1) | `ReadWatermark`, `ReadStatus`, `MessageService.history`, `ConversationListService`, `MessageView` |
| MSG-12 mesaj bilgisi | `MessageReadsTest` (8) | `MessageReadService`, `MessageReadsView`, `MessageReadsResponse`, `MessageController` |
| MSG-11 canlı güncelleme (okuma, gönderim, ayrılma/çıkarma) | `ReadReceiptStreamTest` (16), `SseMessageHubTest` (7; +3) | `ReadReceiptService`, `ReadReceiptView`, `ReadReceiptsCreated`, `ReadReceiptDispatcher`, `SseMessageHub`, `GroupMembershipService` |

Öne çıkan kanıtlar: A/B/R gizlilik senaryosu (`yukAlicininKendiMesajlarinaKirpilir`: A seq 5, B seq 9, R `(0, 9]` okur → A `readUpToSeq = 5`), kilitsiz sürümde `eszamanliOkumalarCakismazAralikUretir` 3/3 başarısız, kırpma kaldırılınca `yeniKatilanGoremedigiMesajlarIcinOlayUretmez` başarısız, geri alınan transaction'da olay yok (`geriAlinanIslemdeOlayGitmez`), spec §3.2 uç durumu (`tumUygunOkuyucularAyrildiysaReadVeListelerBos`).

## 6. Değişen dosyalar

- `backend/services/messaging-service`: `V133__create_message_read_ranges.sql`; `MessageReadRange`, `ReadStatus`; `ReadTracker`, `ReadAdvance`, `ReadWatermark`, `ReadReceiptService`, `ReadReceiptView`, `ReadReceiptsCreated`, `ReadReceiptDispatcher`, `MessageReadService`, `MessageReadsView`, `MessageReadsResponse`; `MessageReadRangeRepository`; `MessageService`, `MessageView`/`MessageResponse` (`readStatus`), `ConversationListService`, `GroupMembershipService`, `MessageController`, `MessagingApiPaths`, `MessagingErrorCode`, `MessageStreamPort`, `SseMessageHub`, `MessageRepository`, `ConversationMemberRepository` (`advanceLastRead` kaldırıldı, `findActiveMembersOf` eklendi); testler (`MessagingServiceApplicationTests` iskelet testine assertion eklendi).
- `backend/services/social-service`: `JwtConfig` — `@Bean` metotlarındaki gereksiz `@Qualifier` kaldırıldı (Sonar `S6831`); bean adları metot adlarından gelir, `SecurityConfig`'teki `@Qualifier` eşleşmesi aynı kalır, davranış değişmedi (218 test PASS).
- `docs/api.md` MESSAGING (yeni `.../reads` kaydı; `readStatus`, `read` olayı), `docs/database.md` §3.8, `docs/er-diagram.md` §9, `docs/decisions/0001-gercek-zamanli-mesajlasma.md`, `docs-security/messaging-service/NOTLAR.md`, `docs-messaging/TASK-03-TASARIM.md` (Durum satırı).
- `docs/folder-structure.md` değişmedi: yeni sınıflar mevcut paketlerde. Kök `migrations.md` değişmedi (bkz. §3).
- ENV değişikliği yok.

## 7. Açık noktalar

1. **Eşzamanlı okumada canlı olay eski olabilir.** İki üye aynı mesajı eşzamanlı okursa her işlem `allReadUpToSeq`'i diğerinin henüz commit edilmemiş işaretçisiyle hesaplar; iki canlı olay da eski değeri taşıyabilir ve gönderen, bir sonraki geçmiş/liste çekimine kadar mesajı `SENT` görür. Spec §5 olayı en iyi çaba sayar, `readStatus` her istekte yeniden hesaplanır; hesabı commit sonrasına taşımak ihtiyaç olursa ayrı iş (ADR 0001'e de yazıldı).
2. **Gateway + tarayıcı ile `read` olayı** canlı doğrulanmadı (TASK-02 açık nokta 1 ile aynı: SSE tamponlama, kalp atışı/zaman aşımı).
3. **Migration öncesi okumalar için `readAt` `null`.** Geçmişe dönük doldurma yapılmadı; kalıcı veride gerekirse ayrı bir veri düzeltmesi gerekir.
4. **Saklama/temizlik yok.** `message_read_ranges` büyür (ilerleme sayısıyla); ayrılan üyelerin satırları kalır. Saklama süresi kararı ve temizlik görevi yok (hesap silme iç ucu ile birlikte ele alınmalı, TASK-02 açık nokta 4).
5. **Kapsam dışı (tasarım §2):** okundu bilgisini kapatma ayarı, "cihaza iletildi" aşaması, okumanın outbox/notification'a gitmesi, kullanıcının diğer cihazlarına okunmamış sayısı eşitleme, "yazıyor…".
6. **Küçük performans notu:** ayrılma/çıkarma `onMemberDeparted` her kalan gönderici için su seviyesini hesaplar ve kilit altında en çok 2N sorgu yapar; üye sınırı (100) ile sınırlıdır, ölçüm gerekirse optimize edilir.
7. **Frontend** bu görevde yok (`readStatus` göstergesi, mesaj bilgisi paneli, `read` olayı işleme web tarafında yazılmalı).
8. **Durum canlı olay olmadan değişebilir.** Son diğer üye ayrılınca `W = 0` olur (spec §3.2) ve mesaj `READ`'den `SENT`'e dönebilir; yeni katılan da eski mesajları `READ`'e çevirebilir (kabul edilen §3.2 kenar durumu). İkisinde de canlı olay yoktur; istemci güncel değeri bir sonraki geçmiş/liste çekiminde alır.
9. **`findCovering` ölçeği.** `from_seq <= seq <= to_seq` süzer, indeks `(conversation_id, user_id, to_seq)`; uzun sohbette eski bir mesaj için okuyucu başına sonraki tüm aralıkları tarar. Mevcut ölçekte yeterli; gerekirse `DISTINCT ON (user_id) … ORDER BY user_id, to_seq` ile yeniden yazılabilir.
10. **SonarQube Quality Gate** yalnız mail-service (S4502, S2583) ve hardware-service (S2259) bulguları nedeniyle FAIL; bu servislere bu görevde dokunulmadı (Umut'un kararı). social-service'teki 25 eski kod kokusu ayrı bir social işinde ele alınmalı.
