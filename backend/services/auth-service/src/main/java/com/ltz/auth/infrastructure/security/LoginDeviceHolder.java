package com.ltz.auth.infrastructure.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ltz.auth.application.port.LoginDeviceContext;

/** Giriş isteğinin cihaz çerezi değerini aynı iş parçacığında taşır ({@link LoginDeviceFilter} yazar, istek bitince siler). */
@Component
public class LoginDeviceHolder implements LoginDeviceContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    void set(String token) {
        CURRENT.set(token);
    }

    void clear() {
        CURRENT.remove();
    }

    @Override
    public Optional<String> deviceToken() {
        return Optional.ofNullable(CURRENT.get());
    }
}
