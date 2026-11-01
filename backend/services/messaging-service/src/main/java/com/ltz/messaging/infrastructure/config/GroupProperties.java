package com.ltz.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Grup sınırları. Üye sayısına sahip dahildir. Üst sınır 100'dür: yeni üyelerin engel durumu Social'a tek çağrıda
 * sorulur ve Social bir çağrıda en fazla 100 kimlik kabul eder.
 */
@Validated
@ConfigurationProperties("ltz.messaging.groups")
public record GroupProperties(@Min(2) @Max(100) int maxMembers) {
}
