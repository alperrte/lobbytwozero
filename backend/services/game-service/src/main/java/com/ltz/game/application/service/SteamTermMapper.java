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
    /** Steam etiket adı (küçük harf) → tür (GENRE) kodu. */
    private static final Map<String, String> GENRES = Map.ofEntries(
            Map.entry("adventure", "adventure"), Map.entry("action-adventure", "adventure"), Map.entry("rpg", "role-playing-rpg"),
            Map.entry("action rpg", "role-playing-rpg"), Map.entry("jrpg", "role-playing-rpg"), Map.entry("strategy", "strategy"),
            Map.entry("simulation", "simulator"), Map.entry("racing", "racing"), Map.entry("sports", "sport"), Map.entry("puzzle", "puzzle"),
            Map.entry("platformer", "platform"), Map.entry("2d platformer", "platform"), Map.entry("3d platformer", "platform"),
            Map.entry("fighting", "fighting"), Map.entry("shooter", "shooter"), Map.entry("fps", "shooter"), Map.entry("indie", "indie"),
            Map.entry("visual novel", "visual-novel"), Map.entry("card game", "card-and-board-game"), Map.entry("board game", "card-and-board-game"),
            Map.entry("arcade", "arcade"), Map.entry("hack and slash", "hack-and-slash-beat-em-up"), Map.entry("beat 'em up", "hack-and-slash-beat-em-up"),
            Map.entry("rts", "real-time-strategy-rts"), Map.entry("turn-based strategy", "turn-based-strategy-tbs"), Map.entry("tactical", "tactical"),
            Map.entry("moba", "moba"), Map.entry("point & click", "point-and-click"), Map.entry("pinball", "pinball"),
            Map.entry("trivia", "quiz-trivia"), Map.entry("music", "music"), Map.entry("rhythm", "music"),
            // Faz 0 etiket frekansından (100 bin sınıfsız kayıt) eklenen eş anlamlılar: yakın ve belirsizlik taşımayan eşlemeler.
            Map.entry("third-person shooter", "shooter"), Map.entry("looter shooter", "shooter"), Map.entry("shoot 'em up", "shooter"),
            Map.entry("bullet hell", "shooter"), Map.entry("hero shooter", "shooter"), Map.entry("arena shooter", "shooter"), Map.entry("tactical shooter", "shooter"),
            Map.entry("life sim", "simulator"), Map.entry("farming sim", "simulator"), Map.entry("city builder", "simulator"), Map.entry("colony sim", "simulator"),
            Map.entry("management", "simulator"), Map.entry("dating sim", "simulator"), Map.entry("space sim", "simulator"), Map.entry("automation", "simulator"),
            Map.entry("strategy rpg", "role-playing-rpg"), Map.entry("tactical rpg", "role-playing-rpg"), Map.entry("crpg", "role-playing-rpg"),
            Map.entry("party-based rpg", "role-playing-rpg"), Map.entry("turn-based rpg", "role-playing-rpg"),
            Map.entry("tower defense", "strategy"), Map.entry("grand strategy", "strategy"), Map.entry("turn-based tactics", "tactical"),
            Map.entry("tabletop", "card-and-board-game"), Map.entry("deckbuilding", "card-and-board-game"), Map.entry("card battler", "card-and-board-game"),
            Map.entry("metroidvania", "platform"), Map.entry("precision platformer", "platform"), Map.entry("puzzle platformer", "platform"),
            Map.entry("hidden object", "puzzle"), Map.entry("match 3", "puzzle"), Map.entry("logic", "puzzle"),
            Map.entry("driving", "racing"), Map.entry("football", "sport"), Map.entry("basketball", "sport"), Map.entry("golf", "sport"),
            Map.entry("walking simulator", "adventure"));
    /** Steam etiket adı (küçük harf) → etiket/tema (TAG) kodu. */
    private static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("action", "action"), Map.entry("fantasy", "fantasy"), Map.entry("horror", "horror"), Map.entry("psychological horror", "horror"),
            Map.entry("survival horror", "horror"), Map.entry("open world", "open-world"), Map.entry("survival", "survival"),
            Map.entry("sandbox", "sandbox"), Map.entry("stealth", "stealth"), Map.entry("comedy", "comedy"), Map.entry("funny", "comedy"),
            Map.entry("historical", "historical"), Map.entry("mystery", "mystery"), Map.entry("romance", "romance"),
            Map.entry("sci-fi", "science-fiction"), Map.entry("thriller", "thriller"), Map.entry("4x", "4x-explore-expand-exploit-and-exterminate"),
            Map.entry("business", "business"), Map.entry("education", "educational"), Map.entry("party", "party"), Map.entry("warfare", "warfare"),
            Map.entry("drama", "drama"), Map.entry("sexual content", "erotic"), Map.entry("kids", "kids"),
            Map.entry("dark fantasy", "fantasy"), Map.entry("cyberpunk", "science-fiction"), Map.entry("space", "science-fiction"),
            Map.entry("futuristic", "science-fiction"), Map.entry("detective", "mystery"), Map.entry("zombies", "horror"),
            Map.entry("dating sim", "romance"), Map.entry("action roguelike", "action"), Map.entry("action rpg", "action"), Map.entry("hack and slash", "action"),
            Map.entry("action-adventure", "action"), Map.entry("shooter", "action"), Map.entry("fps", "action"), Map.entry("beat 'em up", "action"));
    /** Steam oyuncu kategorisi kimliği → kategori (CATEGORY) kodu. */
    private static final Map<Integer, String> CATEGORIES = Map.ofEntries(
            Map.entry(2, "single-player"), Map.entry(1, "multiplayer"), Map.entry(49, "multiplayer"), Map.entry(36, "multiplayer"),
            Map.entry(9, "co-operative"), Map.entry(38, "co-operative"), Map.entry(39, "co-operative"), Map.entry(48, "co-operative"),
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
