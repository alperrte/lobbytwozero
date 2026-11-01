package com.ltz.messaging;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.ltz.messaging.application.port.BlockCheckPort;
import com.ltz.messaging.application.port.BlockCheckUnavailableException;

/**
 * Social'ın engel sorgusunun test karşılığı: engeller yönlü kaydedilir, sorgu Social gibi iki yöne de bakar. Social'a
 * ulaşılamama durumu ve yapılan çağrı sayısı da taklit edilir.
 */
public class ScriptedBlockCheck implements BlockCheckPort {

    private final Set<String> blocks = ConcurrentHashMap.newKeySet();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean unavailable;

    @Override
    public Set<UUID> blockedAmong(UUID userId, Collection<UUID> otherUsers) {
        calls.incrementAndGet();
        if (unavailable) {
            throw new BlockCheckUnavailableException("Scripted: social-service unavailable.");
        }
        return otherUsers.stream()
                .filter(other -> blocks.contains(userId + ">" + other) || blocks.contains(other + ">" + userId))
                .collect(Collectors.toSet());
    }

    /** {@code blocker}, {@code blocked} kullanıcısını engeller. */
    public void block(UUID blocker, UUID blocked) {
        blocks.add(blocker + ">" + blocked);
    }

    public void unblock(UUID blocker, UUID blocked) {
        blocks.remove(blocker + ">" + blocked);
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        blocks.clear();
        calls.set(0);
        unavailable = false;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {

        @Bean
        @ConditionalOnProperty(name = "ltz.messaging.blocks.adapter", havingValue = "scripted")
        ScriptedBlockCheck scriptedBlockCheck() {
            return new ScriptedBlockCheck();
        }
    }
}
