package com.ltz.mail.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.MailSenderPort;
import com.ltz.mail.application.port.OutgoingMail;
import com.ltz.mail.application.port.PayloadCipherPort;
import com.ltz.mail.domain.entity.MailDeliveryAttempt;
import com.ltz.mail.domain.entity.MailMessage;
import com.ltz.mail.domain.enums.AttemptResult;
import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.infrastructure.config.WorkerProperties;
import com.ltz.mail.infrastructure.persistence.repository.MailDeliveryAttemptRepository;
import com.ltz.mail.infrastructure.persistence.repository.MailMessageRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Kuyruk işleyicisi (MAIL-10, 12, 13, 15): bekleyen kayıtları kiralar ({@code FOR UPDATE SKIP LOCKED}, çok örnekli
 * çalışmada güvenli), şablonu üretir, SMTP ile gönderir ve sonucu işler. Kiralama süresi dolan {@code PROCESSING} kayıt
 * (çöken işleyici) yeniden alınır. Sonuç kaydı iyimser kilitle yazılır: kayıt bu arada başka bir örnek tarafından
 * yeniden kiralandıysa veya silindiyse (hesap silme) sonuç sessizce atılır. SMTP çağrısı hiçbir veritabanı işlemi
 * içinde yapılmaz.
 */
@Slf4j
@Service
public class MailDispatchService {

    static final String PAYLOAD_UNREADABLE = "PAYLOAD_UNREADABLE";
    static final String EXPIRED = "EXPIRED";
    static final String RETRIES_EXHAUSTED = "RETRIES_EXHAUSTED";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final MailMessageRepository messages;
    private final MailDeliveryAttemptRepository attempts;
    private final PayloadCipherPort cipher;
    private final TemplateRenderer renderer;
    private final MailSenderPort sender;
    private final MailRetryPolicy retryPolicy;
    private final WorkerProperties worker;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public MailDispatchService(MailMessageRepository messages, MailDeliveryAttemptRepository attempts,
                               PayloadCipherPort cipher, TemplateRenderer renderer, MailSenderPort sender,
                               MailRetryPolicy retryPolicy, WorkerProperties worker,
                               TransactionTemplate transaction, Clock clock) {
        this.messages = messages;
        this.attempts = attempts;
        this.cipher = cipher;
        this.renderer = renderer;
        this.sender = sender;
        this.retryPolicy = retryPolicy;
        this.worker = worker;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** @return bu turda işlenen kayıt sayısı */
    public int dispatchDue() {
        List<MailMessage> claimed = claim(clock.instant());
        for (MailMessage message : claimed) {
            process(message);
        }
        return claimed.size();
    }

    private List<MailMessage> claim(Instant now) {
        List<MailMessage> claimed = transaction.execute(status -> {
            List<UUID> ids = messages.lockDueIds(now, worker.batchSize());
            if (ids.isEmpty()) {
                return List.<MailMessage>of();
            }
            List<MailMessage> rows = messages.findAllById(ids);
            Instant leaseUntil = now.plus(worker.lease());
            rows.forEach(row -> row.lease(now, leaseUntil));
            return messages.saveAll(rows);
        });
        return claimed == null ? List.of() : claimed;
    }

    private void process(MailMessage message) {
        Instant startedAt = clock.instant();
        long startNanos = System.nanoTime();
        Outcome outcome;
        try {
            outcome = message.isExpired(startedAt) ? Outcome.expired() : send(message, startNanos);
        } catch (RuntimeException exception) {
            // İçerik/adres taşıyabilecek hata mesajı yazılmaz; yalnız tür loglanır.
            log.warn("Mail dispatch failed unexpectedly messageId={} cause={}", message.getId(),
                    exception.getClass().getSimpleName());
            outcome = new Outcome(AttemptResult.TRANSIENT_FAILURE, INTERNAL_ERROR, null, elapsedMillis(startNanos));
        }
        record(message, outcome, startedAt);
    }

    private Outcome send(MailMessage message, long startNanos) {
        try {
            MailPayload payload = readPayload(message);
            RenderedMail rendered = renderer.render(message.getType(), locale(message), payload);
            sender.send(new OutgoingMail(message.getId(), message.getRecipientEmail(), rendered.subject(),
                    rendered.text(), rendered.html()));
            return new Outcome(AttemptResult.SENT, null, null, elapsedMillis(startNanos));
        } catch (MailDeliveryException exception) {
            AttemptResult result = exception.isPermanent()
                    ? AttemptResult.PERMANENT_FAILURE
                    : AttemptResult.TRANSIENT_FAILURE;
            return new Outcome(result, exception.errorCode(), exception.smtpCode(), elapsedMillis(startNanos));
        }
    }

    private MailPayload readPayload(MailMessage message) {
        try {
            byte[] plain = cipher.decrypt(message.getId(), message.getPayloadKeyVersion(), message.getPayloadEncrypted());
            return PayloadCodec.decode(plain);
        } catch (PayloadCipherPort.PayloadCipherException | IllegalArgumentException _) {
            throw MailDeliveryException.permanent(PAYLOAD_UNREADABLE, null);
        }
    }

    private static MailLocale locale(MailMessage message) {
        return MailLocale.fromCode(message.getLocale());
    }

    private void record(MailMessage message, Outcome outcome, Instant now) {
        int attemptNo = message.getAttempts();
        switch (outcome.result()) {
            case SENT -> message.markSent(now);
            case EXPIRED -> message.markFailed(now, EXPIRED);
            case PERMANENT_FAILURE -> message.markFailed(now, outcome.errorCode());
            case TRANSIENT_FAILURE -> scheduleRetry(message, outcome, now);
        }
        MailDeliveryAttempt attempt = new MailDeliveryAttempt(message.getId(), attemptNo, now, outcome.result(),
                outcome.smtpCode(), outcome.errorCode(), outcome.durationMs());
        try {
            transaction.executeWithoutResult(status -> {
                messages.saveAndFlush(message);
                attempts.save(attempt);
            });
            log.info("Mail attempt recorded messageId={} attempt={} result={} status={}", message.getId(), attemptNo,
                    outcome.result(), message.getStatus());
        } catch (DataAccessException exception) {
            // Kayıt yeniden kiralandı (iyimser kilit) ya da hesap silindi; sonuç atılır, e-posta tekrar denenebilir.
            log.warn("Mail attempt result discarded messageId={} cause={}", message.getId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void scheduleRetry(MailMessage message, Outcome outcome, Instant now) {
        if (retryPolicy.isExhausted(message.getAttempts())) {
            message.markFailed(now, RETRIES_EXHAUSTED);
            return;
        }
        Duration delay = retryPolicy.delayAfter(message.getAttempts(), message.getId());
        Instant next = now.plus(delay);
        if (!next.isBefore(message.getExpiresAt())) {
            message.markFailed(now, EXPIRED);
            return;
        }
        message.markRetry(now, next, outcome.errorCode());
    }

    private static int elapsedMillis(long startNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private record Outcome(AttemptResult result, String errorCode, Integer smtpCode, int durationMs) {

        static Outcome expired() {
            return new Outcome(AttemptResult.EXPIRED, EXPIRED, null, 0);
        }
    }
}
