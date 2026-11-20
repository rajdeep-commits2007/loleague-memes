package com.loleague.meme.wallet;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class WalletRepository {

    public record WalletRow(long balance, long lifetimeEarned) {
    }

    public record TransactionRow(UUID id, int amount, String kind, String note, OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;

    public WalletRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public WalletRow get(UUID userId) {
        return jdbc.sql("SELECT balance, lifetime_earned FROM wallets WHERE user_id = :u")
                .param("u", userId)
                .query(WalletRow.class).optional()
                .orElse(new WalletRow(0, 0));
    }

    /** Adds coins (positive amount). Must run inside a transaction together with the ledger insert. */
    public void credit(UUID userId, int amount, String kind, String note, UUID refId) {
        jdbc.sql("""
                        INSERT INTO wallets (user_id, balance, lifetime_earned)
                        VALUES (:u, :amt, CASE WHEN :earned THEN :amt ELSE 0 END)
                        ON CONFLICT (user_id) DO UPDATE
                           SET balance = wallets.balance + :amt,
                               lifetime_earned = wallets.lifetime_earned + CASE WHEN :earned THEN :amt ELSE 0 END,
                               updated_at = now()
                        """)
                .param("u", userId)
                .param("amt", amount)
                .param("earned", !"GOODIE_REFUND".equals(kind))
                .update();
        log(userId, amount, kind, note, refId);
    }

    /** Atomically spends coins. Returns false if the balance is too low. */
    public boolean debit(UUID userId, int amount, String kind, String note, UUID refId) {
        if (amount == 0) {
            log(userId, 0, kind, note, refId);
            return true;
        }
        int updated = jdbc.sql("""
                        UPDATE wallets SET balance = balance - :amt, updated_at = now()
                         WHERE user_id = :u AND balance >= :amt
                        """)
                .param("u", userId)
                .param("amt", amount)
                .update();
        if (updated == 1) {
            log(userId, -amount, kind, note, refId);
        }
        return updated == 1;
    }

    public List<TransactionRow> recent(UUID userId, int limit) {
        return jdbc.sql("""
                        SELECT id, amount, kind, note, created_at FROM coin_transactions
                         WHERE user_id = :u ORDER BY created_at DESC LIMIT :limit
                        """)
                .param("u", userId)
                .param("limit", limit)
                .query(TransactionRow.class).list();
    }

    /** Lifetime engagement others gave this creator (self-interactions excluded). */
    public long commentsReceived(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM comments WHERE post_author_id = :u AND author_id <> :u")
                .param("u", userId).query(Long.class).single();
    }

    public long likesReceived(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM post_likes WHERE post_author_id = :u AND user_id <> :u")
                .param("u", userId).query(Long.class).single();
    }

    private void log(UUID userId, int amount, String kind, String note, UUID refId) {
        jdbc.sql("""
                        INSERT INTO coin_transactions (id, user_id, amount, kind, note, ref_id)
                        VALUES (:id, :u, :amt, :kind, :note, :ref)
                        """)
                .param("id", UUID.randomUUID())
                .param("u", userId)
                .param("amt", amount)
                .param("kind", kind)
                .param("note", note)
                .param("ref", refId)
                .update();
    }
}
