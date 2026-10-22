package com.ltz.social.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.social.application.mapper.FriendRequestMapper;
import com.ltz.social.domain.entity.FriendRequest;
import com.ltz.social.domain.entity.Friendship;
import com.ltz.social.domain.entity.FriendshipId;
import com.ltz.social.domain.entity.UserBlockId;
import com.ltz.social.domain.enums.FriendRequestStatus;
import com.ltz.social.domain.event.FriendRequestAcceptedEvent;
import com.ltz.social.domain.event.FriendRequestSentEvent;
import com.ltz.social.domain.event.FriendshipChangedEvent;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;
import com.ltz.social.infrastructure.config.FriendRequestProperties;
import com.ltz.social.infrastructure.persistence.repository.FriendRequestRepository;
import com.ltz.social.infrastructure.persistence.repository.FriendshipRepository;
import com.ltz.social.infrastructure.persistence.repository.UserBlockRepository;
import com.ltz.social.infrastructure.persistence.repository.UserPairLock;

import lombok.RequiredArgsConstructor;

/**
 * Arkadaşlık isteği kullanım senaryoları (SOC-01..06). Kimlik her zaman doğrulanmış token'dan gelir; bir isteğe
 * yalnız tarafları dokunabilir ve kime ait olmadığı 404 ile gizlenir (varlık sızdırılmaz).
 */
@Service
@RequiredArgsConstructor
public class FriendRequestService {

    private final FriendRequestRepository requests;
    private final FriendshipRepository friendships;
    private final FriendRequestMapper mapper;
    private final FriendRequestProperties properties;
    private final PageSizePolicy pageSizes;
    private final OutboxEventRecorder events;
    private final UserBlockRepository blocks;
    private final UserPairLock pairLock;
    private final Clock clock;

    /** SOC-01. Kendine, zaten arkadaş olunan veya aralarında bekleyen isteği olan kullanıcıya istek gönderilemez. */
    @Transactional
    public FriendRequestView send(UUID senderId, UUID receiverId) {
        if (senderId.equals(receiverId)) {
            throw new SocialException(SocialErrorCode.CANNOT_REQUEST_SELF);
        }
        // Engelleme ile aynı anda istek göndermeyi sıraya koyar: engel konduktan sonra bekleyen istek kalamaz.
        pairLock.acquire(senderId, receiverId);
        if (blocks.existsById(new UserBlockId(senderId, receiverId))) {
            throw new SocialException(SocialErrorCode.USER_BLOCKED);
        }
        // Engellenen taraf engelin varlığını öğrenmez: istek gizlilik tercihiyle aynı genel hatayla reddedilir.
        if (blocks.existsById(new UserBlockId(receiverId, senderId))) {
            throw new SocialException(SocialErrorCode.FRIEND_REQUEST_NOT_ALLOWED);
        }
        if (friendships.existsById(FriendshipId.of(senderId, receiverId))) {
            throw new SocialException(SocialErrorCode.ALREADY_FRIENDS);
        }
        if (requests.existsBySenderIdAndReceiverIdAndStatus(senderId, receiverId, FriendRequestStatus.PENDING)) {
            throw new SocialException(SocialErrorCode.FRIEND_REQUEST_ALREADY_PENDING);
        }
        if (requests.existsBySenderIdAndReceiverIdAndStatus(receiverId, senderId, FriendRequestStatus.PENDING)) {
            throw new SocialException(SocialErrorCode.INCOMING_FRIEND_REQUEST_PENDING);
        }
        if (requests.countBySenderIdAndStatus(senderId, FriendRequestStatus.PENDING)
                >= properties.maxPendingOutgoing()) {
            throw new SocialException(SocialErrorCode.TOO_MANY_PENDING_REQUESTS);
        }
        FriendRequest saved;
        try {
            saved = requests.saveAndFlush(FriendRequest.send(senderId, receiverId, now()));
        } catch (DataIntegrityViolationException exception) {
            // Eşzamanlı iki istek: uq_friend_requests_pending_pair kazananı belirler, kaybeden 409 alır.
            throw new SocialException(SocialErrorCode.FRIEND_REQUEST_ALREADY_PENDING);
        }
        events.record(new FriendRequestSentEvent(saved.getId(), senderId, receiverId));
        return mapper.toView(saved);
    }

