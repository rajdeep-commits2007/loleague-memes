package com.loleague.meme.comment;

import com.loleague.common.security.CurrentUser;
import com.loleague.common.web.ApiException;
import com.loleague.meme.author.AuthorRepository;
import com.loleague.meme.post.Cursor;
import com.loleague.meme.post.PostDtos.AuthorView;
import com.loleague.meme.post.PostDtos.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CommentService {

    public record CreateCommentRequest(
            @NotBlank(message = "Say something!")
            @Size(max = 500, message = "Comments can be at most 500 characters") String body) {
    }

    public record CommentView(UUID id, UUID postId, String body, boolean mine, OffsetDateTime createdAt,
                              AuthorView author) {
    }

    public record CommentRow(UUID id, UUID postId, String body, OffsetDateTime createdAt, UUID authorId,
                             String username, String displayName, String avatarUrl) {
        public CommentView toView(UUID me) {
            return new CommentView(id, postId, body, authorId.equals(me), createdAt,
                    new AuthorView(authorId, username, displayName, avatarUrl));
        }
    }

    public record Owner(UUID postId, UUID authorId, UUID postAuthorId) {
    }

    private static final String SELECT = """
            SELECT c.id, c.post_id, c.body, c.created_at,
                   a.id AS author_id, a.username, a.display_name, a.avatar_url
              FROM comments c JOIN authors a ON a.id = c.author_id
            """;

    private final JdbcClient jdbc;
    private final AuthorRepository authors;

    public CommentService(JdbcClient jdbc, AuthorRepository authors) {
        this.jdbc = jdbc;
        this.authors = authors;
    }

    @Transactional(readOnly = true)
    public Page<CommentView> list(CurrentUser me, UUID postId, String cursorValue, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        Cursor cursor = Cursor.decode(cursorValue);
        String where = cursor == null
                ? " WHERE c.post_id = :post AND c.college_id = :college "
                : " WHERE c.post_id = :post AND c.college_id = :college AND (c.created_at, c.id) < (:cAt, :cId) ";
        var spec = jdbc.sql(SELECT + where + " ORDER BY c.created_at DESC, c.id DESC LIMIT :limit")
                .param("post", postId)
                .param("college", me.collegeId())
                .param("limit", size);
        if (cursor != null) {
            spec = spec.param("cAt", cursor.createdAt()).param("cId", cursor.id());
        }
        List<CommentRow> rows = spec.query(CommentRow.class).list();
        String next = rows.size() == size
                ? new Cursor(rows.getLast().createdAt(), rows.getLast().id()).encode()
                : null;
        return new Page<>(rows.stream().map(r -> r.toView(me.id())).toList(), next);
    }

    @Transactional
    public CommentView create(CurrentUser me, UUID postId, CreateCommentRequest req) {
        authors.ensure(me);
        UUID id = UUID.randomUUID();
        int inserted = jdbc.sql("""
                        INSERT INTO comments (id, post_id, author_id, college_id, post_author_id, body)
                        SELECT :id, p.id, :author, p.college_id, p.author_id, :body
                          FROM posts p WHERE p.id = :post AND p.college_id = :college
                        """)
                .param("id", id)
                .param("author", me.id())
                .param("body", req.body().trim())
                .param("post", postId)
                .param("college", me.collegeId())
                .update();
        if (inserted == 0) {
            throw ApiException.notFound("Meme");
        }
        jdbc.sql("UPDATE posts SET comment_count = comment_count + 1 WHERE id = :id").param("id", postId).update();
        return jdbc.sql(SELECT + " WHERE c.id = :id").param("id", id)
                .query(CommentRow.class).single().toView(me.id());
    }

    @Transactional
    public void delete(CurrentUser me, UUID commentId) {
        Owner owner = jdbc.sql("""
                        SELECT post_id, author_id, post_author_id FROM comments
                         WHERE id = :id AND college_id = :college
                        """)
                .param("id", commentId)
                .param("college", me.collegeId())
                .query(Owner.class).optional()
                .orElseThrow(() -> ApiException.notFound("Comment"));
        // Comment author, the meme's creator, or an admin can remove a comment.
        if (!owner.authorId().equals(me.id()) && !owner.postAuthorId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("You can't delete this comment");
        }
        jdbc.sql("DELETE FROM comments WHERE id = :id").param("id", commentId).update();
        jdbc.sql("UPDATE posts SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = :id")
                .param("id", owner.postId()).update();
    }
}
