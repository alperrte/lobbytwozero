# MAIL-AUTH-REQ-01 — Bildirim platformu için Auth işleri ve `WELCOME`

Tarih: 2026-11-09. Gönderen: Kerem / mail-service. Hedef: Kerem / auth-service.
Öncelik: MEDIUM. Durum: **İŞ VAR (Auth tarafı)**. Mail tarafı paralel yapılıyor; Auth hazır olana kadar Mail geliştirmede sahte adres çözücüyle çalışır, prod'da platform kapısı kapalı kalır (fail-closed).

## Neden

Mail Service artık yalnız Auth'un çağırdığı bir servis olmayacak. Hedef: **herhangi bir servisin developerı** (social, notification, event vb.) kısa bir iç istekle kullanıcıya bilgilendirme e-postası attırabilsin; Mail tasarımı, SMTP'yi, güvenliği ve hız sınırını kendisi yönetsin. İki kapı olacak:

| Kapı | Kim çağırır | Alıcı adresi | Durum |
| --- | --- | --- | --- |
| **Auth kapısı** (mevcut) | yalnız `svc:auth-service` | Auth gövdede `recipientEmail` verir (doğrulanmamış/eski adrese gidebilmesi için) | Çalışıyor |
| **Platform kapısı** (yeni) | kayıtlı diğer servisler | Servis yalnız `accountId` verir; **Mail adresi Auth'tan çözer** | Mail tarafı yazılıyor; Auth işleri bu belgede |

Servislere kullanıcı e-postasını dağıtmamak (ve bir servisin keyfi adrese mail gönderememesi) bu tasarımın güvenlik temelidir.

## İstenen 1 — Adres çözme ucu (Mail'in okuyacağı)

Doğrulandı: `GET /internal/v1/accounts/{accountId}` (`account:read`, `InternalAccountAdministrationController`) **e-posta döndürmez**; `AdminAccountResponse` alanları `id, username, status, roles, twoFactorEnabled, createdAt, suspendedUntil`. Mail için ayrı, dar bir uç gerekir.

- Uç: `GET /internal/v1/accounts/{accountId}/contact`
- Kapsam: yeni `account:contact:read` (`TokenClaims` sabiti). **Yalnız `mail-service` istemcisine** tanımlanır; başka hiçbir istemciye verilmez.
- Yanıt: `{ "accountId": "...", "status": "ACTIVE|...", "email": "ad@ornek.com" | null, "emailVerified": true|false }`. Hesap yoksa `404`. Adresi olmayan hesapta (ör. yalnız Steam ile açılmış) `email: null`.
- Kurallar: yanıt gövdesi ve adres **loglanmaz**; parola, token, kod, rol dönmez; mevcut servis token doğrulaması (`typ=svc+jwt`, `aud=auth-service`, `sub=svc:mail-service`, `scope`) aynen uygulanır; Auth hiçbir kuralı gevşetmez.
- Mail bunu **gönderim anında** (arka plan işçisi) çağırır, istek anında değil; Auth o an kapalıysa mail kuyrukta bekler ve yeniden denenir (kayıp yok). Mail artık platform kapısında adresi kuyrukta saklamaz, yalnız `accountId` tutar.

## İstenen 2 — Mail'in Auth'ta servis istemcisi

Mevcut kalıp (`ltz.auth.service-clients.clients.*`, bkz. `admin-service`):

```
ltz.auth.service-clients.clients.mail-service.secret=${AUTH_MAIL_SERVICE_CLIENT_SECRET:}
ltz.auth.service-clients.clients.mail-service.scopes=account:contact:read
```

`AUTH_MAIL_SERVICE_CLIENT_SECRET`: en az 32 karakter, boşsa istemci devre dışı (fail-closed). `AUTH_ADMIN_SERVICE_CLIENT_SECRET`'ın geçtiği her yerde aynı kalıp izlenir: `.env.example` (boş), `docker-compose.yml` (auth-service **ve** mail-service ortamı), `ops/dev.mjs` anahtar üretici. **Bu ortak dosyalarda değişiklik yapılmadı; onay Auth sahibindedir.**

## İstenen 3 — Çağıran servisler için `mail-service` hedefi

