package com.ltz.social.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Karşılıklı arkadaşlık: iki kullanıcı için tek satır; yön yoktur. */
@Entity
@Table(name = "friendships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @EmbeddedId
    private FriendshipId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Friendship between(UUID first, UUID second, Instant now) {
        Friendship friendship = new Friendship();
        friendship.id = FriendshipId.of(first, second);
        friendship.createdAt = now;
        return friendship;
    }
}
