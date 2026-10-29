package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.MetadataDataState;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Embeddable @Getter @Setter @NoArgsConstructor
public class GameMetadataProvenance {
    @Column(nullable=false, length=32) private String source;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private MetadataDataState dataState;
    @Column(nullable=false) private Instant recordedAt;
    private Instant verifiedAt;
}
