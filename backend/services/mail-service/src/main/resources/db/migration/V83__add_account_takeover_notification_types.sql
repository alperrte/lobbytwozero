-- Auth -> Mail güvenlik bildirimi sözleşmesine 4 tür eklenir (hesap ele geçirme belirtisi olan olaylar):
-- TWO_FACTOR_ENABLED, TWO_FACTOR_DISABLED, RECOVERY_CODE_USED, EXTERNAL_IDENTITY_LINKED.
-- V81/V82 değiştirilmez: tür kısıtı yeniden oluşturulur, şablonlar V82 kalıbıyla (yalnız {{occurredAt}}) eklenir.

ALTER TABLE mail_messages DROP CONSTRAINT ck_mail_messages_type;
ALTER TABLE mail_messages ADD CONSTRAINT ck_mail_messages_type CHECK (type IN (
    'EMAIL_VERIFICATION', 'PASSWORD_RESET_LINK', 'ACCOUNT_DELETION_CONFIRMATION',
    'PASSWORD_CHANGED', 'PASSWORD_RESET_COMPLETED', 'EMAIL_CHANGED',
    'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED', 'ACCOUNT_DISABLED', 'ACCOUNT_REACTIVATED',
    'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'RECOVERY_CODE_USED', 'EXTERNAL_IDENTITY_LINKED'));

INSERT INTO mail_templates (id, code, locale, subject, body_html, body_text, version, active, created_at, updated_at)
SELECT gen_random_uuid(), t.code, t.locale, t.subject, t.body_html, t.body_text, 1, true, now(), now()
FROM (VALUES
-- ---------------------------------------------------------------- TWO_FACTOR_ENABLED
('TWO_FACTOR_ENABLED', 'tr',
 $s$LTZ iki adımlı doğrulama açıldı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">İki adımlı doğrulama açıldı</h1><p style="margin:0 0 16px 0">LTZ hesabınızda iki adımlı doğrulama açıldı.</p><p style="margin:0 0 16px 0">Tarih: {{occurredAt}}</p><p style="margin:0">Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.</p>$h$,
 $x$LTZ hesabınızda iki adımlı doğrulama açıldı.
Tarih: {{occurredAt}}
Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.$x$),
('TWO_FACTOR_ENABLED', 'en',
 $s$Two-step verification was enabled$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Two-step verification was enabled</h1><p style="margin:0 0 16px 0">Two-step verification was enabled on your LTZ account.</p><p style="margin:0 0 16px 0">Date: {{occurredAt}}</p><p style="margin:0">If you did not do this, reset your password immediately and contact support.</p>$h$,
 $x$Two-step verification was enabled on your LTZ account.
Date: {{occurredAt}}
If you did not do this, reset your password immediately and contact support.$x$),
-- ---------------------------------------------------------------- TWO_FACTOR_DISABLED
('TWO_FACTOR_DISABLED', 'tr',
 $s$LTZ iki adımlı doğrulama kapatıldı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">İki adımlı doğrulama kapatıldı</h1><p style="margin:0 0 16px 0">LTZ hesabınızda iki adımlı doğrulama kapatıldı.</p><p style="margin:0 0 16px 0">Tarih: {{occurredAt}}</p><p style="margin:0">Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.</p>$h$,
 $x$LTZ hesabınızda iki adımlı doğrulama kapatıldı.
Tarih: {{occurredAt}}
Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.$x$),
('TWO_FACTOR_DISABLED', 'en',
 $s$Two-step verification was disabled$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Two-step verification was disabled</h1><p style="margin:0 0 16px 0">Two-step verification was disabled on your LTZ account.</p><p style="margin:0 0 16px 0">Date: {{occurredAt}}</p><p style="margin:0">If you did not do this, reset your password immediately and contact support.</p>$h$,
 $x$Two-step verification was disabled on your LTZ account.
Date: {{occurredAt}}
If you did not do this, reset your password immediately and contact support.$x$),
-- ---------------------------------------------------------------- RECOVERY_CODE_USED
('RECOVERY_CODE_USED', 'tr',
 $s$LTZ kurtarma kodu kullanıldı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Kurtarma kodu kullanıldı</h1><p style="margin:0 0 16px 0">LTZ hesabınızda bir kurtarma kodu kullanıldı.</p><p style="margin:0 0 16px 0">Tarih: {{occurredAt}}</p><p style="margin:0">Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.</p>$h$,
 $x$LTZ hesabınızda bir kurtarma kodu kullanıldı.
Tarih: {{occurredAt}}
Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.$x$),
('RECOVERY_CODE_USED', 'en',
 $s$A recovery code was used$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">A recovery code was used</h1><p style="margin:0 0 16px 0">A recovery code was used on your LTZ account.</p><p style="margin:0 0 16px 0">Date: {{occurredAt}}</p><p style="margin:0">If you did not do this, reset your password immediately and contact support.</p>$h$,
 $x$A recovery code was used on your LTZ account.
Date: {{occurredAt}}
If you did not do this, reset your password immediately and contact support.$x$),
-- ---------------------------------------------------------------- EXTERNAL_IDENTITY_LINKED
('EXTERNAL_IDENTITY_LINKED', 'tr',
 $s$LTZ hesabınıza harici hesap bağlandı$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">Harici hesap bağlandı</h1><p style="margin:0 0 16px 0">LTZ hesabınıza bir Google ya da Steam hesabı bağlandı.</p><p style="margin:0 0 16px 0">Tarih: {{occurredAt}}</p><p style="margin:0">Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.</p>$h$,
 $x$LTZ hesabınıza bir Google ya da Steam hesabı bağlandı.
Tarih: {{occurredAt}}
Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.$x$),
('EXTERNAL_IDENTITY_LINKED', 'en',
 $s$An external account was linked$s$,
 $h$<h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3">An external account was linked</h1><p style="margin:0 0 16px 0">A Google or Steam account was linked to your LTZ account.</p><p style="margin:0 0 16px 0">Date: {{occurredAt}}</p><p style="margin:0">If you did not do this, reset your password immediately and contact support.</p>$h$,
 $x$A Google or Steam account was linked to your LTZ account.
Date: {{occurredAt}}
If you did not do this, reset your password immediately and contact support.$x$)
) AS t(code, locale, subject, body_html, body_text);
