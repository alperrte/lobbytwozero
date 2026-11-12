package com.ltz.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Yönetici yaş geri alma isteği; gerekçe zorunludur ve denetim için saklanır. */
public record AgeRevocationRequest(@NotBlank @Size(max = AdminReason.MAX_LENGTH) String reason) {
}
