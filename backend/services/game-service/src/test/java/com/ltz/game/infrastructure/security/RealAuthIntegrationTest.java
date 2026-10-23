package com.ltz.game.infrastructure.security;

import com.ltz.game.PostgresTestSupport;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Gerçek Auth issuer/JWKS + Game filtre/controller/DB testi; DB yalnız Testcontainers'tır.
 * Auth kayıt/giriş HTTP akışı veya çalışan Gateway reverse proxy dağıtımı değildir.
 */
@SpringBootTest @Transactional
class RealAuthIntegrationTest extends PostgresTestSupport {
    static final UUID ADMIN_ID = UUID.randomUUID();
    static final UUID DEVELOPER_ID = UUID.randomUUID();
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    UUID game(String title, String origin, String type, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness) "
                + "values (?,?,?,now(),now(),?,?,?,'INDEX_ONLY')", id, title, title.toLowerCase(java.util.Locale.ROOT), origin, type, status);
        return id;
    }

    ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType("application/json").content(body);
        return mvc.perform(request);
    }

    static String read(ResultActions result, String path) throws Exception {
        Object value = com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return String.valueOf(value);
    }
    static final Path COMPILED;
    static final ProductionAuthSupport AUTH;
    static final HttpServer JWKS;
    static {
        try {
            COMPILED = Files.createTempDirectory("ltz-auth-contract-");
            AUTH = new ProductionAuthSupport(COMPILED);
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = AUTH.jwks().getBytes(StandardCharsets.UTF_8);
            JWKS.createContext("/api/v1/auth/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                exchange.close();
            });
            JWKS.start();
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }

    @DynamicPropertySource static void realAuth(DynamicPropertyRegistry registry) {
        registry.add("ltz.security.jwt.public-key", () -> "");
        registry.add("ltz.security.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/api/v1/auth/.well-known/jwks.json");
    }

    @AfterAll static void release() throws Exception {
        JWKS.stop(0);
        AUTH.close();
        try (var paths = Files.walk(COMPILED)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    @Test void realPlayerCanCreateAndReadOwnFeedbackButCannotAdminister() throws Exception {
        UUID player = UUID.randomUUID();
        String token = AUTH.token(player, "PLAYER");
        UUID game = game("Real Auth feedback", "INTERNAL", "MAIN_GAME", "ACTIVE");
        String id = read(send(post("/api/v1/game/feedback"), token,
                "{\"gameId\":\"" + game + "\",\"type\":\"GENERAL\",\"title\":\"Test feedback\",\"body\":\"Isolated test content\"}")
                .andExpect(status().isCreated()), "$.id");
        assertEquals(player, jdbc.queryForObject("select author_id from game_developer_feedback where id=?", UUID.class, UUID.fromString(id)));
        send(get("/api/v1/game/feedback/" + id), token, null).andExpect(status().isOk());
        send(get("/api/v1/game/feedback/" + id), AUTH.token(UUID.randomUUID(), "PLAYER"), null).andExpect(status().isNotFound());
        send(get("/api/v1/game/admin/games/" + game), token, null).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test void realAdminAuditAndDeveloperGrantUseVerifiedSubjects() throws Exception {
        UUID game = game("Real Auth grants", "INTERNAL", "MAIN_GAME", "ACTIVE");
        String admin = AUTH.token(ADMIN_ID, "ADMIN");
        String dev = AUTH.token(DEVELOPER_ID, "PLAYER", "GAME_DEVELOPER");
        String content = "/api/v1/game/developer/games/" + game + "/content";
        send(get(content), dev, null).andExpect(status().isForbidden());
        send(post("/api/v1/game/admin/games/" + game + "/developers"), admin,
                "{\"developerUserId\":\"" + DEVELOPER_ID + "\",\"reason\":\"Isolated contract grant\"}")
                .andExpect(status().isOk());
        send(get(content), dev, null).andExpect(status().isOk());
        UUID other = game("Other game", "INTERNAL", "MAIN_GAME", "ACTIVE");
        send(get("/api/v1/game/developer/games/" + other + "/content"), dev, null).andExpect(status().isForbidden());
        send(get(content), AUTH.token(DEVELOPER_ID, "PLAYER"), null).andExpect(status().isForbidden());
        send(post("/api/v1/game/admin/games/" + game + "/developers/" + DEVELOPER_ID + "/revoke"), admin,
                "{\"reason\":\"Isolated contract revoke\"}").andExpect(status().isNoContent());
        send(get(content), dev, null).andExpect(status().isForbidden());
        assertEquals(2, jdbc.queryForObject("select count(*) from game_admin_audits where actor_id=?", Integer.class, ADMIN_ID));
    }

    @Test void productionGatewayFiltersConvertCookiesAndEnforceCsrfBeforeGame() throws Exception {
        Filter[] filters = AUTH.gatewayFilters();
        MockMvc gateway = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).apply(springSecurity()).build();
        UUID player = UUID.randomUUID();
        String token = AUTH.token(player, "PLAYER");
        Cookie access = new Cookie("ltz_access", token);
        Cookie csrf = new Cookie("XSRF-TOKEN", "isolated-csrf");
        gateway.perform(get("/api/v1/game/feedback").cookie(access)).andExpect(status().isOk());
        gateway.perform(get("/api/v1/game/admin/games/" + UUID.randomUUID()).cookie(access)
                .header("X-User-Id", ADMIN_ID.toString()).header("X-Role", "ADMIN")).andExpect(status().isForbidden());
        String path = "/api/v1/game/admin/games/" + UUID.randomUUID() + "/archive";
        Cookie admin = new Cookie("ltz_access", AUTH.token(ADMIN_ID, "ADMIN"));
        gateway.perform(post(path).cookie(admin, csrf).contentType("application/json").content("{\"reason\":\"Isolated archive reason\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        gateway.perform(post(path).cookie(admin, csrf).header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        gateway.perform(post(path).cookie(admin, csrf).header("X-XSRF-TOKEN", csrf.getValue()).header("Origin", "https://evil.example")
                .contentType("application/json").content("{\"reason\":\"Isolated archive reason\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        gateway.perform(post(path).cookie(admin, csrf).header("X-XSRF-TOKEN", csrf.getValue()).header("Origin", "https://app.example.test")
                .contentType("application/json").content("{\"reason\":\"Isolated archive reason\"}"))
                .andExpect(status().isNotFound());
        gateway.perform(get("/api/v1/game/feedback")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
