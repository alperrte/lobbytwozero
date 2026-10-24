package com.ltz.community.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.community.domain.enums.PostCategory;
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

	/** Konunun açıldığı forum (COM-19); null ise forum dışı gönderi. Oluşturulduktan sonra değişmez. */
	@Column(name = "forum_id", updatable = false)
	private UUID forumId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PostCategory category;

	/** Repost'ta isteğe bağlı kısa yorumdur (null olabilir); repost olmayan gönderide her zaman doludur. */
	@Column
	private String body;

	/** Repost ise ORİJİNAL (repost olmayan) gönderinin kimliği; aksi halde null. Oluşturulduktan sonra değişmez. */
	@Column(name = "repost_of_id", updatable = false)
	private UUID repostOfId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PostStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/**
	 * Beğeni/yorum sayaçları yalnız atomik {@code update ... set x = x + 1} sorgularıyla değişir
	 * (bkz. {@code PostRepository}); {@code updatable = false} olduğundan gönderi düzenlerken bayat bir değer
	 * geri yazılıp sayaç ezilmez.
	 */
	@Column(name = "like_count", nullable = false, updatable = false)
	private int likeCount;

	@Column(name = "comment_count", nullable = false, updatable = false)
	private int commentCount;

	/** Veritabanı tarafından hesaplanır ({@code like_count + comment_count}); popülerlik sıralaması için. */
	@Column(name = "popularity_score", insertable = false, updatable = false)
	private int popularityScore;

	@Version
	private Long version;

	public static Post publish(UUID authorId, String body, Instant now) {
		return publish(authorId, body, null, now);
	}

	/** {@code gameId} isteğe bağlıdır ve game-service doğrulamasından sonra verilmelidir. */
	public static Post publish(UUID authorId, String body, UUID gameId, Instant now) {
		return publish(authorId, body, gameId, null, now);
	}

	/** {@code category} verilmezse {@link PostCategory#GENERAL} atanır. */
	public static Post publish(UUID authorId, String body, UUID gameId, PostCategory category, Instant now) {
		return publishInForum(authorId, null, body, gameId, category, now);
	}

	/** Forum konusu olarak yayımlar; {@code forumId} görünür bir forumu göstermelidir (servis doğrular). */
	public static Post publishInForum(UUID authorId, UUID forumId, String body, UUID gameId, PostCategory category,
			Instant now) {
		Post post = new Post();
		post.forumId = forumId;
		post.authorId = authorId;
		post.body = body;
		post.gameId = gameId;
		post.category = category == null ? PostCategory.GENERAL : category;
		post.status = PostStatus.VISIBLE;
		post.createdAt = now;
		post.updatedAt = now;
		return post;
	}

	/**
	 * Orijinal gönderiyi kaynağa atıfla yeniden paylaşır. İçerik kopyalanmaz, yalnız {@code originalId} saklanır;
	 * {@code comment} (null olabilir) reposter'ın kısa notudur. {@code originalId} repost olmayan gönderiyi göstermelidir
	 * (servis doğrular).
	 */
	public static Post repost(UUID reposterId, UUID originalId, String comment, Instant now) {
		Post post = new Post();
		post.authorId = reposterId;
		post.repostOfId = originalId;
		post.body = comment;
		post.category = PostCategory.GENERAL;
		post.status = PostStatus.VISIBLE;
		post.createdAt = now;
		post.updatedAt = now;
		return post;
	}

	public boolean isRepost() {
		return repostOfId != null;
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

	public void changeCategory(PostCategory newCategory, Instant now) {
		if (newCategory == category) {
			return;
		}
		this.category = newCategory;
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
		if (newBody.equals(body)) {
			return;
		}
		this.body = newBody;
		this.updatedAt = now;
	}
}
