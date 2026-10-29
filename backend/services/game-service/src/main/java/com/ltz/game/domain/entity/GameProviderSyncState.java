package com.ltz.game.domain.entity;

import com.ltz.game.domain.enums.CatalogRunMode;
import com.ltz.game.domain.enums.CatalogSyncStatus;
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
    private int failureCount;
    private Instant nextAttemptAt;
    @Version private long version;
}
