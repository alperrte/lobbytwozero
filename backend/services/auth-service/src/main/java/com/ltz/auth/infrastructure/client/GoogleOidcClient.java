package com.ltz.auth.infrastructure.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.ltz.auth.application.port.ExternalAuthenticationException;
import com.ltz.auth.application.port.GoogleOidcPort;
import com.ltz.auth.infrastructure.config.OAuthProperties;

/**
 * Authorization Code + PKCE: kodu token uç noktasında client secret ve code_verifier ile değiştirir, id_token'ı
 * Google JWKS'iyle doğrular (yalnız RS256; iss, aud, exp/iat, nonce, sub zorunlu). Hata ayrıntıları dışarı verilmez.
 */
public class GoogleOidcClient implements GoogleOidcPort {

    private static final String NONCE_CLAIM = "nonce";
    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";
    private static final String ID_TOKEN = "id_token";

    private final OAuthProperties.Google google;
    private final RestClient restClient;
    private final NimbusJwtDecoder decoder;

    public GoogleOidcClient(OAuthProperties.Google google, ClientHttpRequestFactory requestFactory, Clock clock) {
        this.google = google;
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(google.jwksUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                timestamps,
                new JwtIssuerValidator(google.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(google.clientId())),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank()),
                new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull));
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        // JWKS önbelleği bu decoder örneğinde yaşar; bu yüzden tek örnek kullanılır.
        this.decoder = nimbus;
    }

    @Override
    public GoogleIdentity authenticate(String code, String codeVerifier, String redirectUri, String expectedNonce) {
        String idToken = exchangeCode(code, codeVerifier, redirectUri);
        Jwt jwt = decode(idToken, expectedNonce);
        String subject = jwt.getSubject();
        return new GoogleIdentity(subject, jwt.getClaimAsString(EMAIL_CLAIM), isTrue(jwt.getClaim(EMAIL_VERIFIED_CLAIM)));
    }

    private String exchangeCode(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", google.clientId());
        form.add("client_secret", google.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        try {
            Map<String, Object> response = restClient.post().uri(google.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || !(response.get(ID_TOKEN) instanceof String idToken) || idToken.isBlank()) {
                throw new ExternalAuthenticationException("Google token response has no id_token");
            }
            return idToken;
        } catch (RestClientException exception) {
            // Sağlayıcı yanıt gövdesi (hata ayrıntıları) taşınmaz.
            throw new ExternalAuthenticationException("Google token exchange failed: "
                    + exception.getClass().getSimpleName());
        }
    }

    private Jwt decode(String idToken, String expectedNonce) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException exception) {
            throw new ExternalAuthenticationException("Google id_token rejected: "
                    + exception.getClass().getSimpleName());
        }
        // Nonce istek başınadır; sabit zamanlı karşılaştırma.
        String nonce = jwt.getClaimAsString(NONCE_CLAIM);
        if (nonce == null || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                expectedNonce.getBytes(StandardCharsets.UTF_8))) {
            throw new ExternalAuthenticationException("Google id_token nonce mismatch");
        }
        return jwt;
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }
}
