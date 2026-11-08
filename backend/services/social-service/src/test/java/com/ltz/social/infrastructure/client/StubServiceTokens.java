package com.ltz.social.infrastructure.client;

import java.util.ArrayList;
import java.util.List;

import com.ltz.social.application.port.ServiceTokenPort;
import com.ltz.social.domain.exception.ServiceTokenUnavailableException;

/** Auth'a gitmeyen sahte servis token kaynağı: istenen hedefleri kaydeder, istenirse kapalı ya da hatalı davranır. */
final class StubServiceTokens implements ServiceTokenPort {

    static final String TOKEN = "service-token-value";

    final List<String> requestedAudiences = new ArrayList<>();
    boolean configured = true;
    boolean failing;

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String tokenFor(String audience) {
        if (failing) {
            throw new ServiceTokenUnavailableException("no token");
        }
        requestedAudiences.add(audience);
        return TOKEN;
    }
}
