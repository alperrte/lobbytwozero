package com.ltz.performance.application.service;

import com.ltz.performance.domain.enums.SessionStatus;
import java.util.UUID;

/** Geçmiş/istatistik sorgularının oturum filtresi; tüm alanlar isteğe bağlıdır ({@code startedAt} aralığı yarı açık). */
public record SessionFilter(UUID gameId, UUID hardwareProfileId, UUID deviceLinkId, SessionStatus status,
        TimeWindow startedAt) {
    public SessionFilter {
        if (startedAt == null) startedAt = TimeWindow.UNBOUNDED;
    }
}
