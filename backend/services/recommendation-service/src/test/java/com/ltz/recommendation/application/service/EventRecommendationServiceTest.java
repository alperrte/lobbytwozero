package com.ltz.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.recommendation.application.port.EventDiscoverySource;
import com.ltz.recommendation.application.port.EventDiscoverySource.Event;
import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PlayerCandidateSource;
import com.ltz.recommendation.application.port.PlayerCandidateSource.Taste;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EventRecommendationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final UUID ME = new UUID(0, 7);
    private static final Actor ACTOR = new Actor(ME, "Bearer user");
    private static final UUID RPG_GAME = new UUID(1, 1);
    private static final UUID RACING_GAME = new UUID(1, 2);
    private static final UUID HIDDEN_GAME = new UUID(1, 3);

    private final EventDiscoverySource events = Mockito.mock(EventDiscoverySource.class);
    private final PlayerCandidateSource viewers = Mockito.mock(PlayerCandidateSource.class);
    private final GameMetadataSource games = Mockito.mock(GameMetadataSource.class);
    private final PersonalizedRecommendationService consent = Mockito.mock(PersonalizedRecommendationService.class);
    private final RecommendationRateLimiter limits = Mockito.mock(RecommendationRateLimiter.class);
    private final EventRecommendationService service = new EventRecommendationService(events, viewers, games, consent, limits,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Event event(int id, UUID organizer, UUID game, Set<String> platforms, String status, Instant endsAt) {
        return eventWith(id, organizer, game, platforms, status, endsAt, 10, 3, null, true);
    }

    private static Event eventWith(int id, UUID organizer, UUID game, Set<String> platforms, String status, Instant endsAt,
            Integer capacity, int participants, String viewerParticipation, boolean joinable) {
        return new Event(new UUID(2, id), organizer, "TOURNAMENT", game, platforms, "Event " + id,
                NOW.plusSeconds(3600L * id), endsAt, status, capacity, participants, viewerParticipation, joinable);
    }

    private static Event open(int id, UUID game, Set<String> platforms) {
        return event(id, new UUID(0, 99), game, platforms, "PLANNED", NOW.plusSeconds(86400));
    }

    private void given(Taste viewer, boolean truncated, Event... list) {
        Mockito.when(viewers.viewer(ACTOR)).thenReturn(viewer);
        Mockito.when(events.upcoming(ACTOR)).thenReturn(new EventDiscoverySource.Scan(List.of(list), truncated));
        Mockito.when(games.genres(Mockito.anyList())).thenReturn(Map.of(RPG_GAME, List.of("rpg"), RACING_GAME, List.of("racing")));
    }

    @Test
    void ranksByGenreThenPlatformAffinityWithStableOrderAndNeverClaimsParticipation() {
        given(new Taste(Set.of("rpg"), Set.of("nintendo-switch"), Set.of()), false,
                open(3, null, Set.of("NINTENDO_SWITCH")), open(2, RPG_GAME, Set.of()), open(1, RPG_GAME, Set.of("NINTENDO_SWITCH")),
                open(4, RACING_GAME, Set.of("XBOX")));
        var result = service.recommend(ACTOR, null);
        assertThat(result.items()).extracting(item -> item.eventId()).containsExactly(new UUID(2, 1), new UUID(2, 2), new UUID(2, 3));
        assertThat(result.items().getFirst().sharedGenres()).containsExactly("rpg");
        assertThat(result.items().getFirst().sharedPlatforms()).containsExactly("nintendoswitch");
        assertThat(result.items().get(0).score()).isEqualTo(10000).isGreaterThan(result.items().get(1).score());
        assertThat(result.items().get(1).score()).isGreaterThan(result.items().get(2).score());
        assertThat(result.limitations()).isEmpty();
        Mockito.verify(consent).verifyConsent(ACTOR);
    }

    @Test
    void fullAlreadyJoinedAndNotJoinableEventsAreNeverRecommendedButUnlimitedOnesAre() {
        Instant ends = NOW.plusSeconds(86400);
        given(new Taste(Set.of("rpg"), Set.of(), Set.of()), false,
                eventWith(1, new UUID(0, 99), RPG_GAME, Set.of(), "PLANNED", ends, 5, 5, null, false),
                eventWith(2, new UUID(0, 99), RPG_GAME, Set.of(), "PLANNED", ends, 5, 2, "ACTIVE", false),
                eventWith(3, new UUID(0, 99), RPG_GAME, Set.of(), "PLANNED", ends, null, 40, null, true),
                eventWith(4, new UUID(0, 99), RPG_GAME, Set.of(), "PLANNED", ends, 5, 4, null, true));
        var result = service.recommend(ACTOR, 5);
        assertThat(result.items()).extracting(item -> item.eventId()).containsExactly(new UUID(2, 3), new UUID(2, 4));
    }

    @Test
    void cancelledFinishedOwnAndHiddenGameEventsAreNeverRecommended() {
        given(new Taste(Set.of("rpg"), Set.of(), Set.of()), true,
                event(1, new UUID(0, 99), RPG_GAME, Set.of(), "CANCELLED", NOW.plusSeconds(60)),
                event(2, new UUID(0, 99), RPG_GAME, Set.of(), "COMPLETED", NOW.plusSeconds(60)),
                event(3, new UUID(0, 99), RPG_GAME, Set.of(), "ACTIVE", NOW.minusSeconds(1)),
                event(4, ME, RPG_GAME, Set.of(), "PLANNED", NOW.plusSeconds(60)),
                open(5, HIDDEN_GAME, Set.of()),
                open(6, RPG_GAME, Set.of()));
        var result = service.recommend(ACTOR, 5);
        assertThat(result.items()).extracting(item -> item.eventId()).containsExactly(new UUID(2, 6));
        assertThat(result.limitations()).containsExactly("EVENT_SCAN_TRUNCATED");
    }

    @Test
    void viewerWithoutPreferencesGetsNoEventsAndNoEventCall() {
        Mockito.when(viewers.viewer(ACTOR)).thenReturn(new Taste(Set.of(), Set.of(), Set.of()));
        var result = service.recommend(ACTOR, 5);
        assertThat(result.items()).isEmpty();
        assertThat(result.limitations()).contains("VIEWER_PREFERENCES_MISSING");
        Mockito.verifyNoInteractions(events);
    }

    @Test
    void invalidSizeIsRejectedBeforeAnySourceIsCalled() {
        for (int size : new int[] {0, -1, 21}) {
            assertThatThrownBy(() -> service.recommend(ACTOR, size)).isInstanceOf(IllegalArgumentException.class);
        }
        Mockito.verifyNoInteractions(events, viewers, consent);
    }

    @Test
    void missingConsentOrUnavailableEventServiceStopsTheRun() {
        Mockito.doThrow(new RecommendationRequestException(403, "CONSENT_REQUIRED")).when(consent).verifyConsent(ACTOR);
        assertThatThrownBy(() -> service.recommend(ACTOR, 5)).isInstanceOfSatisfying(RecommendationRequestException.class,
                e -> assertThat(e.code()).isEqualTo("CONSENT_REQUIRED"));
        Mockito.verifyNoInteractions(events);

        Mockito.reset(consent);
        Mockito.when(viewers.viewer(ACTOR)).thenReturn(new Taste(Set.of("rpg"), Set.of(), Set.of()));
        Mockito.when(events.upcoming(ACTOR)).thenThrow(new RecommendationRequestException(503, "SOURCE_UNAVAILABLE"));
        assertThatThrownBy(() -> service.recommend(ACTOR, 5)).isInstanceOfSatisfying(RecommendationRequestException.class,
                e -> assertThat(e.code()).isEqualTo("SOURCE_UNAVAILABLE"));
    }
}
