package com.ltz.mail.infrastructure.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.MailSenderPort;
import com.ltz.mail.application.port.OutgoingMail;
import com.ltz.mail.infrastructure.config.SmtpProperties;
import com.ltz.mail.infrastructure.config.SmtpSecurity;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * SMTP ile teslim (MAIL-10). Gönderici bilerek Spring bean'i değildir (elle kurulur): Boot'un mail health göstergesi
 * devreye girmez ve SMTP kesintisi {@code /actuator/health}'i düşürmez. TLS ayarlanabilir, sunucu kimliği
 * doğrulanır; üretimde {@code NONE} reddedilir ({@code MailStartupGuard}). E-posta {@code multipart/alternative}
 * (düz metin + HTML) gider, LTZ logosu inline (CID) eklenir; izleme pikseli veya uzak görsel yoktur.
 *
 * <p>Hata sınıflandırması: bağlantı/zaman aşımı/kimlik doğrulama/4xx geçici; 5xx ve geçersiz alıcı kalıcıdır. Hata
 * mesajları sağlayıcı yanıtını (alıcı adresi içerebilir) taşımaz; yalnız SMTP durum kodu ve sabit bir kod döner.
 */
@Slf4j
@Component
public class SmtpMailSender implements MailSenderPort {

    static final String LOGO_CONTENT_ID = "ltz-logo";
    private static final String LOGO_RESOURCE = "mail/ltz-logo.png";
    private static final String SENDER_NAME = "LTZ";
    private static final Pattern SMTP_REPLY_CODE = Pattern.compile("^\\s*([45]\\d\\d)\\b");
    private static final int PERMANENT_REPLY_MIN = 500;

    private final JavaMailSenderImpl mailSender;
    private final InternetAddress from;
    private final byte[] logo;

    public SmtpMailSender(SmtpProperties properties) {
        this.mailSender = buildSender(properties);
        this.from = parseFrom(properties.effectiveFromAddress());
        this.logo = loadLogo();
        log.info("SMTP sender configured host={} port={} security={}", properties.effectiveHost(),
                properties.effectivePort(), properties.effectiveSecurity());
    }

    @Override
    public void send(OutgoingMail mail) {
        MimeMessage message = buildMessage(mail);
        try {
            mailSender.send(message);
        } catch (MailAuthenticationException _) {
            // Yapılandırma hatası (kullanıcı/parola); düzeltilince kuyruk kendiliğinden boşalır.
            throw MailDeliveryException.transientFailure("SMTP_AUTH_FAILED", null);
        } catch (MailSendException exception) {
            throw classify(exception);
        } catch (MailException _) {
            throw MailDeliveryException.transientFailure("SMTP_ERROR", null);
        }
    }

    private MimeMessage buildMessage(OutgoingMail mail) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(new InternetAddress(mail.to(), true));
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            helper.addInline(LOGO_CONTENT_ID, new ByteArrayResource(logo), "image/png");
            message.addHeader("Auto-Submitted", "auto-generated");
            return message;
        } catch (AddressException _) {
            throw MailDeliveryException.permanent("INVALID_RECIPIENT", null);
        } catch (MessagingException _) {
            throw MailDeliveryException.permanent("MESSAGE_BUILD_FAILED", null);
        }
    }

    private static MailDeliveryException classify(MailSendException exception) {
        Integer code = replyCode(exception);
        if (code == null) {
            return MailDeliveryException.transientFailure("SMTP_SEND_FAILED", null);
        }
        String errorCode = "SMTP_" + code;
        return code >= PERMANENT_REPLY_MIN
                ? MailDeliveryException.permanent(errorCode, code)
                : MailDeliveryException.transientFailure(errorCode, code);
    }

    /**
     * Spring'in {@code MailSendException}'ı sunucu hatalarını {@code getMessageExceptions()} içinde taşır; her birinin
     * neden zincirinde sunucunun 4xx/5xx yanıt kodu aranır. Yalnız sayı alınır, metin atılır.
     */
    private static Integer replyCode(MailSendException exception) {
        List<Throwable> roots = new ArrayList<>(Arrays.asList(exception.getMessageExceptions()));
        roots.add(exception);
        for (Throwable root : roots) {
            for (Throwable current = root; current != null; current = current.getCause()) {
                String message = current.getMessage();
                Matcher matcher = message == null ? null : SMTP_REPLY_CODE.matcher(message);
                if (matcher != null && matcher.find()) {
                    return Integer.valueOf(matcher.group(1));
                }
            }
        }
        return null;
    }

    private static JavaMailSenderImpl buildSender(SmtpProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        SmtpSecurity security = properties.effectiveSecurity();
        sender.setHost(properties.effectiveHost());
        sender.setPort(properties.effectivePort());
        sender.setProtocol(security == SmtpSecurity.SSL ? "smtps" : "smtp");
        sender.setDefaultEncoding("UTF-8");
        if (properties.authenticated()) {
            sender.setUsername(properties.username());
            sender.setPassword(properties.password());
        }
        Properties smtp = sender.getJavaMailProperties();
        String prefix = security == SmtpSecurity.SSL ? "mail.smtps." : "mail.smtp.";
        smtp.put(prefix + "connectiontimeout", String.valueOf(properties.connectTimeout().toMillis()));
        smtp.put(prefix + "timeout", String.valueOf(properties.readTimeout().toMillis()));
        smtp.put(prefix + "writetimeout", String.valueOf(properties.writeTimeout().toMillis()));
        smtp.put(prefix + "auth", String.valueOf(properties.authenticated()));
        if (security == SmtpSecurity.STARTTLS) {
            smtp.put(prefix + "starttls.enable", "true");
            smtp.put(prefix + "starttls.required", "true");
        }
        if (security != SmtpSecurity.NONE) {
            smtp.put(prefix + "ssl.checkserveridentity", "true");
            smtp.put(prefix + "ssl.protocols", "TLSv1.2 TLSv1.3");
        }
        if (security == SmtpSecurity.SSL) {
            smtp.put(prefix + "ssl.enable", "true");
        }
        return sender;
    }

    private static InternetAddress parseFrom(String address) {
        try {
            return new InternetAddress(address, SENDER_NAME, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("The sender address could not be built", exception);
        }
    }

    private static byte[] loadLogo() {
        try (InputStream stream = new ClassPathResource(LOGO_RESOURCE).getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Mail logo could not be loaded", exception);
        }
    }
}
