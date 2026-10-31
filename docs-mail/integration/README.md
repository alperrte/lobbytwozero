# Mail Service — entegrasyon (Auth merkezli)

Güncelleme: 2026-10-31. Sorumlu: Kerem. Mail kodu yok; aşağıdakiler Auth'un **gerçek kodu** ve `docs/api.md` kaydına göre türetilmiş taslaktır.

## Kim Mail'e istek atıyor?

| Servis | Mail'e çağrı | Kanıt |
| --- | --- | --- |
| **auth** | **Evet, tek çağıran.** 4 port: doğrulama kodu, parola sıfırlama, hesap silme onayı, güvenlik/durum bildirimi | `auth-service/.../application/port/*Mail*.java`, `*Dispatcher` sınıfları |
| event, notification, admin | Hayır. Kod iskelet; Auth'ta (çağıran→mail) token çifti tanımsız | `docs-auth/servis-sozlesmeleri.md` §3.5 tablosunda yok |
| user, social, community vb. | Hayır | Kodda `mail` çağrısı bulunamadı |
| gateway | `/api/v1/mail/**` rotası var; **kullanıcıya açık uç yok** | — |

Mail kimseyi çağırmaz. Tek yön: Auth → Mail.

## Auth'un Mail'den beklediği davranış (koddan)

- Auth teslimi **commit sonrası, ayrı iş parçacığında** çağırır; sonuç yanıt süresini etkilemez.
- Port istisna fırlatırsa Auth kod/token'ı `DELIVERY_FAILED` yapar ve güvenlik olayı yazar; yeniden gönderme yeni kod üretir. Güvenlik bildirimlerinde hata yalnız loglanır (en iyi çaba, outbox yok).
- Bu yüzden Mail `202` dönmeden önce kaydı **kalıcı** yazmalı; SMTP anında gitmek zorunda değil (kuyruk + retry).
- Auth `recipientEmail`'i her çağrıda verir; kod/bağlantı yalnız Auth belleğinde, Mail'e giderken düz metin (iç ağ, `svc+jwt` korumalı).

## Uçlar (taslak → onaydan sonra `docs/api.md` `## MAIL`)

Hepsi `Internal`, `Authorization: Bearer <svc+jwt>` (`aud=mail-service`, `sub=svc:auth-service`, `scope=mail:send`). Yanıt `202`; hata: 400, 401, 403, 409 (aynı anahtar farklı içerik), 413, 429.

| Uç | Gövde (Auth portundan) | Kaynak port |
| --- | --- | --- |
| `POST /internal/v1/mail/verification-codes` | `idempotencyKey, accountId, recipientEmail, code (^[0-9]{6}$), validitySeconds, locale?` | `VerificationCodeMail` (e-posta değişikliği kodu da bunu kullanır) |
| `POST /internal/v1/mail/password-resets` | `idempotencyKey, accountId, recipientEmail, resetLink, validitySeconds, locale?` | `PasswordResetMail` |
| `POST /internal/v1/mail/account-deletions` | `idempotencyKey, accountId, recipientEmail, confirmationLink, validitySeconds, locale?` | `AccountDeletionMail` |
| `POST /internal/v1/mail/security-notifications` | `idempotencyKey, accountId, recipientEmail, type, occurredAt, locale?`; `type`: `PASSWORD_CHANGED, PASSWORD_RESET, EMAIL_CHANGED, ACCOUNT_SUSPENDED, ACCOUNT_BANNED, ACCOUNT_DISABLED, ACCOUNT_REACTIVATED` | `SecurityNotificationMail` |
| `DELETE /internal/v1/accounts/{accountId}` | — (`scope=account:erase`, `account_id`=yol) → `204` | `AccountDataEraserPort` |

Bağlantılar (`resetLink`, `confirmationLink`): yalnız `https` (dev'de `http://localhost`), kimlik bilgisi içermez; allowlist host. Parola, token, kod hiçbir yanıtta ve loga yazılmaz.

## Auth tarafında yapılacaklar (ayrı küçük görev, Kerem)

1. `MailAdapter`'a `MAIL_SERVICE` (`productionReady=true`) ve RestClient adaptörü (`HttpAccountDataEraser` kopyası: yönlendirme yok, her çağrıda yeni token, 202 dışı = hata). `MAIL_SERVICE_URL` zaten Compose'ta tanımlı.
2. `MailDeliveryConfig` fail-fast'i açılır: prod artık bu adaptörle başlar → **B5 kapanır**, B12/B19 (bildirim e-postaları) kısmen kapanır.
3. `api.md` Auth "Giden sözleşmeler" kaydı 4 uca genişletilir.
4. Mail bittikten sonra: `WELCOME` tipi (MAIL-05), "şüpheli giriş" (MAIL-03) Auth'ta eklenir.

## Gerekirse ileride
Başka bir servis Mail kullanmak isterse: Auth'ta (çağıran→`mail-service`) çifti + `mail:send` kapsamı, Mail'de `type`↔`sub` izin tablosuna satır. Bu bir istek geldiğinde yapılır; şimdi yapılmaz.
