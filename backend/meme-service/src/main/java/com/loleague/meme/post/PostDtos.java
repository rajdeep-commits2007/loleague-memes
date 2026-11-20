package com.loleague.meme.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PostDtos {

    private PostDtos() {
    }

    public record AuthorView(UUID id, String username, String displayName, String avatarUrl) {
    }

    public record PostView(UUID id, String caption, String imageUrl, int likeCount, int commentCount,
                           boolean likedByMe, boolean mine, OffsetDateTime createdAt, AuthorView author) {
    }

    public record Page<T>(List<T> items, String nextCursor) {
    }

    public record CreatePostRequest(
            @Size(max = 500, message = "Caption can be at most 500 characters") String caption,
            @NotBlank(message = "Upload a meme image first") String imageKey) {
    }

    public record LikeResult(boolean liked, int likeCount) {
    }

    /** Flat row straight from SQL. */
    public record PostRow(UUID id, String caption, String imageUrl, String imageKey, int likeCount, int commentCount,
                   boolean likedByMe, OffsetDateTime createdAt, UUID authorId, String username,
                   String displayName, String avatarUrl) {

        public PostView toView(UUID me) {
            return new PostView(id, caption, imageUrl, likeCount, commentCount, likedByMe, authorId.equals(me),
                    createdAt, new AuthorView(authorId, username, displayName, avatarUrl));
        }
    }
}
