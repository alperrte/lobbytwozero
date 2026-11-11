# LTZ-UMT-MSG-05 — Mesaja Yanıt, İlet, Tepki Ver ve Sil: Tasarım

**Servis:** `messaging-service` · **Branch:** `feature/messaging-service` (main'e merge yok) · **Gereksinimler:** MSG-19, MSG-20, MSG-23, MSG-24, MSG-25, MSG-26
**Durum:** TASARIM — Umut onayladı (2026-11-09); uygulama planı `docs-messaging/TASK-05-PLAN.md`

## 1. Amaç

Kullanıcılar mesajlarla WhatsApp'taki gibi etkileşir: bir mesaja yanıt verir, mesajı başka sohbetlere iletir,
emoji ile tepki bırakır, mesajı yalnız kendi görünümünden ya da herkesten siler. Bu task yalnız backend'dir;
mesaj işlem menüsü (MSG-24) ve görünüm frontend'in işidir, backend menünün ihtiyaç duyduğu bilgiyi verir ve
her işlemin yetkisini kendisi uygular.

Ürün gerçekten yayına çıkacak ve binlerce kullanıcıya açılacak (Umut). Bu yüzden yeni tablolar ve sorgular
baştan yük düşünülerek tasarlanır: sayfa başına toplu sorgu, mesaj başına sorgu yok, satır sayısı kullanıcı
eylemiyle sınırlı.

Bu task, MSG-01..04'te kurulanların üzerine gelir: sohbet içi `seq`, `visible_from_seq` görünürlük sınırı,
`MessageView` tek okuma modeli, SSE akışı (`MessageStreamDispatcher`, `AFTER_COMMIT`), outbox, görsel
depolama (`message_attachments`).

## 2. Kapsam

**Hedefler (Umut'un kararı: "WhatsApp gibi olsun")**

- **Yanıtla (MSG-19):** aynı sohbetteki bir mesaja yanıt; yanıtta alıntı önizlemesi.
- **İlet (MSG-20):** erişilebilen bir mesajı tek seferde en fazla 5 sohbete iletme; "İletildi" ve
  "Çok kez iletildi" etiketi.
- **Tepki (MSG-23):** kişi başına mesaj başına tek tepki, herhangi bir tek emoji; değiştirme ve kaldırma;
  kimin hangi tepkiyi verdiğini görme.
- **Benden sil (MSG-25):** herhangi bir mesajı yalnız kendi görünümünden kaldırma.
- **Herkesten sil (MSG-26):** gönderen, gönderimden sonraki 48 saat içinde; grupta OWNER/ADMIN başkasının
  mesajını da aynı süre içinde silebilir. Yerinde "Bu mesaj silindi" kalır; içerik, dosya ve tepkiler gerçekten
  silinir.
- **Menü bilgisi (MSG-24):** mesaj yanıtı menünün kararını verebilmek için gereken alanları taşır (bkz. §4.6).
- Tüm değişiklikler canlı güncellenir (SSE).

**Hedef dışı**

- Mesaj düzenleme (WhatsApp'ta var, PDA'da yok).
- Tepki ve silme için `notification-service` bildirimi (notification-service hazır değil; outbox'a olay yazılmaz).
- Mesaj sabitleme, yıldızlama, mesaj arama.
- Frontend (menü, alıntı görünümü, tepki seçici).
- Hesap silme iç ucu (TASK-02'den beri açık nokta; bu task'ın yeni tabloları hesap silmede silinecek şekilde
  tasarlanır ama uç yazılmaz).
- Sesli mesaj (LTZ-UMT-MSG-06) ve yayına hazırlık işleri (çoklu örnek SSE, "99+", sınırlayıcılar, depolama
  arayüzü) — ayrı tasklar.

**Anti-hedefler** — uç noktalar çalışsa bile işi başarısız sayar:

- Herkesten silinen bir mesajın metni, dosyası, GIF'i ya da tepkileri herhangi bir uçtan, alıntıdan, iletilmiş
  kopyadan dönen alanlardan ya da SSE'den hâlâ okunabiliyorsa (iletilmiş kopyanın **kendi** içeriği hariç,
  bkz. §3.2).
- Alıntı veya iletme yoluyla bir kullanıcı, göremediği bir mesajın içeriğini görebiliyorsa (görünürlük sınırı
  altı, başka sohbet, benden silinmiş değil — bkz. §3.1).
- Görsel iletmek dosya baytlarını kopyalıyorsa (DB her iletmede 1 MB büyür).
- Geçmiş sayfası mesaj başına ayrı tepki/alıntı/gizleme sorgusu atıyorsa.
- "Benden sil" diğer katılımcının görünümünü, okunmamış sayısını ya da okundu durumunu değiştiriyorsa.
- Herkesten silme `seq` sırasını bozuyor, okunmamış sayılarını geri alıyor ya da okundu durumunu (MSG-11)
  çelişkili hale getiriyorsa.

## 3. Kurallar

### 3.1 Görünürlük (her işlemin ortak ön koşulu)

`U` kullanıcısı `C` sohbetindeki `M` mesajını **görebilir**, ancak ve ancak:

- `U`, `C`'nin aktif üyesidir,
- `M.seq ≥ U.visible_from_seq`,
- `M`, `U` için benden silinmemiştir.

Göremediği bir mesaja işlem yapan kullanıcı, mesaj hiç yokmuş gibi mevcut `404 MESSAGE_NOT_FOUND` alır
(var/yok farkı sızdırılmaz — MSG-12 ile aynı).

Herkesten silinmiş mesaj görünürdür (yer tutucu olarak), ama üzerinde yanıtlama, iletme ve tepki
yapılamaz: `422` ve yeni bir hata kodu (ör. `MESSAGE_DELETED`). Benden silme ve okundu bilgisi (MSG-12)
çalışmaya devam eder.

### 3.2 Herkesten silme (MSG-26)

- **Kim:** mesajın göndereni; ya da grup sohbetinde aktif OWNER/ADMIN (başkasının mesajı için). Birebir
  sohbette yalnız gönderen. MEMBER başkasının mesajını silemez → `403`. OWNER/ADMIN, diğer yöneticilerin ve
  sahibin mesajlarını da silebilir (WhatsApp davranışı); işlemi yapanın o andaki rolü geçerlidir.
- **Ne zaman:** `created_at`'ten itibaren 48 saat. Süre dolmuşsa `422`. Süre yapılandırmadan okunur
  (varsayılan `PT48H`).
- **Ne olur (aynı transaction'da):**
  - Mesaj satırı **kalır** (`seq`, `sender_id`, `created_at`, `type` korunur), böylece sıra, okunmamış sayısı
    ve okundu bilgisi bozulmaz.
  - `deleted_at` ve `deleted_by` dolar. Gövde (`body`) ve GIF anlık kaydı (`gif_*`) temizlenir.
  - Mesajın görsel bağlantısı kaldırılır; içeriğe başka hiçbir mesaj bağlı değilse içerik baytları da silinir
    (bkz. §3.4 paylaşılan içerik).
  - Mesajın tüm tepkileri silinir.
  - Bu mesajdan **iletilmiş kopyalar etkilenmez** (WhatsApp davranışı: iletilen her kopya ayrı bir mesajdır).
  - Bu mesaja **verilmiş yanıtlar** kalır; alıntıları "silindi" olarak döner (§3.3).
- Zaten herkesten silinmiş mesajı tekrar silmek idempotenttir (`204`).
- DB CHECK'leri silinmiş mesajı tutarlı tutar: silinmişse gövde ve `gif_*` boş; `type = TEXT` için "gövde
  zorunlu" kuralı silinmiş mesajda gevşer.
- Silen kişi ve zaman loglanmaz içerikle birlikte; loglarda yalnız kimlikler değil olay sayısı/sonuç kalır
  (mevcut gizlilik kuralı: içerik loglanmaz).

### 3.3 Yanıtla (MSG-19)

- Gönderim isteğine isteğe bağlı `replyToMessageId` eklenir (metin, görsel, GIF mesajlarının hepsinde).
- Hedef aynı sohbette olmalı ve gönderen tarafından görülebilmeli (§3.1); herkesten silinmiş olmamalı.
  Başka sohbetin mesajı ya da görülemeyen mesaj → `404`.
- Yanıt, hedefe kalıcı bir referans tutar. Mesajlar fiziksel olarak silinmediği için referans kopmaz; hesap
  silme gibi gelecekteki fiziksel silmeler için referans `ON DELETE SET NULL` olur.
- Yanıt mesajı okunurken alıntı önizlemesi **okuyan kişiye göre** hesaplanır:
  - Hedef okuyan için görünürse: `{messageId, seq, senderId, type, snippet}`. `snippet` gövdenin ilk 100
    karakteridir (UTF-16 birimi; vekil çifti ortadan bölünmez); görsel/GIF'te açıklama ya da boş.
  - Hedef herkesten silinmişse: `{messageId, seq, senderId, deleted: true}` — içerik yok.
  - Hedef okuyan için görünmüyorsa (görünürlük sınırı altı ya da okuyan onu benden silmiş):
    `{messageId, unavailable: true}` — gönderen ve içerik yok. **Varsayım (kararım):** benden silinen mesaj
    alıntıda da gizlenir; WhatsApp alıntıyı gösterir ama "benden sil"in amacı o içeriği görmemektir.
- Yanıt, yeniden deneme (`clientMessageId`) kuralına dahildir: aynı `clientMessageId` ile farklı
  `replyToMessageId` gelirse mevcut çakışma davranışı (`isRetryOf`) uygulanır.

### 3.4 İlet (MSG-20)

- Kaynak mesaj, iletenin göreceği bir mesaj olmalı (§3.1) ve herkesten silinmemiş olmalı.
- Hedefler: 1–5 farklı sohbet kimliği. Her hedefte iletenin **yazma yetkisi** (aktif üye) ve birebir sohbette
  **engel kontrolü** (MSG-15, fail-closed 503) aynen gönderimdeki gibi uygulanır. Hedeflerden biri uygun
  değilse **hiçbiri** gönderilmez (hepsi ya da hiçbiri — kararım; kullanıcıya hangi hedefin neden
  reddedildiği hata detayında döner, başka sohbetin varlığı sızdırılmaz).
- Kaynak sohbet hedeflerden biri olabilir (WhatsApp izin verir). Aynı hedef iki kez verilirse `400`.
- Her hedefte yeni ve bağımsız bir mesaj oluşur: yeni `seq`, gönderen = ileten, yeni outbox `MessageSent`
  olayı, SSE `message` olayı. Gönderim oran sınırına her hedef bir mesaj olarak sayılır.
- Kopyalanan: tür, gövde, GIF anlık kaydı, görsel. Kopyalanmayan: yanıt referansı, tepkiler, okundu bilgisi.
- **İletme sayacı:** kopyanın `forward_count = kaynak.forward_count + 1`. Yanıtta `forwarded: true` (sayaç ≥ 1)
  ve `forwardedManyTimes: true` (sayaç ≥ 5, WhatsApp eşiği). Orijinal gönderenin kimliği kopyada
  **tutulmaz ve dönmez** (WhatsApp davranışı, gizlilik).
- **Görsel paylaşımı (zorunlu):** iletmede dosya baytları kopyalanmaz. İçerik tek bir yerde saklanır, birden
  çok mesaj ona bağlanabilir. İçerik, ona bağlı son mesaj bağlantısı da kalkınca silinir. Hedef sohbette
  görselin indirme yolu hedef sohbetin yolu olur
  (`GET /conversations/{hedef}/attachments/{id}`); erişim kuralı MSG-17'deki gibi o sohbetteki mesajın
  görünürlüğüne bağlıdır. Şemanın nasıl ayrılacağı (ör. içerik tablosu + mesaj başına bağlantı satırı)
  uygulayıcıya bırakılmıştır; koşul, `message_attachments`'taki mevcut güvenlik kurallarının (boyut CHECK,
  MIME CHECK, yükleyen/oran sınırı, bekleyen yükleme temizliği) korunmasıdır.
- **İdempotency:** istemci istek başına bir `clientMessageId` gönderir; hedef başına türetilmiş benzersiz
  kimlik kullanılır, aynı istek tekrarlanırsa çift mesaj oluşmaz.

### 3.5 Tepki (MSG-23)

- Kişi başına mesaj başına **tek** tepki. `PUT` yeni tepkiyi koyar ya da eskisinin yerine yazar; `DELETE`
  kaldırır (yoksa da `204`).
- Tepki **tek bir emoji** olmalıdır: tek grafem kümesi (ZWJ dizileri, ten rengi, bayraklar dahil), emoji
  karakteri içermeli, en fazla 32 UTF-16 birimi. Harf, rakam, boşluk, birden fazla emoji, eşlenmemiş vekil →
  `400`. Sabit liste yok (WhatsApp her emojiye izin verir).
- Görülebilen (§3.1) ve herkesten silinmemiş her mesaja verilebilir; kendi mesajına da.
- Oran sınırı: kullanıcı başına dakikada 60 tepki değişikliği (yapılandırılabilir) — tepki spam'i ve SSE
  sağanağına karşı.
- Okuma modeli:
  - Geçmiş, sohbet listesi `lastMessage`, gönderim yanıtı ve SSE `message` olayı aynı `MessageView`'ı taşır;
    ona tepki özeti eklenir: emoji başına sayı (sayıya göre azalan, sonra ilk tepki zamanı) ve isteyenin kendi
    tepkisi. Sayfa için **tek toplu sorgu**.
  - Ayrı uç: bir mesajın tepki listesi (kim, hangi emoji, ne zaman). Sohbetin aktif ve mesajı görebilen her
    üyesi görebilir (WhatsApp davranışı). Grup üye sınırı sayfalamayı gereksiz kılar.

### 3.6 Benden sil (MSG-25)

- Görülebilen herhangi bir mesaj (kendisinin ya da başkasının, herkesten silinmiş olsa da) yalnız o kullanıcı
  için gizlenir. İdempotent. Geri alma yok (WhatsApp'ta "geri al" kısa süreli bir istemci işidir; kapsam dışı).
- Gizlenen mesaj o kullanıcının geçmişinden, `lastMessage`'ından ve alıntılarından (§3.3) çıkar; tepki
  listelerine erişemez. Diğer katılımcılar için hiçbir şey değişmez.
- Sohbet listesinde `lastMessage`, kullanıcının gizlemediği son mesajdır. Bu, binlerce kullanıcıda sohbet listesi
  başına sınırlı maliyetle hesaplanmalıdır (sohbet başına geriye doğru tarama sınırsız olmamalı).
- Okunmamış sayısı: gizlenen mesaj okunmamış sayılmaz. **Okundu durumu (MSG-11) değişmez** — gizlemek okumak
  değildir; gönderen için mesaj `SENT` kalabilir.
- Mesajın görselini indirme: gizleyen kullanıcı için `404`.

### 3.7 Yetki özeti

| İşlem | Kim | Koşul | Hata |
| --- | --- | --- | --- |
| Yanıtla | Aktif üye (yazma yetkisi, engel) | Hedef görünür, silinmemiş, aynı sohbet | 404 / 422 |
| İlet | Kaynak: görebilen; hedef: yazabilen | ≤ 5 hedef, engel yok, kaynak silinmemiş | 400 / 403 / 404 / 422 / 503 |
| Tepki ver/kaldır | Görebilen aktif üye | Mesaj silinmemiş, tek emoji | 400 / 404 / 422 / 429 |
| Tepkileri listele | Görebilen aktif üye | — | 404 |
| Benden sil | Görebilen aktif üye | — | 404 |
| Herkesten sil | Gönderen; grupta OWNER/ADMIN | 48 saat içinde | 403 / 404 / 422 |

Ayrılmış ya da çıkarılmış üye hiçbir işlemi yapamaz (mevcut aktif üye kuralı).

## 4. API

Yollar `MessagingApiPaths` ve gateway öneki (`/api/v1/messaging`) ile uyumlu. Uç adları ve gövde alanları
aşağıdaki gibi önerilir; adlandırmada küçük sapmalar uygulayıcıya bırakılmıştır, davranış bırakılmamıştır.

1. **`POST /conversations/{id}/messages`** — gövdeye isteğe bağlı `replyToMessageId` (uuid).
2. **`POST /conversations/{id}/messages/{messageId}/forward`** — `{ clientMessageId, targetConversationIds: [..1-5] }`
   → `201`, hedef başına oluşan mesajlar (`MessageResponse` listesi).
3. **`PUT /conversations/{id}/messages/{messageId}/reaction`** — `{ emoji }` → `200` güncel tepki özeti.
   **`DELETE .../reaction`** → `204`.
4. **`GET /conversations/{id}/messages/{messageId}/reactions`** — `{ messageId, reactions: [{ userId, emoji, reactedAt }] }`.
5. **`DELETE /conversations/{id}/messages/{messageId}?scope=me|everyone`** → `204`. (`scope` zorunlu; yanlış
   değer `400`.)
6. **`MessageResponse` yeni alanlar** (geçmiş, liste, gönderim, SSE `message`):
   - `replyTo`: §3.3'teki önizleme ya da `null`.
   - `forwarded`, `forwardedManyTimes`: boolean.
   - `deleted`: boolean; `true` ise `body`, `attachment`, `gif`, `reactions` boş/null döner.
   - `reactions`: `{ summary: [{ emoji, count }], mine: emoji | null }` — tepki yoksa boş özet.
   - `deletableForEveryoneUntil`: isteyen bu mesajı herkesten silebiliyorsa son an, silemiyorsa `null`
     (menü bu alanla "Herkesten sil" seçeneğini gösterir; yetkiyi backend ayrıca uygular).
7. Hata kodları yeni `MessagingErrorCode` değerleri olarak eklenir (ör. `MESSAGE_DELETED`,
   `DELETE_WINDOW_EXPIRED`, `NOT_ALLOWED_TO_DELETE`, `INVALID_REACTION`, `TOO_MANY_FORWARD_TARGETS`);
   mevcut ProblemDetail biçimi korunur.

## 5. Canlı güncelleme (SSE)

Mevcut desen: olay yalnız commit'ten sonra (`AFTER_COMMIT`), en iyi çaba ile, yalnız mesajı görebilen aktif
üyelere. Yük, alıcı başına kurulur (görünürlük ve "kendi tepkim" alıcıya göre değişir).

| Olay | Ne zaman | Kime | Yük |
| --- | --- | --- | --- |
| `message` (mevcut) | Yanıt ve iletme dahil her yeni mesaj | Mevcut kural | Yeni alanlarla `MessageResponse` |
| `message_deleted` | Herkesten silme | Mesajı görebilen aktif üyeler (benden silenler hariç) | `{ conversationId, messageId, seq, deletedAt }` |
| `reaction` | Tepki konduğunda/değiştiğinde/kalktığında | Mesajı görebilen aktif üyeler | `{ conversationId, messageId, summary, mine }` |
| `message_hidden` | Benden silme | Yalnız silen kullanıcının kendi bağlantıları (diğer cihazlar) | `{ conversationId, messageId }` |

Kaçırılan olaylar için mevcut `after` eşitlemesi yetmez (silme ve tepki eski mesajları değiştirir). İstemci
yeniden bağlandığında açık sohbetin son sayfasını yeniden çeker; bu, ADR 0001'e not edilir. Kalıcı bir
"değişiklik akışı" bu task'ta yok.

## 6. Veri

Migration `V135` (MESSAGING aralığı V131–V140). `docs/database.md` §3.8'deki öneri satırları
(`message_reactions`, `message_hidden_for_users`, `reply_to_id`, `forwarded_from_id`, `deleted_for_everyone_at`)
bu tasarımla güncellenir. Farklar bilinçlidir:

- `message_reactions` önerideki PK `(message_id, user_id, emoji)` yerine **`(message_id, user_id)`** — kişi
  başına tek tepki.
- `forwarded_from_id` tutulmaz; yerine `forward_count` (orijinal gönderene geri iz bırakmamak ve silinen
  kaynağa referans kalmaması için).
- `docs/database.md`'deki genel "soft delete (`deleted_at`)" kuralı uygulanır, ama içerik alanları gerçekten
  temizlenir.

Gereken satır düzeyi bilgiler:

- `messages`: `reply_to_message_id` (aynı tablo FK, `ON DELETE SET NULL`), `forward_count` (≥ 0),
  `deleted_at`, `deleted_by` (REF). CHECK: silinmişse içerik alanları boş.
- Tepki: mesaj FK (`ON DELETE CASCADE`), kullanıcı REF, emoji, zaman. Sayfa için `message_id IN (...)`
  sorgusunu karşılayan indeks.
- Benden silme: mesaj FK (`ON DELETE CASCADE`), kullanıcı REF, zaman. Sayfa için ve `lastMessage` hesabı için
  `(user, message)` erişimini karşılayan indeks.
- Görsel paylaşımı için §3.4'ün gerektirdiği ayrım.

Satır sayısı sınırı: tepki ≤ mesaj × aktif üye (kişi başına tek); gizleme ≤ kullanıcının eylemi. Okuma
aralığı gibi her sayfa yüklemesinde büyüyen bir tablo yoktur.

## 7. Güvenlik

- Tüm işlemler mevcut JWT + aktif üye kontrolünden geçer; kimlik yalnız token'dan alınır.
- Var/yok sızıntısı yok: başka sohbetin mesajı, görülemeyen mesaj ve olmayan mesaj aynı `404`.
- İletme, hedef sohbetin engel kontrolünü atlamanın yolu olamaz.
- Herkesten silmede içerik DB'den gerçekten kalkar (yedeklerde saklama süresi DB yedek politikasına bağlıdır —
  `docs/SECURITY.md` §13'e not).
- Tepki emojisi gövde gibi doğrulanır (bozuk UTF-16 yok); istemciye kaçışsız döner, frontend metin olarak
  basar.
- Oran sınırları: tepki 60/dk, iletme gönderim sınırına sayılır.
- İçerik ve emoji loglanmaz.

## 8. Test ve teslim

- Testcontainers PostgreSQL ile entegrasyon testleri, mevcut desen. Her kural (§3.1–3.7) ve her anti-hedef (§2)
  için en az bir test. Özellikle: silinmiş mesajın içeriği hiçbir yoldan dönmez; sonradan katılan alıntıdan eski
  içeriği göremez; iletilen görsel bayt kopyalamaz ve orijinal silinince kopyada kalır; benden sil diğerini
  etkilemez; sayfa başına sorgu sayısı mesaj sayısıyla büyümez.
- `pre-push messaging-service` geçmeli (kapsam, değişen satır, Checkstyle/PMD); commit öncesi `pre-push full dev`
  ve SonarQube (Umut'un akışı).
- Belgeler: `docs/api.md` MESSAGING kayıtları, `docs/database.md` §3.8, `docs/er-diagram.md` §9,
  `docs/SECURITY.md` §13, ADR 0001 (yeni SSE olayları ve yeniden bağlanma notu),
  `docs-security/messaging-service/NOTLAR.md`, `docs-messaging/TASK-05.md`.
- Frontend notu `TASK-05.md` açık noktalarına: menü (hover ok, dokunmatikte uzun basma, klavyede erişim),
  fotoğrafı göndermeden önce istemcide ~1600 px'e küçültme.

## 9. Uygulayıcıya bırakılanlar

- Görsel içerik paylaşımının şema biçimi (§3.4 koşullarıyla).
- Uç ve alan adlarındaki küçük farklar (§4).
- `lastMessage`'ın gizlenmiş mesajları atlama yöntemi (sınırlı maliyet koşuluyla).
- Tek emoji doğrulamasının uygulanışı (JDK grafem ayrımı ya da eşdeğeri; yeni bağımlılık eklenmeden).
- İletme idempotency kimliğinin türetilme biçimi.

## 10. Benim verdiğim kararlar (Umut'un kontrolü için)

1. Herkesten silme süresi 48 saat; grupta OWNER/ADMIN başkasının mesajını aynı sürede silebilir.
2. Benden silinen mesaj alıntıda da "görüntülenemiyor" olur.
3. İletmede hedeflerden biri uygun değilse hiçbiri gönderilmez.
4. İletilen kopyada orijinal gönderen tutulmaz; "Çok kez iletildi" eşiği 5.
5. Tepki listesini sohbetin mesajı görebilen her üyesi görebilir.
6. Tepki oran sınırı 60/dk.
7. Benden silme geri alınamaz; okundu durumunu değiştirmez.
8. Kaçırılan silme/tepki olayları için istemci yeniden bağlanınca son sayfayı yeniden çeker.
