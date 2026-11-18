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
    private static final UUID C = new UUID(0, 3);

    private static JsonNode json(String value) { return JSON.readTree(value); }

    private static String item(UUID id, String adult, String visible, String requirements) {
        return "{\"gameId\":\"" + id + "\",\"gameType\":\"GAME\",\"adult\":" + adult + ",\"visible\":" + visible
                + ",\"genres\":[],\"platforms\":[],\"systemRequirements\":" + requirements + "}";
    }

    private static String field(String value, String state) { return "{\"value\":" + value + ",\"state\":\"" + state + "\"}"; }

    @Test
    void readsOnlyTheRequestedPlatformKeepsLevelsSeparateAndUsesAvailableFieldsOnly() {
        String reqs = "[{\"level\":\"MINIMUM\",\"platform\":\"windows\",\"cpu\":" + field("\"c\"", "AVAILABLE")
                + ",\"gpu\":" + field("null", "UNKNOWN") + ",\"ramMb\":" + field("8192", "AVAILABLE")
                + ",\"os\":" + field("\"w\"", "UNCOMPARABLE") + "},"
                + "{\"level\":\"RECOMMENDED\",\"platform\":\"windows\",\"gpu\":" + field("\"g\"", "AVAILABLE") + "},"
                + "{\"level\":\"MINIMUM\",\"platform\":\"linux\",\"cpu\":" + field("\"x\"", "AVAILABLE") + "}]";
        var batch = HttpGameMetadataSource.read(json("{\"items\":[" + item(A, "false", "true", reqs)
                + "],\"unavailable\":[\"" + B + "\"]}"), List.of(A, B), "windows");
        assertThat(batch.unavailable()).containsExactly(B);
        var items = batch.byGame().get(A);
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().level()).isEqualTo("MINIMUM");
        assertThat(items.getFirst().cpu()).isEqualTo("c");
        assertThat(items.getFirst().gpu()).isNull();
        assertThat(items.getFirst().os()).isNull();
        assertThat(items.getFirst().ramMb()).isEqualTo(8192);
        assertThat(items.get(1).ramMb()).isNull();
    }

    @Test
    void adultOrInvisibleGamesAreTreatedAsUnavailable() {
        var batch = HttpGameMetadataSource.read(json("{\"items\":[" + item(A, "true", "true", "[]") + ","
                + item(B, "false", "false", "[]") + "," + item(C, "false", "true", "[]") + "],\"unavailable\":[]}"),
                List.of(A, B, C), "windows");
        assertThat(batch.unavailable()).containsExactlyInAnyOrder(A, B);
        assertThat(batch.byGame()).containsOnlyKeys(C);
    }

    @Test
    void malformedOrInconsistentRepliesAreSourceUnavailable() {
        String ok = item(A, "false", "true", "[]");
        List<String> bad = List.of(
                "{}",
                "{\"items\":[],\"unavailable\":[]}",
                "{\"items\":[" + ok + "],\"unavailable\":[\"" + A + "\"]}",
                "{\"items\":[" + ok + "," + ok + "],\"unavailable\":[]}",
                "{\"items\":[" + item(UUID.randomUUID(), "false", "true", "[]") + "],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[" + item(A, "false", "true", "{}") + "],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[" + item(A, "\"x\"", "true", "[]") + "],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[{\"gameId\":\"not-a-uuid\",\"adult\":false,\"visible\":true,\"systemRequirements\":[]}],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[" + item(A, "false", "true", "[{\"level\":\"ULTRA\",\"platform\":\"windows\"}]") + "],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[" + item(A, "false", "true", "[{\"level\":\"MINIMUM\",\"platform\":\"windows\"},"
                        + "{\"level\":\"MINIMUM\",\"platform\":\"windows\"}]") + "],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[" + item(A, "false", "true", "[{\"level\":\"MINIMUM\",\"platform\":\"windows\",\"ramMb\":"
                        + field("-1", "AVAILABLE") + "}]") + "],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[" + item(A, "false", "true", "[{\"level\":\"MINIMUM\",\"platform\":\"windows\",\"cpu\":"
                        + field("\"c\"", "BOGUS") + "}]") + "],\"unavailable\":[\"" + B + "\"]}",
                "{\"items\":[" + item(A, "false", "true", "[{\"level\":\"MINIMUM\",\"platform\":\"windows\",\"cpu\":5}]")
                        + "],\"unavailable\":[\"" + B + "\"]}");
        for (String value : bad) {
            assertThatThrownBy(() -> HttpGameMetadataSource.read(json(value), List.of(A, B), "windows"))
                    .as(value).isInstanceOfSatisfying(RecommendationRequestException.class,
                            e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
        }
    }
}
