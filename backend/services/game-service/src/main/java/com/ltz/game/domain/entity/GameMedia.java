package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="game_media") @Getter @Setter @NoArgsConstructor
public class GameMedia {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="game_id") private Game game;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private MediaType type;
    @Column(nullable=false, length=2048) private String url;
    @Column(length=32) private String provider;
    private int sortOrder;
    @Version private long version;
}
