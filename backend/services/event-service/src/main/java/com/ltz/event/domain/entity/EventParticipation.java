package com.ltz.event.domain.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.event.domain.enums.ParticipationStatus;

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
 * Kullanıcının etkinliğe katılım kaydı (EVT-08/09). {@code userId} auth-service hesap kimliğidir (REF, FK yok) ve yalnız
 * doğrulanmış token'dan atanır. İptal edilen kayıt silinmez, geçmiş olarak kalır.
 */
@Entity
@Table(name = "event_participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventParticipation {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ParticipationStatus status;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	public static EventParticipation join(UUID eventId, UUID userId, Instant now) {
		Instant at = now.truncatedTo(ChronoUnit.MILLIS);
		EventParticipation participation = new EventParticipation();
		participation.eventId = eventId;
		participation.userId = userId;
		participation.status = ParticipationStatus.ACTIVE;
		participation.joinedAt = at;
		participation.createdAt = at;
		participation.updatedAt = at;
		return participation;
	}

	/** Kullanıcı kendi katılımını iptal eder; yalnız {@link ParticipationStatus#ACTIVE} kayıt iptal edilebilir. */
	public void cancelByUser(Instant now) {
		if (status != ParticipationStatus.ACTIVE) {
			throw new IllegalStateException("Only an active participation can be cancelled.");
		}
		Instant at = now.truncatedTo(ChronoUnit.MILLIS);
		this.status = ParticipationStatus.CANCELLED_BY_USER;
		this.cancelledAt = at;
		this.updatedAt = at;
	}
}
