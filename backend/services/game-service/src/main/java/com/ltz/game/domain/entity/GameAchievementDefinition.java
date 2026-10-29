package com.ltz.game.domain.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
/** Oyunun tanımıdır; kullanıcı ilerlemesi değildir. */
@Entity @Table(name="game_achievement_definitions") @Getter @Setter @NoArgsConstructor
public class GameAchievementDefinition {
    @Id private UUID id=UUID.randomUUID();
    @Column(nullable=false) private UUID gameId;
    @Column(nullable=false,length=64) private String code;
    @Column(nullable=false,length=120) private String title;
    @Column(length=2000) private String description;
    @Column(nullable=false) private boolean visible=true;
    @Version private long version;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
}
