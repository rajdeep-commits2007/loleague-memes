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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comments are threaded one level deep: a reply to a reply is attached to the top-level comment
 * (and usually starts with an @mention of the person being answered), like Instagram / YouTube.
 */
@Service
public class CommentService {

    /** Reactions a comment can receive. Kept small so the UI stays a single row. */
    public static final List<String> REACTIONS = List.of("👍", "😂", "❤️", "🔥", "😮", "😢");

    private static final Pattern MENTION = Pattern.compile("(?<![A-Za-z0-9_.])@([A-Za-z0-9][A-Za-z0-9._]{1,18}[A-Za-z0-9])");
    private static final int MAX_MENTIONS = 10;

    public record CreateCommentRequest(
            @NotBlank(message = "Say something!")
            @Size(max = 500, message = "Comments can be at most 500 characters") String body,
            UUID parentId) {
    }

    public record EditCommentRequest(
            @NotBlank(message = "Say something!")
            @Size(max = 500, message = "Comments can be at most 500 characters") String body) {
    }

    public record ReactRequest(@NotBlank String emoji) {
    }

    public record ReactionView(String emoji, int count, boolean mine) {
    }

    public record CommentView(UUID id, UUID postId, UUID parentId, String body, boolean mine,
                              OffsetDateTime createdAt, OffsetDateTime editedAt, int replyCount,
                              List<ReactionView> reactions, List<String> mentions, AuthorView author) {
    }

    public record MentionCandidate(UUID id, String username, String displayName, String avatarUrl) {
    }

    public record CommentRow(UUID id, UUID postId, UUID parentId, String body, OffsetDateTime createdAt,
                             OffsetDateTime editedAt, int replyCount, UUID authorId,
                             String username, String displayName, String avatarUrl) {
    }

    private record ReactionRow(UUID commentId, String emoji, int count, boolean mine) {
    }

    private record MentionRow(UUID commentId, String username) {
    }

    public record Owner(UUID postId, UUID parentId, UUID authorId, UUID postAuthorId, int replyCount) {
    }

    private static final String SELECT = """
            SELECT c.id, c.post_id, c.parent_id, c.body, c.created_at, c.edited_at, c.reply_count,
                   a.id AS author_id, a.username, a.display_name, a.avatar_url
              FROM comments c JOIN authors a ON a.id = c.author_id
            """;

    private final JdbcClient jdbc;
    private final AuthorRepository authors;

    public CommentService(JdbcClient jdbc, AuthorRepository authors) {
        this.jdbc = jdbc;
        this.authors = authors;
    }

    // ------------------------------------------------------------------ reads

    /** Top-level comments of a post, newest first. */
    @Transactional(readOnly = true)
    public Page<CommentView> list(CurrentUser me, UUID postId, String cursorValue, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        Cursor cursor = Cursor.decode(cursorValue);
        String where = " WHERE c.post_id = :post AND c.college_id = :college AND c.parent_id IS NULL "
                + (cursor == null ? "" : " AND (c.created_at, c.id) < (:cAt, :cId) ");
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
        return new Page<>(toViews(rows, me.id()), next);
    }

    /** Replies of a top-level comment, oldest first so a conversation reads top to bottom. */
    @Transactional(readOnly = true)
    public Page<CommentView> replies(CurrentUser me, UUID commentId, String cursorValue, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        Cursor cursor = Cursor.decode(cursorValue);
        String where = " WHERE c.parent_id = :parent AND c.college_id = :college "
                + (cursor == null ? "" : " AND (c.created_at, c.id) > (:cAt, :cId) ");
        var spec = jdbc.sql(SELECT + where + " ORDER BY c.created_at ASC, c.id ASC LIMIT :limit")
                .param("parent", commentId)
                .param("college", me.collegeId())
                .param("limit", size);
        if (cursor != null) {
            spec = spec.param("cAt", cursor.createdAt()).param("cId", cursor.id());
        }
        List<CommentRow> rows = spec.query(CommentRow.class).list();
        String next = rows.size() == size
                ? new Cursor(rows.getLast().createdAt(), rows.getLast().id()).encode()
                : null;
        return new Page<>(toViews(rows, me.id()), next);
    }

