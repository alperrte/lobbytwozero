package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class VerificationCodeGeneratorTest {

    private static final int SAMPLE_SIZE = 1_000;

    @Test
    void kucukDegerlerBasindaSifirlaAltiHaneyeTamamlanir() {
        RandomGenerator fixed = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return 42;
            }
        };

        assertThat(new VerificationCodeGenerator(fixed).generate()).isEqualTo("000042");
    }

    @Test
    void secureRandomIleUretilenKodlarAltiHaneliSayisaldir() {
        VerificationCodeGenerator generator = new VerificationCodeGenerator();
        Set<String> codes = new HashSet<>();

        for (int index = 0; index < SAMPLE_SIZE; index++) {
            String code = generator.generate();
            assertThat(code).matches("^[0-9]{6}$");
            codes.add(code);
        }

        assertThat(codes).hasSizeGreaterThan(SAMPLE_SIZE / 2);
    }
}
