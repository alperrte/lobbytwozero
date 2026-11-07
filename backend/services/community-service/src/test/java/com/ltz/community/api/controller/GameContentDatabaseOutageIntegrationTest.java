package com.ltz.community.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import com.ltz.community.CommunityServiceApplication;

/** Her senaryo kendi uygulamasını ve DB'sini kapatır; ortak test PostgreSQL'i etkilenmez. */
class GameContentDatabaseOutageIntegrationTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void firstListOrDetailRequestAfterDatabaseLossIsAnUnavailableProblem(boolean detail) throws Exception {
		try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")) {
			postgres.start();
			try (ConfigurableApplicationContext context = application(postgres);
					HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
				int port = ((WebServerApplicationContext) context).getWebServer().getPort();
				String path = "/api/v1/community/games/" + UUID.randomUUID() + "/content"
						+ (detail ? "/" + UUID.randomUUID() : "?type=GUIDES");
				HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
						.timeout(Duration.ofSeconds(10)).GET().build();
				assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
						.isEqualTo(detail ? 404 : 200);
				postgres.stop();

				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				assertThat(response.statusCode()).isEqualTo(503);
				assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(value ->
						assertThat(value).startsWith("application/problem+json"));
				assertThat(response.body()).contains("\"status\":503", "The service is temporarily unavailable.")
						.doesNotContain("jdbc:", "SQLException", "rollback", "items", postgres.getPassword());
			}
		}
	}

	private static ConfigurableApplicationContext application(PostgreSQLContainer<?> postgres) {
		return new SpringApplicationBuilder(CommunityServiceApplication.class).run(
				"--server.address=127.0.0.1", "--server.port=0", "--logging.level.root=ERROR",
				"--spring.datasource.url=" + postgres.getJdbcUrl(),
				"--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword(),
				"--spring.datasource.hikari.maximum-pool-size=3",
				"--spring.datasource.hikari.minimum-idle=1", "--spring.datasource.hikari.connection-timeout=1000");
	}
}
