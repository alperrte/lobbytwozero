package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider.Capability;
import com.ltz.game.application.port.GameProvider.CatalogQuery;
import com.ltz.game.domain.exception.ProviderException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SteamGameProviderTest {
    // Sabit test girdisi; gerçek anahtar değildir.
    private static final String TEST_KEY = "synthetic-test-value";
    MockRestServiceServer server;
    SteamGameProvider provider;
    @BeforeEach void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new SteamGameProvider(builder.build(), TEST_KEY, true,
                new SteamRequestBudget(Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC)));
    }
    @Test void capabilitiesAreExplicitAndUnavailableFeaturesFail() {
        assertEquals("STEAM", provider.provider());
        assertTrue(provider.capabilities().contains(Capability.CATALOG));
        assertTrue(provider.capabilities().contains(Capability.INCREMENTAL_CATALOG));
        provider.require(Capability.CATALOG);
        assertEquals("UNSUPPORTED_CAPABILITY", assertThrows(ProviderException.class,
                () -> provider.require(Capability.DETAILS)).code());
        assertThrows(ProviderException.class, () -> provider.require(Capability.SEARCH));
    }
    @Test void authorizedPagedRequestUsesDocumentedInputAndNormalizesResponse() {
        server.expect(method(HttpMethod.GET)).andExpect(request -> {
            assertEquals(TEST_KEY, request.getHeaders().getFirst("x-webapi-key"));
            assertFalse(request.getURI().toString().contains(TEST_KEY));
            assertEquals("api.steampowered.com", request.getURI().getHost());
            assertEquals("/IStoreService/GetAppList/v1/", request.getURI().getPath());
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("\"last_appid\":10"));
            assertTrue(query.contains("\"max_results\":2"));
            assertTrue(query.contains("\"if_modified_since\":100"));
            assertTrue(query.contains("\"include_dlc\":false"));
        }).andRespond(withSuccess("""
                {"response":{"apps":[{"appid":20,"name":" Example ","last_modified":200,"unknown":true,"type":"game"}],
                "last_appid":20,"have_more_results":true}}
                """, MediaType.APPLICATION_JSON));
        var result = provider.catalog(new CatalogQuery(10, Instant.ofEpochSecond(100), 2));
        assertEquals("20", result.items().getFirst().externalId());
        assertEquals("Example", result.items().getFirst().title());
        assertNull(result.items().getFirst().providerType());
        assertFalse(provider.capabilities().contains(Capability.ITEM_TYPE));
        assertEquals(Instant.ofEpochSecond(200), result.items().getFirst().lastModified());
        assertEquals(20, result.nextAppId()); assertTrue(result.hasMore()); server.verify();
    }
    @Test void missingConfigurationNeverCallsNetwork() {
        var disabled = new SteamGameProvider(RestClient.create(), "", false, new SteamRequestBudget(Clock.systemUTC()));
        var query = new CatalogQuery(0,null,10);
        assertEquals("PROVIDER_NOT_CONFIGURED", assertThrows(ProviderException.class,
                () -> disabled.catalog(query)).code()); server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"120","Wed, 21 Oct 2099 00:00:00 GMT","invalid"})
    void retryAfterIsParsedWithoutLeakingProviderResponse(String retryAfter) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", retryAfter));
        var query = new CatalogQuery(0,null,10);
        var error = assertThrows(ProviderException.class, () -> provider.catalog(query));
        assertEquals("RATE_LIMITED",error.code());
        if (retryAfter.equals("120")) assertEquals(Duration.ofSeconds(120),error.retryAfter());
        else if (retryAfter.equals("invalid")) assertNull(error.retryAfter());
        else assertNotNull(error.retryAfter());
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,429,503})
    void httpErrorsHaveSafeCodes(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        var query = new CatalogQuery(0,null,10);
        var exception = assertThrows(ProviderException.class, () -> provider.catalog(query));
        String expected = switch(status) {
            case 401,403 -> "PROVIDER_ACCESS_DENIED";
            case 429 -> "RATE_LIMITED";
            case 503 -> "PROVIDER_UNAVAILABLE";
            default -> "PROVIDER_REQUEST_REJECTED";
        };
        assertEquals(expected,exception.code());assertNull(exception.getCause());
        assertFalse(exception.toString().contains(TEST_KEY)); server.verify();
    }
    @Test void transportFailureDoesNotExposeUriOrCause() {
        server.expect(anything()).andRespond(withException(new IOException("secret URI="+TEST_KEY)));
        var query = new CatalogQuery(0,null,10);
        var exception = assertThrows(ProviderException.class, () -> provider.catalog(query));
        assertEquals("PROVIDER_TRANSPORT_ERROR", exception.code());assertNull(exception.getCause());
        assertFalse(exception.toString().contains(TEST_KEY));
    }
    @ParameterizedTest @ValueSource(strings={
        "{}", "{\"response\":{}}", "{\"response\":{\"apps\":[],\"have_more_results\":true}}",
        "{\"response\":{\"apps\":[{\"appid\":1,\"name\":\" \",\"last_modified\":1}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":0,\"name\":\"A\",\"last_modified\":1}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":1,\"name\":\"A\"}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[],\"last_appid\":3,\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[null],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":4294967296,\"name\":\"A\",\"last_modified\":1}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":1,\"name\":\"A\",\"last_modified\":-1}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":1,\"name\":\"A\",\"last_modified\":4294967296}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":1,\"name\":\"A\",\"last_modified\":1},{\"appid\":1,\"name\":\"B\",\"last_modified\":2}],\"have_more_results\":false}}"
    })
    void malformedAndNonProgressingResponsesAreRejected(String body) {
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0,null,10);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class,
                () -> provider.catalog(query)).code());
    }
    @Test void emptyFinalPageIsValid() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[],\"have_more_results\":false}}",MediaType.APPLICATION_JSON));
        var page=provider.catalog(new CatalogQuery(10,null,10));assertEquals(10,page.nextAppId());assertFalse(page.hasMore());
    }
    @Test void unsignedUpperBoundsPreserveCursorAndNormalizedItem() {
        server.expect(anything()).andRespond(withSuccess("""
                {"response":{"apps":[{"appid":4294967295,"name":" Last ","last_modified":4294967295}],
                "last_appid":4294967295,"have_more_results":false}}
                """, MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(4294967294L, null, 1));
        assertEquals(4294967295L, page.nextAppId());
        assertEquals("4294967295", page.items().getFirst().externalId());
        assertEquals("Last", page.items().getFirst().title());
        assertEquals(Instant.ofEpochSecond(4294967295L), page.items().getFirst().lastModified());
        assertFalse(page.hasMore());
    }
    @Test void invalidQueryIsRejectedBeforeRequest() {
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(-1,null,10));
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(0,null,1001));
        var negativeTime = Instant.ofEpochSecond(-1);
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(0,negativeTime,10));
    }
    @Test void immediateRepeatIsRateLimited() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[],\"have_more_results\":false}}",MediaType.APPLICATION_JSON));
        provider.catalog(new CatalogQuery(0,null,10));
        var query = new CatalogQuery(0,null,10);
        assertEquals("RATE_LIMITED",assertThrows(ProviderException.class,()->provider.catalog(query)).code());
    }
}
