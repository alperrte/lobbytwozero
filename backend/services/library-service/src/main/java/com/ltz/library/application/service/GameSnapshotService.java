package com.ltz.library.application.service;

import com.ltz.library.application.port.GameSummary;
import com.ltz.library.domain.entity.GameSnapshot;
import com.ltz.library.infrastructure.persistence.repository.GameSnapshotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameSnapshotService {
    private final GameSnapshotRepository snapshots;
    public GameSnapshotService(GameSnapshotRepository snapshots) { this.snapshots = snapshots; }

    @Transactional(readOnly = true, timeout = 4)
    public Map<UUID, GameSnapshot> read(List<UUID> ids) { return snapshots.find(ids); }

    @Transactional(timeout = 4)
    public void remember(List<GameSummary> cards, Instant observedAt) { snapshots.remember(cards, observedAt); }
}
