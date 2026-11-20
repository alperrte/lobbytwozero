package com.ltz.user.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Oyuncu araması hız sınırı (kullanıcı listesinin kazınmasına karşı). Sayaç bu örneğin belleğindedir; çok örnekli
 * kurulumda sınır örnek başına uygulanır.
 *
 * @param perMinute kullanıcı başına dakikalık arama sayısı
 */
@Validated
@ConfigurationProperties("ltz.user.search-limit")
public record SearchLimitProperties(@DefaultValue("30") @Min(1) @Max(10000) int perMinute) {
}
