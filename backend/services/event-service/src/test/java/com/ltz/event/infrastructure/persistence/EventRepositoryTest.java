package com.ltz.event.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
		return save(title, startOffset, endOffset, status, null);
	}

	private Event save(String title, Duration startOffset, Duration endOffset, EventStatus status, UUID gameId) {
		Instant startsAt = now.plus(startOffset);
		Event event = Event.schedule(ORGANIZER_ID, EventType.COMMUNITY, gameId, title, "Açıklama", startsAt,
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

	// ------------------------------------------------------------------ EVT-05 başlığa göre arama

	private Page<Event> search(String text, int page, int size) {
		return eventRepository.searchByTitle(now, LISTED, "%" + text + "%", PageRequest.of(page, size));
	}

	private List<String> titles(Page<Event> page) {
		return page.getContent().stream().map(Event::getTitle).toList();
	}

	@Test
	void searchFindsTitlesByPartialCaseInsensitiveMatch() {
		save("Bahar Turnuvası", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Cuma Oyun Gecesi", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);
		save("TURNUVA finali", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED);

		assertThat(titles(search("turnuva", 0, 10))).containsExactly("Bahar Turnuvası", "TURNUVA finali");
		assertThat(titles(search("OYUN", 0, 10))).containsExactly("Cuma Oyun Gecesi");
		assertThat(titles(search("a", 0, 10))).hasSize(3);
		assertThat(search("yok-boyle-bir-sey", 0, 10).getContent()).isEmpty();
	}

	@Test
	void searchIsCaseInsensitiveForTurkishLetters() {
		save("Öğrenci Gecesi", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("İstanbul Buluşması", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);
		save("ŞAMPİYONLUK", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED);

		assertThat(titles(search("öğrenci", 0, 10))).containsExactly("Öğrenci Gecesi");
		assertThat(titles(search("ÖĞRENCİ", 0, 10))).containsExactly("Öğrenci Gecesi");
		assertThat(titles(search("istanbul", 0, 10))).containsExactly("İstanbul Buluşması");
		assertThat(titles(search("şampiyonluk", 0, 10))).containsExactly("ŞAMPİYONLUK");
	}

	@Test
	void searchStaysInsideTheListingScope() {
		save("Arama yaklaşan", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Arama devam eden", Duration.ofHours(-1), Duration.ofHours(1), EventStatus.ACTIVE);
		save("Arama bitmiş", Duration.ofHours(-3), Duration.ofHours(-1), EventStatus.PLANNED);
		save("Arama iptal", Duration.ofHours(1), Duration.ofHours(2), EventStatus.CANCELLED);
		save("Arama tamamlandı", Duration.ofHours(-3), Duration.ofHours(1), EventStatus.COMPLETED);

		assertThat(titles(search("arama", 0, 10))).containsExactly("Arama devam eden", "Arama yaklaşan");
	}

	@Test
	void searchResultsKeepTheListingOrderAndPaginateWithTotals() {
		save("Gece 3", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED);
		save("Gece 1", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Gece 2", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);
		save("Başka", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);

		Page<Event> first = search("gece", 0, 2);
		Page<Event> second = search("gece", 1, 2);

		assertThat(titles(first)).containsExactly("Gece 1", "Gece 2");
		assertThat(first.getTotalElements()).isEqualTo(3);
		assertThat(first.hasNext()).isTrue();
		assertThat(titles(second)).containsExactly("Gece 3");
		assertThat(second.hasNext()).isFalse();
	}

	@Test
	void searchTreatsLikeWildcardsAsLiteralTextWhenEscapedLikeTheService() {
		save("Yüzde 100% eğlence", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Yüzde 100 eğlence", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);
		save("a_b etkinlik", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED);
		save("axb etkinlik", Duration.ofHours(4), Duration.ofHours(5), EventStatus.PLANNED);
		save("Ünlem! gecesi", Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED);

		// Desen, EventService'in kullandığı kaçışla ('!' kaçış karakteri) elle hazırlanır.
		assertThat(titles(eventRepository.searchByTitle(now, LISTED, "%100!%%", PageRequest.of(0, 10))))
				.containsExactly("Yüzde 100% eğlence");
		assertThat(titles(eventRepository.searchByTitle(now, LISTED, "%a!_b%", PageRequest.of(0, 10))))
				.containsExactly("a_b etkinlik");
		assertThat(titles(eventRepository.searchByTitle(now, LISTED, "%!!%", PageRequest.of(0, 10))))
				.containsExactly("Ünlem! gecesi");
	}

	// ------------------------------------------------------------------ EVT-17 oyun bağlantısı

	private static final UUID GAME_A = UUID.fromString("0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_B = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
	private static final UUID GAME_C = UUID.fromString("0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66");

	@Test
	void gameIdIsStoredAsAReferenceWithoutAForeignKeyAndIsNullableForEventsWithoutAGame() {
		Event withGame = save("Oyunlu", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);
		Event withoutGame = save("Oyunsuz", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);

		assertThat(eventRepository.findById(withGame.getId()).orElseThrow().getGameId()).isEqualTo(GAME_A);
		assertThat(eventRepository.findById(withoutGame.getId()).orElseThrow().getGameId()).isNull();
	}

	@Test
	void existsReportsOnlyGameLinkedEventsInsideTheListingScope() {
		assertThat(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(now, LISTED)).isFalse();

		save("Oyunsuz", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Oyunlu ama iptal", Duration.ofHours(1), Duration.ofHours(2), EventStatus.CANCELLED, GAME_A);
		save("Oyunlu ama bitmiş", Duration.ofHours(-3), Duration.ofHours(-1), EventStatus.PLANNED, GAME_A);
		assertThat(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(now, LISTED)).isFalse();

		save("Oyunlu yaklaşan", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);
		assertThat(eventRepository.existsByEndsAtAfterAndStatusInAndGameIdIsNotNull(now, LISTED)).isTrue();
	}

	// ------------------------------------------------------------------ EVT-05 başlık VEYA oyun

	private Page<Event> searchTitleOrGame(String text, Collection<UUID> games, int page, int size) {
		return eventRepository.searchByTitleOrGame(now, LISTED, "%" + text + "%", games, PageRequest.of(page, size));
	}

	@Test
	void titleOrGameMatchReturnsEventsMatchingEitherWay() {
		Event byTitle = save("Elden gecesi", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		Event byGame = save("Boss avı", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED, GAME_A);
		save("Alakasız", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED, GAME_B);
		save("Alakasız oyunsuz", Duration.ofHours(4), Duration.ofHours(5), EventStatus.PLANNED);

		Page<Event> page = searchTitleOrGame("elden", Set.of(GAME_A), 0, 10);

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(byTitle.getId(), byGame.getId());
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void anEventMatchingByTitleAndByGameIsReturnedOnlyOnce() {
		Event both = save("Elden zindanı", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);

		Page<Event> page = searchTitleOrGame("elden", Set.of(GAME_A, GAME_B), 0, 10);

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(both.getId());
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	void descriptionIsNeverSearched() {
		Instant startsAt = now.plus(Duration.ofHours(1));
		eventRepository.saveAndFlush(Event.schedule(ORGANIZER_ID, EventType.OTHER, "Başlık", "gizli-kelime burada",
				startsAt, startsAt.plusSeconds(3600), now));
		entityManager.clear();

		assertThat(searchTitleOrGame("gizli-kelime", Set.of(GAME_A), 0, 10).getContent()).isEmpty();
		assertThat(titles(search("gizli-kelime", 0, 10))).isEmpty();
	}

	@Test
	void gameMatchStaysInsideTheListingScope() {
		Event upcoming = save("Yaklaşan", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);
		Event ongoing = save("Devam eden", Duration.ofHours(-1), Duration.ofHours(1), EventStatus.ACTIVE, GAME_A);
		save("Bitmiş", Duration.ofHours(-3), Duration.ofHours(-1), EventStatus.PLANNED, GAME_A);
		save("İptal", Duration.ofHours(1), Duration.ofHours(2), EventStatus.CANCELLED, GAME_A);
		save("Tamamlandı", Duration.ofHours(-3), Duration.ofHours(1), EventStatus.COMPLETED, GAME_A);

		Page<Event> page = searchTitleOrGame("yok-boyle-bir-baslik", Set.of(GAME_A), 0, 10);

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(ongoing.getId(), upcoming.getId());
	}

	@Test
	void titleOrGameResultsKeepTheListingOrderAndPaginateWithTotals() {
		Event third = save("C", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED, GAME_A);
		Event first = save("A", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);
		Event second = save("Gece B", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);
		save("D", Duration.ofHours(4), Duration.ofHours(5), EventStatus.PLANNED, GAME_B);

		Page<Event> firstPage = searchTitleOrGame("gece", Set.of(GAME_A), 0, 2);
		Page<Event> secondPage = searchTitleOrGame("gece", Set.of(GAME_A), 1, 2);

		assertThat(firstPage.getContent()).extracting(Event::getId).containsExactly(first.getId(), second.getId());
		assertThat(firstPage.getTotalElements()).isEqualTo(3);
		assertThat(firstPage.getTotalPages()).isEqualTo(2);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(secondPage.getContent()).extracting(Event::getId).containsExactly(third.getId());
		assertThat(secondPage.hasNext()).isFalse();
	}

	@Test
	void manyGameIdsCanBeSearchedAtOnce() {
		Event target = save("Hedef", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_C);
		Set<UUID> games = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			games.add(new UUID(1L, i));
		}
		games.add(GAME_C);

		assertThat(searchTitleOrGame("yok-boyle-bir-baslik", games, 0, 10).getContent())
				.extracting(Event::getId).containsExactly(target.getId());
	}
}
