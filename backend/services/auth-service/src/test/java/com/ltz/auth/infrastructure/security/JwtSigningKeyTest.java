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
    void zayifVeyaBozukAnahtarReddedilirMesajAnahtariIcermez() throws Exception {
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

    @Test
    void ekAcikAnahtarlarJwksteImzaAnahtarindanSonraYayinlanirOzelAlanSizmaz() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(JwtSigningKey.MIN_KEY_BITS);
        var signing = generator.generateKeyPair();
        var next = generator.generateKeyPair();
        String signingPrivate = Base64.getEncoder().encodeToString(signing.getPrivate().getEncoded());
        String nextPublic = Base64.getEncoder().encodeToString(next.getPublic().getEncoded());
        String signingPublic = Base64.getEncoder().encodeToString(signing.getPublic().getEncoded());

        // İmza anahtarının kendi açık anahtarı ve aynı anahtarın tekrarı yinelenmez.
        JwtSigningKey key = JwtSigningKey.fromBase64Pkcs8(signingPrivate, List.of(nextPublic, nextPublic, signingPublic));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) key.publicJwkSet().get("keys");
        assertThat(keys).hasSize(2);
        assertThat(keys.getFirst()).containsEntry("kid", key.keyId());
        assertThat(keys.getLast().get("kid")).isNotEqualTo(key.keyId());
        assertThat(keys).allSatisfy(jwk -> assertThat(jwk)
                .containsKeys("kty", "n", "e", "kid").doesNotContainKeys("d", "p", "q", "dp", "dq", "qi"));
    }

    @Test
    void zayifVeyaBozukEkAnahtarReddedilirMesajAnahtariIcermez() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(JwtSigningKey.MIN_KEY_BITS);
        String signingPrivate = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        generator.initialize(WEAK_KEY_BITS);
        String weakPublic = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());

        assertThatThrownBy(() -> JwtSigningKey.fromBase64Pkcs8(signingPrivate, List.of(weakPublic)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(weakPublic).hasNoCause();
        assertThatThrownBy(() -> JwtSigningKey.fromBase64Pkcs8(signingPrivate, List.of("not base64 $$$")))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThatThrownBy(() -> JwtSigningKey.fromBase64Pkcs8(signingPrivate, List.of(signingPrivate)))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }
}