    /** SOC-02. Yalnız alıcı kabul edebilir; karşılıklı arkadaşlık aynı işlemde oluşur. */
    @Transactional
    public FriendRequestView accept(UUID userId, UUID requestId) {
        FriendRequest request = loadForUpdate(requestId, candidate -> candidate.isReceivedBy(userId));
        Instant now = now();
        request.accept(now);
        FriendshipId friendshipId = FriendshipId.of(request.getSenderId(), request.getReceiverId());
        if (!friendships.existsById(friendshipId)) {
            friendships.save(Friendship.between(request.getSenderId(), request.getReceiverId(), now));
            events.record(FriendshipChangedEvent.of(request.getSenderId(), request.getReceiverId(),
                    FriendshipChangedEvent.Change.ADDED));
        }
        events.record(new FriendRequestAcceptedEvent(request.getId(), request.getSenderId(), request.getReceiverId()));
        return mapper.toView(request);
    }

    /** SOC-03. Yalnız alıcı reddedebilir. */
    @Transactional
    public FriendRequestView reject(UUID userId, UUID requestId) {
        FriendRequest request = loadForUpdate(requestId, candidate -> candidate.isReceivedBy(userId));
        request.reject(now());
        return mapper.toView(request);
    }

    /** SOC-04. Yalnız gönderen, henüz yanıtlanmamış isteği iptal edebilir. */
    @Transactional
    public FriendRequestView cancel(UUID userId, UUID requestId) {
        FriendRequest request = loadForUpdate(requestId, candidate -> candidate.isSentBy(userId));
        request.cancel(now());
        return mapper.toView(request);
    }

    /** SOC-05. Kullanıcıya gelen bekleyen istekler, en yeniden eskiye. */
    @Transactional(readOnly = true)
    public FriendRequestPageView listIncoming(UUID userId, Integer pageSize, String cursor) {
        int size = pageSizes.resolve(pageSize);
        Limit fetch = Limit.of(size + 1);
        List<FriendRequest> rows;
        if (cursor == null) {
            rows = requests.findByReceiverIdAndStatusOrderByCreatedAtDescIdDesc(userId, FriendRequestStatus.PENDING,
                    fetch);
        } else {
            KeysetCursor after = KeysetCursor.decode(cursor);
            rows = requests.findIncomingAfter(userId, FriendRequestStatus.PENDING, after.createdAt(), after.id(),
                    fetch);
        }
        return toPage(rows, size);
    }

    /** SOC-06. Kullanıcının gönderdiği bekleyen istekler, en yeniden eskiye. */
    @Transactional(readOnly = true)
    public FriendRequestPageView listOutgoing(UUID userId, Integer pageSize, String cursor) {
        int size = pageSizes.resolve(pageSize);
        Limit fetch = Limit.of(size + 1);
        List<FriendRequest> rows;
        if (cursor == null) {
            rows = requests.findBySenderIdAndStatusOrderByCreatedAtDescIdDesc(userId, FriendRequestStatus.PENDING,
                    fetch);
        } else {
            KeysetCursor after = KeysetCursor.decode(cursor);
            rows = requests.findOutgoingAfter(userId, FriendRequestStatus.PENDING, after.createdAt(), after.id(),
                    fetch);
        }
        return toPage(rows, size);
    }

    /** İsteği satır kilidiyle okur; yok ya da çağıranın rolüne ait değilse 404 (IDOR'da varlık sızdırılmaz). */
    private FriendRequest loadForUpdate(UUID requestId, Predicate<FriendRequest> callerMayAct) {
        return requests.findByIdForUpdate(requestId)
                .filter(callerMayAct)
                .orElseThrow(() -> new SocialException(SocialErrorCode.FRIEND_REQUEST_NOT_FOUND));
    }

    /** {@code size + 1} kayıt okunur; fazla kayıt varsa sonraki sayfa imleci son görünen kayıttan üretilir. */
    private FriendRequestPageView toPage(List<FriendRequest> rows, int size) {
        boolean hasMore = rows.size() > size;
        List<FriendRequestView> items = rows.stream().limit(size).map(mapper::toView).toList();
        String nextCursor = hasMore ? cursorAfter(items.getLast()).encode() : null;
        return new FriendRequestPageView(items, nextCursor);
    }

    private static KeysetCursor cursorAfter(FriendRequestView last) {
        return new KeysetCursor(last.createdAt(), last.id());
    }

    /** DB timestamptz mikrosaniye tutar; imleç karşılaştırması kayıpsız olsun diye aynı kesinlikte yazılır. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
