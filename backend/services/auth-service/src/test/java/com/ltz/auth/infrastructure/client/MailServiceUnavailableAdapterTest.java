package com.ltz.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.auth.api.dto.request.ConfirmEmailVerificationRequest;
import com.ltz.auth.api.dto.request.RegisterAccountRequest;
import com.ltz.auth.api.dto.request.ResendEmailVerificationRequest;
import com.ltz.auth.application.port.VerificationCodeDeliveryException;
import com.ltz.auth.application.port.VerificationCodeMail;

class MailServiceUnavailableAdapterTest {

    private static final String EMAIL = "player@example.test";
    private static final String CODE = "012345";

    @Test
    void teslimSessizceBasariliSayilmaz() {
        VerificationCodeMail mail = new VerificationCodeMail(UUID.randomUUID(), UUID.randomUUID(), EMAIL, CODE,
                Duration.ofMinutes(10));
        MailServiceUnavailableAdapter adapter = new MailServiceUnavailableAdapter();

        assertThatThrownBy(() -> adapter.sendVerificationCode(mail))
                .isInstanceOf(VerificationCodeDeliveryException.class)
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining(CODE);
    }

    @Test
    void hassasAlanlarMetinGosterimindeMaskelenir() {
        VerificationCodeMail mail = new VerificationCodeMail(UUID.randomUUID(), UUID.randomUUID(), EMAIL, CODE,
                Duration.ofMinutes(10));
        String password = "correct-horse-battery-staple";

        assertThat(mail.toString()).doesNotContain(EMAIL).doesNotContain(CODE);
        assertThat(new RegisterAccountRequest(EMAIL, "player_one", password).toString())
                .doesNotContain(EMAIL).doesNotContain(password).contains("player_one");
        assertThat(new ConfirmEmailVerificationRequest(EMAIL, CODE, password, "proof-token-value").toString())
                .doesNotContain(EMAIL).doesNotContain(CODE).doesNotContain(password).doesNotContain("proof-token-value");
        assertThat(new ResendEmailVerificationRequest(EMAIL).toString()).doesNotContain(EMAIL);
    }

    @Test
    void sebepliTeslimHatasiSebebiKorur() {
        IllegalStateException cause = new IllegalStateException("timeout");

        assertThat(new VerificationCodeDeliveryException("failed", cause)).hasCause(cause);
    }
}
