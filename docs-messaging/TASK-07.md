# LTZ-UMT-MSG-07 — Hesap Silme ve Yayına Hazırlık (TASK-07)

> Tarih: 2026-11-11 · Sorumlu: Umut · Servis: `messaging-service` · Dal: `feature/messaging-service` · Kapsam: hesap silme iç ucu (D4), birden çok örnekte canlı akış, kalıcı oran sınırları, "99+" okunmamış, bağlantı havuzu · Durum: **kod, testler ve belgeler tamam; `pre-push messaging-service` PASSED; gerçek Auth ile silme ve iki gerçek örnek NOT VERIFIED**
>
> Tasarım ve kararlar: `docs-messaging/TASK-07-TASARIM.md` · Sözleşme: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`, `02_UMUT_social_messaging_lfp.md` §4 · API: `docs/api.md` → MESSAGING ("Hesap verisi silme", ortak kurallar, sohbet listesi, okundu işaretle, gerçek zamanlı akış) · Şema `docs/database.md` §3.8, `docs/er-diagram.md` §9 · ADR 0001 · `docs/SECURITY.md` §13 · `docs-security/messaging-service/NOTLAR.md`.

## 1. Sonuç

| İş | Durum | Not |
| --- | --- | --- |
| Hesap silme iç ucu | IMPLEMENTED | `DELETE /internal/v1/accounts/{id}`, yalnız Auth servis token'ı (rehberin tüm kuralları); mesaj satırları kalır, içerik silinir, gönderen anonim; kişisel veri kalmaz |
| Birden çok örnekte canlı akış | IMPLEMENTED | PostgreSQL `LISTEN/NOTIFY` aktarımı; kopan dinleyici akışları kapatıp eşitletir |
| Kalıcı oran sınırları | IMPLEMENTED | GIF arama ve tepki sayaçları DB'de; tüm örnekler ortak |
| "99+" okunmamış | IMPLEMENTED | Sayım 100'de durur |
| Bağlantı havuzu | IMPLEMENTED | 5 sn'de bağlantı yoksa `503 SERVICE_BUSY` + `Retry-After`; boyutlandırma notu |

Doğrulama (2026-11-11):

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, PostgreSQL 17) | PASS — 411 test, 0 hata (bu görevde 23 yeni test: `AccountErasureTest` 16, `StreamRelayTest` 4, `GifSearchRateLimiterTest` +1 ve DB'ye taşındı, `ConversationListTest` +1, `ConnectionPoolExhaustionTest` 1) |
| `.\pre-push\pre-push.cmd messaging-service` (`AUTH_SERVICE_URL=http://auth-service:8080`) | **BACKEND TEST CHECK PASSED** — 411 test, JaCoCo satır %96,86, değişen satır %96,80 (2115 / 2185; `origin/main`'e göre, MSG-04..07 dahil), Checkstyle/PMD yeni ihlal 0 (ilk koşudaki test dosyası `CloseResource` bulgusu giderildi) |
| `pre-push full dev`, SonarQube | NOT RUN — Umut'un commit öncesi akışı |
| Gerçek Auth servis token'ıyla silme (dev'de test hesabı, `messaging` görevi `CONFIRMED`) | NOT VERIFIED — Auth sahibiyle (Kerem) birlikte yapılmalı |
| İki gerçek örnekle canlı akış (iki konteyner) | NOT VERIFIED — testte ikinci örnek ayrı bir dinleyiciyle taklit edildi |

## 2. Akış ve kararlar

**Hesap silme.** Güvenlik zinciri yola göre doğrulayıcı seçer (`/internal/**` servis token'ı, diğerleri kullanıcı token'ı; social-service ile aynı desen). Uç `sub=svc:auth-service` ve `account_id` = yol denetler. `AccountErasureService` tek transaction'da `JdbcAccountDataEraser`'ı çağırır: hesabın sohbetleri kilitlenir → hesabın mesajlarının ve hesabın tepkileri, hesabın ek bağlantıları ve sahipsiz kalan ek içerikleri silinir → hesabın mesajları temizlenir (`deleted`, gönderen ve silen anonim `0000…`, istemci kimliği = mesaj kimliği) → gizleme, okuma aralığı, oran sayacı, aktarım kayıtları, üyelikler silinir, başkalarının satırlarındaki izler anonimleşir → sahibi silinen gruba ayrılma kuralıyla yeni sahip, aktif üyesi kalmayan grup silinir, birebir sohbet anahtarı `erased:<id>` → outbox'tan hesap çıkarılır. Commit sonrası açık akışlar tüm örneklerde kapanır. Karşı tarafı kalmayan birebir sohbete yazmak `403`.

