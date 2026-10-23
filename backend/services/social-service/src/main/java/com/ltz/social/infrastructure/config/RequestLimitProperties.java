package com.ltz.social.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** İstek gövdesi üst sınırı (SECURITY.md §10). Social uçlarının gövdeleri birkaç yüz bayttır. */
@Validated
@ConfigurationProperties("ltz.social.request-limit")
public record RequestLimitProperties(@NotNull DataSize maxBodySize) {
}
