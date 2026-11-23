# MAIL-SERVICE — YENİ GÜVENLİK BİLDİRİMİ TÜRLERİ (2026-11-05)

**Gönderen:** Kerem (auth-service) · **Branch:** `feature/mail-service` · **Durum:** İŞ VAR (Mail tarafı)

## NE DEĞİŞTİ

Auth, hesap ele geçirme belirtisi olan 4 olayda hesap sahibine e-posta göndermeye başlıyor. Mevcut uç ve gövde aynı: `POST /internal/v1/mail/security-notifications` (`idempotencyKey, accountId, recipientEmail, type, occurredAt`). Yalnız `type` değerleri artıyor; parola, token, kod, IP ya da cihaz bilgisi **taşınmaz**.

| Yeni `type` | Ne zaman gider |
|---|---|
| `TWO_FACTOR_ENABLED` | İki adımlı doğrulama (TOTP) açıldı (Steam hesabında zorunlu kurulum dahil) |
| `TWO_FACTOR_DISABLED` | İki adımlı doğrulama kapatıldı |
| `RECOVERY_CODE_USED` | Tek kullanımlık kurtarma kodu kullanıldı (giriş ya da hassas işlem) |
| `EXTERNAL_IDENTITY_LINKED` | Hesaba Google ya da Steam kimliği bağlandı |

Neden bu ve neden `WELCOME`/`SUSPICIOUS_LOGIN` değil: karşılama e-postası güvenlik katkısı sağlamaz; şüpheli giriş IP/cihaz sezgisi ister ve yanlış alarm üretir. Bunlar ise deterministiktir: olay olduysa bildirim gider. Auth `WELCOME` ve `SUSPICIOUS_LOGIN` göndermeyecek; Mail'in bu türler için uç ya da şablon hazırlamasına gerek yok.

## MAIL TARAFINDA YAPILACAK (sırayla)

1. `SecurityNotificationType` (Mail) enum'una dört değer ekle; her biri yeni bir `MailType` değerine bağlanır (ör. `TWO_FACTOR_ENABLED`, `TWO_FACTOR_DISABLED`, `RECOVERY_CODE_USED`, `EXTERNAL_IDENTITY_LINKED`). Adlar Auth'unkilerle **birebir** aynı olmalı.
2. Yeni Flyway migration (V82'yi değiştirme, V83 aç):
   - `mail_messages.ck_mail_messages_type` CHECK kısıtını dört yeni değeri içerecek şekilde yeniden oluştur (kısıt yeni türü reddeder, aksi halde kayıt yazılamaz → 503).
   - `mail_templates` içine her tür için TR + EN şablon ekle. Gövdede yalnız `{{occurredAt}}` kullanılır (mevcut `ACCOUNT_*` şablonlarıyla aynı kalıp). Metin önerisi aşağıda.
3. Testler: dört türün her biri için `202`, bilinmeyen tür `400`, şablon üretimi.
4. `docs-mail/SPEC.md` ve `docs-mail/integration/README.md` içindeki `type` listesini güncelle (Auth tarafında `docs/api.md` güncellendi).

## YAYIN SIRASI (önemli)

Önce **Mail** yayınlanır, sonra bu Auth sürümü. Auth, Mail tanımadığı bir türü gönderirse Mail `400` döner; Auth bildirimi "en iyi çaba" gönderdiği için yalnız uyarı loglar, hiçbir kullanıcı işlemi bozulmaz. Yani sıra bozulursa kayıp yalnız o e-postaların gitmemesidir. Dev'de (Mailpit adaptörü) Auth kendi metinlerini kullanır; Mail hazır olmadan da denenebilir.

## ŞABLON METNİ ÖNERİSİ (TR / EN, konu + gövde, hepsi `{{occurredAt}}` içerir)

| Tür | TR konu | TR gövde | EN konu | EN gövde |
|---|---|---|---|---|
| `TWO_FACTOR_ENABLED` | LTZ iki adımlı doğrulama açıldı | LTZ hesabınızda iki adımlı doğrulama açıldı. | Two-step verification was enabled | Two-step verification was enabled on your LTZ account. |
| `TWO_FACTOR_DISABLED` | LTZ iki adımlı doğrulama kapatıldı | LTZ hesabınızda iki adımlı doğrulama kapatıldı. | Two-step verification was disabled | Two-step verification was disabled on your LTZ account. |
| `RECOVERY_CODE_USED` | LTZ kurtarma kodu kullanıldı | LTZ hesabınızda bir kurtarma kodu kullanıldı. | A recovery code was used | A recovery code was used on your LTZ account. |
| `EXTERNAL_IDENTITY_LINKED` | LTZ hesabınıza harici hesap bağlandı | LTZ hesabınıza bir Google ya da Steam hesabı bağlandı. | An external account was linked | A Google or Steam account was linked to your LTZ account. |

Her gövdenin sonuna mevcut güvenlik şablonlarındaki gibi "Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin" uyarısı ve `Tarih: {{occurredAt}}` eklenir.

- Durum: **İŞ VAR** (Mail tarafında migration + enum + şablon + test)
