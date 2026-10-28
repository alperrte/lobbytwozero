package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import com.ltz.auth.infrastructure.security.VerificationCodeHasher;

class AuthPropertiesValidationTest {

    private static final String VALID_KEY = randomKey();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(AuthApplicationConfig.class, VerificationCodeHasher.class)
            .withPropertyValues(
                    "ltz.auth.verification.code-ttl=PT10M",
                    "ltz.auth.verification.max-attempts=5",
                    "ltz.auth.verification.resend-cooldown=PT60S",
                    "ltz.auth.verification.daily-limit=5",
                    "ltz.auth.verification.hmac-key=" + VALID_KEY,
                    "ltz.auth.password-hashing.max-concurrent-hashes=4",
                    "ltz.auth.password-hashing.acquire-timeout=PT3S",
                    "ltz.auth.request.max-body-size=8KB",
                    "ltz.auth.jwt.issuer=ltz-auth",
                    "ltz.auth.jwt.audience=ltz-api",
                    "ltz.auth.jwt.access-token-ttl=PT5M",
                    "ltz.auth.jwt.private-key=not-loaded-in-this-test",
                    "ltz.auth.session.refresh-token-ttl=P7D",
                    "ltz.auth.session.absolute-ttl=P30D",
                    "ltz.auth.session.refresh-reuse-grace=PT10S",
                    "ltz.auth.session.secure-cookies=true",
                    "ltz.auth.attempts.max-failures=5",
                    "ltz.auth.attempts.window=PT15M",
                    "ltz.auth.attempts.lock-duration=PT15M",
                    "ltz.auth.two-factor.issuer=LTZ",
                    "ltz.auth.two-factor.encryption-key=" + VALID_KEY,
                    "ltz.auth.two-factor.challenge-ttl=PT5M",
                    "ltz.auth.two-factor.max-challenge-attempts=5",
                    "ltz.auth.two-factor.recovery-code-count=10",
                    "ltz.auth.two-factor.allowed-clock-skew-steps=1",
                    "ltz.auth.two-factor.required-roles=ADMIN,MODERATOR");

    @Test
    void gecerliAyarlarlaBaslar() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(VerificationCodeHasher.class));
    }

    @Test
    void anahtarYoksaBaslamaz() {
        runner.withPropertyValues("ltz.auth.verification.hmac-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void kisaAnahtarlaBaslamazHataAnahtariIcermez() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[8]);

        runner.withPropertyValues("ltz.auth.verification.hmac-key=" + shortKey)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(fullMessage(context.getStartupFailure())).doesNotContain(shortKey);
                });
    }

    @Test
    void gecersizSureVeLimitlerleBaslamaz() {
        runner.withPropertyValues("ltz.auth.verification.code-ttl=PT0S").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.verification.code-ttl=PT2H").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.verification.max-attempts=0").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.verification.daily-limit=0").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.password-hashing.max-concurrent-hashes=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.jwt.access-token-ttl=PT1H").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.jwt.private-key=").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.attempts.max-failures=0").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.two-factor.encryption-key=").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ltz.auth.session.refresh-reuse-grace=PT5M")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void ikiFaktorZorunluluguYalnizDevVeTestProfilindeKaldirilabilir() {
        ApplicationContextRunner policy = runner.withUserConfiguration(TwoFactorPolicyConfig.class);

        policy.withPropertyValues("ltz.auth.two-factor.required-roles=", "spring.profiles.active=dev")
                .run(context -> assertThat(context).hasNotFailed());
        policy.withPropertyValues("ltz.auth.two-factor.required-roles=MODERATOR", "spring.profiles.active=test")
                .run(context -> assertThat(context).hasNotFailed());
        policy.withPropertyValues("ltz.auth.two-factor.required-roles=", "spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
        policy.withPropertyValues("ltz.auth.two-factor.required-roles=", "spring.profiles.active=dev,prod")
                .run(context -> assertThat(context).hasFailed());
        policy.withPropertyValues("ltz.auth.two-factor.required-roles=MODERATOR", "spring.profiles.active=production")
                .run(context -> assertThat(context).hasFailed());
        policy.withPropertyValues("ltz.auth.two-factor.required-roles=MODERATOR", "spring.profiles.active=")
                .run(context -> assertThat(context).hasFailed());
        policy.withPropertyValues("ltz.auth.two-factor.required-roles=ADMIN", "spring.profiles.active=production")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void taninmayanProfilAdiylaBaslamaz() {
        ApplicationContextRunner guard = new ApplicationContextRunner().withUserConfiguration(ProfileGuardConfig.class);

        guard.withPropertyValues("spring.profiles.active=production").run(context -> assertThat(context).hasFailed());
        guard.withPropertyValues("spring.profiles.active=staging").run(context -> assertThat(context).hasFailed());
        guard.withPropertyValues("spring.profiles.active=dev").run(context -> assertThat(context).hasNotFailed());
        guard.withPropertyValues("spring.profiles.active=prod").run(context -> assertThat(context).hasNotFailed());
        guard.withPropertyValues("spring.profiles.active=dev,local").run(context -> assertThat(context).hasNotFailed());
        guard.withPropertyValues("spring.profiles.active=").run(context -> assertThat(context).hasNotFailed());
    }

    private static String fullMessage(Throwable failure) {
        StringBuilder message = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            message.append(current.getMessage()).append('\n');
        }
        return message.toString();
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
