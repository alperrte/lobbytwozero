package com.ltz.lfp;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.ltz.lfp.application.port.CatalogGame;
import com.ltz.lfp.application.port.GameCatalogPort;
import com.ltz.lfp.application.port.GameCatalogUnavailableException;

/**
 * game-service'in oyun kartı ucunun test karşılığı: kayıtlı oyunlar döner, diğerleri "katalogda yok" sayılır.
 * Kataloğa ulaşılamama durumu ve yapılan çağrı sayısı da taklit edilir. Gerçek istemci
 * {@code GameServiceClientTest}'te MockRestServiceServer ile ayrıca sınanır.
 */
public class ScriptedGameCatalog implements GameCatalogPort {

    private final Map<UUID, CatalogGame> games = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean unavailable;

    @Override
    public Optional<CatalogGame> find(UUID gameId) {
        calls.incrementAndGet();
        if (unavailable) {
            throw new GameCatalogUnavailableException("Scripted: game-service unavailable.");
        }
        return Optional.ofNullable(games.get(gameId));
    }

    /** Katalogda yayında bir ana oyun ekler ve kimliğini döner. */
    public UUID addGame(String title) {
        return addGame(title, "MAIN_GAME");
    }

    public UUID addGame(String title, String gameType) {
        UUID gameId = UUID.randomUUID();
        games.put(gameId, new CatalogGame(gameId, title, gameType));
        return gameId;
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        games.clear();
        calls.set(0);
        unavailable = false;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {

        @Bean
        @ConditionalOnProperty(name = "ltz.lfp.games.adapter", havingValue = "scripted")
        ScriptedGameCatalog scriptedGameCatalog() {
            return new ScriptedGameCatalog();
        }
    }
}
