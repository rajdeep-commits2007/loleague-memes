package com.loleague.meme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record MemeProperties(String internalApiKey, Aws aws, Leaderboard leaderboard) {

    public record Aws(String region, String accessKeyId, String secretAccessKey, String bucket,
                      String publicBaseUrl, String endpoint, long maxMemeBytes, long maxAvatarBytes) {

        /** Public URL for an object key, e.g. https://bucket.s3.ap-south-1.amazonaws.com/memes/... */
        public String publicUrl(String key) {
            String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                    ? "https://" + bucket + ".s3." + region + ".amazonaws.com"
                    : publicBaseUrl;
            return (base.endsWith("/") ? base : base + "/") + key;
        }
    }

    public record Leaderboard(String rewards, int likePoints, int commentPoints) {

        /** Coins for rank 1..N. */
        public List<Integer> rewardList() {
            return Arrays.stream(rewards.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Integer::parseInt).toList();
        }
    }
}
