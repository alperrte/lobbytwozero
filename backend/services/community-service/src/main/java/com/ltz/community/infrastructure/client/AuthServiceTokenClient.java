package com.ltz.community.infrastructure.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ltz.community.application.port.ServiceTokenPort;
import com.ltz.community.domain.exception.ServiceTokenUnavailableException;

/**
 * Auth Service'in {@code POST /internal/v1/service-tokens} ucundan (client credentials) hedefe bağlı servis token'ı alır.
 * Token 60 sn yaşar ve yalnız istenen hedefte geçer; hedef başına ayrı alınır. Aynı hedefe art arda çağrılarda yalnız
 * geçerlilik süresi dolmadan önce (güvenlik payıyla) kısa süre yeniden kullanılır, uzun süre önbelleğe alınmaz.
 * Gizli anahtar ortam değişkeninden gelir ({@code AUTH_COMMUNITY_SERVICE_CLIENT_SECRET}); boşsa istemci kapalıdır.
 * Hata iletilerine yanıt gövdesi, token ya da gizli anahtar yazılmaz.
 */
@Component
public class AuthServiceTokenClient implements ServiceTokenPort {

	/** Token bitimine bu kadar kala yeniden alınır (saat farkı ve ağ gecikmesi payı). */
	static final Duration REFRESH_MARGIN = Duration.ofSeconds(15);
	/** Auth'un token ömrü üst sınırı (60 sn); yanıt daha uzun süre bildirse de bundan fazla önbelleğe alınmaz. */
	static final Duration MAX_CACHE = Duration.ofSeconds(60);

	/** Auth'un yanıtı; yeni alanlar eklenirse kırılmasın diye bilinmeyen alanlar yok sayılır. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {
	}

	record TokenRequest(String clientId, String clientSecret, String audience) {
	}

	private record CachedToken(String value, Instant refreshAt) {
	}

	private final RestClient restClient;
	private final String tokenPath;
	private final String clientId;
	private final String clientSecret;
	private final Clock clock;
	private final ConcurrentMap<String, CachedToken> cache = new ConcurrentHashMap<>();

	public AuthServiceTokenClient(RestClient.Builder builder, Clock clock,
			@Value("${ltz.clients.auth-service.url}") String authUrl,
			@Value("${ltz.clients.auth-service.token-path:/internal/v1/service-tokens}") String tokenPath,
			@Value("${ltz.community.auth.client-id}") String clientId,
			@Value("${ltz.community.auth.client-secret:}") String clientSecret) {
		this.restClient = builder.clone().baseUrl(authUrl).build();
		this.tokenPath = tokenPath;
		this.clock = clock;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public boolean isConfigured() {
		return clientSecret != null && !clientSecret.isBlank();
	}

	@Override
	public String tokenFor(String audience) {
		if (!isConfigured()) {
			throw new ServiceTokenUnavailableException("The Auth client secret is not configured.");
		}
		Instant now = clock.instant();
		CachedToken cached = cache.get(audience);
		if (cached != null && now.isBefore(cached.refreshAt())) {
			return cached.value();
		}
		TokenResponse response = request(audience);
		if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
			throw new ServiceTokenUnavailableException("Auth returned no service token.");
		}
		Duration lifetime = Duration.ofSeconds(Math.max(0, response.expiresIn()));
		Duration usable = lifetime.compareTo(MAX_CACHE) > 0 ? MAX_CACHE : lifetime;
		if (usable.compareTo(REFRESH_MARGIN) > 0) {
			cache.put(audience, new CachedToken(response.accessToken(), now.plus(usable).minus(REFRESH_MARGIN)));
		} else {
			cache.remove(audience);
		}
		return response.accessToken();
	}

	private TokenResponse request(String audience) {
		try {
			return restClient.post()
					.uri(tokenPath)
					.contentType(MediaType.APPLICATION_JSON)
					.body(new TokenRequest(clientId, clientSecret, audience))
					.retrieve()
					.body(TokenResponse.class);
		} catch (RestClientException exception) {
			// İleti yalnız istisna türünü taşır (yanıt gövdesi yok); nedeni zincirde kalır, çağıranlar yalnız sınıf adını loglar.
			throw new ServiceTokenUnavailableException("Auth refused or could not issue a service token: "
					+ exception.getClass().getSimpleName(), exception);
		}
	}
}
