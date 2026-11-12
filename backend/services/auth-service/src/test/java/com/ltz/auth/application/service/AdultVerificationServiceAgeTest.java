package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Yaş hesabı: 18. doğum günü 18 sayılır, bir gün eksik sayılmaz, 29 Şubat doğumlular 1 Mart'ta 18 olur. */
class AdultVerificationServiceAgeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @Test
    void onSekizinciDogumGunuOnSekizSayilirBirGunEksikSayilmaz() {
        assertThat(AdultVerificationService.isAdultOn(LocalDate.of(2008, 11, 12), TODAY)).isTrue();
        assertThat(AdultVerificationService.isAdultOn(LocalDate.of(2008, 11, 13), TODAY)).isFalse();
        assertThat(AdultVerificationService.isAdultOn(LocalDate.of(2000, 1, 1), TODAY)).isTrue();
    }

    @Test
    void subatYirmiDokuzdaDoganMarttaOnSekizOlur() {
        LocalDate born = LocalDate.of(2008, 2, 29);

        assertThat(AdultVerificationService.isAdultOn(born, LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(AdultVerificationService.isAdultOn(born, LocalDate.of(2026, 3, 1))).isTrue();
    }
}
