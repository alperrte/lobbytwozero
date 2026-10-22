package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.MediaType;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
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
