package com.ltz.messaging.infrastructure.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
 * JSON istek gövdesinin bayt üst sınırı (413 ProblemDetail). Bildirilen Content-Length sınırı aşıyorsa gövde hiç
 * okunmaz. Aksi halde gövdeden en fazla {@code limit + 1} bayt belleğe alınır: fazlası varsa (uzunluk bildirilmeyen
 * chunked gövde dahil) istek reddedilir, yoksa uygulama okunmuş baytlarla devam eder. Böylece sınırsız okuma olmaz.
 */
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final int limit;
    private final HandlerExceptionResolver resolver;

    public RequestBodySizeLimitFilter(long limit, HandlerExceptionResolver resolver) {
        this.limit = Math.toIntExact(limit);
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > limit) {
            reject(request, response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(limit + 1);
        if (body.length > limit) {
            reject(request, response);
            return;
        }
        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) {
        resolver.resolveException(request, response, null, new ContentTooLargeException(null));
    }

    /** Önceden okunmuş gövdeyi uygulamaya yeniden sunan istek sarmalayıcısı. */
    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new BufferedServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            String declared = getCharacterEncoding();
            Charset charset = declared == null ? StandardCharsets.UTF_8 : Charset.forName(declared);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    /** Bellekteki gövdeyi okuyan akış; kapatılacak bir kaynak tutmaz. */
    private static final class BufferedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream source;

        BufferedServletInputStream(byte[] body) {
            this.source = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return source.read();
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            return source.read(target, offset, length);
        }

        @Override
        public boolean isFinished() {
            return source.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("Asynchronous reads are not supported.");
        }
    }
}
