package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryGameResponse;
import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.domain.enums.GameSummaryStatus;
import com.ltz.library.domain.exception.CatalogUnavailableException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LibraryGameSummaries {
    private final GameCatalogPort catalog;

    public LibraryGameSummaries(GameCatalogPort catalog) { this.catalog = catalog; }

    /** Tek istekte 50 kimlik; ilk geçici hatadan sonra aynı istekte yeniden deneme yapılmaz. */
    public Map<UUID, LibraryGameResponse> resolve(List<UUID> gameIds) {
        List<UUID> ids = gameIds.stream().distinct().toList();
        Map<UUID, LibraryGameResponse> result = new LinkedHashMap<>();
        boolean unavailable = false;
        for (int start = 0; start < ids.size(); start += 50) {
            List<UUID> batch = ids.subList(start, Math.min(start + 50, ids.size()));
            if (!unavailable) {
                try {
                    var cards = catalog.summaries(batch);
                    for (UUID id : batch) {
                        var card = cards.get(id);
                        result.put(id, card == null ? missing(id, GameSummaryStatus.UNAVAILABLE)
                                : new LibraryGameResponse(id, card.title(), card.coverUrl(), card.origin(), card.metadataCompleteness(), card.gameType(), GameSummaryStatus.AVAILABLE));
                    }
                } catch (CatalogUnavailableException ignored) { unavailable = true; }
            }
            if (unavailable) batch.forEach(id -> result.put(id, missing(id, GameSummaryStatus.TEMPORARILY_UNAVAILABLE)));
        }
        return result;
    }

    private static LibraryGameResponse missing(UUID id, GameSummaryStatus status) {
        return new LibraryGameResponse(id, null, null, null, null, null, status);
    }
}
