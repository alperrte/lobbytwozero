package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.UUID;
/** Append-only yönetim kaydı. İçerik metni değil, aktör, eylem, gerekçe ve değişen alan adları tutulur. */
@Entity @Table(name="game_admin_audits") @Immutable @Getter @Setter @NoArgsConstructor
public class GameAdminAudit {
    @Id private UUID id = UUID.randomUUID();
    private UUID gameId;
    @Column(nullable=false) private UUID actorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private ActorRole actorRole;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=48) private AdminAuditAction action;
    @Column(length=1000) private String reason;
    @Column(length=2000) private String details;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}
