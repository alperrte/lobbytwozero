package com.ltz.community.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.community.domain.enums.PostStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

	public static final int BODY_MAX_LENGTH = 5000;

	/** Satır sonu ve sekme dışındaki kontrol karakterlerini reddeder. */
	public static final String BODY_ALLOWED_CHARACTERS = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*";

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "author_id", nullable = false, updatable = false)
	private UUID authorId;

	@Column(name = "game_id")
	private UUID gameId;

	@Column(nullable = false)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PostStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	private Long version;

	public static Post publish(UUID authorId, String body, Instant now) {
		return publish(authorId, body, null, now);
	}

	/** {@code gameId} isteğe bağlıdır ve game-service doğrulamasından sonra verilmelidir. */
	public static Post publish(UUID authorId, String body, UUID gameId, Instant now) {
		Post post = new Post();
		post.authorId = authorId;
		post.body = body;
		post.gameId = gameId;
		post.status = PostStatus.VISIBLE;
		post.createdAt = now;
		post.updatedAt = now;
		return post;
	}

	public boolean isOwnedBy(UUID userId) {
		return authorId.equals(userId);
	}

	/**
	 * Soft delete: satır fiziksel silinmez, böylece sonraki fazlardaki yorum/beğeni/repost kayıtları
	 * orphan kalmaz; okuma sorguları DELETED gönderiyi hariç tutar.
	 */
	public void markDeleted(Instant now) {
		this.status = PostStatus.DELETED;
		this.deletedAt = now;
		this.updatedAt = now;
	}

	public void linkGame(UUID newGameId, Instant now) {
		if (newGameId.equals(gameId)) {
			return;
		}
		this.gameId = newGameId;
		this.updatedAt = now;
	}

	public void unlinkGame(Instant now) {
		if (gameId == null) {
			return;
		}
		this.gameId = null;
		this.updatedAt = now;
	}

	public void editBody(String newBody, Instant now) {
		if (body.equals(newBody)) {
			return;
		}
		this.body = newBody;
		this.updatedAt = now;
	}
}
