package com.ltz.social.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Arkadaşlık davet kodu ömrü ve kullanım sınırı. */
@Validated
@ConfigurationProperties("ltz.social.invite-codes")
public record InviteCodeProperties(
        /** Kodun geçerlilik süresi; süre dolunca kod kullanılamaz, yeni kod üretilir. */
        @NotNull Duration ttl,
        /** Bir kodla en fazla kaç arkadaşlık isteği oluşturulabilir. */
        @Min(1) @Max(1000) int maxUses) {
}
