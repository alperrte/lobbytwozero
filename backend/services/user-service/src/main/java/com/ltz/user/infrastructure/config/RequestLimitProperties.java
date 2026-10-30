package com.ltz.user.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** İstek gövdesi üst sınırı (SECURITY.md §10). Profil JSON uçlarının gövdeleri birkaç yüz bayttır. */
@Validated
@ConfigurationProperties("ltz.user.request-limit")
public record RequestLimitProperties(@NotNull DataSize maxBodySize) {
}
