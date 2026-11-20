package com.loleague.meme.post;

import com.loleague.meme.post.PostDtos.PostRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostRepository {

    private static final String SELECT = """
            SELECT p.id, p.caption, p.image_url, p.image_key, p.like_count, p.comment_count,
                   (pl.user_id IS NOT NULL) AS liked_by_me, p.created_at,
                   a.id AS author_id, a.username, a.display_name, a.avatar_url
              FROM posts p
              JOIN authors a ON a.id = p.author_id
              LEFT JOIN post_likes pl ON pl.post_id = p.id AND pl.user_id = :me
            """;

    private final JdbcClient jdbc;

    public PostRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first, keyset paginated. */
    public List<PostRow> latest(String collegeId, UUID me, Cursor cursor, int limit) {
        String where = cursor == null
                ? " WHERE p.college_id = :college "
                : " WHERE p.college_id = :college AND (p.created_at, p.id) < (:cAt, :cId) ";
        var spec = jdbc.sql(SELECT + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit")
                .param("me", me)
                .param("college", collegeId)
                .param("limit", limit);
        if (cursor != null) {
            spec = spec.param("cAt", cursor.createdAt()).param("cId", cursor.id());
        }
        return spec.query(PostRow.class).list();
    }

    /** "Top this week": most engaging memes from the last 7 days. Offset paging is fine for this bounded list. */
    public List<PostRow> top(String collegeId, UUID me, int offset, int limit, int likePoints, int commentPoints) {
        return jdbc.sql(SELECT + """
                         WHERE p.college_id = :college AND p.created_at > now() - interval '7 days'
                         ORDER BY (p.like_count * :lp + p.comment_count * :cp) DESC, p.created_at DESC
                         OFFSET :offset LIMIT :limit
                        """)
                .param("me", me)
                .param("college", collegeId)
                .param("lp", likePoints)
                .param("cp", commentPoints)
                .param("offset", offset)
                .param("limit", limit)
                .query(PostRow.class).list();
    }

    public List<PostRow> byAuthor(String collegeId, UUID authorId, UUID me, Cursor cursor, int limit) {
        String where = cursor == null
                ? " WHERE p.college_id = :college AND p.author_id = :author "
                : " WHERE p.college_id = :college AND p.author_id = :author AND (p.created_at, p.id) < (:cAt, :cId) ";
        var spec = jdbc.sql(SELECT + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit")
                .param("me", me)
                .param("college", collegeId)
                .param("author", authorId)
                .param("limit", limit);
        if (cursor != null) {
            spec = spec.param("cAt", cursor.createdAt()).param("cId", cursor.id());
        }
        return spec.query(PostRow.class).list();
    }

    public Optional<PostRow> find(UUID id, String collegeId, UUID me) {
        return jdbc.sql(SELECT + " WHERE p.id = :id AND p.college_id = :college")
                .param("me", me)
                .param("id", id)
                .param("college", collegeId)
                .query(PostRow.class).optional();
    }

    public void insert(UUID id, UUID authorId, String collegeId, String caption, String imageKey, String imageUrl) {
        jdbc.sql("""
                        INSERT INTO posts (id, author_id, college_id, caption, image_key, image_url)
                        VALUES (:id, :author, :college, :caption, :key, :url)
                        """)
                .param("id", id)
                .param("author", authorId)
                .param("college", collegeId)
                .param("caption", caption)
                .param("key", imageKey)
                .param("url", imageUrl)
                .update();
    }

    public int delete(UUID id) {
        return jdbc.sql("DELETE FROM posts WHERE id = :id").param("id", id).update();
    }

    /** Returns true if this call created the like. */
    public boolean like(UUID postId, UUID userId, String collegeId) {
        int inserted = jdbc.sql("""
                        INSERT INTO post_likes (post_id, user_id, college_id, post_author_id)
                        SELECT p.id, :user, p.college_id, p.author_id FROM posts p
                         WHERE p.id = :post AND p.college_id = :college
                        ON CONFLICT DO NOTHING
                        """)
                .param("post", postId)
                .param("user", userId)
                .param("college", collegeId)
                .update();
        if (inserted == 1) {
            jdbc.sql("UPDATE posts SET like_count = like_count + 1 WHERE id = :id").param("id", postId).update();
        }
        return inserted == 1;
    }

    public boolean unlike(UUID postId, UUID userId) {
        int removed = jdbc.sql("DELETE FROM post_likes WHERE post_id = :post AND user_id = :user")
                .param("post", postId)
                .param("user", userId)
                .update();
        if (removed == 1) {
            jdbc.sql("UPDATE posts SET like_count = GREATEST(like_count - 1, 0) WHERE id = :id").param("id", postId).update();
        }
        return removed == 1;
    }

    public int likeCount(UUID postId) {
        return jdbc.sql("SELECT like_count FROM posts WHERE id = :id").param("id", postId)
                .query(Integer.class).optional().orElse(0);
    }
}
