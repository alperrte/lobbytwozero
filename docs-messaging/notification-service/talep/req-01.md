# Notification Talebi 01 — `MessageSent` olayının alıcısı

> Tarih: 2026-11-20 · Gönderen: Umut (messaging-service) → `notification-service`
> Sorumlu geliştirici: **atanmamış.** Notification ortak servistir (`AGENTS.md` §1); belgelerde sahip yazılı değil. Değişiklikten önce sorumluluğun kimde olduğu belirlenmelidir.
> Durum: **TALEP / ÖNERİ — Notification'da uygulanmadı.** "İstenen sözleşme" öneridir; bugün Notification'da bulunan bir uç değildir. Olayın **Messaging tarafı üretimi ve teslimi (relay) hazırdır ve testlidir**.
> Messaging gereksinimleri: **MSG-16** (mesaj bildirimleri), **MSG-27** (servis sınırları ve entegrasyon).
> Eşlik eden Auth talebi: [talep-01](../../../docs-auth/verilen-cevaplar/messaging-service/talep-01-notification-events-ingest.md) (`messaging-service → notification-service: events:ingest`).

## 1. İhtiyaç ve kullanım senaryosu

Bir kullanıcı sohbete mesaj yazdığında, sohbetin diğer aktif üyeleri uygulama içinde bilgilendirilmelidir. Bildirimin metni, dili, kanalı, sessize alma ve kullanıcı tercihine göre gösterilip gösterilmeyeceği **Notification'ın** sorumluluğudur; Messaging yalnız "ne oldu, kime, kim yaptı" olayını üretir ve **mesaj içeriği taşımaz**.

## 2. Karşı servisin mevcut desteği ve eksik nokta

Doğrulanan durum (2026-11-20, `origin/main`):

- `backend/services/notification-service` içinde yalnız `NotificationServiceApplication` ve `NotificationServiceApplicationTests` vardır. Alıcı uç, bildirim tablosu, Auth doğrulayıcısı ve kullanıcı bildirim API'si yoktur.
- `docs/api.md` NOTIFICATION bölümü kayıtsızdır.
- `docs/architecture.md` §6.3 `MessageSent` olayını messaging → notification olarak listeler.
- Auth'ta `messaging-service → notification-service` çifti **tanımlı değil** (yukarıdaki Auth talebi).

**Eksik:** olay alıcısı yoktur; Messaging olayları teslim edilemez. Bu yüzden Messaging relay'i bilerek kapalı tutulur (`ltz.messaging.outbox.adapter=unavailable`): hedef yokken olay denenmez, deneme hakkı yanmaz, mesajlaşma etkilenmez.

## 3. İstenen sözleşme (ÖNERİ)

