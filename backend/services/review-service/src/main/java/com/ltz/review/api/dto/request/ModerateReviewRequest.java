package com.ltz.review.api.dto.request;

import com.ltz.review.domain.enums.ModerationAction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code version}: moderatörün gördüğü inceleme sürümü; yazar bu arada düzenlediyse karar 409 ile reddedilir. */
public record ModerateReviewRequest(@NotNull ModerationAction action, @NotBlank @Size(max = 500) String reason, @NotNull @Min(0) Long version) {}
