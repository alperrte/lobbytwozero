# LTZ-UMT-MSG-02 — Gerçek Zamanlı Mesaj Gönderimi ve Sohbet Listesi (TASK-02)

> Tarih: 2026-11-01 · Sorumlu: Umut · Servis: `messaging-service` · Dal: `feature/messaging-service` · Kapsam: MSG-06, MSG-07, MSG-08, MSG-09, MSG-10, MSG-13, MSG-16, MSG-27 · Durum: **kod, testler ve belgeler tamam; Gateway üzerinden canlı akış ve bildirim teslimi doğrulanmadı**
>
> Endpoint sözleşmeleri `docs/api.md` → `## MESSAGING`; gerçek zamanlı taşıma kararı `docs/decisions/0001-gercek-zamanli-mesajlasma.md`.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| MSG-06 Metin mesajı gönderme | IMPLEMENTED | `clientMessageId` ile idempotent; oran sınırı 30/dk |
| MSG-07 Mesaj geçmişi | IMPLEMENTED | `seq` ile sayfalı; `before` geriye, `after` eşitleme |
| MSG-08 Sohbet listesi | IMPLEMENTED | Son etkinlikten eskiye, keyset; son mesaj + okunmamış |
| MSG-09 Gerçek zamanlı mesajlaşma | IMPLEMENTED (servis içi) | SSE (`/stream`); kayıpsızlık `seq` + `after` ile. Gateway üzerinden canlı: NOT VERIFIED |
| MSG-10 Mesaj bilgileri | IMPLEMENTED | Gönderen, oluşturulma zamanı, sohbet içi sıra, istemci kimliği |
| MSG-13 Okunmamış sayısı | IMPLEMENTED | Okuma işaretçisi (`POST .../read`), listede sohbet bazlı sayı |
| MSG-16 Mesaj bildirimleri | IN PROGRESS | `MessageSent` mesajla aynı transaction'da outbox'a yazılıyor; teslim PENDING (notification ucu ve Auth çifti yok) |
| MSG-27 Servis sınırları | IMPLEMENTED (bu görevin payı) | Mükerrer mesaj yok, kayıt bildirimden bağımsız, başka DB'ye erişim yok. Admin moderasyon entegrasyonu ayrı görev |

Doğrulama:

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, PostgreSQL 17) | PASS — 116 test (MSG-01 testleri dahil) |
| JaCoCo | PASS — satır kapsamı %97,54 |
| Checkstyle / PMD yeni ihlal | PASS — 0 |
| `.\pre-push\pre-push.cmd messaging-service` | **BACKEND TEST CHECK PASSED** |
| `.\pre-push\pre-push.cmd full dev` | **Koşulamadı** — Gitleaks kuruldu (`install-tool.mjs`), ama DEV imajları Docker içinde Maven Central DNS çözümleme hatasıyla ("Temporary failure in name resolution", 3 deneme) derlenemedi; ortam sorunu |
| SonarQube (`sonar.cmd scan dev`, 2026-11-02) | Tüm backend testleri PASS, analiz yapıldı. **messaging-service bulgusu 0** (ilk taramadaki 9 kod kokusu giderildi). Quality Gate **FAIL** — diğer servislerden: güvenlik D (mail-service S4502), güvenilirlik C (hardware/mail/social hataları), genel tekrar %3,0. Not: review-service testleri `AUTH_SERVICE_URL` ortam değişkeni ister; tarama bu süreçte `http://auth-service:8080` verilerek koşuldu |
| Gateway üzerinden SSE, gerçek Auth + Social ile uçtan uca | NOT VERIFIED |

## 2. Akış

