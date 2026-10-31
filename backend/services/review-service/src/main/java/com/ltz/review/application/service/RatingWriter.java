package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.RatingResponse;
import com.ltz.review.infrastructure.persistence.repository.RatingRepository;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RatingWriter {
    private final RatingRepository ratings;
    private final AccountWriteLock accounts;

    public RatingWriter(RatingRepository ratings, AccountWriteLock accounts) {
        this.ratings = ratings;
        this.accounts = accounts;
    }

    @Transactional(timeout = 4)
    public RatingResponse put(UUID userId, UUID gameId, int rating) {
        accounts.requireWritable(userId);
        ratings.put(userId, gameId, rating);
        return ratings.find(userId, gameId).orElseThrow();
    }
}
