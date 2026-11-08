package com.ltz.auth.api.dto.request;

import java.time.Instant;

import com.ltz.auth.domain.enums.AccountStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Gerekçe zorunludur ve denetim kaydına yazılır (SECURITY.md §15). {@code suspendedUntil}: yalnız {@code SUSPENDED}
 * hedefinde verilebilir; verilirse süre dolunca hesap otomatik ACTIVE olur, verilmezse askı süresizdir (elle açılır).
 */
public record StatusChangeRequest(@NotNull AccountStatus status,
                                  @NotBlank @Size(max = AdminReason.MAX_LENGTH) String reason,
                                  Instant suspendedUntil) {

    @AssertTrue
    public boolean isSuspensionEndOnlyForSuspended() {
        return suspendedUntil == null || status == AccountStatus.SUSPENDED;
    }
}
