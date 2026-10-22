package com.ltz.social.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bir kullanıcının başka bir kullanıcıyı engellemesi. Değişmez kayıttır: engel ya vardır ya kaldırılmıştır (silinir). */
@Entity
@Table(name = "user_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @EmbeddedId
    private UserBlockId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static UserBlock create(UUID blockerId, UUID blockedId, Instant now) {
        if (blockerId.equals(blockedId)) {
            throw new SocialException(SocialErrorCode.CANNOT_BLOCK_SELF);
        }
        UserBlock block = new UserBlock();
        block.id = new UserBlockId(blockerId, blockedId);
        block.createdAt = now;
        return block;
    }
}
