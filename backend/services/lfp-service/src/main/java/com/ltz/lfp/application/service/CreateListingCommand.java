package com.ltz.lfp.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.lfp.domain.enums.Platform;

/**
 * İlan oluşturma girdisi (LFP-01..09). Sahip bu kayıtta yoktur: kimlik yalnız doğrulanmış token'dan gelir.
 *
 * @param durationHours ilanın geçerlilik süresi (saat); {@code null} ise varsayılan süre (LFP-15)
 */
public record CreateListingCommand(UUID gameId, Platform platform, String skillLevel, List<String> roles,
                                   int playersNeeded, Instant playTimeStart, Instant playTimeEnd, boolean micRequired,
                                   String description, Integer durationHours) {
}
