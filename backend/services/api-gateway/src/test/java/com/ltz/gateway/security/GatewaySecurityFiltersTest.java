package com.ltz.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

class GatewaySecurityFiltersTest {

    private static final String ORIGIN = "http://localhost:5001";
    private static final String TOKEN = "header.payload.signature";

    private static GatewaySecurityProperties properties(String trustedProxies, int perMinute) {
        return new GatewaySecurityProperties(new GatewaySecurityProperties.Cors(List.of(ORIGIN)), trustedProxies,
                new GatewaySecurityProperties.AuthRateLimit(perMinute));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("10.1.1.1");
        return request;
    }

    /** Zincire ulaşan (muhtemelen sarmalanmış) isteği yakalar. */
    private static final class Capture implements FilterChain {
        final AtomicReference<HttpServletRequest> reached = new AtomicReference<>();

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            reached.set((HttpServletRequest) req);
        }
    }

    // ---- Cookie -> Bearer ----

    @Test
    void cerezliGetIstegiBearerBasligiylaIletilir() throws ServletException, java.io.IOException {
        CookieBearerFilter filter = new CookieBearerFilter(properties("", 120));
        MockHttpServletRequest request = request("GET", "/api/v1/library/items");
        request.setCookies(new Cookie("__Host-ltz_access", TOKEN));
        Capture chain = new Capture();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.reached.get().getHeader("Authorization")).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void cerezliPostCsrfOlmadanReddedilirVeServiseUlasmaz() throws ServletException, java.io.IOException {
        CookieBearerFilter filter = new CookieBearerFilter(properties("", 120));
        MockHttpServletRequest request = request("POST", "/api/v1/library/items");
        request.setCookies(new Cookie("__Host-ltz_access", TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();
        Capture chain = new Capture();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_TOKEN_INVALID").doesNotContain(TOKEN);
        assertThat(chain.reached.get()).isNull();
    }

    @Test
    void cerezliPostYanlisCsrfBasligiylaReddedilir() throws ServletException, java.io.IOException {
        CookieBearerFilter filter = new CookieBearerFilter(properties("", 120));
        MockHttpServletRequest request = request("POST", "/api/v1/library/items");
        request.setCookies(new Cookie("__Host-ltz_access", TOKEN), new Cookie("__Host-XSRF-TOKEN", "abc"));
        request.addHeader("X-XSRF-TOKEN", "abd");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Capture chain = new Capture();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.reached.get()).isNull();
    }

    @Test
    void cerezliPostEslesenCsrfIleBearerOlarakIletilir() throws ServletException, java.io.IOException {
        CookieBearerFilter filter = new CookieBearerFilter(properties("", 120));
        MockHttpServletRequest request = request("POST", "/api/v1/library/items");
        request.setCookies(new Cookie("ltz_access", TOKEN), new Cookie("XSRF-TOKEN", "abc"));
        request.addHeader("X-XSRF-TOKEN", "abc");
        request.addHeader("Origin", ORIGIN);
        Capture chain = new Capture();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.reached.get().getHeader("Authorization")).isEqualTo("Bearer " + TOKEN);
    }

    @Test
    void izinsizOriginliCerezliPostEslesenCsrfeRagmenReddedilir() throws ServletException, java.io.IOException {
        CookieBearerFilter filter = new CookieBearerFilter(properties("", 120));
        MockHttpServletRequest request = request("DELETE", "/api/v1/library/items/1");
        request.setCookies(new Cookie("__Host-ltz_access", TOKEN), new Cookie("__Host-XSRF-TOKEN", "abc"));
        request.addHeader("X-XSRF-TOKEN", "abc");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Capture chain = new Capture();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ORIGIN_NOT_ALLOWED");
        assertThat(chain.reached.get()).isNull();
    }

    @Test
    void authorizationBasligiVarsaCerezeDokunulmaz() throws ServletException, java.io.IOException {
        CookieBearerFilter filter = new CookieBearerFilter(properties("", 120));
        MockHttpServletRequest request = request("POST", "/api/v1/library/items");
        request.addHeader("Authorization", "Bearer client-supplied");
        request.setCookies(new Cookie("__Host-ltz_access", TOKEN));
        Capture chain = new Capture();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.reached.get().getHeader("Authorization")).isEqualTo("Bearer client-supplied");
    }

    @Test
    void authRotasiVeCerezsizIstekDegistirilmez() throws ServletException, java.io.IOException {
        CookieBearerFilter filter = new CookieBearerFilter(properties("", 120));
        MockHttpServletRequest auth = request("POST", "/api/v1/auth/session/refresh");
        auth.setCookies(new Cookie("__Host-ltz_access", TOKEN));
        Capture authChain = new Capture();
        filter.doFilter(auth, new MockHttpServletResponse(), authChain);
        assertThat(authChain.reached.get().getHeader("Authorization")).isNull();

        Capture anonymousChain = new Capture();
        filter.doFilter(request("GET", "/api/v1/game/games"), new MockHttpServletResponse(), anonymousChain);
        assertThat(anonymousChain.reached.get().getHeader("Authorization")).isNull();
    }

    @Test
    void kodlanmisYolAuthRotasiOlarakAyriGorulmez() throws ServletException, java.io.IOException {
        CookieBearerFilter filter = new CookieBearerFilter(properties("", 120));
        MockHttpServletRequest request = request("GET", "/api/v1/%61uth/session/refresh");
        request.setCookies(new Cookie("__Host-ltz_access", TOKEN));
        Capture chain = new Capture();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // Çözülmüş yol /api/v1/auth/... olduğundan Auth rotası gibi davranılır ve çerez çevrilmez.
        assertThat(chain.reached.get().getHeader("Authorization")).isNull();
    }

    // ---- İstemci başlık temizliği ----

    @Test
    void kimlikBasliklariHerZamanSilinir() throws ServletException, java.io.IOException {
        ClientRequestSanitizerFilter filter = new ClientRequestSanitizerFilter(properties("", 120));
        MockHttpServletRequest request = request("GET", "/api/v1/library/items");
        request.addHeader("X-User-Id", "11111111-1111-1111-1111-111111111111");
        request.addHeader("X-Role", "ADMIN");
        request.addHeader("X-Roles", "ADMIN");
        request.addHeader("X-Account-Id", "x");
        request.addHeader("X-Internal-Call", "1");
        request.addHeader("Accept", "application/json");
        Capture chain = new Capture();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest reached = chain.reached.get();
        assertThat(reached.getHeader("X-User-Id")).isNull();
        assertThat(reached.getHeader("x-role")).isNull();
        assertThat(reached.getHeader("X-Roles")).isNull();
        assertThat(reached.getHeader("X-Account-Id")).isNull();
        assertThat(reached.getHeader("X-Internal-Call")).isNull();
        assertThat(java.util.Collections.list(reached.getHeaderNames())).containsExactly("Accept");
    }

    @Test
    void guvenilmeyenProxydenGelenForwardedBasliklariSilinirVeIpBaglantidanAlinir()
            throws ServletException, java.io.IOException {
        ClientRequestSanitizerFilter filter = new ClientRequestSanitizerFilter(properties("", 120));
        MockHttpServletRequest request = request("GET", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("Forwarded", "for=1.2.3.4");
        request.addHeader("X-Real-IP", "1.2.3.4");
        Capture chain = new Capture();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.reached.get().getHeader("X-Forwarded-For")).isNull();
        assertThat(chain.reached.get().getHeader("Forwarded")).isNull();
        assertThat(chain.reached.get().getAttribute(ClientRequestSanitizerFilter.CLIENT_IP_ATTRIBUTE))
                .isEqualTo("10.1.1.1");
    }

    @Test
    void guvenilirProxydenGelenForwardedBasligiKorunurVeSonGirisIstemciIpsiOlur()
            throws ServletException, java.io.IOException {
        ClientRequestSanitizerFilter filter = new ClientRequestSanitizerFilter(properties("10\\.1\\.1\\.\\d+", 120));
        MockHttpServletRequest request = request("GET", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");
        Capture chain = new Capture();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.reached.get().getHeader("X-Forwarded-For")).isEqualTo("6.6.6.6, 203.0.113.9");
        assertThat(chain.reached.get().getAttribute(ClientRequestSanitizerFilter.CLIENT_IP_ATTRIBUTE))
                .isEqualTo("203.0.113.9");
    }

    // ---- Hız sınırı ----

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-21T10:00:10Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public java.time.ZoneId getZone() {
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

    @Test
    void authUclariIpBasinaSinirlanirVePencereSonundaSifirlanir() throws ServletException, java.io.IOException {
        MutableClock clock = new MutableClock();
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties("", 3), clock);

        for (int i = 0; i < 3; i++) {
            Capture chain = new Capture();
            filter.doFilter(request("POST", "/api/v1/auth/login"), new MockHttpServletResponse(), chain);
            assertThat(chain.reached.get()).isNotNull();
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        Capture blockedChain = new Capture();
        filter.doFilter(request("POST", "/api/v1/auth/login"), blocked, blockedChain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("50");
        assertThat(blockedChain.reached.get()).isNull();

        MockHttpServletRequest other = request("POST", "/api/v1/auth/login");
        other.setRemoteAddr("10.9.9.9");
        Capture otherChain = new Capture();
        filter.doFilter(other, new MockHttpServletResponse(), otherChain);
        assertThat(otherChain.reached.get()).isNotNull();

        clock.advanceSeconds(60);
        Capture afterWindow = new Capture();
        filter.doFilter(request("POST", "/api/v1/auth/login"), new MockHttpServletResponse(), afterWindow);
        assertThat(afterWindow.reached.get()).isNotNull();
    }

    @Test
    void authDisiVeJwksIstekleriSinirlanmaz() throws ServletException, java.io.IOException {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties("", 1), new MutableClock());

        for (int i = 0; i < 5; i++) {
            Capture jwks = new Capture();
            filter.doFilter(request("GET", "/api/v1/auth/.well-known/jwks.json"), new MockHttpServletResponse(), jwks);
            assertThat(jwks.reached.get()).isNotNull();
            Capture other = new Capture();
            filter.doFilter(request("GET", "/api/v1/game/games"), new MockHttpServletResponse(), other);
            assertThat(other.reached.get()).isNotNull();
        }
    }

    @Test
    void sinirSifirsaKapalidir() throws ServletException, java.io.IOException {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties("", 0), new MutableClock());

        for (int i = 0; i < 5; i++) {
            Capture chain = new Capture();
            filter.doFilter(request("POST", "/api/v1/auth/login"), new MockHttpServletResponse(), chain);
            assertThat(chain.reached.get()).isNotNull();
        }
    }

    // ---- Ayar doğrulama ----

    @Test
    void gecersizCorsKaynaklariAcilistaReddedilir() {
        for (String bad : List.of("*", "http://localhost:5001/path", "ftp://x.example", "localhost:5001",
                "http://user@localhost:5001")) {
            assertThatThrownBy(() -> new GatewaySecurityProperties.Cors(List.of(bad)))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new GatewaySecurityProperties.Cors(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewaySecurityProperties.Cors(List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gecersizProxyRegexiVeSinirReddedilir() {
        assertThatThrownBy(() -> properties("(", 120)).isInstanceOf(java.util.regex.PatternSyntaxException.class);
        assertThatThrownBy(() -> new GatewaySecurityProperties.AuthRateLimit(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prodProfilindeYalnizHttpsOriginKabulEdilir() {
        GatewaySecurityProperties http = properties("", 120);
        GatewaySecurityProperties https = new GatewaySecurityProperties(
                new GatewaySecurityProperties.Cors(List.of("https://app.example.com")), "", new GatewaySecurityProperties.AuthRateLimit(120));
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new GatewaySecurityConfig(http, prod)).isInstanceOf(IllegalStateException.class);
        new GatewaySecurityConfig(https, prod);
        new GatewaySecurityConfig(http, new MockEnvironment());
    }
}
