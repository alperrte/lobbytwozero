package com.ltz.hardware.infrastructure.security;

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

/** Hardware APIs accept one small record per mutation, never bulk uploads. */
final class HardwareRequestBodyLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 16 * 1024;
    private final ObjectMapper mapper;

    HardwareRequestBodyLimitFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        boolean hardwarePath = path.startsWith("/api/v1/hardware/");
        return !hardwarePath || !(request.getMethod().equals("POST") || request.getMethod().equals("PUT")
                || request.getMethod().equals("DELETE"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "İstek gövdesi en fazla 16 KiB olabilir.");
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
