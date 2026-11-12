package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.messaging.domain.entity.Conversation;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.GifSnapshot;
import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.entity.MessageAttachment;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.MessageProperties;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationMemberRepository;
import com.ltz.messaging.infrastructure.persistence.repository.ConversationRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageAttachmentRepository;
import com.ltz.messaging.infrastructure.persistence.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Mesaj gönderme (metin MSG-06, görsel MSG-17, emoji MSG-18, platform GIF'i MSG-22, sesli mesaj MSG-21), mesaj geçmişi (MSG-07, MSG-10), okuma işaretçisi (MSG-13) ve okuma saati (MSG-11).
 * <p>
 * Gönderim sırası: aynı {@code clientMessageId} ile daha önce kaydedilmiş mesaj varsa o döner (yeniden deneme, yan etki
 * yok) → yazma yetkisi (aktif üye; birebirde engel yok, MSG-14/15; engel sorgusu transaction dışında) → sohbet satırı
 * kilitlenir, üyelik ve oran sınırı yeniden denetlenir, mesaj {@code seq} alır, gönderenin okuma işaretçisi ilerler ve
 * {@code MessageSent} olayı aynı transaction'da outbox'a yazılır → commit sonrası açık akışlara iletilir (MSG-09).
 * İşaretçi her iki yolda da {@link ReadTracker} üzerinden ilerler; gerçek ilerleme bir okuma aralığı yazar. İsteyenin
 * kendi mesajlarına okundu durumu {@link ReadWatermark} ile eklenir (MSG-11): yeni mesaj {@code SENT} döner.
 * <p>
 * Görsel ve sesli mesajda tür bağlanacak dosyadan çözülür (sesli mesaj açıklama almaz); dosya mesajla aynı
 * transaction'da koşullu UPDATE ile bağlanır; bağlanamazsa (başkasının, başka sohbetin, süresi dolmuş ya da gönderilmiş
 * dosya) mesaj da kaydedilmez. GIF mesajında GIF, engel sorgusundan sonra ve
 * transaction dışında platformdan çözülür; adresler istemciden alınmaz.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final VisibleMessages visible;
    private final MessageViews views;
    private final MessageAppender appender;
    private final GifService gifs;
    private final ConversationAccessPolicy access;
    private final ReadTracker tracker;
    private final MessageProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public SendMessageResult send(UUID sender, UUID conversationId, String clientMessageId, MessageDraft draft) {
        Optional<MessageView> replay = Objects.requireNonNull(
                transactions.execute(status -> findRetry(sender, conversationId, clientMessageId, draft)));
        if (replay.isPresent()) {
            return new SendMessageResult(replay.get(), false);
        }
        access.requireCanWrite(conversationId, sender);
        GifSnapshot gif = draft.type() == MessageType.GIF ? gifs.resolve(draft.gifProvider(), draft.gifId()) : null;
        try {
            return Objects.requireNonNull(
                    transactions.execute(status -> insert(sender, conversationId, clientMessageId, draft, gif)));
        } catch (DataIntegrityViolationException exception) {
            // Aynı clientMessageId başka bir sohbette eşzamanlı kullanıldı (tekil indeks kazananı seçti).
            throw new MessagingException(MessagingErrorCode.CLIENT_MESSAGE_ID_REUSED, exception);
        }
    }

    /**
     * MSG-07. {@code after} verilirse ondan sonraki mesajlar eskiden yeniye (yeniden bağlanınca eşitleme); aksi halde
     * {@code before}'dan (yoksa en sondan) önceki mesajlar yeniden eskiye. Yalnız aktif üye görür ve yalnız katıldıktan
     * sonraki mesajları görür.
     */
    @Transactional(readOnly = true)
    public MessagePageView history(UUID viewer, UUID conversationId, Long before, Long after, Integer limit) {
        if (before != null && after != null) {
            throw new MessagingException(MessagingErrorCode.CONFLICTING_PAGE_DIRECTION);
        }
        ConversationMember member = access.requireMember(conversationId, viewer);
        int size = PageSizes.resolve(limit, properties.defaultPageSize(), properties.maxPageSize());
        Limit fetch = Limit.of(size + 1);
        long fromSeq = member.getVisibleFromSeq();
        List<Message> rows;
        if (after != null) {
            rows = messages.findNewer(conversationId, after, fromSeq, viewer, fetch);
        } else {
            long upperBound = before == null ? Long.MAX_VALUE : before;
            rows = messages.findOlder(conversationId, upperBound, fromSeq, viewer, fetch);
        }
        List<Message> page = rows.stream().limit(size).toList();
        // Üye listesi yalnız sayfada isteyenin kendi mesajı varsa, bir kez çekilir.
        boolean hasOwn = page.stream().anyMatch(message -> message.getSenderId().equals(viewer));
        long watermark = hasOwn ? ReadWatermark.of(viewer, members.findActiveMembers(conversationId)) : 0L;
        List<MessageView> items = views.decorate(viewer, Map.of(conversationId, member), page.stream()
                .map(message -> message.getSenderId().equals(viewer)
                        ? MessageView.of(message).withReadStatus(ReadWatermark.status(message.getSeq(), watermark))
                        : MessageView.of(message))
                .toList());
        return new MessagePageView(items, rows.size() > size);
    }

    /**
     * MSG-13. Okuma işaretçisini {@code seq}'e kadar ilerletir (geri gitmez; sohbetteki son mesajın ötesine geçmez) ve
     * kalan okunmamış sayısını döner. İşaretçi ilerlediyse okuma aralığı yazılır (MSG-11).
     */
    @Transactional
    public ReadStateView markRead(UUID reader, UUID conversationId, long seq) {
        ConversationMember member = access.requireMember(conversationId, reader);
        long lastMessage = conversations.findById(conversationId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND))
                .getLastMessageSeq();
        tracker.advance(member, Math.min(seq, lastMessage), now());
        // Sayım sorgusu öncesi otomatik flush, ilerlemiş işaretçiyi veritabanına yazar.
        long unread = members.countUnread(reader, List.of(conversationId)).stream()
                .mapToLong(row -> (Long) row[1])
                .sum();
        return new ReadStateView(conversationId, member.getLastReadSeq(), unread);
    }

    private Optional<MessageView> findRetry(UUID sender, UUID conversationId, String clientMessageId,
                                            MessageDraft draft) {
        ConversationMember member = access.requireMember(conversationId, sender);
        return messages.findBySenderIdAndClientMessageId(sender, clientMessageId)
                .map(existing -> {
                    if (!isRetry(existing, conversationId, draft)) {
                        throw new MessagingException(MessagingErrorCode.CLIENT_MESSAGE_ID_REUSED);
                    }
                    // Yeniden denemede durum, kayıttan sonra okunmuş olabilir: aynı transaction'da hesaplanır.
                    long watermark = ReadWatermark.of(sender, members.findActiveMembers(conversationId));
                    return views.decorate(sender, member, MessageView.of(existing)
                            .withReadStatus(ReadWatermark.status(existing.getSeq(), watermark)));
                });
    }

    /**
     * Aynı içerik mi? Ekli taslakta tür kayıtlı mesajdan alınır (görsel ya da ses; taslak türü bilmez) ve bağlı ekin
     * aynı olması da gerekir.
     */
    private boolean isRetry(Message existing, UUID conversationId, MessageDraft draft) {
        MessageType type = draft.attachmentId() != null && existing.getType().hasAttachment()
                ? existing.getType()
                : draft.type();
        if (!existing.isRetryOf(conversationId, type, draft.body(), draft.replyToMessageId(),
                draft.gifProvider(), draft.gifId())) {
            return false;
        }
        return !type.hasAttachment()
                || attachments.findIdByMessageId(existing.getId()).map(draft.attachmentId()::equals).orElse(false);
    }

    /**
     * Ekli taslağın kesin türü bağlanacak dosyadan çözülür (MSG-21): ses → {@code VOICE} (açıklama alınmaz), görsel →
     * {@code IMAGE}. Dosya bu kullanıcının bu sohbete yüklediği, gönderilmemiş bir dosya değilse
     * {@code ATTACHMENT_NOT_AVAILABLE}. Bağlamanın kendisi yine koşullu UPDATE ile yapılır (yarış koruması).
     */
    private MessageType resolveType(UUID sender, UUID conversationId, MessageDraft draft) {
        if (draft.attachmentId() == null) {
            return draft.type();
        }
        String mimeType = attachments.findPendingMimeType(draft.attachmentId(), conversationId, sender)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.ATTACHMENT_NOT_AVAILABLE));
        if (!MessageAttachment.VOICE_MIME_TYPE.equals(mimeType)) {
            return MessageType.IMAGE;
        }
        if (draft.body() != null) {
            throw new MessagingException(MessagingErrorCode.VOICE_CAPTION_NOT_ALLOWED);
        }
        return MessageType.VOICE;
    }

    private SendMessageResult insert(UUID sender, UUID conversationId, String clientMessageId, MessageDraft draft,
                                     GifSnapshot gif) {
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new MessagingException(MessagingErrorCode.CONVERSATION_NOT_FOUND));
        ConversationMember senderMember = access.requireMember(conversationId, sender);
        // Kilit beklenirken aynı istek (aynı sohbette) kaydedilmiş olabilir.
        Optional<MessageView> retried = findRetry(sender, conversationId, clientMessageId, draft);
        if (retried.isPresent()) {
            return new SendMessageResult(retried.get(), false);
        }
        MessageType type = resolveType(sender, conversationId, draft);
        Instant now = now();
        appender.requireWithinRate(sender, 1, now);
        UUID replyTo = draft.replyToMessageId();
        if (replyTo != null) {
            VisibleMessages.requireNotDeleted(visible.require(senderMember, replyTo));
        }
        MessageView view = appender.append(conversation, senderMember, now, seq -> switch (type) {
            case TEXT -> Message.text(conversationId, seq, sender, clientMessageId, replyTo, draft.body(), now);
            case IMAGE -> Message.image(conversationId, seq, sender, clientMessageId, replyTo, draft.body(), now);
            case GIF -> Message.gif(conversationId, seq, sender, clientMessageId, replyTo, draft.body(), gif, now);
            case VOICE -> Message.voice(conversationId, seq, sender, clientMessageId, replyTo, now);
        }, message -> {
            // Bağlanamayan dosya transaction'ı geri alır: mesaj ve sıra da kaydedilmez.
            if (type.hasAttachment()
                    && attachments.link(draft.attachmentId(), conversationId, sender, message.getId()) != 1) {
                throw new MessagingException(MessagingErrorCode.ATTACHMENT_NOT_AVAILABLE);
            }
        });
        return new SendMessageResult(view, true);
    }


    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