    /** Batchmates whose username starts with {@code q}, for the @mention picker. */
    @Transactional(readOnly = true)
    public List<MentionCandidate> mentionCandidates(CurrentUser me, String q) {
        String prefix = q == null ? "" : q.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.]", "");
        if (prefix.length() > 20) {
            prefix = prefix.substring(0, 20);
        }
        return jdbc.sql("""
                        SELECT id, username, display_name, avatar_url FROM authors
                         WHERE college_id = :college AND lower(username) LIKE :prefix AND id <> :me
                         ORDER BY lower(username) LIMIT 8
                        """)
                .param("college", me.collegeId())
                .param("prefix", prefix + "%")
                .param("me", me.id())
                .query(MentionCandidate.class).list();
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public CommentView create(CurrentUser me, UUID postId, CreateCommentRequest req) {
        authors.ensure(me);
        UUID parentId = null;
        if (req.parentId() != null) {
            // Flatten: replying to a reply attaches to that reply's top-level comment.
            parentId = jdbc.sql("""
                            SELECT COALESCE(parent_id, id) FROM comments
                             WHERE id = :id AND post_id = :post AND college_id = :college
                            """)
                    .param("id", req.parentId())
                    .param("post", postId)
                    .param("college", me.collegeId())
                    .query(UUID.class).optional()
                    .orElseThrow(() -> ApiException.notFound("Comment"));
        }

        UUID id = UUID.randomUUID();
        String body = req.body().trim();
        int inserted = jdbc.sql("""
                        INSERT INTO comments (id, post_id, parent_id, author_id, college_id, post_author_id, body)
                        SELECT :id, p.id, :parent, :author, p.college_id, p.author_id, :body
                          FROM posts p WHERE p.id = :post AND p.college_id = :college
                        """)
                .param("id", id)
                .param("parent", parentId)
                .param("author", me.id())
                .param("body", body)
                .param("post", postId)
                .param("college", me.collegeId())
                .update();
        if (inserted == 0) {
            throw ApiException.notFound("Meme");
        }
        jdbc.sql("UPDATE posts SET comment_count = comment_count + 1 WHERE id = :id").param("id", postId).update();
        if (parentId != null) {
            jdbc.sql("UPDATE comments SET reply_count = reply_count + 1 WHERE id = :id").param("id", parentId).update();
        }
        saveMentions(me, id, body);
        return load(id, me.id());
    }

    @Transactional
    public CommentView edit(CurrentUser me, UUID commentId, EditCommentRequest req) {
        Owner owner = owner(me, commentId);
        if (!owner.authorId().equals(me.id())) {
            throw ApiException.forbidden("You can only edit your own comments");
        }
        String body = req.body().trim();
        jdbc.sql("UPDATE comments SET body = :body, edited_at = now() WHERE id = :id AND body <> :body")
                .param("body", body)
                .param("id", commentId)
                .update();
        jdbc.sql("DELETE FROM comment_mentions WHERE comment_id = :id").param("id", commentId).update();
        saveMentions(me, commentId, body);
        return load(commentId, me.id());
    }

    /** Toggles one reaction of the current user on a comment. */
    @Transactional
    public CommentView react(CurrentUser me, UUID commentId, ReactRequest req) {
        String emoji = req.emoji().strip();
        if (!REACTIONS.contains(emoji)) {
            throw ApiException.badRequest("Unsupported reaction");
        }
        owner(me, commentId); // 404 for other colleges
        authors.ensure(me);
        int removed = jdbc.sql("DELETE FROM comment_reactions WHERE comment_id = :c AND user_id = :u AND emoji = :e")
                .param("c", commentId).param("u", me.id()).param("e", emoji)
                .update();
        if (removed == 0) {
            jdbc.sql("""
                            INSERT INTO comment_reactions (comment_id, user_id, emoji) VALUES (:c, :u, :e)
                            ON CONFLICT DO NOTHING
                            """)
                    .param("c", commentId).param("u", me.id()).param("e", emoji)
                    .update();
        }
        return load(commentId, me.id());
    }

