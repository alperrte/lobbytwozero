# Mail Service — Servis Entegrasyon Rehberi

Güncelleme: 2026-11-09. Sorumlu: Kerem. **Kimler için:** kendi servisinde bir olay olunca kullanıcıya e-posta attırmak isteyen servis geliştiricileri ve ajanları. Şablon yazmanız, SMTP bilmeniz ya da Mail'in iç yapısına dokunmanız gerekmez.

> **Notification Service ile karıştırmayın.** Notification, uygulama içi bildirimleri üretir ve "kime ne bildirilecek, kullanıcı e-posta ister mi" kararını verir. Mail Service yalnız **e-postayı güvenle gönderir**; ne zaman, kime, neden gönderileceğine karar vermez. Notification ileride Mail'i sizin gibi çağıracak.

## 30 saniyelik özet

Bir hesaba bilgilendirme maili attırmak için:

1. **Bir kerelik kayıt:** Auth'ta servisinize `mail:notify` izni ve Mail'in izin tablosunda bir satır (aşağıda, Adım 1).
2. **Token al:** Auth'tan `audience=mail-service` token'ı (60 sn).
3. **İstek at:** `POST /internal/v1/mail/notices` ile `accountId`, `kind`, `title`, `message` (+ isteğe bağlı düğme). `202` dönerse kuyruğa yazıldı.

Alıcının e-posta adresini **bilmeniz ve vermeniz gerekmez**: yalnız `accountId`. Adres Mail tarafından Auth'tan çözülür. Tasarım (logo, renk, altbilgi, yasal not) Mail'indir; siz yalnız içerik yazarsınız.

## Adım 1 — Kayıt (bir kerelik)

İki taraf birbirini doğrular; ikisi de gerekir.

**Auth'ta** (`auth-service/src/main/resources/application.properties`; yanıt/talep: `docs-mail/integration/auth/auth-service-talepleri/`):

```
ltz.auth.service-clients.clients.<servisiniz>.secret=${AUTH_<SERVISINIZ>_SERVICE_CLIENT_SECRET:}
ltz.auth.service-clients.clients.<servisiniz>.targets.mail-service=mail:notify
```

Sırrı (`AUTH_<SERVISINIZ>_SERVICE_CLIENT_SECRET`, en az 32 karakter) `docker-compose.yml`, `.env.example` ve `ops/dev.mjs` anahtar üreticisine ekleyin (örnek: `AUTH_SOCIAL_SERVICE_CLIENT_SECRET`). Servisiniz zaten başka bir hedef için Auth istemcisiyse yalnız `targets.mail-service` satırı yeter.

**Mail'de** (`mail-service/src/main/resources/application.properties`, PR ile; Mail sahibi ekler):

```
ltz.mail.platform.callers.<servisiniz>.account-per-hour=5
ltz.mail.platform.callers.<servisiniz>.caller-per-hour=500
```

Kayıtsız servisin token'ı geçerli imzayla bile `401` alır. Sınırlar isteğe bağlıdır (varsayılan 5 ve 500).

## Adım 2 — Token

```
POST {AUTH_SERVICE_URL}/internal/v1/service-tokens
{ "clientId": "<servisiniz>", "clientSecret": "<sır>", "audience": "mail-service" }
→ { "accessToken": "...", "tokenType": "Bearer", "expiresIn": 60, "scope": "mail:notify" }
```

Token 60 sn yaşar: önbelleğe alıp süre dolmadan (≈15 sn önce) yenileyin, her istekte yeniden almayın. Hazır örnek: `community-service/.../AuthServiceTokenClient.java` ve `game-service/.../AuthServiceTokenClient.java` (aynı deseni kopyalayın). Auth `401/403` verirse yeniden denemeyin, yapılandırmayı düzeltin.

## Adım 3 — İstek

```
POST {MAIL_SERVICE_URL}/internal/v1/mail/notices
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "idempotencyKey": "3f6c0c3e-8b1a-5d7e-9a2b-6f0e4c1d2a10",
  "accountId": "0b6f7a52-5c0e-4f0a-9a52-2f1b9d6f6a11",
  "kind": "INFO",
  "title": "Yeni arkadaşlık isteği",
  "message": "Ahmet sana arkadaşlık isteği gönderdi.",
  "actionLabel": "İsteği gör",
  "actionLink": "https://ltz.example/friends/requests"
}
→ 202 (gövde yok)
```

| Alan | Zorunlu | Kural |
| --- | --- | --- |
| `idempotencyKey` | evet | UUID. **Olayınızın kimliğinden türetin** (örn. `UUID.nameUUIDFromBytes("friend-request:" + istekId)`). Aynı olay iki kez gelirse tek mail gider; aynı anahtar farklı içerikle `409` |
| `accountId` | evet | Alıcı hesap (Auth hesap kimliği). E-posta adresi vermeyin |
| `kind` | evet | `INFO` (pembe), `SUCCESS` (yeşil), `WARNING` (turuncu), `DANGER` (kırmızı). Yalnız renk ve etiketi belirler |
| `title` | evet | 1–80 karakter, tek satır, düz metin. Konu satırı da bu olur |
| `message` | evet | 1–600 karakter, düz metin. Boş satırla paragraf ayrılır |
| `actionLabel` + `actionLink` | ikisi birlikte | Etiket ≤30; adres **https** ve Mail'in izinli host listesinde (`MAIL_ALLOWED_LINK_HOSTS`) olmalı |
| `locale` | hayır | `tr` (varsayılan) ya da `en`. Bilgilendirme **tek dillidir**: metni siz yerelleştirirsiniz, Mail çevirmez |

