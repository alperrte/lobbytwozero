package com.ltz.library.infrastructure.client;

import com.ltz.library.infrastructure.client.dto.request.ServiceTokenRequest;
import com.ltz.library.infrastructure.client.dto.response.ServiceTokenResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Auth client-credentials ({@code POST /internal/v1/service-tokens}). Başarısızlık "token yok" demektir; çağıran fail-closed
 * davranır. Token hedef (aud) başına yalnız süresi dolmadan önce yeniden kullanılır, yanıt kapsamı beklenenle eşleşmezse atılır.
 * Gizli anahtar ve token günlüğe yazılmaz; retry yoktur.
 */
@Component
public class AuthServiceTokenClient {
    /** Library → User gizlilik kararı: hedef servis ve zorunlu kapsam (Auth talep-01 son karar). events:ingest olay içindir, bunun yerine geçmez. */
    public static final String USER_AUDIENCE = "user-service";
    public static final String PRIVACY_READ_SCOPE = "privacy:read";
    private static final Logger LOG = LoggerFactory.getLogger(AuthServiceTokenClient.class);
    private static final Pattern AUDIENCE = Pattern.compile("^[a-z0-9-]{1,32}$");
    private static final long MAX_TTL_SECONDS = 60;
    /** Önbellek, token bitmeden bu kadar önce yenilenir; alıcıdaki 30 sn saat toleransına yaslanılmaz. */
    private static final Duration RENEW_MARGIN = Duration.ofSeconds(10);

    private final RestClient client;
    private final String tokenPath;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private final Map<String, Cached> cache = new HashMap<>();

    @Autowired
    public AuthServiceTokenClient(RestClient.Builder builder,
            @Value("${ltz.library.auth.base-url:}") String baseUrl,
            @Value("${ltz.library.auth.service-token-path}") String tokenPath,
            @Value("${ltz.library.auth.client-id:library-service}") String clientId,
            @Value("${ltz.library.auth.client-secret:}") String clientSecret) {
        this(builder, baseUrl, tokenPath, clientId, clientSecret, Clock.systemUTC());
    }

    AuthServiceTokenClient(RestClient.Builder builder, String baseUrl, String tokenPath, String clientId, String clientSecret, Clock clock) {
        this.client = baseUrl.isBlank() ? null : builder.baseUrl(baseUrl).build();
        this.tokenPath = tokenPath;
        this.clientId = clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret.strip();
        this.clock = clock;
    }

    /** Servis kimliği ve Auth adresi tanımlı değilse hiç çağrı yapılmaz ve token verilmez. */
    public boolean configured() { return client != null && !clientSecret.isEmpty() && !clientId.isBlank(); }

    /** User'dan gizlilik kararı almak için {@code privacy:read} taşıyan token; alınamazsa boş (çağıran fail-closed davranır). */
    public Optional<String> privacyDecisionToken() { return token(USER_AUDIENCE, PRIVACY_READ_SCOPE); }

    /** {@code audience} için {@code requiredScope} taşıyan geçerli bir Bearer token; alınamazsa boş. */
    public synchronized Optional<String> token(String audience, String requiredScope) {
        if (!configured() || audience == null || !AUDIENCE.matcher(audience).matches() || requiredScope == null) return Optional.empty();
        String key = audience + ' ' + requiredScope;
        Cached cached = cache.get(key);
        if (cached != null && clock.instant().isBefore(cached.renewAt())) return Optional.of(cached.value());
        cache.remove(key);
        Optional<Cached> fresh = fetch(audience, requiredScope);
        fresh.ifPresent(value -> cache.put(key, value));
        return fresh.map(Cached::value);
    }

    private Optional<Cached> fetch(String audience, String requiredScope) {
        try {
            var result = client.post().uri(tokenPath).contentType(MediaType.APPLICATION_JSON)
                    .body(new ServiceTokenRequest(clientId, clientSecret, audience)).retrieve().toEntity(ServiceTokenResponse.class);
            if (result.getStatusCode().value() != 200) return reject("durum kodu");
            ServiceTokenResponse body = result.getBody();
            if (body == null || body.accessToken() == null || body.accessToken().isBlank() || !"Bearer".equalsIgnoreCase(body.tokenType())
                    || body.expiresIn() == null || body.expiresIn() < 1 || body.expiresIn() > MAX_TTL_SECONDS || body.scope() == null)
                return reject("yanıt biçimi");
            if (!Set.of(body.scope().split(" ")).contains(requiredScope)) return reject("kapsam");
            Instant renewAt = clock.instant().plusSeconds(body.expiresIn()).minus(RENEW_MARGIN);
            return Optional.of(new Cached(body.accessToken(), renewAt));
        } catch (RestClientException _) {
            return reject("bağlantı veya hata yanıtı");
        }
    }

    private static Optional<Cached> reject(String reason) {
        LOG.warn("Auth servis token'ı alınamadı: {}", reason);
        return Optional.empty();
    }

    private record Cached(String value, Instant renewAt) {
        @Override
        public String toString() { return "Cached[value=***, renewAt=" + renewAt + "]"; }
    }
}
