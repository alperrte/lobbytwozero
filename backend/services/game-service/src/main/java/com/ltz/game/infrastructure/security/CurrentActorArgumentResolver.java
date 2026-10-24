package com.ltz.game.infrastructure.security;

import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.exception.AccessForbiddenException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Controller parametresi {@link Actor}: kimlik yalnız doğrulanmış güvenlik bağlamından gelir. */
final class CurrentActorArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) { return Actor.class.equals(parameter.getParameterType()); }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Actor actor) return actor;
        throw new AccessForbiddenException();
    }
}
