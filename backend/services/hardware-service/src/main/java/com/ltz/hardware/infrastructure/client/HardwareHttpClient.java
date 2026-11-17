package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.infrastructure.security.HardwareAuthProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Sınırlı yanıt, timeout ve sabit hedef; yönlendirme ve otomatik retry yoktur. */
@Component
public class HardwareHttpClient {
    private static final int MAX_BODY_BYTES = 65536;
    private final RestClient client;

    public HardwareHttpClient(HardwareAuthProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(properties.readTimeout());
        client = RestClient.builder().requestFactory(factory).build();
    }

    byte[] post(URI uri, Object body, String authorization) {
        return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (authorization != null) headers.set(HttpHeaders.AUTHORIZATION, authorization);
                }).body(body).exchange((request, response) -> readJson(response));
    }

    byte[] get(URI uri, String authorization) {
        return client.get().uri(uri).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange((request, response) -> readJson(response));
    }

    private static byte[] readJson(ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().value() != 200) throw new IOException("Service response unavailable");
        if (response.getHeaders().getContentType() == null
                || !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())) {
            throw new IOException("Invalid service response type");
        }
        byte[] bytes = response.getBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IOException("Service response exceeds limit");
        return bytes;
    }

    boolean activeDevice(URI uri, String authorization) {
        return client.get().uri(uri).header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange((request, response) -> response.getStatusCode().value() == 204);
    }
}
