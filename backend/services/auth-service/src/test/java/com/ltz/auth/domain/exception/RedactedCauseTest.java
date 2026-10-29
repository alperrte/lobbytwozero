package com.ltz.auth.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class RedactedCauseTest {

    private static final String PRIVATE_MESSAGE = "alice@example.test code=123456";
    private static final String PRIVATE_CAUSE = "provider-response-body-secret";

    private final Exception original = new IllegalStateException(PRIVATE_MESSAGE, new RuntimeException(PRIVATE_CAUSE));

    @Test
    void mesajiVeNedeniTasimazSadeceTurVeYiginIziniKorur() {
        RedactedCause cause = RedactedCause.of(original);

        assertThat(cause.getMessage()).isEqualTo(IllegalStateException.class.getName())
                .doesNotContain("alice").doesNotContain("123456");
        assertThat(cause.getCause()).isNull();
        assertThat(cause.getSuppressed()).isEmpty();
        assertThat(cause.getStackTrace()).isEqualTo(original.getStackTrace());
    }

    @Test
    void saricininGunlugeYazilanYiginIzindeOzelVeriGorunmez() {
        StringWriter log = new StringWriter();

        new UsernameAlreadyTakenException(RedactedCause.of(original)).printStackTrace(new PrintWriter(log));

        assertThat(log.toString()).contains("IllegalStateException").contains("RedactedCauseTest")
                .doesNotContain("alice@example.test").doesNotContain(PRIVATE_CAUSE);
    }
}
