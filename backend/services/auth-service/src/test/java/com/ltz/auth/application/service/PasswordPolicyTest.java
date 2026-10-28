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
            "passwordpassword1",
            "Password-Password-1",
            "1q2w3e4r5t6y7u8i9o0p",
            "123456789012345",
            "9876543210987654",
            "abcdefghijklmnop",
            "aaaaaaaaaaaaaaaa",
            "abcabcabcabcabcabc",
            "Lobby Two Zero 2026!",
            "my-gamer_kerem-secret-1"
    })
    void zayifParolalarReddedilir(String password) {
        assertThat(policy.isWeak(password, EMAIL, USERNAME)).as(password).isTrue();
        assertThatThrownBy(() -> policy.require(password, EMAIL, USERNAME))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.WEAK_PASSWORD));
    }

    @Test
    void epostaYerelKisminiIcerenParolaReddedilir() {
        assertThat(policy.isWeak("xx-kerem.test-yy-zz-123", EMAIL, "baska_ad")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "correct-horse-battery-staple",
            "another-long-passphrase-42",
            "  spaced passphrase 99  ",
            "Tr0ub4dor&3-and-more-words",
            "mavi-bisiklet-yagmur-ve-cay-7"
    })
    void uzunVeOngorulemeyenParolalarKabulEdilir(String password) {
        assertThatCode(() -> policy.require(password, EMAIL, USERNAME)).doesNotThrowAnyException();
    }

    @Test
    void kisaKimlikParcalariYanlisPozitifUretmez() {
        // Kullanıcı adı 4 karakterden kısaysa parolada aranmaz.
        assertThat(policy.isWeak("correct-horse-battery-staple", EMAIL, "abc")).isFalse();
        assertThat(policy.isWeak("correct-horse-battery-staple", null, null)).isFalse();
    }
}