Bir servis Mail'e istek atabilmek için Auth'ta kayıtlı olmalıdır (§3.5 kalıbı: her istemci yalnız kendisine tanımlı (hedef, kapsam) çiftleri için token alır, listede olmayan `403 SERVICE_AUDIENCE_FORBIDDEN`):

```
ltz.auth.service-clients.clients.<servis>.secret=${AUTH_<SERVIS>_SERVICE_CLIENT_SECRET:}
ltz.auth.service-clients.clients.<servis>.targets.mail-service=mail:notify
```

- Yeni kapsam: `mail:notify` (`TokenClaims` sabiti). `mail:send` Auth'a özel kalır (Auth kapısı).
- **Şimdi hiçbir servis için satır eklenmesi gerekmiyor.** Mekanizma hazır olsun; bir servis developerı istediğinde ilgili satır ve ENV eklenir (rehberde: `docs-mail/entegrasyon-rehberi.md`, Mail tarafı yazılınca eklenecek).
- Mail, bu token'larda `account_id` claim'i **aramaz** (istemci-kimlikli token'da hesap bağı yoktur); hesabı gövdedeki `accountId` belirler. Çağıran izin tablosu ve hız sınırı Mail'dedir.

## İstenen 4 — `WELCOME` bildirimi

Mail tarafı hazır (V85, canlı). Auth, e-posta doğrulaması tamamlanınca mevcut `security-notifications` ucuna `type=WELCOME` göndermeli (`idempotencyKey, accountId, recipientEmail, type, occurredAt, locale?`; `locale` gönderilmez → iki dilli).

1. `application/port/SecurityNotificationType` enum'una `WELCOME` ekle (ad Mail ile **birebir**); `infrastructure/client/SecurityNotificationTexts` ve ilgili testleri güncelle.
2. Tetik: `EmailVerificationService.confirm` başarıyla tamamlandığında, **commit sonrası**, mevcut `SecurityNotificationDispatcher.notifyAfterCommit` ile (en iyi çaba; hata yalnız loglanır, kayıt/doğrulamayı bozmaz). Doğrulanmamış adrese hoş geldin maili **gönderilmez**.
3. Google/Steam ile yeni hesap açılışında (`GoogleLoginService` vb.) hesap ilk kez doğrulanmış e-postayla oluşuyorsa bir kez gönder; göndermeyeceksen nedenini `docs-auth`'a yaz.
4. Tek seferlik: `idempotencyKey` hesap başına sabit türetilsin (ör. `accountId` + `"WELCOME"` üzerinden deterministik UUID); tekrar doğrulama/bağlama ikinci mail üretmesin.
5. `docs/api.md` Auth "giden sözleşmeler" kaydına `WELCOME` ekle.

## Kabul

- `GET /internal/v1/accounts/{id}/contact`: yalnız `mail-service` istemcisi `200` alır; diğer istemci/kapsam `403`; olmayan hesap `404`; adres ve gövde loga düşmez; testleri var.
- `mail-service` istemcisi `account:contact:read` kapsamıyla Auth'tan token alabilir; `AUTH_MAIL_SERVICE_CLIENT_SECRET` boşken istemci devre dışı.
- `mail:notify` hedefi tanımlı bir test istemcisi `aud=mail-service` token'ı alabilir; tanımsız istemci `403 SERVICE_AUDIENCE_FORBIDDEN`.
- Dev'de yeni hesap: kayıt → kod → doğrulama → `WELCOME` bildirimi **bir kez** Mail'e gider (Mailpit/Gmail'de "LTZ'ye hoş geldin"); başarısız doğrulama tetiklemez; Mail hata verse doğrulama başarılı kalır.

## Bu belgenin kapsamı dışı

Şüpheli giriş / yeni cihaz uyarısı, silme süreci bildirimleri (planlandı/iptal/tamamlandı), kullanıcının "mail almak istemiyorum" tercihi (çağıran servis veya Notification kontrol eder; Mail tutmaz).

**Referans:** `docs-auth/servis-sozlesmeleri.md` §3.4–§3.5, `ServiceClientConfig`, `ServiceClientTokenService`, `docs-mail/SPEC.md` §2–§3, `docs-mail/progress/LTZ-KER-MAIL-04.md`.
