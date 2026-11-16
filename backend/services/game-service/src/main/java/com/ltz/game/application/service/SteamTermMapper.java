package com.ltz.game.application.service;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.TermKind;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Steam mağaza etiket adı, oyuncu kategorisi kimliği ve platform bayrağını katalogdaki MEVCUT terim kodlarına eşler (tür, etiket, kategori,
 * platform). Tablolar belgeli ve testle sabittir; eşlemesi olmayan Steam değeri yok sayılır, yeni terim uydurulmaz. Eşleme "en yakın doğru"dur:
 * kesin karşılığı olmayan etiket (ör. "Casual", "Cute") atlanır. Kaynak veri 40 bin Steam kaydının etiket frekansından çıkarılmıştır.
 */
@Component
public class SteamTermMapper {
    private static final String ADVENTURE = "adventure";
    private static final String ROLE_PLAYING_RPG = "role-playing-rpg";
    private static final String STRATEGY = "strategy";
    private static final String SIMULATOR = "simulator";
    private static final String RACING = "racing";
    private static final String SPORT = "sport";
    private static final String PUZZLE = "puzzle";
    private static final String PLATFORM = "platform";
    private static final String SHOOTER = "shooter";
    private static final String CARD_AND_BOARD_GAME = "card-and-board-game";
    private static final String TACTICAL = "tactical";
    private static final String MUSIC = "music";
    private static final String ACTION = "action";
    private static final String FANTASY = "fantasy";
    private static final String HORROR = "horror";
    private static final String COMEDY = "comedy";
    private static final String MYSTERY = "mystery";
    private static final String ROMANCE = "romance";
    private static final String SCIENCE_FICTION = "science-fiction";
    private static final String MULTIPLAYER = "multiplayer";
    private static final String CO_OPERATIVE = "co-operative";

