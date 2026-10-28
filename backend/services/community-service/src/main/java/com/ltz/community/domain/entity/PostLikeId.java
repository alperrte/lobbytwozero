package com.ltz.community.domain.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Beğeninin bileşik anahtarı: bir kullanıcı bir gönderiyi en çok bir kez beğenir. */
@Embeddable
public record PostLikeId(
		@Column(name = "post_id", nullable = false, updatable = false) UUID postId,
		@Column(name = "user_id", nullable = false, updatable = false) UUID userId) implements Serializable {
}
