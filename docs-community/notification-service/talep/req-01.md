# Notification Talebi 01 — Topluluk etkileşim olaylarının alıcısı (`CommentCreated`, `PostLiked`, `PostReposted`)

> Tarih: 2026-11-07 · Gönderen: Nisa (community-service) → `notification-service`
> Sorumlu geliştirici: **atanmamış.** Notification ortak servistir (`AGENTS.md` §1, `docs-security/notification-service/NOTLAR.md`); belgelerde sahip yazılı değil. Değişiklikten önce sorumluluğun kimde olduğu belirlenmelidir.
> Durum: **TALEP / ÖNERİ — Notification'da uygulanmadı.** "İstenen sözleşme" öneridir; bugün Notification'da bulunan bir uç değildir. Olayların **Community tarafı üretimi mevcut ve testlidir**.
> Community gereksinimleri: **COM-18** (topluluk bildirimleri), **COM-21** (servis sınırları).

## 1. İhtiyaç ve kullanım senaryosu

Bir gönderiye yorum ya da yanıt yazıldığında, bir gönderi beğenildiğinde veya repostlandığında içerik sahibi (yanıtta ayrıca üst yorumun yazarı) uygulama içinde bilgilendirilmelidir. Bildirimin metni, dili, kanalı ve kullanıcı tercihine göre gösterilip gösterilmeyeceği **Notification'ın** sorumluluğudur; Community yalnız "ne oldu, kime, kim yaptı" olayını üretir.

## 2. Karşı servisin mevcut desteği ve eksik nokta

Doğrulanan durum (2026-11-07):

- `backend/services/notification-service` içinde yalnız `NotificationServiceApplication` ve bir `contextLoads` testi vardır. Alıcı uç, bildirim tablosu, Auth doğrulayıcısı ve kullanıcı bildirim API'si yoktur.
- `docs/api.md` NOTIFICATION bölümü: `_Kayıt yok._`
- `docs/architecture.md` §6.3 `PostCreated / CommentCreated / PostLiked` olaylarını community → notification olarak **[ÖNERİ]** listeler. `PostReposted` katalogda yoktur (kataloğa eklenmesi önerilir).
- Auth tarafı hazırdır (`docs-auth/entegrasyon/notification-service.md`): `community-service → notification-service` çifti `events:ingest` kapsamıyla tanımlıdır.

