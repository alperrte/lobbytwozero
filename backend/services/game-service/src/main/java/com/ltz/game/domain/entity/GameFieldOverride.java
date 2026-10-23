package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.OverridableField;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
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
