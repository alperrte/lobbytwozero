package com.ltz.game.infrastructure.security;
import com.ltz.game.application.port.Actor;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
/** Mevcut sınırlı kova/IP altyapısı; kullanıcı 5 ani + 1/dk, IP 30 ani + 30/dk. */
final class FeedbackPrivacyAndRateLimitFilter extends OncePerRequestFilter {
    private final RequestMatcher privatePaths;
    private final RequestMatcher create;
    private final TokenBucketRateLimiter users;
    private final TokenBucketRateLimiter addresses;
    private final ClientAddressResolver clients;
    FeedbackPrivacyAndRateLimitFilter(RequestMatcher privatePaths,RequestMatcher create,int maxKeys,ClientAddressResolver clients) {
        this(privatePaths,create,new TokenBucketRateLimiter(5,1.0/60,maxKeys,System::nanoTime),
                new TokenBucketRateLimiter(30,0.5,maxKeys,System::nanoTime),clients);
    }
    FeedbackPrivacyAndRateLimitFilter(RequestMatcher privatePaths,RequestMatcher create,TokenBucketRateLimiter users,
                                     TokenBucketRateLimiter addresses,ClientAddressResolver clients) {
        this.privatePaths=privatePaths; this.create=create; this.users=users; this.addresses=addresses; this.clients=clients;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(privatePaths.matches(request)) response.setHeader("Cache-Control","private, no-store");
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(create.matches(request) && authentication!=null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Actor actor) {
            var decision=users.tryAcquire(actor.userId().toString());
            if(decision.allowed()) decision=addresses.tryAcquire(clients.resolve(request));
            if(!decision.allowed()) {
                response.setStatus(429); response.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"code\":\"FEEDBACK_RATE_LIMITED\"}");
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
