package com.ltz.game.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bir oyunun belirli bir dildeki açıklaması; yalnız sağlayıcının o dilde gerçekten verdiği metin (çeviri değildir). Varsayılan açıklama
 * {@link Game#getDescription()} alanındadır. (oyun, dil) çifti tektir.
 */
@Entity @Table(name = "game_localized_descriptions") @Getter @Setter @NoArgsConstructor
public class GameLocalizedDescription {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "game_id") private Game game;
    @Column(nullable = false, length = 35) private String language;
    @Column(nullable = false, length = 16000) private String description;
    @Column(nullable = false, length = 32) private String source;
    @Column(nullable = false) private Instant fetchedAt;
    @Version private long version;
}
