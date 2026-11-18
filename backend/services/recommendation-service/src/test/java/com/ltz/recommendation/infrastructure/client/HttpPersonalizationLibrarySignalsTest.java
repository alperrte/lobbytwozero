package com.ltz.recommendation.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PersonalizationSource.Filters;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** REC-LIB-04/06: Library sinyal hataları asla boş koleksiyon (tam öneri) olarak yorumlanmaz. */
class HttpPersonalizationLibrarySignalsTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID ACCOUNT = new UUID(0, 7);
    private static final Actor ACTOR = new Actor(ACCOUNT, "Bearer user");

    private static JsonNode json(String value) { return JSON.readTree(value); }

    private static HttpPersonalizationSource source(RecommendationHttpClient http, RecommendationServiceTokenClient tokens) {
        return new HttpPersonalizationSource(http, tokens, "http://user.local", "http://game.local", "http://library.local");
    }

    private static RecommendationHttpClient http(Object library) {
        RecommendationHttpClient http = Mockito.mock(RecommendationHttpClient.class);
        Mockito.when(http.get(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath().equals("/api/v1/user/me")) {
                return json("{\"userId\":\"" + ACCOUNT + "\",\"favoriteGenres\":[\"rpg\"]}");
            }
            if (uri.getPath().contains("recommendation-signals")) {
                if (library instanceof RuntimeException failure) throw failure;
                return (JsonNode) library;
            }
            return json("{\"items\":[],\"page\":0,\"size\":50,\"includeAdultApplied\":false}");
        });
        return http;
    }

    private static RecommendationServiceTokenClient tokens() {
        RecommendationServiceTokenClient tokens = Mockito.mock(RecommendationServiceTokenClient.class);
        Mockito.when(tokens.libraryToken(ACCOUNT)).thenReturn("Bearer svc");
        return tokens;
    }

    @Test
    void forbiddenLibraryIsDeniedNeverAnEmptyCollection() {
        var http = http(new RecommendationRequestException(403, "SOURCE_ACCESS_DENIED"));
        assertThatThrownBy(() -> source(http, tokens()).candidates(ACTOR, new Filters(null, null, 20)))
                .isInstanceOfSatisfying(RecommendationRequestException.class, e -> {
                    assertThat(e.status()).isEqualTo(403);
                    assertThat(e.code()).isEqualTo("SOURCE_ACCESS_DENIED");
                });
        // Game keşfi hiç çağrılmaz: Library kaynaklı kişiselleştirme sessizce atlanmaz.
        Mockito.verify(http, Mockito.never()).get(Mockito.argThat(uri -> uri.getPath().equals("/api/v1/game/games")), Mockito.any());
    }

    @Test
    void unavailableLibraryStopsTheRunAndIsNotRetried() {
        var http = http(new RecommendationRequestException(503, "SOURCE_UNAVAILABLE"));
        assertThatThrownBy(() -> source(http, tokens()).candidates(ACTOR, new Filters(null, null, 20)))
                .isInstanceOfSatisfying(RecommendationRequestException.class, e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
        Mockito.verify(http, Mockito.times(1)).get(Mockito.argThat(uri -> uri.getPath().contains("recommendation-signals")), Mockito.any());
    }

    @Test
    void inconsistentOrOverLimitPagesAreUnavailable() {
        for (String body : new String[] {
                "{\"items\":[],\"page\":0,\"size\":50,\"totalElements\":501}",
                "{\"items\":[],\"page\":1,\"size\":50,\"totalElements\":0}",
                "{\"items\":[],\"page\":0,\"size\":20,\"totalElements\":0}",
                "{\"items\":[{\"gameId\":\"" + new UUID(0, 1) + "\",\"favorite\":true,\"completed\":false}],\"page\":0,\"size\":50,\"totalElements\":0}",
                "{\"items\":[{\"gameId\":\"" + new UUID(0, 1) + "\",\"favorite\":\"yes\",\"completed\":false}],\"page\":0,\"size\":50,\"totalElements\":1}"}) {
            var http = http(json(body));
            assertThatThrownBy(() -> source(http, tokens()).candidates(ACTOR, new Filters(null, null, 20)))
                    .as(body).isInstanceOfSatisfying(RecommendationRequestException.class,
                            e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
        }
    }

    @Test
    void genuinelyEmptyLibraryOnHttp200IsAvailableAndContinues() {
        var http = http(json("{\"items\":[],\"page\":0,\"size\":50,\"totalElements\":0}"));
        assertThat(source(http, tokens()).candidates(ACTOR, new Filters(null, null, 20))).isEmpty();
        Mockito.verify(http, Mockito.atLeastOnce()).get(Mockito.argThat(uri -> uri.getPath().equals("/api/v1/game/games")), Mockito.any());
    }
}
