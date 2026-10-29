package com.ltz.auth.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Masaüstü cihaz kimliği politikası (AUTH-17). Cihaz token'ının ömrü (60 sn) ve kapsamı kodda sabittir.
 *
 * @param maxActive      hesap başına en çok aktif cihaz
 * @param credentialTtl  cihaz kimlik bilgisinin ömrü; dolunca yeni cihaz bağlanır
 * @param dailyBindLimit hesap başına 24 saatte en çok bağlama (iptal ve yeniden bağlama döngüsüyle tablo şişmesin)
 */
@Validated
@ConfigurationProperties("ltz.auth.device")
public record DeviceProperties(
        @Min(1) @Max(20) int maxActive,
        @NotNull @DurationMin(days = 1) @DurationMax(days = 730) Duration credentialTtl,
        @Min(1) @Max(100) int dailyBindLimit) {
}
