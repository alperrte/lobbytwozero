# 0001 — Gerçek zamanlı mesajlaşma: SSE + kayıpsız eşitleme

- **Tarih:** 2026-11-01
- **Durum:** Kabul edildi (messaging-service sahibi Umut'un kararı; LTZ-UMT-MSG-02)
- **Kapsam:** messaging-service, MSG-09. Bildirim servisi kendi kararını ayrıca verir.
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
data:{"id":"…","conversationId":"…","seq":7,"senderId":"…","type":"TEXT","body":"…","clientMessageId":"…","createdAt":"…"}

:hb
```

`id`, `Last-Event-ID` için değil, istemcinin tekilleştirmesi içindir (sohbet + seq tekildir). Yeniden bağlanınca istemci önce sohbet listesini (okunmamış sayıları) yeniler, açık sohbet için `after=<son seq>` ile eşitlenir.

## Değerlendirilen seçenekler

| Seçenek | Neden seçilmedi / seçildi |
| --- | --- |
| WebSocket (STOMP) | Gateway WebMVC proxy'leyemez. Gateway değişikliği ya da doğrudan erişim gerekir (başka sahip, ağ topolojisi kararı). Çift yönlülük ("yazıyor…") şu an gereksinim değil |
| Yalnız polling | Gateway'den kesin geçer ama "sayfa yenilemeden iletim" gecikmeli olur. Eşitleme ucu (`after`) zaten var; SSE kullanılamazsa istemci aynı uçla polling'e düşebilir |
| **SSE** | Gateway üzerinden düz HTTP. `EventSource` çerezle çalışır, kendiliğinden yeniden bağlanır. Sunucu tarafı basit |

## Sonuçlar ve açık noktalar

- **Gateway üzerinden canlı geçiş NOT VERIFIED.** Servis içi uçtan uca akış testlidir (MockMvc + gerçek PostgreSQL). Gateway WebMVC'nin `text/event-stream` yanıtını tamponlamadan akıtması ve 4 sn kalp atışının okuma zaman aşımını aşması gerçek yığında doğrulanmalıdır. Sorun çıkarsa Gateway sahibiyle `/api/v1/messaging/stream` rotası için akış ve zaman aşımı ayarı konuşulur; istemci geçici olarak `after` polling'ine düşer.
- **Tek örnek varsayımı.** Akış kaydı bellektedir. messaging-service birden çok örnekte çalışırsa örnekler arası yayın gerekir (ör. PostgreSQL `LISTEN/NOTIFY` ya da broker). O zaman ayrı ADR yazılır. Mesaj kaybı olmaz (eşitleme DB'den), yalnız diğer örneğe bağlı istemci olayı geç görür.
- **CORS/Origin.** Tarayıcı akışı ancak Gateway'in CORS izin listesindeki kökenden okuyabilir. Akış GET'tir ve durum değiştirmez.
- Bildirimler (MSG-16) bu kanalı kullanmaz: `MessageSent` olayı outbox üzerinden notification-service'e gider (teslim notification hazır olunca).
