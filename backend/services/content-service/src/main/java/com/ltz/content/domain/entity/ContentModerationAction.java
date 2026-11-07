package com.ltz.content.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.content.domain.enums.ModerationActionType;
import com.ltz.content.domain.enums.ModeratorRole;
import com.ltz.content.domain.enums.ModerationSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Değişmez moderasyon kaydı (CONT-10): hangi içerik, hangi karar, kim (moderatör kimliği), ne zaman, hangi yoldan ve hangi
 * gerekçeyle. Kayıtlar eklenir, hiçbir zaman güncellenmez ya da silinmez (içerik kalıcı silinirse cascade ile gider).
 */
@Entity
@Table(name = "content_moderation_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentModerationAction {

	public static final int REASON_MAX_LENGTH = 500;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "content_id", nullable = false, updatable = false)
	private UUID contentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 16)
	private ModerationActionType action;

	/** Kararı veren moderatör/yönetici (REF: auth hesabı). */
	@Column(name = "actor_id", nullable = false, updatable = false)
	private UUID actorId;

	/** Kararı veren kullanıcının admin-service tarafından doğrulanmış rolü (olay yükünden). */
	@Enumerated(EnumType.STRING)
	@Column(name = "actor_role", updatable = false, length = 16)
	private ModeratorRole actorRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 16)
	private ModerationSource source;

	@Column(nullable = false, updatable = false, length = REASON_MAX_LENGTH)
	private String reason;

	/** Admin-service'in bağladığı rapor (varsa). */
	@Column(name = "report_id", updatable = false)
	private UUID reportId;

	/** Kararı taşıyan olay (yalnız admin-service yolunda). */
	@Column(name = "event_id", updatable = false)
	private UUID eventId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Admin-service kararı için yeni kayıt (karar yolu her zaman {@link ModerationSource#ADMIN_SERVICE}). */
	public static ContentModerationAction of(UUID contentId, ModerationActionType action, UUID actorId, ModeratorRole actorRole,
			String reason, Instant now) {
		ContentModerationAction entry = new ContentModerationAction();
		entry.contentId = contentId;
		entry.action = action;
		entry.actorId = actorId;
		entry.actorRole = actorRole;
		entry.source = ModerationSource.ADMIN_SERVICE;
		entry.reason = reason;
		entry.createdAt = now;
		return entry;
	}

	/** Kararın admin-service yolundan geldiği bağlamı ekler (bağlı rapor ve kararı taşıyan olay); yeni kayıtta bir kez çağrılır. */
	public ContentModerationAction viaEvent(UUID linkedReportId, UUID linkedEventId) {
		this.reportId = linkedReportId;
		this.eventId = linkedEventId;
		return this;
	}
}
