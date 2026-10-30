package com.ltz.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.enums.AccountStatus;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.persistence.repository.InboxEventRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servisler arası olay girişi (inbox). Olay, etkisiyle <b>aynı transaction'da</b> {@code inbox_events} tablosuna
 * yazılır: aynı {@code eventId} ikinci kez gelirse hiçbir şey değişmez ({@link EventOutcome#DUPLICATE}); yük geçersizse
 * transaction geri alınır ve olay işlenmiş sayılmaz. Bu servisin ilgilenmediği olay türü {@link EventOutcome#IGNORED}
 * olur ve kaydedilmez, böylece üretici yeniden denemez. Olay yükü ve hesap kimliği tabloya yazılmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestService {

    static final String AUTH_PRODUCER = "auth-service";
    private static final int SUPPORTED_VERSION = 1;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    private final InboxEventRepository inbox;
    private final UserProfileRepository profiles;
    private final Clock clock;

    @Transactional
    public EventOutcome ingest(IncomingEvent event) {
        Optional<EventType> type = EventType.find(event.eventType());
        if (type.isEmpty()) {
            return EventOutcome.IGNORED;
        }
        if (!type.get().producer().equals(event.producer())) {
            throw new UserException(UserErrorCode.EVENT_PRODUCER_MISMATCH);
        }
        if (event.eventVersion() != SUPPORTED_VERSION) {
            throw new UserException(UserErrorCode.INVALID_EVENT);
        }
        Instant now = clock.instant();
        if (inbox.recordIfNew(event.eventId(), event.eventType(), event.producer(), now) == 0) {
            return EventOutcome.DUPLICATE;
        }
        switch (type.get()) {
            case USER_REGISTERED -> applyUserRegistered(event, now);
            case ACCOUNT_STATUS_CHANGED -> applyAccountStatusChanged(event, now);
            case ACCOUNT_EMAIL_CHANGED -> requireAccountId(event);
        }
        return EventOutcome.PROCESSED;
    }

    /** Hesap ACTIVE olunca profili açar ya da var olan profile kullanıcı adı kopyasını yazar. Durumu değiştirmez. */
    private void applyUserRegistered(IncomingEvent event, Instant now) {
        UUID accountId = requireAccountId(event);
        String username = text(event.payload(), "username");
        if (!USERNAME.matcher(username).matches()) {
            throw new UserException(UserErrorCode.INVALID_EVENT);
        }
        profiles.insertIfAbsent(accountId, AccountStatus.ACTIVE.name(), null, now);
        UserProfile profile = profiles.findById(accountId).orElseThrow();
        profile.applyUsername(username, now);
    }

    /**
     * Hesap durumunu eşitler. Profil henüz yoksa yalnız kısıtlayıcı durumlar kaydedilir (askıya alınmış hesap sonradan
     * oluşan profille görünür hale gelmesin); ACTIVE gibi durumlar için profil ilk kullanımda zaten ACTIVE açılır.
     */
    private void applyAccountStatusChanged(IncomingEvent event, Instant now) {
        UUID accountId = requireAccountId(event);
        Optional<AccountStatus> parsed = AccountStatus.parse(text(event.payload(), "status"));
        if (parsed.isEmpty()) {
            log.warn("Unknown account status ignored: accountId={}", accountId);
            return;
        }
        AccountStatus status = parsed.get();
        Optional<UserProfile> existing = profiles.findById(accountId);
        if (existing.isPresent()) {
            existing.get().applyStatus(status, event.occurredAt(), now);
        } else if (status.isRestrictive()) {
            profiles.insertIfAbsent(accountId, status.name(), event.occurredAt(), now);
        }
    }

    /** Yükteki {@code accountId}, zarftaki {@code aggregateId} ile aynı hesabı göstermelidir. */
    private static UUID requireAccountId(IncomingEvent event) {
        String raw = text(event.payload(), "accountId");
        try {
            UUID accountId = UUID.fromString(raw);
            if (!accountId.equals(event.aggregateId())) {
                throw new UserException(UserErrorCode.INVALID_EVENT);
            }
            return accountId;
        } catch (IllegalArgumentException _) {
            throw new UserException(UserErrorCode.INVALID_EVENT);
        }
    }

    private static String text(Map<String, Object> payload, String field) {
        if (payload.get(field) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new UserException(UserErrorCode.INVALID_EVENT);
    }
}
