package com.ltz.review.application.service;

import com.ltz.review.infrastructure.persistence.repository.FeaturedReviewRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeaturedReviewServiceTest {
    @Test void computesPartialLastPageAndForwardsFilterAndOffset() {
        var repository = mock(FeaturedReviewRepository.class);
        var game = UUID.randomUUID();
        var actor = UUID.randomUUID();
        when(repository.count(game, 3)).thenReturn(51L);
        when(repository.find(game, actor, 3, 50, 50L)).thenReturn(List.of());
        var page = new FeaturedReviewService(repository).get(game, actor, 1, 50, 3);
        assertEquals(2L, page.totalPages());
        assertEquals(51L, page.totalElements());
        assertEquals(50, page.size());
        verify(repository).find(game, actor, 3, 50, 50L);
    }

    @Test void exactMultipleHasNoExtraPage() {
        var repository = mock(FeaturedReviewRepository.class);
        var game = UUID.randomUUID();
        var actor = UUID.randomUUID();
        when(repository.count(game, 0)).thenReturn(100L);
        when(repository.find(game, actor, 0, 50, 0L)).thenReturn(List.of());
        assertEquals(2L, new FeaturedReviewService(repository).get(game, actor, 0, 50, 0).totalPages());
    }
}
