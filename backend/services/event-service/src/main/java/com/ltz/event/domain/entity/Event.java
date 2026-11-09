package com.ltz.event.domain.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.domain.exception.InvalidEventScheduleException;
import com.ltz.event.domain.exception.InvalidEventScheduleException.Reason;

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
 * Etkinlik (EVT-01). Sahibi ({@code organizerId}) auth-service hesap kimliğidir ve yalnız doğrulanmış token'ın
 * {@code sub} değerinden atanır. Oluşturma kuralları fabrika metodunda uygulanır: başlangıç gelecekte, bitiş
 * başlangıçtan sonra olmalıdır; yeni etkinlik her zaman {@link EventStatus#PLANNED} başlar.
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

	public static final int TITLE_MAX_LENGTH = 120;
	public static final int DESCRIPTION_MAX_LENGTH = 4000;

	/** Başlıkta hiçbir kontrol karakterine (satır sonu dahil) izin verilmez. */
	public static final String TITLE_ALLOWED_CHARACTERS = "[\\P{Cc}]*";
	/** Açıklamada yalnız satır sonu ve sekme dışındaki kontrol karakterleri reddedilir. */
	public static final String DESCRIPTION_ALLOWED_CHARACTERS = "[\\P{Cc}\\r\\n\\t]*";

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "organizer_id", nullable = false, updatable = false)
	private UUID organizerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private EventType type;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	/** DB'de {@code text}; uzunluk sınırı (4000) DTO doğrulamasında ve CHECK kısıtındadır. */
	@Column(nullable = false)
	private String description;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private EventStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	/**
	 * Yeni etkinlik oluşturur. Metinler çağıran tarafından kırpılmış (strip) olmalıdır.
	 *
	 * @throws InvalidEventScheduleException başlangıç {@code now} anından sonra değilse ya da bitiş başlangıçtan
	 *         sonra değilse
	 */
	public static Event schedule(UUID organizerId, EventType type, String title, String description, Instant startsAt,
			Instant endsAt, Instant now) {
		// Zamanlar milisaniyeye kırpılır: PostgreSQL timestamptz mikrosaniye saklar; kurallar kırpılmış değerlerle
		// uygulanmazsa yalnız nanosaniyede farklı iki zaman domain kuralını geçip ck_events_schedule'ı ihlal eder (500).
		Instant start = startsAt.truncatedTo(ChronoUnit.MILLIS);
		Instant end = endsAt.truncatedTo(ChronoUnit.MILLIS);
		Instant created = now.truncatedTo(ChronoUnit.MILLIS);
		if (!start.isAfter(created)) {
			throw new InvalidEventScheduleException(Reason.STARTS_IN_THE_PAST);
		}
		if (!end.isAfter(start)) {
			throw new InvalidEventScheduleException(Reason.ENDS_NOT_AFTER_START);
		}
		Event event = new Event();
		event.organizerId = organizerId;
		event.type = type;
		event.title = title;
		event.description = description;
		event.startsAt = start;
		event.endsAt = end;
		event.status = EventStatus.PLANNED;
		event.createdAt = created;
		event.updatedAt = created;
		return event;
	}
}
