package com.ltz.messaging.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.infrastructure.persistence.repository.AttachmentMeta;
import com.ltz.messaging.infrastructure.persistence.repository.MessageAttachmentRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@code IMAGE} mesajlarına görsel bilgisini ekler (MSG-17). Bir sayfa ya da liste için tek sorgu yapılır (mesaj başına
 * sorgu yok); içerik belleğe alınmaz. Çağıranın transaction'ına katılır.
 */
@Component
@RequiredArgsConstructor
public class MessageAttachments {

    private final MessageAttachmentRepository attachments;

    public List<MessageView> attachTo(List<MessageView> views) {
        List<UUID> imageIds = views.stream()
                .filter(view -> view.type() == MessageType.IMAGE)
                .map(MessageView::id)
                .toList();
        if (imageIds.isEmpty()) {
            return views;
        }
        Map<UUID, AttachmentView> byMessage = attachments.findMetaByMessageIds(imageIds).stream()
                .collect(Collectors.toMap(AttachmentMeta::messageId, AttachmentView::of, (first, second) -> first));
        return views.stream()
                .map(view -> byMessage.containsKey(view.id()) ? view.withAttachment(byMessage.get(view.id())) : view)
                .toList();
    }

    public MessageView attachTo(MessageView view) {
        return attachTo(List.of(view)).getFirst();
    }
}
