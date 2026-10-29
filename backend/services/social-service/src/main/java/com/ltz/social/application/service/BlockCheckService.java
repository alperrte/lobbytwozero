package com.ltz.social.application.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.social.infrastructure.persistence.repository.UserBlockRepository;

import lombok.RequiredArgsConstructor;

/**
 * Servisler arası engel sorgusu (SOC-15): messaging ve lfp, bir kullanıcı ile bir grup kullanıcı arasında engel olup
 * olmadığını sorar. Engel yönlü saklanır ama sonuç yönden bağımsızdır: iki yönden biri yeterlidir. Yanıtta yalnız
 * kimlik ve {@code blocked} bayrağı bulunur; engelin yönü ve zamanı sızdırılmaz.
 */
@Service
@RequiredArgsConstructor
public class BlockCheckService {

    private final UserBlockRepository blocks;

    /** Her ayrık {@code otherUserIds} değeri için, girdideki sırayla bir sonuç döner. */
    @Transactional(readOnly = true)
    public List<BlockCheckResult> check(UUID userId, List<UUID> otherUserIds) {
        Set<UUID> others = new LinkedHashSet<>(otherUserIds);
        Set<UUID> blocked = new HashSet<>(blocks.findBlockedAmong(userId, others));
        blocked.addAll(blocks.findBlockersAmong(userId, others));
        List<BlockCheckResult> results = new ArrayList<>(others.size());
        for (UUID other : others) {
            results.add(new BlockCheckResult(other, blocked.contains(other)));
        }
        return results;
    }
}
