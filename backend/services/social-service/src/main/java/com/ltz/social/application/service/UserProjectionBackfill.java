package com.ltz.social.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;

import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.application.port.SocialEventPublisher;
import com.ltz.social.domain.entity.Friendship;
import com.ltz.social.domain.entity.UserBlock;
import com.ltz.social.domain.event.FriendshipChangedEvent;
import com.ltz.social.domain.event.SocialEvent;
import com.ltz.social.domain.event.UserBlockedEvent;
import com.ltz.social.infrastructure.persistence.repository.FriendshipRepository;
import com.ltz.social.infrastructure.persistence.repository.UserBlockRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Mevcut arkadaşlık ve engelleri user-service yansımasına (olay iletimi öncesinden kalan geçmiş ilişkiler) aktarır.
 * Outbox'a yazılmaz: yalnız user-service'e doğrudan gönderilir (community gibi diğer tüketiciler aynı engeli yeniden almaz).
 * <p>
 * Tekrar çalıştırmak güvenlidir: her olay ilişkinin gerçek {@code created_at} zamanını taşır ve olay kimliği ilişkiden
 * türetilir. user-service yansıması yalnız daha yeni zamanlı olayı uygular; bu yüzden sonradan gelen bir kaldırma ya da
 * yeniden ekleme olayı aktarımla ezilmez, aynı aktarım ikinci kez çalışınca hiçbir şeyi değiştirmez. Kaldırılmış ilişkiler
 * Social'da silindiği için aktarılmaz (kaldırma olayı zaten outbox ile iletilmiş olmalıdır).
 */
@Slf4j
public class UserProjectionBackfill {

    /** Gönderilen ilişki sayıları; {@code complete} false ise teslim hatasıyla yarıda kesildi (tekrar çalıştırılabilir). */
    public record Result(int friendships, int blocks, boolean complete) {
    }

    private static final UUID NIL = new UUID(0L, 0L);
    private static final String PRODUCER = "social-service";

    private final FriendshipRepository friendships;
    private final UserBlockRepository blocks;
    private final SocialEventPublisher userPublisher;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public UserProjectionBackfill(FriendshipRepository friendships, UserBlockRepository blocks,
                                  SocialEventPublisher userPublisher, ObjectMapper objectMapper, int batchSize) {
        this.friendships = friendships;
        this.blocks = blocks;
        this.userPublisher = userPublisher;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    public Result run() {
        if (!userPublisher.isReady()) {
            log.warn("User projection backfill skipped: service identity is not configured");
            return new Result(0, 0, false);
        }
        int sentFriendships = 0;
        int sentBlocks = 0;
        try {
            List<Friendship> page = friendships.findBatchAfter(NIL, NIL, Limit.of(batchSize));
            while (!page.isEmpty()) {
                for (Friendship friendship : page) {
                    send(FriendshipChangedEvent.of(friendship.getId().getUserLowId(), friendship.getId().getUserHighId(),
                            FriendshipChangedEvent.Change.ADDED), friendship.getCreatedAt());
                    sentFriendships++;
                }
                Friendship last = page.get(page.size() - 1);
                page = friendships.findBatchAfter(last.getId().getUserLowId(), last.getId().getUserHighId(),
                        Limit.of(batchSize));
            }
            List<UserBlock> blockPage = blocks.findBatchAfter(NIL, NIL, Limit.of(batchSize));
            while (!blockPage.isEmpty()) {
                for (UserBlock block : blockPage) {
                    send(new UserBlockedEvent(block.getId().getBlockerId(), block.getId().getBlockedId()),
                            block.getCreatedAt());
                    sentBlocks++;
                }
                UserBlock last = blockPage.get(blockPage.size() - 1);
                blockPage = blocks.findBatchAfter(last.getId().getBlockerId(), last.getId().getBlockedId(),
                        Limit.of(batchSize));
            }
        } catch (EventDeliveryException exception) {
            log.error("User projection backfill stopped after friendships={}, blocks={}: {}", sentFriendships,
                    sentBlocks, exception.getMessage());
            return new Result(sentFriendships, sentBlocks, false);
        }
        log.info("User projection backfill finished: friendships={}, blocks={}", sentFriendships, sentBlocks);
        return new Result(sentFriendships, sentBlocks, true);
    }

    private void send(SocialEvent event, Instant occurredAt) {
        // Kararlı kimlik: aynı ilişki ve zaman için aynı olay (tüketici tekilleştirir); yeniden kurulan ilişki farklı zamanla farklı kimlik alır.
        UUID eventId = UUID.nameUUIDFromBytes(
                ("projection-backfill|" + event.eventType() + "|" + event.aggregateId() + "|" + occurredAt)
                        .getBytes(StandardCharsets.UTF_8));
        userPublisher.publish(new EventEnvelope(eventId, event.eventType(), event.eventVersion(), occurredAt, PRODUCER,
                event.aggregateId(), objectMapper.writeValueAsString(event)));
    }
}
