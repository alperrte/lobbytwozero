package com.ltz.community.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.community.domain.enums.CommentStatus;

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

/** Gönderi yorumu; {@code parentCommentId} doluysa başka bir yoruma yanıttır (sınırsız iç içe). */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

	public static final int BODY_MAX_LENGTH = 2000;

	/** Satır sonu ve sekme dışındaki kontrol karakterlerini reddeder. */
	public static final String BODY_ALLOWED_CHARACTERS = Post.BODY_ALLOWED_CHARACTERS;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "post_id", nullable = false, updatable = false)
	private UUID postId;

	@Column(name = "author_id", nullable = false, updatable = false)
	private UUID authorId;

	@Column(name = "parent_comment_id", updatable = false)
	private UUID parentCommentId;

	@Column(nullable = false)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CommentStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	private Long version;

	/** {@code parentCommentId} null ise üst düzey yorumdur. */
	public static Comment write(UUID postId, UUID authorId, UUID parentCommentId, String body, Instant now) {
		Comment comment = new Comment();
		comment.postId = postId;
		comment.authorId = authorId;
		comment.parentCommentId = parentCommentId;
		comment.body = body;
		comment.status = CommentStatus.VISIBLE;
		comment.createdAt = now;
		comment.updatedAt = now;
		return comment;
	}

	public boolean isOwnedBy(UUID userId) {
		return authorId.equals(userId);
	}

	public boolean isVisible() {
		return status == CommentStatus.VISIBLE;
	}

	public void editBody(String newBody, Instant now) {
		if (body.equals(newBody)) {
			return;
		}
		this.body = newBody;
		this.updatedAt = now;
	}

	/** Soft delete: yanıt ilişkisi bozulmasın diye satır korunur; içerik API'de gösterilmez. */
	public void markDeleted(Instant now) {
		this.status = CommentStatus.DELETED;
		this.deletedAt = now;
		this.updatedAt = now;
	}
}
