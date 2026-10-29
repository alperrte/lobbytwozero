package com.ltz.game.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity @Table(name="game_identity_audits") @Immutable @Getter @Setter @NoArgsConstructor
public class GameIdentityAudit {
    @Id private UUID id=UUID.randomUUID();
    @Column(nullable=false) private UUID referenceId;
    @Column(nullable=false) private UUID fromGameId;
    @Column(nullable=false) private UUID toGameId;
    @Column(nullable=false) private UUID actorId;
    @Column(nullable=false,length=32) private String action;
    @Column(nullable=false,length=1000) private String evidence;
    @Column(nullable=false) private Instant createdAt=Instant.now();
}
