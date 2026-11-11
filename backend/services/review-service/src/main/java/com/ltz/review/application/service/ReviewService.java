package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.ReviewPageResponse;
import com.ltz.review.api.dto.response.ReviewResponse;
import com.ltz.review.application.mapper.ReviewMapper;
import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.ReviewRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private static final String VISIBLE = "VISIBLE";

    private final GameCatalogPort games;
    private final ReviewWriter writer;
    private final ReviewRepository reviews;

    public ReviewService(GameCatalogPort games, ReviewWriter writer, ReviewRepository reviews) {
        this.games = games;
        this.writer = writer;
        this.reviews = reviews;
    }

    public ReviewResponse create(UUID userId, UUID gameId, String body) {
        String content = checkedBody(body);
        games.requireAvailable(gameId);
        return writer.create(userId, gameId, content);
    }

    public ReviewResponse update(UUID actor, UUID reviewId, long version, String body) {
        String content = checkedBody(body);
        if (version < 0) throw new ReviewException(400, "Sürüm negatif olamaz.");
        var review = reviews.findById(reviewId).orElseThrow(ReviewService::notFound);
        if (!actor.equals(review.getUserId())) throw new ReviewException(403, "Bu inceleme için yetkiniz yok.");
        if (!VISIBLE.equals(review.getStatus())) throw notFound();
        games.requireAvailable(review.getGameId());
        return writer.update(actor, reviewId, version, content);
    }

    public void delete(UUID actor, UUID reviewId, long version) {
        writer.delete(actor, reviewId, version);
    }

    @Transactional(readOnly = true)
    public ReviewResponse get(UUID actor, UUID reviewId) {
        var review = reviews.findById(reviewId).filter(value -> VISIBLE.equals(value.getStatus()))
                .orElseThrow(ReviewService::notFound);
        return ReviewMapper.response(review, actor);
    }

    /** Kullanıcının bu oyundaki görünür incelemesi; yoksa 404 (silinmiş inceleme "yok" sayılır). Yalnız yerel okuma. */
    @Transactional(readOnly = true)
    public ReviewResponse mine(UUID actor, UUID gameId) {
        return reviews.findByUserIdAndGameIdAndStatus(actor, gameId, VISIBLE)
                .map(review -> ReviewMapper.response(review, actor))
                .orElseThrow(ReviewService::notFound);
    }

    /** Kullanıcının tüm oyunlardaki görünür incelemeleri: oluşturulma tarihi azalan, eşitlikte id artan. */
    @Transactional(readOnly = true)
    public ReviewPageResponse mine(UUID actor, int page, int size) {
        var result = reviews.findByUserIdAndStatus(actor, VISIBLE,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new ReviewPageResponse(result.map(review -> ReviewMapper.response(review, actor)).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private static ReviewException notFound() { return new ReviewException(404, "İnceleme bulunamadı."); }

    static String checkedBody(String body) {
        if (body == null || body.length() > 5000 || body.codePoints().allMatch(value -> Character.isWhitespace(value) || Character.isSpaceChar(value))
                || body.codePoints().anyMatch(value -> Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t'))
            throw new ReviewException(400, "İnceleme 1–5000 karakter düz metin olmalıdır.");
        return body.strip();
    }
}
