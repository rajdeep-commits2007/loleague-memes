package com.loleague.meme.goodie;

import com.loleague.common.security.CurrentUser;
import com.loleague.common.web.ApiException;
import com.loleague.meme.author.AuthorRepository;
import com.loleague.meme.wallet.WalletRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Coins -> real goodies, delivered to the address the student enters.
 * A goodie can require coins, a lifetime "comments received" milestone (e.g. 20,000), or both.
 */
@Service
public class GoodieService {

    public record Goodie(UUID id, String name, String description, String imageUrl, int coinCost,
                         int minCommentsReceived, Integer stock, boolean oncePerUser, boolean active) {
    }

    public record GoodieView(UUID id, String name, String description, String imageUrl, int coinCost,
                             int minCommentsReceived, Integer stock, boolean oncePerUser,
                             boolean canClaim, String blockedReason) {
    }

    public record ClaimRequest(
            @NotBlank(message = "Full name is required") @Size(max = 80) String fullName,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String phone,
            @NotBlank(message = "Address is required") @Size(max = 160) String addressLine1,
            @Size(max = 160) String addressLine2,
            @NotBlank(message = "City is required") @Size(max = 60) String city,
            @NotBlank(message = "State is required") @Size(max = 60) String state,
            @NotBlank @Pattern(regexp = "^[1-9]\\d{5}$", message = "Enter a valid 6-digit PIN code") String pincode) {
    }

    public record ClaimView(UUID id, UUID goodieId, String goodieName, int coinsSpent, String status,
                            String fullName, String city, String pincode, String adminNote, OffsetDateTime createdAt) {
    }

    public static final Set<String> STATUSES = Set.of("PENDING", "SHIPPED", "DELIVERED", "CANCELLED");

    private static final String SELECT_GOODIE = """
            SELECT id, name, description, image_url, coin_cost, min_comments_received, stock, once_per_user, active
              FROM goodies
            """;

    private final JdbcClient jdbc;
    private final WalletRepository wallets;
    private final AuthorRepository authors;

    public GoodieService(JdbcClient jdbc, WalletRepository wallets, AuthorRepository authors) {
        this.jdbc = jdbc;
        this.wallets = wallets;
        this.authors = authors;
    }

    @Transactional(readOnly = true)
    public List<GoodieView> catalogue(CurrentUser me) {
        long balance = wallets.get(me.id()).balance();
        long comments = wallets.commentsReceived(me.id());
        List<UUID> alreadyClaimed = jdbc.sql("""
                        SELECT goodie_id FROM goodie_claims WHERE user_id = :u AND status <> 'CANCELLED'
                        """)
                .param("u", me.id()).query(UUID.class).list();
        return jdbc.sql(SELECT_GOODIE + " WHERE active ORDER BY sort_order, coin_cost")
                .query(Goodie.class).list().stream()
                .map(g -> {
                    String reason = blockedReason(g, balance, comments, alreadyClaimed.contains(g.id()));
                    return new GoodieView(g.id(), g.name(), g.description(), g.imageUrl(), g.coinCost(),
                            g.minCommentsReceived(), g.stock(), g.oncePerUser(), reason == null, reason);
                }).toList();
    }

