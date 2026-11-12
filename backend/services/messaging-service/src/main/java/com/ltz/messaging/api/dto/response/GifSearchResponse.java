package com.ltz.messaging.api.dto.response;

import java.util.List;

import com.ltz.messaging.application.port.GifProviderPort.GifItem;
import com.ltz.messaging.application.port.GifProviderPort.GifPage;
import com.ltz.messaging.domain.enums.GifProvider;

/** GIF arama/öne çıkanlar sayfası (MSG-22). {@code nextOffset}: sonraki sayfa yoksa {@code null}. */
public record GifSearchResponse(List<Item> items, Integer nextOffset) {

    /** {@code title} platformdaki başlıktır (kullanıcı içeriği; düz metin gösterilir), {@code null} olabilir. */
    public record Item(GifProvider provider, String id, String title, String url, String previewUrl, int width,
                       int height) {

        static Item from(GifItem item) {
            return new Item(item.provider(), item.id(), item.title(), item.url(), item.previewUrl(), item.width(),
                    item.height());
        }
    }

    public static GifSearchResponse from(GifPage page) {
        return new GifSearchResponse(page.items().stream().map(Item::from).toList(), page.nextOffset());
    }
}
