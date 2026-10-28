package com.ltz.auth.infrastructure.client;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ltz.auth.application.port.ExternalAuthenticationException;
import com.ltz.auth.application.port.SteamAssertionVerifier;
import com.ltz.auth.domain.exception.RedactedCause;
import com.ltz.auth.infrastructure.config.OAuthProperties;

/** OpenID 2.0 direct verification: alınan yanıt alanları mode=check_authentication ile Steam'e geri gönderilir. */
public class SteamOpenIdClient implements SteamAssertionVerifier {

    private static final String MODE = "openid.mode";
    private static final String CHECK_AUTHENTICATION = "check_authentication";
    private static final String IS_VALID_TRUE = "is_valid:true";

    private final OAuthProperties.Steam steam;
    private final RestClient restClient;

    public SteamOpenIdClient(OAuthProperties.Steam steam, ClientHttpRequestFactory requestFactory) {
        this.steam = steam;
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public boolean isValid(Map<String, String> openIdParams) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        openIdParams.forEach((name, value) -> form.add(name, MODE.equals(name) ? CHECK_AUTHENTICATION : value));
        try {
            String body = restClient.post().uri(steam.openIdEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return body != null && body.lines().map(String::strip).anyMatch(IS_VALID_TRUE::equals);
        } catch (RestClientException exception) {
            throw new ExternalAuthenticationException("Steam verification failed: "
                    + exception.getClass().getSimpleName(), RedactedCause.of(exception));
        }
    }
}
