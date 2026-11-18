package com.ltz.recommendation.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.LibraryTasteSource.Affinity;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** REC-02/05: favori ve tamamlanan oyunların tür payları; kaynak hataları fail-closed. */
class HttpLibraryTasteSourceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID ACCOUNT = new UUID(0, 7);
    private static final Actor ACTOR = new Actor(ACCOUNT, "Bearer user");
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static JsonNode json(String value) { return JSON.readTree(value); }

    private static String item(UUID id, boolean favorite, boolean completed) {
        return "{\"gameId\":\"" + id + "\",\"favorite\":" + favorite + ",\"completed\":" + completed + "}";
    }

    private static HttpLibraryTasteSource source(Object library, GameMetadataSource games) {
        RecommendationHttpClient http = mock(RecommendationHttpClient.class);
        when(http.get(any(URI.class), any())).thenAnswer(invocation -> {
            if (library instanceof RuntimeException failure) throw failure;
            return (JsonNode) library;
        });
        RecommendationServiceTokenClient tokens = mock(RecommendationServiceTokenClient.class);
        when(tokens.libraryToken(ACCOUNT)).thenReturn("Bearer svc");
        return new HttpLibraryTasteSource(http, tokens, games, "http://library.local");
    }

    @Test
    void sharesAreComputedOverVisibleGamesPerSetWithLowercasedGenres() {
        GameMetadataSource games = mock(GameMetadataSource.class);
        when(games.genres(anyList())).thenReturn(Map.of(A, List.of("RPG", "Action"), B, List.of("rpg")));
        // C Game tarafında görünmez (yetişkin/gizli): paydaya girmez.
        var library = json("{\"items\":[" + item(A, true, false) + "," + item(B, true, true) + "," + item(C, false, true)
                + "],\"page\":0,\"size\":50,\"totalElements\":3}");
        Affinity affinity = source(library, games).affinity(ACTOR);
        assertThat(affinity.favoriteGenres()).containsEntry("rpg", 10000).containsEntry("action", 5000);
        assertThat(affinity.completedGenres()).containsOnly(Map.entry("rpg", 10000));
    }

    @Test
    void emptyLibraryDoesNotCallGame() {
        GameMetadataSource games = mock(GameMetadataSource.class);
        var library = json("{\"items\":[" + item(A, false, false) + "],\"page\":0,\"size\":50,\"totalElements\":1}");
        assertThat(source(library, games).affinity(ACTOR)).isEqualTo(Affinity.NONE);
        verifyNoInteractions(games);
    }

    @Test
    void duplicateGameInLibraryAndSourceFailuresAreUnavailableNeverEmptyTaste() {
        GameMetadataSource games = mock(GameMetadataSource.class);
        var duplicate = json("{\"items\":[" + item(A, true, false) + "," + item(A, false, true)
                + "],\"page\":0,\"size\":50,\"totalElements\":2}");
        assertThatThrownBy(() -> source(duplicate, games).affinity(ACTOR)).isInstanceOfSatisfying(
                RecommendationRequestException.class, e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
        assertThatThrownBy(() -> source(new RecommendationRequestException(403, "SOURCE_ACCESS_DENIED"), games).affinity(ACTOR))
                .isInstanceOfSatisfying(RecommendationRequestException.class, e -> assertThat(e.status()).isEqualTo(403));
        verifyNoInteractions(games);
    }

    @Test
    void gameFailureStopsTheRun() {
        GameMetadataSource games = mock(GameMetadataSource.class);
        when(games.genres(anyList())).thenThrow(new RecommendationRequestException(503, "SOURCE_UNAVAILABLE"));
        var library = json("{\"items\":[" + item(A, true, false) + "],\"page\":0,\"size\":50,\"totalElements\":1}");
        assertThatThrownBy(() -> source(library, games).affinity(ACTOR)).isInstanceOfSatisfying(
                RecommendationRequestException.class, e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
    }

    @Test
    void unresolvedGamesYieldEmptyShares() {
        assertThat(HttpLibraryTasteSource.share(java.util.Set.of(A), Map.of())).isEmpty();
    }
}
