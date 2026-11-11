# MAIL-AUTH-REQ-02 — Kullanıcının dil tercihiyle tek dilli mail (Auth + User)

Tarih: 2026-11-10. Gönderen: Kerem / mail-service. Hedef: Kerem / auth-service ve user-service.
Öncelik: MEDIUM. Durum: **İŞ VAR (Auth + User tarafı)**. Mail tarafında kod değişikliği gerekmez.

## Neden

Auth'un mailleri (kayıt kodu, `WELCOME`, parola sıfırlama, hesap silme onayı, güvenlik/durum bildirimleri) şu an **TR ve EN alt alta** gidiyor, çünkü Auth Mail'e `locale` göndermiyor (`HttpMailServiceAdapter`: "locale gönderilmez"). Kullanıcı dashboard'da dilini Türkçe ya da İngilizce seçtiğinde bu tercih User Service'te (`user_preferences.locale`, `tr|en`) duruyor ama mailin diline hiçbir şekilde yansımıyor. Hedef: dil tercihi bilinen kullanıcıya **yalnız o dilde** mail gitsin; bilinmiyorsa bugünkü iki dilli davranış sürsün.

## Mail tarafı (hazır, değişmez)

Mail'in dört Auth ucunun hepsi isteğe bağlı `locale` (`tr` | `en`) alanını **zaten kabul eder** (`verification-codes`, `password-resets`, `account-deletions`, `security-notifications`). Dolu gelirse tek dilli mail üretir (konu, gövde, altbilgi o dilde); boşsa iki dilli. Yanlış değer `400` alır, bu yüzden Auth yalnız `tr`/`en` göndermelidir.

## İstenen 1 — User Service: dil okuma ucu (yeni)

Doğrulandı: User Service'te servisler arası bir tercih ucu yoktur; `GET /api/v1/user/me/preferences` yalnız kullanıcı token'ıyla çalışır (`PreferenceController`).

- Uç: `GET /internal/v1/users/{userId}/locale`
- Yanıt: `{ "locale": "tr" | "en" }`. Profil ya da tercih satırı yoksa `404` (Auth bunu "dil bilinmiyor" sayar). **Yalnız dili döndürür**: tema, hareket ve diğer tercihler dönmez (en az veri).
- Kimlik: servis token'ı (`typ=svc+jwt`, `aud=user-service`, `sub=svc:auth-service`, yeni kapsam `preferences:locale:read`, `account_id` = `userId`). Auth bu token'ı hesap silme akışındaki gibi kendisi imzalar (`ltz.auth.deletion.service-urls.user` ile aynı adres). Başka çağıran kabul edilmez; yanıt ve değer loglanmaz.
- Kabul: doğru token `200`; başka `sub` / kapsam / `account_id` `403`; kullanıcı yok `404`; tokensiz `401`.

## İstenen 2 — Auth: dili okuyup Mail'e göndermek

1. Dört mail portunun (`VerificationCodeMail`, `PasswordResetMail`, `AccountDeletionMail`, güvenlik bildirimi) gövdesine `locale` eklenir; değer User'ın `locale` ucundan gelir.
2. **En iyi çaba:** kısa zaman aşımı (≈2 sn), hata/`404`/zaman aşımında `locale` hiç gönderilmez (bugünkü iki dilli davranış). Dil okuma hatası mail göndermeyi ya da kayıt/giriş akışını asla bozmaz ve bloklamaz (mail çağrıları zaten commit sonrası ayrı iş parçacığında).
3. Kısa önbellek (örn. 60 sn, hesap başına) isteğe bağlı: aynı kullanıcıya art arda mail giderken User'ı yormamak için.
4. **Dili henüz bilinmeyen alıcılar:** kayıt anındaki doğrulama kodu ve profili oluşmamış hesaplar User'da `404` alır; bu mailler iki dilli kalır. İsteğe bağlı iyileştirme (ayrı iş): frontend kayıt/yeniden gönder isteğinde kullanıcının o anki sitesi dilini bir başlıkla (örn. `X-LTZ-Locale: tr|en`) taşır, Auth bunu `locale` olarak kullanır.
5. Testler: tercih `en` iken Mail isteği `locale":"en"` taşır; `tr` iken `"tr"`; User `404`/`5xx`/zaman aşımında `locale` yoktur ve mail yine gider; geçersiz değer (`de` gibi) Mail'e **gönderilmez**; dil okuma loga düşmez.

## Kabul (uçtan uca)

Dashboard'da dili İngilizce yapılmış bir kullanıcıda parola sıfırlama ve güvenlik bildirimi maili **yalnız İngilizce** gelir; Türkçe yapılmışta yalnız Türkçe; tercihi olmayan/yeni kayıtta iki dilli. Mailpit'te konu satırı tek dilli ("LTZ parola sıfırlama" ya da "LTZ password reset", " / " ile birleşik değil).

## Kapsam dışı

- **Tema (koyu/açık):** taşınmaz; mail tabanı koyu, açık tema istemci tercihine bırakıldı (karar 2026-11-09).
- **Mail'in dili kendisinin çözmesi:** Mail kimseyi çağırmaz kuralı bozulmasın diye bu istekte yapılmıyor; dil Auth'tan `locale` olarak gelir.
- **Platform kapısı (`/mail/notices`):** orada dil zaten çağıranın verdiği `locale` ile belirlenir (içeriği çağıran yerelleştirir).

**Referans:** `docs/api.md` → MAIL (dört uçta `locale`), `docs-mail/entegrasyon-rehberi.md`, `user-service/.../PreferenceController`, V73 `user_preferences`.
