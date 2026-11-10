package com.ltz.library.infrastructure.client;

import com.ltz.library.domain.enums.LibraryAccessDecision;
import com.ltz.library.domain.enums.LibraryAccessPurpose;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** User karar sözleşmesi; sahte HTTP (MockRestServiceServer), gerçek User değildir. */
class UserServiceLibraryVisibilityTest {
    private static final String URL = "http://user.test/internal/v1/privacy/decisions";
    private static final String TOKEN = "synthetic.privacy.token";
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VIEWER = UUID.randomUUID();
    private MockRestServiceServer server;
    private AuthServiceTokenClient tokens;
    private UserServiceLibraryVisibility adapter;

    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokens = Mockito.mock(AuthServiceTokenClient.class);
        when(tokens.privacyDecisionToken()).thenReturn(Optional.of(TOKEN));
        adapter = new UserServiceLibraryVisibility(builder, tokens, "http://user.test");
    }

    @Test void profileAsksForTheLibraryFieldWithTheVerifiedViewerAndMapsAllow() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json("{\"viewerId\":\"" + VIEWER + "\",\"field\":\"LIBRARY\",\"ownerIds\":[\"" + OWNER + "\"]}", JsonCompareMode.STRICT))
                .andRespond(decisions(OWNER, "true"));
        assertEquals(LibraryAccessDecision.ALLOW, adapter.decide(OWNER, VIEWER, LibraryAccessPurpose.PROFILE));
        server.verify();
    }

    @Test void profileMapsAllowedFalseToDenyEvenWhenSettingLooksPublic() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"decisions\":[{\"ownerId\":\"" + OWNER
                + "\",\"setting\":\"PUBLIC\",\"allowed\":false,\"friends\":false}]}", MediaType.APPLICATION_JSON));
        assertEquals(LibraryAccessDecision.DENY, adapter.decide(OWNER, VIEWER, LibraryAccessPurpose.PROFILE));
        server.verify();
    }

    @Test void recommendationAsksForTheRecommendationFieldWithTheOwnerAsViewer() {
        server.expect(requestTo(URL)).andExpect(content().json("{\"viewerId\":\"" + OWNER
                + "\",\"field\":\"RECOMMENDATION\",\"ownerIds\":[\"" + OWNER + "\"]}", JsonCompareMode.STRICT)).andRespond(decisions(OWNER, "true"));
        assertEquals(LibraryAccessDecision.ALLOW, adapter.decide(OWNER, OWNER, LibraryAccessPurpose.RECOMMENDATION));
        server.verify();
    }

    @Test void recommendationWithoutConsentIsDeniedNotUnavailable() {
        server.expect(requestTo(URL)).andRespond(decisions(OWNER, "false"));
        assertEquals(LibraryAccessDecision.DENY, adapter.decide(OWNER, OWNER, LibraryAccessPurpose.RECOMMENDATION));
        server.verify();
    }

    @Test void recommendationForAnotherViewerIsDeniedWithoutCallingUser() {
        server.expect(never(), requestTo(URL)).andRespond(withSuccess());
        assertEquals(LibraryAccessDecision.DENY, adapter.decide(OWNER, VIEWER, LibraryAccessPurpose.RECOMMENDATION));
        server.verify();
    }

    @Test void missingForeignDuplicateOrNonBooleanDecisionsAreUnavailable() {
        UUID other = UUID.randomUUID();
        for (String body : List.of("null", "{}", "{\"decisions\":null}", "{\"decisions\":[]}", "{\"decisions\":[null]}",
                "{\"decisions\":[{\"ownerId\":\"" + OWNER + "\"}]}",
                "{\"decisions\":[{\"ownerId\":\"" + OWNER + "\",\"allowed\":null}]}",
                "{\"decisions\":[{\"ownerId\":\"" + OWNER + "\",\"allowed\":\"yes\"}]}",
                "{\"decisions\":[{\"ownerId\":\"" + other + "\",\"allowed\":true}]}",
                "{\"decisions\":[{\"allowed\":true}]}",
                "{\"decisions\":[{\"ownerId\":\"" + OWNER + "\",\"allowed\":true},{\"ownerId\":\"" + OWNER + "\",\"allowed\":true}]}",
                "{\"decisions\":[{\"ownerId\":\"" + OWNER + "\",\"allowed\":true},{\"ownerId\":\"" + other + "\",\"allowed\":true}]}",
                "not json")) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(OWNER, VIEWER, LibraryAccessPurpose.PROFILE), body);
            server.verify();
        }
    }

    @Test void errorStatusesRedirectsAndTransportFailuresAreUnavailableAndNotRetried() {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND,
                HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.FOUND)) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withStatus(status));
            assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(OWNER, VIEWER, LibraryAccessPurpose.PROFILE), status.toString());
            server.verify();
        }
        server.reset();
        server.expect(requestTo(URL)).andRespond(withException(new IOException("timeout")));
        assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(OWNER, VIEWER, LibraryAccessPurpose.PROFILE));
        server.verify();
    }

    @Test void noServiceTokenMeansNoCallAndNoAccess() {
        when(tokens.privacyDecisionToken()).thenReturn(Optional.empty());
        server.expect(never(), requestTo(URL)).andRespond(withSuccess());
        assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(OWNER, VIEWER, LibraryAccessPurpose.PROFILE));
        server.verify();
        verify(tokens).privacyDecisionToken();
    }

    @Test void missingBaseUrlOrArgumentsAreUnavailableWithoutAskingForAToken() {
        for (String url : new String[] {null, "", "  "}) {
            var unconfigured = new UserServiceLibraryVisibility(RestClient.builder(), tokens, url);
            assertEquals(LibraryAccessDecision.UNAVAILABLE, unconfigured.decide(OWNER, VIEWER, LibraryAccessPurpose.PROFILE));
        }
        assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(null, VIEWER, LibraryAccessPurpose.PROFILE));
        assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(OWNER, null, LibraryAccessPurpose.PROFILE));
        assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(OWNER, VIEWER, null));
        verify(tokens, Mockito.never()).privacyDecisionToken();
    }

    private static org.springframework.test.web.client.ResponseCreator decisions(UUID owner, String allowed) {
        return withSuccess("{\"decisions\":[{\"ownerId\":\"" + owner + "\",\"setting\":\"PUBLIC\",\"allowed\":" + allowed
                + ",\"friends\":false}]}", MediaType.APPLICATION_JSON);
    }
}
