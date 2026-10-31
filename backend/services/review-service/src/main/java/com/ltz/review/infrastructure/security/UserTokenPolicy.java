package com.ltz.review.infrastructure.security;

import java.time.Duration;

/** Auth JwtProperties: 1–15 dakika; yerel doğrulama toleransı 30 saniye. */
public final class UserTokenPolicy {
    public static final Duration MAX_ACCESS_TTL = Duration.ofMinutes(15);
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    public static final Duration ERASURE_BLOCK_TTL = MAX_ACCESS_TTL.plus(CLOCK_SKEW.multipliedBy(2)).plusMinutes(1);

    private UserTokenPolicy() {}
}
