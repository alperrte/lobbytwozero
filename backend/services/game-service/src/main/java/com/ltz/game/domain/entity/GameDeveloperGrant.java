package com.ltz.game.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
/** Geliştirici kullanıcının belirli bir oyun üzerindeki yönetim yetkisi (rol, Auth tokenından ayrı kontrol edilir). */
@Entity @Table(name="game_developer_grants") @Getter @Setter @NoArgsConstructor
public class GameDeveloperGrant {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable=false) private UUID gameId;
    @Column(nullable=false) private UUID developerUserId;
    @Column(nullable=false) private UUID grantedBy;
    @Column(nullable=false) private Instant grantedAt = Instant.now();
    private UUID revokedBy;
    private Instant revokedAt;
    @Version private long version;
    public boolean isActive() { return revokedAt == null; }
}
