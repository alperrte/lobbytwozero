package com.ltz.messaging.api.dto.request;

import java.util.UUID;

import com.ltz.messaging.application.service.MessageDraft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mesaj gönderme (MSG-06, MSG-17, MSG-18, MSG-22). {@code clientMessageId} istemcinin ürettiği tekil kimliktir (ör. UUID):
 * aynı kimlikle yeniden gönderim yeni mesaj oluşturmaz.
 * <ul>
 *   <li>{@code body}: metin mesajında zorunlu, görsel/GIF mesajında isteğe bağlı açıklama. Olduğu gibi saklanır (emoji,
 *       ZWJ dizileri, ten rengi ve bayraklar dahil; satır sonu serbest). Boşluktan ibaret olamaz; satır sonu ve sekme
 *       dışında kontrol karakteri ve eşlenmemiş UTF-16 vekil (bozuk emoji) içeremez. Uzunluk UTF-16 birimiyle en çok 4000
 *       (JavaScript {@code string.length} ile aynı; çoğu emoji 2 birimdir). Sunucu HTML yorumlamaz.</li>
 *   <li>{@code attachmentId}: aynı sohbete daha önce yüklenmiş, henüz gönderilmemiş kendi görseliniz.</li>
 *   <li>{@code gif}: GIF platformundaki GIF; adresler sunucuda platformdan çözülür.</li>
 *   <li>{@code replyToMessageId}: isteğe bağlı; aynı sohbette görebildiğiniz, silinmemiş bir mesaja yanıt (MSG-19).</li>
 * </ul>
 * {@code attachmentId} ile {@code gif} birlikte verilemez.
 */
public record SendMessageRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String clientMessageId,
        @Size(max = 4000) @Pattern(regexp = SendMessageRequest.BODY_PATTERN) String body,
        UUID attachmentId,
        @Valid GifReferenceRequest gif,
        UUID replyToMessageId) {

    /**
     * En az bir boşluk dışı karakter; satır sonu ve sekme dışında C0 kontrol karakteri, DEL ve eşlenmemiş vekil yok. Java
     * regex eşlenmiş vekil çiftini tek kod noktası (ör. U+1F600) olarak gördüğünden emoji bu aralığa düşmez.
     */
    static final String BODY_PATTERN = "(?s)(?=.*\\S)[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\x{D800}-\\x{DFFF}]*";

    /**
     * Uygulama katmanının içerik modeli. Tür ve içerik kuralları (görsel ile GIF birlikte olamaz, içerik zorunlu) orada
     * denetlenir: {@code 400 CONFLICTING_MESSAGE_CONTENT}, {@code 400 MESSAGE_CONTENT_REQUIRED}.
     */
    public MessageDraft toDraft() {
        if (gif == null) {
            return new MessageDraft(body, attachmentId, null, null, replyToMessageId);
        }
        return new MessageDraft(body, attachmentId, gif.provider(), gif.id(), replyToMessageId);
    }
}
