package com.ltz.game.infrastructure.security;
import com.ltz.game.application.port.Actor;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
class FeedbackRateLimitTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticated(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(Actor.of(id,Set.of()),null,Set.of()));
    }
    private FeedbackPrivacyAndRateLimitFilter filter(int userCapacity,int ipCapacity) {
        return new FeedbackPrivacyAndRateLimitFilter(r->true,r->r.getMethod().equals("POST"),
                new TokenBucketRateLimiter(userCapacity,0.001,100,()->0L),
                new TokenBucketRateLimiter(ipCapacity,0.001,100,()->0L),new ClientAddressResolver(Set.of()));
    }
    private MockHttpServletResponse call(FeedbackPrivacyAndRateLimitFilter filter,String method,String address) throws Exception {
        var request=new MockHttpServletRequest(method,"/api/v1/game/feedback"); request.setRemoteAddr(address);
        request.addHeader("X-Forwarded-For",UUID.randomUUID().toString());
        var response=new MockHttpServletResponse(); filter.doFilter(request,response,new MockFilterChain()); return response;
    }
    @Test void userCannotAvoidLimitByChangingIpAndReadResponsesAreNotSharedCached() throws Exception {
        var filter=filter(1,10); authenticated(UUID.randomUUID());
        assertEquals(200,call(filter,"POST","192.0.2.1").getStatus());
        var denied=call(filter,"POST","192.0.2.2");
        assertEquals(429,denied.getStatus()); assertNotNull(denied.getHeader("Retry-After"));
        assertEquals("application/problem+json",denied.getContentType());
        var read=call(filter,"GET","192.0.2.2"); assertEquals(200,read.getStatus());
        assertEquals("private, no-store",read.getHeader("Cache-Control"));
    }
    @Test void usersOnSameIpShareIpQuotaAndAnonymousRequestsDoNotConsumeIt() throws Exception {
        var filter=filter(10,1);
        assertEquals(200,call(filter,"POST","192.0.2.1").getStatus());
        authenticated(UUID.randomUUID()); assertEquals(200,call(filter,"POST","192.0.2.1").getStatus());
        authenticated(UUID.randomUUID()); assertEquals(429,call(filter,"POST","192.0.2.1").getStatus());
    }
}
