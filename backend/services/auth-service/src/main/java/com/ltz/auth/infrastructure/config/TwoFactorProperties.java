package com.ltz.auth.infrastructure.config;

import java.time.Duration;
import java.util.Set;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.ltz.auth.domain.enums.RoleCode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * TOTP (D5). encryptionKey: Base64 kodlu 32 baytlık AES-256 anahtarı (AUTH_TOTP_ENCRYPTION_KEY).
 * requiredRoles: bu rollerden birine sahip hesap TOTP etkin değilse oturum alamaz ve TOTP'yi kapatamaz.
 */
@Validated
@ConfigurationProperties("ltz.auth.two-factor")
public record TwoFactorProperties(
        @NotBlank String issuer,
        @NotBlank String encryptionKey,
        @NotNull @DurationMin(minutes = 1) @DurationMax(minutes = 15) Duration challengeTtl,
        @Min(1) @Max(10) int maxChallengeAttempts,
        @Min(5) @Max(20) int recoveryCodeCount,
        @Min(0) @Max(2) int allowedClockSkewSteps,
        @NotNull Set<RoleCode> requiredRoles) {

    @Override
    public String toString() {
        return "TwoFactorProperties[issuer=" + issuer + ", encryptionKey=***, challengeTtl=" + challengeTtl
                + ", maxChallengeAttempts=" + maxChallengeAttempts + ", recoveryCodeCount=" + recoveryCodeCount
                + ", allowedClockSkewSteps=" + allowedClockSkewSteps + ", requiredRoles=" + requiredRoles + "]";
    }
}