    @Transactional
    public ClaimView claim(CurrentUser me, UUID goodieId, ClaimRequest req) {
        authors.ensure(me);
        Goodie g = jdbc.sql(SELECT_GOODIE + " WHERE id = :id FOR UPDATE")
                .param("id", goodieId).query(Goodie.class).optional()
                .filter(Goodie::active)
                .orElseThrow(() -> ApiException.notFound("Goodie"));

        boolean claimedBefore = jdbc.sql("""
                        SELECT count(*) > 0 FROM goodie_claims
                         WHERE user_id = :u AND goodie_id = :g AND status <> 'CANCELLED'
                        """)
                .param("u", me.id()).param("g", goodieId).query(Boolean.class).single();
        String reason = blockedReason(g, Long.MAX_VALUE, wallets.commentsReceived(me.id()), claimedBefore);
        if (reason != null) {
            throw ApiException.badRequest(reason);
        }

        if (g.stock() != null) {
            int updated = jdbc.sql("UPDATE goodies SET stock = stock - 1 WHERE id = :id AND stock > 0")
                    .param("id", goodieId).update();
            if (updated == 0) {
                throw ApiException.conflict("Out of stock. Check back soon!");
            }
        }

        UUID claimId = UUID.randomUUID();
        if (!wallets.debit(me.id(), g.coinCost(), "GOODIE_CLAIM", "Claimed " + g.name(), claimId)) {
            // Throwing rolls back the stock decrement too.
            throw ApiException.badRequest("Not enough coins yet. Keep meme-ing!");
        }

        jdbc.sql("""
                        INSERT INTO goodie_claims (id, user_id, goodie_id, coins_spent, full_name, phone,
                                                   address_line1, address_line2, city, state, pincode)
                        VALUES (:id, :u, :g, :coins, :name, :phone, :a1, :a2, :city, :state, :pin)
                        """)
                .param("id", claimId)
                .param("u", me.id())
                .param("g", goodieId)
                .param("coins", g.coinCost())
                .param("name", req.fullName().trim())
                .param("phone", req.phone())
                .param("a1", req.addressLine1().trim())
                .param("a2", req.addressLine2() == null ? null : req.addressLine2().trim())
                .param("city", req.city().trim())
                .param("state", req.state().trim())
                .param("pin", req.pincode())
                .update();
        return claims(me.id()).stream().filter(c -> c.id().equals(claimId)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ClaimView> claims(UUID userId) {
        return jdbc.sql("""
                        SELECT c.id, c.goodie_id, g.name AS goodie_name, c.coins_spent, c.status, c.full_name,
                               c.city, c.pincode, c.admin_note, c.created_at
                          FROM goodie_claims c JOIN goodies g ON g.id = c.goodie_id
                         WHERE c.user_id = :u ORDER BY c.created_at DESC
                        """)
                .param("u", userId).query(ClaimView.class).list();
    }

    /** Admin: move a claim through PENDING -> SHIPPED -> DELIVERED, or CANCEL it (refunds coins + stock). */
    @Transactional
    public void updateStatus(UUID claimId, String status, String note) {
        if (!STATUSES.contains(status)) {
            throw ApiException.badRequest("Unknown status");
        }
        record Current(UUID userId, UUID goodieId, int coinsSpent, String status) {
        }
        Current c = jdbc.sql("SELECT user_id, goodie_id, coins_spent, status FROM goodie_claims WHERE id = :id FOR UPDATE")
                .param("id", claimId)
                .query((rs, i) -> new Current(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4)))
                .optional().orElseThrow(() -> ApiException.notFound("Claim"));
        if ("CANCELLED".equals(c.status())) {
            throw ApiException.badRequest("Claim already cancelled");
        }
        if ("CANCELLED".equals(status)) {
            if (c.coinsSpent() > 0) {
                wallets.credit(c.userId(), c.coinsSpent(), "GOODIE_REFUND", "Refund for cancelled claim", claimId);
            }
            jdbc.sql("UPDATE goodies SET stock = stock + 1 WHERE id = :id AND stock IS NOT NULL")
                    .param("id", c.goodieId()).update();
        }
        jdbc.sql("UPDATE goodie_claims SET status = :s, admin_note = :n, updated_at = now() WHERE id = :id")
                .param("s", status).param("n", note).param("id", claimId).update();
    }

    private static String blockedReason(Goodie g, long balance, long commentsReceived, boolean claimedBefore) {
        if (g.oncePerUser() && claimedBefore) {
            return "Already claimed";
        }
        if (g.stock() != null && g.stock() <= 0) {
            return "Out of stock";
        }
        if (commentsReceived < g.minCommentsReceived()) {
            return "Unlocks at " + g.minCommentsReceived() + " comments on your memes";
        }
        if (balance < g.coinCost()) {
            return "Need " + (g.coinCost() - balance) + " more coins";
        }
        return null;
    }
}
