package com.ltz.lfp.api.dto.request;

import java.time.Instant;
import java.util.List;

import com.ltz.lfp.application.service.UpdateListingCommand;
import com.ltz.lfp.domain.enums.Platform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * İlan güncelleme gövdesi (LFP-12): değiştirilebilen alanların tamamı gönderilir (tam değiştirme). {@code gameId} ve
 * {@code durationHours} burada yoktur; gönderilirse bilinmeyen alan olarak 400 döner. {@code version} son okunan
 * ilandaki değerdir.
 */
public record UpdateListingRequest(
        @NotNull Platform platform,
        @Size(max = 200) String skillLevel,
        @NotNull @Size(min = 1, max = 5) List<@NotNull @Size(max = 100) String> roles,
        @NotNull @Min(1) @Max(50) Integer playersNeeded,
        Instant playTimeStart,
        Instant playTimeEnd,
        @NotNull Boolean micRequired,
        @NotNull @Size(max = 4000) String description,
        @NotNull @Min(0) Long version) {

    public UpdateListingCommand toCommand() {
        return new UpdateListingCommand(platform, skillLevel, roles, playersNeeded, playTimeStart, playTimeEnd,
                micRequired, description, version);
    }
}
