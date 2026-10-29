package com.ltz.game.api.dto.request;
import com.ltz.game.domain.enums.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
public record FeedbackStatusRequest(@NotNull FeedbackStatus status,@NotNull @PositiveOrZero Long version) { }
