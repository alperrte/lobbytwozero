# 0001 — Gerçek zamanlı mesajlaşma: SSE + kayıpsız eşitleme

- **Tarih:** 2026-11-01
- **Durum:** Kabul edildi (messaging-service sahibi Umut'un kararı; LTZ-UMT-MSG-02)
- **Kapsam:** messaging-service, MSG-09 (`message` olayı) ve MSG-11 (`read` olayı, LTZ-UMT-MSG-03). Bildirim servisi kendi kararını ayrıca verir.
- **İlgili:** `docs/architecture.md` §9, `docs/SECURITY.md` §6, `docs/api.md` → MESSAGING

## Bağlam

- `architecture.md` §9 gerçek zamanlı taşımayı (WebSocket ya da SSE) açık karar olarak bırakmıştı. SECURITY.md §0 karar verilmeden uygulama yapılmamasını istiyor.
- Tüm istemci trafiği API Gateway'den geçer. Gateway **Spring Cloud Gateway Server WebMVC**'dir. Bu sürüm WebSocket proxy'lemeyi desteklemiyor; WebSocket için Gateway'in değiştirilmesi ya da servisin dışarı açılması gerekirdi. İkisi de başka sahibin alanı ve mimari değişiklik.
- Tarayıcı oturumu HttpOnly çerezle taşınır, Gateway çerezi `Authorization: Bearer`'a çevirir. `EventSource` özel başlık gönderemez ama çerez gönderebilir (`withCredentials`).
- Gateway'in proxy istemcisinin okuma zaman aşımı 5 sn'dir (`REST_CLIENT_READ_TIMEOUT_MS`).
- Kullanıcı access token'ı 60 sn yaşar; askıya alınan hesap en fazla ~90 sn görünür kalmalıdır.

## Karar

1. **Taşıma: Server-Sent Events.** `GET /api/v1/messaging/stream` (`text/event-stream`). Gönderme normal `POST .../messages` ile yapılır. Akış yalnız bildirim taşır; doğruluk kaynağı veritabanıdır.
2. **Kayıpsızlık sıradan gelir, akıştan değil.** Her mesaj, sohbet satırı kilitliyken sohbet içinde artan bir `seq` alır. Bu yüzden bir sohbette `seq` sırası commit sırasıyla aynıdır. Kopan ya da olay kaçıran istemci `GET .../messages?after=<son seq>` ile eksiklerini kayıpsız çeker. Akışa yalnız **commit edilmiş** mesaj gider (`@TransactionalEventListener(AFTER_COMMIT)`).
3. **Alıcılar gönderim anındaki aktif üyelerdir.** Çıkarılan ya da ayrılan üye yeni mesaj almaz. Gönderenin diğer cihazları da alır.
4. **Bağlantı ömrü token'a bağlıdır.** Akış, token'ın `exp` zamanında (en çok `ltz.messaging.stream.max-duration`, en az 5 sn) kapanır. `EventSource` kendiliğinden yeniden bağlanır ve Gateway yeni token'ı koyar. Böylece askıya alınan ya da silinen hesap en geç token ömrü kadar akış alır.
5. **Canlılık:** her `ltz.messaging.stream.heartbeat` (4 sn) aralığında yorum satırı gönderilir; Gateway'in 5 sn okuma zaman aşımının altında kalır. İlk olay `ready` yanıt başlıklarını hemen gönderir.
6. **Sınırlar:** kullanıcı başına en çok 5 akış (`429 TOO_MANY_STREAMS`). İletim 2 iş parçacıklı, 1000 kuyruklu ayrı bir yürütücüdedir. Kuyruk dolarsa ya da yazma başarısızsa ilgili akış kapanır ve istemci eşitlenir; sınırsız bellek büyümesi olmaz. Gönderim ayrıca kullanıcı başına dakikada 30 mesajla sınırlıdır.

### Olay biçimi

```text
event:ready
data:{"heartbeatSeconds":4}

event:message
id:<conversationId>:<seq>
data:{"id":"…","conversationId":"…","seq":7,"senderId":"…","type":"TEXT","body":"…","clientMessageId":"…","createdAt":"…","readStatus":"SENT"}

event:read
data:{"conversationId":"…","readerId":"…","readUpToSeq":5,"readAt":"…","allReadUpToSeq":5}

:hb
```

`readStatus` gönderenin kendi akışlarında `"SENT"`, diğer üyelerin akışlarında `null` gider. Örnekte kısaltılan `attachment` ve `gif` alanları (LTZ-UMT-MSG-04) metin mesajında `null`'dır; görsel mesajında `attachment` indirme `url`'ini taşır, baytlar akışa girmez.

`id`, `Last-Event-ID` için değil, istemcinin tekilleştirmesi içindir (sohbet + seq tekildir). Yeniden bağlanınca istemci önce sohbet listesini (okunmamış sayıları) yeniler, açık sohbet için `after=<son seq>` ile eşitlenir.

`read` (MSG-11, LTZ-UMT-MSG-03) commit edilmiş okuma ilerlemesinde yalnız okunan aralıkta mesajı olan aktif göndericiye gider. Değerler alıcının kendi mesajlarıyla kırpılır; ham işaretçi, ham su seviyesi ve mesaj içeriği taşınmaz. `id` yoktur. Kaçan olay geçmişteki `readStatus` ile eşitlenir.

**Mesaj değişiklik olayları (LTZ-UMT-MSG-05).** `message_deleted` (herkesten sil, MSG-26), `reaction` (tepki, MSG-23) ve `message_hidden` (benden sil, MSG-25; yalnız silenin kendi akışlarına) commit sonrası ve en iyi çabayla, yalnız mesajı görebilen ve benden silmemiş aktif üyelere gider. `id` taşımazlar. Bu olaylar eski mesajları değiştirdiği için `after=<son seq>` eşitlemesi onları kapsamaz: istemci yeniden bağlanınca açık sohbetin **son sayfasını yeniden çeker** (geçmiş ucu `deleted`, `reactions` ve gizleme durumunu her istekte yeniden hesaplar). Kalıcı bir değişiklik akışı (ör. sohbet başına değişiklik sırası) bu görevde yok; ihtiyaç olursa ayrı karar. `message` olayının yükü artık alıcı başına kurulur ve geçmiş ucundaki `Message` ile aynı yapıdadır (`replyTo` alıcının görünürlüğüne, `deletableForEveryoneUntil` alıcının rolüne göre).

Bir üye gruptan ayrılınca ya da çıkarılınca kalan göndericilerin su seviyesi yükselebilir; durumu `SENT`'ten `READ`'e geçen göndericilere aynı `read` olayı gider (`readerId`, `readUpToSeq`, `readAt` `null`, yalnız `allReadUpToSeq` dolu). Ayrılan kişi olayı almaz. `read` olayı outbox'a yazılmaz ve notification-service'e gitmez.

## Değerlendirilen seçenekler

| Seçenek | Neden seçilmedi / seçildi |
| --- | --- |
| WebSocket (STOMP) | Gateway WebMVC proxy'leyemez. Gateway değişikliği ya da doğrudan erişim gerekir (başka sahip, ağ topolojisi kararı). Çift yönlülük ("yazıyor…") şu an gereksinim değil |
| Yalnız polling | Gateway'den kesin geçer ama "sayfa yenilemeden iletim" gecikmeli olur. Eşitleme ucu (`after`) zaten var; SSE kullanılamazsa istemci aynı uçla polling'e düşebilir |
| **SSE** | Gateway üzerinden düz HTTP. `EventSource` çerezle çalışır, kendiliğinden yeniden bağlanır. Sunucu tarafı basit |

## Sonuçlar ve açık noktalar

- **Gateway üzerinden canlı geçiş NOT VERIFIED.** Servis içi uçtan uca akış testlidir (MockMvc + gerçek PostgreSQL). Gateway WebMVC'nin `text/event-stream` yanıtını tamponlamadan akıtması ve 4 sn kalp atışının okuma zaman aşımını aşması gerçek yığında doğrulanmalıdır. Sorun çıkarsa Gateway sahibiyle `/api/v1/messaging/stream` rotası için akış ve zaman aşımı ayarı konuşulur; istemci geçici olarak `after` polling'ine düşer.
- **Birden çok örnek (LTZ-UMT-MSG-07, 2026-11-10).** Akış kaydı her örneğin belleğindedir; olaylar PostgreSQL `LISTEN/NOTIFY` ile tüm örneklere aktarılır (Redis/broker eklenmedi; yeni altyapı ve ENV yok). Olay commit sonrası alıcı başına JSON olarak `stream_relay_events`'e yazılır ve kimliği `pg_notify('ltz_messaging_stream', id)` ile duyurulur (NOTIFY yükü 8 KB ile sınırlı olduğundan içerik tabloda); yazma tek iş parçacığında, üretim sırasıyla yapılır. Her örnek havuz dışı tek bağlantıyla dinler ve yalnız kendi akışlarına iletir. Dinleme bağlantısı koparsa örnek geri çekilmeyle yeniden bağlanır ve kendi akışlarını kapatır: istemciler yeniden bağlanıp geçmişten eşitlenir. Akış kapatma (hesap silme) içerik taşımadığından yalnız bildirimle gider. Kayıtlar 5 dk sonra silinir. `ltz.messaging.stream.relay=local` tek örnekte doğrudan iletimdir. Kullanıcı başına akış sınırı örnek başınadır (örneğin kaynağını korur). Aktarım eşzamansız olduğundan akış açıldıktan hemen sonra, açılmadan önce üretilmiş bir olay gelebilir; olaylar idempotenttir (`message` `id` taşır, `read` en büyük değer uygulanır).
- **CORS/Origin.** Tarayıcı akışı ancak Gateway'in CORS izin listesindeki kökenden okuyabilir. Akış GET'tir ve durum değiştirmez.
- **`read` olayı en iyi çabadır; eşzamanlı okumada eski değer taşıyabilir (bilinen sınırlama, LTZ-UMT-MSG-03).** İki üye aynı mesajı eşzamanlı okursa her işlem `allReadUpToSeq`'i diğerinin henüz commit edilmemiş işaretçisiyle hesaplar; iki canlı olay da eski değeri taşıyabilir ve gönderen, bir sonraki geçmiş ya da sohbet listesi çekimine kadar mesajı `SENT` görür (o uçlar `readStatus`'u her istekte yeniden hesaplar). Hesabı commit sonrasına taşımak mümkündür ama en iyi çaba sözleşmesi için gereksiz karmaşıklık sayıldı; ihtiyaç olursa ayrı iş olarak ele alınır. Okuyucunun kendi `readUpToSeq` değeri etkilenmez.
- Bildirimler (MSG-16) bu kanalı kullanmaz: `MessageSent` olayı outbox üzerinden notification-service'e gider (teslim notification hazır olunca).
