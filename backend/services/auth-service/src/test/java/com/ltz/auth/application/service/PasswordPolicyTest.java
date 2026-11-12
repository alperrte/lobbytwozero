package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;

class PasswordPolicyTest {

    private static final String EMAIL = "kerem.test@example.test";
    private static final String USERNAME = "gamer_kerem";

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            // Kara liste: bileşim kuralını geçen yaygın kalıplar
            "Password1!",
            "Passw0rd.",
            "Qwerty123!",
            "Welcome1-",
            "Admin123_",
            "Password-Password-1",
            // Tekrar ve desen
            "Aa1!Aa1!Aa1!",
            "Lobby Two Zero 2026!",
            "my-Gamer_kerem-secret-1",
            // Bileşim kuralı: biri eksik
            "correct-horse-battery-9",
            "CORRECT-HORSE-BATTERY-9",
            "Correct-Horse-Battery",
            "CorrectHorseBattery9",
            "Correct Horse 9 Battery",
            "Abcdefg1 "
    })
    void zayifParolalarReddedilir(String password) {
        assertThat(policy.isWeak(password, EMAIL, USERNAME)).as(password).isTrue();
        assertThatThrownBy(() -> policy.require(password, EMAIL, USERNAME))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.WEAK_PASSWORD));
    }

    @Test
    void epostaYerelKisminiIcerenParolaReddedilir() {
        assertThat(policy.isWeak("Xx-kerem.test-yy-Zz-123", EMAIL, "baska_ad")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Kx7!mQ2p",
            "mavi-Bisiklet-7",
            "Correct-Horse-Battery-9",
            "Another-Long-Passphrase-42",
            "Tr0ub4dor&3-and-more-words",
            "Ağaç.Çiçek_9",
            "Zq9#vL4$"
    })
    void kurallaraUyanParolalarKabulEdilir(String password) {
        assertThatCode(() -> policy.require(password, EMAIL, USERNAME)).doesNotThrowAnyException();
    }

    @Test
    void ozelKarakterHarfVeRakamDisindakiHerSeydir() {
        for (char special : "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|`~".toCharArray()) {
            assertThat(PasswordPolicy.hasRequiredComposition("Abcdefg1" + special)).as("" + special).isTrue();
        }
        assertThat(PasswordPolicy.hasRequiredComposition("Abcdefg1 ")).isFalse();
    }

    @Test
    void kisaKimlikParcalariYanlisPozitifUretmez() {
        // Kullanıcı adı 4 karakterden kısaysa parolada aranmaz.
        assertThat(policy.isWeak("Correct-Horse-Battery-9", EMAIL, "abc")).isFalse();
        assertThat(policy.isWeak("Correct-Horse-Battery-9", null, null)).isFalse();
    }
}
