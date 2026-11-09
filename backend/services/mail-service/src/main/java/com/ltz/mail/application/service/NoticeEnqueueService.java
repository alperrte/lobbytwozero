package com.ltz.mail.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.mail.application.port.PayloadCipherPort;
import com.ltz.mail.application.port.RecipientResolverPort;
import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;
import com.ltz.mail.domain.exception.MailErrorCode;
import com.ltz.mail.domain.exception.MailException;
import com.ltz.mail.infrastructure.config.PlatformProperties;
import com.ltz.mail.infrastructure.config.WorkerProperties;
import com.ltz.mail.infrastructure.persistence.repository.MailMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Platform kapısı kuyruğa alma (Auth dışındaki kayıtlı servisler). {@link MailEnqueueService} ile aynı güvenceleri verir
 * (önce kalıcı yazım, sonra {@code 202}; idempotency; düz içerik loglanmaz) ve ek olarak:
 * <ul>
 *   <li>alıcı adresi YAZILMAZ: yalnız {@code accountId} saklanır, adres gönderim anında Auth'tan çözülür;</li>
 *   <li>içerik doğrulanır (kontrol ve çift yönlü yönlendirme karakterleri yok, düğme adresi izinli host + https);</li>
 *   <li>çağıran başına ve hesap başına saatlik hız sınırı uygulanır (aşılırsa {@code 429}).</li>
 * </ul>
 * Hız sınırı yaklaşıktır: sayım ve yazım aynı işlemde yapılır, eşzamanlı çok istekte sınır kısa süre küçük farkla
 * aşılabilir; amaç kötüye kullanımı ve hatalı döngüyü durdurmaktır, kesin kota değildir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeEnqueueService {

    private static final Duration RATE_WINDOW = Duration.ofHours(1);
    private static final char FIELD_SEPARATOR = '\0';
    /** Satır sonu dışında kontrol karakteri ve bidi geçersiz kılma karakterleri (görünür metni değiştirmek için kullanılır). */
    private static final Pattern FORBIDDEN_IN_MESSAGE = Pattern.compile("(?![\\n\\r])\\p{Cntrl}|[\\u202A-\\u202E\\u2066-\\u2069]");
    private static final Pattern FORBIDDEN_IN_SINGLE_LINE = Pattern.compile("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]");

    private final MailMessageRepository messages;
    private final PayloadCipherPort cipher;
    private final LinkPolicy linkPolicy;
    private final WorkerProperties worker;
    private final RecipientResolverPort resolver;
    private final Clock clock;

    @Transactional
    public void enqueue(String caller, PlatformProperties.Caller limits, NoticeCommand command) {
        if (!resolver.isConfigured()) {
            throw new MailException(MailErrorCode.PLATFORM_NOT_CONFIGURED);
        }
        validate(command.content());
        Instant now = clock.instant();
        MailLocale locale = command.locale() == null ? MailLocale.TR : command.locale();
        String fingerprint = cipher.fingerprint(canonical(caller, command, locale));

        if (isRepeat(command.idempotencyKey(), fingerprint)) {
            return;
        }
        enforceRateLimits(caller, limits, command.accountId(), now);

        UUID messageId = UUID.randomUUID();
        byte[] encrypted = cipher.encrypt(messageId,
                PayloadCodec.encode(new MailPayload(null, null, null, null, command.content())));
        int inserted = messages.insertPlatformIfAbsent(messageId, command.idempotencyKey(), fingerprint,
                MailType.NOTICE.name(), command.accountId(), locale.code(), caller, encrypted,
                cipher.currentKeyVersion(), now, now.plus(worker.notificationMaxAge()));
        if (inserted == 1) {
            log.info("Notice queued messageId={} caller={}", messageId, caller);
            return;
        }
        // Eşzamanlı aynı anahtarlı iki istek: kayıp olan, kazananla aynı içerikse sessizce kabul edilir.
        if (!isRepeat(command.idempotencyKey(), fingerprint)) {
            throw new MailException(MailErrorCode.TEMPORARILY_UNAVAILABLE);
        }
    }

    /** @return aynı anahtar + aynı içerik daha önce kabul edilmişse {@code true}; farklı içerikse 409 */
    private boolean isRepeat(UUID idempotencyKey, String fingerprint) {
        return messages.findRequestHashByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!MessageDigest.isEqual(existing.getBytes(StandardCharsets.UTF_8),
                    fingerprint.getBytes(StandardCharsets.UTF_8))) {
                throw new MailException(MailErrorCode.IDEMPOTENCY_CONFLICT);
            }
            log.debug("Notice request repeated with the same idempotency key");
            return true;
        }).orElse(false);
    }

    private void enforceRateLimits(String caller, PlatformProperties.Caller limits, UUID accountId, Instant now) {
        Instant since = now.minus(RATE_WINDOW);
        if (messages.countForAccountSince(caller, accountId, since) >= limits.accountPerHour()
                || messages.countForCallerSince(caller, since) >= limits.callerPerHour()) {
            log.warn("Notice rate limit reached caller={}", caller);
            throw new MailException(MailErrorCode.RATE_LIMITED);
        }
    }

    private void validate(NoticeContent content) {
        boolean hasLabel = content.actionLabel() != null && !content.actionLabel().isBlank();
        boolean hasLink = content.actionLink() != null && !content.actionLink().isBlank();
        if (hasLabel != hasLink
                || FORBIDDEN_IN_SINGLE_LINE.matcher(content.title()).find()
                || FORBIDDEN_IN_MESSAGE.matcher(content.message()).find()
                || (hasLabel && FORBIDDEN_IN_SINGLE_LINE.matcher(content.actionLabel()).find())
                || content.title().isBlank() || content.message().isBlank()) {
            throw new MailException(MailErrorCode.INVALID_CONTENT);
        }
        if (hasLink) {
            linkPolicy.requireAllowed(content.actionLink());
        }
    }

    private static String canonical(String caller, NoticeCommand command, MailLocale locale) {
        NoticeContent content = command.content();
        StringBuilder canonical = new StringBuilder();
        for (String part : new String[] {
                MailType.NOTICE.name(), caller, command.accountId().toString(), locale.code(), content.kind().name(),
                content.title(), content.message(),
                content.actionLabel() == null ? "" : content.actionLabel(),
                content.actionLink() == null ? "" : content.actionLink()}) {
            canonical.append(part).append(FIELD_SEPARATOR);
        }
        return canonical.toString();
    }
}
