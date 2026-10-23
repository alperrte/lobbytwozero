package com.ltz.auth.infrastructure.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.application.port.AccountDataEraserPort;
import com.ltz.auth.application.port.AccountErasureException;
import com.ltz.auth.infrastructure.config.DeletionProperties;
import com.ltz.auth.infrastructure.security.ServiceTokenIssuer;
import com.ltz.auth.infrastructure.security.TokenClaims;

import lombok.extern.slf4j.Slf4j;

/**
 * Auth → hedef servis hesap verisi silme (AUTH-16/17, D4):
 * {@code DELETE <SERVİS>_SERVICE_URL/internal/v1/accounts/{accountId}}, her çağrıda yeni servis token'ıyla
 * ({@code typ=svc+jwt}, {@code aud=<servis>-service}, {@code scope=account:erase}, {@code account_id}).
 * <p>
 * Silme yalnız {@code 200} veya {@code 204} ile onaylanmış sayılır. {@code 202} ("sonra silerim"), {@code 3xx},
 * {@code 404} (uç yok / yanlış adres), diğer tüm kodlar, zaman aşımı ve bağlantı hatası onay DEĞİLDİR: görev PENDING
 * kalır ve geri çekilmeyle yeniden denenir. Yönlendirme izlenmez (token başka adrese gitmez); yanıt gövdesi okunmaz;
 * token ve hesap verisi loglanmaz.
 */
@Slf4j
@Component
public class HttpAccountDataEraser implements AccountDataEraserPort {

    /** Sözleşme yolu {@code /internal/v1/accounts/{accountId}}; bilerek yapılandırılamaz (güvenlik tabanı). */
    private static final String[] ERASURE_PATH_SEGMENTS = {"internal", "v1", "accounts"};
    static final String NOT_CONFIGURED = "SERVICE_ERASURE_NOT_CONFIGURED";
    static final String UNREACHABLE = "SERVICE_ERASURE_UNREACHABLE";
    static final String FAILED = "SERVICE_ERASURE_FAILED";
    static final String HTTP_STATUS_PREFIX = "SERVICE_ERASURE_HTTP_";
    private static final Set<HttpStatusCode> CONFIRMED = Set.of(HttpStatus.OK, HttpStatus.NO_CONTENT);
    private static final String BEARER = "Bearer ";

    private final RestClient restClient;
    private final ServiceTokenIssuer serviceTokens;
    private final Map<String, URI> serviceUrls;

    public HttpAccountDataEraser(DeletionProperties properties, ServiceTokenIssuer serviceTokens) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.httpTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.httpTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.serviceTokens = serviceTokens;
        this.serviceUrls = Map.copyOf(properties.serviceUrls());
    }

    @Override
    public void erase(String service, UUID requestId, UUID accountId) {
        URI baseUrl = serviceUrls.get(service);
        if (baseUrl == null) {
            throw failure(service, requestId, NOT_CONFIGURED);
        }
        String token = serviceTokens.issue(ServiceTokenIssuer.audienceFor(service), TokenClaims.ACCOUNT_ERASE_SCOPE,
                accountId);
        HttpStatusCode status;
        try {
            status = restClient.delete()
                    .uri(erasureUri(baseUrl, accountId))
                    .header(HttpHeaders.AUTHORIZATION, BEARER + token)
                    .exchangeForRequiredValue((request, response) -> response.getStatusCode());
        } catch (ResourceAccessException _) {
            throw failure(service, requestId, UNREACHABLE);
        } catch (RestClientException _) {
            throw failure(service, requestId, FAILED);
        }
        if (!CONFIRMED.contains(status)) {
            throw failure(service, requestId, HTTP_STATUS_PREFIX + status.value());
        }
    }

    /** Taban adres açılışta yalnız şema + host (+ port) olarak doğrulanır; yol buradan eklenir. */
    private static URI erasureUri(URI baseUrl, UUID accountId) {
        return UriComponentsBuilder.newInstance()
                .scheme(baseUrl.getScheme())
                .host(baseUrl.getHost())
                .port(baseUrl.getPort())
                .pathSegment(ERASURE_PATH_SEGMENTS)
                .pathSegment(accountId.toString())
                .build()
                .encode()
                .toUri();
    }

    private static AccountErasureException failure(String service, UUID requestId, String code) {
        log.debug("Account erasure not confirmed service={} requestId={} code={}", service, requestId, code);
        return new AccountErasureException(code);
    }
}
