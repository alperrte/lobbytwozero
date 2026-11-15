package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import com.ltz.recommendation.infrastructure.security.RecommendationAuthProperties;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RecommendationHttpClient {
    private final RestClient client;
    private final ObjectMapper mapper;

    public RecommendationHttpClient(RecommendationAuthProperties properties, ObjectMapper mapper) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(properties.readTimeout());
        client = RestClient.builder().requestFactory(factory).build();
        this.mapper = mapper;
    }

    public JsonNode get(URI uri, String bearer) { return request(uri, bearer, null, false); }
    public JsonNode post(URI uri, Object body) { return request(uri, null, body, false); }
    public JsonNode getResource(URI uri, String bearer) { return request(uri, bearer, null, true); }
    public JsonNode postResource(URI uri, Object body, String bearer) { return request(uri, bearer, body, true); }

    private JsonNode request(URI uri, String bearer, Object body, boolean resourceLookup) {
        try {
            RestClient.RequestBodySpec request = client.method(body == null ? org.springframework.http.HttpMethod.GET
                    : org.springframework.http.HttpMethod.POST).uri(uri).accept(MediaType.APPLICATION_JSON);
            if (bearer != null) request.header(HttpHeaders.AUTHORIZATION, bearer);
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
            return request.exchange((sent, response) -> {
                int status = response.getStatusCode().value();
                if (status == 404 && resourceLookup) throw new RecommendationRequestException(404, "SOURCE_NOT_FOUND");
                if (status == 403) throw new RecommendationRequestException(403, "SOURCE_ACCESS_DENIED");
                if (status != 200 || response.getHeaders().getContentType() == null
                        || !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())) throw unavailable();
                byte[] bytes = response.getBody().readNBytes(262145);
                if (bytes.length > 262144) throw unavailable();
                JsonNode reply = mapper.readTree(bytes);
                if (reply == null || !reply.isObject()) throw unavailable();
                return reply;
            });
        } catch (RestClientException | tools.jackson.core.JacksonException _) {
            throw unavailable();
        }
    }

    public static URI origin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw unavailable();
            return uri;
        } catch (IllegalArgumentException _) { throw unavailable(); }
    }

    static RecommendationRequestException unavailable() { return new RecommendationRequestException(503, "SOURCE_UNAVAILABLE"); }
}
