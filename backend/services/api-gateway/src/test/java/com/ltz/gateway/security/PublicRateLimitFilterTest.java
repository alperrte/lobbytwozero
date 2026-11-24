package com.ltz.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

/** Girişsiz profil/medya uçlarının IP başına sınırı (C056/C073). Saf birim testi, Spring bağlamı gerekmez. */
class PublicRateLimitFilterTest {

    private static final String PROFILE = "/api/v1/user/profiles/6f9c1c1e-0000-4000-8000-000000000001";

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-21T10:00:10Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static MockHttpServletRequest request(String method, String uri, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    /** İstek zincire ulaştıysa true; engellendiyse (429) false döner. */
    private static boolean passes(PublicRateLimitFilter filter, String method, String uri, String remoteAddr)
            throws ServletException, IOException {
        AtomicBoolean reached = new AtomicBoolean();
        FilterChain chain = (req, res) -> reached.set(true);
        filter.doFilter(request(method, uri, remoteAddr), new MockHttpServletResponse(), chain);
        return reached.get();
    }

    @Test
    void profilVeMedyaUclariIpBasinaSinirlanirVePencereSonundaSifirlanir() throws ServletException, IOException {
        MutableClock clock = new MutableClock();
        PublicRateLimitFilter filter = new PublicRateLimitFilter(3, clock);

        for (int i = 0; i < 3; i++) {
            assertThat(passes(filter, "GET", PROFILE, "10.1.1.1")).isTrue();
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();
        filter.doFilter(request("GET", PROFILE, "10.1.1.1"), blocked, (req, res) -> reached.set(true));
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("50");
        assertThat(reached).isFalse();

        // Başka IP etkilenmez; medya yolu aynı sayaca girer.
        assertThat(passes(filter, "GET", PROFILE, "10.9.9.9")).isTrue();
        assertThat(passes(filter, "GET", "/api/v1/user/media/avatar.png", "10.1.1.1")).isFalse();

        clock.advanceSeconds(60);
        assertThat(passes(filter, "GET", PROFILE, "10.1.1.1")).isTrue();
    }

    @Test
    void digerUclarVeOptionsSinirlanmaz() throws ServletException, IOException {
        PublicRateLimitFilter filter = new PublicRateLimitFilter(1, new MutableClock());

        for (int i = 0; i < 5; i++) {
            assertThat(passes(filter, "GET", "/api/v1/user/me", "10.1.1.1")).isTrue();
            assertThat(passes(filter, "POST", "/api/v1/auth/login", "10.1.1.1")).isTrue();
            assertThat(passes(filter, "OPTIONS", PROFILE, "10.1.1.1")).isTrue();
        }
    }

    @Test
    void sinirSifirsaKapalidir() throws ServletException, IOException {
        PublicRateLimitFilter filter = new PublicRateLimitFilter(0, new MutableClock());

        for (int i = 0; i < 10; i++) {
            assertThat(passes(filter, "GET", PROFILE, "10.1.1.1")).isTrue();
        }
    }

    @Test
    void gecersizSinirAcilistaReddedilir() {
        Clock clock = new MutableClock();
        assertThatThrownBy(() -> new PublicRateLimitFilter(-1, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicRateLimitFilter(100_001, clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
