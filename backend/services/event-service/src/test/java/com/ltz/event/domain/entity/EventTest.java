package com.ltz.event.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;
import com.ltz.event.domain.exception.InvalidEventScheduleException;
import com.ltz.event.domain.exception.InvalidEventScheduleException.Reason;

class EventTest {

	private static final UUID ORGANIZER_ID = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

	private static Event schedule(Instant startsAt, Instant endsAt) {
		return Event.schedule(ORGANIZER_ID, EventType.TOURNAMENT, "Başlık", "Açıklama", startsAt, endsAt, NOW);
	}

	@Test
	void newEventIsPlannedAndOwnedByTheOrganizer() {
		Instant startsAt = NOW.plusSeconds(3600);
		Instant endsAt = NOW.plusSeconds(7200);

		Event event = schedule(startsAt, endsAt);

		assertThat(event.getOrganizerId()).isEqualTo(ORGANIZER_ID);
		assertThat(event.getType()).isEqualTo(EventType.TOURNAMENT);
		assertThat(event.getTitle()).isEqualTo("Başlık");
		assertThat(event.getDescription()).isEqualTo("Açıklama");
		assertThat(event.getStartsAt()).isEqualTo(startsAt);
		assertThat(event.getEndsAt()).isEqualTo(endsAt);
		assertThat(event.getStatus()).isEqualTo(EventStatus.PLANNED);
		assertThat(event.getCreatedAt()).isEqualTo(NOW);
		assertThat(event.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	void startInThePastIsRejected() {
		assertThatThrownBy(() -> schedule(NOW.minusSeconds(1), NOW.plusSeconds(3600)))
				.isInstanceOfSatisfying(InvalidEventScheduleException.class,
						exception -> assertThat(exception.getReason()).isEqualTo(Reason.STARTS_IN_THE_PAST));
	}

	@Test
	void startExactlyNowIsRejected() {
		assertThatThrownBy(() -> schedule(NOW, NOW.plusSeconds(3600)))
				.isInstanceOfSatisfying(InvalidEventScheduleException.class,
						exception -> assertThat(exception.getReason()).isEqualTo(Reason.STARTS_IN_THE_PAST));
	}

	@Test
	void endBeforeStartIsRejected() {
		assertThatThrownBy(() -> schedule(NOW.plusSeconds(7200), NOW.plusSeconds(3600)))
				.isInstanceOfSatisfying(InvalidEventScheduleException.class,
						exception -> assertThat(exception.getReason()).isEqualTo(Reason.ENDS_NOT_AFTER_START));
	}

	@Test
	void endEqualToStartIsRejected() {
		Instant same = NOW.plusSeconds(3600);

		assertThatThrownBy(() -> schedule(same, same))
				.isInstanceOfSatisfying(InvalidEventScheduleException.class,
						exception -> assertThat(exception.getReason()).isEqualTo(Reason.ENDS_NOT_AFTER_START));
	}

	@Test
	void timesAreTruncatedToMillisSoSubMillisecondDifferencesCannotReachTheDatabase() {
		Instant startsAt = NOW.plusSeconds(3600).plusNanos(400);
		Instant endsAt = NOW.plusSeconds(3600).plusNanos(600);

		assertThatThrownBy(() -> schedule(startsAt, endsAt))
				.isInstanceOfSatisfying(InvalidEventScheduleException.class,
						exception -> assertThat(exception.getReason()).isEqualTo(Reason.ENDS_NOT_AFTER_START));
	}

	@Test
	void storedTimesHaveMillisecondPrecision() {
		Event event = schedule(NOW.plusSeconds(3600).plusNanos(1_234_567), NOW.plusSeconds(7200).plusNanos(9_999_999));

		assertThat(event.getStartsAt()).isEqualTo(NOW.plusSeconds(3600).plusMillis(1));
		assertThat(event.getEndsAt()).isEqualTo(NOW.plusSeconds(7200).plusMillis(9));
	}

	@Test
	void pastStartIsReportedBeforeAnInvalidRange() {
		assertThatThrownBy(() -> schedule(NOW.minusSeconds(7200), NOW.minusSeconds(3600)))
				.isInstanceOfSatisfying(InvalidEventScheduleException.class,
						exception -> assertThat(exception.getReason()).isEqualTo(Reason.STARTS_IN_THE_PAST));
	}

	@Test
	void gameIdIsCarriedWhenGiven() {
		UUID gameId = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");

		Event event = Event.schedule(ORGANIZER_ID, EventType.GAME_NIGHT, gameId, "Başlık", "Açıklama",
				NOW.plusSeconds(3600), NOW.plusSeconds(7200), NOW);

		assertThat(event.getGameId()).isEqualTo(gameId);
	}

	@Test
	void eventWithoutGameHasNoGameId() {
		assertThat(schedule(NOW.plusSeconds(3600), NOW.plusSeconds(7200)).getGameId()).isNull();
	}
}
