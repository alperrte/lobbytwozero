package com.ltz.performance.infrastructure.client;

import com.ltz.performance.application.port.AuthDeviceDirectory;
import com.ltz.performance.domain.exception.AuthServiceUnavailableException;
import com.ltz.performance.infrastructure.security.PerformanceAuthProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Auth Service {@code GET /api/v1/auth/devices} çağrısı. Çağıranın kendi Bearer'ı iletilir; Auth yalnız o hesabın
 * kullanılabilir cihazlarını döner, bu yüzden sahiplik ve iptal durumu Auth'un kararıdır. Yönlendirme izlenmez,
 * yanıt boyutu sınırlıdır ve her belirsizlik (zaman aşımı, 5xx, bozuk gövde) kapalı başarısızlık olarak 503'e gider.
 */
@Component
public class AuthServiceDeviceDirectory implements AuthDeviceDirectory {
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient client;
    private final PerformanceAuthProperties properties;
    private final ObjectMapper mapper;

    public AuthServiceDeviceDirectory(PerformanceAuthProperties properties, ObjectMapper mapper) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder().requestFactory(factory).build();
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public List<AuthDevice> listUsableDevices(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX) || authorization.length() <= BEARER_PREFIX.length()) {
            throw new AuthServiceUnavailableException();
        }
        try {
            byte[] body = client.get().uri(properties.devicesUri()).accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() != 200) throw new IOException("Auth devices unavailable");
                        byte[] bytes = response.getBody().readNBytes(MAX_BODY_BYTES + 1);
                        if (bytes.length > MAX_BODY_BYTES) throw new IOException("Auth devices response exceeds limit");
                        return bytes;
                    });
            return devices(mapper.readTree(body));
        } catch (RestClientException | JacksonException | IllegalArgumentException _) {
            throw new AuthServiceUnavailableException();
        }
    }

    private static List<AuthDevice> devices(JsonNode root) {
        if (root == null || !root.isArray()) throw new AuthServiceUnavailableException();
        var devices = new ArrayList<AuthDevice>();
        var ids = new HashSet<UUID>();
        for (JsonNode node : root) {
            JsonNode id = node.get("deviceId");
            if (id == null || !id.isString()) throw new AuthServiceUnavailableException();
            UUID deviceId = UUID.fromString(id.asString());
            if (!deviceId.toString().equalsIgnoreCase(id.asString()) || !ids.add(deviceId)) {
                throw new AuthServiceUnavailableException();
            }
            JsonNode label = node.get("label");
            devices.add(new AuthDevice(deviceId, label != null && label.isString() ? label.asString() : null));
        }
        return List.copyOf(devices);
    }
}
