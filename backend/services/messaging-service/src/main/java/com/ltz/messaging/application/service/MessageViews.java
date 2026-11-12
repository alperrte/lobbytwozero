package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.enums.MemberRole;
import com.ltz.messaging.infrastructure.config.MessageProperties;
import com.ltz.messaging.infrastructure.persistence.repository.HiddenMessageRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageReactionRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

/**
 * Okuma modelini izleyene göre tamamlar: görsel bilgisi (MSG-17), yanıt alıntısı (MSG-19), tepki özeti (MSG-23) ve
 * herkesten silme hakkı (MSG-24/26). Bir sayfa ya da liste sabit sayıda sorguyla süslenir (mesaj başına sorgu yok).
 * Herkesten silinmiş mesaja alıntı ve tepki eklenmez. Çağıranın transaction'ına katılır.
 */
@Component
public class MessageViews {

    private final MessageAttachments attachments;
    private final MessageRepository messages;
    private final HiddenMessageRepository hidden;
    private final MessageReactionRepository reactions;
    private final MessageProperties properties;
    private final Clock clock;

    public MessageViews(MessageAttachments attachments, MessageRepository messages, HiddenMessageRepository hidden,
                        MessageReactionRepository reactions, MessageProperties properties, Clock clock) {
        this.attachments = attachments;
        this.messages = messages;
        this.hidden = hidden;
        this.reactions = reactions;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * {@code viewer} için süsler. {@code membershipByConversation}, mesajların sohbetlerindeki izleyen üyeliğidir; üyeliği
     * olmayan sohbetin mesajına alıntı "görüntülenemiyor" ve silme hakkı boş döner.
     */
    public List<MessageView> decorate(UUID viewer, Map<UUID, ConversationMember> membershipByConversation,
                                      List<MessageView> views) {
        if (views.isEmpty()) {
            return views;
        }
        List<MessageView> withAttachments = attachments.attachTo(views);
        List<MessageView> live = withAttachments.stream().filter(view -> !view.deleted()).toList();
        Map<UUID, ReplyPreview> previews = replyPreviews(viewer, membershipByConversation, live);
        Map<UUID, ReactionsView> reactionViews = reactionsOf(viewer, live);
        Instant now = Instant.now(clock);
        Duration window = properties.deleteForEveryoneWindow();
        return withAttachments.stream()
                .map(view -> {
                    MessageView result = view;
                    if (!view.deleted()) {
                        if (view.replyToMessageId() != null) {
                            result = result.withReplyTo(previews.getOrDefault(view.replyToMessageId(),
                                    ReplyPreview.unavailable(view.replyToMessageId())));
                        }
                        result = result.withReactions(reactionViews.getOrDefault(view.id(), ReactionsView.EMPTY));
                    }
                    ConversationMember membership = membershipByConversation.get(view.conversationId());
                    return result.withDeletableUntil(
                            membership == null ? null : deletableUntil(result, membership, window, now));
                })
                .toList();
    }

    public MessageView decorate(UUID viewer, ConversationMember membership, MessageView view) {
        return decorate(viewer, Map.of(view.conversationId(), membership), List.of(view)).getFirst();
    }

    /**
     * Yeni mesajın alıcı başına yükü (SSE {@code message} olayı ve gönderim yanıtı): alıntı her alıcının görünürlüğüne,
     * silme hakkı her alıcının rolüne göre hesaplanır. Yanıt hedefi için en çok iki sorgu yapılır; tepki yoktur.
     */
    public Map<UUID, MessageView> forRecipients(MessageView base, Collection<ConversationMember> recipients) {
        MessageView withAttachment = attachments.attachTo(base);
        Message target = base.replyToMessageId() == null ? null
                : messages.findById(base.replyToMessageId()).orElse(null);
        Set<UUID> hidTarget = target == null ? Set.of() : hidden.findUsersWhoHid(target.getId());
        Instant now = Instant.now(clock);
        Map<UUID, MessageView> payloads = new LinkedHashMap<>();
        for (ConversationMember recipient : recipients) {
            MessageView view = withAttachment;
            if (base.replyToMessageId() != null) {
                view = view.withReplyTo(target == null ? ReplyPreview.unavailable(base.replyToMessageId())
                        : previewFor(target, recipient, hidTarget.contains(recipient.getUserId())));
            }
            payloads.put(recipient.getUserId(), view.withDeletableUntil(
                    deletableUntil(view, recipient, properties.deleteForEveryoneWindow(), now)));
        }
        return payloads;
    }

    /**
     * Herkesten silme hakkının son anı (MSG-24/26): mesajın göndereni ya da grupta OWNER/ADMIN (bu roller yalnız grupta
     * vardır), gönderimden sonraki {@code window} içinde. Hak yoksa, süre geçmişse ya da mesaj silinmişse {@code null}.
     */
    public static Instant deletableUntil(MessageView message, ConversationMember viewer, Duration window, Instant now) {
        if (message.deleted()) {
            return null;
        }
        if (!canDeleteForEveryone(message.senderId(), viewer)) {
            return null;
        }
        Instant until = message.createdAt().plus(window);
        return until.isAfter(now) ? until : null;
    }

    /** Herkesten silme yetkisi (süreden bağımsız): gönderen ya da OWNER/ADMIN (bu roller yalnız grupta vardır). */
    public static boolean canDeleteForEveryone(UUID senderId, ConversationMember viewer) {
        return senderId.equals(viewer.getUserId())
                || viewer.getRole() == MemberRole.OWNER || viewer.getRole() == MemberRole.ADMIN;
    }

    /** Yanıt hedefinin {@code viewer}'a göre önizlemesi; göremediği hedefin içeriği ve göndereni sızmaz. */
    static ReplyPreview previewFor(Message target, ConversationMember viewer, boolean hiddenByViewer) {
        if (hiddenByViewer || !viewer.canSee(target.getSeq())) {
            return ReplyPreview.unavailable(target.getId());
        }
        return target.isDeleted() ? ReplyPreview.deleted(target) : ReplyPreview.visible(target);
    }

    private Map<UUID, ReplyPreview> replyPreviews(UUID viewer, Map<UUID, ConversationMember> memberships,
                                                  List<MessageView> views) {
        Set<UUID> replyIds = views.stream()
                .map(MessageView::replyToMessageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (replyIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> hiddenByViewer = hidden.findHiddenAmong(viewer, replyIds);
        return messages.findAllById(replyIds).stream()
                .collect(Collectors.toMap(Message::getId, target -> {
                    ConversationMember membership = memberships.get(target.getConversationId());
                    return membership == null ? ReplyPreview.unavailable(target.getId())
                            : previewFor(target, membership, hiddenByViewer.contains(target.getId()));
                }));
    }

    private Map<UUID, ReactionsView> reactionsOf(UUID viewer, List<MessageView> views) {
        List<UUID> ids = views.stream().map(MessageView::id).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> mine = reactions.findMine(viewer, ids).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (String) row[1], (first, second) -> first));
        Map<UUID, List<Object[]>> byMessage = reactions.summarize(ids).stream()
                .collect(Collectors.groupingBy(row -> (UUID) row[0]));
        Comparator<Object[]> order = Comparator.<Object[]>comparingLong(row -> (Long) row[2]).reversed()
                .thenComparing(row -> (Instant) row[3]);
        return ids.stream()
                .filter(id -> byMessage.containsKey(id) || mine.containsKey(id))
                .collect(Collectors.toMap(Function.identity(), id -> new ReactionsView(
                        byMessage.getOrDefault(id, List.of()).stream()
                                .sorted(order)
                                .map(row -> new ReactionCount((String) row[1], (Long) row[2]))
                                .toList(),
                        mine.get(id))));
    }
}
