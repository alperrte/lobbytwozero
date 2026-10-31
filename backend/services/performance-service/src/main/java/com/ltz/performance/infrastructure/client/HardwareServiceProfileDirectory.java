package com.ltz.performance.infrastructure.client;

import com.ltz.performance.application.port.HardwareProfileDirectory;
import com.ltz.performance.domain.exception.ServiceDependencyUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Hardware Service HW-17 {@code POST /api/v1/hardware/profiles/batch}: owner-only; çağıranın kendi Bearer'ı
 * iletilir, Hardware sahip olmayan veya olmayan profil için 404 verir. Yalnızca sahiplik sorulur
 * (page 0, size 1). 404 dışındaki her belirsizlik kapalı başarısızlık olarak 503'e gider.
 * Hardware'in tüketici E2E'si DEFERRED_CONTRACT'tır; bu istemci o sözleşmeye göre yazılmıştır.
 */
@Component
public class HardwareServiceProfileDirectory implements HardwareProfileDirectory {
    private static final String UNAVAILABLE = ServiceDependencyUnavailableException.HARDWARE_SERVICE_UNAVAILABLE;
    private static final String BEARER_PREFIX = "Bearer ";

    private final BoundedJsonClient client;
    private final PerformanceClientProperties properties;
    private final ObjectMapper mapper;

    public HardwareServiceProfileDirectory(PerformanceClientProperties properties, ObjectMapper mapper) {
        this.client = new BoundedJsonClient(properties.connectTimeout(), properties.readTimeout());
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public boolean isOwnedProfile(String authorization, UUID profileId) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() <= BEARER_PREFIX.length()) {
            throw new ServiceDependencyUnavailableException(UNAVAILABLE);
        }
        var body = Map.of("profiles", List.of(Map.of("profileId", profileId, "page", 0, "size", 1)));
        try {
            var reply = client.post(properties.hardwareProfilesBatchUri(), body, authorization);
            if (reply.status() == 404) return false;
            if (reply.status() != 200) throw new ServiceDependencyUnavailableException(UNAVAILABLE);
            return returnsProfile(mapper.readTree(reply.body()), profileId);
        } catch (RestClientException | JacksonException | IllegalArgumentException _) {
            throw new ServiceDependencyUnavailableException(UNAVAILABLE);
        }
    }

    private static boolean returnsProfile(JsonNode root, UUID profileId) {
        JsonNode profiles = root == null ? null : root.get("profiles");
        if (profiles == null || !profiles.isArray() || profiles.size() != 1) {
            throw new ServiceDependencyUnavailableException(UNAVAILABLE);
        }
        JsonNode id = profiles.get(0).get("profileId");
        if (id == null || !id.isString() || !profileId.equals(UUID.fromString(id.asString()))) {
            throw new ServiceDependencyUnavailableException(UNAVAILABLE);
        }
        return true;
    }
}
