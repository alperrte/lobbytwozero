package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JwtSigningKeyTest {

    private static final int WEAK_KEY_BITS = 2048;

    @Test
    void zayifVeyaBozukAnahtarReddedilir_mesajAnahtariIcermez() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(WEAK_KEY_BITS);
        String weakKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());

        assertThatThrownBy(() -> JwtSigningKey.fromBase64Pkcs8(weakKey))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(weakKey).hasNoCause();
        assertThatThrownBy(() -> JwtSigningKey.fromBase64Pkcs8("bm90LWEta2V5"))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThatThrownBy(() -> JwtSigningKey.fromBase64Pkcs8("not base64 $$$"))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    @Test
    void gecerliAnahtarKidVeYalnizAcikAlanlarlaJwksUretir() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(JwtSigningKey.MIN_KEY_BITS);
        JwtSigningKey key = JwtSigningKey.fromBase64Pkcs8(
                Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded()));

        assertThat(key.keyId()).isNotBlank();
        assertThat(key.publicKey().getModulus().bitLength()).isEqualTo(JwtSigningKey.MIN_KEY_BITS);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) key.publicJwkSet().get("keys");
        assertThat(keys).singleElement().satisfies(jwk -> assertThat(jwk)
                .containsKeys("kty", "n", "e", "kid").doesNotContainKeys("d", "p", "q", "dp", "dq", "qi"));
    }
}
