package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameUpdateNote;
import com.ltz.game.domain.enums.ContentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameUpdateNoteRepository extends JpaRepository<GameUpdateNote, UUID> {
    List<GameUpdateNote> findByGameIdAndStatusOrderByCreatedAtDescIdAsc(UUID gameId, ContentStatus status, Pageable pageable);
    List<GameUpdateNote> findByGameIdAndStatusNotOrderByCreatedAtDescIdAsc(UUID gameId, ContentStatus status, Pageable pageable);
    Optional<GameUpdateNote> findByIdAndGameId(UUID id, UUID gameId);
    long countByGameIdAndStatusNot(UUID gameId, ContentStatus status);
}
