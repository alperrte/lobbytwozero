package com.ltz.social.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.social.domain.entity.FriendRequest;
import com.ltz.social.domain.entity.FriendshipId;
import com.ltz.social.domain.entity.UserBlock;
import com.ltz.social.domain.entity.UserBlockId;
import com.ltz.social.domain.event.FriendshipChangedEvent;
import com.ltz.social.domain.event.UserBlockedEvent;
import com.ltz.social.domain.event.UserUnblockedEvent;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;
import com.ltz.social.infrastructure.persistence.repository.FriendRequestRepository;
import com.ltz.social.infrastructure.persistence.repository.FriendshipRepository;
import com.ltz.social.infrastructure.persistence.repository.UserBlockRepository;
import com.ltz.social.infrastructure.persistence.repository.UserPairLock;

import lombok.RequiredArgsConstructor;

/**
 * Kullanıcı engelleme (SOC-09), engel kaldırma (SOC-10) ve engellenenler listesi (SOC-11). Engel yönlüdür ama
 * kısıtlama iki yönde de uygulanır. Engellenince aynı işlemde: aralarındaki bekleyen istekler kapatılır, arkadaşlık
 * varsa bitirilir ve {@code UserBlocked} olayı yazılır. Engellenen kullanıcıya bildirim gitmez.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository blocks;
    private final FriendRequestRepository requests;
    private final FriendshipRepository friendships;
    private final UserPairLock pairLock;
    private final PageSizePolicy pageSizes;
    private final OutboxEventRecorder events;
    private final Clock clock;

    /**
     * SOC-09. İdempotenttir: zaten engelliyse hiçbir şey değişmez ve mevcut kayıt döner. Kendini engelleme 400.
     * Çift kilidi, aynı anda gönderilen arkadaşlık isteğiyle sıralama sağlar.
     */
    @Transactional
    public BlockResult block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new SocialException(SocialErrorCode.CANNOT_BLOCK_SELF);
        }
        pairLock.acquire(blockerId, blockedId);
        Optional<UserBlock> existing = blocks.findById(new UserBlockId(blockerId, blockedId));
        if (existing.isPresent()) {
            return new BlockResult(toView(existing.get()), false);
        }
        Instant now = now();
        UserBlock block = blocks.saveAndFlush(UserBlock.create(blockerId, blockedId, now));
        closePendingRequests(blockerId, blockedId, now);
        endFriendship(blockerId, blockedId);
        events.record(new UserBlockedEvent(blockerId, blockedId));
        return new BlockResult(toView(block), true);
    }

    /**
     * SOC-10. Yalnız kendi koyduğu engeli kaldırabilir; engel yoksa 404. Karşı tarafın kendi engeli (varsa) sürer.
     * Daha önce biten arkadaşlık ve kapanan istekler geri gelmez.
     */
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        if (blocks.deleteByBlockId(new UserBlockId(blockerId, blockedId)) == 0) {
            throw new SocialException(SocialErrorCode.BLOCK_NOT_FOUND);
        }
        events.record(new UserUnblockedEvent(blockerId, blockedId));
    }

    /** SOC-11. Kullanıcının engellediği hesaplar, en yeniden eskiye; keyset sayfalama. */
    @Transactional(readOnly = true)
    public BlockPageView listBlocked(UUID blockerId, Integer pageSize, String cursor) {
        int size = pageSizes.resolve(pageSize);
        Limit fetch = Limit.of(size + 1);
        List<UserBlock> rows;
        if (cursor == null) {
            rows = blocks.findFirstPage(blockerId, fetch);
        } else {
            KeysetCursor after = KeysetCursor.decode(cursor);
            rows = blocks.findPageAfter(blockerId, after.createdAt(), after.id(), fetch);
        }
        boolean hasMore = rows.size() > size;
        List<BlockView> items = rows.stream().limit(size).map(BlockService::toView).toList();
        String nextCursor = hasMore
                ? new KeysetCursor(items.getLast().createdAt(), items.getLast().blockedUserId()).encode()
                : null;
        return new BlockPageView(items, nextCursor);
    }

    /** Engelleyenin gönderdiği bekleyen istek iptal, engellenenden gelen istek reddedilmiş sayılır. */
    private void closePendingRequests(UUID blockerId, UUID blockedId, Instant now) {
        for (FriendRequest pending : requests.findPendingBetweenForUpdate(blockerId, blockedId)) {
            if (pending.isSentBy(blockerId)) {
                pending.cancel(now);
            } else {
                pending.reject(now);
            }
        }
    }

    private void endFriendship(UUID blockerId, UUID blockedId) {
        if (friendships.deleteByFriendshipId(FriendshipId.of(blockerId, blockedId)) > 0) {
            events.record(FriendshipChangedEvent.of(blockerId, blockedId, FriendshipChangedEvent.Change.REMOVED));
        }
    }

    private static BlockView toView(UserBlock block) {
        return new BlockView(block.getId().getBlockedId(), block.getCreatedAt());
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
