package com.ltz.messaging;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.ltz.messaging.application.port.GifProviderPort;
import com.ltz.messaging.application.port.GifProviderUnavailableException;
import com.ltz.messaging.domain.enums.GifProvider;

/**
 * GIF platformunun test karşılığı: kayıtlı GIF'ler aramada ve kimlikle bulunur; platforma ulaşılamama ve yapılan çağrı
 * sayısı taklit edilir. Gerçek GIPHY istemcisi {@code GiphyGifClientTest}'te sınanır.
 */
public class ScriptedGifProvider implements GifProviderPort {

    private final Map<String, GifItem> gifs = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean unavailable;

    @Override
    public GifPage search(String query, int limit, int offset, String language) {
        calls.incrementAndGet();
        requireAvailable();
        List<GifItem> found = gifs.values().stream()
                .filter(gif -> gif.title() != null && gif.title().contains(query))
                .sorted((first, second) -> first.id().compareTo(second.id()))
                .skip(offset)
                .limit(limit)
                .toList();
        return new GifPage(found, null);
    }

    @Override
    public GifPage trending(int limit, int offset) {
        calls.incrementAndGet();
        requireAvailable();
        return new GifPage(gifs.values().stream().limit(limit).toList(), null);
    }

    @Override
    public Optional<GifItem> find(String gifId) {
        calls.incrementAndGet();
        requireAvailable();
        return Optional.ofNullable(gifs.get(gifId));
    }

    /** Platformda {@code id} kimlikli GIF yayımlar; adresler platformun gerçek medya sunucusu biçimindedir. */
    public GifItem publish(String id, String title) {
        GifItem item = new GifItem(GifProvider.GIPHY, id, title, "https://media1.giphy.com/media/" + id + "/200.gif",
                "https://media1.giphy.com/media/" + id + "/100.gif", 267, 200);
        gifs.put(id, item);
        return item;
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        gifs.clear();
        calls.set(0);
        unavailable = false;
    }

    private void requireAvailable() {
        if (unavailable) {
            throw new GifProviderUnavailableException("Scripted: GIF provider unavailable.");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {

        @Bean
        @ConditionalOnProperty(name = "ltz.messaging.gifs.adapter", havingValue = "scripted")
        ScriptedGifProvider scriptedGifProvider() {
            return new ScriptedGifProvider();
        }
    }
}
