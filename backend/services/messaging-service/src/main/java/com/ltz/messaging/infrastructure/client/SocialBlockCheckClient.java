package com.ltz.messaging.infrastructure.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ltz.messaging.application.port.BlockCheckPort;
import com.ltz.messaging.application.port.BlockCheckUnavailableException;
import com.ltz.messaging.application.port.ServiceTokenPort;
import com.ltz.messaging.application.port.ServiceTokenUnavailableException;
import com.ltz.messaging.infrastructure.config.ServiceClientProperties;

/**
 * social-service'in {@code POST /internal/v1/blocks/check} ucu (docs/api.md → SOCIAL → Engel sorgusu). Her çağrı
 * Auth'tan alınan {@code aud=social-service}, {@code scope=blocks:check} servis token'ıyla yapılır. Yanıt eksik ya da
 * tutarsızsa sonuç güvenilmez sayılır ve {@link BlockCheckUnavailableException} atılır; engel varmış gibi ya da yokmuş
 * gibi tahmin yapılmaz. Zaman aşımı ve yönlendirme ayarları {@code spring.http.clients.*} ile gelir; istek otomatik
 * tekrarlanmaz.
 */
public class SocialBlockCheckClient implements BlockCheckPort {

    static final String BLOCKS_CHECK_PATH = "/internal/v1/blocks/check";
    static final String SOCIAL_AUDIENCE = "social-service";
    /** Social'in tek çağrıda kabul ettiği en fazla kimlik. */
    static final int MAX_USERS_PER_CALL = 100;

    record CheckRequest(UUID userId, List<UUID> otherUserIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CheckResponse(List<Result> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(UUID userId, Boolean blocked) {
    }

    private final RestClient restClient;
    private final ServiceTokenPort serviceTokens;

    public SocialBlockCheckClient(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                  ServiceClientProperties properties) {
        this.restClient = builder.clone().baseUrl(properties.socialServiceUrl()).build();
        this.serviceTokens = serviceTokens;
    }

    @Override
    public Set<UUID> blockedAmong(UUID userId, Collection<UUID> otherUsers) {
        List<UUID> others = List.copyOf(new LinkedHashSet<>(otherUsers));
        if (others.isEmpty() || others.size() > MAX_USERS_PER_CALL) {
            throw new IllegalArgumentException("Between 1 and " + MAX_USERS_PER_CALL + " users can be checked at once.");
        }
        CheckResponse response = call(new CheckRequest(userId, others));
        return blockedFrom(response, others);
    }

    private CheckResponse call(CheckRequest request) {
        try {
            String token = serviceTokens.tokenFor(SOCIAL_AUDIENCE);
            return restClient.post()
                    .uri(BLOCKS_CHECK_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CheckResponse.class);
        } catch (ServiceTokenUnavailableException exception) {
            throw new BlockCheckUnavailableException("No service token for social-service.", exception);
        } catch (RestClientException exception) {
            throw new BlockCheckUnavailableException("Social block check failed: "
                    + exception.getClass().getSimpleName(), exception);
        }
    }

    /** Her sorulan kimlik için tam bir sonuç bekler; eksik, fazla ya da boş sonuç kabul edilmez. */
    private static Set<UUID> blockedFrom(CheckResponse response, List<UUID> asked) {
        if (response == null || response.results() == null) {
            throw new BlockCheckUnavailableException("Social returned no block check results.");
        }
        Set<UUID> answered = new HashSet<>();
        Set<UUID> blocked = new HashSet<>();
        for (Result result : response.results()) {
            if (result == null || result.userId() == null || result.blocked() == null) {
                throw new BlockCheckUnavailableException("Social returned an incomplete block check result.");
            }
            answered.add(result.userId());
            if (Boolean.TRUE.equals(result.blocked())) {
                blocked.add(result.userId());
            }
        }
        if (!answered.equals(Set.copyOf(asked))) {
            throw new BlockCheckUnavailableException("Social block check results do not match the request.");
        }
        return blocked;
    }
}
