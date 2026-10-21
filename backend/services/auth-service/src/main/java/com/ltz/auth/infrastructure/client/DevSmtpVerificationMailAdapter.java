package com.ltz.auth.infrastructure.client;

import java.util.Properties;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.ltz.auth.application.port.PasswordResetMail;
import com.ltz.auth.application.port.PasswordResetMailPort;
import com.ltz.auth.application.port.VerificationCodeDeliveryException;
import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.application.port.VerificationCodeMailPort;
import com.ltz.auth.infrastructure.config.DevSmtpProperties;

/**
 * Yalnız development: e-postaları Mailpit'e SMTP ile iletir. Mailpit e-postayı dışarıya göndermez; buradaki
 * başarılı teslim production e-posta teslimi sayılmaz. Production SMTP mantığı Auth'ta yoktur ve olmayacak;
 * gerçek teslim Mail Service'in sorumluluğudur. Gönderen JavaMailSender bean değildir, böylece Boot'un mail
 * health göstergesi devreye girmez ve Mailpit kapalıyken servis sağlığı etkilenmez.
 */
public class DevSmtpVerificationMailAdapter implements VerificationCodeMailPort, PasswordResetMailPort {

    static final String SUBJECT = "LTZ e-posta doğrulama kodu / email verification code";
    static final String RESET_SUBJECT = "LTZ parola sıfırlama / password reset";
    private static final String BODY_TEMPLATE = """
            LTZ doğrulama kodunuz: %1$s
            Kod %2$d dakika geçerlidir.

            Your LTZ verification code: %1$s
            The code is valid for %2$d minutes.
            """;
    private static final String RESET_BODY_TEMPLATE = """
            LTZ parolanızı sıfırlamak için bağlantıya tıklayın (%2$d dakika geçerli, tek kullanımlık):
            %1$s

            Bu isteği siz yapmadıysanız bu e-postayı yok sayın.

            To reset your LTZ password open this link (valid for %2$d minutes, single use):
            %1$s

            If you did not request this, ignore this email.
            """;

    private final JavaMailSenderImpl mailSender;
    private final String from;

    public DevSmtpVerificationMailAdapter(DevSmtpProperties properties) {
        this.mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.host());
        mailSender.setPort(properties.port());
        Properties smtp = mailSender.getJavaMailProperties();
        smtp.put("mail.smtp.connectiontimeout", String.valueOf(properties.connectTimeout().toMillis()));
        smtp.put("mail.smtp.timeout", String.valueOf(properties.readTimeout().toMillis()));
        smtp.put("mail.smtp.writetimeout", String.valueOf(properties.readTimeout().toMillis()));
        this.from = properties.from();
    }

    @Override
    public void sendVerificationCode(VerificationCodeMail mail) {
        send(mail.recipientEmail(), SUBJECT, BODY_TEMPLATE.formatted(mail.code(), mail.validity().toMinutes()));
    }

    @Override
    public void sendPasswordReset(PasswordResetMail mail) {
        send(mail.recipientEmail(), RESET_SUBJECT,
                RESET_BODY_TEMPLATE.formatted(mail.resetLink(), mail.validity().toMinutes()));
    }

    private void send(String recipient, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            // Sağlayıcı hata mesajları alıcı adresini içerebilir; yalnız hata türü taşınır.
            throw new VerificationCodeDeliveryException(
                    "Development SMTP delivery failed: " + exception.getClass().getSimpleName());
        }
    }
}
