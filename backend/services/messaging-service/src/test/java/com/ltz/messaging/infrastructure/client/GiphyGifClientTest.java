package com.ltz.messaging.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ltz.messaging.application.port.GifProviderPort.GifItem;
import com.ltz.messaging.application.port.GifProviderPort.GifPage;
import com.ltz.messaging.application.port.GifProviderUnavailableException;
import com.ltz.messaging.domain.enums.GifProvider;
import com.ltz.messaging.infrastructure.config.GifProperties;

/**
 * GIPHY adaptörü: istek parametreleri (anahtar, kodlanmış arama metni, derece, dil), yanıtın süzülmesi (derece, medya
 * sunucusu, HTTPS, eksik ölçü), izleme parametrelerinin atılması ve her hatada anahtar sızdırmadan fail-closed.
 */
class GiphyGifClientTest {

    private static final String KEY = "test-only-giphy-key";
    private static final String API = "https://api.giphy.test";

    private MockRestServiceServer server;
    private GiphyGifClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GiphyGifClient(builder, properties(KEY));
    }

    private static GifProperties properties(String key) {
        return new GifProperties(API, key, "pg-13", 24, 50, 499, 50, 30);
    }

    private static String gif(String id, String rating, String url, String preview, String width) {
        return """
                {"type":"gif","id":"%s","title":"Kedi\\u0007 dans","rating":"%s","images":{
                  "fixed_height":{"url":"%s","width":"%s","height":"200","mp4":"ignored"},
                  "fixed_height_small":{"url":"%s","width":"133","height":"100"},
                  "original":{"url":"https://media.giphy.com/media/%s/giphy.gif"}}}"""
                .formatted(id, rating, url, width, preview, id);
    }

    private static String list(int total, int offset, String... gifs) {
        return "{\"data\":[" + String.join(",", gifs) + "],\"pagination\":{\"total_count\":" + total
                + ",\"count\":" + gifs.length + ",\"offset\":" + offset + "},\"meta\":{\"status\":200}}";
    }

    @Test
    void aramaParametreleriGonderilirSonuclarSuzulur() {
        server.expect(once(), requestTo(Matchers.startsWith(API + "/v1/gifs/search?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("api_key", KEY))
                .andExpect(queryParam("q", "kedi%20%7Bdans%7D%20%26%20m%C3%BCzik"))
                .andExpect(queryParam("limit", "4"))
                .andExpect(queryParam("offset", "10"))
                .andExpect(queryParam("rating", "pg-13"))
                .andExpect(queryParam("lang", "tr"))
                .andRespond(withSuccess(list(100, 10,
                        gif("ok1", "g", "https://media2.giphy.com/media/ok1/200.gif?cid=abc&rid=200.gif&ct=g",
                                "https://media2.giphy.com/media/ok1/100.gif?cid=abc", "267"),
                        gif("adult", "r", "https://media.giphy.com/media/adult/200.gif",
                                "https://media.giphy.com/media/adult/100.gif", "200"),
                        gif("plain", "g", "http://media.giphy.com/media/plain/200.gif",
                                "https://media.giphy.com/media/plain/100.gif", "200"),
                        gif("evil", "g", "https://evil.example/200.gif", "https://media.giphy.com/x/100.gif", "200"),
                        gif("nowidth", "g", "https://i.giphy.com/media/nowidth/200.gif",
                                "https://i.giphy.com/media/nowidth/100.gif", "abc")),
                        MediaType.APPLICATION_JSON));

        GifPage page = client.search("kedi {dans} & müzik", 4, 10, "tr");

        server.verify();
        assertThat(page.items()).containsExactly(new GifItem(GifProvider.GIPHY, "ok1", "Kedi dans",
                "https://media2.giphy.com/media/ok1/200.gif", "https://media2.giphy.com/media/ok1/100.gif", 267, 200));
        assertThat(page.nextOffset()).isEqualTo(15);
    }

    @Test
    void oneCikanlarSonSayfadaSonrakiKonumYok() {
        server.expect(once(), requestTo(Matchers.startsWith(API + "/v1/gifs/trending?")))
                .andExpect(queryParam("api_key", KEY))
                .andExpect(queryParam("rating", "pg-13"))
                .andRespond(withSuccess(list(11, 10, gif("last", "pg", "https://media.giphy.com/media/last/200.gif",
                        "https://media.giphy.com/media/last/100.gif", "300")), MediaType.APPLICATION_JSON));

        GifPage page = client.trending(10, 10);

        assertThat(page.items()).extracting(GifItem::id).containsExactly("last");
        assertThat(page.nextOffset()).isNull();
    }

    @Test
    void kimlikleCozulurYokVeyaDerecesiYuksekseBos() {
        server.expect(once(), requestToUriTemplate(API + "/v1/gifs/{id}?api_key={key}", "abc123", KEY))
                .andRespond(withSuccess("{\"data\":" + gif("abc123", "pg-13",
                        "https://media.giphy.com/media/abc123/200.gif", "https://media.giphy.com/media/abc123/100.gif",
                        "356") + "}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestToUriTemplate(API + "/v1/gifs/{id}?api_key={key}", "gone", KEY))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestToUriTemplate(API + "/v1/gifs/{id}?api_key={key}", "spicy", KEY))
                .andRespond(withSuccess("{\"data\":" + gif("spicy", "r", "https://media.giphy.com/media/spicy/200.gif",
                        "https://media.giphy.com/media/spicy/100.gif", "200") + "}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestToUriTemplate(API + "/v1/gifs/{id}?api_key={key}", "other", KEY))
                .andRespond(withSuccess("{\"data\":" + gif("different", "g",
                        "https://media.giphy.com/media/d/200.gif", "https://media.giphy.com/media/d/100.gif", "200")
                        + "}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestToUriTemplate(API + "/v1/gifs/{id}?api_key={key}", "empty", KEY))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        Optional<GifItem> found = client.find("abc123");

        assertThat(found).map(GifItem::url).contains("https://media.giphy.com/media/abc123/200.gif");
        assertThat(client.find("gone")).isEmpty();
        assertThat(client.find("spicy")).isEmpty();
        assertThat(client.find("other")).isEmpty();
        assertThatThrownBy(() -> client.find("empty")).isInstanceOf(GifProviderUnavailableException.class);
        // Geçersiz kimlik platforma hiç sorulmaz.
        assertThat(client.find("../v1/gifs/trending")).isEmpty();
        assertThat(client.find(null)).isEmpty();
        server.verify();
    }

    @Test
    void platformHatasindaAnahtarSizdirmadanFailClosed() {
        server.expect(once(), requestTo(Matchers.startsWith(API + "/v1/gifs/search?")))
                .andRespond(withServerError());
        server.expect(once(), requestTo(Matchers.startsWith(API + "/v1/gifs/trending?")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(once(), requestTo(Matchers.startsWith(API + "/v1/gifs/search?")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(once(), requestTo(Matchers.startsWith(API + "/v1/gifs/trending?")))
                .andRespond(withSuccess("{\"meta\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("kedi", 4, 0, "tr"))
                .isInstanceOf(GifProviderUnavailableException.class)
                .hasNoCause()
                .message().doesNotContain(KEY);
        assertThatThrownBy(() -> client.trending(4, 0)).isInstanceOf(GifProviderUnavailableException.class)
                .hasNoCause();
        assertThatThrownBy(() -> client.search("kedi", 4, 0, "tr"))
                .isInstanceOf(GifProviderUnavailableException.class);
        assertThatThrownBy(() -> client.trending(4, 0)).isInstanceOf(GifProviderUnavailableException.class);
        server.verify();
    }

    @Test
    void zamanAsimiVeOkunamayanYanitKokHataBaglamadanFailClosed() {
        server.expect(once(), requestTo(Matchers.startsWith(API + "/v1/gifs/search?")))
                .andRespond(request -> {
                    throw new SocketTimeoutException("timeout " + request.getURI());
                });
        server.expect(once(), requestTo(Matchers.startsWith(API + "/v1/gifs/trending?")))
                .andRespond(withSuccess("{bozuk", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("kedi", 4, 0, "tr"))
                .isInstanceOf(GifProviderUnavailableException.class)
                .hasNoCause()
                .hasMessage("GIF provider unreachable or timed out.");
        assertThatThrownBy(() -> client.trending(4, 0))
                .isInstanceOf(GifProviderUnavailableException.class)
                .hasNoCause()
                .hasMessage("GIF provider response could not be read.");
        server.verify();
    }

    @Test
    void anahtarYoksaPlatformaHicGidilmez() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer silent = MockRestServiceServer.bindTo(builder).build();
        GiphyGifClient disabled = new GiphyGifClient(builder, properties(" "));

        assertThatThrownBy(() -> disabled.search("kedi", 4, 0, "tr"))
                .isInstanceOf(GifProviderUnavailableException.class);
        assertThatThrownBy(() -> disabled.trending(4, 0)).isInstanceOf(GifProviderUnavailableException.class);
        assertThatThrownBy(() -> disabled.find("abc")).isInstanceOf(GifProviderUnavailableException.class);
        silent.verify();
        assertThat(properties(KEY).toString()).doesNotContain(KEY);
        assertThat(properties(null).hasApiKey()).isFalse();
    }

    @Test
    void medyaAdresiYalnizGiphyHttpsVeSorgusuz() {
        assertThat(GiphyGifClient.mediaUrl("https://media0.giphy.com/media/a/200.gif?cid=1#x"))
                .contains("https://media0.giphy.com/media/a/200.gif");
        assertThat(GiphyGifClient.mediaUrl("https://I.GIPHY.COM/a.gif")).contains("https://i.giphy.com/a.gif");
        assertThat(GiphyGifClient.mediaUrl("http://media.giphy.com/a.gif")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl("https://media.giphy.com:8443/a.gif")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl("https://user@media.giphy.com/a.gif")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl("https://media.giphy.com.evil.example/a.gif")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl("https://media12.giphy.com/a.gif")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl("https://media.giphy.com")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl("javascript:alert(1)")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl("https://media.giphy.com/a b.gif")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl("https://media.giphy.com/" + "a".repeat(600))).isEmpty();
        assertThat(GiphyGifClient.mediaUrl(" ")).isEmpty();
        assertThat(GiphyGifClient.mediaUrl(null)).isEmpty();
    }
}
