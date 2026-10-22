package com.ltz.social.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.social.domain.entity.Friendship;
import com.ltz.social.domain.entity.FriendshipId;
import com.ltz.social.domain.event.FriendshipChangedEvent;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;
import com.ltz.social.infrastructure.persistence.repository.FriendshipRepository;

import lombok.RequiredArgsConstructor;

/**
 * Arkadaş listesi (SOC-07) ve arkadaşlıktan çıkarma (SOC-08). Kullanıcı yalnız kendi arkadaşlıklarını görür ve
 * yalnız kendi arkadaşlığını bitirebilir: kimlik token'dan gelir, işlem {@code (kullanıcı, arkadaş)} çiftine bağlıdır.
 */
@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendships;
    private final PageSizePolicy pageSizes;
    private final FriendsListAccessPolicy accessPolicy;
    private final OutboxEventRecorder events;

    /** SOC-07. Arkadaşlar, en yeni arkadaşlıktan eskiye; keyset sayfalama. */
    @Transactional(readOnly = true)
    public FriendPageView listFriends(UUID userId, Integer pageSize, String cursor) {
        int size = pageSizes.resolve(pageSize);
        Limit fetch = Limit.of(size + 1);
        List<Friendship> rows;
        if (cursor == null) {
            rows = friendships.findFirstPage(userId, fetch);
        } else {
            KeysetCursor after = KeysetCursor.decode(cursor);
            rows = friendships.findPageAfter(userId, after.createdAt(), after.id(), fetch);
        }
        boolean hasMore = rows.size() > size;
        List<FriendView> items = rows.stream().limit(size).map(row -> toView(userId, row)).toList();
        String nextCursor = hasMore
                ? new KeysetCursor(items.getLast().since(), items.getLast().friendId()).encode()
                : null;
        return new FriendPageView(items, nextCursor);
    }

    /**
     * SOC-12. Başka bir kullanıcının arkadaş listesi: engel ve user-service'teki görünürlük tercihi uygulanır
     * ({@link FriendsListAccessPolicy}). Kendi listesi her zaman görünür.
     */
    @Transactional(readOnly = true)
    public FriendPageView listFriendsOf(UUID viewerId, UUID ownerId, Integer pageSize, String cursor) {
        accessPolicy.ensureVisible(viewerId, ownerId);
        return listFriends(ownerId, pageSize, cursor);
    }

    /** SOC-08. Arkadaş değilse (ya da kendisiyse) 404; eşzamanlı iki çıkarmadan yalnız biri başarılı olur. */
    @Transactional
    public void unfriend(UUID userId, UUID friendId) {
        if (friendships.deleteByFriendshipId(FriendshipId.of(userId, friendId)) == 0) {
            throw new SocialException(SocialErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        events.record(FriendshipChangedEvent.of(userId, friendId, FriendshipChangedEvent.Change.REMOVED));
    }

    private static FriendView toView(UUID userId, Friendship friendship) {
        FriendshipId id = friendship.getId();
        UUID friendId = id.getUserLowId().equals(userId) ? id.getUserHighId() : id.getUserLowId();
        return new FriendView(friendId, friendship.getCreatedAt());
    }
}
