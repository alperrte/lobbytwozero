package com.ltz.auth.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * Auth'a iç çağrı yapacak servislerin (client credentials) kayıt defteri; ör. {@code admin}. Kimlik doğrulaması yalnız
 * Auth'un imzaladığı kısa ömürlü {@code svc+jwt} ile yapılır: servis gizli anahtarını bir kez gösterip token alır
 * ({@code POST /internal/v1/service-tokens}). Gizli anahtarı boş olan istemci <b>devre dışıdır</b> (fail-closed).
 *
 * @param clients istemci kimliği ({@code ^[a-z0-9-]{1,32}$}) → ayarlar; {@link ServiceClientConfig} açılışta denetler
 */
@Validated
@ConfigurationProperties("ltz.auth.service-clients")
public record ServiceClientProperties(Map<String, Client> clients) {

    public ServiceClientProperties {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    /**
     * @param secret  en az 32 karakter (üretici betik 256 bit rastgele üretir); boşsa istemci devre dışı
     * @param scopes  Auth'un kendisine ({@code aud=auth-service}) alabileceği kapsamlar;
     *                {@link ServiceClientConfig#ALLOWED_SCOPES} içinden olmalı
     * @param targets başka servislere ({@code aud=<servis>-service}) alabileceği kapsamlar: hedef → kapsamlar. Listede
     *                olmayan hedef için token verilmez; kapsamlar {@link ServiceClientConfig#ALLOWED_TARGET_SCOPES}
     *                içinden olmalı
     */
    public record Client(String secret, Set<String> scopes, Map<String, Set<String>> targets) {

        @ConstructorBinding
        public Client {
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
            Map<String, Set<String>> safeTargets = new HashMap<>();
            if (targets != null) {
                targets.forEach((audience, granted) -> safeTargets.put(audience, granted == null ? Set.of() : Set.copyOf(granted)));
            }
            targets = Map.copyOf(safeTargets);
        }

        public Client(String secret, Set<String> scopes) {
            this(secret, scopes, null);
        }

        public boolean enabled() {
            return secret != null && !secret.isBlank();
        }

        @Override
        public String toString() {
            return "Client[secret=***, scopes=" + scopes + ", targets=" + targets + "]";
        }
    }
}
