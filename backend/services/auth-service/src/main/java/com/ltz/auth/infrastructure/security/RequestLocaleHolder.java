package com.ltz.auth.infrastructure.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ltz.auth.application.port.RequestLocaleContext;

/** İsteğin arayüz dilini aynı iş parçacığında taşır ({@link RequestLocaleFilter} yazar, istek bitince siler). */
@Component
public class RequestLocaleHolder implements RequestLocaleContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    void set(String locale) {
        if (locale == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(locale);
        }
    }

    void clear() {
        CURRENT.remove();
    }

    @Override
    public Optional<String> locale() {
        return Optional.ofNullable(CURRENT.get());
    }
}
