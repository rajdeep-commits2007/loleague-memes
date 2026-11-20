package com.loleague.meme.ad;

import com.loleague.common.security.CurrentUser;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * In-feed sponsored cards. The client inserts one after every N memes (Instagram style) and
 * reports impressions / clicks. Ads can target one college or all of them.
 */
@Service
public class AdService {

    public record AdView(UUID id, String title, String body, String imageUrl, String ctaText, String targetUrl) {
    }

    public record AdAdminView(UUID id, String title, String body, String imageUrl, String ctaText, String targetUrl,
                              String collegeId, int weight, boolean active, long impressions, long clicks) {
    }

    public record AdRequest(@NotBlank @Size(max = 80) String title,
                            @Size(max = 200) String body,
                            @Size(max = 700) String imageUrl,
                            @NotBlank @Size(max = 30) String ctaText,
                            @NotBlank @Size(max = 700) String targetUrl,
                            @Size(max = 80) String collegeId,
                            @Min(1) int weight,
                            boolean active) {
    }

    private final JdbcClient jdbc;

    public AdService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Weighted random pick (Efraimidis-Spirakis) of active ads for the caller's college. */
    public List<AdView> pick(CurrentUser me, int count) {
        return jdbc.sql("""
                        SELECT id, title, body, image_url, cta_text, target_url FROM ads
                         WHERE active AND (college_id IS NULL OR college_id = :college)
                         ORDER BY -ln(1 - random()) / weight
                         LIMIT :n
                        """)
                .param("college", me.collegeId())
                .param("n", Math.max(1, Math.min(count, 10)))
                .query(AdView.class).list();
    }

    public void impression(UUID id) {
        jdbc.sql("UPDATE ads SET impressions = impressions + 1 WHERE id = :id").param("id", id).update();
    }

    public void click(UUID id) {
        jdbc.sql("UPDATE ads SET clicks = clicks + 1 WHERE id = :id").param("id", id).update();
    }

    // ---- admin
    public List<AdAdminView> all() {
        return jdbc.sql("""
                        SELECT id, title, body, image_url, cta_text, target_url, college_id, weight, active,
                               impressions, clicks FROM ads ORDER BY created_at DESC
                        """)
                .query(AdAdminView.class).list();
    }

    public UUID save(UUID id, AdRequest r) {
        UUID adId = id == null ? UUID.randomUUID() : id;
        jdbc.sql("""
                        INSERT INTO ads (id, title, body, image_url, cta_text, target_url, college_id, weight, active)
                        VALUES (:id, :title, :body, :img, :cta, :url, :college, :weight, :active)
                        ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, body = EXCLUDED.body,
                            image_url = EXCLUDED.image_url, cta_text = EXCLUDED.cta_text,
                            target_url = EXCLUDED.target_url, college_id = EXCLUDED.college_id,
                            weight = EXCLUDED.weight, active = EXCLUDED.active
                        """)
                .param("id", adId)
                .param("title", r.title())
                .param("body", r.body())
                .param("img", r.imageUrl())
                .param("cta", r.ctaText())
                .param("url", r.targetUrl())
                .param("college", r.collegeId() == null || r.collegeId().isBlank() ? null : r.collegeId())
                .param("weight", Math.max(1, r.weight()))
                .param("active", r.active())
                .update();
        return adId;
    }
}
