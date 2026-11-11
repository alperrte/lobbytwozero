package com.ltz.messaging.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.application.service.MessageView;
import com.ltz.messaging.domain.enums.MessageType;
import com.ltz.messaging.domain.enums.ReadStatus;

/**
 * Mesaj ve bilgileri (MSG-10). Akıştaki {@code message} olayının verisiyle aynı alanlara sahiptir. {@code readStatus}
 * yalnız isteyenin kendi mesajında doludur (MSG-11). {@code attachment} yalnız {@code IMAGE}, {@code gif} yalnız
 * {@code GIF} türünde doludur (MSG-17/22); bu türlerde {@code body} isteğe bağlı açıklamadır.
 * <ul>
 *   <li>{@code replyTo}: yanıtlanan mesajın isteyene göre önizlemesi (MSG-19).</li>
 *   <li>{@code forwarded}, {@code forwardedManyTimes}: iletildi / çok kez (5+) iletildi (MSG-20).</li>
 *   <li>{@code deleted}: herkesten silindi (MSG-26); {@code body}, {@code attachment}, {@code gif}, {@code replyTo} boş,
 *       tepki özeti boştur.</li>
 *   <li>{@code reactions}: tepki özeti ve isteyenin kendi tepkisi (MSG-23).</li>
 *   <li>{@code deletableForEveryoneUntil}: isteyen bu mesajı herkesten silebiliyorsa son an, silemiyorsa {@code null}
 *       (MSG-24 menüsü; yetkiyi sunucu ayrıca uygular).</li>
 * </ul>
 */
public record MessageResponse(UUID id, UUID conversationId, long seq, UUID senderId, MessageType type, String body,
                              String clientMessageId, Instant createdAt, ReadStatus readStatus,
                              AttachmentResponse attachment, GifResponse gif, ReplyPreviewResponse replyTo,
                              boolean forwarded, boolean forwardedManyTimes, boolean deleted,
                              ReactionsResponse reactions, Instant deletableForEveryoneUntil) {

    /** "Çok kez iletildi" eşiği (MSG-20). */
    static final int FORWARDED_MANY_TIMES = 5;

    public static MessageResponse from(MessageView view) {
        return new MessageResponse(view.id(), view.conversationId(), view.seq(), view.senderId(), view.type(),
                view.body(), view.clientMessageId(), view.createdAt(), view.readStatus(),
                AttachmentResponse.from(view.attachment()), GifResponse.from(view.gif()),
                ReplyPreviewResponse.from(view.replyTo()), view.forwardCount() >= 1,
                view.forwardCount() >= FORWARDED_MANY_TIMES, view.deleted(), ReactionsResponse.from(view.reactions()),
                view.deletableForEveryoneUntil());
    }
}
