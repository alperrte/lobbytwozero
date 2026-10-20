package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider.*;
import com.ltz.game.domain.exception.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

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
        assertEquals("PROVIDER_NOT_CONFIGURED", assertThrows(ProviderException.class,
                () -> disabled.catalog(new CatalogQuery(0,null,10))).code()); server.verify();
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,429,503})
    void httpErrorsHaveSafeCodes(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        var exception = assertThrows(ProviderException.class, () -> provider.catalog(new CatalogQuery(0,null,10)));
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
        var exception = assertThrows(ProviderException.class, () -> provider.catalog(new CatalogQuery(0,null,10)));
        assertEquals("PROVIDER_TRANSPORT_ERROR", exception.code());assertNull(exception.getCause());
        assertFalse(exception.toString().contains(TEST_KEY));
    }
    @ParameterizedTest @ValueSource(strings={
        "{}", "{\"response\":{}}", "{\"response\":{\"apps\":[],\"have_more_results\":true}}",
        "{\"response\":{\"apps\":[{\"appid\":1,\"name\":\" \",\"last_modified\":1}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":0,\"name\":\"A\",\"last_modified\":1}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":1,\"name\":\"A\"}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[],\"last_appid\":3,\"have_more_results\":false}}"
    })
    void malformedAndNonProgressingResponsesAreRejected(String body) {
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class,
                () -> provider.catalog(new CatalogQuery(0,null,10))).code());
    }
    @Test void emptyFinalPageIsValid() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[],\"have_more_results\":false}}",MediaType.APPLICATION_JSON));
        var page=provider.catalog(new CatalogQuery(10,null,10));assertEquals(10,page.nextAppId());assertFalse(page.hasMore());
    }
    @Test void invalidQueryIsRejectedBeforeRequest() {
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(-1,null,10));
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(0,null,1001));
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(0,Instant.ofEpochSecond(-1),10));
    }
    @Test void immediateRepeatIsRateLimited() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[],\"have_more_results\":false}}",MediaType.APPLICATION_JSON));
        provider.catalog(new CatalogQuery(0,null,10));
        assertEquals("RATE_LIMITED",assertThrows(ProviderException.class,()->provider.catalog(new CatalogQuery(0,null,10))).code());
    }
}
