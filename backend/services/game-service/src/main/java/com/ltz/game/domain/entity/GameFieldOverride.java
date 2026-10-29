package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.OverridableField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
/** Manuel düzeltilmiş alan; sağlayıcı senkronizasyonu bu alanı ezmez. */
@Entity @Table(name="game_field_overrides") @Getter @Setter @NoArgsConstructor
public class GameFieldOverride {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable=false) private UUID gameId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private OverridableField field;
    @Column(nullable=false) private UUID actorId;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Version private long version;
}
