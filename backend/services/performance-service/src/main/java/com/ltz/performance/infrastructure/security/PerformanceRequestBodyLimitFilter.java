package com.ltz.performance.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Performance mutasyonları küçük JSON zarflarıdır; toplu yükleme bu katmanda kabul edilmez. Tek istisna örnek
 * taşıyan senkronizasyon ucudur (en çok 500 örnek, ~100 bayt/örnek): 64 KiB.
 */
final class PerformanceRequestBodyLimitFilter extends OncePerRequestFilter {
    static final int MAX_BYTES = 16 * 1024;
    static final int MAX_SYNC_BYTES = 64 * 1024;
    private final ObjectMapper mapper;

    PerformanceRequestBodyLimitFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        boolean performancePath = path.startsWith("/api/v1/performance/");
        return !performancePath || !(request.getMethod().equals("POST") || request.getMethod().equals("PUT")
                || request.getMethod().equals("DELETE"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        int limit = isSyncPath(path) ? MAX_SYNC_BYTES : MAX_BYTES;
        byte[] body = request.getInputStream().readNBytes(limit + 1);
        if (body.length > limit) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                    "İstek gövdesi en fazla " + (limit / 1024) + " KiB olabilir.");
            problem.setProperty("code", "REQUEST_TOO_LARGE");
            response.setStatus(413);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "private, no-store");
            response.getWriter().write(mapper.writeValueAsString(problem));
            return;
        }
        chain.doFilter(new BufferedRequest(request, body), response);
    }

    private static boolean isSyncPath(String path) {
        return path.equals("/api/v1/performance/sync/batches");
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        BufferedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Bu API senkron JSON isteklerini destekler");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
