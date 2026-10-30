package com.ltz.user.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Social yansıması (arkadaşlık ve engel olayları) güvenilir mi. social-service olayları bu servise göndermeye başlayıp
 * geçmiş ilişkiler aktarılana kadar {@code false} kalır (T6). {@code false} iken iç karar ucu sahibi olmayan izleyiciye
 * hiçbir bölüm için izin vermez (fail-closed) ve arkadaş sayısı {@code UNAVAILABLE} döner. ENV değil, dağıtım ayarıdır.
 */
@ConfigurationProperties("ltz.user.social")
public record SocialProperties(@DefaultValue("false") boolean projectionEnabled) {
}
