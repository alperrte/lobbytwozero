package com.ltz.auth.api.dto.request;

import java.util.UUID;

import com.ltz.auth.domain.enums.RoleCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Servis kimlikli rol değişikliği; {@code actorId} için bkz. {@link InternalStatusChangeRequest}. */
public record InternalRoleChangeRequest(@NotNull UUID actorId, @NotNull RoleCode role,
                                        @NotBlank @Size(max = AdminReason.MAX_LENGTH) String reason) {
}
