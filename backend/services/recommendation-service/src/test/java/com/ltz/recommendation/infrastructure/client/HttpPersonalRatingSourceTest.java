package com.ltz.recommendation.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HttpPersonalRatingSourceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static JsonNode json(String value) { return JSON.readTree(value); }

    private static String item(UUID id, String rating) { return "{\"gameId\":\"" + id + "\",\"rating\":" + rating + ",\"updatedAt\":\"2026-11-18T10:00:00Z\"}"; }

    @Test
    void readsPageAndReturnsNextCursor() {
        Map<UUID, Integer> into = new LinkedHashMap<>();
        String next = HttpPersonalRatingSource.readPage(json("{\"items\":[" + item(A, "9") + "," + item(B, "4")
                + "],\"nextCursor\":\"abc\",\"truncated\":false}"), into);
        assertThat(next).isEqualTo("abc");
        assertThat(into).containsEntry(A, 9).containsEntry(B, 4);
        assertThat(HttpPersonalRatingSource.readPage(json("{\"items\":[],\"nextCursor\":null,\"truncated\":true}"), into)).isNull();
    }

    @Test
    void malformedPagesAreSourceUnavailable() {
        List<String> bad = List.of(
                "{}",
                "{\"items\":[" + item(A, "0") + "],\"nextCursor\":null,\"truncated\":false}",
                "{\"items\":[" + item(A, "11") + "],\"nextCursor\":null,\"truncated\":false}",
                "{\"items\":[" + item(A, "7.5") + "],\"nextCursor\":null,\"truncated\":false}",
                "{\"items\":[" + item(A, "7") + "," + item(A, "8") + "],\"nextCursor\":null,\"truncated\":false}",
                "{\"items\":[{\"gameId\":\"nope\",\"rating\":7}],\"nextCursor\":null,\"truncated\":false}",
                "{\"items\":[" + item(A, "7") + "],\"nextCursor\":null}",
                "{\"items\":[" + item(A, "7") + "],\"nextCursor\":5,\"truncated\":false}",
                "{\"items\":[],\"nextCursor\":\"abc\",\"truncated\":false}",
                "{\"items\":[" + item(A, "7") + "],\"nextCursor\":\"" + "x".repeat(201) + "\",\"truncated\":false}");
        for (String value : bad) {
            assertThatThrownBy(() -> HttpPersonalRatingSource.readPage(json(value), new HashMap<>()))
                    .as(value).isInstanceOfSatisfying(RecommendationRequestException.class,
                            e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
        }
    }

    @Test
    void genreAffinityScalesAverageAndDropsLowGenres() {
        Map<String, long[]> sums = new HashMap<>();
        sums.put("rpg", new long[] {18, 2});      // ortalama 9 -> 8000
        sums.put("racing", new long[] {10, 2});   // ortalama 5 -> 0 (elenir)
        sums.put("puzzle", new long[] {3, 1});    // ortalama 3 -> 0 (elenir)
        sums.put("action", new long[] {10, 1});   // ortalama 10 -> 10000
        assertThat(HttpPersonalRatingSource.affinity(sums)).containsOnly(Map.entry("rpg", 8000), Map.entry("action", 10000));
    }

    @Test
    void gameGenresFailClosedOnInconsistentAccountingAndHideAdultOrInvisible() {
        UUID c = new UUID(0, 3);
        String ok = "{\"gameId\":\"" + A + "\",\"adult\":false,\"visible\":true,\"genres\":[{\"code\":\"RPG\"}]}";
        String adult = "{\"gameId\":\"" + B + "\",\"adult\":true,\"visible\":true,\"genres\":[{\"code\":\"rpg\"}]}";
        var genres = HttpGameMetadataSource.readGenres(json("{\"items\":[" + ok + "," + adult + "],\"unavailable\":[\"" + c + "\"]}"),
                List.of(A, B, c));
        assertThat(genres).containsOnlyKeys(A);
        assertThat(genres.get(A)).containsExactly("RPG");
        assertThatThrownBy(() -> HttpGameMetadataSource.readGenres(json("{\"items\":[" + ok + "],\"unavailable\":[]}"), List.of(A, B)))
                .isInstanceOf(RecommendationRequestException.class);
        assertThatThrownBy(() -> HttpGameMetadataSource.readGenres(json("{\"items\":[" + ok + "],\"unavailable\":[\"" + A + "\"]}"),
                List.of(A, B))).isInstanceOf(RecommendationRequestException.class);
    }

    @Test
    void noRatingsMeansNoGameCallAndNoAffinity() {
        GameMetadataSource games = mock(GameMetadataSource.class);
        RecommendationHttpClient http = mock(RecommendationHttpClient.class);
        when(http.get(any(), any())).thenReturn(json("{\"items\":[],\"nextCursor\":null,\"truncated\":false}"));
        var source = new HttpPersonalRatingSource(http, games, "http://review.local");
        assertThat(source.genreAffinity(new Actor(A, "Bearer x"))).isEmpty();
        verify(games, never()).genres(any());
    }

    @Test
    void ratingsAreJoinedWithGenresAcrossPages() {
        GameMetadataSource games = mock(GameMetadataSource.class);
        RecommendationHttpClient http = mock(RecommendationHttpClient.class);
        when(http.get(any(), any()))
                .thenReturn(json("{\"items\":[" + item(A, "10") + "],\"nextCursor\":\"c1\",\"truncated\":false}"))
                .thenReturn(json("{\"items\":[" + item(B, "8") + "],\"nextCursor\":null,\"truncated\":false}"));
        when(games.genres(any())).thenReturn(Map.of(A, List.of("RPG"), B, List.of("rpg", "Action")));
        var source = new HttpPersonalRatingSource(http, games, "http://review.local");
        // rpg ortalama 9 -> 8000; action ortalama 8 -> 6000
        assertThat(source.genreAffinity(new Actor(A, "Bearer x"))).containsOnly(Map.entry("rpg", 8000), Map.entry("action", 6000));
    }
}
