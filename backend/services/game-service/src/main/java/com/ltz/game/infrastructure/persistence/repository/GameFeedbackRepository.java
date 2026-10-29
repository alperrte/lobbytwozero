package com.ltz.game.infrastructure.persistence.repository;
import com.ltz.game.domain.entity.GameDeveloperFeedback;
import com.ltz.game.domain.enums.FeedbackStatus;
import com.ltz.game.domain.enums.FeedbackType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface GameFeedbackRepository extends JpaRepository<GameDeveloperFeedback,UUID> {
    Optional<GameDeveloperFeedback> findByIdAndAuthorId(UUID id,UUID authorId);
    Optional<GameDeveloperFeedback> findByIdAndGameId(UUID id,UUID gameId);
    @Query("select f from GameDeveloperFeedback f where f.authorId=:author and (:game is null or f.gameId=:game) and (:type is null or f.type=:type) and (:status is null or f.status=:status)")
    Page<GameDeveloperFeedback> own(@Param("author") UUID author,@Param("game") UUID game,@Param("type") FeedbackType type,@Param("status") FeedbackStatus status,Pageable page);
    @Query("select f from GameDeveloperFeedback f where f.gameId=:game and (:type is null or f.type=:type) and (:status is null or f.status=:status)")
    Page<GameDeveloperFeedback> forGame(@Param("game") UUID game,@Param("type") FeedbackType type,@Param("status") FeedbackStatus status,Pageable page);
}
