package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecureTokensTest {

    private final SecureTokens tokens = new SecureTokens();

    @Test
    void opaqueTokenUrlGuvenli256BittirVeOzetiHexdir() {
        String token = tokens.newOpaqueToken();

        assertThat(token).matches("^[A-Za-z0-9_-]{43}$").isNotEqualTo(tokens.newOpaqueToken());
        assertThat(tokens.sha256Hex(token)).matches("^[0-9a-f]{64}$").isEqualTo(tokens.sha256Hex(token));
    }

    @Test
    void kurtarmaKoduOkunabilirBicimdedirKarsilastirmaBicimiAyractanBagimsizdir() {
        String code = tokens.newRecoveryCode();

        assertThat(code).matches("^[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}$");
        assertThat(tokens.normalizeRecoveryCode(" " + code.toLowerCase() + " "))
                .isEqualTo(code.replace("-", ""));
    }
}
