package com.ltz.game.domain.entity;

import com.ltz.game.domain.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="game_provider_sync_state") @Getter @Setter @NoArgsConstructor
public class GameProviderSyncState {
    @Id private UUID id;
    @Column(nullable=false, length=32) private String provider;
    private long cursor;
    @Enumerated(EnumType.STRING) @Column(length=32) private CatalogRunMode runMode;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32)
    private CatalogSyncStatus syncStatus = CatalogSyncStatus.IDLE;
    private Instant querySince;
    private Instant runStartedAt;
    private Instant lastSuccessfulAt;
    private long processedCount;
    @Column(length=64) private String lastErrorCode;
    @Version private long version;
}
