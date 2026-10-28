package com.ltz.community.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.community.domain.enums.ReportReason;
import com.ltz.community.domain.enums.ReportStatus;
import com.ltz.community.domain.enums.ReportTarget;

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
 * Gönderi ya da yorum raporu. İçerik referansı ({@code targetType}, {@code targetId}) ve içeriğin yazarı rapor anında
 * kaydedilir; içerik sonradan silinse bile moderasyon kaydı anlamlı kalır. Durum yalnız admin-service kararıyla değişir.
 */
@Entity
@Table(name = "community_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

	public static final int DETAIL_MAX_LENGTH = 500;

	/** Satır sonu ve sekme dışındaki kontrol karakterlerini reddeder. */
	public static final String DETAIL_ALLOWED_CHARACTERS = Post.BODY_ALLOWED_CHARACTERS;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false, length = 16)
	private ReportTarget targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private UUID targetId;

	@Column(name = "target_author_id", nullable = false, updatable = false)
	private UUID targetAuthorId;

	@Column(name = "reporter_id", nullable = false, updatable = false)
	private UUID reporterId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 32)
	private ReportReason reason;

	@Column(updatable = false, length = DETAIL_MAX_LENGTH)
	private String detail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ReportStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	public static Report open(ReportTarget targetType, UUID targetId, UUID targetAuthorId, UUID reporterId,
			ReportReason reason, String detail, Instant now) {
		Report report = new Report();
		report.targetType = targetType;
		report.targetId = targetId;
		report.targetAuthorId = targetAuthorId;
		report.reporterId = reporterId;
		report.reason = reason;
		report.detail = detail;
		report.status = ReportStatus.OPEN;
		report.createdAt = now;
		report.updatedAt = now;
		return report;
	}

	/** Yalnız açık rapor sonuçlanır; zaten sonuçlanmış rapor değişmez. Değişiklik olduysa true döner. */
	public boolean close(ReportStatus outcome, Instant now) {
		if (status != ReportStatus.OPEN || outcome == ReportStatus.OPEN) {
			return false;
		}
		this.status = outcome;
		this.updatedAt = now;
		return true;
	}
}
