package com.ltz.game.infrastructure.persistence;

import com.ltz.game.GameServiceApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static com.ltz.game.infrastructure.persistence.MigrationTransferSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MigrationTransferApplicationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Test void realApplicationStartsAndReadsTransferredDataWithUnchangedSecurity() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var source = database(postgres, HISTORY);
            populate(source);
            var target = database(postgres, CURRENT);
            Receipt receipt = transfer(source, target, (connection, count) -> { });
            requireReady(source, target, receipt);
            // Önce eski fixture zinciriyle aynı güncel kod, sonra yeni zincir + taşınmış veri.
            Map<String, JsonNode> reference;
            try (var app = startApplication(source, HISTORY)) { reference = readPublicApis(app); }
            try (var app = startApplication(target, CURRENT)) {
                assertEquals(reference, readPublicApis(app));
                int port = ((WebServerApplicationContext) app).getWebServer().getPort();
                for (String path : List.of("/api/v1/game/feedback",
                        "/api/v1/game/admin/games/" + GAME + "/achievements",
                        "/api/v1/game/developer/games/" + GAME + "/feedback")) {
                    assertEquals(401, request(port, path).statusCode());
                }
            }
            requireReady(source, target, receipt); // HTTP okumaları verileri değiştirmedi.
            MigrationSchemaSupport.validateHibernate(target);
            flyway(target, CURRENT).validate();
            assertEquals(0, flyway(target, CURRENT).migrate().migrationsExecuted);
        }
    }

    private ConfigurableApplicationContext startApplication(DriverManagerDataSource source, String location) {
        // Sadece test JVM'si ve rastgele loopback portu; ENV ve DEV yapılandırması kullanılmaz.
        return new SpringApplicationBuilder(GameServiceApplication.class).run(
                "--server.address=127.0.0.1", "--server.port=0",
                "--spring.datasource.url=" + source.getUrl(),
                "--spring.datasource.username=" + source.getUsername(),
                "--spring.datasource.password=" + source.getPassword(),
                "--spring.flyway.locations=" + location,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--ltz.security.jwt.jwk-set-uri=", "--ltz.security.jwt.public-key=",
                "--game.catalog.sync.enabled=false", "--STEAM_CATALOG_ENABLED=false",
                "--logging.level.org.springframework.jdbc.core=INFO",
                "--logging.level.org.hibernate.SQL=INFO");
    }

    private Map<String, JsonNode> readPublicApis(ConfigurableApplicationContext app) throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        Map<String, JsonNode> results = new LinkedHashMap<>();
        for (String path : List.of("/actuator/health", "/api/v1/game/games?size=10",
                "/api/v1/game/games/search?q=Synthetic", "/api/v1/game/" + GAME + "/card",
                "/api/v1/game/" + GAME + "/metadata", "/api/v1/game/games/" + GAME,
                "/api/v1/game/games/" + GAME + "/modules/ACHIEVEMENTS",
                "/api/v1/game/games/" + GAME + "/modules/GUIDES",
                "/api/v1/game/games/" + GAME + "/developer-content")) {
            var response = request(port, path);
            assertEquals(200, response.statusCode(), path);
            results.put(path, JSON.readTree(response.body()));
        }
        assertEquals("UP", results.get("/actuator/health").get("status").asString());
        assertEquals(2, results.get("/api/v1/game/games?size=10").get("totalElements").asInt());
        assertEquals("Synthetic Atlas", results.get("/api/v1/game/" + GAME + "/card").get("title").asString());
        var metadata = results.get("/api/v1/game/" + GAME + "/metadata");
        assertEquals("tr", metadata.get("metadataLanguage").asString());
        assertEquals(2, metadata.get("languageSupport").size());
        assertTrue(metadata.get("languages").toString().contains("tr"));
        var achievements = results.get("/api/v1/game/games/" + GAME + "/modules/ACHIEVEMENTS");
        assertEquals("AVAILABLE", achievements.get("state").asString());
        assertEquals(1, achievements.get("items").size());
        assertEquals("INTEGRATION_PENDING", results.get("/api/v1/game/games/" + GAME + "/modules/GUIDES").get("state").asString());
        return results;
    }

    private HttpResponse<String> request(int port, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
