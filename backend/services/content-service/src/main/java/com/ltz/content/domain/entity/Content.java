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

	public boolean isDeleted() {
		return deletedAt != null;
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

	/**
	 * Silme işareti: satır fiziksel silinmez (docs/database.md §2.3), ancak hiçbir okuma yolunda görünmez; saatlik oluşturma
	 * kotası bu satırları saymaya devam eder. Silinen metin saklanmaz (saklama süresi 0 gün): çevirileri ve görselleri servis
	 * aynı transaction'da kalıcı olarak siler, metinden türeyen parmak izi de burada nötr değerle değiştirilir.
	 */
	public void markDeleted(Instant now) {
		this.deletedAt = now;
		this.updatedAt = now;
		this.contentHash = ERASED_HASH;
	}
}
