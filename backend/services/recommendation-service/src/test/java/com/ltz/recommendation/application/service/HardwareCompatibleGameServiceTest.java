package com.ltz.recommendation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.GameMetadataSource.Batch;
import com.ltz.recommendation.application.port.HardwareAssessmentSource;
import com.ltz.recommendation.application.port.HardwareAssessmentSource.Requirement;
import com.ltz.recommendation.application.port.PersonalizationSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PersonalizationSource.Match;
import com.ltz.recommendation.application.port.RecommendationHistory;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HardwareCompatibleGameServiceTest {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PROFILE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID MEETS = id(1);
    private static final UUID UNKNOWN_MODEL = id(2);
    private static final UUID BELOW = id(3);
    private static final UUID NO_REQ = id(4);
    private static final UUID GONE = id(5);
    private static final UUID DISLIKED = id(6);
    private static final Actor ACTOR = new Actor(ACCOUNT, "Bearer x");

    private final PersonalizationSource personalization = mock(PersonalizationSource.class);
    private final HardwareAssessmentSource hardware = mock(HardwareAssessmentSource.class);
    private final GameMetadataSource games = mock(GameMetadataSource.class);
    private final PersonalizedRecommendationService consent = mock(PersonalizedRecommendationService.class);
    private final RecommendationHistory history = mock(RecommendationHistory.class);
    private final RecommendationRateLimiter limits = mock(RecommendationRateLimiter.class);
    private HardwareCompatibleGameService service;

    @BeforeEach
    void setup() {
        service = new HardwareCompatibleGameService(personalization, hardware, games, new HardwareRequirementEvaluator(), consent,
                history, limits, Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
        when(hardware.profile(ACTOR, PROFILE)).thenReturn(HardwareRequirementEvaluatorTest.profile(16, 512));
        when(history.notInterested(ACCOUNT)).thenReturn(Set.of(DISLIKED));
    }

    private static UUID id(int n) { return new UUID(0, n); }

    private static Requirement minimum(String cpu, String gpu, int ramMb) {
        return new Requirement("MINIMUM", cpu, gpu, ramMb, 10000, "Windows");
    }

    private void candidates(Match... matches) {
        when(personalization.candidates(any(), any())).thenReturn(List.of(matches));
    }

    private static Match match(UUID id, int affinity) { return new Match(id, affinity, List.of("rpg")); }

    @Test
    void provenMeetsRankFirstUnknownFollowsAndBelowMinimumIsExcluded() {
        candidates(match(UNKNOWN_MODEL, 9000), match(MEETS, 5000), match(BELOW, 10000), match(NO_REQ, 8000), match(GONE, 7000),
                match(DISLIKED, 10000));
        when(games.requirements(any(), any(), any())).thenReturn(new Batch(Map.of(
                MEETS, List.of(minimum("cpu model", "GPU MODEL", 8192)),
                UNKNOWN_MODEL, List.of(minimum("OTHER CPU", "OTHER GPU", 8192)),
                BELOW, List.of(minimum("cpu model", "GPU MODEL", 65536)),
                NO_REQ, List.of()), Set.of(GONE)));

        var result = service.recommend(ACTOR, PROFILE, "windows", "rpg", null);

        assertThat(result.items()).extracting(HardwareCompatibleGameService.Item::gameId)
                .containsExactly(MEETS, UNKNOWN_MODEL, NO_REQ);
        assertThat(result.items().getFirst().compatibility()).isEqualTo("MEETS_MINIMUM_COMPUTE");
        assertThat(result.items().get(1).compatibility()).isEqualTo("UNKNOWN");
        assertThat(result.items().get(2).reason()).isEqualTo("REQUIREMENTS_MISSING");
        assertThat(result.excludedBelowMinimum()).isEqualTo(1);
        assertThat(result.limitations()).contains("UNKNOWN_IS_NOT_KNOWN_INCOMPATIBLE", "SOME_CANDIDATES_UNAVAILABLE",
                "SOME_REQUIREMENTS_MISSING", "NO_CPU_GPU_MODEL_PERFORMANCE_ORDER");
        assertThat(result.algorithmVersion()).isEqualTo("hardware-compatible-v1");
        assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-11-18T10:00:00Z"));
        // Not-interested oyun kaynağa hiç sorulmaz.
        verify(games).requirements(any(), org.mockito.ArgumentMatchers.argThat(list -> !list.contains(DISLIKED) && list.size() == 5), any());
    }

    @Test
    void sizeLimitsResultsAndTiesAreDeterministic() {
        candidates(match(id(11), 5000), match(id(10), 5000), match(id(12), 5000));
        var same = List.of(minimum("cpu model", "GPU MODEL", 8192));
        when(games.requirements(any(), any(), any())).thenReturn(new Batch(Map.of(id(10), same, id(11), same, id(12), same), Set.of()));
        var result = service.recommend(ACTOR, PROFILE, "windows", null, 2);
        assertThat(result.items()).extracting(HardwareCompatibleGameService.Item::gameId).containsExactly(id(10), id(11));
    }

    @Test
    void noCandidatesDoesNotCallGameService() {
        candidates();
        var result = service.recommend(ACTOR, PROFILE, "windows", null, null);
        assertThat(result.items()).isEmpty();
        verify(games, never()).requirements(any(), any(), any());
    }

    @Test
    void invalidInputIsRejectedBeforeAnyQuotaOrSourceCall() {
        for (var call : List.<Runnable>of(
                () -> service.recommend(ACTOR, null, "windows", null, null),
                () -> service.recommend(ACTOR, PROFILE, null, null, null),
                () -> service.recommend(ACTOR, PROFILE, " ", null, null),
                () -> service.recommend(ACTOR, PROFILE, "windows", " ", null),
                () -> service.recommend(ACTOR, PROFILE, "windows", null, 0),
                () -> service.recommend(ACTOR, PROFILE, "windows", null, 21),
                () -> service.recommend(ACTOR, PROFILE, "x".repeat(65), null, null))) {
            assertThatThrownBy(call::run).isInstanceOf(IllegalArgumentException.class);
        }
        verify(limits, never()).acquire(any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(hardware, never()).profile(any(), any());
    }

    @Test
    void consentOwnershipAndGameFailuresFailClosedWithoutPartialResults() {
        org.mockito.Mockito.doThrow(new RecommendationRequestException(403, "CONSENT_REQUIRED")).when(consent).verifyConsent(ACTOR);
        assertThatThrownBy(() -> service.recommend(ACTOR, PROFILE, "windows", null, null))
                .isInstanceOfSatisfying(RecommendationRequestException.class, e -> assertThat(e.code()).isEqualTo("CONSENT_REQUIRED"));
        verify(hardware, never()).profile(any(), any());
    }

    @Test
    void foreignProfileAndUnavailableGameServiceStopTheRequest() {
        candidates(match(MEETS, 5000));
        when(hardware.profile(ACTOR, PROFILE)).thenThrow(new RecommendationRequestException(404, "SOURCE_NOT_FOUND"));
        assertThatThrownBy(() -> service.recommend(ACTOR, PROFILE, "windows", null, null))
                .isInstanceOfSatisfying(RecommendationRequestException.class, e -> assertThat(e.status()).isEqualTo(404));
        verify(games, never()).requirements(any(), any(), any());

        org.mockito.Mockito.doReturn(HardwareRequirementEvaluatorTest.profile(16, 512)).when(hardware).profile(ACTOR, PROFILE);
        when(games.requirements(any(), any(), any())).thenThrow(new RecommendationRequestException(503, "SOURCE_UNAVAILABLE"));
        assertThatThrownBy(() -> service.recommend(ACTOR, PROFILE, "windows", null, null))
                .isInstanceOfSatisfying(RecommendationRequestException.class, e -> assertThat(e.status()).isEqualTo(503));
    }
}
