package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.PayloadTooLargeException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

class RequestBodySizeLimitFilterTest {

    private static final int LIMIT = 16;

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(LIMIT, resolver);

    @Test
    void bildirilenUzunlukSiniriAsarsaGovdeOkunmadan413Doner() throws Exception {
        MockHttpServletRequest request = requestWithBody("x".repeat(LIMIT + 1));
        AtomicReference<ServletRequest> forwarded = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> forwarded.set(req));

        assertThat(forwarded.get()).isNull();
        verify(resolver).resolveException(any(), any(), isNull(), any(PayloadTooLargeException.class));
    }

    @Test
    void uzunluguBilinmeyenGovdeOkunurkenSinirAsilirsaDurdurulur() throws Exception {
        HttpServletRequest chunked = withoutContentLength(requestWithBody("x".repeat(LIMIT + 1)));
        AtomicReference<ServletInputStream> stream = new AtomicReference<>();

        filter.doFilter(chunked, new MockHttpServletResponse(),
                (req, res) -> stream.set(req.getInputStream()));

        ServletInputStream limited = stream.get();

        assertThatThrownBy(limited::readAllBytes).isInstanceOf(PayloadTooLargeException.class);
        verifyNoInteractions(resolver);
    }

    @Test
    void sinirIcindekiGovdeAkisVeOkuyucuIleOkunur() throws Exception {
        String body = "{\"a\":1}";
        AtomicReference<String> read = new AtomicReference<>();

        filter.doFilter(withoutContentLength(requestWithBody(body)), new MockHttpServletResponse(), (req, res) -> {
            try (BufferedReader reader = ((HttpServletRequest) req).getReader()) {
                read.set(reader.readLine());
            }
            ServletInputStream input = req.getInputStream();
            assertThat(input.isFinished()).isTrue();
            assertThat(input.isReady()).isTrue();
            assertThat(input.read()).isEqualTo(-1);
        });

        assertThat(read.get()).isEqualTo(body);
    }

    private static MockHttpServletRequest requestWithBody(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static HttpServletRequest withoutContentLength(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
    }
}
