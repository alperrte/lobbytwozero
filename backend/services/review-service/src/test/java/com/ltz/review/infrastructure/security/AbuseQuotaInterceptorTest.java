package com.ltz.review.infrastructure.security;

import com.ltz.review.application.service.AbuseQuotaService;
import com.ltz.review.domain.enums.AbuseAction;
import com.ltz.review.domain.exception.RateLimitExceededException;
import com.ltz.review.domain.exception.ReviewException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** preHandle sözleşmesi: devam edecekse true; kota aşıldıysa tek 429 yanıtı yazıp false (istek iş katmanına ulaşmaz). */
class AbuseQuotaInterceptorTest {
    final AbuseQuotaService quotas = mock(AbuseQuotaService.class);
    final AbuseQuotaInterceptor interceptor = new AbuseQuotaInterceptor(quotas, AbuseAction.REVIEW_LIKE, Set.of("PUT", "DELETE"));
    final UUID account = UUID.randomUUID();

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    void authenticate() {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(account.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test void allowedRequestContinuesWithoutWritingAnything() throws Exception {
        authenticate();
        var response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("PUT", "/x"), response, new Object()));
        verify(quotas).require(account, AbuseAction.REVIEW_LIKE);
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
        assertFalse(response.containsHeader("Retry-After"));
    }

    @Test void exceededQuotaWritesOneProblemResponseAndStopsTheRequest() throws Exception {
        authenticate();
        doThrow(new RateLimitExceededException(42)).when(quotas).require(account, AbuseAction.REVIEW_LIKE);
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("DELETE", "/x"), response, new Object()));
        assertEquals(429, response.getStatus());
        assertEquals(List.of("42"), response.getHeaders("Retry-After"));
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        String body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.startsWith("{") && body.endsWith("}") && body.indexOf("\"status\":429") == body.lastIndexOf("\"status\":429"), body);
        assertTrue(body.contains("Çok fazla istek"), body);
    }

    @Test void otherMethodsAndNonJwtAuthenticationDoNotConsumeQuota() throws Exception {
        authenticate();
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), new Object()));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", "p"));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("PUT", "/x"), new MockHttpServletResponse(), new Object()));
        SecurityContextHolder.clearContext();
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("PUT", "/x"), new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(quotas);
    }

    @Test void unexpectedFailuresPropagateWithoutWritingAResponse() {
        authenticate();
        var failure = new ReviewException(500, "depo");
        doThrow(failure).when(quotas).require(any(), any());
        var response = new MockHttpServletResponse();
        var request = new MockHttpServletRequest("PUT", "/x");
        var handler = new Object();
        var thrown = assertThrows(ReviewException.class, () -> interceptor.preHandle(request, response, handler));
        assertSame(failure, thrown);
        assertEquals(200, response.getStatus());
    }
}
