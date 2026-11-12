package com.ltz.messaging.application.port;

import java.util.List;
import java.util.Optional;

import com.ltz.messaging.domain.enums.GifProvider;

/**
 * GIF platformu (MSG-22; üretimde GIPHY). Anahtar yalnız sunucuda tutulur; istemci platforma Messaging üzerinden arar.
 * Dönen adresler yalnız izinli HTTPS sunucularını gösterir ve sorgu dizgesi atılmıştır.
 */
public interface GifProviderPort {

    /** @throws GifProviderUnavailableException anahtar yoksa ya da platform yanıt vermezse */
    GifPage search(String query, int limit, int offset, String language);

    /** @throws GifProviderUnavailableException anahtar yoksa ya da platform yanıt vermezse */
    GifPage trending(int limit, int offset);

    /**
     * Gönderilecek GIF'i platformdan yeniden çözer; istemcinin bildirdiği adres kullanılmaz.
     *
     * @return GIF yoksa, içerik derecesi izin verilenin üstündeyse ya da adresleri geçersizse boş
     * @throws GifProviderUnavailableException anahtar yoksa ya da platform yanıt vermezse
     */
    Optional<GifItem> find(String gifId);

    /** Platformdaki bir GIF: mesajda gösterilecek adres ({@code url}) ve seçicideki küçük önizleme. */
    record GifItem(GifProvider provider, String id, String title, String url, String previewUrl, int width,
                   int height) {
    }

    /** {@code nextOffset}: sonraki sayfa yoksa {@code null}. */
    record GifPage(List<GifItem> items, Integer nextOffset) {

        public GifPage {
            items = List.copyOf(items);
        }
    }
}
