package com.ltz.messaging.infrastructure.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ltz.messaging.application.port.GifProviderPort;
import com.ltz.messaging.application.port.GifProviderUnavailableException;
import com.ltz.messaging.domain.enums.GifProvider;
import com.ltz.messaging.infrastructure.config.GifProperties;

/**
 * GIPHY API v1 adaptörü (MSG-22): {@code /v1/gifs/search}, {@code /v1/gifs/trending} ve {@code /v1/gifs/{id}}.
 * <ul>
 *   <li>Anahtar yalnız sunucudadır ve platformun istediği biçimde sorgu dizgesinde gider; bu yüzden hiçbir hata
 *       iletisine, loga ya da kök hataya istek adresi yazılmaz ({@link GifProviderUnavailableException}).</li>
 *   <li>İçerik derecesi istekte süzülür ve yanıtta yeniden denetlenir; derecesi bilinmeyen GIF kabul edilmez.</li>
 *   <li>Adresler yalnız GIPHY medya sunucularındaki HTTPS adresleridir; sorgu dizgesi (izleme kimlikleri) atılır.
 *       Eksik ya da geçersiz adresli öğe listeden çıkarılır.</li>
 *   <li>Mesajda {@code fixed_height} (200 px yükseklik), seçicide {@code fixed_height_small} (100 px) kullanılır.</li>
 * </ul>
 * Zaman aşımı ve yönlendirme ayarları {@code spring.http.clients.*} ile gelir; istek otomatik tekrarlanmaz.
 */
public class GiphyGifClient implements GifProviderPort {

