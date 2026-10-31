package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.RatingResponse;
import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.RatingRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RatingService {
    private final GameCatalogPort games;
    private final RatingWriter writer;
    private final RatingRepository ratings;

    public RatingService(GameCatalogPort games, RatingWriter writer, RatingRepository ratings) {
        this.games = games;
        this.writer = writer;
        this.ratings = ratings;
    }

    public RatingResponse rate(UUID userId, UUID gameId, int rating) {
        if (rating < 1 || rating > 10) throw new ReviewException(400, "Puan 1–10 tam sayı olmalıdır.");
        games.requireAvailable(gameId);
        return writer.put(userId, gameId, rating);
    }

    public RatingResponse get(UUID userId, UUID gameId) {
        return ratings.find(userId, gameId).orElseThrow(() -> new ReviewException(404, "Puan bulunamadı."));
    }
}
