package com.ltz.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("ltz.auth.mail")
public record MailDeliveryProperties(@NotNull MailAdapter adapter) {
}
