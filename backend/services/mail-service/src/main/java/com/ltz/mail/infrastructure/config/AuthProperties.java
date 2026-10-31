package com.ltz.mail.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Auth Service'in servis token sözleşmesi (docs-auth/servis-sozlesmeleri.md §3): JWKS adresi ve issuer Auth ile aynı
 * ortak ENV değerlerinden gelir; kodda sabit değer yoktur.
 */
@Validated
@ConfigurationProperties("ltz.mail.auth")
public record AuthProperties(@NotBlank String jwksUri, @NotBlank String issuer,
                             /** Servis token'larında bu servisin adı: {@code aud} bu değeri içermeli. */
                             @NotBlank String serviceAudience) {
}
