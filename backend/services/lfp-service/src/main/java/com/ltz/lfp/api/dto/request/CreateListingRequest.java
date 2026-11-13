package com.ltz.lfp.api.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.lfp.application.service.CreateListingCommand;
import com.ltz.lfp.domain.enums.Platform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * İlan oluşturma gövdesi (LFP-01..09). Sahip kimliği gövdede yoktur (token'dan gelir); tanımsız alan 400 döner.
 * Buradaki uzunluklar ham girdinin üst sınırıdır; boşluk atıldıktan sonraki asıl kurallar (açıklama 1–1000, seviye
 * 1–50, rol 1–30 karakter) domain'de uygulanır.
 */
public record CreateListingRequest(
        @NotNull UUID gameId,
        @NotNull Platform platform,
        /** LFP-04: isteğe bağlı rank/seviye/deneyim beklentisi. */
        @Size(max = 200) String skillLevel,
        /** LFP-05: 1–5 rol ya da oyuncu tipi. */
        @NotNull @Size(min = 1, max = 5) List<@NotNull @Size(max = 100) String> roles,
        /** LFP-06: aranan oyuncu sayısı. */
        @NotNull @Min(1) @Max(50) Integer playersNeeded,
        /** LFP-07: isteğe bağlı planlanan başlangıç (ISO-8601, UTC önerilir). */
        Instant playTimeStart,
        /** LFP-07: isteğe bağlı planlanan bitiş; yalnız başlangıçla birlikte. */
        Instant playTimeEnd,
        /** LFP-08: sesli iletişim/mikrofon gerekli mi. */
        @NotNull Boolean micRequired,
        /** LFP-09: oyun tarzı ve aranan oyuncu özellikleri. */
        @NotNull @Size(max = 4000) String description,
        /** LFP-15: geçerlilik süresi (saat, 1–168); verilmezse 24 saat. */
        @Min(1) @Max(168) Integer durationHours) {

    public CreateListingCommand toCommand() {
        return new CreateListingCommand(gameId, platform, skillLevel, roles, playersNeeded, playTimeStart,
                playTimeEnd, micRequired, description, durationHours);
    }
}
