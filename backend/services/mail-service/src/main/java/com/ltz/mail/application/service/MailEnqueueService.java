package com.ltz.mail.application.service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.mail.application.port.PayloadCipherPort;
import com.ltz.mail.domain.enums.MailType;
import com.ltz.mail.domain.exception.MailErrorCode;
import com.ltz.mail.domain.exception.MailException;
import com.ltz.mail.infrastructure.config.WorkerProperties;
import com.ltz.mail.infrastructure.persistence.repository.MailMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * E-posta isteğini kalıcı kuyruğa alır (MAIL-11, MAIL-14, MAIL-16). Çağıran ({@code 202}) yalnız kayıt veritabanına
 * yazıldıktan sonra yanıt alır; SMTP'ye gitmek isteğin parçası değildir, bu yüzden ana akış bloke olmaz ve SMTP
 * kesintisinde istek kaybolmaz. Kod/bağlantı şifreli yazılır, düz hâli hiçbir yerde tutulmaz ve loglanmaz.
 *
 * <p>Idempotency: aynı {@code idempotencyKey} + aynı içerik ikinci kez e-posta üretmez (sessizce kabul); aynı anahtar
 * farklı içerikle gelirse {@code 409}. İçerik karşılaştırması anahtarlı parmak iziyledir (düz özet değil).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailEnqueueService {

    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    private static final char FIELD_SEPARATOR = '\0';

    private final MailMessageRepository messages;
    private final PayloadCipherPort cipher;
    private final LinkPolicy linkPolicy;
    private final WorkerProperties worker;
    private final Clock clock;

    @Transactional
    public void enqueue(MailCommand command) {
        Instant now = clock.instant();
        validate(command, now);
        String email = command.recipientEmail().strip();
        String fingerprint = cipher.fingerprint(canonical(command, email));
        UUID messageId = UUID.randomUUID();
        byte[] encrypted = cipher.encrypt(messageId, PayloadCodec.encode(command.payload()));
        Instant expiresAt = now.plus(lifetime(command.payload()));

        int inserted = messages.insertIfAbsent(messageId, command.idempotencyKey(), fingerprint, command.type().name(),
                command.accountId(), email, EmailMasking.mask(email),
                command.locale() == null ? null : command.locale().code(), encrypted,
                cipher.currentKeyVersion(), now, expiresAt);
        if (inserted == 1) {
            log.info("Mail queued messageId={} type={}", messageId, command.type());
            return;
        }
        String existing = messages.findRequestHashByIdempotencyKey(command.idempotencyKey())
                .orElseThrow(() -> new MailException(MailErrorCode.TEMPORARILY_UNAVAILABLE));
        if (!MessageDigest.isEqual(existing.getBytes(StandardCharsets.UTF_8), fingerprint.getBytes(StandardCharsets.UTF_8))) {
            throw new MailException(MailErrorCode.IDEMPOTENCY_CONFLICT);
        }
        log.debug("Mail request repeated with the same idempotency key type={}", command.type());
    }

    private void validate(MailCommand command, Instant now) {
        MailPayload payload = command.payload();
        if (payload.link() != null) {
            linkPolicy.requireAllowed(payload.link());
        }
        boolean notification = payload.validitySeconds() == null;
        if (notification && (payload.occurredAt() == null || payload.occurredAt().isAfter(now.plus(MAX_FUTURE_SKEW)))) {
            throw new MailException(MailErrorCode.INVALID_OCCURRED_AT);
        }
    }

    /** Kod/bağlantı geçerlilik süresi kadar, bildirimler en çok {@code notification-max-age} kadar gönderilebilir. */
    private Duration lifetime(MailPayload payload) {
        return payload.validitySeconds() == null
                ? worker.notificationMaxAge()
                : Duration.ofSeconds(payload.validitySeconds());
    }

    private static String canonical(MailCommand command, String email) {
        MailPayload payload = command.payload();
        MailType type = command.type();
        StringBuilder canonical = new StringBuilder();
        for (String part : new String[] {
                type.name(), command.accountId().toString(), email,
                command.locale() == null ? "" : command.locale().code(),
                payload.code() == null ? "" : payload.code(),
                payload.link() == null ? "" : payload.link(),
                payload.validitySeconds() == null ? "" : payload.validitySeconds().toString(),
                payload.occurredAt() == null ? "" : payload.occurredAt().toString()}) {
            canonical.append(part).append(FIELD_SEPARATOR);
        }
        return canonical.toString();
    }
}
