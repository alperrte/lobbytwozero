package com.ltz.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import com.ltz.auth.application.port.VerificationCodeMailPort;
import com.ltz.auth.infrastructure.client.DevSmtpVerificationMailAdapter;
import com.ltz.auth.infrastructure.client.MailServiceUnavailableAdapter;

/** Profil izolasyonu ve production fail-fast: development adaptörü hiçbir prod kombinasyonunda açılmaz. */
class MailDeliveryConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(MailDeliveryConfig.class, DevSmtpMailConfig.class,
                    MailServiceUnavailableAdapter.class)
            .withPropertyValues(
                    "ltz.auth.mail.dev-smtp.host=localhost",
                    "ltz.auth.mail.dev-smtp.port=1025",
                    "ltz.auth.mail.dev-smtp.connect-timeout=PT1S",
                    "ltz.auth.mail.dev-smtp.read-timeout=PT1S",
                    "ltz.auth.mail.dev-smtp.from=no-reply@ltz.local");

    @Test
    void profilYokkenVarsayilanAdaptorTeslimiReddeder() {
        runner.withPropertyValues("ltz.auth.mail.adapter=unavailable")
                .run(context -> assertThat(context).hasNotFailed()
                        .getBean(VerificationCodeMailPort.class).isInstanceOf(MailServiceUnavailableAdapter.class));
    }

    @Test
    void devProfilindeMailpitAdaptoruSecilir() {
        runner.withPropertyValues("spring.profiles.active=dev", "ltz.auth.mail.adapter=dev-smtp")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(VerificationCodeMailPort.class))
                            .isInstanceOf(DevSmtpVerificationMailAdapter.class);
                    assertThat(context).doesNotHaveBean(MailServiceUnavailableAdapter.class);
                });
    }

    @Test
    void devProfilindeAdaptorKapatilabilir() {
        runner.withPropertyValues("spring.profiles.active=dev", "ltz.auth.mail.adapter=unavailable")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(DevSmtpVerificationMailAdapter.class));
    }

    @Test
    void prodProfilindeProductionAdaptoruYoksaBaslamaz() {
        runner.withPropertyValues("spring.profiles.active=prod", "ltz.auth.mail.adapter=unavailable")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("PENDING"));
    }

    @Test
    void prodProfilindeDevAdaptoruIstenirseBaslamaz() {
        runner.withPropertyValues("spring.profiles.active=prod", "ltz.auth.mail.adapter=dev-smtp")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("never with 'prod'"));
    }

    @Test
    void devVeProdBirlikteSeciliyseDevAdaptoruIstenirseBaslamaz() {
        runner.withPropertyValues("spring.profiles.active=dev,prod", "ltz.auth.mail.adapter=dev-smtp")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void devProfiliOlmadanDevAdaptoruIstenirseBaslamaz() {
        runner.withPropertyValues("ltz.auth.mail.adapter=dev-smtp", "spring.profiles.active=test")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void korumaDenetimiOlmasaBileProdVarkenDevAdaptoruYuklenmez() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(DevSmtpMailConfig.class)
                .withPropertyValues("spring.profiles.active=dev,prod", "ltz.auth.mail.adapter=dev-smtp")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(DevSmtpVerificationMailAdapter.class));
    }

    @Test
    void eksikSmtpAyarlariylaDevAdaptoruBaslamaz() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(MailDeliveryConfig.class, DevSmtpMailConfig.class)
                .withPropertyValues("spring.profiles.active=dev", "ltz.auth.mail.adapter=dev-smtp")
                .run(context -> assertThat(context).hasFailed());
    }
}
