package com.ltz.community.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * social-service engelleme verisinin yerel yansıması (COM-21). Kayıtlar eşzamanlı/yinelenen olaylarda mükerrer
 * oluşmasın diye {@code UserBlockRepository} üzerindeki {@code insert ... on conflict do nothing} ile eklenir.
 */
@Entity
@Table(name = "user_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

	@EmbeddedId
	private UserBlockId id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
