package com.ltz.social.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class InviteCodeGeneratorTest {

    private final InviteCodeGenerator generator = new InviteCodeGenerator();

    @Test
    void uretilenKodOnKarakterlikCrockfordBase32dir() {
        for (int i = 0; i < 500; i++) {
            String code = generator.next();

            assertThat(code).hasSize(InviteCodeGenerator.LENGTH).matches("[0-9A-HJKMNP-TV-Z]{10}");
            assertThat(InviteCodeGenerator.isWellFormed(code)).isTrue();
        }
    }

    @Test
    void kodlarTahminEdilemez_cakismaz() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            codes.add(generator.next());
        }

        assertThat(codes).hasSize(5000);
    }

    @Test
    void normallestirme_buyukHarfTireBoslukVeKarisanKarakterler() {
        assertThat(InviteCodeGenerator.normalize("abcd-efgh jk")).isEqualTo("ABCDEFGHJK");
        assertThat(InviteCodeGenerator.normalize(" 0123 4567\t89 ")).isEqualTo("0123456789");
        assertThat(InviteCodeGenerator.normalize("oIl")).isEqualTo("011");
    }

    @Test
    void biçimDenetimi() {
        assertThat(InviteCodeGenerator.isWellFormed("0123456789")).isTrue();
        assertThat(InviteCodeGenerator.isWellFormed("ABCDEFGHJK")).isTrue();
        assertThat(InviteCodeGenerator.isWellFormed("012345678")).isFalse();
        assertThat(InviteCodeGenerator.isWellFormed("01234567890")).isFalse();
        assertThat(InviteCodeGenerator.isWellFormed("abcdefghjk")).isFalse();
        assertThat(InviteCodeGenerator.isWellFormed("ABCDEFGHJU")).isFalse();
        assertThat(InviteCodeGenerator.isWellFormed("ABCDEFGHJ-")).isFalse();
        assertThat(InviteCodeGenerator.isWellFormed("")).isFalse();
    }
}
