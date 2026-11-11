package com.ltz.messaging.application.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ltz.messaging.application.port.GifProviderPort;
import com.ltz.messaging.application.port.GifProviderPort.GifItem;
import com.ltz.messaging.application.port.GifProviderPort.GifPage;
import com.ltz.messaging.application.port.GifProviderUnavailableException;
import com.ltz.messaging.domain.entity.GifSnapshot;
import com.ltz.messaging.domain.enums.GifProvider;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.GifProperties;

import lombok.RequiredArgsConstructor;

/**
 * GIF arama ve gönderilecek GIF'i çözme (MSG-22). Arama yalnız kimlik ister (sohbetten bağımsızdır); platforma
 * kullanıcı kimliği gönderilmez, yalnız arama metni gider. Girdi sunucuda doğrulanır, kullanıcı başına dakikalık sınır
 * uygulanır. Platform kapalıysa (anahtar yok) ya da yanıt vermiyorsa {@code 503 GIF_PROVIDER_UNAVAILABLE}.
 */
@Service
@RequiredArgsConstructor
public class GifService {

    private static final Set<String> LANGUAGES = Set.of("tr", "en");
    private static final String DEFAULT_LANGUAGE = "tr";

    private final GifProviderPort provider;
    private final GifSearchRateLimiter rateLimiter;
    private final GifProperties properties;

    public GifPage search(UUID userId, String query, Integer limit, Integer offset, String language) {
        String text = query == null ? "" : query.strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > properties.maxQueryLength()
                || text.codePoints().anyMatch(Character::isISOControl)) {
            throw new MessagingException(MessagingErrorCode.INVALID_GIF_QUERY);
        }
        String lang = language == null ? DEFAULT_LANGUAGE : language;
        if (!LANGUAGES.contains(lang)) {
            throw new MessagingException(MessagingErrorCode.INVALID_GIF_QUERY);
        }
        int size = pageSize(limit);
        int start = offset(offset);
        requireQuota(userId);
        try {
            return provider.search(text, size, start, lang);
        } catch (GifProviderUnavailableException _) {
            throw new MessagingException(MessagingErrorCode.GIF_PROVIDER_UNAVAILABLE);
        }
    }

    public GifPage trending(UUID userId, Integer limit, Integer offset) {
        int size = pageSize(limit);
        int start = offset(offset);
        requireQuota(userId);
        try {
            return provider.trending(size, start);
        } catch (GifProviderUnavailableException _) {
            throw new MessagingException(MessagingErrorCode.GIF_PROVIDER_UNAVAILABLE);
        }
    }

    /**
     * Gönderilecek GIF'i platformdan çözer; adresler istemciden alınmaz. Ağ çağrısıdır: transaction dışında çağrılmalıdır.
     *
     * @throws MessagingException 422 {@code GIF_NOT_AVAILABLE}, 503 {@code GIF_PROVIDER_UNAVAILABLE}
     */
    public GifSnapshot resolve(GifProvider gifProvider, String gifId) {
        GifItem item;
        try {
            item = provider.find(gifId)
                    .filter(found -> found.provider() == gifProvider)
                    .orElseThrow(() -> new MessagingException(MessagingErrorCode.GIF_NOT_AVAILABLE));
        } catch (GifProviderUnavailableException _) {
            throw new MessagingException(MessagingErrorCode.GIF_PROVIDER_UNAVAILABLE);
        }
        return new GifSnapshot(item.provider(), item.id(), item.url(), item.previewUrl(), item.width(), item.height());
    }

    private int pageSize(Integer limit) {
        return PageSizes.resolve(limit, properties.defaultLimit(), properties.maxLimit());
    }

    private int offset(Integer offset) {
        if (offset == null) {
            return 0;
        }
        if (offset < 0 || offset > properties.maxOffset()) {
            throw new MessagingException(MessagingErrorCode.INVALID_GIF_QUERY);
        }
        return offset;
    }

    private void requireQuota(UUID userId) {
        if (!rateLimiter.tryAcquire(userId)) {
            throw new MessagingException(MessagingErrorCode.TOO_MANY_GIF_SEARCHES);
        }
    }
}
