package com.ltz.review.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {
    @Id private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID gameId;
    @Column(length = 5000) private String body;
    @Column(nullable = false, length = 32) private String status;
    @Version private long version;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant deletedAt;

    public Review(UUID userId, UUID gameId, String body, Instant now) {
        id = UUID.randomUUID();
        this.userId = userId;
        this.gameId = gameId;
        this.body = body;
        status = "VISIBLE";
        createdAt = now;
        updatedAt = now;
    }

    public void edit(String content, Instant now) {
        if (!body.equals(content)) {
            body = content;
            updatedAt = now;
        }
    }

    public void remove(Instant now) {
        body = null;
        status = "DELETED";
        deletedAt = now;
        updatedAt = now;
    }
}
