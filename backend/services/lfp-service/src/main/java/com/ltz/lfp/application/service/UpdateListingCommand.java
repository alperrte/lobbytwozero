package com.ltz.lfp.application.service;

import java.time.Instant;
import java.util.List;

import com.ltz.lfp.domain.enums.Platform;

/**
 * İlan güncelleme girdisi (LFP-12): sahibin değiştirebildiği alanların tamamı (tam değiştirme). Oyun ve süre
 * değişmez. {@code version} istemcinin son gördüğü sürümdür; farklıysa güncelleme 409 ile reddedilir.
 */
public record UpdateListingCommand(Platform platform, String skillLevel, List<String> roles, int playersNeeded,
                                   Instant playTimeStart, Instant playTimeEnd, boolean micRequired,
                                   String description, long version) {
}
