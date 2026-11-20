package com.loleague.auth.sync;

import com.loleague.auth.config.AuthProperties;
import com.loleague.auth.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.loleague.common.web.ApiException;
import org.springframework.http.HttpStatus;
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
    private final RestClient chat;
    private final String apiKey;

    public AuthorSyncClient(AuthProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder()
                .baseUrl(withScheme(props.internal().memeServiceUrl()))
                .requestFactory(factory)
                .build();
        this.chat = RestClient.builder()
                .baseUrl(withScheme(props.internal().chatServiceUrl()))
                .requestFactory(factory)
                .build();
        this.apiKey = props.internal().apiKey();
    }

    /** Render's fromService "hostport" has no scheme; accept both forms. */
    private static String withScheme(String url) {
        return url.startsWith("http://") || url.startsWith("https://") ? url : "http://" + url;
    }

    /** Best effort, in the background: display name / avatar changes. */
    @Async
    public void push(User user) {
        try {
            pushNow(user);
        } catch (Exception e) {
            log.warn("Author sync to meme-service failed for {}: {}", user.getUsername(), e.getMessage());
        }
    }

    /** Synchronous and throwing, for username changes (meme-service also keeps usernames unique). */
    public void pushNow(User user) {
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
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't update your username right now. Try again.");
        }
    }

    /** Chat stores the sender's username on each message; keep old messages pointing at the new name. */
    @Async
    public void renameInChat(User user) {
        try {
            chat.put()
                    .uri("/internal/users/{id}/username", user.getId())
                    .header("X-Internal-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", user.getUsername()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Username sync to chat-service failed for {}: {}", user.getUsername(), e.getMessage());
        }
    }
}
