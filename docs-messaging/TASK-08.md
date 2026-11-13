# Messaging Service — Servisler Arası Entegrasyon (TASK-08, LTZ-UMT-MSG-08)

> Tarih: 2026-11-13 · Sorumlu: Umut · Servis: `messaging-service` · Dal: `feature/messaging-service` · Kapsam: Auth, Social, LFP, Gateway bağlantılarının gerçek servislerle doğrulanması; MSG-16 olay teslimi (relay) ve outbox saklaması; notification/user/admin için sorumlulara talepler · **Kod değişikliği yalnız messaging-service'te; diğer servislerin koduna dokunulmadı.**
>
> Görev: Messaging Service'in bağımlı olduğu servislerle entegrasyonun tamamlanması; API iletişimi, veri tutarlılığı ve entegrasyon testleri.

## 1. Sonuç

| # | Bağlantı | Önce | Şimdi | Kanıt |
| --- | --- | --- | --- | --- |
| 1 | Auth → Messaging: kullanıcı token'ı (JWKS) | Servis içi testli | **PASS** (canlı, Gateway) | §4 G, M |
| 2 | Auth → Messaging: hesap verisi silme | Uç var, canlı NOT VERIFIED | **PASS** (canlı, görev `CONFIRMED`) | §4 D |
| 3 | Messaging → Social: engel sorgusu (`blocks:check`) | Social TASK-06'da birebir için PASS | **PASS** (birebir iki yön + grup) | §4 B |
| 4 | LFP → Messaging: ilan sahibiyle iletişim (LFP-16) | İki taraf hazır, canlı NOT VERIFIED | **PASS** (`recipientId` → aynı birebir sohbet) | §4 L |
| 5 | Gateway → Messaging: REST, SSE, 1 MB yükleme, `/internal/**` kapalı | SSE ve yükleme canlı NOT VERIFIED | **PASS** | §4 G, S, I |
| 6 | Birden çok örnek: canlı akış aktarımı | Testte taklit | **PASS** (iki gerçek konteyner) | §4 T |
| 7 | Messaging → Notification: `MessageSent` (MSG-16) | Outbox'a yazılıyor, relay yok, tablo sınırsız | **Kod**: relay + 7 gün saklama; teslim hedefi bekliyor | §3, §6 |
| 8 | Messaging → User: kullanıcı varlığı | Doğrulanmıyor | Değişmedi; talep | §6 |
| 9 | Admin → Messaging: moderasyon (MSG-27) | admin-service iskelet | Değişmedi; sözleşme yok | §6 |
| 10 | GIPHY | Anahtarla NOT VERIFIED | DEV'de anahtar yok; fail-closed `503` canlı PASS | §4 F |

Doğrulama (2026-11-13):