**Eksik:** olay alıcısı yoktur; Community olayları teslim edilemez. Bildirimin kullanıcıya gösterildiği uç da yoktur (bu talep yalnız olay alımıyla ilgilidir; kullanıcı API'si Notification sahibinin işidir).

## 3. İstenen sözleşme (ÖNERİ)

`POST {NOTIFICATION_SERVICE_URL}/internal/events` (Gateway'e açılmaz).

**Kimlik doğrulama (Notification doğrular):** Auth servis token'ı — `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud=notification-service`, `scope` ⊇ `events:ingest`, `sub=svc:community-service`, `jti`, ömür ≤ 60 sn. Zarftaki `producer`, `sub` ile (`svc:<producer>`) eşleşmelidir. Kullanıcı token'ı kabul edilmez.

**Zarf** (`docs/architecture.md` §6.2; Community bugün tam bu biçimi gönderiyor):

```json
{
  "eventId": "UUID (outbox kaydı kimliği; yeniden teslimde aynı kalır)",
  "eventType": "CommentCreated | PostLiked | PostReposted",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC",
  "producer": "community-service",
  "aggregateId": "UUID",
  "payload": { }
}
```

**Yükler (Community'de uygulanmış; yalnız kimlik taşır, metin ve görünen ad yoktur):**

| Olay | `aggregateId` | `payload` | Alıcı(lar) |
| --- | --- | --- | --- |
| `CommentCreated` | yorum kimliği | `commentId`, `postId`, `parentCommentId` (yanıt değilse `null`), `actorId`, `recipientIds[]` (1–2 kimlik), `isReply` | Gönderi sahibi ve yanıtsa üst yorumun yazarı. Yorumu yazan ve tekrarlar çıkarılmıştır |
| `PostLiked` | gönderi kimliği | `postId`, `actorId`, `recipientId` | Gönderi sahibi. Yalnız beğeni **gerçekten eklendiğinde** üretilir (geri alma/tekrar beğeni olay üretmez) |
| `PostReposted` | orijinal gönderi kimliği | `postId` (orijinal), `repostId`, `actorId`, `recipientId` | Orijinal gönderi sahibi |

- Kişi kendi içeriğine yaptığı etkileşim için olay **üretilmez**; alıcı yoksa olay yazılmaz.
- Community etkileşimi, engel varken zaten reddeder (engelli yazarın içeriği `404`). Ancak olay teslimi gecikirse bildirim, sonradan oluşan bir engelden önce üretilmiş olabilir; engelleme sonrası bildirim bastırma (`UserBlocked`) Notification'ın işidir (`docs/architecture.md` §6.3). Bunun için Social → Notification çifti Auth'ta tanımlı değildir (`docs-auth/entegrasyon/notification-service.md`, "Auth'ta bekleyen").

**Alıcı kuralları (Notification'dan beklenen):**

1. Kalıcı kabul: kayıt commit edildikten sonra `200` döner; `2xx` dönülen olay Community tarafında teslim edilmiş sayılır ve bir daha gönderilmez.
2. Tekilleştirme: `eventId` ile (inbox). Aynı olay ikinci bildirim üretmez; tekrar `200` döner (`{ "eventId": "UUID", "outcome": "PROCESSED | DUPLICATE | IGNORED" }` önerilir, Community yalnız durum kodunu kullanır).
3. Bilinmeyen `eventType` ve desteklenmeyen `eventVersion` kalıcı hata değil, `200 IGNORED` olmalıdır; aksi halde Community olayı 10 kez yeniden dener.
4. Kullanıcı tercihleri (kanal/kapatma) ve bildirimin gruplanması (ör. çok sayıda beğeni) Notification'ın kararıdır; `recipientIds` çok alıcılı olduğundan olay başına birden çok bildirim kaydı doğabilir ve her alıcı için ayrı tekilleştirme anahtarı (`eventId` + alıcı) gerekir.
5. Bildirim, içerik metni taşımaz; ilgili içeriğe bağlantı `postId`/`commentId` ile kurulur ve içeriğe erişim Community'nin yetki kuralları (gizli/silinmiş içerik `404`) ile sınırlıdır.
6. Hesap silme: Auth `DELETE /internal/v1/accounts/{accountId}` Notification'da da beklenir (`bilgilendirme-2026-11-05-auth-degisiklikleri.md`); aktör ya da alıcı olarak silinen hesabın bildirimleri temizlenir.
7. Yanıt süresi: Community okuma zaman aşımı 5 sn.
8. Hata davranışı: `400` geçersiz zarf/yük, `401` kimlik, `403` kapsam/`sub`≠`producer`, `413` aşırı gövde, `429/503` geri basınç. Community **her 2xx dışı yanıtı** (3xx dahil) yeniden dener: 10 sn × 2ⁿ (en çok 1 saat), en çok 10 deneme, sonra `FAILED`.

## 4. Community tarafında tamamlanan hazırlık ve bekleyen iş

Tamamlanan (kod + test):

- `InteractionEvents`: `CommentCreated`, `PostLiked`, `PostReposted` üretimi; alıcı süzme (kendi içeriğine etkileşim, tekrarlar); olaylar iş verisiyle **aynı transaction'da** outbox'a yazılır (iş geri alınırsa olay da gitmez). Gerçek PostgreSQL entegrasyon testleri vardır.
- Outbox relay ve `EventDeliveryClient`: Notification için `aud=notification-service` Auth servis token'ı, `Authorization: Bearer`, üstel geri çekilme, 10 denemede `FAILED`. Gizli anahtar yoksa hiçbir olay denenmez.

Bekleyen:

- Relay varsayılan **kapalıdır** (`ltz.outbox.relay.enabled=false`). Notification alıcısı yayında olmadan açılırsa bekleyen bildirim olayları 10 denemede `FAILED` olur.
- Gerçek Notification ile uçtan uca doğrulama **yapılmadı (NOT VERIFIED)**.
- Kataloğun `PostCreated` olayı Community'de **üretilmez**; Community gereksinimlerinde gönderi yayımlanınca bildirim gerektiren bir akış (ör. takip) yoktur. Böyle bir ihtiyaç doğarsa ayrıca talep edilir.

## 5. Kabul kriterleri

1. Dev'de gerçek Auth token'ıyla başka bir kullanıcının gönderisine yorum yazılınca Community relay'i `CommentCreated` olayını Notification'a `200` ile teslim eder; gönderi sahibi için **tek** bildirim oluşur.
2. Yanıt yazılınca hem gönderi sahibi hem üst yorum yazarı bildirim alır; ikisi aynı kişiyse tek bildirim oluşur; yorumu yazan kendi bildirimini almaz.
3. Beğeni ve repost yalnız içerik sahibine bildirim üretir; kendi içeriğine yapılan etkileşim bildirim üretmez.
4. Aynı `eventId` tekrar teslim edilince ikinci bildirim oluşmaz.
5. Yanlış `aud`, `scope`, `sub`≠`producer` ve kullanıcı token'ı alıcıda reddedilir.
6. Notification kesintisinde olaylar Community outbox'ında bekler ve dönüşte teslim edilir; bildirim kaybolmaz ve çift oluşmaz.
7. Bilinmeyen `eventType` başarıyla yok sayılır (yeniden deneme döngüsü oluşmaz).
