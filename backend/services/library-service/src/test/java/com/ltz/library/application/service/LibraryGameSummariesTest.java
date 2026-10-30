package com.ltz.library.application.service;

import com.ltz.library.application.port.GameCatalogPort;
import com.ltz.library.application.port.GameSummary;
import com.ltz.library.domain.enums.GameSummaryStatus;
import com.ltz.library.domain.exception.CatalogUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class LibraryGameSummariesTest {
    private GameCatalogPort catalog;
    private LibraryGameSummaries summaries;

    @BeforeEach void setup() { catalog = mock(GameCatalogPort.class); summaries = new LibraryGameSummaries(catalog); }

    @Test void fiftyGamesUseOneBatchAndLargerRequestsUseOnlyBoundedChunks() {
        when(catalog.summaries(anyList())).thenAnswer(invocation -> available(invocation.getArgument(0)));
        assertEquals(50, summaries.resolve(ids(50)).size());
        verify(catalog).summaries(org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 50));
        org.mockito.Mockito.clearInvocations(catalog);
        var ids = ids(121);
        assertEquals(121, summaries.resolve(ids).size());
        ArgumentCaptor<List<UUID>> batches = ArgumentCaptor.captor();
        verify(catalog, times(3)).summaries(batches.capture());
        assertEquals(List.of(50, 50, 21), batches.getAllValues().stream().map(List::size).toList());
        assertEquals(ids, batches.getAllValues().stream().flatMap(List::stream).toList());
    }

    @Test void noIdsNeedNoCallAndDuplicateIdsAreDeduplicated() {
        assertEquals(Map.of(), summaries.resolve(List.of()));
        verifyNoInteractions(catalog);
        UUID id = UUID.randomUUID();
        when(catalog.summaries(List.of(id))).thenReturn(Map.of());
        var result = summaries.resolve(List.of(id, id, id));
        assertEquals(1, result.size());
        assertEquals(GameSummaryStatus.UNAVAILABLE, result.get(id).status());
        verify(catalog).summaries(List.of(id));
    }

    @Test void firstFailureStopsFurtherRequestsWithoutLosingCollectionIdentities() {
        when(catalog.summaries(anyList())).thenThrow(new CatalogUnavailableException());
        var result = summaries.resolve(ids(120));
        assertEquals(120, result.size());
        assertEquals(120, result.values().stream().filter(card -> card.status() == GameSummaryStatus.TEMPORARILY_UNAVAILABLE).count());
        verify(catalog).summaries(anyList());
    }

    @Test void laterFailurePreservesEarlierBatchAndDoesNotRetryOrContinue() {
        var ids = ids(120);
        when(catalog.summaries(anyList())).thenAnswer(invocation -> available(ids.subList(0, 50)))
                .thenThrow(new CatalogUnavailableException());
        var result = summaries.resolve(ids);
        assertEquals(50, result.values().stream().filter(card -> card.status() == GameSummaryStatus.AVAILABLE).count());
        assertEquals(70, result.values().stream().filter(card -> card.status() == GameSummaryStatus.TEMPORARILY_UNAVAILABLE).count());
        verify(catalog, times(2)).summaries(anyList());
    }

    private static List<UUID> ids(int count) { return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList(); }

    private static Map<UUID, GameSummary> available(List<UUID> ids) {
        return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                id -> new GameSummary(id, "Example", null, "INTERNAL", "INDEX_ONLY", "MAIN_GAME")));
    }
}
