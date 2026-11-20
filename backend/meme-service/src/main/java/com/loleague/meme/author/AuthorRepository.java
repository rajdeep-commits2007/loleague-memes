package com.loleague.meme.author;

import com.loleague.common.security.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthorRepository {

    public record AuthorRow(UUID id, String username, String collegeId, String displayName, String avatarUrl) {
    }

    private final JdbcClient jdbc;

    public AuthorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Lazily creates the author row from JWT claims the first time a student does anything here. */
    public void ensure(CurrentUser me) {
        jdbc.sql("""
                        INSERT INTO authors (id, username, college_id, display_name)
                        VALUES (:id, :username, :college, :username)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", me.id())
                .param("username", me.username())
                .param("college", me.collegeId())
                .update();
    }

    /** Called by auth-service whenever a profile changes. */
    public void upsert(UUID id, String username, String collegeId, String displayName, String avatarUrl) {
        jdbc.sql("""
                        INSERT INTO authors (id, username, college_id, display_name, avatar_url)
                        VALUES (:id, :username, :college, :displayName, :avatar)
                        ON CONFLICT (id) DO UPDATE
                           SET display_name = EXCLUDED.display_name,
                               avatar_url   = EXCLUDED.avatar_url,
                               updated_at   = now()
                        """)
                .param("id", id)
                .param("username", username)
                .param("college", collegeId)
                .param("displayName", displayName)
                .param("avatar", avatarUrl == null || avatarUrl.isBlank() ? null : avatarUrl)
                .update();
    }

    public Optional<AuthorRow> findByUsername(String username, String collegeId) {
        return jdbc.sql("""
                        SELECT id, username, college_id, display_name, avatar_url
                          FROM authors WHERE lower(username) = lower(:u) AND college_id = :c
                        """)
                .param("u", username)
                .param("c", collegeId)
                .query(AuthorRow.class)
                .optional();
    }
}
