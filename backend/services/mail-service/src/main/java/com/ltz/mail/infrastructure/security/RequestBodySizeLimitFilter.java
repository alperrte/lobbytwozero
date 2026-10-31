package com.ltz.mail.infrastructure.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ContentTooLargeException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * İstek gövdesinin bayt üst sınırı. Bildirilen Content-Length sınırı aşıyorsa gövde okunmadan reddedilir; uzunluk
 * bildirilmeyen (chunked) gövdede okuma bütçesi tükenince ayrıştırma durdurulur. İkisi de 413 ProblemDetail döner.
 */
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final long limit;
    private final HandlerExceptionResolver resolver;

    public RequestBodySizeLimitFilter(long limit, HandlerExceptionResolver resolver) {
        this.limit = limit;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > limit) {
            resolver.resolveException(request, response, null, new ContentTooLargeException(null));
            return;
        }
        chain.doFilter(new LimitedRequest(request, limit), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedStream(super.getInputStream(), limit);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String declared = getCharacterEncoding();
            Charset charset = declared == null ? StandardCharsets.UTF_8 : Charset.forName(declared);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedStream extends ServletInputStream {

        private final ServletInputStream source;
        private long budget;

        LimitedStream(ServletInputStream source, long budget) {
            this.source = source;
            this.budget = budget;
        }

        @Override
        public int read() throws IOException {
            int next = source.read();
            if (next >= 0) {
                spend(1);
            }
            return next;
        }

        @Override
        public int read(byte[] target, int from, int length) throws IOException {
            int count = source.read(target, from, length);
            if (count > 0) {
                spend(count);
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return source.isFinished();
        }

        @Override
        public boolean isReady() {
            return source.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            source.setReadListener(listener);
        }

        private void spend(long bytes) {
            budget -= bytes;
            if (budget < 0) {
                throw new ContentTooLargeException(null);
            }
        }
    }
}
