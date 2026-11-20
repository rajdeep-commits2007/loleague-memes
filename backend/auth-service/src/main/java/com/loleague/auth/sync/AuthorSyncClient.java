package com.loleague.auth.sync;

import com.loleague.auth.config.AuthProperties;
import com.loleague.auth.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Pushes the public bits of a profile (display name, avatar) to the meme-service so posts, comments and
 * the leaderboard can render them without a cross-service call per row.
 * Best effort: the meme-service also creates authors lazily from the JWT, so a missed sync only means
 * a stale avatar until the next profile save.
 */
@Component
public class AuthorSyncClient {

    private static final Logger log = LoggerFactory.getLogger(AuthorSyncClient.class);

    private final RestClient client;
    private final String apiKey;

    public AuthorSyncClient(AuthProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder()
                .baseUrl(withScheme(props.internal().memeServiceUrl()))
                .requestFactory(factory)
                .build();
        this.apiKey = props.internal().apiKey();
    }

    /** Render's fromService "hostport" has no scheme; accept both forms. */
    private static String withScheme(String url) {
        return url.startsWith("http://") || url.startsWith("https://") ? url : "http://" + url;
    }

    @Async
    public void push(User user) {
        try {
            client.put()
                    .uri("/internal/authors/{id}", user.getId())
                    .header("X-Internal-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "username", user.getUsername(),
                            "collegeId", user.getCollegeId(),
                            "displayName", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(),
                            "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Author sync to meme-service failed for {}: {}", user.getUsername(), e.getMessage());
        }
    }
}
