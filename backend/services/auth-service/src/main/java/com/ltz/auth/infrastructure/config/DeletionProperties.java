package com.ltz.auth.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Hesap silme politikası (AUTH-16). targets: kullanıcı verisi tutan servisler; hepsi silmeyi onaylamadan hesap
 * DELETED olmaz. recentLoginWindow: parolasız (sosyal) hesaplarda yeniden doğrulama olarak kabul edilen en eski giriş.
 * Worker ayarları koordinasyon döngüsünü yönetir (kira, üstel geri çekilme). stuckAttempts: bu kadar başarısız
 * denemeden sonra görev "takılı" sayılır (uyarı günlüğü + yönetici uç noktasında işaretlenir); otomatik onaylanmaz.
 */
@Validated
@ConfigurationProperties("ltz.auth.deletion")
public record DeletionProperties(
        @NotEmpty Set<@Pattern(regexp = "^[a-z0-9-]{1,32}$") String> targets,
        @NotNull @DurationMin(minutes = 5) @DurationMax(hours = 2) Duration tokenTtl,
        @NotNull @DurationMin(seconds = 1) Duration requestCooldown,
        @NotNull @DurationMin(minutes = 1) Duration recentLoginWindow,
        @NotNull URI linkBaseUrl,
        boolean workerEnabled,
        @NotNull @DurationMin(seconds = 1) Duration pollInterval,
        @Min(1) @Max(500) int batchSize,
        @NotNull @DurationMin(seconds = 10) Duration lease,
        @NotNull @DurationMin(seconds = 1) Duration initialBackoff,
        @NotNull @DurationMin(seconds = 1) Duration maxBackoff,
        @Min(1) @Max(1000) int stuckAttempts) {
}