**Gönderim** `POST /conversations/{id}/messages {clientMessageId, body}`:
1. Aynı `(gönderen, clientMessageId)` ile kayıtlı mesaj varsa → aynı içerik ve sohbetse **200** ve o mesaj (yeni mesaj/olay yok), değilse **409**.
2. Yazma yetkisi (`ConversationAccessPolicy.requireCanWrite`, MSG-01'de hazırlandı): aktif üye; birebirde Social engel sorgusu (transaction dışında, 403/503).
3. Transaction: sohbet satırı `FOR UPDATE` → üyelik ve yeniden deneme tekrar denetlenir → oran sınırı → `seq = last_message_seq + 1` → mesaj, gönderenin okuma işaretçisi, `MessageSent` outbox satırı → commit.
4. Commit sonrası (`AFTER_COMMIT`): gönderim anındaki aktif üyelerin açık SSE akışlarına iletim (ayrı yürütücü).

**Eşitleme:** istemci akış koptuğunda ya da yeniden bağlandığında `GET .../messages?after=<son seq>`. Sıra, kilit altında verildiği için commit sırasıyla aynıdır; `seq > X` sorgusu arada commit edilmiş mesajı kaçırmaz (8 eşzamanlı gönderimle boşluksuz `1..8` testli).

**Görünürlük:** üyenin `visible_from_seq`'i katıldığı andaki `last_message_seq + 1`'dir (kilit altında). Geçmiş, liste ve okunmamış sayısı bu sınırı uygular; çıkarılıp geri eklenen kişi aradaki mesajları görmez.

## 3. Kararlar ve varsayımlar

| Konu | Karar | Neden |
| --- | --- | --- |
| Gerçek zamanlı taşıma | SSE + `after` eşitlemesi (kullanıcı kararı, ADR 0001) | Gateway WebMVC WebSocket proxy'lemez; SSE düz HTTP, `EventSource` çerezle çalışır |
| Grup geçmişi | Yalnız katıldıktan sonrası (kullanıcı kararı) | WhatsApp davranışı; eski konuşma yeni üyeye sızmaz |
| Okunmamış modeli | Tek okuma işaretçisi (`last_read_seq`); mesaj gönderen o mesaja kadar okumuş sayılır | Ucuz ve tutarlı sayım; mesaj başına okundu (MSG-11/12) ayrı görev |
| Mesaj uzunluğu | 4000 karakter; istek gövdesi 32 KB | Çok baytlı karakterler (emoji, Türkçe) için pay |
| İçerik | Olduğu gibi saklanır, HTML temizlenmez; kontrol karakteri (satır sonu/sekme hariç) reddedilir | İstemci düz metin gösterir; sunucu HTML üretmez |
| Oran sınırı | 30 mesaj/dk/kullanıcı (DB sayımı) | SECURITY.md §12 "mesaj gönderimi" profili |
| Akış ömrü | Token bitişi (en çok 30 dk) | Askıya alınan hesap uzun süre akış almasın |
| Bildirim | Outbox'a yaz, teslim etme | Alıcı uç ve Auth çifti yok; relay boşuna kod olurdu |
| `before`/`after` | Opak imleç değil, `seq` sayısı | `seq` zaten herkese açık mesaj alanı; istemci için basit |

## 4. Güvenlik

| Konu | Uygulama |
| --- | --- |
| Erişim | Tüm mesaj uçları `ConversationAccessPolicy` ile: aktif üye değilse 404; birebirde engel varsa yazma 403 |
| Akış | Kimlik zorunlu; yalnız üyesi olunan sohbetlerin mesajları; kullanıcı başına 5 akış; token bitişinde kapanır; yalnız commit edilmiş mesajlar |
| Idempotency | `UQ (sender_id, client_message_id)` + kilit altında yeniden denetim; farklı içerikle yeniden kullanım 409 |
| Gizlilik | Mesaj içeriği loglanmaz; outbox olayı içerik/önizleme taşımaz |
| Kaynak sınırları | Sayfa boyutları sunucuda; iletim kuyruğu sınırlı (1000) ve dolunca akış kapanır; gövde 32 KB |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| MSG-06, MSG-10 | `MessageSendTest` (12) | `MessageService.send`, `Message`, `SendMessageRequest` |
| MSG-07 | `MessageHistoryTest` (5) | `MessageService.history`, `MessageRepository` |
| MSG-08 | `ConversationListTest` (6) | `ConversationListService`, `ActivityCursor` |
| MSG-09 | `MessageStreamTest` (6), `SseMessageHubTest` (4) | `SseMessageHub`, `MessageStreamDispatcher`, `MessageStreamController` |
| MSG-13 | `ConversationListTest`, `ConversationTest` | `MessageService.markRead`, `ConversationMemberRepository.countUnread/advanceLastRead` |
| MSG-16 | `MessageSendTest.olayIcerikTasimaz...` | `OutboxRecorder`, `MessageSentEvent`, `OutboxEvent` |
| MSG-27 | `MessageSendTest` (idempotency, eşzamanlılık), `ServiceBoundaryTest` | — |
| Şema | `FlywayMigrationTest` (7) | `V132__create_messages_and_outbox.sql` |

## 6. Değişen dosyalar

- `backend/services/messaging-service`: `V132__create_messages_and_outbox.sql`; `Message`, `OutboxEvent`, `MessageType`, `MessageSentEvent`; `Conversation`/`ConversationMember` yeni alanlar; `MessageService`, `ConversationListService`, `MessageStreamService`, `MessageStreamDispatcher`, `OutboxRecorder`; `SseMessageHub` (`infrastructure/messaging/realtime`); `MessageController`, `MessageStreamController`, liste ucu; `MessageProperties`, `StreamProperties`; `SecurityConfig` (`ASYNC` dispatch); `application.properties`; testler.
- `docs/decisions/0001-gercek-zamanli-mesajlasma.md` (yeni ADR), `docs/architecture.md` §6.3/§9, `docs/folder-structure.md` (`messaging/realtime`), `docs/api.md` MESSAGING, `docs/database.md` §3.8, `docs/er-diagram.md` §9, `docs-security/messaging-service/NOTLAR.md`.
- ENV değişikliği yok.

## 7. Açık noktalar

1. **Gateway + tarayıcı ile SSE** doğrulanmalı (tamponlama, 4 sn kalp atışı / 5 sn okuma zaman aşımı). Sorun çıkarsa Gateway sahibiyle rota ayarı; istemci geçici olarak `after` polling'i kullanabilir.
2. **Bildirim teslimi:** notification-service alıcı ucu + Auth'ta `messaging-service → notification-service (events:ingest)` çifti gerekir; sonra relay (social'daki kalıp) ve outbox temizliği.
3. **Çoklu örnek:** akış kaydı bellekte; ölçeklenirken örnekler arası yayın ADR'si.
4. **Hesap silme iç ucu** hâlâ yok (TASK-01 açık noktası); artık mesaj içeriği de tutulduğu için önceliği arttı.
5. **Admin moderasyon** (MSG-27'nin moderasyon kısmı) ve sohbet açma/grup kurma oran sınırı yok.
6. **Frontend** bu görevde yok (EventSource + eşitleme istemcisi web tarafında yazılmalı).
