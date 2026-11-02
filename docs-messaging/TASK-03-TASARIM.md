# LTZ-UMT-MSG-03 — Okundu Durumları ve Mesajı Görenler: Tasarım

**Servis:** `messaging-service` · **Branch:** `feature/messaging-service` (main'e merge yok) · **Gereksinimler:** MSG-11, MSG-12
**Durum:** TASARIM — onaylandı ve uygulandı (bkz. `docs-messaging/TASK-03.md`)

## 1. Amaç

Mesaj gönderen kişi, WhatsApp'taki gibi, kendi mesajının okunup okunmadığını görür. Grup sohbetinde mesaja
basarak kimin hangi saatte okuduğunu ve kimin henüz okumadığını görebilir. Durum, sayfa yenilemeden
canlı güncellenir.

Bu iş MSG-02'de kurulan okuma işaretçisinin (`conversation_members.last_read_seq`, `POST .../read`) üzerine
kurulur. Okunmamış sayısı (MSG-13) ile okundu durumu aynı kaynaktan beslenir, bu yüzden ikisi birbiriyle
çelişmez.

## 2. Kapsam

**Hedefler (Umut'un kararları)**

- İki durum vardır: `SENT` (sunucuya kaydedildi) ve `READ` (okundu).
- "Okumak", istemcinin `POST .../read` ile işaretçiyi ilerletmesidir. İşaretçi bir seq'e kadar ilerlerse, o
  seq dahil öncesindeki her şey okunmuş sayılır. "Görüldü" ile "okundu" aynı olaydır.
- Grupta bir mesaj, **şu anda aktif olan ve o mesajı görebilen herkes** okuyunca `READ` olur. Mesajdan sonra
  katılanlar ile ayrılmış ya da çıkarılmış kişiler hesaba katılmaz.
- Okuyanlar listesinde **okuma saati** de gösterilir.
- Okuyanları **yalnızca mesajı gönderen kişi** görebilir.
- Durum şu üç yerde görünür:
  1. Mesaj geçmişinde, kendi mesajlarının yanında.
  2. Sohbet listesinde, son mesaj kendisine aitse.
  3. Ayrı bir "mesaj bilgisi" ucunda (MSG-12).
- Durum canlı güncellenir: mevcut SSE akışına bir `read` olayı eklenir.

**Hedef dışı**

- Okundu bilgisini kapatma ayarı. Bu taskta herkesin okundu bilgisi görünür.
- "Cihaza iletildi" aşaması (çift gri tik). Bu serviste cihaz teslimi takip edilmiyor.
- Okuma olayının outbox'a veya `notification-service`'e gönderilmesi.
- Aynı kullanıcının diğer cihazlarına okunmamış sayısının eşitlenmesi.
- "Yazıyor..." göstergesi.

**Anti-hedefler**

Aşağıdakiler, uç noktalar çalışsa bile işi başarısız sayar:

- Başkasının mesajını kimin okuduğu, gönderen dışındaki biri tarafından API'den ya da SSE'den öğrenilebiliyorsa.
- Okunmamış sayısı ile okundu durumu birbiriyle çelişiyorsa.
- Okuyanlar listesinde mesajı hiç göremeyecek biri görünüyorsa. Örnek: yeniden eklenmiş bir üye için
  `rejoin` işaretçiyi yapay olarak ileri taşır ve bu kişi okumuş gibi görünür.
- Sohbet açıkken her okuma çağrısı sınırsız satır üretiyorsa.

## 3. Kurallar

### 3.1 Uygun okuyucu

`S` sıra numaralı ve `A` tarafından gönderilmiş mesaj için **uygun okuyucu**, şu üç koşulu sağlayan üye `m`'dir:

- `m` aktif üyedir (`left_at is null`),
- `m ≠ A`,
- `m.visible_from_seq ≤ S`.

`m`'nin bu mesajı okumuş sayılması için `m.last_read_seq ≥ S` olmalıdır.

### 3.2 Durum ve su seviyesi

Bir mesaj, en az bir uygun okuyucusu varsa ve uygun okuyucularının hepsi okuduysa `READ` olur. Diğer
durumlarda `SENT` olur.

Durum, mesaj başına sorgu atmadan bir **su seviyesi** (watermark) ile hesaplanır. `A` kullanıcısının bir
sohbetteki kendi mesajları için su seviyesi:

```
W(A) = min over aktif m ≠ A of  max(m.last_read_seq, m.visible_from_seq - 1)
```

`A`'nın `S ≤ W(A)` olan her mesajı `READ` olur. `A` dışında aktif üye yoksa `W(A) = 0` olur ve tüm mesajlar
`SENT` kalır.

Formüldeki `visible_from_seq - 1` terimi, mesajdan sonra katılan üyeyi uygun okuyucu olmaktan çıkarır.
`rejoin` işaretçiyi `visible_from - 1` değerine çekse bile sonuç bozulmaz.

**Bilinen uç durum:** Mesajın tüm uygun okuyucuları ayrılmış, yerlerine yalnızca sonradan katılanlar gelmiş
olabilir. Bu durumda formül mesajı `READ` gösterir. Mesaj bilgisi ucunda ise okuyanlar ve okumayanlar
listesi boş döner. Bu davranış kabul edildi ve testle sabitlenecek.

Aynı formül üç yerde kullanılır: geçmiş, sohbet listesi ve SSE olayı. Formül tek bir yerde yazılmalıdır.

### 3.3 Okuma saati

Okuma saati, her işaretçi ilerlemesinde bir **okuma aralığı** satırı olarak saklanır. Satır şu alanları
taşır: üye, ilerlemeden önceki işaretçi + 1, yeni işaretçi ve zaman. Bir okuyucunun `S` mesajını okuma
saati, `S`'yi kapsayan aralığın zamanıdır.

Satır sayısı mesaj sayısıyla değil, gerçek ilerleme sayısıyla büyür. İşaretçi ilerlemiyorsa satır yazılmaz:
aynı seq'in tekrarı, geri gitme ya da son mesajın ötesine geçme bu kapsamdadır.

Aralık satırı yazan yerler:

- `markRead`: işaretçi gerçekten ilerlediyse.
- Mesaj gönderimi: gönderenin işaretçisi kendi mesajına ilerler (MSG-02 kuralı, "yanıtlayan okumuştur").
  İşaretçi ilerlediyse burada da aralık yazılır. Kendi mesajı aralığa dahil olsa da sorun yoktur, çünkü
  gönderen kendi mesajının uygun okuyucusu değildir.

Aralık satırı yazmayan yer:

- `join` ve `rejoin`'in işaretçiyi taşıması gerçek bir okuma değildir, aralık yazılmaz.

Migration'dan önce okunmuş mesajların aralığı yoktur. Bu mesajlar için `readAt` alanı `null` döner.
Geçmişe dönük doldurma yapılmaz; geliştirme verisi olduğu için kabul edildi.

## 4. API değişiklikleri

Kod içi yollar `MessagingApiPaths` ile, gateway önekleri `docs/api.md` ile uyumlu olmalıdır.

1. **`GET /conversations/{id}/messages`.** `MessageResponse` yapısına `readStatus` alanı (`SENT` | `READ`)
   eklenir. Alan yalnızca isteyen kişinin kendi mesajlarında doludur, başkalarının mesajlarında `null`
   olur. `MessageResponse` akıştaki `message` olayının verisiyle aynı yapıdadır. Bu yüzden akışta yeni
   gönderilen mesaj alıcıya `readStatus: null`, gönderene `SENT` ile gider. Gönderen ile alıcının akış
   yükü bu noktada ayrışabilir.
2. **`GET /conversations`.** `lastMessage` isteyen kişinin mesajıysa `readStatus` dolu olur, değilse `null`
   olur.
3. **Yeni uç (MSG-12): `GET /conversations/{id}/messages/{messageId}/reads`.**
   - Erişilemeyen sohbet, başka sohbetin mesajı veya üyenin göremediği mesaj için mevcut 404 davranışı
     korunur. Var olan ile olmayan arasında fark sızdırılmaz.
   - Aktif üye olup mesajın göndereni olmayan kişi `403` alır. Hata kodu yeni bir `MessagingErrorCode`
     değeridir.
   - Yanıt:

     ```json
     {
       "messageId": "...",
       "seq": 42,
       "readStatus": "READ",
       "readers": [{ "userId": "...", "readAt": "..." }],
       "pending": [{ "userId": "..." }]
     }
     ```

   - `readers` listesi `readAt` değerine göre yeniden eskiye sıralanır, `null` değerler en sonda yer alır.
     `pending` listesi katılma sırasına göre sıralanır.
   - Listeler 3.1'deki uygun okuyucu kuralına uyar. Grup üye sınırı (`GroupProperties`) sayfalamayı gereksiz
     kılar.
   - Birebir sohbette de çalışır; liste tek kişiden oluşur.
4. **`POST /conversations/{id}/read`.** Sözleşme değişmez. Yalnızca 3.3'teki aralık yazımı ve 5. bölümdeki
   olay eklenir.

## 5. Canlı güncelleme (SSE)

`R` kullanıcısının işaretçisi `a` değerinden `b` değerine (`a < b`) ilerlediğinde, commit'ten sonra
`read` adlı bir olay yayınlanır. Mevcut `MessageStreamDispatcher` deseni izlenir: `AFTER_COMMIT` sonrasında,
en iyi çaba ile.

- **Kime:** `(a, b]` aralığında mesajı olan aktif göndericilere gider; `R` hariç tutulur. Bu aralıkta mesajı
  olmayan üyeye olay gitmez. Gizlilik kuralının (2. bölüm) gereği budur.
- **Yük:** Her alıcı için ayrı kurulur ve alıcının kendi mesajlarıyla kırpılır.

  ```json
  { "conversationId": "...", "readerId": "R", "readUpToSeq": "...", "readAt": "...", "allReadUpToSeq": "..." }
  ```

  - `readUpToSeq`: alıcının `≤ b` olan en büyük kendi seq'i. `R`, alıcının bu seq'e kadarki mesajlarını okudu.
  - `allReadUpToSeq`: alıcının `≤ W(alıcı)` olan en büyük kendi seq'i; böyle bir mesaj yoksa `0`.

  İstemci, kendi mesajlarından `seq ≤ allReadUpToSeq` olanları `READ` yapar.

  Ham `b` ya da ham `W` gönderilmez. Bu değerler, alıcıya başkalarının mesajlarının okunup okunmadığını
  sızdırır; örneğin A, R'nin B'nin mesajını okuduğunu öğrenebilir (anti-hedef #1). Yük mesaj içeriği taşımaz.
  Kırpma, gizlilik testlerinde grup senaryosuyla kanıtlanmalıdır: A seq 5, B seq 9, R `(0, 9]` okur; A
  `readUpToSeq = 5` almalıdır.
- Olay kaçarsa istemci geçmiş ucundan eşitlenir. `readStatus` alanı her istekte yeniden hesaplanır.
- Mesaj gönderiminde gönderenin işaretçisi ilerlediğinde de olay üretilir. Sohbette yazan kişi, önündeki
  mesajları okumuş sayılır.
- **Ayrılma ve çıkarma (Umut'un kararı: canlı gitsin).** Bir üye ayrıldığında ya da çıkarıldığında kalan
  göndericilerin su seviyesi yükselebilir. Bu durumda `GroupMembershipService.leave` ve `removeMember`,
  commit'ten sonra aynı `read` olayını gönderir.
  - **Kime:** su seviyesi gerçekten yükselen, yani en az bir mesajı `SENT`'ten `READ`'e geçen aktif
    göndericilere.
  - **Yük:** `readerId`, `readUpToSeq` ve `readAt` değerleri `null` olur; yalnızca `allReadUpToSeq` dolu
    gelir.
  - Ayrılan ya da çıkarılan kişi bu olayı almaz.

## 6. Veri

- Yeni migration `V133__...` (MESSAGING aralığı V131–V140): okuma aralığı tablosu eklenir. Bu tabloda:
  - konuşma FK ve `on delete cascade` vardır,
  - kullanıcı REF'tir, FK yoktur (mevcut desen),
  - `from_seq ≤ to_seq` ve `from_seq ≥ 1` için CHECK vardır,
  - `(conversation_id, user_id, to_seq)` üzerinde, kapsayan aralığı bulmaya yarayan bir indeks vardır.
- Ayrılan ya da çıkarılan üyenin aralık satırları silinmez. Bu satırlar listede kullanılmaz, çünkü kişi
  aktif değildir.
- `docs/database.md` §3.8, `er-diagram.md` §9 ve kök `migrations.md` güncellenir.

## 7. Eşzamanlılık

- `advanceLastRead` yalnızca ileri taşır ve etkilenen satır sayısını döner. Aralığın `from` değeri için eski
  işaretçi, aynı transaction içinde ve satır güncellemesiyle tutarlı şekilde okunmalıdır. Aynı kullanıcının
  eşzamanlı iki okuma isteği çakışan ya da boşluklu aralık üretmemelidir. Örneğin `RETURNING` ya da satır
  kilidi kullanılabilir; yöntem uygulayıcıya bırakıldı. Bu durum testle kanıtlanmalıdır.
- Mesaj gönderimi sohbet satırını zaten kilitliyor. Oradaki ilerleme de aynı yardımcıdan geçmelidir.

## 8. Test

Testler mevcut desende, Testcontainers PostgreSQL ile yazılır.

**Durum hesabı**

- Birebir sohbette `SENT`'ten `READ`'e geçiş.
- Grupta kısmi okuma (`SENT` kalır) ve tam okuma (`READ` olur).
- Sonradan katılan üyenin eski mesajı bloklamaması.
- Ayrılan üyenin hesaptan düşmesi.
- `rejoin` olan üyenin eski mesajlarda okuyucu gibi görünmemesi.
- 3.2'deki uç durum.

**Gizlilik**

- Başkasının mesajında `readStatus` alanının `null` olması.
- Mesaj bilgisi ucunda gönderen dışındaki üyenin `403` alması.
- Üye olmayan kişinin `404` alması.
- SSE `read` olayının yalnızca aralıkta mesajı olan göndericiye gitmesi.
- SSE yükünün alıcının kendi mesajlarıyla kırpılması (5. bölümdeki A/B/R senaryosu).
- Ayrılma ve çıkarmada, durumu `READ`'e dönen göndericiye olay gitmesi; durumu değişmeyene gitmemesi.

**Okuma saati**

- Aralıklarla doğru `readAt` dönmesi.
- İşaretçi ilerlemediğinde satır yazılmaması.
- Migration öncesi okunmuş mesajda `readAt` alanının `null` olması.
- Eşzamanlı okuma isteklerinin çakışmaz aralık üretmesi.

**Olay**

- Olayın commit'ten sonra gitmesi; geri alınan işlemde olay gitmemesi.

**Diğer**

- `FlywayMigrationTest` V133'ü kapsamalı.
- Satır kapsamı mevcut seviyenin (~%97) altına düşmemeli.
- Yeni Checkstyle, PMD ya da Sonar bulgusu olmamalı.

## 9. Uygulayıcıya bırakılanlar

- Okuma aralığı tablosunun ve entity'nin adı.
- Su seviyesi hesabının SQL'de mi yoksa bellekte mi yapılacağı. Grup üye sınırı küçük olduğu için aktif
  üyeleri tek sorguda çekip bellekte hesaplamak yeterlidir.
- `readStatus` için enum adı.
- Yeni hata kodunun adı.
- SSE olayının hub'da nasıl adlandırılacağı. Olay adı `read` olmalıdır.

## 10. Teslim

Teslimde şunlar yapılır:

- `docs/api.md` MESSAGING kayıtları güncellenir.
- ADR `0001` güncellenir; yeni olay türü eklenir.
- `NOTLAR.md` güncellenir.
- `docs-messaging/TASK-03.md` yazılır.
- `pre-push` ile `SAFE TO COMMIT` sonucu alınır.
- SonarQube çalıştırılır.
- Commit mesajı önerilir. Commit'i Umut atar ve main'e merge edilmez.
