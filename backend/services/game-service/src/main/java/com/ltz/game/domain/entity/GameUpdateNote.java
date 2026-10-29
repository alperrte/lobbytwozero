package com.ltz.game.domain.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="game_update_notes") @Getter @Setter @NoArgsConstructor
public class GameUpdateNote extends DeveloperContentBase {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable=false) private UUID gameId;
    @Column(nullable=false, length=120) private String title;
    @Column(nullable=false, length=4000) private String body;
}