Bilinmeyen alan `400` alır. Gövde en çok 16 KB.

### Yanıtlar

| Kod | Anlam | Ne yapmalı |
| --- | --- | --- |
| `202` | Kalıcı kuyruğa yazıldı (e-postanın gittiği anlamına gelmez; birkaç sn içinde gönderilir, SMTP kesintisinde yeniden denenir) | İşiniz bitti |
| `400` | `VALIDATION_FAILED` alan hatası; `INVALID_CONTENT` kontrol/yön değiştirme karakteri ya da yalnız biri verilmiş düğme; `INVALID_LINK` izinli olmayan/https olmayan adres | İsteği düzeltin, yeniden göndermeyin |
| `401` | Token geçersiz/süresi dolmuş **ya da servisiniz Mail'in izin tablosunda değil** | Yeni token alın; sürerse Adım 1 |
| `403` | Kapsam `mail:notify` değil | Auth `targets.mail-service` satırını kontrol edin |
| `409` | Aynı anahtar, farklı içerik | Anahtarınızı olaya bağlı türetin |
| `429` | `RATE_LIMITED`: hesap başına ya da toplam saatlik sınır | Yeniden denemeden bekleyin; döngü/hata arayın |
| `503` | `PLATFORM_NOT_CONFIGURED` (Mail'in Auth anahtarı yok) ya da kayıt yazılamadı | Kısa süre sonra aynı anahtarla yeniden deneyin |

`5xx`/zaman aşımında **aynı `idempotencyKey` ile** yeniden deneyin: tekrar mail üretmez.

## Gönderilmeyen durumlar (hata dönmez)

`202` aldıktan sonra Mail, gönderim anında alıcıyı Auth'tan çözer. Şu durumlarda mail **gitmez ve size bildirilmez**: hesap yok/silinmiş, adresi olmayan hesap (yalnız Steam ile açılmış), doğrulanmamış adres, hesap `ACTIVE` değil (askıya alınmış/yasaklı/devre dışı). Auth kapalıysa mail kuyrukta bekler ve yeniden denenir (en çok 24 saat).

## Sizin sorumluluğunuz

- **Kullanıcı tercihi:** Mail "bu kullanıcı e-posta almak istiyor mu" bilgisini **tutmaz ve denetlemez**. Mail attırmadan önce kullanıcının tercihine siz (veya Notification Service) bakın.
- **İçerik:** Düz metin yazın (HTML yazmayın; Mail kaçışlar). Parola, token, kod, kişisel veri koymayın; mesajı kısa tutun.
- **Sıklık:** Sınırlar kötüye kullanım ve hatalı döngüyü durdurmak içindir, kota değildir. Toplu/duyuru göndermek için bu uç uygun değildir.
- **Eşzamanlı akışı bloke etmeyin:** İsteği olay işlemenizden ayrı atın (commit sonrası); hata ana işinizi bozmamalı.

## Özel şablon isteği (Mail geliştiricisine)

Genel bilgilendirme yetmiyorsa (örn. arkadaşlık isteği için özel tasarım) Mail sahibine şunları iletin: tür adı (`FRIEND_REQUEST`), hangi olayda gittiği, alanlar (hangi bilgiler değişken), TR ve EN metin, örnek görünüm fikri. Mail tarafında bu bir `.mail` dosyasıdır (`sablon-rehberi.md`); migration gerekmez.

## Yerelde deneme

1. `.env`'de `AUTH_MAIL_SERVICE_CLIENT_SECRET` ve kendi servis sırrınız dolu olmalı (`node ops/dev.mjs keys`).
2. Auth ve Mail satırları (Adım 1) eklenmiş, `docker compose up -d --build auth-service mail-service <servisiniz>`.
3. Mail'in SMTP'si boşsa e-posta Mailpit'e düşer: `http://127.0.0.1:8025`.
4. Token alıp (Adım 2) isteği atın; Mailpit'te mailin tasarımını görün.

## Sık sorulanlar

- **Neden e-posta adresi vermiyorum?** Adres Auth'ta yaşar; servislere dağıtılmaz, böylece bir servis keyfi adrese mail gönderemez ve adresler sızmaz.
- **Neden `202`, gönderildi değil?** Mail önce kalıcı kaydeder, sonra SMTP ile gönderir; SMTP kesintisinde mail kaybolmaz.
- **Neden `kind` var?** Kullanıcıya mailin önemini renkle anlatır (bilgi, başarı, uyarı, önemli); içerik yine sizindir.
- **Hata arıyorum:** Mail'in logunda mesaj içeriği ve adres yoktur; yalnız mesaj kimliği, çağıran adı ve sonuç kodu. `docker compose logs mail-service --tail 50`.
