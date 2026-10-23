package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameAdminAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameAdminAuditRepository extends JpaRepository<GameAdminAudit, UUID> {
    List<GameAdminAudit> findByGameIdOrderByCreatedAtAsc(UUID gameId);
}
