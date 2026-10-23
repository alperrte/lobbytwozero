package com.ltz.social.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.social.domain.enums.FriendRequestStatus;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Arkadaşlık isteği. Durum makinesi: {@code PENDING → ACCEPTED | REJECTED | CANCELLED}; yanıtlanmış istek bir daha
 * değişmez. Kullanıcılar başka servise ait olduğundan yalnız kimlikle (REF) tutulur.
 */
@Entity
@Table(name = "friend_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false, updatable = false)
    private UUID receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FriendRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static FriendRequest send(UUID senderId, UUID receiverId, Instant now) {
        if (senderId.equals(receiverId)) {
            throw new SocialException(SocialErrorCode.CANNOT_REQUEST_SELF);
        }
        FriendRequest request = new FriendRequest();
        request.senderId = senderId;
        request.receiverId = receiverId;
        request.status = FriendRequestStatus.PENDING;
        request.createdAt = now;
        return request;
    }

    public boolean isSentBy(UUID userId) {
        return senderId.equals(userId);
    }

    public boolean isReceivedBy(UUID userId) {
        return receiverId.equals(userId);
    }

    public void accept(Instant now) {
        respond(FriendRequestStatus.ACCEPTED, now);
    }

    public void reject(Instant now) {
        respond(FriendRequestStatus.REJECTED, now);
    }

    public void cancel(Instant now) {
        respond(FriendRequestStatus.CANCELLED, now);
    }

    private void respond(FriendRequestStatus newStatus, Instant now) {
        if (status != FriendRequestStatus.PENDING) {
            throw new SocialException(SocialErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }
        status = newStatus;
        respondedAt = now;
    }
}
