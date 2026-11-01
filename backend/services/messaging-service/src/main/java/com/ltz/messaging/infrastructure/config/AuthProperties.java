package com.ltz.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Auth Service'in access token sözleşmesi (docs-auth/entegrasyon/README.md bölüm 1): JWKS adresi, issuer ve audience
 * Auth ile aynı ortak ENV değerlerinden gelir; kodda sabit değer yoktur.
 */
@Validated
@ConfigurationProperties("ltz.messaging.auth")
public record AuthProperties(@NotBlank String jwksUri, @NotBlank String issuer, @NotBlank String audience) {
}
