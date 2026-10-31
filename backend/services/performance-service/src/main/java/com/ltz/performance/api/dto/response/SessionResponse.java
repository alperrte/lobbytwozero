package com.ltz.performance.api.dto.response;

import com.ltz.performance.domain.entity.PerformanceSession;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID id, UUID gameId, String gameTitle, UUID hardwareProfileId, UUID deviceLinkId,
        String source, String status, Instant startedAt, Instant endedAt, Long durationSeconds) {
    public static SessionResponse from(PerformanceSession session) {
        return new SessionResponse(session.getId(), session.getGameId(), session.getGameTitle(),
                session.getHardwareProfileId(), session.getDeviceLinkId(), session.getSource().name(),
                session.getStatus().name(), session.getStartedAt(), session.getEndedAt(), session.durationSeconds());
    }
}
