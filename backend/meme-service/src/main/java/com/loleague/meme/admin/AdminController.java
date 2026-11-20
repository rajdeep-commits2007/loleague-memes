package com.loleague.meme.admin;

import com.loleague.meme.ad.AdService;
import com.loleague.meme.goodie.GoodieService;
import com.loleague.meme.leaderboard.LeaderboardService;
import com.loleague.meme.leaderboard.Week;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ROLE_ADMIN only (set ADMIN_USERNAMES on the auth-service). Small API for ops:
 * goodie deliveries, ads and manual leaderboard payouts. Use it from curl/Postman or build an admin UI later.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record ClaimRow(UUID id, String username, String goodieName, int coinsSpent, String status,
                           String fullName, String phone, String addressLine1, String addressLine2,
                           String city, String state, String pincode, String adminNote, OffsetDateTime createdAt) {
    }

    public record StatusUpdate(String status, String note) {
    }

    private final JdbcClient jdbc;
    private final GoodieService goodies;
    private final AdService ads;
    private final LeaderboardService leaderboard;

    public AdminController(JdbcClient jdbc, GoodieService goodies, AdService ads, LeaderboardService leaderboard) {
        this.jdbc = jdbc;
        this.goodies = goodies;
        this.ads = ads;
        this.leaderboard = leaderboard;
    }

    @GetMapping("/claims")
    public List<ClaimRow> claims(@RequestParam(defaultValue = "PENDING") String status) {
        return jdbc.sql("""
                        SELECT c.id, a.username, g.name AS goodie_name, c.coins_spent, c.status, c.full_name, c.phone,
                               c.address_line1, c.address_line2, c.city, c.state, c.pincode, c.admin_note, c.created_at
                          FROM goodie_claims c
                          JOIN authors a ON a.id = c.user_id
                          JOIN goodies g ON g.id = c.goodie_id
                         WHERE c.status = :s ORDER BY c.created_at
                        """)
                .param("s", status.toUpperCase())
                .query(ClaimRow.class).list();
    }

    @PatchMapping("/claims/{id}")
    public ResponseEntity<Void> updateClaim(@PathVariable UUID id, @RequestBody StatusUpdate body) {
        goodies.updateStatus(id, body.status().toUpperCase(), body.note());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ads")
    public List<AdService.AdAdminView> listAds() {
        return ads.all();
    }

    @PostMapping("/ads")
    public Map<String, UUID> createAd(@Valid @RequestBody AdService.AdRequest req) {
        return Map.of("id", ads.save(null, req));
    }

    @PutMapping("/ads/{id}")
    public Map<String, UUID> updateAd(@PathVariable UUID id, @Valid @RequestBody AdService.AdRequest req) {
        return Map.of("id", ads.save(id, req));
    }

    /** Re-run (idempotent) payout for the week containing the given date, default = last week. */
    @PostMapping("/leaderboard/payout")
    public Map<String, Object> payout(@RequestParam(required = false) LocalDate date) {
        Week week = date == null ? Week.current().previous() : Week.containing(date);
        if (!week.start().isBefore(Week.current().start())) {
            return Map.of("paid", 0, "message", "Week not finished yet");
        }
        return Map.of("paid", leaderboard.payout(week), "weekStart", week.start().toString());
    }
}
