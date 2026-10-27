package com.ltz.social.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.social.domain.enums.FriendRequestStatus;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

class FriendRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private final UUID sender = UUID.randomUUID();
    private final UUID receiver = UUID.randomUUID();

    @Test
    void yeniIstekBekleyenDurumdadir() {
        FriendRequest request = FriendRequest.send(sender, receiver, NOW);

        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.PENDING);
        assertThat(request.getCreatedAt()).isEqualTo(NOW);
        assertThat(request.getRespondedAt()).isNull();
        assertThat(request.isSentBy(sender)).isTrue();
        assertThat(request.isSentBy(receiver)).isFalse();
        assertThat(request.isReceivedBy(receiver)).isTrue();
        assertThat(request.isReceivedBy(sender)).isFalse();
    }

    @Test
    void kendineIstekOlusturulamaz() {
        assertThatThrownBy(() -> FriendRequest.send(sender, sender, NOW))
                .isInstanceOfSatisfying(SocialException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(SocialErrorCode.CANNOT_REQUEST_SELF));
    }

    @Test
    void kabulRedVeIptalSonDurumlarinaGecer() {
        FriendRequest accepted = FriendRequest.send(sender, receiver, NOW);
        accepted.accept(LATER);
        FriendRequest rejected = FriendRequest.send(sender, receiver, NOW);
        rejected.reject(LATER);
        FriendRequest cancelled = FriendRequest.send(sender, receiver, NOW);
        cancelled.cancel(LATER);

        assertThat(accepted.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(rejected.getStatus()).isEqualTo(FriendRequestStatus.REJECTED);
        assertThat(cancelled.getStatus()).isEqualTo(FriendRequestStatus.CANCELLED);
        assertThat(accepted.getRespondedAt()).isEqualTo(LATER);
        assertThat(rejected.getRespondedAt()).isEqualTo(LATER);
        assertThat(cancelled.getRespondedAt()).isEqualTo(LATER);
    }

    @Test
    void yanitlanmisIstekHicbirYonde_degistirilemez() {
        FriendRequest request = FriendRequest.send(sender, receiver, NOW);
        request.accept(LATER);

        for (Runnable change : new Runnable[] {() -> request.accept(LATER), () -> request.reject(LATER),
                () -> request.cancel(LATER)}) {
            assertThatThrownBy(change::run).isInstanceOfSatisfying(SocialException.class,
                    exception -> assertThat(exception.errorCode())
                            .isEqualTo(SocialErrorCode.FRIEND_REQUEST_NOT_PENDING));
        }
        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(request.getRespondedAt()).isEqualTo(LATER);
    }
}
