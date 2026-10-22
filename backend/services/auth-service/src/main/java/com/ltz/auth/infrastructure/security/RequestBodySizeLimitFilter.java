package com.ltz.auth.infrastructure.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.PayloadTooLargeException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * İstek gövdesini üst sınırla korur. Content-Length bildirilmişse gövde okunmadan 413 döner;
 * bildirilmemişse (chunked) okuma sırasında sınır aşıldığında ayrıştırma durdurulur.
 */
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final HandlerExceptionResolver exceptionResolver;

    public RequestBodySizeLimitFilter(long maxBodyBytes, HandlerExceptionResolver exceptionResolver) {
        this.maxBodyBytes = maxBodyBytes;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            exceptionResolver.resolveException(request, response, null, new PayloadTooLargeException(null));
            return;
        }
        chain.doFilter(new BoundedBodyRequest(request, maxBodyBytes), response);
    }

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {

        private final long maxBodyBytes;
        private ServletInputStream boundedStream;

        BoundedBodyRequest(HttpServletRequest request, long maxBodyBytes) {
            super(request);
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (boundedStream == null) {
                boundedStream = new BoundedServletInputStream(super.getInputStream(), maxBodyBytes);
            }
            return boundedStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class BoundedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBodyBytes;
        private long bytesRead;

        BoundedServletInputStream(ServletInputStream delegate, long maxBodyBytes) {
            this.delegate = delegate;
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void count(int read) {
            bytesRead += read;
            if (bytesRead > maxBodyBytes) {
                throw new PayloadTooLargeException(null);
            }
        }
    }
}
