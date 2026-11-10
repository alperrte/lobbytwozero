package com.ltz.game.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ltz.game.application.port.GameProvider.StoreType;
import com.ltz.game.domain.enums.TermKind;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Steam etiket/kategori/platform → mevcut terim kodları: tablolar sabittir, bilinmeyen değer yok sayılır, terim uydurulmaz. */
class SteamTermMapperTest {
    private static final Map<Integer, String> NAMES = Map.ofEntries(Map.entry(21, "Adventure"), Map.entry(122, "RPG"), Map.entry(492, "Indie"),
            Map.entry(599, "Simulation"), Map.entry(19, "Action"), Map.entry(1667, "Horror"), Map.entry(1721, "Psychological Horror"),
            Map.entry(3942, "Sci-fi"), Map.entry(597, "Casual"), Map.entry(4726, "Cute"), Map.entry(1663, "FPS"), Map.entry(5379, "2D Platformer"));
    private final SteamTermMapper mapper = new SteamTermMapper();

    private static StoreType store(Set<Integer> tags, Set<Integer> categories, Set<String> platforms) {
        return new StoreType(0, true, null, null, tags, categories, platforms);
    }

    @Test void tagsMapToGenresAndThemesAndUnmappedOnesAreIgnored() {
        var terms = mapper.map(store(Set.of(21, 122, 492, 19, 1667, 1721, 3942, 597, 4726, 99999), Set.of(), Set.of()), NAMES);
        assertEquals(Set.of("adventure", "role-playing-rpg", "indie"), terms.of(TermKind.GENRE));
        assertEquals(Set.of("action", "horror", "science-fiction"), terms.of(TermKind.TAG), "iki korku etiketi tek tema olur; Casual ve Cute eşlenmez");
    }

    @Test void shooterAndPlatformerVariantsShareOneGenre() {
        var terms = mapper.map(store(Set.of(1663, 5379), Set.of(), Set.of()), NAMES);
        assertEquals(Set.of("shooter", "platform"), terms.of(TermKind.GENRE));
    }

    @Test void extendedSynonymsFromTheFrequencyAnalysisMapToTheirGenreAndThemeCodes() {
        var names = Map.ofEntries(Map.entry(1, "Strategy RPG"), Map.entry(2, "Tabletop"), Map.entry(3, "Metroidvania"), Map.entry(4, "Cyberpunk"), Map.entry(5, "Dating Sim"),
                Map.entry(6, "Looter Shooter"), Map.entry(7, "City Builder"), Map.entry(8, "Rhythm"), Map.entry(9, "Zombies"));
        var terms = mapper.map(store(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9), Set.of(), Set.of()), names);
        assertEquals(Set.of("role-playing-rpg", "card-and-board-game", "platform", "simulator", "shooter", "music"), terms.of(TermKind.GENRE));
        assertEquals(Set.of("science-fiction", "romance", "horror"), terms.of(TermKind.TAG));
    }

    @Test void playerCategoriesAndPlatformsMapToExistingCodes() {
        var terms = mapper.map(store(Set.of(), Set.of(2, 1, 38, 20, 24, 12345), Set.of("windows", "mac", "linux", "beos")), NAMES);
        assertEquals(Set.of("single-player", "multiplayer", "co-operative", "massively-multiplayer-online-mmo", "split-screen"), terms.of(TermKind.CATEGORY));
        assertEquals(Set.of("win", "mac", "linux"), terms.of(TermKind.PLATFORM));
    }

    @Test void unknownTagNamesAndEmptyInputProduceNothing() {
        assertTrue(mapper.map(store(Set.of(1, 2), Set.of(), Set.of()), Map.of()).isEmpty(), "ad tablosu yoksa eşleme yapılmaz");
        assertTrue(mapper.map(store(Set.of(), Set.of(), Set.of()), NAMES).isEmpty());
    }
}
