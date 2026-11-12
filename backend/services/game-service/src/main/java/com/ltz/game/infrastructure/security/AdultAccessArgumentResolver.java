package com.ltz.game.infrastructure.security;

import com.ltz.game.application.port.AdultAccess;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Controller parametresi {@link AdultAccess}: yaş bilgisi yalnız doğrulanmış (imzası, vereni ve hedefi denetlenmiş) token'ın {@value #CLAIM} iddiasından gelir.
 * Yalnız boolean {@code true} yetki verir; metin, sayı, {@code null} ve diğer türler reddedilir. Anonim istekte hata fırlatmaz (herkese açık uçlar için), yetkisiz döner.
 */
final class AdultAccessArgumentResolver implements HandlerMethodArgumentResolver {
    /** Auth'un {@code TokenClaims.ADULT_VERIFIED} iddiası. Değişirse Auth, bu sabit ve docs/api.md birlikte değişir. */
    static final String CLAIM = "adult_verified";

    @Override
    public boolean supportsParameter(MethodParameter parameter) { return AdultAccess.class.equals(parameter.getParameterType()); }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean verified = authentication instanceof ActorAuthentication actor && actor.isAuthenticated() && Boolean.TRUE.equals(actor.claim(CLAIM));
        return verified ? new AdultAccess(true) : AdultAccess.denied();
    }
}
