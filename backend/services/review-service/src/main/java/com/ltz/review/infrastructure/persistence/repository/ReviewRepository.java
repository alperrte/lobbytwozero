package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.domain.entity.Review;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByUserIdAndGameIdAndStatusNot(UUID userId, UUID gameId, String status);

    /** Kullanıcının bir oyundaki tek aktif incelemesi (uq_reviews_active_user_game kısmi tekilliği). */
    Optional<Review> findByUserIdAndGameIdAndStatus(UUID userId, UUID gameId, String status);

    /** Kullanıcının kendi incelemeleri; yalnız verilen durum (VISIBLE). Sıra çağıranın Pageable'ındadır. */
    Page<Review> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> lockById(@Param("id") UUID id);
}