**Canlı akış aktarımı.** `PostgresStreamRelay` (`MessageStreamPort`) olayları alıcı başına JSON'a çevirir, tek iş parçacıklı yürütücüde `stream_relay_events`'e yazar ve `pg_notify` ile duyurur; `StreamRelayListener` (her örnekte, havuz dışı bağlantı) satırı okuyup `SseMessageHub.deliver` ile yerel akışlara yazar. Akış kapatma yalnız bildirimle gider (tabloda hesap kimliği kalmaz). Bağlantı koparsa yeniden bağlanınca yerel akışlar kapanır. Kayıtlar 5 dk. `ltz.messaging.stream.relay=local` tek örnek modu.

| Konu | Karar (Claude; `TASK-07-TASARIM.md` §7) | Neden |
| --- | --- | --- |
| Silinen hesabın mesajları | Satır kalır, içerik silinir, gönderen anonim | Karşı tarafın geçmişi ve `seq`/okundu bilgisi bozulmaz; rehberin önerdiği iki yoldan biri |
| Boş grup | Silinir | Kimse göremez |
| Birebir sohbet | Karşı taraf için kalır, yazılamaz | Geçmiş okunabilir; boş sohbete yazmak anlamsız |
| Çoklu örnek | PostgreSQL `LISTEN/NOTIFY` | Yeni altyapı/ENV yok |
| Akış sınırı | Örnek başına | Amaç örneğin kaynağını korumak |
| Okunmamış | 100'de durur | Sabit maliyet; arayüz `99+` |
| Havuz | 5 sn, sonra `503` | Uzun bekleyen istek ve 500 yerine yeniden denenebilir hata |
| Sürücü kapsamı | `postgresql` `runtime` → `compile` | `PGConnection` (yeni bağımlılık değil) |

## 3. Güvenlik

| Konu | Uygulama |
| --- | --- |
| İç uç | Gateway'e açık değil; yalnız Auth servis token'ı; başka servis (`svc:admin-service`) ve başka hesaba bağlı token `403`; imza saldırıları (başka anahtar, `none`, HS256, bozuk imza) `401` (testli) |
| Kişisel veri | Silme sonrası hesabın kimliği hiçbir kişisel sütunda ve outbox/aktarım yükünde kalmaz (test sorgusu tüm sütunları sayar) |
| Aktarım | İçerik yalnız 5 dk tabloda; hesap silmede hemen silinir; akış kapatma kimliği tabloya yazılmaz |
| Yoğunluk | Havuz tükenince `503` + `Retry-After`; ayrıntı yansıtılmaz |

## 4. Gereksinim → test → dosya

| İş | Test | Ana dosya |
| --- | --- | --- |
| Hesap silme | `AccountErasureTest` (16) | `AccountErasureController`, `InternalApiPaths`, `AccountErasureService`, `AccountErased`, `AccountErasureDispatcher`, `AccountDataEraserPort`, `JdbcAccountDataEraser`, `JwtConfig`, `SecurityConfig`, `TokenClaims`, `AuthProperties`, `ConversationAccessPolicy` |
| Çoklu örnek akış | `StreamRelayTest` (4), `ReadReceiptStreamTest` (aktarımı bekleyecek biçimde), `MessageStreamTest`, `SseMessageHubTest` | `PostgresStreamRelay`, `StreamRelayListener`, `StreamEnvelope`, `LocalStreamDelivery`, `SseMessageHub`, `MessageStreamPort.closeStreams`, `MessagingApplicationConfig`, `V137` |
| Oran sınırları | `GifSearchRateLimiterTest` (3), `MessageReactionTest`, `GifMessageTest` | `FixedWindowRateLimiter`, `GifSearchRateLimiter`, `ReactionRateLimiter`, `V137` |
| 99+ | `ConversationListTest.okunmamisSayisi100deKesilir` | `ConversationMemberRepository.countUnread` |
| Havuz | `ConnectionPoolExhaustionTest` | `GlobalExceptionHandler`, `MessagingErrorCode.SERVICE_BUSY`, `application.properties` |

## 5. Açık noktalar

1. **Gerçek Auth ile silme** dev'de doğrulanmalı (Kerem'le): test hesabı silinir, `messaging` görevi `CONFIRMED` olmalı.
2. **Silme sonrası canlı güncelleme:** karşı tarafın açık ekranı, silinen hesabın mesajlarını sayfa yenilenene kadar eski haliyle gösterebilir (silme olayı yayınlanmıyor; yeniden bağlanma/yenileme düzeltir).
3. **Silinen hesapla yeniden sohbet:** kullanıcı varlığı doğrulanmadığından (user-service ucu PENDING) silinmiş bir kimlikle yeni birebir sohbet açılabilir; tüm servislerdeki ortak açık nokta.
4. **İki gerçek örnekle** canlı akış ve yük altında aktarım gecikmesi ölçülmedi.
5. Frontend: `senderId` `00000000-0000-0000-0000-000000000000` → "Silinmiş hesap"; `unreadCount` `100` → `99+`; `503`'te `Retry-After` ile yeniden dene.