| Kontrol | Sonuç |
| --- | --- |
| `.\pre-push\pre-push.cmd messaging-service` (`AUTH_SERVICE_URL=http://auth-service:8080`) | **BACKEND TEST CHECK PASSED** — 427 test, 0 hata (önce 411; yeni: `OutboxRelayTest` 8, `NotificationEventPublisherTest` 8, `AuthServiceTokenClientTest` +1 doğrulama), JaCoCo satır %96,52, değişen satır %92,31 (144/156), Checkstyle/PMD yeni ihlal 0 |
| Canlı DEV yığını (Gateway + gerçek Auth, Social, LFP, Game, Mailpit) | **44/44 PASS** (§4) + iki örnek **2/2 PASS** |
| `.\pre-push\pre-push.cmd full dev` (origin/main merge'ü dahil) | **SAFE TO COMMIT** — tüm adımlar PASS, 18 servis HTTP sağlık PASS |
| `sonarqube\sonar.cmd scan dev` | Quality Gate **FAIL** (proje geneli güvenilirlik C, güvenlik C). TASK-08 dosyalarında Sonar bulgusu **0**. Gate'i düşüren MAJOR bulgular: community/hardware/mail `S2259`, hardware `S2077` ve messaging'in önceki görevlerinden kalan `S6218` (`AttachmentMeta`, `WebmOpusWriter.Cluster`: dizi alanlı record, bu nesneler karşılaştırılmıyor). Messaging tek başına düzeltse de gate diğer servisler yüzünden FAIL kalır. |

## 2. Sözleşmeler (karşı tarafın kodundan / canlı yanıttan doğrulandı)

| Uç | Gövde | Yetki |
| --- | --- | --- |
| Auth `GET /api/v1/auth/.well-known/jwks.json` | RS256 anahtarları | Public; kullanıcı token'ı `typ=at+jwt`, `aud=ltz-api` |
| Auth → messaging `DELETE /internal/v1/accounts/{id}` | — | `svc+jwt`, `aud=messaging-service`, `scope=account:erase`, `sub=svc:auth-service`, `account_id` = yol |
| social `POST /internal/v1/blocks/check` | `{userId, otherUserIds[≤100]}` → `{results:[{userId, blocked}]}` | `aud=social-service`, `scope=blocks:check` |
| lfp `POST /api/v1/lfp/listings/{id}/contact` | → `{listingId, recipientId}`; istemci `POST /api/v1/messaging/conversations/direct {userId: recipientId}` | Kullanıcı token'ı (servisler arası token iletimi yok) |
| notification `POST /internal/events` (**henüz yok**) | Ekip zarfı (architecture.md §6.2), `MessageSent` v1 | `aud=notification-service`, `scope=events:ingest` — Auth çifti yok (talep-01) |

## 3. Değişiklikler (messaging-service)

**Olay teslimi (MSG-16).** `OutboxRelay` + `OutboxEventStore` (Social'daki kalıp): zamanı gelmiş `PENDING` olaylar `FOR UPDATE SKIP LOCKED` ile alınır, kira (1 dk) kadar ötelenir, teslim transaction dışında yapılır; başarı `SENT`, hata üstel geri çekilme (30 sn → 1 sa), 10. denemede `FAILED`. `NotificationEventPublisher` ekip standardı uca (`POST {NOTIFICATION_SERVICE_URL}/internal/events`) `aud=notification-service` token'ıyla gönderir; 2xx dışı (3xx dahil) teslim sayılmaz, iletilere yük/token yazılmaz. `OutboxRelayWorker` 5 sn'de bir relay'i, 10 dk'da bir saklama temizliğini çalıştırır.

| Konu | Karar (Claude) | Neden |
| --- | --- | --- |
| Varsayılan adaptör | `unavailable` (`ltz.messaging.outbox.adapter`); `notification` seçilince teslim | Notification alıcı ucu ve Auth çifti yok; boşuna deneme ve `FAILED` birikmesin. Yeni ENV yok: `LTZ_MESSAGING_OUTBOX_ADAPTER` |
| Saklama | `occurredAt` 7 günden eskiyse durumdan bağımsız sil (tur başına 5000) | Her mesaj bir olay üretir; eski "yeni mesaj" bildiriminin değeri yok; tablo sınırsız büyümez. V138: `ix_outbox_events_occurred_at` |
| Servis kimliği yoksa | Relay hiç olay almaz (`ServiceTokenPort.isConfigured`) | Deneme hakkı yanmaz |
| Social olayları (`UserBlocked`, `FriendshipChanged`) | Messaging tüketmez; Social kaydındaki tüketici listesi düzeltildi | Engel her etkileşimde senkron, fail-closed sorulur (gecikmesiz); sohbet için arkadaşlık şartı yok |
| Kullanıcı varlığı | Messaging'de değişiklik yok | user-service'te Messaging'e açık uç yok; kullanıcı token'ını servisler arası iletmek (`profiles/batch`) kural dışı |

Dosyalar: `V138__outbox_retention_index.sql`; yeni `EventEnvelope`, `EventDeliveryException`, `EventPublisherPort`, `OutboxEventStore`, `OutboxRelay`, `OutboxRelayWorker`, `NotificationEventPublisher`, `UnavailableEventPublisher`, `OutboxProperties`; değişen `OutboxEvent` (kira, `SENT`/`FAILED`, geri çekilme), `OutboxEventRepository` (`lockDue`, `deleteOccurredBefore`), `ServiceTokenPort`/`AuthServiceTokenClient` (`isConfigured`), `MessagingApplicationConfig`, `application.properties`.

## 4. Canlı entegrasyon (gerçek DEV yığını, 2026-11-13)

Yığın: `ltz-dev` (20 servis). messaging-service bu daldan derlendi (V138 gerçek `messaging_db`'ye uygulandı). Hesaplar Gateway'den kayıt + Mailpit'teki kodla doğrulandı; istemci Node HTTP. Doğrulama hem API yanıtı hem ilgili veritabanı. Betik repoya eklenmedi (Social TASK-06 §7.4 ile aynı karar).

Geçici ayar: Auth konteyneri test boyunca `AUTH_MAIL_ADAPTER=dev-smtp` (Mailpit) ve `AUTH_DELETION_GRACE_PERIOD=PT1M` ile kabuk ortamından başlatıldı (`.env.dev`'e dokunulmadı); **test sonrası normal ayarlarla (mail-service, P14D) yeniden başlatıldı**. Not: main'deki güncel Auth imajı yerel `auth_db` ile Flyway checksum uyuşmazlığı veriyor (V61–V63; Auth migration'ları 2026-11-10'da birleştirildi, e2958980). Yerel veriyi silmemek için Auth, `auth_db` şemasıyla aynı olan `4e79726a` kaynağından derlenip çalıştırıldı; DEV'de şu an bu imaj çalışıyor (bkz. §7.1).

| # | Senaryo | Sonuç |
| --- | --- | --- |
| G1–G3 | Gateway: kimliksiz `401`, `/internal/**` route edilmez `404`, geçersiz token `401` | PASS |
| M1–M3 | Gerçek Auth JWT ile birebir sohbet `201`, ters yönde aynı sohbet `200`, gövdede kimlik alanı `400` | PASS |
| S1–S5 | SSE Gateway üzerinden `text/event-stream`; `ready`; mesaj alıcıya 70–170 ms; `read` olayı gönderene; 9 sn boşta kalp atışıyla açık kalır | PASS |
| M4–M6 | Mesaj `201`; aynı `clientMessageId` aynı mesaj `200` (mükerrer yok); okundu işaretleme | PASS |
| O1–O3 | Outbox'ta tek `MessageSent`, `PENDING`, deneme 0; yük içerik taşımaz, alıcıyı taşır; V138 indeksi var | PASS |
| I1–I5 | PNG yükleme `201`, görsel mesajı `201`, alıcı indirir `200 image/png`, üye olmayan `404`, 1 MB üstü Gateway'den `413` | PASS |
| B1–B7 | A, C'yi engeller → C→A ve A→C birebir `403 MESSAGING_NOT_ALLOWED`; A engellediği C ile grup kuramaz `403`; engelsiz B, A ve C ile grup kurar `201`; engel kalkınca C→A `201` | PASS |
| L1–L5 | A ilan açar; B `contact` → `recipientId = A` → birebir sohbet (mevcut) `200`; engelli C `contact` `404`; engel kalkınca `200` | PASS |
| F1 | GIF arama: DEV'de anahtar yok → `503 GIF_PROVIDER_UNAVAILABLE` | PASS |
| D0–D8 | B silme ister (parola) → Mailpit token'ı ile onaylar → **67 sn** sonra Auth'ta `messaging` görevi `CONFIRMED` (ilk deneme); `messaging_db`'de B'nin üyeliği 0, gönderen satırı 0, outbox izi 0; A'nın geçmişinde B'nin mesajı `deleted: true`, gönderen `0000…`; karşı tarafı silinen sohbete yazmak `403` | PASS |
| T1–T2 | İkinci messaging örneği (`compose run`, Gateway takma adı yok); akış ikinci örnekte, mesaj Gateway'den birinci örneğe → ikinci örnekteki akışa ulaştı | PASS |

Aynı silmede diğer görevler: user, mail, game, library, review, social, lfp, community, hardware, performance `CONFIRMED`; admin, content, event, notification, recommendation `PENDING` (silme uçları yok — o servislerin işi).

## 5. Güvenlik

| Konu | Uygulama |
| --- | --- |
| Olay yükü | İçerik/önizleme yok (canlı doğrulandı); relay loglarında yalnız tür ve kimlik |
| Giden çağrı | Hedefe bağlı 60 sn token; gizli anahtar yoksa çağrı yok; yönlendirme izlenmez, 3xx teslim değil |
| İç uçlar | Gateway `/internal/**` route etmez (canlı `404`) |
| Test ortamı | Gerçek ENV dosyası okunmadı; geçici Auth ayarları kabuk ortamından verildi ve geri alındı; test parolaları yalnız bellekte |

## 6. Sorumlulara iletilecekler

| Kime | Konu |
| --- | --- |
| Kerem (Auth) | [talep-01](../docs-auth/verilen-cevaplar/messaging-service/talep-01-notification-events-ingest.md): `messaging-service → notification-service: events:ingest` çifti. Auth → Messaging silme canlı `CONFIRMED`; `bilgilendirme-2026-11-05` kapanabilir. Ayrıca main'deki Auth imajı yerel DEV `auth_db`'lerinde checksum hatası veriyor (birleştirilmiş migration); ekibe `migration-yazim-kurallari.md`'deki sıfırlama adımı hatırlatılmalı. |
| Kerem (User) | Messaging'in kullanıcı varlığını doğrulayabileceği bir iç uç yok: `privacy/decisions` çağıranları social/hardware/library ile sınırlı. Öneri: `messaging-service` (ve `lfp-service`) için hesap durumu kontrolü (`REVIEW_AUTHOR` benzeri, yalnız "var ve görünür mü") ve Auth çifti. Gelene kadar silinmiş/olmayan kimlikle birebir sohbet açılabilir (tüm servislerde ortak açık nokta). |
| Notification sorumlusu (ortak, belirlenmedi) | `POST /internal/events` alıcı ucu, `MessageSent` v1 (`docs/api.md` → MESSAGING → "Giden olay sözleşmeleri"): `recipientIds`'e bildirim; `eventId` ile tekilleştirme; içerik yok. Ayrıca Auth silme görevi `notification` `PENDING` (silme ucu yok). |
| Admin sorumlusu (ortak, belirlenmedi) | MSG-27 "yetkili moderasyon akışları": admin-service iskelet, sözleşme yok. Messaging tarafında hazır bir moderasyon ucu yazılmadı; ihtiyaç (ör. şikâyet edilen mesajın incelenmesi, kullanıcının mesajlaşmadan men edilmesi) tanımlanınca ayrı görev. |
| Umut (LFP) | LFP → Social engel (`contact` `404`) ve Auth → LFP silme (`lfp` görevi `CONFIRMED`) bu testte canlı doğrulandı; `docs-auth/entegrasyon/lfp-service.md` ve `docs/api.md` LFP "NOT VERIFIED" notları güncellenebilir. |

## 7. Açık noktalar

| # | Konu | Not |
| --- | --- | --- |
| 1 | DEV Auth imajı | DEV'de `4e79726a` kaynaklı Auth çalışıyor (eski imaj silinmişti). Güncel Auth'a geçmek için yerel `auth_db` Kerem'in kuralıyla sıfırlanmalı; sonra `node ops/dev.mjs` hesapları yeniden kurar. Bu Umut'un kararı. |
| 2 | Bildirim teslimi | Relay hazır, adaptör `unavailable`. Talep-01 + notification alıcı ucu gelince `LTZ_MESSAGING_OUTBOX_ADAPTER=notification` (compose'a eklenir) ve canlı doğrulama. |
| 3 | Kullanıcı varlığı | §6 Kerem (User). |
| 4 | Tarayıcı | `EventSource` ile canlı akış ve arayüz: messaging frontend'i yok, NOT VERIFIED. |
| 5 | Test hesapları | Canlı testte açılan `mit_*` hesaplarından silinenler `DELETION_REQUESTED`'da kalır (5 servisin silme ucu yok); diğerleri DEV'de durur. |