`POST {NOTIFICATION_SERVICE_URL}/internal/events` (Gateway'e açılmaz; `/internal/**` route edilmez). Topluluk olaylarını isteyen [Notification Talebi 01 (Community)](../../../docs-community/notification-service/talep/req-01.md) ile **aynı uç ve zarf**; tek alıcı uç tüm üreticilere hizmet etmelidir.

**Kimlik doğrulama (Notification doğrular):** Auth servis token'ı — `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud=notification-service`, `scope` ⊇ `events:ingest`, `sub=svc:messaging-service`, `jti`, ömür ≤ 60 sn. Zarftaki `producer`, `sub` ile (`svc:<producer>`) eşleşmelidir. Kullanıcı token'ı bu uçta kabul edilmez.

**Zarf** (`docs/architecture.md` §6.2; Messaging bugün tam bu biçimi gönderiyor):

```json
{
  "eventId": "UUID (outbox kaydı kimliği; yeniden teslimde aynı kalır)",
  "eventType": "MessageSent",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC",
  "producer": "messaging-service",
  "aggregateId": "conversationId (UUID)",
  "payload": { }
}
```

**Yük (`MessageSent` v1; Messaging'de uygulanmış, yalnız kimlik taşır):**

| Alan | Anlam |
| --- | --- |
| `messageId` | Yeni mesajın kimliği |
| `conversationId` | Sohbet kimliği (= `aggregateId`) |
| `conversationType` | `DIRECT` \| `GROUP` |
| `seq` | Sohbet içi sıra numarası |
| `senderId` | Gönderen (bildirim almaz) |
| `recipientIds[]` | Gönderen dışındaki **aktif** üyeler; bildirim bu kimliklere gider |

- Yeniden denemede (`clientMessageId` mükerrer) olay **yeniden üretilmez**; mesaj geri alınırsa olay da yoktur (aynı transaction).
- Mesaj içeriği ve önizleme **yoktur** (SECURITY.md §6). Bildirimde önizleme gösterilecekse gizlilik kararıyla ayrıca tasarlanır; bu talep kapsamı dışındadır.

**Alıcı kuralları (Notification'dan beklenen):**

1. Kalıcı kabul: kayıt commit edildikten sonra `2xx` döner; `2xx` dönülen olay Messaging'de teslim edilmiş sayılır ve bir daha gönderilmez.
2. Tekilleştirme: `eventId` ile (inbox). Aynı olay ikinci bildirim üretmez, tekrar `2xx` döner. `recipientIds` çok alıcılı olduğundan her alıcı için ayrı anahtar (`eventId` + alıcı) gerekir.
3. Bilinmeyen `eventType` ve desteklenmeyen `eventVersion` kalıcı hata değil, `200 IGNORED` olmalıdır; aksi halde Messaging olayı 10 kez yeniden dener.
4. Kullanıcı tercihleri (kanal, sessize alma) ve bildirimin gruplanması (ör. aynı sohbetten art arda gelen çok sayıda mesaj tek bildirimde) Notification'ın kararıdır. **Her mesaj bir olay üretir**; gruplama yapılmazsa yoğun sohbetlerde bildirim seli oluşur.
5. Engel: Messaging engelli taraflar arasında mesajı zaten reddeder (`403 MESSAGING_NOT_ALLOWED`); engel sonradan kurulursa önceden üretilmiş olay bildirime dönüşebilir. Bu bastırma Notification'ın `UserBlocked` tüketimine bağlıdır ([Social talebi](../../../docs-social/notification-service/talep/req-01.md) §3.3).
6. Hesap silme: Auth `DELETE /internal/v1/accounts/{accountId}` Notification'da da beklenir; Auth'taki `notification` silme görevi bugün `PENDING`'dir (uç yok). Silinen hesabın bildirimleri temizlenir.
7. Yanıt süresi: Messaging okuma zaman aşımı 5 sn.
8. Hata davranışı: `400` geçersiz zarf/yük, `401` kimlik, `403` kapsam/`sub`≠`producer`, `413` aşırı gövde, `429/503` geri basınç. Messaging **her 2xx dışı yanıtı** (3xx dahil) yeniden dener: üstel geri çekilme 30 sn → en çok 1 sa, en çok 10 deneme, sonra `FAILED`.

## 4. Messaging tarafında tamamlanan hazırlık ve bekleyen iş

Tamamlanan (kod + test; `docs-messaging/TASK-08.md`):

- `MessageSent` üretimi iş verisiyle **aynı transaction'da** outbox'a yazılır; yük içerik taşımaz (canlı DEV'de doğrulandı).
- `OutboxRelay` + `NotificationEventPublisher`: `FOR UPDATE SKIP LOCKED`, kira, üstel geri çekilme, 10 denemede `FAILED`, 7 gün saklama; token `aud=notification-service`. Gizli anahtar yoksa hiçbir olay denenmez.

Bekleyen:

- Adaptör varsayılanı `unavailable`. Alıcı uç + Auth çifti gelince ortama `LTZ_MESSAGING_OUTBOX_ADAPTER=notification` verilir (yeni ENV yok) ve canlı doğrulama yapılır.
- Gerçek Notification ile uçtan uca doğrulama **NOT VERIFIED**.

## 5. Kabul kriterleri

1. Dev'de gerçek Auth token'ıyla A, B'ye mesaj yazınca Messaging relay'i `MessageSent` olayını Notification'a `2xx` ile teslim eder; B için **tek** bildirim oluşur, A kendi bildirimini almaz.
2. Grup sohbetinde gönderen dışındaki her aktif üye için ayrı bildirim oluşur.
3. Aynı `eventId` tekrar teslim edilince ikinci bildirim oluşmaz.
4. Yanlış `aud`, `scope`, `sub`≠`producer` ve kullanıcı token'ı alıcıda reddedilir.
5. Notification kesintisinde olaylar Messaging outbox'ında bekler ve dönüşte teslim edilir; bildirim kaybolmaz ve çift oluşmaz.
6. Bilinmeyen `eventType` başarıyla yok sayılır (yeniden deneme döngüsü oluşmaz).
7. Bildirim yükünde mesaj metni/önizleme bulunmaz.
