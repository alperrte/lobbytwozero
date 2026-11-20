# Notification Talebi 01 — Arkadaşlık olaylarının alıcısı (`FriendRequestSent`, `FriendRequestAccepted`; engel bastırma)

> Tarih: 2026-11-20 · Gönderen: Umut (social-service) → `notification-service`
> Sorumlu geliştirici: **atanmamış.** Notification ortak servistir (`AGENTS.md` §1); belgelerde sahip yazılı değil. Değişiklikten önce sorumluluğun kimde olduğu belirlenmelidir.
> Durum: **TALEP / ÖNERİ — Notification'da uygulanmadı.** "İstenen sözleşme" öneridir; bugün Notification'da bulunan bir uç değildir. Olayların **Social tarafı üretimi mevcut ve testlidir**.
> Social gereksinimleri: **SOC-13** (arkadaşlık bildirimleri), **SOC-15** (servis sınırları ve entegrasyon).
> Eşlik eden Auth talebi: [talep-02](../../../docs-auth/verilen-cevaplar/social-service/talep-02-notification-events-ingest.md) (`social-service → notification-service: events:ingest`).

## 1. İhtiyaç ve kullanım senaryosu

1. Bir oyuncu başka bir oyuncuya arkadaşlık isteği gönderince (normal ya da davet koduyla) **alıcı** uygulama içinde bilgilendirilmelidir.
2. İstek kabul edilince isteği **gönderen** "kabul edildi" bildirimi almalıdır.
3. Bir kullanıcı başkasını engellediğinde, Notification engellenen kişiden kaynaklı bildirim üretmeyi durdurabilmelidir (isteğe bağlı, §3.3).

Bildirimin metni, dili, kanalı ve kullanıcı tercihine göre gösterilip gösterilmeyeceği **Notification'ın** sorumluluğudur; Social yalnız "ne oldu, kime, kim yaptı" olayını üretir.

## 2. Karşı servisin mevcut desteği ve eksik nokta

Doğrulanan durum (2026-11-20, `origin/main`):

- `backend/services/notification-service` içinde yalnız `NotificationServiceApplication` ve `NotificationServiceApplicationTests` vardır. Alıcı uç, bildirim tablosu ve Auth doğrulayıcısı yoktur.
- `docs/api.md` NOTIFICATION bölümü kayıtsızdır.
- `docs/architecture.md` §6.3: `FriendRequestSent`, `FriendRequestAccepted`, `FriendshipChanged`, `UserBlocked/UserUnblocked` → notification olarak listelidir.
- Auth'ta `social-service → notification-service` çifti **tanımlı değil** (yukarıdaki Auth talebi).

**Eksik:** alıcı uç yoktur. Social'da `FriendRequestSent/Accepted` olayları outbox'ta `PENDING` bekler; relay açıkken `No delivery target` ile 10 denemede `FAILED` olur.

## 3. İstenen sözleşme (ÖNERİ)

