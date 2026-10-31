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

    @Test void singleSummaryPreservesDecimalPrecision() {
        var repository = mock(RatingSummaryRepository.class);
        var game = UUID.randomUUID();
        var summary = new GameRatingSummaryResponse(game, new BigDecimal("1.13"), 8);
        when(repository.find(game)).thenReturn(summary);
        assertEquals(summary, new RatingSummaryService(repository).get(game));
    }
}
