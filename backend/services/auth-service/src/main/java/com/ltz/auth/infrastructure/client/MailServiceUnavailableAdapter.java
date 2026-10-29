package com.ltz.auth.infrastructure.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ltz.auth.application.port.AccountDeletionMail;
import com.ltz.auth.application.port.AccountDeletionMailPort;
import com.ltz.auth.application.port.PasswordResetMail;
import com.ltz.auth.application.port.PasswordResetMailPort;
import com.ltz.auth.application.port.SecurityNotificationMail;
import com.ltz.auth.application.port.SecurityNotificationMailPort;
import com.ltz.auth.application.port.VerificationCodeDeliveryException;
import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.application.port.VerificationCodeMailPort;

/**
 * Faz 1 üretim davranışı (K5): Mail Service'te MAIL-01 ucu ve servisler arası kimlik doğrulama (D4)
 * henüz yoktur. Kimliksiz bir iç HTTP çağrısı yapmak yerine teslim açıkça reddedilir; kod
 * DELIVERY_FAILED olarak geçersiz kılınır ve hiçbir yerde "gönderildi" denmez.
 * MAIL-01 ve servis JWT'si hazır olduğunda yerini kimlik doğrulamalı RestClient adaptörü alır.
 * Development'ta yerine Mailpit adaptörü seçilebilir (ltz.auth.mail.adapter=dev-smtp).
 */
@Component
@ConditionalOnProperty(name = "ltz.auth.mail.adapter", havingValue = "unavailable", matchIfMissing = true)
public class MailServiceUnavailableAdapter implements VerificationCodeMailPort, PasswordResetMailPort,
        AccountDeletionMailPort, SecurityNotificationMailPort {

    @Override
    public void sendVerificationCode(VerificationCodeMail mail) {
        throw new VerificationCodeDeliveryException("Mail Service verification delivery is not available");
    }

    @Override
    public void sendPasswordReset(PasswordResetMail mail) {
        throw new VerificationCodeDeliveryException("Mail Service password reset delivery is not available");
    }

    @Override
    public void sendAccountDeletionConfirmation(AccountDeletionMail mail) {
        throw new VerificationCodeDeliveryException("Mail Service account deletion delivery is not available");
    }

    @Override
    public void sendSecurityNotification(SecurityNotificationMail mail) {
        throw new VerificationCodeDeliveryException("Mail Service security notification delivery is not available");
    }
}
