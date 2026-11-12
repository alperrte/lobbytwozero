# Mail Service için not: Auth tarafı hazır, Mail'in yapacakları (MAIL-AUTH-REQ-01)

Kaynak: Auth PR #98 (`feature/auth-service` → `main`). Bu not PR main'e girdikten sonra Mail tarafına iletilir.
Auth'un ne yaptığı için bkz. `cevap-2026-11-09-mail-auth-req-01.md`; bu dosya yalnız **Mail'in yapması gerekenleri** söyler.

## 1. Main'e girince Auth'ta hazır olanlar

| Ne | Değer |
| --- | --- |
| Token ucu | `POST /internal/v1/service-tokens` gövde `{"clientId":"mail-service","clientSecret":"<sır>"}` → `{accessToken, tokenType, expiresIn:60, scope:"account:contact:read"}` |
| Adres çözme | `GET /internal/v1/accounts/{accountId}/contact` başlık `Authorization: Bearer <accessToken>` |
| Yanıt | `{ "accountId", "status", "email" (null olabilir), "emailVerified" }` |
| Hatalar | `404` hesap yok · `401` token yok/geçersiz/süresi dolmuş · `403` kapsam yok · istemci anahtarı boş/yanlışsa token ucu `401` |
| Sır (ENV) | `AUTH_MAIL_SERVICE_CLIENT_SECRET` (≥32 karakter). `docker-compose.yml`'de **mail-service ortamına da** eklendi; Mail aynı adla okuyabilir |
| Token ömrü | 60 sn; her gönderim turunda ya da süre dolmadan önce yenile, önbelleğe alıp tekrar kullan |

`audience` **verilmez** (varsayılan `auth-service`). `mail-service` başka bir hedef için token alamaz (`403`).

## 2. Mail tarafının yapması gerekenler

1. **Adres çözücüyü bağla:** platform kapısında kuyruktaki iş yalnız `accountId` taşır; gönderim anında (arka plan işçisi) `/contact` çağrılır. Geliştirmedeki sahte çözücü yerine bu istemci kullanılır.
2. **Sonuçlara göre davran:**
   - `email == null` (yalnız Steam hesabı ya da anonimleşmiş hesap) → gönderme, kalıcı atla (yeniden deneme anlamsız).
   - `emailVerified == false` → bilgilendirme maili gönderme (Auth de doğrulanmamış adrese `WELCOME` göndermez).
   - `status` hesabın gönderime uygun olup olmadığına Mail karar verir (Auth `ACTIVE` dışını da döndürür).
   - `404` → kalıcı atla; `5xx`/zaman aşımı/bağlantı hatası/`401` (token yenile) → kuyrukta bekle, yeniden dene (kayıp yok).
3. **Loglama:** yanıt gövdesi ve adres loglanmaz; hata loguna yalnız durum kodu ve `accountId` yaz.
4. **`WELCOME` türünü kabul et:** Auth e-posta doğrulaması başarılı olunca ve Google ile yeni hesap açılınca `POST /internal/v1/mail/security-notifications` ucuna `type=WELCOME` gönderir (`locale` yok → iki dilli, `idempotencyKey` hesap başına sabit). Main'deki `SecurityNotificationType` enum'unda (`mail-service/.../domain/enums`) `WELCOME` **yok**; V85 ile canlı olan sürüm main'e girmeden Auth'un gönderdiği `WELCOME` reddedilir.
5. **`docs/api.md` MAIL bölümü:** "bilinmeyen `type` (`WELCOME` dahil) `400` döner" notu V85 sonrası eskidir, güncellenmeli.
6. **Migration uyumu:** yerel Mail veritabanı sürüm **85**'te, main'deki Mail kodu **83**'e kadar migration biliyor (`Schema has a version (85) that is newer than the latest available migration (83)`). V84/V85 main'e girmeden yerel/dev ortamlar bu uyarıyı verir.

## 3. Auth'un şu an gördüğü davranış (Mail `WELCOME`'u bilmezken)

Doğrulama başarılı kalır; Auth yalnız şunu loglar ve devam eder:
`Security notification not delivered type=WELCOME cause=VerificationCodeDeliveryException`
Mail `WELCOME`'u kabul edince bu uyarı kaybolur ve "LTZ'ye hoş geldin" maili tek sefer gider.

## 4. Mail'in doğrulama listesi (main'e girdikten sonra)

- [ ] `AUTH_MAIL_SERVICE_CLIENT_SECRET` hem auth hem mail ortamında dolu (`node ops/dev.mjs keys` boş olanı üretir).
- [ ] Token alma `200`, `scope=account:contact:read`.
- [ ] Var olan hesap `200` ve doğru `email`; rastgele UUID `404`; tokensiz `401`.
- [ ] Yeni hesap: kayıt → kod → doğrulama → Mailpit'te "LTZ'ye hoş geldin" **tek** mail; yanlış kodda yok; ikinci doğrulama denemesinde ikinci mail yok.
- [ ] Auth kapalıyken kuyruk bekler, açılınca gider (kayıp yok).
- [ ] Gerçek Gmail/SMTP ile uçtan uca **NOT VERIFIED** (Auth tarafında yalnız Mailpit/test).

## 5. Yeni servis eklenirse (`mail:notify`)

Bir servis Mail'e istek atmak isterse Auth'ta şu satır ve kendi sırrı eklenir (şimdilik hiçbir servis için tanımlı değil):
`ltz.auth.service-clients.clients.<servis>.targets.mail-service=mail:notify`
Token `aud=mail-service`, `sub=svc:<servis>`; `account_id` claim'i yoktur, hesabı gövdedeki `accountId` belirler. İzin tablosu ve hız sınırı Mail'dedir.
