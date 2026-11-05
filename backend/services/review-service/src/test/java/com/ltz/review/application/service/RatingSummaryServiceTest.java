package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.GameRatingSummaryResponse;
import com.ltz.review.infrastructure.persistence.repository.RatingSummaryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RatingSummaryServiceTest {
    @Test void deduplicatesInFirstOccurrenceOrderAndReturnsImmutableItems() {
        var repository = mock(RatingSummaryRepository.class);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var summaries = List.of(new GameRatingSummaryResponse(first, new BigDecimal("7.50"), 2),
                new GameRatingSummaryResponse(second, null, 0));
        when(repository.findAll(List.of(first, second))).thenReturn(summaries);
        var result = new RatingSummaryService(repository).getAll(List.of(first, second, first));
        assertEquals(summaries, result.items());
        verify(repository).findAll(List.of(first, second));
        var items = result.items();
        assertThrows(UnsupportedOperationException.class, items::clear);
    }

    @Test void pageReturnsCursorOnlyWhenMoreRowsExist() {
        var repository = mock(RatingSummaryRepository.class);
        var a = new GameRatingSummaryResponse(UUID.randomUUID(), new BigDecimal("5.00"), 1);
        var b = new GameRatingSummaryResponse(UUID.randomUUID(), new BigDecimal("6.00"), 2);
        var c = new GameRatingSummaryResponse(UUID.randomUUID(), new BigDecimal("7.00"), 3);
        var after = UUID.randomUUID();
        var service = new RatingSummaryService(repository);
        when(repository.findPage(after, 3)).thenReturn(List.of(a, b, c));
        var more = service.page(after, 2);
        assertEquals(List.of(a, b), more.items());
        assertEquals(b.gameId(), more.nextAfter());
        when(repository.findPage(null, 3)).thenReturn(List.of(a, b));
        var last = service.page(null, 2);
        assertEquals(List.of(a, b), last.items());
        assertEquals(null, last.nextAfter());
        when(repository.findPage(null, 4)).thenReturn(List.of());
        assertEquals(List.of(), service.page(null, 3).items());
        assertEquals(null, service.page(null, 3).nextAfter());
    }

    @Test void singleSummaryPreservesDecimalPrecision() {
        var repository = mock(RatingSummaryRepository.class);
        var game = UUID.randomUUID();
        var summary = new GameRatingSummaryResponse(game, new BigDecimal("1.13"), 8);
        when(repository.find(game)).thenReturn(summary);
        assertEquals(summary, new RatingSummaryService(repository).get(game));
    }
}
