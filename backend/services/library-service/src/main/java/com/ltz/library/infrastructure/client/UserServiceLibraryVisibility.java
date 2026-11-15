package com.ltz.library.infrastructure.client;

import com.ltz.library.application.port.LibraryVisibilityPort;
import com.ltz.library.domain.enums.LibraryAccessDecision;
import com.ltz.library.domain.enums.LibraryAccessPurpose;
import com.ltz.library.infrastructure.client.dto.request.PrivacyDecisionRequest;
import com.ltz.library.infrastructure.client.dto.response.PrivacyDecisionResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * User gizlilik kararı ({@code POST /internal/v1/privacy/decisions}, scope {@code privacy:read}). Karar User'a aittir, burada
 * kopyalanmaz ve önbelleğe alınmaz. PROFILE → {@code field=LIBRARY}; RECOMMENDATION → {@code field=RECOMMENDATION}, görüntüleyen
 * sahibin kendisidir (User'da sahiplik kısayolu yoktur, açık rıza aranır). Yalnız {@code allowed} okunur.
 *
 * <p>Fail-closed: adres veya token yoksa, hata kodu, zaman aşımı, bozuk yanıt ve eksik/yinelenen/yabancı sahip
 * {@link LibraryAccessDecision#UNAVAILABLE} olur; hiçbiri izin sayılmaz. Retry yoktur. Token ve kimlik günlüğe yazılmaz.
 */
@Component
public class UserServiceLibraryVisibility implements LibraryVisibilityPort {
    static final String LIBRARY_FIELD = "LIBRARY";
    static final String RECOMMENDATION_FIELD = "RECOMMENDATION";

    private final RestClient client;
    private final AuthServiceTokenClient tokens;
    private final String decisionsPath;

    public UserServiceLibraryVisibility(RestClient.Builder builder, AuthServiceTokenClient tokens,
            @Value("${ltz.library.user.base-url:}") String baseUrl,
            @Value("${ltz.library.user.decisions-path:/internal/v1/privacy/decisions}") String decisionsPath) {
        this.client = baseUrl == null || baseUrl.isBlank() ? null : builder.baseUrl(baseUrl.strip()).build();
        this.tokens = tokens;
        this.decisionsPath = decisionsPath;
    }

    @Override
    public LibraryAccessDecision decide(UUID owner, UUID viewer, LibraryAccessPurpose purpose) {
        if (client == null || owner == null || viewer == null || purpose == null) return LibraryAccessDecision.UNAVAILABLE;
        if (purpose == LibraryAccessPurpose.RECOMMENDATION && !owner.equals(viewer)) return LibraryAccessDecision.DENY;
        Optional<String> token = tokens.privacyDecisionToken();
        if (token.isEmpty()) return LibraryAccessDecision.UNAVAILABLE;
        String field = purpose == LibraryAccessPurpose.RECOMMENDATION ? RECOMMENDATION_FIELD : LIBRARY_FIELD;
        try {
            var result = client.post().uri(decisionsPath).contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token.get()))
                    .body(new PrivacyDecisionRequest(viewer, field, List.of(owner)))
                    .retrieve().toEntity(PrivacyDecisionResponse.class);
            if (result.getStatusCode().value() != 200) return LibraryAccessDecision.UNAVAILABLE;
            return interpret(result.getBody(), owner);
        } catch (RestClientException _) {
            return LibraryAccessDecision.UNAVAILABLE;
        }
    }

    /** Tam olarak istenen sahip için tek ve açık bir {@code allowed} değeri yoksa karar alınamamıştır. */
    private static LibraryAccessDecision interpret(PrivacyDecisionResponse body, UUID owner) {
        if (body == null || body.decisions() == null || body.decisions().size() != 1) return LibraryAccessDecision.UNAVAILABLE;
        var decision = body.decisions().getFirst();
        if (decision == null || !owner.equals(decision.ownerId()) || decision.allowed() == null) return LibraryAccessDecision.UNAVAILABLE;
        boolean allowed = decision.allowed();
        return allowed ? LibraryAccessDecision.ALLOW : LibraryAccessDecision.DENY;
    }
}
