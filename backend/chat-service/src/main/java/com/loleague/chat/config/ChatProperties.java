package com.loleague.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record ChatProperties(Jwt jwt, Chat chat, Giphy giphy) {

    public record Jwt(String secret, String issuer) {
    }

    public record Chat(String allowedOrigins, String publicWsUrl, Duration ticketTtl, int maxMessageLength) {
        public String[] origins() {
            return allowedOrigins.split("\\s*,\\s*");
        }

        /** Explicit CHAT_PUBLIC_WS_URL, else derived from Render's RENDER_EXTERNAL_URL, else localhost. */
        public String wsUrl() {
            if (publicWsUrl != null && !publicWsUrl.isBlank()) {
                return publicWsUrl;
            }
            String external = System.getenv("RENDER_EXTERNAL_URL");
            if (external != null && !external.isBlank()) {
                return external.replaceFirst("^http", "ws") + "/ws";
            }
            return "ws://localhost:8083/ws";
        }
    }

    public record Giphy(String apiKey, String rating) {
    }
}
