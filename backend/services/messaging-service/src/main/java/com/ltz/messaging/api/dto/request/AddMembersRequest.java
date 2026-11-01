package com.ltz.messaging.api.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Gruba eklenecek kullanıcılar; yinelenen kimlikler tek sayılır. */
public record AddMembersRequest(@NotNull @Size(min = 1, max = 99) List<@NotNull UUID> userIds) {
}
