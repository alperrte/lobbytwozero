package com.ltz.library.application.service;

import com.ltz.library.api.dto.response.LibraryCollectionItemResponse;
import com.ltz.library.api.dto.response.LibraryCollectionPageResponse;
import com.ltz.library.api.dto.response.LibraryEntryPageResponse;
import com.ltz.library.api.dto.response.LibraryEntryResponse;
import com.ltz.library.api.dto.response.LibraryGameResponse;
import com.ltz.library.api.dto.response.LibraryStatisticsResponse;
import com.ltz.library.domain.enums.CollectionSort;
import com.ltz.library.domain.enums.SortDirection;
import com.ltz.library.domain.enums.GameSummaryStatus;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LibraryCollectionService {
    private final LibraryEntryReader reader;
    private final LibraryGameSummaries summaries;

    public LibraryCollectionService(LibraryEntryReader reader, LibraryGameSummaries summaries) {
        this.reader = reader;
        this.summaries = summaries;
    }

    public LibraryCollectionPageResponse list(UUID userId, Boolean favorite, Boolean completed,
            CollectionSort sort, SortDirection direction, int page, int size) {
        if (sort == CollectionSort.TITLE) return byTitle(userId, favorite, completed, direction, page, size);
        return enrich(reader.collection(userId, favorite, completed, direction, page, size));
    }

    public LibraryStatisticsResponse statistics(UUID userId) { return reader.statistics(userId); }

    private LibraryCollectionPageResponse enrich(LibraryEntryPageResponse page) {
        var games = summaries.resolve(page.items().stream().map(entry -> entry.gameId()).toList());
        var items = enriched(page.items(), games);
        return new LibraryCollectionPageResponse(items, page.page(), page.size(), page.totalElements(), partial(items));
    }

    private LibraryCollectionPageResponse byTitle(UUID userId, Boolean favorite, Boolean completed,
            SortDirection direction, int page, int size) {
        var snapshot = reader.snapshot(userId, favorite, completed);
        int start = (int) Math.min((long) page * size, snapshot.size());
        if (start == snapshot.size()) return new LibraryCollectionPageResponse(List.of(), page, size, snapshot.size(), false);
        var games = summaries.resolve(snapshot.stream().map(LibraryEntryResponse::gameId).toList());
        Comparator<String> titles = direction == SortDirection.ASC ? Comparator.naturalOrder() : Comparator.reverseOrder();
        var sorted = enriched(snapshot, games).stream().sorted(Comparator
                .comparing((LibraryCollectionItemResponse item) -> titleKey(item.game().title()), Comparator.nullsLast(titles))
                .thenComparing(item -> item.entry().id().toString())).toList();
        int end = (int) Math.min((long) start + size, sorted.size());
        return new LibraryCollectionPageResponse(sorted.subList(start, end), page, size, sorted.size(), partial(sorted));
    }

    private static String titleKey(String title) {
        return title == null ? null : Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static List<LibraryCollectionItemResponse> enriched(List<LibraryEntryResponse> entries, Map<UUID, LibraryGameResponse> games) {
        return entries.stream().map(entry -> new LibraryCollectionItemResponse(entry, games.get(entry.gameId()))).toList();
    }

    private static boolean partial(List<LibraryCollectionItemResponse> items) {
        return items.stream().anyMatch(item -> item.game().status() != GameSummaryStatus.AVAILABLE || item.game().title() == null);
    }
}
