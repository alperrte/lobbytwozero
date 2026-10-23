package com.ltz.game.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_external_refs")
@Getter @Setter @NoArgsConstructor
public class GameExternalReference {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id") private Game game;
    @Column(nullable = false, length = 32) private String provider;
    @Column(nullable = false, length = 128) private String externalId;
    private Instant lastSyncedAt;
    @Version private long version;
}
