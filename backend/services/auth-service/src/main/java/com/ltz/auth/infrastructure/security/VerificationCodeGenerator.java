package com.ltz.auth.infrastructure.security;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.ltz.auth.domain.entity.EmailVerificationCode;

/** Kriptografik rastgele, başında sıfır olabilen sabit uzunlukta sayısal kod üretir. */
@Component
public class VerificationCodeGenerator {

    private static final int CODE_SPACE = (int) Math.pow(10, EmailVerificationCode.CODE_LENGTH);
    private static final String CODE_FORMAT = "%0" + EmailVerificationCode.CODE_LENGTH + "d";

    private final RandomGenerator random;

    public VerificationCodeGenerator() {
        this(new SecureRandom());
    }

    VerificationCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String generate() {
        return String.format(Locale.ROOT, CODE_FORMAT, random.nextInt(CODE_SPACE));
    }
}
