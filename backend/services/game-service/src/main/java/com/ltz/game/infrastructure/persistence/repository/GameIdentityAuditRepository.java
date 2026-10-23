package com.ltz.game.infrastructure.persistence.repository;
import com.ltz.game.domain.entity.GameIdentityAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface GameIdentityAuditRepository extends JpaRepository<GameIdentityAudit, UUID> { }
