package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.GameDiscoveryResponse;
import com.ltz.game.infrastructure.persistence.repository.GameDiscoveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class GameDiscoveryService {
    private final GameDiscoveryRepository repository;
    // Count, sayfa ve kart ilişkileri tek MVCC görünümünü kullanır.
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public GameDiscoveryResponse list(DiscoveryQuery query) { return repository.discover(query); }
}
