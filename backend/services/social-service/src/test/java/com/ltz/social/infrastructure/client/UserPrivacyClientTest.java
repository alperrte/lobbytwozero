package com.ltz.social.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.social.application.port.FriendsVisibility;
import com.ltz.social.application.port.PrivacyUnavailableException;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

/**
 * Arkadaş listesi görünürlük tercihi user-service'in gizlilik kararı ucundan Auth servis token'ıyla okunur (SOC-12).
 * Tercih belirlenemeyen her durumda {@link PrivacyUnavailableException} fırlatılır; çağıran bunu PRIVATE sayar.
 */
class UserPrivacyClientTest {

    private static final String DECISIONS_URL = "http://user-service:8080/internal/v1/privacy/decisions";
    private static final String TOKEN = StubServiceTokens.TOKEN;
    private static final UUID OWNER = UUID.fromString("0198a3c2-5555-7d44-9a55-3f1c2b4d5e66");
    private static final UUID OTHER = UUID.fromString("0198a3c2-6666-7d44-9a55-3f1c2b4d5e66");

    private StubServiceTokens tokens;
    private MockRestServiceServer server;
    private UserPrivacyClient client;

    @BeforeEach
    void setUp() {
        tokens = new StubServiceTokens();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UserPrivacyClient(builder, tokens, new ServiceClientProperties(
                "http://auth-service:8080", "/internal/v1/service-tokens", "social-service", "secret",
                "http://community-service:8080", "http://user-service:8080"));
    }

    private static String decision(UUID ownerId, String setting) {
        return "{\"decisions\":[{\"ownerId\":\"%s\",\"setting\":\"%s\",\"allowed\":false,\"friends\":false}]}"
                .formatted(ownerId, setting);
    }

    @Test
    void tercihUserServisindenTokenlaVeYalnizSahipKimligiyleOkunur() {
        server.expect(once(), requestTo(DECISIONS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.field").value("FRIENDS_LIST"))
                .andExpect(jsonPath("$.ownerIds[0]").value(OWNER.toString()))
                .andExpect(jsonPath("$.ownerIds.length()").value(1))
                .andExpect(jsonPath("$.viewerId").doesNotExist())
                .andRespond(withSuccess(decision(OWNER, "PUBLIC"), MediaType.APPLICATION_JSON));

        assertThat(client.visibilityOf(OWNER)).isEqualTo(FriendsVisibility.PUBLIC);

        server.verify();
        assertThat(tokens.requestedAudiences).containsExactly("user-service");
    }

    @ParameterizedTest
    @EnumSource(FriendsVisibility.class)
    void herAyarAyniAdlaEslenir(FriendsVisibility visibility) {
        server.expect(once(), requestTo(DECISIONS_URL))
                .andRespond(withSuccess(decision(OWNER, visibility.name()), MediaType.APPLICATION_JSON));

        assertThat(client.visibilityOf(OWNER)).isEqualTo(visibility);
    }

    @Test
    void gizliAnahtarYoksaCagriYapilmazTercihBelirsizdir() {
        tokens.configured = false;

        assertThatThrownBy(() -> client.visibilityOf(OWNER)).isInstanceOf(PrivacyUnavailableException.class);

        server.verify();
        assertThat(tokens.requestedAudiences).isEmpty();
    }

    @Test
    void tokenAlinamazsaTercihBelirsizdirIletideTokenYok() {
        tokens.failing = true;

        assertThatThrownBy(() -> client.visibilityOf(OWNER))
                .isInstanceOf(PrivacyUnavailableException.class)
                .hasMessageNotContaining(TOKEN);

        server.verify();
    }

    @Test
    void sunucuHatasindaTercihBelirsizdirIletideGovdeYok() {
        server.expect(once(), requestTo(DECISIONS_URL)).andRespond(withServerError().body("secret-body-detail"));

        assertThatThrownBy(() -> client.visibilityOf(OWNER))
                .isInstanceOf(PrivacyUnavailableException.class)
                .hasMessageNotContaining("secret-body-detail")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void reddedilenTokendaTercihBelirsizdir() {
        server.expect(once(), requestTo(DECISIONS_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.visibilityOf(OWNER)).isInstanceOf(PrivacyUnavailableException.class);
    }

    @Test
    void yanittaSahibinKarariYoksaTercihBelirsizdir() {
        server.expect(once(), requestTo(DECISIONS_URL))
                .andRespond(withSuccess(decision(OTHER, "PUBLIC"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.visibilityOf(OWNER)).isInstanceOf(PrivacyUnavailableException.class);
    }

    @Test
    void bosYanittaTercihBelirsizdir() {
        server.expect(once(), requestTo(DECISIONS_URL))
                .andRespond(withSuccess("{\"decisions\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.visibilityOf(OWNER)).isInstanceOf(PrivacyUnavailableException.class);
    }

    @Test
    void bilinmeyenAyarTercihBelirsizdir() {
        server.expect(once(), requestTo(DECISIONS_URL))
                .andRespond(withSuccess(decision(OWNER, "EVERYONE"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.visibilityOf(OWNER)).isInstanceOf(PrivacyUnavailableException.class);
    }

}
