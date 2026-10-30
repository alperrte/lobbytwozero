package com.ltz.user.api.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Toplu profil kartı (arkadaş listesinde ad/avatar). En çok 50 kimlik; yinelenenler tek sayılır. */
public record BatchProfilesRequest(@NotNull @Size(min = 1, max = 50) List<@NotNull UUID> userIds) {
}
