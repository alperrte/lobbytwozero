package com.ltz.recommendation.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

final class RecommendationBodyLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    RecommendationBodyLimitFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!request.getMethod().equals("POST") && !request.getMethod().equals("PUT")) {
            chain.doFilter(request, response);
            return;
        }
        byte[] bytes = request.getInputStream().readNBytes(16385);
        if (bytes.length > 16384) {
            ProblemDetail body = ProblemDetail.forStatus(413);
            body.setProperty("code", "REQUEST_TOO_LARGE");
            response.setStatus(413);
            response.setContentType("application/problem+json");
            response.setHeader("Cache-Control", "private, no-store");
            mapper.writeValue(response.getOutputStream(), body);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
        }, response);
    }
}
