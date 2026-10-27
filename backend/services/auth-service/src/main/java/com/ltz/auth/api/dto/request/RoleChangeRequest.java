package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.enums.RoleCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleChangeRequest(@NotNull RoleCode role, @NotBlank @Size(max = AdminReason.MAX_LENGTH) String reason) {
}
