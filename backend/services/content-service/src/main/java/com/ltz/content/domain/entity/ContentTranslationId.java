package com.ltz.content.domain.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Çevirinin bileşik anahtarı: bir içeriğin her dilde en çok bir çevirisi olur. */
@Embeddable
public record ContentTranslationId(
		@Column(name = "content_id", nullable = false, updatable = false) UUID contentId,
		@Column(name = "locale", nullable = false, updatable = false, length = 5) String locale) implements Serializable {
}
