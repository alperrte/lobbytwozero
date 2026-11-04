package com.ltz.auth.api.dto.request;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.AccountStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Servis kimlikli durum değişikliği. {@code actorId}: işlemi yapan yönetici hesabı; Auth ACTIVE ve ADMIN rolünde olduğunu
 * doğrular, denetim kaydına bu kimlik yazılır. Gerekçe zorunludur. {@code suspendedUntil}: yalnız {@code SUSPENDED}
 * hedefinde verilebilir (süre dolunca otomatik ACTIVE); verilmezse askı süresizdir.
 */
public record InternalStatusChangeRequest(@NotNull UUID actorId, @NotNull AccountStatus status,
                                          @NotBlank @Size(max = AdminReason.MAX_LENGTH) String reason,
                                          Instant suspendedUntil) {

    @AssertTrue
    public boolean isSuspensionEndOnlyForSuspended() {
        return suspendedUntil == null || status == AccountStatus.SUSPENDED;
    }
}