    /** Steam etiket adı (küçük harf) → tür (GENRE) kodu. */
    private static final Map<String, String> GENRES = Map.ofEntries(
            Map.entry(ADVENTURE, ADVENTURE), Map.entry("action-adventure", ADVENTURE), Map.entry("rpg", ROLE_PLAYING_RPG),
            Map.entry("action rpg", ROLE_PLAYING_RPG), Map.entry("jrpg", ROLE_PLAYING_RPG), Map.entry(STRATEGY, STRATEGY),
            Map.entry("simulation", SIMULATOR), Map.entry(RACING, RACING), Map.entry("sports", SPORT), Map.entry(PUZZLE, PUZZLE),
            Map.entry("platformer", PLATFORM), Map.entry("2d platformer", PLATFORM), Map.entry("3d platformer", PLATFORM),
            Map.entry("fighting", "fighting"), Map.entry(SHOOTER, SHOOTER), Map.entry("fps", SHOOTER), Map.entry("indie", "indie"),
            Map.entry("visual novel", "visual-novel"), Map.entry("card game", CARD_AND_BOARD_GAME), Map.entry("board game", CARD_AND_BOARD_GAME),
            Map.entry("arcade", "arcade"), Map.entry("hack and slash", "hack-and-slash-beat-em-up"), Map.entry("beat 'em up", "hack-and-slash-beat-em-up"),
            Map.entry("rts", "real-time-strategy-rts"), Map.entry("turn-based strategy", "turn-based-strategy-tbs"), Map.entry(TACTICAL, TACTICAL),
            Map.entry("moba", "moba"), Map.entry("point & click", "point-and-click"), Map.entry("pinball", "pinball"),
            Map.entry("trivia", "quiz-trivia"), Map.entry(MUSIC, MUSIC), Map.entry("rhythm", MUSIC),
            // Faz 0 etiket frekansından (100 bin sınıfsız kayıt) eklenen eş anlamlılar: yakın ve belirsizlik taşımayan eşlemeler.
            Map.entry("third-person shooter", SHOOTER), Map.entry("looter shooter", SHOOTER), Map.entry("shoot 'em up", SHOOTER),
            Map.entry("bullet hell", SHOOTER), Map.entry("hero shooter", SHOOTER), Map.entry("arena shooter", SHOOTER), Map.entry("tactical shooter", SHOOTER),
            Map.entry("life sim", SIMULATOR), Map.entry("farming sim", SIMULATOR), Map.entry("city builder", SIMULATOR), Map.entry("colony sim", SIMULATOR),
            Map.entry("management", SIMULATOR), Map.entry("dating sim", SIMULATOR), Map.entry("space sim", SIMULATOR), Map.entry("automation", SIMULATOR),
            Map.entry("strategy rpg", ROLE_PLAYING_RPG), Map.entry("tactical rpg", ROLE_PLAYING_RPG), Map.entry("crpg", ROLE_PLAYING_RPG),
            Map.entry("party-based rpg", ROLE_PLAYING_RPG), Map.entry("turn-based rpg", ROLE_PLAYING_RPG),
            Map.entry("tower defense", STRATEGY), Map.entry("grand strategy", STRATEGY), Map.entry("turn-based tactics", TACTICAL),
            Map.entry("tabletop", CARD_AND_BOARD_GAME), Map.entry("deckbuilding", CARD_AND_BOARD_GAME), Map.entry("card battler", CARD_AND_BOARD_GAME),
            Map.entry("metroidvania", PLATFORM), Map.entry("precision platformer", PLATFORM), Map.entry("puzzle platformer", PLATFORM),
            Map.entry("hidden object", PUZZLE), Map.entry("match 3", PUZZLE), Map.entry("logic", PUZZLE),
            Map.entry("driving", RACING), Map.entry("football", SPORT), Map.entry("basketball", SPORT), Map.entry("golf", SPORT),
            Map.entry("walking simulator", ADVENTURE));
    /** Steam etiket adı (küçük harf) → etiket/tema (TAG) kodu. */
    private static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry(ACTION, ACTION), Map.entry(FANTASY, FANTASY), Map.entry(HORROR, HORROR), Map.entry("psychological horror", HORROR),
            Map.entry("survival horror", HORROR), Map.entry("open world", "open-world"), Map.entry("survival", "survival"),
            Map.entry("sandbox", "sandbox"), Map.entry("stealth", "stealth"), Map.entry(COMEDY, COMEDY), Map.entry("funny", COMEDY),
            Map.entry("historical", "historical"), Map.entry(MYSTERY, MYSTERY), Map.entry(ROMANCE, ROMANCE),
            Map.entry("sci-fi", SCIENCE_FICTION), Map.entry("thriller", "thriller"), Map.entry("4x", "4x-explore-expand-exploit-and-exterminate"),
            Map.entry("business", "business"), Map.entry("education", "educational"), Map.entry("party", "party"), Map.entry("warfare", "warfare"),
            Map.entry("drama", "drama"), Map.entry("sexual content", "erotic"), Map.entry("kids", "kids"),
            Map.entry("dark fantasy", FANTASY), Map.entry("cyberpunk", SCIENCE_FICTION), Map.entry("space", SCIENCE_FICTION),
            Map.entry("futuristic", SCIENCE_FICTION), Map.entry("detective", MYSTERY), Map.entry("zombies", HORROR),
            Map.entry("dating sim", ROMANCE), Map.entry("action roguelike", ACTION), Map.entry("action rpg", ACTION), Map.entry("hack and slash", ACTION),
            Map.entry("action-adventure", ACTION), Map.entry(SHOOTER, ACTION), Map.entry("fps", ACTION), Map.entry("beat 'em up", ACTION));
    /** Steam oyuncu kategorisi kimliği → kategori (CATEGORY) kodu. */
    private static final Map<Integer, String> CATEGORIES = Map.ofEntries(
            Map.entry(2, "single-player"), Map.entry(1, MULTIPLAYER), Map.entry(49, MULTIPLAYER), Map.entry(36, MULTIPLAYER),
            Map.entry(9, CO_OPERATIVE), Map.entry(38, CO_OPERATIVE), Map.entry(39, CO_OPERATIVE), Map.entry(48, CO_OPERATIVE),
            Map.entry(20, "massively-multiplayer-online-mmo"), Map.entry(24, "split-screen"), Map.entry(37, "split-screen"));
    /** Sağlayıcıdan bağımsız platform adı → platform (PLATFORM) kodu. */
    private static final Map<String, String> PLATFORMS = Map.of("windows", "win", "mac", "mac", "linux", "linux");

    /** Bir oyun için eşlenen terim kodları (tür başına). */
    public record Terms(Map<TermKind, Set<String>> byKind) {
        public Terms {
            Map<TermKind, Set<String>> copy = new EnumMap<>(TermKind.class);
            byKind.forEach((kind, codes) -> copy.put(kind, Set.copyOf(codes)));
            byKind = Map.copyOf(copy);
        }

        public Set<String> of(TermKind kind) { return byKind.getOrDefault(kind, Set.of()); }

        public boolean isEmpty() { return byKind.values().stream().allMatch(Set::isEmpty); }
    }

    /** Steam türü bilgisini (etiket kimlikleri, kategoriler, platformlar) terim kodlarına çevirir; {@code tagNames} etiket kimliği → İngilizce ad. */
    public Terms map(GameProvider.StoreType store, Map<Integer, String> tagNames) {
        Set<String> genres = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        for (Integer tagId : store.tagIds()) {
            String name = tagNames.get(tagId);
            if (name == null) continue;
            String key = name.strip().toLowerCase(Locale.ROOT);
            if (GENRES.containsKey(key)) genres.add(GENRES.get(key));
            if (TAGS.containsKey(key)) tags.add(TAGS.get(key));
        }
        Set<String> categories = new LinkedHashSet<>();
        store.categoryIds().stream().sorted().forEach(id -> {
            if (CATEGORIES.containsKey(id)) categories.add(CATEGORIES.get(id));
        });
        Set<String> platforms = new LinkedHashSet<>();
        store.platforms().stream().sorted().forEach(name -> {
            if (PLATFORMS.containsKey(name)) platforms.add(PLATFORMS.get(name));
        });
        return new Terms(Map.of(TermKind.GENRE, genres, TermKind.TAG, tags, TermKind.CATEGORY, categories, TermKind.PLATFORM, platforms));
    }
}
