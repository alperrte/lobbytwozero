package com.ltz.community.application.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.community.infrastructure.persistence.repository.UserBlockRepository;

/**
 * Engelleme kuralı (COM-21), iki yönlüdür: bir kullanıcıyı engelleyen de, tarafından engellenen de birbirinin
 * içeriğini görmez ve birbirinin içeriğiyle etkileşemez. Veri social-service olaylarından yerelde yansıtılır
 * ({@code user_blocks}); misafir (kimliksiz) okumada kural uygulanmaz.
 */
@Component
public class BlockPolicy {

	private final UserBlockRepository userBlockRepository;

	public BlockPolicy(UserBlockRepository userBlockRepository) {
		this.userBlockRepository = userBlockRepository;
	}

	/** {@code userId} ile arasında engelleme olan tüm kullanıcılar (iki yön birleşik); misafir için boştur. */
	public Set<UUID> counterparts(UUID userId) {
		if (userId == null) {
			return Set.of();
		}
		Set<UUID> counterparts = new HashSet<>(userBlockRepository.findBlockedIds(userId));
		counterparts.addAll(userBlockRepository.findBlockerIds(userId));
		return counterparts;
	}

	/** Kendisiyle ya da misafirle engelleme olmaz. */
	public boolean isBlocked(UUID viewerId, UUID otherId) {
		return viewerId != null && !viewerId.equals(otherId) && userBlockRepository.existsBetween(viewerId, otherId);
	}
}
