package com.ltz.community.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ltz.community.domain.exception.ServiceTokenUnavailableException;

class AuthServiceTokenClientTest {

	private static final String URL = "http://auth-service:8080/internal/v1/service-tokens";
	private static final String SECRET = "test-gizli-anahtar-degeri";

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-29T10:00:00Z"));
	private RestClient.Builder builder;
	private MockRestServiceServer server;
	private AuthServiceTokenClient client;

	@BeforeEach
	void setUp() {
		builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new AuthServiceTokenClient(builder, clock, "http://auth-service:8080", "/internal/v1/service-tokens", "community-service", SECRET);
	}

	private static String tokenBody(String token, long expiresIn) {
		return "{\"accessToken\":\"" + token + "\",\"tokenType\":\"Bearer\",\"expiresIn\":" + expiresIn
				+ ",\"scope\":\"events:ingest\",\"yeniAlan\":true}";
	}

	@Test
	void requestsATokenForTheAudienceWithClientCredentials() {
		server.expect(once(), requestTo(URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.clientId").value("community-service"))
				.andExpect(jsonPath("$.clientSecret").value(SECRET))
				.andExpect(jsonPath("$.audience").value("admin-service"))
				.andRespond(withSuccess(tokenBody("jwt-admin", 60), MediaType.APPLICATION_JSON));

		assertThat(client.tokenFor("admin-service")).isEqualTo("jwt-admin");
		server.verify();
	}

	@Test
	void isConfiguredOnlyWhenTheSecretIsPresent() {
		assertThat(client.isConfigured()).isTrue();
		for (String blank : new String[] { "", "   ", null }) {
			assertThat(new AuthServiceTokenClient(builder, clock, "http://auth-service:8080", "/internal/v1/service-tokens", "community-service", blank)
					.isConfigured()).isFalse();
		}
	}

	@Test
	void aBlankSecretFailsClosedWithoutCallingAuth() {
		AuthServiceTokenClient unconfigured = new AuthServiceTokenClient(builder, clock, "http://auth-service:8080",
				"/internal/v1/service-tokens", "community-service", "");

		assertThatThrownBy(() -> unconfigured.tokenFor("admin-service")).isInstanceOf(ServiceTokenUnavailableException.class);
		server.verify();
	}

	@Test
	void theTokenIsReusedUntilShortlyBeforeItExpiresThenRefreshed() {
		server.expect(once(), requestTo(URL)).andRespond(withSuccess(tokenBody("ilk", 60), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(URL)).andRespond(withSuccess(tokenBody("ikinci", 60), MediaType.APPLICATION_JSON));

		assertThat(client.tokenFor("admin-service")).isEqualTo("ilk");
		clock.advance(Duration.ofSeconds(40));
		assertThat(client.tokenFor("admin-service")).isEqualTo("ilk");
		// 60 sn - 15 sn yenileme payı = 45. sn'den sonra yeniden alınır; süresi dolmak üzere olan token gönderilmez.
		clock.advance(Duration.ofSeconds(6));
		assertThat(client.tokenFor("admin-service")).isEqualTo("ikinci");
		server.verify();
	}

	@Test
	void tokensAreKeptPerAudience() {
		server.expect(once(), requestTo(URL)).andExpect(jsonPath("$.audience").value("admin-service"))
				.andRespond(withSuccess(tokenBody("admin-jwt", 60), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(URL)).andExpect(jsonPath("$.audience").value("notification-service"))
				.andRespond(withSuccess(tokenBody("bildirim-jwt", 60), MediaType.APPLICATION_JSON));

		assertThat(client.tokenFor("admin-service")).isEqualTo("admin-jwt");
		assertThat(client.tokenFor("notification-service")).isEqualTo("bildirim-jwt");
		assertThat(client.tokenFor("admin-service")).isEqualTo("admin-jwt");
		server.verify();
	}

	@Test
	void aLongerLifetimeThanTheContractIsStillCachedForAtMostSixtySeconds() {
		server.expect(once(), requestTo(URL)).andRespond(withSuccess(tokenBody("uzun", 3600), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(URL)).andRespond(withSuccess(tokenBody("yeni", 3600), MediaType.APPLICATION_JSON));

		assertThat(client.tokenFor("admin-service")).isEqualTo("uzun");
		clock.advance(Duration.ofSeconds(50));
		assertThat(client.tokenFor("admin-service")).isEqualTo("yeni");
		server.verify();
	}

	@Test
	void aTokenTooShortLivedToCacheIsReturnedButNeverReused() {
		server.expect(once(), requestTo(URL)).andRespond(withSuccess(tokenBody("kisa-1", 10), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(URL)).andRespond(withSuccess(tokenBody("kisa-2", 0), MediaType.APPLICATION_JSON));

		assertThat(client.tokenFor("admin-service")).isEqualTo("kisa-1");
		assertThat(client.tokenFor("admin-service")).isEqualTo("kisa-2");
		server.verify();
	}

	@Test
	void authErrorsBecomeServiceTokenUnavailableWithoutLeakingTheSecret() {
		server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("gizli: " + SECRET));
		server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
		server.expect(once(), requestTo(URL)).andRespond(withServerError());

		for (int attempt = 0; attempt < 3; attempt++) {
			assertThatThrownBy(() -> client.tokenFor("admin-service"))
					.isInstanceOf(ServiceTokenUnavailableException.class)
					.hasMessageNotContaining(SECRET)
					.hasCauseInstanceOf(RestClientException.class);
		}
		server.verify();
	}

	@Test
	void aFailedRequestIsNeverCachedSoTheNextCallTriesAgain() {
		server.expect(once(), requestTo(URL)).andRespond(withServerError());
		server.expect(once(), requestTo(URL)).andRespond(withSuccess(tokenBody("kurtarildi", 60), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.tokenFor("admin-service")).isInstanceOf(ServiceTokenUnavailableException.class);
		assertThat(client.tokenFor("admin-service")).isEqualTo("kurtarildi");
		server.verify();
	}

	@Test
	void anEmptyOrTokenlessResponseIsRejected() {
		server.expect(once(), requestTo(URL)).andRespond(withSuccess("{\"expiresIn\":60}", MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(URL)).andRespond(withSuccess("{\"accessToken\":\" \",\"expiresIn\":60}", MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

		for (int attempt = 0; attempt < 3; attempt++) {
			assertThatThrownBy(() -> client.tokenFor("admin-service")).isInstanceOf(ServiceTokenUnavailableException.class);
		}
		server.verify();
	}

	/** Testte zamanı elle ilerletmek için. */
	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant start) {
			this.now = start;
		}

		void advance(Duration by) {
			now = now.plus(by);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
