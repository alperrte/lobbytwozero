package com.ltz.social.infrastructure.client;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ltz.social.application.port.FriendsVisibility;
import com.ltz.social.application.port.FriendsVisibilityPort;
import com.ltz.social.application.port.PrivacyUnavailableException;
import com.ltz.social.application.port.ServiceTokenPort;
import com.ltz.social.domain.exception.ServiceTokenUnavailableException;
import com.ltz.social.infrastructure.config.ServiceClientProperties;

/**
 * Arkadaş listesi görünürlük tercihini user-service'in gizlilik kararı ucundan okur (SOC-12,
 * {@code POST /internal/v1/privacy/decisions}, {@code field=FRIENDS_LIST}). Çağrı Auth'tan alınan hedefe bağlı servis
 * token'ıyla yapılır ({@code aud=user-service}, {@code scope=privacy:read}, {@code sub=svc:social-service}).
 * <p>
 * Yalnız sahibin ayarı ({@code setting}) kullanılır; {@code viewerId} gönderilmez. Arkadaşlık ve engel kararı Social'ın
 * kendi verisiyle {@code FriendsListAccessPolicy}'de verilir; user-service'in projeksiyonu olay gecikmesiyle geride
 * kalabileceğinden onun {@code allowed} sonucuna güvenilmez. Tercih belirlenemeyen her durumda (servis kimliği yok,
 * token alınamadı, hata yanıtı, eksik ya da bilinmeyen karar) {@link PrivacyUnavailableException} fırlatılır; çağıran
 * fail-closed {@code PRIVATE} sayar. İletilere token ya da yanıt gövdesi yazılmaz.
 */
public class UserPrivacyClient implements FriendsVisibilityPort {

    /** user-service gizlilik kararı ucu: {@code /internal/v1/privacy/decisions}. */
    private static final String[] DECISIONS_SEGMENTS = {"internal", "v1", "privacy", "decisions"};
    static final String USER_AUDIENCE = "user-service";
    static final String FRIENDS_LIST_FIELD = "FRIENDS_LIST";

    record DecisionRequest(String field, List<UUID> ownerIds) {
    }

    record DecisionResponse(List<Decision> decisions) {
    }

    record Decision(UUID ownerId, String setting, boolean allowed, boolean friends) {
    }

    private final RestClient restClient;
    private final ServiceTokenPort serviceTokens;

    public UserPrivacyClient(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                             ServiceClientProperties properties) {
        this.restClient = builder.clone().baseUrl(properties.userServiceUrl()).build();
        this.serviceTokens = serviceTokens;
    }

    @Override
    public FriendsVisibility visibilityOf(UUID ownerId) {
        if (!serviceTokens.isConfigured()) {
            throw new PrivacyUnavailableException("Service identity is not configured");
        }
        DecisionResponse response = fetch(ownerId);
        String setting = response == null || response.decisions() == null ? null : response.decisions().stream()
                .filter(decision -> ownerId.equals(decision.ownerId()))
                .map(Decision::setting)
                .findFirst()
                .orElse(null);
        return parse(setting);
    }

    private DecisionResponse fetch(UUID ownerId) {
        String token;
        try {
            token = serviceTokens.tokenFor(USER_AUDIENCE);
        } catch (ServiceTokenUnavailableException exception) {
            throw new PrivacyUnavailableException("Service token is unavailable", exception);
        }
        try {
            return restClient.post()
                    .uri(uri -> uri.pathSegment(DECISIONS_SEGMENTS).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new DecisionRequest(FRIENDS_LIST_FIELD, List.of(ownerId)))
                    .retrieve()
                    .body(DecisionResponse.class);
        } catch (RestClientException exception) {
            throw new PrivacyUnavailableException("Privacy decision failed: " + exception.getClass().getSimpleName(),
                    exception);
        }
    }

    private static FriendsVisibility parse(String setting) {
        if (setting == null) {
            throw new PrivacyUnavailableException("Privacy decision is missing");
        }
        try {
            return FriendsVisibility.valueOf(setting);
        } catch (IllegalArgumentException exception) {
            throw new PrivacyUnavailableException("Privacy setting is not recognized", exception);
        }
    }
}
