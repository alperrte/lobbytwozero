package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.RequirementLevel;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
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
