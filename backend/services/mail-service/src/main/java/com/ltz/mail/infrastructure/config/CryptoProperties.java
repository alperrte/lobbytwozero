package com.ltz.mail.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/** Kuyruktaki içeriği şifreleyen anahtar (Base64, 32 bayt). Boşsa servis açılmaz; değer hiçbir çıktıda görünmez. */
@Validated
@ConfigurationProperties("ltz.mail.crypto")
public record CryptoProperties(@NotBlank String payloadKey) {

    @Override
    public String toString() {
        return "CryptoProperties[payloadKey=***]";
    }
}
