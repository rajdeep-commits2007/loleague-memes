package com.loleague.chat.gif;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.loleague.chat.config.ChatProperties;
import com.loleague.common.security.CurrentUser;
import com.loleague.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proxies GIPHY search so the API key stays on the server. Results are cached for 10 minutes.
 */
@RestController
public class GifController {

    public record Gif(String id, String title, String url, String previewUrl, int width, int height) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GiphyResponse(List<GiphyGif> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GiphyGif(String id, String title, GiphyImages images) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GiphyImages(@JsonProperty("fixed_height") Rendition fixedHeight,
                              @JsonProperty("fixed_width_small") Rendition fixedWidthSmall) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rendition(String url, String width, String height) {
    }

    private record Cached(List<Gif> gifs, Instant at) {
    }

    private final RestClient client;
    private final ChatProperties.Giphy giphy;
    private final JsonMapper json;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public GifController(ChatProperties props, JsonMapper json) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl("https://api.giphy.com/v1/gifs").requestFactory(factory).build();
        this.giphy = props.giphy();
        this.json = json;
    }

    @GetMapping("/api/chat/gifs")
    public List<Gif> search(CurrentUser me, @RequestParam(required = false) String q) {
        if (giphy.apiKey() == null || giphy.apiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GIFs are not configured (set GIPHY_API_KEY)");
        }
        String query = q == null ? "" : q.trim().toLowerCase();
        if (query.length() > 50) {
            query = query.substring(0, 50);
        }
        String key = query.isEmpty() ? "__trending__" : query;
        Cached cached = cache.get(key);
        if (cached != null && cached.at().plusSeconds(600).isAfter(Instant.now())) {
            return cached.gifs();
        }
        if (cache.size() > 500) {
            cache.clear();
        }
        final String finalQuery = query;
        String body = query.isEmpty()
                ? client.get().uri(u -> u.path("/trending")
                        .queryParam("api_key", giphy.apiKey())
                        .queryParam("limit", 24)
                        .queryParam("rating", giphy.rating()).build())
                .retrieve().body(String.class)
                : client.get().uri(u -> u.path("/search")
                        .queryParam("api_key", giphy.apiKey())
                        .queryParam("q", finalQuery)
                        .queryParam("limit", 24)
                        .queryParam("rating", giphy.rating())
                        .queryParam("lang", "en").build())
                .retrieve().body(String.class);

        GiphyResponse parsed = json.readValue(body, GiphyResponse.class);
        List<Gif> gifs = parsed.data() == null ? List.of() : parsed.data().stream()
                .filter(g -> g.images() != null && g.images().fixedHeight() != null)
                .map(g -> new Gif(g.id(), g.title(),
                        stripQuery(g.images().fixedHeight().url()),
                        stripQuery(g.images().fixedWidthSmall() != null
                                ? g.images().fixedWidthSmall().url()
                                : g.images().fixedHeight().url()),
                        parse(g.images().fixedHeight().width()),
                        parse(g.images().fixedHeight().height())))
                .filter(g -> Objects.nonNull(g.url()))
                .toList();
        cache.put(key, new Cached(gifs, Instant.now()));
        return gifs;
    }

    private static String stripQuery(String url) {
        if (url == null) {
            return null;
        }
        int i = url.indexOf('?');
        return i < 0 ? url : url.substring(0, i);
    }

    private static int parse(String v) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return 200;
        }
    }
}
