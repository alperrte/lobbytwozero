package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.infrastructure.config.IcecatProperties;
import jakarta.annotation.PreDestroy;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Fixed official hosts, no redirects, capped streams and server-side token headers only. */
@Component
public class IcecatHttpClient {
    private static final Set<String> HOSTS = Set.of("data.icecat.biz", "live.icecat.biz", "images.icecat.biz",
            "images2.icecat.biz", "objects.icecat.biz");
    private final RestClient client;
    private final HttpClient http;
    private final IcecatProperties properties;
    private long nextRequest;
    public IcecatHttpClient(IcecatProperties properties) {
        this.properties = properties;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(60));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public <T> T get(URI uri, long maxBytes, boolean credentials, Function<InputStream, T> reader) {
        requireOfficial(uri);
        for (int attempt = 0; attempt < 3; attempt++) {
            pace();
            var result = client.get().uri(uri).headers(headers -> {
                if (credentials) {
                    headers.set("api-token", properties.getApiToken());
                    headers.set("content-token", properties.getContentToken());
                }
            }).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 429 || status >= 500) return new Result<T>(status, null);
                if (status != 200) throw new IllegalStateException("ICECAT_ACCESS_" + status);
                try (InputStream input = bounded(response.getBody(), maxBytes)) {
                    return new Result<>(status, reader.apply(input));
                }
            });
            if (result != null && result.status() == 200) return result.value();
            pause(500L << attempt);
        }
        throw new IllegalStateException("ICECAT_RETRY_EXHAUSTED");
    }

    public static void requireOfficial(URI uri) {
        if (!officialAuthority(uri)) {
            throw new IllegalArgumentException("ICECAT_UNSAFE_URL");
        }
        try {
            for (var address : InetAddress.getAllByName(uri.getHost())) {
                if (privateAddress(address)) {
                    throw new IllegalArgumentException("ICECAT_UNSAFE_ADDRESS");
                }
            }
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalStateException("ICECAT_DNS_UNAVAILABLE", exception);
        }
    }

    private static boolean officialAuthority(URI uri) {
        return "https".equals(uri.getScheme()) && HOSTS.contains(uri.getHost()) && uri.getUserInfo() == null
                && uri.getFragment() == null && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private static boolean privateAddress(InetAddress address) {
        var bytes = address.getAddress();
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isMulticastAddress()
                || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc);
    }

    @PreDestroy
    public void close() {
        http.close();
    }

    public static InputStream bounded(InputStream input, long maximum) {
        return new FilterInputStream(input) {
            private long count;
            private void counted(int size) throws IOException {
                if (size > 0 && (count += size) > maximum) throw new IOException("ICECAT_RESPONSE_LIMIT");
            }
            @Override public int read() throws IOException { int value = super.read(); if (value >= 0) counted(1); return value; }
            @Override public int read(byte[] data, int offset, int length) throws IOException {
                int size = in.read(data, offset, length); counted(size); return size;
            }
        };
    }

    private synchronized void pace() {
        long remaining = nextRequest - System.currentTimeMillis();
        if (remaining > 0) pause(remaining);
        nextRequest = System.currentTimeMillis() + 1000;
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ICECAT_INTERRUPTED", exception);
        }
    }
    private record Result<T>(int status, T value) { }
}
