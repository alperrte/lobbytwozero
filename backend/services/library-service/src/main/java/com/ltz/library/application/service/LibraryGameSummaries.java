package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryGameResponse;
import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.domain.enums.GameSummaryStatus;
import com.ltz.library.domain.enums.GameMetadataSource;
import com.ltz.library.domain.entity.GameSnapshot;
import com.ltz.library.application.port.GameSummary;
import java.time.Instant;
import com.ltz.library.domain.exception.CatalogUnavailableException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LibraryGameSummaries {
    private final GameCatalogPort catalog;
    private final GameSnapshotService snapshots;

    public LibraryGameSummaries(GameCatalogPort catalog, GameSnapshotService snapshots) {
        this.catalog = catalog;
        this.snapshots = snapshots;
    }

    /** Tek istekte 50 kimlik; ilk geçici hatadan sonra aynı istekte yeniden deneme yapılmaz. */
    public Map<UUID, LibraryGameResponse> resolve(List<UUID> gameIds) {
        List<UUID> ids = gameIds.stream().distinct().toList();
        Map<UUID, LibraryGameResponse> result = new LinkedHashMap<>();
        boolean unavailable = false;
        for (int start = 0; start < ids.size(); start += 50) {
            List<UUID> batch = ids.subList(start, Math.min(start + 50, ids.size()));
            var stored = snapshots.read(batch);
            if (!unavailable) {
                try {
                    var cards = catalog.summaries(batch);
                    Instant observedAt = Instant.now();
                    snapshots.remember(List.copyOf(cards.values()), observedAt);
                    for (UUID id : batch) {
                        var card = cards.get(id);
                        result.put(id, current(id, card, stored.get(id), observedAt));
                    }
                } catch (CatalogUnavailableException _) { unavailable = true; }
            }
            if (unavailable) batch.forEach(id -> result.put(id, fallback(id, stored.get(id), GameSummaryStatus.TEMPORARILY_UNAVAILABLE)));
        }
        return result;
    }

    private static LibraryGameResponse current(UUID id, GameSummary card, GameSnapshot stored, Instant observedAt) {
        if (card == null) return fallback(id, stored, GameSummaryStatus.UNAVAILABLE);
        if (card.title() == null && stored != null) return fallback(id, stored, GameSummaryStatus.AVAILABLE);
        return new LibraryGameResponse(id, card.title(), card.coverUrl(), card.origin(), card.metadataCompleteness(), card.gameType(),
                GameSummaryStatus.AVAILABLE, GameMetadataSource.LIVE, observedAt);
    }

    private static LibraryGameResponse fallback(UUID id, GameSnapshot stored, GameSummaryStatus status) {
        if (stored == null) return new LibraryGameResponse(id, null, null, null, null, null, status, GameMetadataSource.NONE, null);
        return new LibraryGameResponse(id, stored.getTitle(), stored.getCoverUrl(), stored.getOrigin(), stored.getMetadataCompleteness(),
                stored.getGameType(), status, GameMetadataSource.LAST_KNOWN, stored.getRefreshedAt());
    }
}
