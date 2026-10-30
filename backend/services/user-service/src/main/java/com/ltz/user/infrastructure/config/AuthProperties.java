package com.ltz.user.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Auth Service'in token sözleşmesi (docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md §3): JWKS adresi, issuer ve audience
 * Auth ile aynı ortak ENV değerlerinden gelir; kodda sabit değer yoktur.
 */
@Validated
@ConfigurationProperties("ltz.user.auth")
public record AuthProperties(@NotBlank String jwksUri, @NotBlank String issuer, @NotBlank String audience,
                             /** Servis token'larında (iç uçlar) bu servisin adı: {@code aud} bu değeri içermeli. */
                             @NotBlank String serviceAudience) {
}
