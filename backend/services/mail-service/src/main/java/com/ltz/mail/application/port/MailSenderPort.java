package com.ltz.mail.application.port;

/**
 * E-posta teslim sözleşmesi (MAIL-10). Uygulama, teslimi kabul edemediğinde {@link MailDeliveryException} fırlatmalıdır;
 * sessizce başarılı dönmek yasaktır. Hata mesajı alıcı adresini veya içeriği taşımaz.
 */
public interface MailSenderPort {

    void send(OutgoingMail mail);
}
