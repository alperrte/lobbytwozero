package com.ltz.event.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.infrastructure.persistence.repository.EventRepository;

import jakarta.persistence.EntityManager;

/**
 * Flyway V111–V113 migration'larını gerçek PostgreSQL üzerinde çalıştırır ({@code ddl-auto=validate}); CHECK
 * kısıtlarını ve EVT-04 liste sorgusunu sınar. Veritabanı bu test çalışmasına özel geçici bir veritabanıdır
 * ({@link PostgreSqlIntegrationTest}); her test geri alınır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventRepositoryTest extends PostgreSqlIntegrationTest {

	private static final UUID ORGANIZER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final List<EventStatus> LISTED = List.of(EventStatus.PLANNED, EventStatus.ACTIVE);

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

	/** Zamanlar şimdiye göre verilir; oluşturma anı başlangıçtan önce olacak şekilde ayarlanır. */
	private Event save(String title, Duration startOffset, Duration endOffset, EventStatus status) {
		Instant startsAt = now.plus(startOffset);
		Event event = Event.schedule(ORGANIZER_ID, EventType.COMMUNITY, title, "Açıklama", startsAt,
				now.plus(endOffset), startsAt.minus(Duration.ofMinutes(1)));
		Event saved = eventRepository.saveAndFlush(event);
		if (status != EventStatus.PLANNED) {
			jdbcTemplate.update("update events set status = ? where id = ?", status.name(), saved.getId());
		}
		entityManager.clear();
		return saved;
	}

	private Page<Event> list(int page, int size) {
		return eventRepository.findByEndsAtAfterAndStatusInOrderByStartsAtAscIdAsc(now, LISTED,
				PageRequest.of(page, size));
	}

	// ------------------------------------------------------------------ eşleme ve migration

	@Test
	void savesEventWithGeneratedIdVersionAndUtcTimestamps() {
		Event saved = save("Bahar Turnuvası", Duration.ofHours(1), Duration.ofHours(3), EventStatus.PLANNED);

		Event loaded = eventRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getId()).isNotNull();
		assertThat(loaded.getId().version()).isEqualTo(7);
		assertThat(loaded.getOrganizerId()).isEqualTo(ORGANIZER_ID);
		assertThat(loaded.getType()).isEqualTo(EventType.COMMUNITY);
		assertThat(loaded.getTitle()).isEqualTo("Bahar Turnuvası");
		assertThat(loaded.getDescription()).isEqualTo("Açıklama");
		assertThat(loaded.getStartsAt()).isEqualTo(now.plus(Duration.ofHours(1)));
		assertThat(loaded.getEndsAt()).isEqualTo(now.plus(Duration.ofHours(3)));
		assertThat(loaded.getStatus()).isEqualTo(EventStatus.PLANNED);
		assertThat(loaded.getVersion()).isZero();
	}

	@Test
	void everyEventTypeIncludingPollCanBeStored() {
		for (EventType type : EventType.values()) {
			Instant startsAt = now.plus(Duration.ofHours(1));
			Event event = Event.schedule(ORGANIZER_ID, type, "Tür " + type, "Açıklama", startsAt,
					startsAt.plus(Duration.ofHours(1)), now);

			assertThat(eventRepository.saveAndFlush(event).getType()).isEqualTo(type);
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void databaseRejectsAnUnknownTypeStatusAndAnInvalidSchedule() {
		String sql = "insert into events (id, organizer_id, type, title, description, starts_at, ends_at, status, "
				+ "created_at, updated_at) values (?, ?, ?, 'T', 'D', ?, ?, ?, now(), now())";
		Instant start = now.plus(Duration.ofHours(1));

		assertThatThrownBy(() -> insertRaw(sql, "VOTING", start, start.plusSeconds(60), "PLANNED"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertRaw(sql, "POLL", start, start.plusSeconds(60), "DONE"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertRaw(sql, "POLL", start, start, "PLANNED"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertRaw(sql, "POLL", start, start.minusSeconds(60), "PLANNED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertRaw(String sql, String type, Instant startsAt, Instant endsAt, String status) {
		jdbcTemplate.update(sql, UUID.randomUUID(), ORGANIZER_ID, type, Timestamp.from(startsAt),
				Timestamp.from(endsAt), status);
	}

	// ------------------------------------------------------------------ EVT-04 liste sorgusu

	@Test
	void listsUpcomingAndCurrentEventsInStartOrder() {
		Event later = save("Yarın", Duration.ofDays(1), Duration.ofDays(1).plusHours(2), EventStatus.PLANNED);
		Event soon = save("Yakında", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		Event ongoing = save("Devam ediyor", Duration.ofHours(-1), Duration.ofHours(1), EventStatus.PLANNED);
		Event active = save("Aktif", Duration.ofHours(-2), Duration.ofHours(2), EventStatus.ACTIVE);

		Page<Event> page = list(0, 10);

		assertThat(page.getContent()).extracting(Event::getId)
				.containsExactly(active.getId(), ongoing.getId(), soon.getId(), later.getId());
		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.hasNext()).isFalse();
	}

	@Test
	void excludesEndedCancelledAndCompletedEvents() {
		Event upcoming = save("Yaklaşan", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Bitmiş ama durumu PLANNED", Duration.ofHours(-3), Duration.ofHours(-1), EventStatus.PLANNED);
		save("İptal", Duration.ofHours(1), Duration.ofHours(2), EventStatus.CANCELLED);
		save("Tamamlandı", Duration.ofHours(-3), Duration.ofHours(1), EventStatus.COMPLETED);
		save("Aktif ama bitmiş", Duration.ofHours(-3), Duration.ofHours(-1), EventStatus.ACTIVE);

		assertThat(list(0, 10).getContent()).extracting(Event::getId).containsExactly(upcoming.getId());
	}

	@Test
	void eventEndingExactlyNowIsNotListed() {
		save("Tam şimdi biten", Duration.ofHours(-1), Duration.ZERO, EventStatus.PLANNED);

		assertThat(list(0, 10).getContent()).isEmpty();
	}

	@Test
	void paginatesWithStableOrderAndTotals() {
		Event first = save("1", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		Event second = save("2", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);
		Event third = save("3", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED);

		Page<Event> firstPage = list(0, 2);
		Page<Event> secondPage = list(1, 2);

		assertThat(firstPage.getContent()).extracting(Event::getId).containsExactly(first.getId(), second.getId());
		assertThat(firstPage.getTotalElements()).isEqualTo(3);
		assertThat(firstPage.getTotalPages()).isEqualTo(2);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(secondPage.getContent()).extracting(Event::getId).containsExactly(third.getId());
		assertThat(secondPage.hasNext()).isFalse();
		assertThat(list(5, 2).getContent()).isEmpty();
	}

	@Test
	void eventsStartingAtTheSameTimeKeepAStableIdOrder() {
		Event a = save("A", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		Event b = save("B", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		Event c = save("C", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);

		List<UUID> ids = list(0, 10).getContent().stream().map(Event::getId).toList();

		assertThat(ids).containsExactlyInAnyOrder(a.getId(), b.getId(), c.getId());
		assertThat(ids.stream().map(UUID::toString).toList()).isSorted();
		assertThat(list(0, 10).getContent()).extracting(Event::getId).isEqualTo(ids);
	}
}
