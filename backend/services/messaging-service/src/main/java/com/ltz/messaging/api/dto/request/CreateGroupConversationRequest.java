package com.ltz.messaging.api.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Grup oluşturma. Başlık kırpılarak kaydedilir; kontrol karakteri içeremez. {@code memberIds} oluşturan dışındaki
 * ilk üyelerdir (en az 1); yinelenen kimlikler tek sayılır. Üst sınır yapılandırılan grup boyutundan da denetlenir.
 */
public record CreateGroupConversationRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}]*") String title,
        @NotNull @Size(min = 1, max = 99) List<@NotNull UUID> memberIds) {
}
