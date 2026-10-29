package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.DeveloperMediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
/** Yalnız doğrulanmış HTTPS bağlantısı saklanır; sunucu bu adreslere istek atmaz. */
@Entity @Table(name="game_developer_media") @Getter @Setter @NoArgsConstructor
public class GameDeveloperMedia extends DeveloperContentBase {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable=false) private UUID gameId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private DeveloperMediaType type;
    @Column(nullable=false, length=1024) private String url;
    @Column(length=200) private String altText;
}
