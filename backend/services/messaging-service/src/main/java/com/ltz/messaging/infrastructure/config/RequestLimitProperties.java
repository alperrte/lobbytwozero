package com.ltz.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** JSON istek gövdesi üst sınırı (SECURITY.md §10). Sohbet/üyelik gövdeleri birkaç KB'ı geçmez. */
@Validated
@ConfigurationProperties("ltz.messaging.request-limit")
public record RequestLimitProperties(@NotNull DataSize maxBodySize) {
}
