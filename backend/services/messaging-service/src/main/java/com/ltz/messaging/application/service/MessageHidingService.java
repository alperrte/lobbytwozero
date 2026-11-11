package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.application.port.MessageStreamPort;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.HiddenMessageId;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.infrastructure.persistence.repository.HiddenMessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * MSG-25 benden sil: görülebilen herhangi bir mesaj (kendi ya da başkasının, herkesten silinmiş olsa da) yalnız bu
 * kullanıcının görünümünden kalkar. Diğer katılımcılar, okundu bilgisi ve okuma işaretçisi etkilenmez. İdempotenttir;
 * yalnız gerçekten gizlendiğinde kullanıcının kendi akışlarına {@code message_hidden} gider (diğer cihazlar).
 */
@Service
@RequiredArgsConstructor
public class MessageHidingService {

    private final ConversationAccessPolicy access;
    private final VisibleMessages visible;
    private final HiddenMessageRepository hidden;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void hide(UUID user, UUID conversationId, UUID messageId) {
        ConversationMember member = access.requireMember(conversationId, user);
        // Zaten gizlenmiş: idempotent (satır yalnız daha önce görülebilen bir mesaj için yazılmış olabilir).
        if (hidden.existsById(new HiddenMessageId(user, messageId))) {
            return;
        }
        Message message = visible.require(member, messageId);
        if (hidden.hide(user, message.getId(), Instant.now(clock).truncatedTo(ChronoUnit.MICROS)) == 1) {
            events.publishEvent(new MessageChanged(MessageStreamPort.HIDDEN_EVENT,
                    Map.of(user, new MessageHiddenView(conversationId, message.getId()))));
        }
    }
}
