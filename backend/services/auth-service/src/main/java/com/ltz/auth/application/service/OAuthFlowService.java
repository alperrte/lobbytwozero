package com.ltz.auth.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.domain.entity.OAuthFlow;
import com.ltz.auth.domain.enums.ExternalProvider;
import com.ltz.auth.infrastructure.config.OAuthProperties;
import com.ltz.auth.infrastructure.persistence.repository.OAuthFlowRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;
import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

import lombok.RequiredArgsConstructor;

/**
 * Harici giriş akışının state yönetimi. state 256 bit rastgeledir, tarayıcıya hem yönlendirme adresinde hem de
 * HttpOnly cookie'de verilir (login CSRF koruması). PKCE verifier ve nonce sunucu anahtarıyla state'ten
 * türetilir; böylece saklanmaz ve state'i bilmeyen biri üretemez.
 */
@Service
@RequiredArgsConstructor
public class OAuthFlowService {

    static final String PKCE_DOMAIN = "OAUTH_PKCE";
    static final String NONCE_DOMAIN = "OAUTH_NONCE";

    private final OAuthFlowRepository flowRepository;
    private final SecureTokens secureTokens;
    private final VerificationCodeHasher hasher;
    private final OAuthProperties properties;
    private final Clock clock;

    @Transactional
    public String start(ExternalProvider provider) {
        Instant now = clock.instant();
        String state = secureTokens.newOpaqueToken();
        flowRepository.save(OAuthFlow.start(secureTokens.sha256Hex(state), provider, now, now.plus(properties.flowTtl())));
        return state;
    }

    /**
     * Tarayıcıdaki cookie ile geri dönen state aynı olmalı, akış var, süresi dolmamış ve daha önce kullanılmamış
     * olmalıdır. Tüketim atomiktir.
     */
    @Transactional
    public boolean consume(ExternalProvider provider, String state, String stateFromCookie) {
        if (state == null || stateFromCookie == null || !MessageDigest.isEqual(
                state.getBytes(StandardCharsets.UTF_8), stateFromCookie.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        return flowRepository.consume(secureTokens.sha256Hex(state), provider, clock.instant()) == 1;
    }

    public String codeVerifier(String state) {
        return hasher.hash(PKCE_DOMAIN, state);
    }

    public String nonce(String state) {
        return hasher.hash(NONCE_DOMAIN, state);
    }
}
