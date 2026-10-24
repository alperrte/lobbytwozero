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
 * Gönderi beğenisi. Kayıtlar eşzamanlı isteklerde mükerrer oluşmasın diye {@code PostLikeRepository} üzerindeki
 * {@code insert ... on conflict do nothing} ile eklenir; bu varlık okuma ve şema doğrulaması içindir.
 */
@Entity
@Table(name = "post_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

	@EmbeddedId
	private PostLikeId id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}
