package com.ltz.lfp.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.lfp.domain.enums.ListingStatus;
import com.ltz.lfp.domain.enums.Platform;
import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;

/** İlanın açılış kuralları: durum, süre ve oyun zamanı (LFP-07, LFP-14, LFP-15). */
class ListingTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final Duration DAY = Duration.ofHours(24);
    private static final Duration WINDOW = Duration.ofHours(24);

    @Test
    void acikDurumdaAcilirSureSonuHesaplanir() {
        UUID owner = UUID.randomUUID();

        Listing listing = Listing.open(owner, UUID.randomUUID(), "Game", content(null, null), NOW, DAY, WINDOW);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.OPEN);
        assertThat(listing.getExpiresAt()).isEqualTo(NOW.plus(DAY));
        assertThat(listing.getCreatedAt()).isEqualTo(NOW);
        assertThat(listing.getUpdatedAt()).isEqualTo(NOW);
        assertThat(listing.getClosedAt()).isNull();
        assertThat(listing.isOwnedBy(owner)).isTrue();
        assertThat(listing.isOwnedBy(UUID.randomUUID())).isFalse();
        assertThat(listing.getRoles()).containsExactly("Support");
    }

    @Test
    void rollerDisaridanDegistirilemez() {
        Listing listing = Listing.open(UUID.randomUUID(), UUID.randomUUID(), "Game", content(null, null), NOW, DAY,
                WINDOW);

        assertThatThrownBy(() -> listing.getRoles().add("Hack")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void oyunZamaniSinirlari() {
        assertOpens(NOW.minus(Listing.PLAY_TIME_TOLERANCE), null);
        assertOpens(NOW.plus(DAY), null);
        assertOpens(NOW.plusSeconds(60), NOW.plusSeconds(60).plus(WINDOW));

        assertFails(NOW.minus(Listing.PLAY_TIME_TOLERANCE).minusSeconds(1), null, LfpErrorCode.INVALID_PLAY_TIME);
        assertFails(NOW.plus(DAY).plusSeconds(1), null, LfpErrorCode.PLAY_TIME_AFTER_EXPIRY);
        assertFails(null, NOW.plusSeconds(3600), LfpErrorCode.INVALID_PLAY_TIME);
        assertFails(NOW.plusSeconds(3600), NOW.plusSeconds(3600), LfpErrorCode.INVALID_PLAY_TIME);
        assertFails(NOW.plusSeconds(60), NOW.plusSeconds(60).plus(WINDOW).plusSeconds(1),
                LfpErrorCode.INVALID_PLAY_TIME);
    }

    @Test
    void sureSonundaDurumExpiredSayilir() {
        Listing listing = open();

        assertThat(listing.statusAt(NOW.plus(DAY).minusNanos(1000))).isEqualTo(ListingStatus.OPEN);
        assertThat(listing.statusAt(NOW.plus(DAY))).isEqualTo(ListingStatus.EXPIRED);
        assertThat(listing.isOpenAt(NOW.plus(DAY))).isFalse();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.OPEN);
    }

    @Test
    void guncellemeIcerigiDegistirirSureyiDegistirmez() {
        Listing listing = open();
        Instant later = NOW.plusSeconds(600);

        listing.update(ListingContent.of(Platform.XBOX, "Gold", List.of("Tank", "Healer"), 4, null, null, true,
                "Yeni"), later, WINDOW);

        assertThat(listing.getPlatform()).isEqualTo(Platform.XBOX);
        assertThat(listing.getRoles()).containsExactly("Tank", "Healer");
        assertThat(listing.getUpdatedAt()).isEqualTo(later);
        assertThat(listing.getExpiresAt()).isEqualTo(NOW.plus(DAY));
    }

    @Test
    void degismeyenBaslangicGecmisteOlabilirDegisenOlamaz() {
        Instant start = NOW.plusSeconds(600);
        Listing listing = Listing.open(UUID.randomUUID(), UUID.randomUUID(), "Game", content(start, null), NOW, DAY,
                WINDOW);
        Instant muchLater = start.plus(Duration.ofHours(2));

        listing.update(content(start, null), muchLater, WINDOW);
        assertThat(listing.getPlayTimeStart()).isEqualTo(start);

        assertThatThrownBy(() -> listing.update(content(start.plusSeconds(1), null), muchLater, WINDOW))
                .isInstanceOfSatisfying(LfpException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(LfpErrorCode.INVALID_PLAY_TIME));
    }

    @Test
    void kapatmaIdempotentKapaliVeSuresiDolmusGuncellenemez() {
        Listing listing = open();
        Instant closeAt = NOW.plusSeconds(60);

        assertThat(listing.close(closeAt)).isTrue();
        assertThat(listing.close(closeAt.plusSeconds(1))).isFalse();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(listing.getClosedAt()).isEqualTo(closeAt);
        assertNotOpen(() -> listing.update(content(null, null), closeAt, WINDOW));

        Listing expired = open();
        assertNotOpen(() -> expired.close(NOW.plus(DAY)));
        assertNotOpen(() -> expired.update(content(null, null), NOW.plus(DAY), WINDOW));
    }

    private static Listing open() {
        return Listing.open(UUID.randomUUID(), UUID.randomUUID(), "Game", content(null, null), NOW, DAY, WINDOW);
    }

    private static void assertNotOpen(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(LfpException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(LfpErrorCode.LISTING_NOT_OPEN));
    }

    private static void assertOpens(Instant start, Instant end) {
        Listing listing = Listing.open(UUID.randomUUID(), UUID.randomUUID(), "Game", content(start, end), NOW, DAY,
                WINDOW);
        assertThat(listing.getPlayTimeStart()).isEqualTo(start);
        assertThat(listing.getPlayTimeEnd()).isEqualTo(end);
    }

    private static void assertFails(Instant start, Instant end, LfpErrorCode code) {
        assertThatThrownBy(() -> Listing.open(UUID.randomUUID(), UUID.randomUUID(), "Game", content(start, end), NOW,
                DAY, WINDOW)).isInstanceOfSatisfying(LfpException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }

    private static ListingContent content(Instant start, Instant end) {
        return ListingContent.of(Platform.PC, null, List.of("Support"), 2, start, end, false, "Açıklama");
    }
}
