package com.ltz.messaging.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.enums.ConversationType;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

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
 * Birebir (MSG-01) ya da grup (MSG-02) sohbeti. Birebir sohbet iki kullanıcı için tektir ({@code directKey}); grubun
 * başlığı vardır. Üyeler {@link ConversationMember} ile tutulur; kullanıcılar başka servise ait olduğundan yalnız
 * kimlikle (REF) saklanır.
 */
@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 32)
    private ConversationType type;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "direct_key", updatable = false, length = 73)
    private String directKey;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static Conversation direct(UUID initiator, UUID other, Instant now) {
        if (initiator.equals(other)) {
            throw new MessagingException(MessagingErrorCode.CANNOT_MESSAGE_SELF);
        }
        Conversation conversation = new Conversation();
        conversation.type = ConversationType.DIRECT;
        conversation.directKey = directKey(initiator, other);
        conversation.createdBy = initiator;
        conversation.createdAt = now;
        return conversation;
    }

    /** {@code title} doğrulanmış ve kırpılmış gelir (API katmanı); DB de 1..100 karakteri zorlar. */
    public static Conversation group(String title, UUID owner, Instant now) {
        Conversation conversation = new Conversation();
        conversation.type = ConversationType.GROUP;
        conversation.title = title;
        conversation.createdBy = owner;
        conversation.createdAt = now;
        return conversation;
    }

    /**
     * İki kullanıcının yönden bağımsız anahtarı. Sıralama PostgreSQL uuid sırasıyla (işaretsiz, baytsal) yapılır;
     * {@link UUID#compareTo} işaretli karşılaştırdığından kullanılmaz.
     */
    public static String directKey(UUID first, UUID second) {
        int order = Long.compareUnsigned(first.getMostSignificantBits(), second.getMostSignificantBits());
        if (order == 0) {
            order = Long.compareUnsigned(first.getLeastSignificantBits(), second.getLeastSignificantBits());
        }
        return order <= 0 ? first + ":" + second : second + ":" + first;
    }

    public boolean isGroup() {
        return type == ConversationType.GROUP;
    }

    public void requireGroup() {
        if (!isGroup()) {
            throw new MessagingException(MessagingErrorCode.GROUP_CONVERSATION_REQUIRED);
        }
    }
}