    @Transactional
    public void delete(CurrentUser me, UUID commentId) {
        Owner owner = owner(me, commentId);
        // Comment author, the meme's creator, or an admin can remove a comment.
        if (!owner.authorId().equals(me.id()) && !owner.postAuthorId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("You can't delete this comment");
        }
        // Deleting a top-level comment cascades to its replies.
        int removed = 1 + (owner.parentId() == null ? owner.replyCount() : 0);
        jdbc.sql("DELETE FROM comments WHERE id = :id").param("id", commentId).update();
        jdbc.sql("UPDATE posts SET comment_count = GREATEST(comment_count - :n, 0) WHERE id = :id")
                .param("n", removed)
                .param("id", owner.postId()).update();
        if (owner.parentId() != null) {
            jdbc.sql("UPDATE comments SET reply_count = GREATEST(reply_count - 1, 0) WHERE id = :id")
                    .param("id", owner.parentId()).update();
        }
    }

    // ------------------------------------------------------------------ helpers

    private Owner owner(CurrentUser me, UUID commentId) {
        return jdbc.sql("""
                        SELECT post_id, parent_id, author_id, post_author_id, reply_count FROM comments
                         WHERE id = :id AND college_id = :college
                        """)
                .param("id", commentId)
                .param("college", me.collegeId())
                .query(Owner.class).optional()
                .orElseThrow(() -> ApiException.notFound("Comment"));
    }

    private CommentView load(UUID id, UUID me) {
        CommentRow row = jdbc.sql(SELECT + " WHERE c.id = :id").param("id", id).query(CommentRow.class).single();
        return toViews(List.of(row), me).getFirst();
    }

    /** Only batchmates who exist are stored as mentions; anything else stays plain text. */
    private void saveMentions(CurrentUser me, UUID commentId, String body) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = MENTION.matcher(body);
        while (m.find() && names.size() < MAX_MENTIONS) {
            names.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        if (names.isEmpty()) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO comment_mentions (comment_id, user_id)
                        SELECT :comment, a.id FROM authors a
                         WHERE a.college_id = :college AND lower(a.username) IN (:names)
                        ON CONFLICT DO NOTHING
                        """)
                .param("comment", commentId)
                .param("college", me.collegeId())
                .param("names", names)
                .update();
    }

    private List<CommentView> toViews(List<CommentRow> rows, UUID me) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(CommentRow::id).toList();

        Map<UUID, List<ReactionView>> reactions = jdbc.sql("""
                        SELECT comment_id, emoji, count(*)::int AS count, bool_or(user_id = :me) AS mine
                          FROM comment_reactions WHERE comment_id IN (:ids)
                         GROUP BY comment_id, emoji
                        """)
                .param("me", me)
                .param("ids", ids)
                .query(ReactionRow.class).list().stream()
                .collect(Collectors.groupingBy(ReactionRow::commentId,
                        Collectors.mapping(r -> new ReactionView(r.emoji(), r.count(), r.mine()), Collectors.toList())));
        reactions.values().forEach(list -> list.sort(Comparator.comparingInt(r -> REACTIONS.indexOf(r.emoji()))));

        Map<UUID, List<String>> mentions = jdbc.sql("""
                        SELECT m.comment_id, a.username FROM comment_mentions m JOIN authors a ON a.id = m.user_id
                         WHERE m.comment_id IN (:ids)
                        """)
                .param("ids", ids)
                .query(MentionRow.class).list().stream()
                .collect(Collectors.groupingBy(MentionRow::commentId,
                        Collectors.mapping(MentionRow::username, Collectors.toList())));

        return rows.stream().map(r -> new CommentView(r.id(), r.postId(), r.parentId(), r.body(),
                r.authorId().equals(me), r.createdAt(), r.editedAt(), r.replyCount(),
                reactions.getOrDefault(r.id(), List.of()), mentions.getOrDefault(r.id(), List.of()),
                new AuthorView(r.authorId(), r.username(), r.displayName(), r.avatarUrl()))).toList();
    }
}
