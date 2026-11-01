package com.ltz.messaging.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.api.controller.MessagingApiPaths;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 8 KB gövde sınırı: bildirilen ve bildirilmeyen (chunked) uzunlukta 413; sınır içindeki gövde aynen iletilir. */
class RequestBodySizeLimitTest extends IntegrationTestSupport {

    private static final int LIMIT = 8 * 1024;

    @Test
    void sinirAsanGovde413() throws Exception {
        String body = "{\"title\":\"%s\",\"memberIds\":[]}".formatted("x".repeat(LIMIT));

        mockMvc.perform(post(MessagingApiPaths.GROUP_CONVERSATIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void uzunlukBildirilmeyenGovdeDeOkumaSirasindaReddedilir() throws Exception {
        RecordingResolver resolver = new RecordingResolver();
        RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(16, resolver);
        MockHttpServletRequest request = new ChunkedRequest("x".repeat(17));
        boolean[] passed = {false};

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> passed[0] = true);

        assertThat(passed[0]).isFalse();
        assertThat(resolver.resolved).isTrue();
    }

    @Test
    void sinirIcindekiGovdeAkisVeOkuyucuIleAynenIletilir() throws Exception {
        RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(16, new RecordingResolver());
        AtomicReference<String> viaStream = new AtomicReference<>();
        AtomicReference<String> viaReader = new AtomicReference<>();
        AtomicReference<Long> length = new AtomicReference<>();

        FilterChain readStream = (req, res) -> {
            viaStream.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            length.set(((HttpServletRequest) req).getContentLengthLong());
        };
        filter.doFilter(new ChunkedRequest("{\"ğ\":1}"), new MockHttpServletResponse(), readStream);
        FilterChain readReader = (req, res) -> viaReader.set(readAll(req.getReader()));
        filter.doFilter(new ChunkedRequest("{\"ğ\":1}"), new MockHttpServletResponse(), readReader);

        assertThat(viaStream.get()).isEqualTo("{\"ğ\":1}");
        assertThat(viaReader.get()).isEqualTo("{\"ğ\":1}");
        assertThat(length.get()).isEqualTo("{\"ğ\":1}".getBytes(StandardCharsets.UTF_8).length);
    }

    private static String readAll(BufferedReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        int next;
        while ((next = reader.read()) >= 0) {
            text.append((char) next);
        }
        return text.toString();
    }

    /** Content-Length bildirmeyen (chunked) istek. */
    private static final class ChunkedRequest extends MockHttpServletRequest {

        ChunkedRequest(String body) {
            super("POST", "/api/v1/messaging/conversations/groups");
            setContent(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }
    }

    private static final class RecordingResolver implements HandlerExceptionResolver {

        private boolean resolved;

        @Override
        public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                             Object handler, Exception exception) {
            resolved = true;
            return new ModelAndView();
        }
    }
}