`POST {NOTIFICATION_SERVICE_URL}/internal/events` (Gateway'e açılmaz). Community ([req-01](../../../docs-community/notification-service/talep/req-01.md)) ve Messaging ([req-01](../../../docs-messaging/notification-service/talep/req-01.md)) talepleriyle **aynı uç ve zarf**.

**Kimlik doğrulama:** Auth servis token'ı — `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud=notification-service`, `scope` ⊇ `events:ingest`, `sub=svc:social-service`, `jti`, ömür ≤ 60 sn. `producer`, `sub` ile (`svc:<producer>`) eşleşmelidir. Kullanıcı token'ı kabul edilmez.

**Zarf** (`docs/architecture.md` §6.2): `eventId`, `eventType`, `eventVersion`, `occurredAt` (UTC), `producer: "social-service"`, `aggregateId`, `payload`.

### 3.1 Zorunlu olaylar (SOC-13)

| Olay | Ne zaman | `payload` | Bildirim alacak |
| --- | --- | --- | --- |
| `FriendRequestSent` v1 | Yeni istek oluştu (normal ya da kodla) | `requestId`, `senderId`, `receiverId` | `receiverId` |
| `FriendRequestAccepted` v1 | İstek kabul edildi | `requestId`, `senderId`, `receiverId` | `senderId` (isteği gönderen) |

- Red ve iptal olay **üretmez** (reddedilen kişiye bildirim gitmez).
- Engellenen kullanıcıya hiçbir olay/bildirim gönderilmez (engelin varlığı sızdırılmaz).
- Yük yalnız kimlik taşır; ad, e-posta yoktur. Gösterim adı Notification/istemci tarafında `userId` ile çözülür.

### 3.2 Alıcı kuralları

1. Kalıcı kabul sonrası `2xx`; `2xx` olan olay Social'da teslim edilmiş sayılır.
2. `eventId` ile tekilleştirme; yeniden teslim ikinci bildirim üretmez.
3. Bilinmeyen `eventType`/`eventVersion` → `200 IGNORED` (aksi halde Social 10 kez yeniden dener).
4. Kullanıcı tercihleri ve gruplama Notification'ın kararıdır.
5. Hesap silme: Auth `DELETE /internal/v1/accounts/{accountId}` Notification'da da beklenir (`notification` görevi bugün `PENDING`).
6. Hata davranışı: `400` geçersiz zarf, `401` kimlik, `403` kapsam/`sub`≠`producer`, `413`, `429/503`. Social **her 2xx dışı yanıtı** (3xx dahil) yeniden dener: üstel geri çekilme, en çok 10 deneme, sonra `FAILED`.

### 3.3 İsteğe bağlı: engel bastırma (karar istenir)

Katalog (§6.3) Notification'ın `UserBlocked/UserUnblocked` ile engellenenden bildirim üretmeyi durdurmasını öngörür. Social bu olayları bugün community ve user'a teslim ediyor, **notification'a değil**. İstek: Notification bu bastırmayı yapacak mı? "Evet" ise Social'a `UserBlocked/UserUnblocked` için notification hedefi eklenir (`blockerId`, `blockedId` yükü; Auth çifti aynı); "hayır" ise katalogdaki bu tüketici satırı kaldırılır. `FriendshipChanged` notification için gerekli görülmüyor (bildirimler `FriendRequest*` ile karşılanır).

## 4. Social tarafında tamamlanan hazırlık ve bekleyen iş

Tamamlanan (kod + test; `docs-social/TASK-02.md`, `TASK-04.md`):

- Olaylar iş verisiyle **aynı transaction'da** outbox'a yazılır; kayıt `FanOutEventPublisher`'da türe göre hedefe yönlenir.
- Relay: `FOR UPDATE SKIP LOCKED`, üstel geri çekilme, 10 denemede `FAILED`; gizli anahtar yoksa olay denenmez.

Bekleyen:

- `FanOutEventPublisher`'a notification hedefi eklenecek (alıcı uç ve Auth çifti gelince, küçük değişiklik).
- Relay varsayılanı (`SOCIAL_OUTBOX_RELAY_ENABLED`) kapalı; notification hedefi yokken açılırsa `FriendRequest*` olayları `FAILED` olur.
- Gerçek Notification ile uçtan uca doğrulama **NOT VERIFIED**.

## 5. Kabul kriterleri

1. Dev'de gerçek Auth token'ıyla A, B'ye istek gönderince `FriendRequestSent` Notification'a `2xx` ile teslim edilir; B için **tek** bildirim oluşur, A bildirim almaz.
2. B isteği kabul edince `FriendRequestAccepted` teslim edilir; A için **tek** bildirim oluşur.
3. Davet koduyla gelen istek de aynı bildirimi üretir.
4. Red ve iptal bildirim üretmez.
5. Aynı `eventId` tekrar teslim edilince ikinci bildirim oluşmaz.
6. Yanlış `aud`, `scope`, `sub`≠`producer` ve kullanıcı token'ı reddedilir.
7. Notification kesintisinde olaylar Social outbox'ında bekler, dönüşte teslim edilir; kayıp ve çift yoktur.
