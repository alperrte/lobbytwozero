package com.ltz.event.domain.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.domain.exception.InvalidEventScheduleException;
import com.ltz.event.domain.exception.InvalidEventScheduleException.Reason;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
	public static final int MAX_CAPACITY = 100_000;

	/** Başlıkta hiçbir kontrol karakterine (satır sonu dahil) izin verilmez. */
	public static final String TITLE_ALLOWED_CHARACTERS = "[\\P{Cc}]*";
	/** Açıklamada yalnız satır sonu ve sekme dışındaki kontrol karakterleri reddedilir. */
	public static final String DESCRIPTION_ALLOWED_CHARACTERS = "[\\P{Cc}\\r\\n\\t]*";

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	/** Hesap silinince null olur (KD-3: "silinmiş kullanıcı"); bu yüzden yalnız toplu güncellemeyle değişir. */
	@Column(name = "organizer_id", updatable = false)
	private UUID organizerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private EventType type;

	/** Game Service'teki oyunun kimliği (REF, FK yok); oyun adı saklanmaz. Oyunsuz etkinlikte null. */
	@Column(name = "game_id")
	private UUID gameId;

	/**
	 * Etkinliğin kendi platformları (EVT-06); bağlı oyundan türetilmez. Boş küme = platform belirtilmemiş. Liste
	 * sorgularında N+1 olmaması için toplu (batch) yüklenir.
	 */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "event_platforms", joinColumns = @JoinColumn(name = "event_id"))
	@Column(name = "platform", nullable = false, length = 32)
	@Enumerated(EnumType.STRING)
	@BatchSize(size = 100)
	private Set<EventPlatform> platforms = new LinkedHashSet<>();

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

	/** Katılımcı sınırı (EVT-13); null = sınırsız. */
	@Column(name = "capacity", updatable = false)
	private Integer capacity;

	/**
	 * Geçerli (ACTIVE/ATTENDED) katılım sayısı. Yalnız katılım/iptal akışlarındaki atomik UPDATE ile değişir; entity
	 * üzerinden yazılmaz (eşzamanlı güncellemelerin üzerine eski değer yazılmasın diye {@code updatable=false}).
	 */
	@Column(name = "participant_count", nullable = false, insertable = false, updatable = false)
	private int participantCount;

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
		return schedule(organizerId, type, null, title, description, startsAt, endsAt, now);
	}

	/** Platformsuz, oyuna bağlı etkinlik (EVT-17). */
	public static Event schedule(UUID organizerId, EventType type, UUID gameId, String title, String description,
			Instant startsAt, Instant endsAt, Instant now) {
		return schedule(organizerId, type, gameId, Set.of(), title, description, startsAt, endsAt, now);
	}

	/**
	 * Oyuna bağlı etkinlik oluşturur (EVT-17). {@code gameId} (varsa) çağıran tarafından Game Service'te doğrulanmış
	 * olmalıdır; bu sınıf yalnız kimliği taşır.
	 */
	public static Event schedule(UUID organizerId, EventType type, UUID gameId, Set<EventPlatform> platforms,
			String title, String description, Instant startsAt, Instant endsAt, Instant now) {
		return schedule(organizerId, type, gameId, platforms, null, title, description, startsAt, endsAt, now);
	}

	/**
	 * Katılımcı sınırlı etkinlik oluşturur (EVT-13). {@code capacity} null ise sınırsızdır; verilirse 1..{@link #MAX_CAPACITY}
	 * olmalıdır (DTO doğrulaması ve DB CHECK ile aynı sınır).
	 */
	public static Event schedule(UUID organizerId, EventType type, UUID gameId, Set<EventPlatform> platforms,
			Integer capacity, String title, String description, Instant startsAt, Instant endsAt, Instant now) {
		if (capacity != null && (capacity < 1 || capacity > MAX_CAPACITY)) {
			throw new IllegalArgumentException("capacity must be between 1 and " + MAX_CAPACITY);
		}
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
		event.gameId = gameId;
		if (platforms != null) {
			event.platforms.addAll(platforms);
		}
		event.capacity = capacity;
		event.title = title;
		event.description = description;
		event.startsAt = start;
		event.endsAt = end;
		event.status = EventStatus.PLANNED;
		event.createdAt = created;
		event.updatedAt = created;
		return event;
	}

	/** Kalan kontenjan; sınırsız etkinlikte null. */
	public Integer getRemainingCapacity() {
		return capacity == null ? null : Math.max(0, capacity - participantCount);
	}

	/** Kapasite doluysa true (sınırsız etkinlik hiç dolmaz). */
	public boolean isFull() {
		return capacity != null && participantCount >= capacity;
	}

	/** Katılıma açık: PLANNED/ACTIVE ve bitiş zamanı gelmemiş. Kapasite ve izleyici koşulları ayrıca denetlenir. */
	public boolean isOpenAt(Instant now) {
		return (status == EventStatus.PLANNED || status == EventStatus.ACTIVE) && endsAt.isAfter(now);
	}

	/** Platformlar (salt okunur, enum sırasında); boş = platform belirtilmemiş. */
	public Set<EventPlatform> getPlatforms() {
		return platforms.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(platforms));
	}
}
