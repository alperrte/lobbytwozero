package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataCompleteness;
import com.ltz.game.domain.enums.MetadataDataState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    private Boolean earlyAccess;
    private Integer editorRank;
    private Instant popularityUpdatedAt;
    private UUID editorActorId;
    @Column(length=1000) private String editorEvidence;
    @ElementCollection @CollectionTable(name="game_languages", joinColumns=@JoinColumn(name="game_id"))
    @Column(name="language_code", length=35, nullable=false)
    private Set<String> languages = new HashSet<>();
    @Column(length=35) private String metadataLanguage;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16)
    private MetadataDataState languageDataState = MetadataDataState.UNKNOWN;
    @ElementCollection @CollectionTable(name="game_language_supports", joinColumns=@JoinColumn(name="game_id"))
    private Set<GameLanguageSupport> languageSupport = new HashSet<>();
    @ElementCollection @CollectionTable(name="game_metadata_provenance", joinColumns=@JoinColumn(name="game_id"))
    @MapKeyColumn(name="field", length=32)
    private Map<String,GameMetadataProvenance> metadataProvenance = new HashMap<>();
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
