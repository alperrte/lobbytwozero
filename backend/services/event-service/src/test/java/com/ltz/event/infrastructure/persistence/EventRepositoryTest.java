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

import com.ltz.event.application.port.EventSearchCriteria;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventPlatform;
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
		return saveTyped(title, EventType.COMMUNITY, startOffset, endOffset, status, gameId);
	}

	private Event saveTyped(String title, EventType type, Duration startOffset, Duration endOffset, EventStatus status,
			UUID gameId) {
		return saveFull(title, type, startOffset, endOffset, status, gameId, Set.of());
	}

	private Event saveFull(String title, EventType type, Duration startOffset, Duration endOffset, EventStatus status,
			UUID gameId, Set<EventPlatform> platforms) {
		Instant startsAt = now.plus(startOffset);
		Event event = Event.schedule(ORGANIZER_ID, type, gameId, platforms, title, "Açıklama", startsAt,
				now.plus(endOffset), startsAt.minus(Duration.ofMinutes(1)));
		Event saved = eventRepository.saveAndFlush(event);
		if (status != EventStatus.PLANNED) {
			jdbcTemplate.update("update events set status = ? where id = ?", status.name(), saved.getId());
		}
		entityManager.clear();
		return saved;
	}

	private EventSearchCriteria criteria(String pattern, Set<UUID> games, Set<EventType> types, UUID gameId,
			Instant from, Instant to) {
		return criteria(pattern, games, types, Set.of(), gameId, from, to);
	}

	private EventSearchCriteria criteria(String pattern, Set<UUID> games, Set<EventType> types,
			Set<EventPlatform> platforms, UUID gameId, Instant from, Instant to) {
		return new EventSearchCriteria(now, LISTED, pattern, games, types, platforms, gameId, from, to);
	}

	private Page<Event> list(int page, int size) {
		return eventRepository.search(criteria(null, Set.of(), Set.of(), null, null, null), PageRequest.of(page, size));
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
		return eventRepository.search(criteria("%" + text + "%", Set.of(), Set.of(), null, null, null),
				PageRequest.of(page, size));
	}

	private Page<Event> searchPattern(String pattern) {
		return eventRepository.search(criteria(pattern, Set.of(), Set.of(), null, null, null), PageRequest.of(0, 10));
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
		assertThat(titles(searchPattern("%100!%%")))
				.containsExactly("Yüzde 100% eğlence");
		assertThat(titles(searchPattern("%a!_b%")))
				.containsExactly("a_b etkinlik");
		assertThat(titles(searchPattern("%!!%")))
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
		return eventRepository.search(criteria("%" + text + "%", Set.copyOf(games), Set.of(), null, null, null),
				PageRequest.of(page, size));
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

	// ------------------------------------------------------------------ EVT-06 filtreler

	private Page<Event> filter(Set<EventType> types, UUID gameId, Instant from, Instant to) {
		return eventRepository.search(criteria(null, Set.of(), types, gameId, from, to), PageRequest.of(0, 20));
	}

	@Test
	void filtersByEventType() {
		save("Topluluk", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		Event tournament = saveTyped("Turnuva", EventType.TOURNAMENT, Duration.ofHours(2), Duration.ofHours(3),
				EventStatus.PLANNED, null);
		Event poll = saveTyped("Oylama", EventType.POLL, Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED, null);
		saveTyped("Gece", EventType.GAME_NIGHT, Duration.ofHours(4), Duration.ofHours(5), EventStatus.PLANNED, null);

		assertThat(filter(Set.of(EventType.TOURNAMENT, EventType.POLL), null, null, null).getContent())
				.extracting(Event::getId).containsExactly(tournament.getId(), poll.getId());
		assertThat(titles(filter(Set.of(EventType.GAME_NIGHT), null, null, null))).containsExactly("Gece");
		assertThat(filter(Set.of(EventType.OTHER), null, null, null).getContent()).isEmpty();
	}

	@Test
	void filtersByGame() {
		Event a1 = save("A1", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);
		Event a2 = save("A2", Duration.ofHours(2), Duration.ofHours(3), EventStatus.ACTIVE, GAME_A);
		save("B", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED, GAME_B);
		save("Oyunsuz", Duration.ofHours(4), Duration.ofHours(5), EventStatus.PLANNED);

		assertThat(filter(Set.of(), GAME_A, null, null).getContent()).extracting(Event::getId)
				.containsExactly(a1.getId(), a2.getId());
		assertThat(titles(filter(Set.of(), GAME_B, null, null))).containsExactly("B");
		assertThat(filter(Set.of(), GAME_C, null, null).getContent()).isEmpty();
	}

	@Test
	void gameFilterStaysInsideTheListingScope() {
		save("Yaklaşan", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);
		save("İptal", Duration.ofHours(1), Duration.ofHours(2), EventStatus.CANCELLED, GAME_A);
		save("Bitmiş", Duration.ofHours(-3), Duration.ofHours(-1), EventStatus.PLANNED, GAME_A);

		assertThat(titles(filter(Set.of(), GAME_A, null, null))).containsExactly("Yaklaşan");
	}

	@Test
	void dateRangeKeepsEventsWhoseTimeOverlapsTheRange() {
		save("E1 erken", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("E2 aralıkta", Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED);
		save("E3 devam eden", Duration.ofHours(-1), Duration.ofHours(3), EventStatus.PLANNED);
		save("E4 geç", Duration.ofHours(10), Duration.ofHours(11), EventStatus.PLANNED);
		Instant from = now.plus(Duration.ofMinutes(150));
		Instant to = now.plus(Duration.ofMinutes(330));

		assertThat(titles(filter(Set.of(), null, from, to))).containsExactly("E3 devam eden", "E2 aralıkta");
	}

	@Test
	void dateRangeBoundariesAreFromInclusiveOfOverlapAndToExclusive() {
		save("Tam from'da biten", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Tam from'da başlayan", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);
		save("Tam to'da başlayan", Duration.ofHours(4), Duration.ofHours(5), EventStatus.PLANNED);
		Instant from = now.plus(Duration.ofHours(2));
		Instant to = now.plus(Duration.ofHours(4));

		assertThat(titles(filter(Set.of(), null, from, to))).containsExactly("Tam from'da başlayan");
	}

	@Test
	void openEndedDateRanges() {
		save("Erken", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Orta", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED);
		save("Geç", Duration.ofHours(6), Duration.ofHours(7), EventStatus.PLANNED);
		Instant middle = now.plus(Duration.ofHours(5));

		assertThat(titles(filter(Set.of(), null, middle, null))).containsExactly("Geç");
		assertThat(titles(filter(Set.of(), null, null, middle))).containsExactly("Erken", "Orta");
	}

	@Test
	void aPastFromDoesNotBringBackEventsOutsideTheListingScope() {
		save("Bitmiş", Duration.ofHours(-3), Duration.ofHours(-1), EventStatus.PLANNED);
		save("Yaklaşan", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);

		assertThat(titles(filter(Set.of(), null, now.minus(Duration.ofDays(30)), null))).containsExactly("Yaklaşan");
	}

	@Test
	void filtersCombineWithAnd() {
		Event match = saveTyped("Eşleşen", EventType.TOURNAMENT, Duration.ofHours(5), Duration.ofHours(6),
				EventStatus.PLANNED, GAME_A);
		saveTyped("Yanlış tür", EventType.POLL, Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED, GAME_A);
		saveTyped("Yanlış oyun", EventType.TOURNAMENT, Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED, GAME_B);
		saveTyped("Yanlış tarih", EventType.TOURNAMENT, Duration.ofHours(20), Duration.ofHours(21), EventStatus.PLANNED, GAME_A);

		Page<Event> page = filter(Set.of(EventType.TOURNAMENT), GAME_A, now.plus(Duration.ofHours(4)),
				now.plus(Duration.ofHours(8)));

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(match.getId());
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	void filtersApplyToTheTitleOrGameSearchResult() {
		Event byTitle = saveTyped("Elden turnuvası", EventType.TOURNAMENT, Duration.ofHours(1), Duration.ofHours(2),
				EventStatus.PLANNED, null);
		Event byGame = saveTyped("Boss avı", EventType.TOURNAMENT, Duration.ofHours(2), Duration.ofHours(3),
				EventStatus.PLANNED, GAME_A);
		saveTyped("Elden gecesi", EventType.GAME_NIGHT, Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED, null);
		saveTyped("Boss gecesi", EventType.GAME_NIGHT, Duration.ofHours(4), Duration.ofHours(5), EventStatus.PLANNED, GAME_A);

		Page<Event> page = eventRepository.search(
				criteria("%elden%", Set.of(GAME_A), Set.of(EventType.TOURNAMENT), null, null, null), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(byTitle.getId(), byGame.getId());
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void filteredResultsPaginateWithFilteredTotals() {
		for (int i = 1; i <= 5; i++) {
			saveTyped("Turnuva " + i, EventType.TOURNAMENT, Duration.ofHours(i), Duration.ofHours(i + 1), EventStatus.PLANNED,
					null);
		}
		save("Topluluk", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);

		Page<Event> first = eventRepository.search(criteria(null, Set.of(), Set.of(EventType.TOURNAMENT), null, null, null),
				PageRequest.of(0, 2));
		Page<Event> last = eventRepository.search(criteria(null, Set.of(), Set.of(EventType.TOURNAMENT), null, null, null),
				PageRequest.of(2, 2));

		assertThat(titles(first)).containsExactly("Turnuva 1", "Turnuva 2");
		assertThat(first.getTotalElements()).isEqualTo(5);
		assertThat(first.getTotalPages()).isEqualTo(3);
		assertThat(titles(last)).containsExactly("Turnuva 5");
		assertThat(last.hasNext()).isFalse();
	}

	@Test
	void withoutFiltersTheFullListingScopeIsReturned() {
		save("1", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("2", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED, GAME_A);
		save("İptal", Duration.ofHours(1), Duration.ofHours(2), EventStatus.CANCELLED);

		assertThat(titles(filter(Set.of(), null, null, null))).containsExactly("1", "2");
	}

	@Test
	void aSingleQuoteInTheSearchTextIsHarmless() {
		save("O'Brien gecesi", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Başka", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);

		assertThat(titles(search("o'brien", 0, 10))).containsExactly("O'Brien gecesi");
		assertThat(titles(search("'; drop table events; --", 0, 10))).isEmpty();
	}

	// ------------------------------------------------------------------ EVT-06 platform (etkinliğin kendi alanı)

	private static final Set<EventPlatform> PC = Set.of(EventPlatform.PC);
	private static final Set<EventPlatform> XBOX = Set.of(EventPlatform.XBOX);

	private Page<Event> byPlatform(Set<EventPlatform> platforms) {
		return eventRepository.search(criteria(null, Set.of(), Set.of(), platforms, null, null, null), PageRequest.of(0, 20));
	}

	@Test
	void platformsArePersistedAsTheEventsOwnRowsAndLoadedInEnumOrder() {
		Event saved = saveFull("Çapraz", EventType.TOURNAMENT, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED,
				null, Set.of(EventPlatform.VR, EventPlatform.PC, EventPlatform.XBOX));

		Event loaded = eventRepository.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getPlatforms()).containsExactly(EventPlatform.PC, EventPlatform.XBOX, EventPlatform.VR);
		assertThat(jdbcTemplate.queryForObject("select count(*) from event_platforms where event_id = ?", Long.class,
				saved.getId())).isEqualTo(3L);
	}

	@Test
	void eventsWithoutPlatformsHaveNoRowsAndLoadAsEmpty() {
		Event saved = save("Platformsuz", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);

		assertThat(eventRepository.findById(saved.getId()).orElseThrow().getPlatforms()).isEmpty();
		assertThat(jdbcTemplate.queryForObject("select count(*) from event_platforms where event_id = ?", Long.class,
				saved.getId())).isZero();
	}

	@Test
	void deletingAnEventCascadesToItsPlatforms() {
		Event saved = saveFull("Silinecek", EventType.OTHER, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED,
				null, PC);

		assertThat(jdbcTemplate.update("delete from events where id = ?", saved.getId())).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from event_platforms where event_id = ?", Long.class,
				saved.getId())).isZero();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void databaseRejectsPlatformValuesOutsideTheVocabulary() {
		Instant start = now.plus(Duration.ofHours(1));
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("insert into events (id, organizer_id, type, title, description, starts_at, ends_at, status, "
				+ "created_at, updated_at) values (?, ?, 'OTHER', 'T', 'D', ?, ?, 'PLANNED', now(), now())", id, ORGANIZER_ID,
				Timestamp.from(start), Timestamp.from(start.plusSeconds(60)));
		try {
			assertThatThrownBy(() -> jdbcTemplate.update("insert into event_platforms (event_id, platform) values (?, 'PS5')", id))
					.isInstanceOf(DataIntegrityViolationException.class);
			assertThatThrownBy(() -> jdbcTemplate.update(
					"insert into event_platforms (event_id, platform) values (?, 'pc')", id))
					.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			jdbcTemplate.update("delete from events where id = ?", id);
		}
	}

	@Test
	void platformFilterAloneKeepsOnlyEventsWithThatPlatform() {
		Event pc = saveFull("PC", EventType.COMMUNITY, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, null, PC);
		saveFull("Xbox", EventType.COMMUNITY, Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED, null, XBOX);
		save("Platformsuz", Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED);

		Page<Event> page = byPlatform(PC);

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(pc.getId());
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	void eventsWithoutAPlatformNeverMatchAPlatformFilter() {
		save("Platformsuz", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED);
		save("Oyunlu platformsuz", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED, GAME_A);

		for (EventPlatform platform : EventPlatform.values()) {
			assertThat(byPlatform(Set.of(platform)).getContent()).as(platform.name()).isEmpty();
		}
	}

	@Test
	void platformIsNotDerivedFromTheLinkedGameAndWorksWithoutAGame() {
		Event linkedNoPlatform = save("Oyunlu", Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, GAME_A);
		Event platformNoGame = saveFull("Oyunsuz platformlu", EventType.GAME_NIGHT, Duration.ofHours(2), Duration.ofHours(3),
				EventStatus.PLANNED, null, PC);

		Page<Event> page = byPlatform(PC);

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(platformNoGame.getId());
		assertThat(page.getContent()).extracting(Event::getId).doesNotContain(linkedNoPlatform.getId());
	}

	@Test
	void multiplePlatformsMatchAnyOfThemAndEachEventIsReturnedOnce() {
		Event both = saveFull("Çapraz", EventType.TOURNAMENT, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED,
				null, Set.of(EventPlatform.PC, EventPlatform.XBOX));
		Event onlyPc = saveFull("Yalnız PC", EventType.TOURNAMENT, Duration.ofHours(2), Duration.ofHours(3),
				EventStatus.PLANNED, null, PC);
		saveFull("Yalnız VR", EventType.TOURNAMENT, Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED, null,
				Set.of(EventPlatform.VR));

		Page<Event> page = byPlatform(Set.of(EventPlatform.PC, EventPlatform.XBOX));

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(both.getId(), onlyPc.getId());
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void platformFilterStaysInsideTheListingScope() {
		saveFull("Yaklaşan", EventType.OTHER, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, null, PC);
		saveFull("İptal", EventType.OTHER, Duration.ofHours(1), Duration.ofHours(2), EventStatus.CANCELLED, null, PC);
		saveFull("Bitmiş", EventType.OTHER, Duration.ofHours(-3), Duration.ofHours(-1), EventStatus.PLANNED, null, PC);
		saveFull("Tamamlandı", EventType.OTHER, Duration.ofHours(-3), Duration.ofHours(1), EventStatus.COMPLETED, null, PC);

		assertThat(titles(byPlatform(PC))).containsExactly("Yaklaşan");
	}

	@Test
	void platformFilterCombinesWithTypeGameAndDateRange() {
		Event match = saveFull("Eşleşen", EventType.TOURNAMENT, Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED,
				GAME_A, PC);
		saveFull("Yanlış platform", EventType.TOURNAMENT, Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED,
				GAME_A, XBOX);
		saveFull("Platformsuz", EventType.TOURNAMENT, Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED, GAME_A,
				Set.of());
		saveFull("Yanlış tür", EventType.POLL, Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED, GAME_A, PC);
		saveFull("Yanlış oyun", EventType.TOURNAMENT, Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED, GAME_B,
				PC);
		saveFull("Yanlış tarih", EventType.TOURNAMENT, Duration.ofHours(20), Duration.ofHours(21), EventStatus.PLANNED,
				GAME_A, PC);

		Page<Event> page = eventRepository.search(criteria(null, Set.of(), Set.of(EventType.TOURNAMENT), PC, GAME_A,
				now.plus(Duration.ofHours(4)), now.plus(Duration.ofHours(8))), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(Event::getId).containsExactly(match.getId());
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	void platformFilterKeepsTheDateOverlapRule() {
		saveFull("Erken", EventType.OTHER, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, null, PC);
		saveFull("Devam eden", EventType.OTHER, Duration.ofHours(-1), Duration.ofHours(3), EventStatus.PLANNED, null, PC);
		saveFull("Aralıkta", EventType.OTHER, Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED, null, PC);
		saveFull("Geç", EventType.OTHER, Duration.ofHours(10), Duration.ofHours(11), EventStatus.PLANNED, null, PC);

		Page<Event> page = eventRepository.search(criteria(null, Set.of(), Set.of(), PC, null,
				now.plus(Duration.ofMinutes(150)), now.plus(Duration.ofMinutes(330))), PageRequest.of(0, 20));

		assertThat(titles(page)).containsExactly("Devam eden", "Aralıkta");
	}

	@Test
	void platformFilterAppliesToTheTitleOrGameSearch() {
		saveFull("Elden PC gecesi", EventType.GAME_NIGHT, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, null,
				PC);
		saveFull("Boss avı PC", EventType.GAME_NIGHT, Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED, GAME_A, PC);
		saveFull("Elden Xbox gecesi", EventType.GAME_NIGHT, Duration.ofHours(3), Duration.ofHours(4), EventStatus.PLANNED,
				null, XBOX);
		saveFull("Boss avı Xbox", EventType.GAME_NIGHT, Duration.ofHours(4), Duration.ofHours(5), EventStatus.PLANNED, GAME_A,
				XBOX);
		save("Elden platformsuz", Duration.ofHours(5), Duration.ofHours(6), EventStatus.PLANNED);

		Page<Event> page = eventRepository.search(criteria("%elden%", Set.of(GAME_A), Set.of(), PC, null, null, null),
				PageRequest.of(0, 20));

		assertThat(titles(page)).containsExactly("Elden PC gecesi", "Boss avı PC");
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void platformFilteredResultsPaginateWithFilteredTotals() {
		for (int i = 1; i <= 5; i++) {
			saveFull("PC " + i, EventType.OTHER, Duration.ofHours(i), Duration.ofHours(i + 1), EventStatus.PLANNED, null,
					Set.of(EventPlatform.PC, EventPlatform.XBOX));
		}
		saveFull("Yalnız VR", EventType.OTHER, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, null,
				Set.of(EventPlatform.VR));

		Page<Event> first = eventRepository.search(criteria(null, Set.of(), Set.of(), PC, null, null, null),
				PageRequest.of(0, 2));
		Page<Event> last = eventRepository.search(criteria(null, Set.of(), Set.of(), PC, null, null, null),
				PageRequest.of(2, 2));

		assertThat(titles(first)).containsExactly("PC 1", "PC 2");
		assertThat(first.getTotalElements()).isEqualTo(5);
		assertThat(first.getTotalPages()).isEqualTo(3);
		assertThat(titles(last)).containsExactly("PC 5");
		assertThat(last.hasNext()).isFalse();
	}

	@Test
	void withoutAPlatformFilterEventsWithAndWithoutPlatformsAreAllListed() {
		saveFull("Platformlu", EventType.OTHER, Duration.ofHours(1), Duration.ofHours(2), EventStatus.PLANNED, null, PC);
		save("Platformsuz", Duration.ofHours(2), Duration.ofHours(3), EventStatus.PLANNED);

		assertThat(titles(list(0, 10))).containsExactly("Platformlu", "Platformsuz");
	}

	@Test
	void listedEventsCarryTheirPlatforms() {
		for (int i = 1; i <= 5; i++) {
			saveFull("E" + i, EventType.OTHER, Duration.ofHours(i), Duration.ofHours(i + 1), EventStatus.PLANNED, null,
					Set.of(EventPlatform.PC, EventPlatform.VR));
		}

		Page<Event> page = list(0, 10);

		assertThat(page.getContent()).hasSize(5)
				.allSatisfy(event -> assertThat(event.getPlatforms()).containsExactly(EventPlatform.PC, EventPlatform.VR));
	}
}
