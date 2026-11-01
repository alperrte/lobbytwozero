package com.ltz.messaging.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Metin mesajı. {@code clientMessageId} istemcinin ürettiği tekil kimliktir (ör. UUID): aynı kimlikle yeniden gönderim
 * yeni mesaj oluşturmaz. {@code body} olduğu gibi saklanır (emoji ve satır sonu serbest); boşluktan ibaret olamaz,
 * satır sonu ve sekme dışında kontrol karakteri içeremez. Sunucu HTML yorumlamaz; istemci düz metin olarak gösterir.
 */
public record SendMessageRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String clientMessageId,
        @NotBlank @Size(max = 4000) @Pattern(regexp = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*") String body) {
}
