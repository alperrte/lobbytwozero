package com.ltz.performance.infrastructure.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Servisler arası JSON POST: yönlendirme izlenmez, yanıt boyutu sınırlıdır, zaman aşımları kısadır. */
final class BoundedJsonClient {
    static final int MAX_BODY_BYTES = 64 * 1024;

    record Reply(int status, String body) { }

    private final RestClient client;

    BoundedJsonClient(Duration connectTimeout, Duration readTimeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /** 2xx dışı yanıtların gövdesi okunmaz; ağ hataları {@code RestClientException} olarak çağırana çıkar. */
    Reply post(URI uri, Object body, String authorization) {
        return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
                })
                .body(body)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status != 200) return new Reply(status, "");
                    byte[] bytes = response.getBody().readNBytes(MAX_BODY_BYTES + 1);
                    if (bytes.length > MAX_BODY_BYTES) throw new IOException("Response exceeds limit");
                    return new Reply(status, new String(bytes, StandardCharsets.UTF_8));
                });
    }
}
