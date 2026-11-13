package com.ltz.lfp.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** İstek gövdesi üst sınırı (SECURITY.md §10). En büyük LFP gövdesi (ilan) birkaç KB'dir. */
@Validated
@ConfigurationProperties("ltz.lfp.request-limit")
public record RequestLimitProperties(@NotNull DataSize maxBodySize) {
}
