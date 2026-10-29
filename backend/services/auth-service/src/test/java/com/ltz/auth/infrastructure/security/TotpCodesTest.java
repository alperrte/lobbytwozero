package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** RFC 6238 Ek B SHA-1 test vektörleri (8 hanelik değerlerin son 6 hanesi). */
class TotpCodesTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final TotpCodes totp = new TotpCodes();

    @ParameterizedTest
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924",
            "2000000000, 279037", "20000000000, 353130"})
    void rfc6238TestVektorleriniUretir(long epochSeconds, String expected) {
        assertThat(totp.codeAt(RFC_SECRET, totp.stepAt(Instant.ofEpochSecond(epochSeconds)))).isEqualTo(expected);
    }

    @Test
    void saatPenceresiIcindekiAdimiBulurDisindakiniReddeder() {
        Instant now = Instant.ofEpochSecond(1111111111);
        long step = totp.stepAt(now);

        assertThat(totp.matchingStep(RFC_SECRET, totp.codeAt(RFC_SECRET, step + 1), now, 1)).hasValue(step + 1);
        assertThat(totp.matchingStep(RFC_SECRET, totp.codeAt(RFC_SECRET, step + 2), now, 1)).isEmpty();
        assertThat(totp.matchingStep(RFC_SECRET, totp.codeAt(RFC_SECRET, step - 1), now, 0)).isEmpty();
    }

    @Test
    void base32VeOtpauthUriStandartBicimdedir() {
        assertThat(totp.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(totp.otpauthUri("LTZ", "player one", RFC_SECRET)).isEqualTo(
                "otpauth://totp/LTZ%3Aplayer%20one?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=LTZ"
                        + "&algorithm=SHA1&digits=6&period=30");
        assertThat(totp.newSecret()).hasSize(TotpCodes.SECRET_BYTES).isNotEqualTo(totp.newSecret());
    }
}
