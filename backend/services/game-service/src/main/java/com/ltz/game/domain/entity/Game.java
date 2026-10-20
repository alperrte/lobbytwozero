package com.ltz.game.domain.entity;
import jakarta.persistence.*;
import com.ltz.game.domain.enums.*;
import lombok.*;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="games") @Getter @Setter @NoArgsConstructor
public class Game {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable=false, length=256) private String title;
    @Column(nullable=false, length=1024) private String normalizedTitle;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant updatedAt = Instant.now();
    @Version private long version;
    @Column(length=16000) private String description;
    private java.time.LocalDate releaseDate;
    private Instant detailsFetchedAt;
    private Instant detailsExpiresAt;
    private Instant detailsNextAttemptAt;
    private int detailsFailureCount;
    @Column(length=64) private String detailsLastErrorCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32)
    private GameOrigin origin = GameOrigin.INTERNAL;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32)
    private MetadataCompleteness metadataCompleteness = MetadataCompleteness.INDEX_ONLY;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32)
    private GameStatus status = GameStatus.ACTIVE;
    @OneToMany(mappedBy="game", cascade=CascadeType.ALL, orphanRemoval=true)
    private Set<GameExternalReference> externalReferences = new HashSet<>();
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32)
    private GameType gameType = GameType.UNKNOWN;
    public boolean isActive() { return status == GameStatus.ACTIVE; }
    @ManyToMany @JoinTable(name="game_terms", joinColumns=@JoinColumn(name="game_id"), inverseJoinColumns=@JoinColumn(name="term_id"))
    private Set<CatalogTerm> terms = new HashSet<>();
    @ElementCollection @CollectionTable(name="game_developers", joinColumns=@JoinColumn(name="game_id")) @Column(name="name", length=256)
    private Set<String> developers = new HashSet<>();
    @ElementCollection @CollectionTable(name="game_publishers", joinColumns=@JoinColumn(name="game_id")) @Column(name="name", length=256)
    private Set<String> publishers = new HashSet<>();
    @OneToMany(mappedBy="game", cascade=CascadeType.ALL, orphanRemoval=true)
    private List<GameMedia> media = new ArrayList<>();
    @OneToMany(mappedBy="game", cascade=CascadeType.ALL, orphanRemoval=true)
    private List<GameSystemRequirement> systemRequirements = new ArrayList<>();
}
