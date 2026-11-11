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
// DynamicUpdate: yalnız değişen sütunlar yazılır. Tüm sütunları yazan güncelleme unique indeksli editor_rank'i de SET listesine koyar ve Postgres satırı
// fiyat tablolarının yabancı anahtar paylaşım kilidiyle çakışan düzeyde kilitler (katalog işi ile fiyat yenilemesi arasında deadlock; DEV'de görüldü).
@Entity @Table(name="games") @org.hibernate.annotations.DynamicUpdate @Getter @Setter @NoArgsConstructor
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
    /** Steam mağaza bilgisinden: ücretsiz oyun. null = bilinmiyor (Steam oyunu tanımadı); false varsayılmaz. */
    private Boolean free;
    /** Steam mağaza bilgisinden: henüz çıkmamış ("yakında"). null = bilinmiyor. */
    private Boolean comingSoon;
    /** Steam içerik tanımlayıcıları 3 veya 4 (yetişkin içerik). null = bilinmiyor. Varsayılan listelerde gizlenir, "+18 oyunları göster" ile açılır. */
    private Boolean adult;
    /** Steam'in art arda tanımadığı (mağazadan kaldırılmış) oyun; kayıt silinmez, keşif listelerinden gizlenir. Steam tekrar tanırsa null olur. */
    private Instant delistedAt;
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
    /** DLC'nin ana oyunu (aynı tabloda MAIN_GAME); yalnız DLC için dolu olabilir. Veritabanı kısıtları kendine bağlanmayı ve DLC olmayan kayda bağlanmayı reddeder. */
    @Column(name="parent_game_id") private UUID parentGameId;
    /**
     * Steam'in çıkış durumunu uygular: ücretsiz, "henüz çıkmamış" ve yetişkin içerik bilgisi Steam'e aittir ve tanınan oyunda her zaman Steam'in
     * değeridir (oyun çıkınca {@code comingSoon} false olur).
     *
     * @return değerlerden biri değiştiyse (ya da ilk kez yazıldıysa) true
     */
    public boolean applySteamReleaseStatus(boolean freeValue, boolean comingSoonValue, boolean adultValue) {
        boolean changed = !Boolean.valueOf(freeValue).equals(free) || !Boolean.valueOf(comingSoonValue).equals(comingSoon)
                || !Boolean.valueOf(adultValue).equals(adult);
        free = freeValue;
        comingSoon = comingSoonValue;
        adult = adultValue;
        return changed;
    }

    /** Steam oyunu art arda tanımadı: ilk kez işaretlenirse true (zaten işaretliyse özgün zaman korunur). */
    public boolean markDelisted(Instant at) {
        if (delistedAt != null) return false;
        delistedAt = at;
        return true;
    }

    /** Steam oyunu yeniden tanıdı: işaret kalktıysa true. */
    public boolean clearDelisted() {
        boolean changed = delistedAt != null;
        delistedAt = null;
        return changed;
    }

    public boolean isDelisted() { return delistedAt != null; }
    /** DLC dışına geçen kayıt ana oyun ilişkisini taşıyamaz (games_parent_only_dlc). */
    public void setGameType(GameType gameType) {
        this.gameType = gameType;
        if (gameType != GameType.DLC) this.parentGameId = null;
    }
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
