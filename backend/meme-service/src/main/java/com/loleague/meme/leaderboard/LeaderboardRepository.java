package com.loleague.meme.leaderboard;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LeaderboardRepository {

    public record ScoreRow(long rank, UUID userId, String username, String displayName, String avatarUrl,
                           int likes, int comments, int score) {
    }

    public record PayoutRow(long rank, UUID userId, String username, String displayName, String avatarUrl,
                            int likes, int comments, int score, int coins) {
    }

    /**
     * Engagement *received* during the week, per creator. Self-likes and self-comments don't count.
     * Ranking: score (comments are worth more than likes), then comments, then likes.
     */
    private static final String RANKED = """
            WITH l AS (
                SELECT post_author_id AS uid, count(*) AS likes
                  FROM post_likes
                 WHERE college_id = :college AND created_at >= :from AND created_at < :until
                   AND user_id <> post_author_id
                 GROUP BY post_author_id
            ), c AS (
                SELECT post_author_id AS uid, count(*) AS comments
                  FROM comments
                 WHERE college_id = :college AND created_at >= :from AND created_at < :until
                   AND author_id <> post_author_id
                 GROUP BY post_author_id
            ), t AS (
                SELECT COALESCE(l.uid, c.uid) AS uid,
                       COALESCE(l.likes, 0)    AS likes,
                       COALESCE(c.comments, 0) AS comments
                  FROM l FULL OUTER JOIN c ON c.uid = l.uid
            ), ranked AS (
                SELECT a.id AS user_id, a.username, a.display_name, a.avatar_url,
                       t.likes::int AS likes, t.comments::int AS comments,
                       (t.likes * :lp + t.comments * :cp)::int AS score,
                       ROW_NUMBER() OVER (ORDER BY (t.likes * :lp + t.comments * :cp) DESC,
                                                   t.comments DESC, t.likes DESC, a.username ASC) AS rank
                  FROM t JOIN authors a ON a.id = t.uid
            )
            """;

    private final JdbcClient jdbc;

    public LeaderboardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScoreRow> top(String collegeId, Week week, int limit, int likePoints, int commentPoints) {
        return jdbc.sql(RANKED + """
                        SELECT rank, user_id, username, display_name, avatar_url, likes, comments, score
                          FROM ranked WHERE score > 0 ORDER BY rank LIMIT :limit
                        """)
                .param("college", collegeId)
                .param("from", week.from())
                .param("until", week.until())
                .param("lp", likePoints)
                .param("cp", commentPoints)
                .param("limit", limit)
                .query(ScoreRow.class).list();
    }

    public Optional<ScoreRow> standing(String collegeId, Week week, UUID userId, int likePoints, int commentPoints) {
        return jdbc.sql(RANKED + """
                        SELECT rank, user_id, username, display_name, avatar_url, likes, comments, score
                          FROM ranked WHERE user_id = :user
                        """)
                .param("college", collegeId)
                .param("from", week.from())
                .param("until", week.until())
                .param("lp", likePoints)
                .param("cp", commentPoints)
                .param("user", userId)
                .query(ScoreRow.class).optional();
    }

    /** Colleges that had any engagement during the week (only these need a payout run). */
    public List<String> activeColleges(Week week) {
        return jdbc.sql("""
                        SELECT college_id FROM post_likes WHERE created_at >= :from AND created_at < :until
                        UNION
                        SELECT college_id FROM comments WHERE created_at >= :from AND created_at < :until
                        """)
                .param("from", week.from())
                .param("until", week.until())
                .query(String.class).list();
    }

    /** Returns true when the row was inserted (i.e. this rank had not been paid yet). */
    public boolean insertPayout(String collegeId, LocalDate weekStart, ScoreRow row, int coins) {
        return jdbc.sql("""
                        INSERT INTO leaderboard_payouts (id, college_id, week_start, rank, user_id, likes, comments, score, coins)
                        VALUES (:id, :college, :week, :rank, :user, :likes, :comments, :score, :coins)
                        ON CONFLICT DO NOTHING
                        """)
                .param("id", UUID.randomUUID())
                .param("college", collegeId)
                .param("week", weekStart)
                .param("rank", (int) row.rank())
                .param("user", row.userId())
                .param("likes", row.likes())
                .param("comments", row.comments())
                .param("score", row.score())
                .param("coins", coins)
                .update() == 1;
    }

    public List<PayoutRow> payouts(String collegeId, LocalDate weekStart) {
        return jdbc.sql("""
                        SELECT p.rank, p.user_id, a.username, a.display_name, a.avatar_url,
                               p.likes, p.comments, p.score, p.coins
                          FROM leaderboard_payouts p JOIN authors a ON a.id = p.user_id
                         WHERE p.college_id = :college AND p.week_start = :week
                         ORDER BY p.rank
                        """)
                .param("college", collegeId)
                .param("week", weekStart)
                .query(PayoutRow.class).list();
    }
}
