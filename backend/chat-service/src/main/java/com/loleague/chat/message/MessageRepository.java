package com.loleague.chat.message;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class MessageRepository {

    public record ChatMessage(UUID id, String collegeId, UUID userId, String username, String kind, String content,
                              Integer gifWidth, Integer gifHeight, OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;

    public MessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ChatMessage insert(UUID userId, String username, String collegeId, String kind, String content,
                              Integer gifWidth, Integer gifHeight) {
        return jdbc.sql("""
                        INSERT INTO messages (id, college_id, user_id, username, kind, content, gif_width, gif_height)
                        VALUES (:id, :college, :user, :username, :kind, :content, :w, :h)
                        RETURNING id, college_id, user_id, username, kind, content, gif_width, gif_height, created_at
                        """)
                .param("id", UUID.randomUUID())
                .param("college", collegeId)
                .param("user", userId)
                .param("username", username)
                .param("kind", kind)
                .param("content", content)
                .param("w", gifWidth)
                .param("h", gifHeight)
                .query(ChatMessage.class).single();
    }

    /** Newest first, older than the given message (keyset). The client reverses for display. */
    public List<ChatMessage> history(String collegeId, OffsetDateTime beforeAt, UUID beforeId, int limit) {
        String where = beforeAt == null
                ? " WHERE college_id = :college "
                : " WHERE college_id = :college AND (created_at, id) < (:at, :bid) ";
        var spec = jdbc.sql("""
                        SELECT id, college_id, user_id, username, kind, content, gif_width, gif_height, created_at
                          FROM messages
                        """ + where + " ORDER BY created_at DESC, id DESC LIMIT :limit")
                .param("college", collegeId)
                .param("limit", limit);
        if (beforeAt != null) {
            spec = spec.param("at", beforeAt).param("bid", beforeId);
        }
        return spec.query(ChatMessage.class).list();
    }

    public OffsetDateTime createdAt(UUID id, String collegeId) {
        return jdbc.sql("SELECT created_at FROM messages WHERE id = :id AND college_id = :college")
                .param("id", id).param("college", collegeId)
                .query(OffsetDateTime.class).optional().orElse(null);
    }
}
