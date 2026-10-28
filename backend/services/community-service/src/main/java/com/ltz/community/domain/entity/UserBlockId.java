package com.ltz.community.domain.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Engellemenin bileşik anahtarı: engelleyen ve engellenen kullanıcı. */
@Embeddable
public record UserBlockId(
		@Column(name = "blocker_id", nullable = false, updatable = false) UUID blockerId,
		@Column(name = "blocked_id", nullable = false, updatable = false) UUID blockedId) implements Serializable {
}
