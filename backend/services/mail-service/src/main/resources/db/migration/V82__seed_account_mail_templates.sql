-- MAIL-01..05, 08, 09: hesap e-postası şablonları (TR/EN). Gövdeler yalnız içerik parçasıdır; logo, düzen ve altbilgi
-- mail/layout.html içindedir. {{değişken}} değerleri HTML bölümünde kaçışlanır; değişken yoksa e-posta üretilmez.
-- Kullanılabilen değişkenler: code, link, validityMinutes, occurredAt.

INSERT INTO mail_templates (id, code, locale, subject, body_html, body_text, version, active, created_at, updated_at)
SELECT gen_random_uuid(), t.code, t.locale, t.subject, t.body_html, t.body_text, 1, true, now(), now()
FROM (VALUES
-- ---------------------------------------------------------------- EMAIL_VERIFICATION
('EMAIL_VERIFICATION', 'tr',
 $s$LTZ e-posta doğrulama kodunuz$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">E-posta adresinizi doğrulayın</h1><p style="margin:0 0 16px 0">LTZ hesabınızı doğrulamak için aşağıdaki kodu girin. Kod {{validityMinutes}} dakika geçerlidir.</p><p style="margin:0 0 16px 0;padding:16px;text-align:center;font-size:32px;letter-spacing:8px;font-weight:700;font-family:Consolas,Menlo,monospace;background:#f3f4f6;border-radius:8px;color:#111827">{{code}}</p><p style="margin:0">Bu isteği siz yapmadıysanız bu e-postayı yok sayabilirsiniz. Kodu kimseyle paylaşmayın.</p>$h$,
 $x$LTZ doğrulama kodunuz: {{code}}
Kod {{validityMinutes}} dakika geçerlidir.
Bu isteği siz yapmadıysanız bu e-postayı yok sayın. Kodu kimseyle paylaşmayın.$x$),
('EMAIL_VERIFICATION', 'en',
 $s$Your LTZ email verification code$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Verify your email address</h1><p style="margin:0 0 16px 0">Enter the code below to verify your LTZ account. The code is valid for {{validityMinutes}} minutes.</p><p style="margin:0 0 16px 0;padding:16px;text-align:center;font-size:32px;letter-spacing:8px;font-weight:700;font-family:Consolas,Menlo,monospace;background:#f3f4f6;border-radius:8px;color:#111827">{{code}}</p><p style="margin:0">If you did not make this request you can ignore this email. Never share the code with anyone.</p>$h$,
 $x$Your LTZ verification code: {{code}}
The code is valid for {{validityMinutes}} minutes.
If you did not make this request, ignore this email. Never share the code with anyone.$x$),
-- ---------------------------------------------------------------- PASSWORD_RESET_LINK
('PASSWORD_RESET_LINK', 'tr',
 $s$LTZ parola sıfırlama$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Parolanızı sıfırlayın</h1><p style="margin:0 0 16px 0">LTZ parolanızı sıfırlamak için aşağıdaki düğmeyi kullanın. Bağlantı {{validityMinutes}} dakika geçerlidir ve yalnız bir kez kullanılabilir.</p><p style="margin:0 0 16px 0"><a href="{{link}}" style="display:inline-block;background:#6d28d9;color:#ffffff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Parolayı sıfırla</a></p><p style="margin:0 0 16px 0;font-size:13px">Düğme çalışmazsa bu adresi tarayıcınıza yapıştırın:<br><span style="word-break:break-all">{{link}}</span></p><p style="margin:0">Bu isteği siz yapmadıysanız bu e-postayı yok sayın; parolanız değişmez.</p>$h$,
 $x$LTZ parolanızı sıfırlamak için bağlantıyı açın ({{validityMinutes}} dakika geçerli, tek kullanımlık):
{{link}}

Bu isteği siz yapmadıysanız bu e-postayı yok sayın; parolanız değişmez.$x$),
('PASSWORD_RESET_LINK', 'en',
 $s$LTZ password reset$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Reset your password</h1><p style="margin:0 0 16px 0">Use the button below to reset your LTZ password. The link is valid for {{validityMinutes}} minutes and can be used only once.</p><p style="margin:0 0 16px 0"><a href="{{link}}" style="display:inline-block;background:#6d28d9;color:#ffffff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Reset password</a></p><p style="margin:0 0 16px 0;font-size:13px">If the button does not work, paste this address into your browser:<br><span style="word-break:break-all">{{link}}</span></p><p style="margin:0">If you did not make this request, ignore this email; your password will not change.</p>$h$,
 $x$Open the link to reset your LTZ password (valid for {{validityMinutes}} minutes, single use):
{{link}}

If you did not make this request, ignore this email; your password will not change.$x$),
-- ---------------------------------------------------------------- ACCOUNT_DELETION_CONFIRMATION
('ACCOUNT_DELETION_CONFIRMATION', 'tr',
 $s$LTZ hesap silme onayı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Hesabınızı silmeyi onaylayın</h1><p style="margin:0 0 16px 0">LTZ hesabınızı kalıcı olarak silmek için bağlantıyı açın ve kullanıcı adınızı yazarak onaylayın. Bağlantı {{validityMinutes}} dakika geçerlidir, tek kullanımlıktır ve bu işlem geri alınamaz.</p><p style="margin:0 0 16px 0"><a href="{{link}}" style="display:inline-block;background:#b91c1c;color:#ffffff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Silme işlemini onayla</a></p><p style="margin:0 0 16px 0;font-size:13px">Düğme çalışmazsa bu adresi tarayıcınıza yapıştırın:<br><span style="word-break:break-all">{{link}}</span></p><p style="margin:0">Bu isteği siz yapmadıysanız bu e-postayı yok sayın ve parolanızı değiştirin.</p>$h$,
 $x$LTZ hesabınızı kalıcı olarak silmek için bağlantıyı açın ve kullanıcı adınızı yazarak onaylayın ({{validityMinutes}} dakika geçerli, tek kullanımlık). Bu işlem geri alınamaz:
{{link}}

Bu isteği siz yapmadıysanız bu e-postayı yok sayın ve parolanızı değiştirin.$x$),
('ACCOUNT_DELETION_CONFIRMATION', 'en',
 $s$LTZ account deletion confirmation$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Confirm your account deletion</h1><p style="margin:0 0 16px 0">To permanently delete your LTZ account open the link and confirm by typing your username. The link is valid for {{validityMinutes}} minutes, single use, and this cannot be undone.</p><p style="margin:0 0 16px 0"><a href="{{link}}" style="display:inline-block;background:#b91c1c;color:#ffffff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Confirm deletion</a></p><p style="margin:0 0 16px 0;font-size:13px">If the button does not work, paste this address into your browser:<br><span style="word-break:break-all">{{link}}</span></p><p style="margin:0">If you did not make this request, ignore this email and change your password.</p>$h$,
 $x$To permanently delete your LTZ account open the link and confirm by typing your username (valid for {{validityMinutes}} minutes, single use). This cannot be undone:
{{link}}

If you did not make this request, ignore this email and change your password.$x$),
-- ---------------------------------------------------------------- PASSWORD_CHANGED
('PASSWORD_CHANGED', 'tr',
 $s$LTZ parolanız değiştirildi$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Parolanız değiştirildi</h1><p style="margin:0 0 16px 0">LTZ hesabınızın parolası değiştirildi.</p><p style="margin:0 0 16px 0">Tarih: {{occurredAt}}</p><p style="margin:0">Bu değişikliği siz yapmadıysanız hemen "Parolamı unuttum" ile parolanızı sıfırlayın.</p>$h$,
 $x$LTZ hesabınızın parolası değiştirildi.
Tarih: {{occurredAt}}
Bu değişikliği siz yapmadıysanız hemen "Parolamı unuttum" ile parolanızı sıfırlayın.$x$),
('PASSWORD_CHANGED', 'en',
 $s$Your LTZ password was changed$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Your password was changed</h1><p style="margin:0 0 16px 0">The password of your LTZ account was changed.</p><p style="margin:0 0 16px 0">Date: {{occurredAt}}</p><p style="margin:0">If you did not make this change, reset your password immediately using "Forgot password".</p>$h$,
 $x$The password of your LTZ account was changed.
Date: {{occurredAt}}
If you did not make this change, reset your password immediately using "Forgot password".$x$),
-- ---------------------------------------------------------------- PASSWORD_RESET_COMPLETED
('PASSWORD_RESET_COMPLETED', 'tr',
 $s$LTZ parolanız sıfırlandı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Parolanız sıfırlandı</h1><p style="margin:0 0 16px 0">LTZ hesabınızın parolası sıfırlama bağlantısıyla yenilendi.</p><p style="margin:0 0 16px 0">Tarih: {{occurredAt}}</p><p style="margin:0">Bu işlemi siz yapmadıysanız parolanızı yeniden sıfırlayın ve hesabınızın e-postasını kontrol edin.</p>$h$,
 $x$LTZ hesabınızın parolası sıfırlama bağlantısıyla yenilendi.
Tarih: {{occurredAt}}
Bu işlemi siz yapmadıysanız parolanızı yeniden sıfırlayın ve hesabınızın e-postasını kontrol edin.$x$),
('PASSWORD_RESET_COMPLETED', 'en',
 $s$Your LTZ password was reset$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Your password was reset</h1><p style="margin:0 0 16px 0">The password of your LTZ account was renewed with a reset link.</p><p style="margin:0 0 16px 0">Date: {{occurredAt}}</p><p style="margin:0">If you did not do this, reset your password again and check the email address of your account.</p>$h$,
 $x$The password of your LTZ account was renewed with a reset link.
Date: {{occurredAt}}
If you did not do this, reset your password again and check the email address of your account.$x$),
-- ---------------------------------------------------------------- EMAIL_CHANGED
('EMAIL_CHANGED', 'tr',
 $s$LTZ hesabınızın e-posta adresi değişti$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">E-posta adresiniz değiştirildi</h1><p style="margin:0 0 16px 0">LTZ hesabınızın e-posta adresi değiştirildi. Bu bildirim önceki adresinize gönderildi.</p><p style="margin:0 0 16px 0">Tarih: {{occurredAt}}</p><p style="margin:0">Bu değişikliği siz yapmadıysanız hesabınız ele geçirilmiş olabilir; parolanızı sıfırlayın.</p>$h$,
 $x$LTZ hesabınızın e-posta adresi değiştirildi. Bu bildirim önceki adresinize gönderildi.
Tarih: {{occurredAt}}
Bu değişikliği siz yapmadıysanız hesabınız ele geçirilmiş olabilir; parolanızı sıfırlayın.$x$),
('EMAIL_CHANGED', 'en',
 $s$The email address of your LTZ account changed$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Your email address was changed</h1><p style="margin:0 0 16px 0">The email address of your LTZ account was changed. This notice was sent to your previous address.</p><p style="margin:0 0 16px 0">Date: {{occurredAt}}</p><p style="margin:0">If you did not make this change your account may have been compromised; reset your password.</p>$h$,
 $x$The email address of your LTZ account was changed. This notice was sent to your previous address.
Date: {{occurredAt}}
If you did not make this change your account may have been compromised; reset your password.$x$),
-- ---------------------------------------------------------------- ACCOUNT_SUSPENDED
('ACCOUNT_SUSPENDED', 'tr',
 $s$LTZ hesabınız askıya alındı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Hesabınız askıya alındı</h1><p style="margin:0 0 16px 0">LTZ hesabınız askıya alındı. Bu süre boyunca hesabınızla bazı işlemleri yapamazsınız.</p><p style="margin:0">Tarih: {{occurredAt}}</p>$h$,
 $x$LTZ hesabınız askıya alındı. Bu süre boyunca hesabınızla bazı işlemleri yapamazsınız.
Tarih: {{occurredAt}}$x$),
('ACCOUNT_SUSPENDED', 'en',
 $s$Your LTZ account was suspended$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Your account was suspended</h1><p style="margin:0 0 16px 0">Your LTZ account was suspended. You cannot perform some actions with your account during this time.</p><p style="margin:0">Date: {{occurredAt}}</p>$h$,
 $x$Your LTZ account was suspended. You cannot perform some actions with your account during this time.
Date: {{occurredAt}}$x$),
-- ---------------------------------------------------------------- ACCOUNT_BANNED
('ACCOUNT_BANNED', 'tr',
 $s$LTZ hesabınız yasaklandı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Hesabınız yasaklandı</h1><p style="margin:0 0 16px 0">LTZ hesabınız platform kuralları nedeniyle yasaklandı ve artık kullanılamaz.</p><p style="margin:0">Tarih: {{occurredAt}}</p>$h$,
 $x$LTZ hesabınız platform kuralları nedeniyle yasaklandı ve artık kullanılamaz.
Tarih: {{occurredAt}}$x$),
('ACCOUNT_BANNED', 'en',
 $s$Your LTZ account was banned$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Your account was banned</h1><p style="margin:0 0 16px 0">Your LTZ account was banned for violating the platform rules and can no longer be used.</p><p style="margin:0">Date: {{occurredAt}}</p>$h$,
 $x$Your LTZ account was banned for violating the platform rules and can no longer be used.
Date: {{occurredAt}}$x$),
-- ---------------------------------------------------------------- ACCOUNT_DISABLED
('ACCOUNT_DISABLED', 'tr',
 $s$LTZ hesabınız devre dışı bırakıldı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Hesabınız devre dışı bırakıldı</h1><p style="margin:0 0 16px 0">LTZ hesabınız devre dışı bırakıldı ve şu anda kullanılamaz.</p><p style="margin:0">Tarih: {{occurredAt}}</p>$h$,
 $x$LTZ hesabınız devre dışı bırakıldı ve şu anda kullanılamaz.
Tarih: {{occurredAt}}$x$),
('ACCOUNT_DISABLED', 'en',
 $s$Your LTZ account was disabled$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Your account was disabled</h1><p style="margin:0 0 16px 0">Your LTZ account was disabled and cannot be used at the moment.</p><p style="margin:0">Date: {{occurredAt}}</p>$h$,
 $x$Your LTZ account was disabled and cannot be used at the moment.
Date: {{occurredAt}}$x$),
-- ---------------------------------------------------------------- ACCOUNT_REACTIVATED
('ACCOUNT_REACTIVATED', 'tr',
 $s$LTZ hesabınız yeniden etkinleştirildi$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Hesabınız yeniden etkin</h1><p style="margin:0 0 16px 0">LTZ hesabınız yeniden etkinleştirildi. Tekrar giriş yapabilirsiniz.</p><p style="margin:0">Tarih: {{occurredAt}}</p>$h$,
 $x$LTZ hesabınız yeniden etkinleştirildi. Tekrar giriş yapabilirsiniz.
Tarih: {{occurredAt}}$x$),
('ACCOUNT_REACTIVATED', 'en',
 $s$Your LTZ account was reactivated$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Your account is active again</h1><p style="margin:0 0 16px 0">Your LTZ account was reactivated. You can sign in again.</p><p style="margin:0">Date: {{occurredAt}}</p>$h$,
 $x$Your LTZ account was reactivated. You can sign in again.
Date: {{occurredAt}}$x$)
) AS t(code, locale, subject, body_html, body_text);
