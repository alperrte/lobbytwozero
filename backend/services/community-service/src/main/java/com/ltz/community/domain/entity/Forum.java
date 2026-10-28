package com.ltz.community.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.community.domain.enums.ForumStatus;

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

/**
 * Oyuna özel (ya da genel) forum. {@code slug} ve {@code gameId} oluşturulduktan sonra değişmez; başlık, açıklama
 * ve görünürlük moderatör tarafından güncellenir.
 */
@Entity
@Table(name = "forums")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Forum {

	public static final int TITLE_MAX_LENGTH = 120;
	public static final int DESCRIPTION_MAX_LENGTH = 1000;
	public static final String SLUG_PATTERN = "[a-z0-9]+(-[a-z0-9]+)*";

	/** Satır sonu ve sekme dışındaki kontrol karakterlerini reddeder. */
	public static final String TEXT_ALLOWED_CHARACTERS = Post.BODY_ALLOWED_CHARACTERS;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "game_id", updatable = false)
	private UUID gameId;

	@Column(nullable = false, updatable = false, length = 80)
	private String slug;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(length = DESCRIPTION_MAX_LENGTH)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ForumStatus status;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	/** {@code gameId} (varsa) game-service doğrulamasından sonra verilmelidir. */
	public static Forum open(UUID createdBy, UUID gameId, String slug, String title, String description, Instant now) {
		Forum forum = new Forum();
		forum.createdBy = createdBy;
		forum.gameId = gameId;
		forum.slug = slug;
		forum.title = title;
		forum.description = description;
		forum.status = ForumStatus.VISIBLE;
		forum.createdAt = now;
		forum.updatedAt = now;
		return forum;
	}

	public boolean isVisible() {
		return status == ForumStatus.VISIBLE;
	}

	/** Verilmeyen (null) alanlar korunur; hiçbir şey değişmediyse {@code updatedAt} dokunulmaz. */
	public void update(String newTitle, String newDescription, ForumStatus newStatus, Instant now) {
		boolean changed = false;
		if (newTitle != null && !newTitle.equals(title)) {
			title = newTitle;
			changed = true;
		}
		if (newDescription != null && !newDescription.equals(description)) {
			description = newDescription;
			changed = true;
		}
		if (newStatus != null && newStatus != status) {
			status = newStatus;
			changed = true;
		}
		if (changed) {
			updatedAt = now;
		}
	}
}
