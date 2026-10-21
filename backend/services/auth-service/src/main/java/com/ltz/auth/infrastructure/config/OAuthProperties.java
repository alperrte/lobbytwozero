package com.ltz.auth.infrastructure.config;

import java.net.URI;
import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Harici giriş (AUTH-05/06). publicBaseUrl: sağlayıcıların döneceği, dışarıdan görünen Gateway adresi (Google'da
 * kayıtlı redirect URI ve Steam realm bundan türetilir). frontendRedirectUrl: sonucun fragment ile iletileceği
 * SABİT adres (istemciden yönlendirme adresi alınmaz, open redirect yoktur).
 * Google client kimlik bilgileri boşsa Google girişi kapalıdır; kimlik bilgisi üretilmez veya varsayılan verilmez.
 */
@Validated
@ConfigurationProperties("ltz.auth.oauth")
public record OAuthProperties(
        @NotNull URI publicBaseUrl,
        @NotNull URI frontendRedirectUrl,
        @NotNull @DurationMin(minutes = 1) @DurationMax(minutes = 30) Duration flowTtl,
        @NotNull @DurationMin(millis = 1) Duration httpTimeout,
        @NotNull @Valid Google google,
        @NotNull @Valid Steam steam) {

    public record Google(String clientId, String clientSecret, @NotNull URI authorizationUri, @NotNull URI tokenUri,
                         @NotNull URI jwksUri, @NotBlank String issuer) {

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        @Override
        public String toString() {
            return "Google[clientId=" + clientId + ", clientSecret=***, issuer=" + issuer + "]";
        }
    }

    public record Steam(@NotNull URI openIdEndpoint,
                        @NotNull @DurationMin(seconds = 30) Duration assertionMaxAge,
                        @NotNull @DurationMin(minutes = 1) @DurationMax(minutes = 60) Duration registrationTtl) {
    }
}
