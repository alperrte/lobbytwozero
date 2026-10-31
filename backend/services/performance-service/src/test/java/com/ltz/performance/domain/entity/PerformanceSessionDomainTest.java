package com.ltz.performance.domain.entity;

import com.ltz.performance.api.dto.request.EndSessionRequest;
import com.ltz.performance.api.dto.request.ReportRequest;
import com.ltz.performance.domain.enums.GraphicsPreset;
import com.ltz.performance.domain.enums.MeasurementSource;
import com.ltz.performance.domain.enums.SessionStatus;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceUnprocessableException;
import com.ltz.performance.infrastructure.client.PerformanceClientProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PerformanceSessionDomainTest {
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final URI GAME = URI.create("http://game:8080");
    private static final URI HARDWARE = URI.create("https://hardware.example");
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private static PerformanceSession active() {
        return PerformanceSession.startManual(UUID.randomUUID(), UUID.randomUUID(), "Oyun", null, null, T0);
    }

    private static ReportValues values(Double avg, Double p1, String notes) {
        return new ReportValues("1920x1080", GraphicsPreset.HIGH, avg, p1, null, null, null, null, notes);
    }

    // ---------- PerformanceSession ----------

    @Test
    void startManualCreatesActiveManualSessionWithGivenReferences() {
        UUID owner = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID link = UUID.randomUUID();
        var session = PerformanceSession.startManual(owner, UUID.randomUUID(), "  Hades\u0007 II ", profile, link, T0);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getSource()).isEqualTo(MeasurementSource.MANUAL);
        assertThat(session.getGameTitle()).isEqualTo("Hades  II");
        assertThat(session.getHardwareProfileId()).isEqualTo(profile);
        assertThat(session.getDeviceLinkId()).isEqualTo(link);
        assertThat(session.getStartedAt()).isEqualTo(T0);
        assertThat(session.getEndedAt()).isNull();
        assertThat(session.durationSeconds()).isNull();
        assertThat(session.isActive()).isTrue();
        assertThat(session.isCompleted()).isFalse();
        assertThat(session.isOwnedBy(owner)).isTrue();
        assertThat(session.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void startManualRejectsMissingRequiredValues() {
        UUID user = UUID.randomUUID();
        UUID game = UUID.randomUUID();
        assertThatThrownBy(() -> PerformanceSession.startManual(null, game, "Oyun", null, null, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PerformanceSession.startManual(user, null, "Oyun", null, null, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PerformanceSession.startManual(user, game, "Oyun", null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PerformanceSession.startManual(user, game, "  ", null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void titleIsNormalizedAndBoundedByCodePoints() {
        assertThat(PerformanceSession.normalizeTitle("a\nb")).isEqualTo("a b");
        assertThatThrownBy(() -> PerformanceSession.normalizeTitle(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PerformanceSession.normalizeTitle("\u0000\n")).isInstanceOf(IllegalArgumentException.class);
        assertThat(PerformanceSession.normalizeTitle("x".repeat(500))).hasSize(PerformanceSession.TITLE_MAX);
        String emoji = "😀".repeat(300);
        assertThat(PerformanceSession.normalizeTitle(emoji).codePointCount(0, PerformanceSession.normalizeTitle(emoji).length()))
                .isEqualTo(PerformanceSession.TITLE_MAX);
    }

    @Test
    void endCompletesOnceAndRepeatedSameStatusChangesNothing() {
        var session = active();
        session.end(SessionStatus.COMPLETED, T0.plusSeconds(90));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getEndedAt()).isEqualTo(T0.plusSeconds(90));
        assertThat(session.durationSeconds()).isEqualTo(90);
        assertThat(session.isCompleted()).isTrue();
        assertThat(session.isActive()).isFalse();

        session.end(SessionStatus.COMPLETED, T0.plusSeconds(500));
        assertThat(session.getEndedAt()).isEqualTo(T0.plusSeconds(90));
        assertThat(session.getUpdatedAt()).isEqualTo(T0.plusSeconds(90));
    }

    @Test
    void endNeverOverwritesADifferentFinalStatus() {
        var completed = active();
        Instant first = T0.plusSeconds(10);
        Instant later = T0.plusSeconds(20);
        completed.end(SessionStatus.COMPLETED, first);
        assertThatThrownBy(() -> completed.end(SessionStatus.ABANDONED, later))
                .isInstanceOfSatisfying(PerformanceConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(PerformanceConflictException.SESSION_ALREADY_ENDED));
        var abandoned = active();
        abandoned.end(SessionStatus.ABANDONED, first);
        assertThatThrownBy(() -> abandoned.end(SessionStatus.COMPLETED, later))
                .isInstanceOf(PerformanceConflictException.class);
        assertThat(abandoned.getStatus()).isEqualTo(SessionStatus.ABANDONED);
    }

    @Test
    void endRejectsActiveOrNullTargetAndClampsClockSkew() {
        var session = active();
        assertThatThrownBy(() -> session.end(null, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> session.end(SessionStatus.ACTIVE, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(session.isActive()).isTrue();
        // saat geriye kayarsa bitiş başlangıçtan önce olamaz
        session.end(SessionStatus.COMPLETED, T0.minusSeconds(30));
        assertThat(session.getEndedAt()).isEqualTo(T0);
        assertThat(session.durationSeconds()).isZero();
    }

    // ---------- ReportValues / PerformanceReport ----------

    @Test
    void reportValuesRequireAtLeastOneMeasurementAndNeverInventData() {
        assertThatThrownBy(() -> values(null, null, "yalnız not"))
                .isInstanceOfSatisfying(PerformanceUnprocessableException.class,
                        e -> assertThat(e.code()).isEqualTo(PerformanceUnprocessableException.REPORT_EMPTY));
        var onlyTemp = new ReportValues("1280x720", GraphicsPreset.LOW, null, null, 70.0, null, null, null, null);
        assertThat(onlyTemp.avgFps()).isNull();
        assertThat(onlyTemp.p1LowFps()).isNull();
        assertThat(onlyTemp.maxCpuTempC()).isEqualTo(70.0);
    }

    @Test
    void reportValuesRejectLowFpsAboveAverageAndMissingKeys() {
        assertThatThrownBy(() -> values(50.0, 60.0, null))
                .isInstanceOfSatisfying(PerformanceUnprocessableException.class,
                        e -> assertThat(e.code()).isEqualTo(PerformanceUnprocessableException.REPORT_INCONSISTENT));
        assertThat(values(60.0, 60.0, null).p1LowFps()).isEqualTo(60.0);
        assertThat(values(null, 90.0, null).p1LowFps()).isEqualTo(90.0);
        assertThatThrownBy(() -> new ReportValues(null, GraphicsPreset.LOW, 60.0, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReportValues("1920x1080", null, 60.0, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void reportNotesAreNormalizedAndBounded() {
        assertThat(values(60.0, null, null).notes()).isNull();
        assertThat(values(60.0, null, " \n\u0000 ").notes()).isNull();
        assertThat(values(60.0, null, "  a\u0000b\nc  ").notes()).isEqualTo("a b c");
        assertThat(values(60.0, null, "x".repeat(900)).notes()).hasSize(ReportValues.NOTES_MAX);
        String emoji = "😀".repeat(700);
        String notes = values(60.0, null, emoji).notes();
        assertThat(notes.codePointCount(0, notes.length())).isEqualTo(ReportValues.NOTES_MAX);
    }

    @Test
    void reportReplaceIsIdempotentAndReportsChange() {
        UUID session = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var report = PerformanceReport.createManual(session, owner, values(60.0, 40.0, "ilk"), T0);
        assertThat(report.getSource()).isEqualTo(MeasurementSource.MANUAL);
        assertThat(report.getSessionId()).isEqualTo(session);
        assertThat(report.getUserId()).isEqualTo(owner);
        assertThat(report.getCreatedAt()).isEqualTo(T0);
        assertThat(report.values()).isEqualTo(values(60.0, 40.0, "ilk"));

        assertThat(report.replace(values(60.0, 40.0, "ilk"), T0.plusSeconds(5))).isFalse();
        assertThat(report.getUpdatedAt()).isEqualTo(T0);

        assertThat(report.replace(values(75.0, null, null), T0.plusSeconds(9))).isTrue();
        assertThat(report.getUpdatedAt()).isEqualTo(T0.plusSeconds(9));
        // yeni değerde olmayan ölçüm eskisini korumaz: tamamen değiştirilir
        assertThat(report.getP1LowFps()).isNull();
        assertThat(report.getNotes()).isNull();
        assertThat(report.getAvgFps()).isEqualTo(75.0);
    }

    @Test
    void reportRequestMapsToValues() {
        var request = new ReportRequest("2560x1440", GraphicsPreset.ULTRA, 120.0, 90.0, 80.0, 82.0, 40.0, 99.0, " not ");
        var values = request.toValues();
        assertThat(values).isEqualTo(new ReportValues("2560x1440", GraphicsPreset.ULTRA, 120.0, 90.0, 80.0, 82.0, 40.0, 99.0, "not"));
    }

    // ---------- istek / yapılandırma ----------

    @Test
    void endRequestDefaultsToCompleted() {
        assertThat(new EndSessionRequest(null).targetOrDefault()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(new EndSessionRequest(SessionStatus.ABANDONED).targetOrDefault()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(new EndSessionRequest(SessionStatus.ACTIVE).targetOrDefault()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void clientPropertiesAcceptOriginsAndBuildContractUris() {
        var properties = new PerformanceClientProperties(GAME, HARDWARE, TIMEOUT, Duration.ofSeconds(30));
        assertThat(properties.gamesBatchUri()).hasToString("http://game:8080/api/v1/game/games/batch");
        assertThat(properties.hardwareProfilesBatchUri()).hasToString("https://hardware.example/api/v1/hardware/profiles/batch");
        assertThat(new PerformanceClientProperties(URI.create("http://game:8080/"), HARDWARE, TIMEOUT, TIMEOUT)
                .gamesBatchUri()).hasToString("http://game:8080/api/v1/game/games/batch");
    }

    @Test
    void clientPropertiesRejectUnsafeUrlsAndTimeouts() {
        for (String bad : new String[]{"ftp://game", "http://user:pw@game:8080", "http://game:8080/x", "http://game:8080?a=1",
                "http://game:8080#f", "/relative", "game:8080"}) {
            URI badUri = URI.create(bad);
            assertThatThrownBy(() -> new PerformanceClientProperties(badUri, HARDWARE, TIMEOUT, TIMEOUT)).as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PerformanceClientProperties(GAME, badUri, TIMEOUT, TIMEOUT)).as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new PerformanceClientProperties(null, HARDWARE, TIMEOUT, TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        for (Duration bad : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(31)}) {
            assertThatThrownBy(() -> new PerformanceClientProperties(GAME, HARDWARE, bad, TIMEOUT)).as(String.valueOf(bad))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PerformanceClientProperties(GAME, HARDWARE, TIMEOUT, bad)).as(String.valueOf(bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
