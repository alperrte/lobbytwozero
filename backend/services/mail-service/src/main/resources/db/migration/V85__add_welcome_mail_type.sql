-- MAIL-05: hoş geldin e-postası. Auth, e-posta doğrulaması tamamlanınca security-notifications ucuna type=WELCOME gönderir
-- (Auth SecurityNotificationType ile aynı ad sözleşmesi). Bildirim yalnız occurredAt taşır; şablon değişken kullanmaz.
-- V81..V84 değiştirilmez: tür kısıtı yeniden oluşturulur, şablon V84 tasarım kuşağıyla (sürüm 2) eklenir.

ALTER TABLE mail_messages DROP CONSTRAINT ck_mail_messages_type;
ALTER TABLE mail_messages ADD CONSTRAINT ck_mail_messages_type CHECK (type IN (
    'EMAIL_VERIFICATION', 'PASSWORD_RESET_LINK', 'ACCOUNT_DELETION_CONFIRMATION',
    'PASSWORD_CHANGED', 'PASSWORD_RESET_COMPLETED', 'EMAIL_CHANGED',
    'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED', 'ACCOUNT_DISABLED', 'ACCOUNT_REACTIVATED',
    'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'RECOVERY_CODE_USED', 'EXTERNAL_IDENTITY_LINKED',
    'WELCOME'));

INSERT INTO mail_templates (id, code, locale, subject, body_html, body_text, version, active, created_at, updated_at)
SELECT gen_random_uuid(), t.code, t.locale, t.subject, t.body_html, t.body_text, 2, true, now(), now()
FROM (VALUES
('WELCOME', 'tr',
 $s$LTZ'ye hoş geldin$s$,
 $h$<p class="eb-accent" style="margin:0 0 10px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:12px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">HOŞ GELDİN</p><h1 class="h1" style="margin:0 0 16px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:28px;line-height:1.2;font-weight:700;color:#f1f1f1">LTZ'ye hoş geldin</h1><p style="margin:0 0 18px 0">Hesabın hazır. LobbyTwoZero, oyun dünyasını tek bir toplulukta buluşturur.</p><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 12px 0"><tr><td class="box" style="padding:14px 18px;background:#1e1f26;border:1px solid #34343c;border-radius:10px;font-size:14px;line-height:1.5"><p class="eb-accent" style="margin:0 0 4px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">KEŞFET</p><p style="margin:0">Oyunları, haberleri ve incelemeleri keşfet.</p></td></tr><tr><td height="8" style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr><tr><td class="box" style="padding:14px 18px;background:#1e1f26;border:1px solid #34343c;border-radius:10px;font-size:14px;line-height:1.5"><p class="eb-accent" style="margin:0 0 4px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">TOPLULUK</p><p style="margin:0">Forumlara katıl, takım bul, etkinlikleri takip et.</p></td></tr><tr><td height="8" style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr><tr><td class="box" style="padding:14px 18px;background:#1e1f26;border:1px solid #34343c;border-radius:10px;font-size:14px;line-height:1.5"><p class="eb-accent" style="margin:0 0 4px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">LTZ PERFORMANCE</p><p style="margin:0">Donanımını ekle, oyunların bilgisayarında nasıl çalışacağını gör.</p></td></tr><tr><td height="8" style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr></table><p style="margin:0 0 18px 0">İyi oyunlar, LTZ ekibi.</p>$h$,
 $x$LTZ'ye hoş geldin! Hesabın hazır.
Keşfet: oyunları, haberleri ve incelemeleri keşfet.
Topluluk: forumlara katıl, takım bul, etkinlikleri takip et.
LTZ Performance: donanımını ekle, oyunların bilgisayarında nasıl çalışacağını gör.
İyi oyunlar, LTZ ekibi.$x$),
('WELCOME', 'en',
 $s$Welcome to LTZ$s$,
 $h$<p class="eb-accent" style="margin:0 0 10px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:12px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">WELCOME</p><h1 class="h1" style="margin:0 0 16px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:28px;line-height:1.2;font-weight:700;color:#f1f1f1">Welcome to LTZ</h1><p style="margin:0 0 18px 0">Your account is ready. LobbyTwoZero brings the gaming world together in one community.</p><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 12px 0"><tr><td class="box" style="padding:14px 18px;background:#1e1f26;border:1px solid #34343c;border-radius:10px;font-size:14px;line-height:1.5"><p class="eb-accent" style="margin:0 0 4px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">DISCOVER</p><p style="margin:0">Explore games, news and reviews.</p></td></tr><tr><td height="8" style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr><tr><td class="box" style="padding:14px 18px;background:#1e1f26;border:1px solid #34343c;border-radius:10px;font-size:14px;line-height:1.5"><p class="eb-accent" style="margin:0 0 4px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">COMMUNITY</p><p style="margin:0">Join forums, find a team, follow events.</p></td></tr><tr><td height="8" style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr><tr><td class="box" style="padding:14px 18px;background:#1e1f26;border:1px solid #34343c;border-radius:10px;font-size:14px;line-height:1.5"><p class="eb-accent" style="margin:0 0 4px 0;font-family:'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif;font-size:11px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">LTZ PERFORMANCE</p><p style="margin:0">Add your hardware and see how games will run on your PC.</p></td></tr><tr><td height="8" style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr></table><p style="margin:0 0 18px 0">Have fun, the LTZ team.</p>$h$,
 $x$Welcome to LTZ! Your account is ready.
Discover: explore games, news and reviews.
Community: join forums, find a team, follow events.
LTZ Performance: add your hardware and see how games will run on your PC.
Have fun, the LTZ team.$x$)
) AS t(code, locale, subject, body_html, body_text);
