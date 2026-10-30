package com.ltz.user.api.dto.response;

import java.util.List;

import com.ltz.user.application.service.PrivacyDecisionService.Decision;

/**
 * Gizlilik kararları, istek sırasıyla (yinelenen sahipler bir kez). {@code setting}: sahibin ayarı; {@code allowed}:
 * izleyici görebilir mi (engel, arkadaşlık ve hesap durumu dahil); {@code friends}: izleyici ile sahip arkadaş mı.
 */
public record PrivacyDecisionResponse(List<Decision> decisions) {
}
