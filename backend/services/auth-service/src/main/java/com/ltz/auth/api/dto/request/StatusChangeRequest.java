package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.enums.AccountStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Gerekçe zorunludur ve denetim kaydına yazılır (SECURITY.md §15). */
public record StatusChangeRequest(@NotNull AccountStatus status,
                                  @NotBlank @Size(max = AdminReason.MAX_LENGTH) String reason) {
}