    private static final Pattern MEDIA_HOST = Pattern.compile("(?:media\\d?|i)\\.giphy\\.com");
    private static final Pattern GIF_ID = Pattern.compile("[A-Za-z0-9]{1,64}");
    private static final List<String> RATING_ORDER = List.of("g", "pg", "pg-13", "r");
    private static final int MAX_TITLE_LENGTH = 140;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_DIMENSION = 4096;
    private static final String MESSAGE_RENDITION = "fixed_height";
    private static final String PREVIEW_RENDITION = "fixed_height_small";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListResponse(List<GiphyGif> data, Pagination pagination) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SingleResponse(GiphyGif data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pagination(@JsonProperty("total_count") Integer totalCount, Integer count, Integer offset) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GiphyGif(String id, String title, String rating, Map<String, Rendition> images) {
    }

    /** GIPHY ölçüleri metin olarak döner ("200"). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rendition(String url, String width, String height) {
    }

    private final RestClient restClient;
    private final GifProperties properties;
    private final int allowedRatingRank;

    public GiphyGifClient(RestClient.Builder builder, GifProperties properties) {
        this.restClient = builder.clone().baseUrl(properties.apiUrl()).build();
        this.properties = properties;
        this.allowedRatingRank = RATING_ORDER.indexOf(properties.rating());
    }

    @Override
    public GifPage search(String query, int limit, int offset, String language) {
        ListResponse response = fetchList(() -> restClient.get()
                .uri("/v1/gifs/search?api_key={key}&q={q}&limit={limit}&offset={offset}&rating={rating}&lang={lang}",
                        Map.of("key", apiKey(), "q", query, "limit", limit, "offset", offset,
                                "rating", properties.rating(), "lang", language))
                .retrieve()
                .body(ListResponse.class));
        return page(response, offset);
    }

    @Override
    public GifPage trending(int limit, int offset) {
        ListResponse response = fetchList(() -> restClient.get()
                .uri("/v1/gifs/trending?api_key={key}&limit={limit}&offset={offset}&rating={rating}",
                        Map.of("key", apiKey(), "limit", limit, "offset", offset, "rating", properties.rating()))
                .retrieve()
                .body(ListResponse.class));
        return page(response, offset);
    }

    @Override
    public Optional<GifItem> find(String gifId) {
        if (gifId == null || !GIF_ID.matcher(gifId).matches()) {
            return Optional.empty();
        }
        String key = apiKey();
        SingleResponse response;
        try {
            response = fetch(() -> restClient.get()
                    .uri("/v1/gifs/{id}?api_key={key}", Map.of("id", gifId, "key", key))
                    .retrieve()
                    .body(SingleResponse.class));
        } catch (GifNotFound _) {
            return Optional.empty();
        }
        return Optional.ofNullable(response).map(SingleResponse::data).flatMap(this::toItem)
                .filter(item -> item.id().equals(gifId));
    }

    private String apiKey() {
        if (!properties.hasApiKey()) {
            throw new GifProviderUnavailableException("GIF provider API key is not configured.");
        }
        return properties.apiKey();
    }

    /** Kök hata ne bağlanır ne okunur: iletisi anahtarlı istek adresini içerebilir. Tür, sabit iletiyle ayrılır. */
    private <T> T fetch(Call<T> call) {
        try {
            return call.run();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest _) {
            throw new GifNotFound();
        } catch (ResourceAccessException _) {
            throw new GifProviderUnavailableException("GIF provider unreachable or timed out.");
        } catch (HttpStatusCodeException _) {
            throw new GifProviderUnavailableException("GIF provider returned an error status.");
        } catch (RestClientException _) {
            throw new GifProviderUnavailableException("GIF provider response could not be read.");
        }
    }

    /** Liste çağrısında platformun 400/404 yanıtı da ulaşılamama sayılır. */
    private ListResponse fetchList(Call<ListResponse> call) {
        try {
            return fetch(call);
        } catch (GifNotFound _) {
            throw new GifProviderUnavailableException("GIF provider rejected the request.");
        }
    }

    private GifPage page(ListResponse response, int offset) {
        if (response == null || response.data() == null) {
            throw new GifProviderUnavailableException("GIF provider returned no results.");
        }
        List<GifItem> items = response.data().stream()
                .filter(Objects::nonNull)
                .map(this::toItem)
                .flatMap(Optional::stream)
                .toList();
        return new GifPage(items, nextOffset(response.pagination(), offset, response.data().size()));
    }

    private Integer nextOffset(Pagination pagination, int offset, int returned) {
        if (pagination == null || pagination.totalCount() == null || returned == 0) {
            return null;
        }
        int next = offset + returned;
        return next < pagination.totalCount() && next <= properties.maxOffset() ? next : null;
    }

    private Optional<GifItem> toItem(GiphyGif gif) {
        if (gif == null || gif.id() == null || !GIF_ID.matcher(gif.id()).matches() || !ratingAllowed(gif.rating())
                || gif.images() == null) {
            return Optional.empty();
        }
        Rendition message = gif.images().get(MESSAGE_RENDITION);
        Rendition preview = gif.images().get(PREVIEW_RENDITION);
        Optional<String> url = message == null ? Optional.empty() : mediaUrl(message.url());
        Optional<String> previewUrl = preview == null ? Optional.empty() : mediaUrl(preview.url());
        int width = message == null ? 0 : dimension(message.width());
        int height = message == null ? 0 : dimension(message.height());
        if (url.isEmpty() || previewUrl.isEmpty() || width == 0 || height == 0) {
            return Optional.empty();
        }
        return Optional.of(new GifItem(GifProvider.GIPHY, gif.id(), title(gif.title()), url.get(), previewUrl.get(),
                width, height));
    }

    private boolean ratingAllowed(String rating) {
        int rank = rating == null ? -1 : RATING_ORDER.indexOf(rating.toLowerCase(Locale.ROOT));
        return rank >= 0 && rank <= allowedRatingRank;
    }

    /** Yalnız GIPHY medya sunucusundaki HTTPS adresi; sorgu dizgesi ve parça atılır. */
    static Optional<String> mediaUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() != -1 || !MEDIA_HOST.matcher(uri.getHost().toLowerCase(Locale.ROOT)).matches()
                    || uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
                return Optional.empty();
            }
            String clean = "https://" + uri.getHost().toLowerCase(Locale.ROOT) + uri.getRawPath();
            return clean.length() <= MAX_URL_LENGTH ? Optional.of(clean) : Optional.empty();
        } catch (URISyntaxException _) {
            return Optional.empty();
        }
    }

    private static int dimension(String raw) {
        try {
            int value = raw == null ? 0 : Integer.parseInt(raw.trim());
            return value >= 1 && value <= MAX_DIMENSION ? value : 0;
        } catch (NumberFormatException _) {
            return 0;
        }
    }

    /** Platform başlığı kullanıcı içeriğidir: kontrol karakterleri atılır, kısaltılır; istemci düz metin gösterir. */
    private static String title(String raw) {
        if (raw == null) {
            return null;
        }
        String clean = raw.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(MAX_TITLE_LENGTH)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .strip();
        return clean.isEmpty() ? null : clean;
    }

    @FunctionalInterface
    private interface Call<T> {
        T run();
    }

    /** Tek GIF sorgusunda platform "yok" dedi. */
    private static final class GifNotFound extends RuntimeException {

        private static final long serialVersionUID = 1L;

        GifNotFound() {
            super("GIF not found", null, false, false);
        }
    }
}
