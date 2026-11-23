# LTZ-UMT-MSG-07 — Hesap Silme ve Yayına Hazırlık: Tasarım ve Plan

**Servis:** `messaging-service` · **Branch:** `feature/messaging-service` (main'e merge yok)
**Durum:** Umut "yap, bitince söyle" dedi (2026-11-10); kararlar Claude'un, §7'de Umut'un kontrolü için listelendi.

TASK-02'den beri açık olan hesap silme iç ucu ile TASK-04/05'te yayına hazırlık işi olarak bırakılan dört madde: birden
çok örnekte canlı akış, kalıcı oran sınırları, "99+" okunmamış sayısı, bağlantı havuzu. Sözleşme kaynağı:
`docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md` ve `02_UMUT_social_messaging_lfp.md` §4; örnek uygulama
`social-service` (`JwtConfig`, `SecurityConfig`, `AccountErasureController`, `AccountErasureTest`).

## 1. Hesap silme iç ucu

`DELETE /internal/v1/accounts/{accountId}`; yalnız Auth'un servis token'ı: `typ=svc+jwt`, RS256 (aynı JWKS), `iss=ltz-auth`,
`aud=messaging-service` (`ltz.messaging.auth.service-audience`, yeni ENV değil), `sub=svc:` önekli ve uçta **tam**
`svc:auth-service`, `scope` içinde `account:erase`, `account_id` = yoldaki hesap, `jti` zorunlu, ömür ≤ 60 sn (iat yoksa
ret). Kullanıcı token'ı iç uçta, servis token'ı `/api/v1/messaging/**`'da `401`. Diğer `/internal/**` `403`. Gateway'e
route eklenmez. İdempotent `204`; gövde yok. Yalnız `accountId` + sonuç loglanır.

**Ne olur (tek transaction, set tabanlı; başka servise çağrı yok):**

| Veri | Karar |
| --- | --- |
| Hesabın gönderdiği mesajlar | Satır kalır (sohbetin `seq` sırası, okunmamış ve okundu bilgisi bozulmasın), **içerik herkesten silinmiş gibi temizlenir**: gövde, GIF, ek bağlantısı (başka bağlantısı kalmayan içerik baytları da) ve mesajın tepkileri silinir; `deleted_at`, `deleted_by` dolar; `sender_id` ve `deleted_by` **anonim hesap** kimliğine (`00000000-0000-0000-0000-000000000000`) çevrilir; `client_message_id` mesaj kimliğine çevrilir (istemci kimliği hesaba bağlanamasın, tekillik bozulmasın) |
| Başkalarının mesajlarında hesap izi | Hesabın herkesten sildiği başka mesajların `deleted_by`'ı ve `conversations.created_by` anonimleşir; `conversation_members.removed_by` anonimleşir |
| Tepkiler, benden sil, okuma aralıkları | Hesabın satırları silinir |
| Gönderilmemiş yüklemeler | Silinir (içerik baytları dahil) |
| Üyelikler | Hesabın tüm üyelik satırları silinir. Grupta aktif sahipse ayrılma kuralıyla (önce ADMIN, sonra en eski üye) yeni sahip atanır; aktif üyesi kalmayan grup tümüyle silinir (zaten kimse göremez) |
| Birebir sohbet | Sohbet karşı taraf için kalır (geçmişi bozulmaz, hesabın mesajları "silindi"); `direct_key` hesabın kimliğini içerdiğinden `erased:<sohbetId>` olur. Karşı tarafı kalmayan birebir sohbete yazmak `403 MESSAGING_NOT_ALLOWED` (yeni kural) |
| Outbox | Bekleyen `MessageSent` olaylarından göndereni hesap olanlar silinir; alıcı listesinden hesap çıkarılır |
| Oran sayaçları, akış aktarım kayıtları | Hesabın satırları silinir |
| Açık canlı akışlar | Commit sonrası tüm örneklerde kapatılır |

Saklama istisnası yok: `204` "kişisel veri kalmadı" demektir. Anonim kimlik, ürün içinde "Silinmiş hesap" olarak gösterilir
(frontend notu). Rehberdeki 17 test + veri testleri.

## 2. Birden çok örnekte canlı akış (ADR 0001 güncellenir)

PostgreSQL `LISTEN/NOTIFY` (yeni altyapı ve ENV yok; Redis/broker eklenmez):

- Olaylar bugünkü gibi commit sonrası üretilir; alıcı başına yük JSON'a çevrilip tek satır olarak
  `stream_relay_events(id bigserial, created_at, payload text)` tablosuna yazılır ve aynı transaction'da
  `pg_notify('ltz_messaging_stream', id)` gönderilir (NOTIFY yükü 8 KB ile sınırlı olduğundan içerik tabloda).
- Her örnek havuz dışı tek bir bağlantıyla `LISTEN` eder; bildirimde satırı okuyup yalnız kendi açık akışlarına iletir.
- Bağlantı koparsa geri çekilmeyle yeniden bağlanır ve **o örnekteki tüm akışları kapatır**: istemciler yeniden bağlanıp
  geçmişten eşitlenir (kaçan olay sessizce kaybolmaz).
- Kayıtlar 5 dakika sonra silinir (dakikada bir; her örnek çalıştırabilir, idempotent).
- `ltz.messaging.stream.relay=postgres` (varsayılan) | `local` (tek örnek, doğrudan).
- Kullanıcı başına akış sınırı (5) **örnek başına** kalır: amaç örneğin kaynağını korumaktır (karar).

## 3. Kalıcı oran sınırları

GIF arama (30/dk) ve tepki (60/dk) sınırları bellekten DB'ye: `rate_limit_windows(user_id, bucket, window_start, hits)`,
PK üçlüsü; tek atomik `insert … on conflict do update … where hits < limit returning hits`. Eski pencereler dakikada bir
silinir. Mesaj/yükleme oranları zaten DB'den sayılıyor.

## 4. "99+" okunmamış sayısı

`unreadCount` sohbet başına en çok **100**'e kadar sayılır (`LATERAL … LIMIT 100`, `(conversation_id, seq)` indeksi): çok
okunmamış birikmiş sohbetlerde sayım maliyeti sabit kalır. `100` "100 ya da daha fazla" demektir; arayüz `99+` gösterir.

## 5. Bağlantı havuzu

- Havuzdan bağlantı **5 sn** içinde alınamazsa istek beklemeden `503 SERVICE_BUSY` + `Retry-After: 1` (öncesi 30 sn bekleyip
  500).
- Havuz boyutu ENV'den (`DB_POOL_MAX_SIZE`, mevcut); boyutlandırma notu `docs/SECURITY.md`/ops'ta: örnek başına havuz + 1
  `LISTEN` bağlantısı; toplam ≤ PostgreSQL `max_connections`. SSE akışları DB bağlantısı tutmaz (`open-in-view=false`).

## 6. Test ve teslim

TDD, Testcontainers. Silme: rehber §5'in 17 senaryosu + veri senaryoları (mesaj içeriği/ek/tepki kalmaz, başkasının verisi
korunur, grup sahipliği devri, boş grup silinir, birebirde karşı taraf geçmişi kalır ve yazamaz, outbox). Aktarım: iki
bağımsız dinleyicinin aynı olayı alması, yeniden bağlanınca akış kapatma, temizlik. Oran: DB sayacı, iki "örnek" ortak sınır.
99+: 150 okunmamışta `100`. Havuz: havuz tükenince `503`. `pre-push messaging-service`; belgeler: `docs/api.md` (yeni iç uç
kaydı, `unreadCount`, `503`), `database.md`, `er-diagram.md`, `SECURITY.md`, ADR 0001, `NOTLAR.md`, `TASK-07.md`.

## 7. Claude'un kararları (Umut'un kontrolü için)

1. Silinen hesabın mesajları satır olarak kalır ama içerikleri silinir; gönderen "anonim hesap" olur (karşı tarafın
   geçmişi ve sıra bozulmaz). Tüm mesajları fiziksel silmek yerine bu seçildi (rehberin önerdiği iki yoldan biri).
2. Aktif üyesi kalmayan grup tümüyle silinir; birebir sohbet karşı taraf için kalır ama ona yazılamaz.
3. Çoklu örnek için PostgreSQL `LISTEN/NOTIFY`; Redis eklenmez.
4. Akış sınırı örnek başına kalır.
5. Okunmamış sayısı 100'de kesilir (`99+`).
6. Havuz bekleme süresi 5 sn, aşılınca `503`.
7. Auth sahibine (Kerem) haber: dev'de test hesabı silinip messaging görevinin `CONFIRMED` olduğu birlikte kontrol edilmeli.
