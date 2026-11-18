package com.ltz.recommendation.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HttpGameMetadataSourceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static JsonNode json(String value) { return JSON.readTree(value); }

    @Test
    void readsOnlyTheRequestedPlatformAndKeepsLevelsSeparate() {
        var batch = HttpGameMetadataSource.read(json("{\"items\":[{\"gameId\":\"" + A + "\",\"requirements\":["
                + "{\"level\":\"MINIMUM\",\"platform\":\"windows\",\"cpu\":\"c\",\"ramMb\":8192},"
                + "{\"level\":\"RECOMMENDED\",\"platform\":\"windows\",\"gpu\":\"g\"},"
                + "{\"level\":\"MINIMUM\",\"platform\":\"linux\",\"cpu\":\"x\"}]}],\"unavailable\":[\"" + B + "\"]}"),
                List.of(A, B), "windows");
        assertThat(batch.unavailable()).containsExactly(B);
        var items = batch.byGame().get(A);
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().level()).isEqualTo("MINIMUM");
        assertThat(items.getFirst().ramMb()).isEqualTo(8192);
        assertThat(items.get(1).ramMb()).isNull();
    }

    @Test
    void malformedOrInconsistentRepliesAreSourceUnavailable() {
        List<String> bad = List.of(
                "{}",
                "{\"items\":[],\"unavailable\":[]}",
                "{\"items\":[{\"gameId\":\"" + A + "\",\"requirements\":[]}],\"unavailable\":[\"" + A + "\"]}",
                "{\"items\":[{\"gameId\":\"" + A + "\",\"requirements\":[]},{\"gameId\":\"" + A + "\",\"requirements\":[]}],\"unavailable\":[]}",
                "{\"items\":[{\"gameId\":\"" + UUID.randomUUID() + "\",\"requirements\":[]}],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[{\"gameId\":\"not-a-uuid\",\"requirements\":[]}],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[{\"gameId\":\"" + A + "\",\"requirements\":{}}],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[{\"gameId\":\"" + A + "\",\"requirements\":[{\"level\":\"ULTRA\",\"platform\":\"windows\"}]}],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[{\"gameId\":\"" + A + "\",\"requirements\":[{\"level\":\"MINIMUM\",\"platform\":\"windows\"},"
                        + "{\"level\":\"MINIMUM\",\"platform\":\"windows\"}]}],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[{\"gameId\":\"" + A + "\",\"requirements\":[{\"level\":\"MINIMUM\",\"platform\":\"windows\",\"ramMb\":-1}]}],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[{\"gameId\":\"" + A + "\",\"requirements\":[{\"level\":\"MINIMUM\",\"platform\":\"windows\",\"cpu\":5}]}],\"unavailable\":[\"" + B + "\"]}");
        for (String value : bad) {
            assertThatThrownBy(() -> HttpGameMetadataSource.read(json(value), List.of(A, B), "windows"))
                    .as(value).isInstanceOfSatisfying(RecommendationRequestException.class,
                            e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
        }
    }
}
