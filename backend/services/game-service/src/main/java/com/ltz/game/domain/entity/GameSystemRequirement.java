package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.RequirementLevel;
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
@Entity @Table(name="game_system_requirements") @Getter @Setter @NoArgsConstructor
public class GameSystemRequirement {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="game_id") private Game game;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private RequirementLevel level;
    @Column(nullable=false, length=64) private String platform;
    @Column(length=512) private String os;
    @Column(length=512) private String cpu;
    @Column(length=512) private String gpu;
    private Integer ramMb;
    private Integer storageMb;
    @Column(length=16000) private String rawText;
    @Version private long version;
}
