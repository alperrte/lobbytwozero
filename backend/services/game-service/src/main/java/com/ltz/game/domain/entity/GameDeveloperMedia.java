package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.DeveloperMediaType;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
/** Yalnız doğrulanmış HTTPS bağlantısı saklanır; sunucu bu adreslere istek atmaz. */
@Entity @Table(name="game_developer_media") @Getter @Setter @NoArgsConstructor
public class GameDeveloperMedia extends DeveloperContentBase {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable=false) private UUID gameId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private DeveloperMediaType type;
    @Column(nullable=false, length=1024) private String url;
    @Column(length=200) private String altText;
}
