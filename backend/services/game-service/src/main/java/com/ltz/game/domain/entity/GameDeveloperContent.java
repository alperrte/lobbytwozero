package com.ltz.game.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
/** Oyun başına tek geliştirici açıklaması; katalog açıklamasından (games.description) ayrıdır. */
@Entity @Table(name="game_developer_contents") @Getter @Setter @NoArgsConstructor
public class GameDeveloperContent extends DeveloperContentBase {
    @Id private UUID gameId;
    @Column(nullable=false, length=8000) private String description;
}
