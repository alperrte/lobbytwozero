package com.ltz.content.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;

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
 * Haber, resmî duyuru veya kullanıcı içeriği. Başlık, özet ve gövde {@link ContentTranslation} içinde tutulur. Yazar, tür
 * ve ilk dil oluşturulduktan sonra değişmez; istemci bu alanları düzenleme isteğiyle değiştiremez.
 */
@Entity
@Table(name = "contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content {

	public static final String DEFAULT_LOCALE = "tr";

	/** Silinen içeriğin parmak izi yerine yazılan nötr değer (64 sıfır); mükerrer indeksi silinmiş satırları zaten dışlar. */
	public static final String ERASED_HASH = "0".repeat(64);

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "author_id", nullable = false, updatable = false)
	private UUID authorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 32)
	private ContentType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ContentStatus status;

	@Column(name = "default_locale", nullable = false, updatable = false, length = 5)
	private String defaultLocale;

	/** Metnin parmak izi (SHA-256, onaltılık); (yazar, tür) başına mükerrer içeriği engeller. */
	@Column(name = "content_hash", nullable = false, length = 64)
	private String contentHash;

	/** LTZ oyun kataloğundaki ilişkili oyun (REF, isteğe bağlı); game-service doğrulamasından sonra verilir. */
	@Column(name = "game_id")
	private UUID gameId;

	/** Yayınlanma anı; taslakta null, yayınlandıktan sonra hiç değişmez. */
	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(nullable = false)
	private boolean featured;

	@Column(name = "featured_at")
	private Instant featuredAt;

	@Column(name = "featured_by")
	private UUID featuredBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	private Long version;

	/** Yeni taslak. Yayın durumu istemciden gelmez: her içerik {@code DRAFT} başlar. */
	public static Content draft(UUID authorId, ContentType type, String locale, String contentHash, Instant now) {
		Content content = new Content();
		content.authorId = authorId;
		content.type = type;
		content.status = ContentStatus.DRAFT;
		content.defaultLocale = locale;
		content.contentHash = contentHash;
		content.createdAt = now;
		content.updatedAt = now;
		return content;
	}

	public boolean isOwnedBy(UUID userId) {
		return authorId.equals(userId);
	}

	public boolean isDraft() {
		return status == ContentStatus.DRAFT;
	}

	public boolean isPublished() {
		return status == ContentStatus.PUBLISHED && !isDeleted();
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	/** Sahibi taslağını ve yayımlanmış içeriğini düzenleyebilir; kaldırılmış (REMOVED) ve arşivlenmiş içerik düzenlenemez. */
	public boolean isEditableByOwner() {
		return !isDeleted() && (status == ContentStatus.DRAFT || status == ContentStatus.PUBLISHED);
	}

	/**
	 * Sahibi, moderatörce kaldırılmış (REMOVED) içerik dışında her durumdaki içeriğini silebilir; kaldırılmış içerik
	 * moderasyon kaydı olarak korunur.
	 */
	public boolean isDeletableByOwner() {
		return !isDeleted() && status != ContentStatus.REMOVED;
	}

	/** Metin değişti: yeni parmak izi ve değişiklik zamanı kaydedilir. */
	public void revise(String newContentHash, Instant now) {
		this.contentHash = newContentHash;
		this.updatedAt = now;
	}

	/** Değişiklik zamanını günceller (kategori, kaynak, çeviri ya da oyun ilişkisi değiştiğinde). */
	public void touch(Instant now) {
		this.updatedAt = now;
	}

	/** Taslağı yayınlar (ön moderatör onayı yoktur, CONT-09). Yayın kuralları servis katmanında denetlenir. */
	public void publish(Instant now) {
		this.status = ContentStatus.PUBLISHED;
		this.publishedAt = now;
		this.updatedAt = now;
	}

	/** Yayımlanmış içeriği öne çıkarır; yetki (ADMIN) ve durum denetimi servis katmanındadır. Değişiklik olduysa true döner. */
	public boolean feature(UUID adminId, Instant now) {
		if (featured) {
			return false;
		}
		this.featured = true;
		this.featuredAt = now;
		this.featuredBy = adminId;
		this.updatedAt = now;
		return true;
	}

	public boolean unfeature(Instant now) {
		if (!featured) {
			return false;
		}
		this.featured = false;
		this.featuredAt = null;
		this.featuredBy = null;
		this.updatedAt = now;
		return true;
	}

	public boolean linkGame(UUID newGameId, Instant now) {
		if (newGameId.equals(gameId)) {
			return false;
		}
		this.gameId = newGameId;
		this.updatedAt = now;
		return true;
	}

	public boolean unlinkGame(Instant now) {
		if (gameId == null) {
			return false;
		}
		this.gameId = null;
		this.updatedAt = now;
		return true;
	}

	/**
	 * Silme işareti: satır fiziksel silinmez (docs/database.md §2.3), ancak hiçbir okuma yolunda görünmez; saatlik oluşturma
	 * kotası bu satırları saymaya devam eder. Silinen metin saklanmaz (saklama süresi 0 gün): çevirileri ve görselleri servis
	 * aynı transaction'da kalıcı olarak siler, metinden türeyen parmak izi de burada nötr değerle değiştirilir. Öne çıkarma da
	 * kaldırılır.
	 */
	public void markDeleted(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
		this.contentHash = ERASED_HASH;
		this.featured = false;
		this.featuredAt = null;
		this.featuredBy = null;
	}
}
