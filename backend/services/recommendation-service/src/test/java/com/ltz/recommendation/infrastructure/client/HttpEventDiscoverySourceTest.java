package com.ltz.recommendation.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** REC-15: Event yanıtı sıkı doğrulanır; hata veya sözleşme ihlali boş liste sayılmaz. */
class HttpEventDiscoverySourceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Actor ACTOR = new Actor(new UUID(0, 7), "Bearer user");

    private static String item(int id, String extra) {
        return "{\"id\":\"" + new UUID(2, id) + "\",\"organizerId\":null,\"type\":\"TOURNAMENT\",\"gameId\":null,"
                + "\"platforms\":[\"PC\"],\"title\":\"T" + id + "\",\"startsAt\":\"2026-12-01T10:00:00Z\","
                + "\"endsAt\":\"2026-12-01T12:00:00Z\",\"status\":\"PLANNED\",\"capacity\":10,\"participantCount\":3,"
                + "\"remainingCapacity\":7,\"viewerParticipation\":null,\"joinable\":true" + extra + "}";
    }

    private static String page(int page, boolean hasNext, String... items) {
        return "{\"items\":[" + String.join(",", items) + "],\"page\":" + page + ",\"size\":50,\"totalItems\":1,"
                + "\"totalPages\":1,\"hasNext\":" + hasNext + "}";
    }

    private static HttpEventDiscoverySource source(RecommendationHttpClient http) {
        return new HttpEventDiscoverySource(http, "http://event.local");
    }

    private static RecommendationHttpClient replies(String... bodies) {
        RecommendationHttpClient http = mock(RecommendationHttpClient.class);
        var stub = when(http.get(any(), eq("Bearer user")));
        for (String body : bodies) stub = stub.thenReturn((JsonNode) JSON.readTree(body));
        return http;
    }

    @Test
    void readsPagesWithTheUserTokenUntilNoNextPage() {
        var http = replies(page(0, true, item(1, "")), page(1, false, item(2, "")));
        var scan = source(http).upcoming(ACTOR);
        assertThat(scan.events()).hasSize(2);
        assertThat(scan.truncated()).isFalse();
        assertThat(scan.events().getFirst().platforms()).containsExactly("PC");
        verify(http).get(argThat((URI uri) -> uri.toString().equals("http://event.local/api/v1/event/events?page=1&size=50")),
                eq("Bearer user"));
    }

    @Test
    void readsParticipationFieldsIncludingUnlimitedCapacityAndViewerParticipation() {
        var http = replies(page(0, false,
                item(1, "").replace("\"capacity\":10", "\"capacity\":null").replace("\"joinable\":true", "\"joinable\":false")
                        .replace("\"viewerParticipation\":null", "\"viewerParticipation\":\"ACTIVE\"")));
        var event = source(http).upcoming(ACTOR).events().getFirst();
        assertThat(event.capacity()).isNull();
        assertThat(event.participantCount()).isEqualTo(3);
        assertThat(event.viewerParticipation()).isEqualTo("ACTIVE");
        assertThat(event.joinable()).isFalse();
    }

    @Test
    void stopsAtThePageLimitAndReportsTruncation() {
        var http = replies(page(0, true, item(1, "")), page(1, true, item(2, "")), page(2, true, item(3, "")), page(3, true, item(4, "")));
        var scan = source(http).upcoming(ACTOR);
        assertThat(scan.events()).hasSize(4);
        assertThat(scan.truncated()).isTrue();
    }

    @Test
    void contractViolationsAreUnavailableNeverAnEmptyList() {
        for (String body : new String[] {
                page(1, false, item(1, "")),
                page(0, false, item(1, ""), item(1, "")),
                page(0, false, item(1, "").replace("\"PLANNED\"", "null")),
                page(0, false, item(1, "").replace("2026-12-01T10:00:00Z", "yarin")),
                page(0, false, item(1, "").replace("\"platforms\":[\"PC\"]", "\"platforms\":\"PC\"")),
                page(0, false, item(1, "").replace("\"joinable\":true", "\"joinable\":\"yes\"")),
                page(0, false, item(1, "").replace(",\"joinable\":true", "")),
                page(0, false, item(1, "").replace("\"participantCount\":3", "\"participantCount\":-1")),
                page(0, false, item(1, "").replace("\"capacity\":10", "\"capacity\":0")),
                page(0, false, item(1, "").replace("\"capacity\":10", "\"capacity\":\"10\"")),
                "{\"items\":{},\"page\":0,\"hasNext\":false}",
                "{\"items\":[],\"page\":0}"}) {
            assertThatThrownBy(() -> source(replies(body)).upcoming(ACTOR)).as(body)
                    .isInstanceOfSatisfying(RecommendationRequestException.class, e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
        }
    }

    @Test
    void forbiddenOrUnavailableEventPropagatesAndIsNotRetried() {
        RecommendationHttpClient http = mock(RecommendationHttpClient.class);
        when(http.get(any(), any())).thenThrow(new RecommendationRequestException(403, "SOURCE_ACCESS_DENIED"));
        assertThatThrownBy(() -> source(http).upcoming(ACTOR)).isInstanceOfSatisfying(RecommendationRequestException.class,
                e -> assertThat(e.code()).isEqualTo("SOURCE_ACCESS_DENIED"));
        verify(http, times(1)).get(any(), any());
    }
}
