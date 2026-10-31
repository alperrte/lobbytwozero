package com.ltz.library.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Kullanıcıdan bağımsız public metadata; status yalnız son başarılı gözlemi anlatır. */
@Entity
@Table(name = "game_snapshots")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor
public class GameSnapshot {
    @Id private UUID gameId;
    @Column(nullable = false, length = 256) private String title;
    @Column(length = 2048) private String coverUrl;
    @Column(length = 16) private String origin;
    @Column(length = 16) private String metadataCompleteness;
    @Column(length = 32) private String gameType;
    @Column(nullable = false, length = 16) private String status;
    @Column(nullable = false) private Instant refreshedAt;
    @Version private long version;
}
