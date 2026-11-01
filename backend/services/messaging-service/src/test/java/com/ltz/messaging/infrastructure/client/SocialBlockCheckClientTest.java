package com.ltz.messaging.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.messaging.application.port.BlockCheckUnavailableException;
import com.ltz.messaging.application.port.ServiceTokenPort;
import com.ltz.messaging.application.port.ServiceTokenUnavailableException;
import com.ltz.messaging.infrastructure.config.ServiceClientProperties;

/** Social'ın engel sorgusu: istek biçimi, servis token'ı, yanıt doğrulaması ve her hatada fail-closed. */
class SocialBlockCheckClientTest {

    private static final String CHECK_URL = "http://social-service:8080/internal/v1/blocks/check";

    private final UUID user = UUID.randomUUID();
    private final UUID blocked = UUID.randomUUID();
    private final UUID free = UUID.randomUUID();

    private MockRestServiceServer server;
    private SocialBlockCheckClient client;
    private String issuedAudience;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ServiceTokenPort tokens = audience -> {
            issuedAudience = audience;
            return "svc-token";
        };
        client = new SocialBlockCheckClient(builder, tokens, properties());
    }

    private static ServiceClientProperties properties() {
        return new ServiceClientProperties("http://auth-service:8080", "/internal/v1/service-tokens",
                "messaging-service", "test-only-secret", "http://social-service:8080", "/internal/v1/blocks/check");
    }

    private static String results(String... entries) {
        return "{\"results\":[" + String.join(",", entries) + "],\"extra\":true}";
    }

    private static String result(UUID userId, boolean isBlocked) {
        return "{\"userId\":\"%s\",\"blocked\":%s}".formatted(userId, isBlocked);
    }

    @Test
    void servisTokeniylaSorarEngelliOlanlariDoner() {
        server.expect(once(), requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer svc-token"))
                .andExpect(jsonPath("$.userId").value(user.toString()))
                .andExpect(jsonPath("$.otherUserIds.length()").value(2))
                .andRespond(withSuccess(results(result(blocked, true), result(free, false)),
                        MediaType.APPLICATION_JSON));

        assertThat(client.blockedAmong(user, List.of(blocked, free, blocked))).containsExactly(blocked);
        assertThat(issuedAudience).isEqualTo("social-service");
        server.verify();
    }

    @Test
    void sunucuHatasiVeYetkiHatasiFailClosed() {
        server.expect(once(), requestTo(CHECK_URL)).andRespond(withServerError());
        server.expect(once(), requestTo(CHECK_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(once(), requestTo(CHECK_URL)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "http://elsewhere"));

        List<UUID> others = List.of(free);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> client.blockedAmong(user, others))
                    .isInstanceOf(BlockCheckUnavailableException.class)
                    .hasMessageNotContaining("svc-token");
        }
    }

    @Test
    void eksikFazlaYaDaBozukYanitGuvenilmez() {
        String[] responses = {
            results(result(blocked, true)),
            results(result(blocked, true), result(free, false), result(UUID.randomUUID(), true)),
            results("{\"userId\":\"%s\"}".formatted(blocked), result(free, false)),
            "{}",
            "not json"
        };
        for (String response : responses) {
            server.reset();
            server.expect(once(), requestTo(CHECK_URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            List<UUID> asked = List.of(blocked, free);
            assertThatThrownBy(() -> client.blockedAmong(user, asked))
                    .isInstanceOf(BlockCheckUnavailableException.class);
        }
    }

    @Test
    void tokenAlinamazsaSocialCagrilmaz() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer silent = MockRestServiceServer.bindTo(builder).build();
        silent.expect(never(), requestTo(CHECK_URL));
        SocialBlockCheckClient noToken = new SocialBlockCheckClient(builder, audience -> {
            throw new ServiceTokenUnavailableException("no secret");
        }, properties());

        List<UUID> others = List.of(free);
        assertThatThrownBy(() -> noToken.blockedAmong(user, others))
                .isInstanceOf(BlockCheckUnavailableException.class)
                .hasCauseInstanceOf(ServiceTokenUnavailableException.class);
        silent.verify();
    }

    @Test
    void bosYaDaYuzdenFazlaKimlikReddedilir() {
        List<UUID> tooMany = Stream.generate(UUID::randomUUID).limit(101).toList();

        assertThatThrownBy(() -> client.blockedAmong(user, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.blockedAmong(user, tooMany)).isInstanceOf(IllegalArgumentException.class);
    }
}
