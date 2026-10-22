package com.ltz.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** Public uçlarda istek gövdesi üst sınırı; büyük gövdeler JSON ayrıştırılmadan reddedilir. */
@Validated
@ConfigurationProperties("ltz.auth.request")
public record RequestLimitProperties(@NotNull DataSize maxBodySize) {
}
