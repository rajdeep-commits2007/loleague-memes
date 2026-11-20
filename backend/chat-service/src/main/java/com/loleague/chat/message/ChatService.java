package com.loleague.chat.message;

import com.loleague.chat.config.ChatPrincipal;
import com.loleague.chat.config.ChatProperties;
import com.loleague.chat.message.MessageRepository.ChatMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class ChatService {

    public enum Kind { TEXT, GIF }

    public record SendMessage(Kind kind, String content, Integer gifWidth, Integer gifHeight) {
    }

    public static class ChatException extends RuntimeException {
        public ChatException(String message) {
            super(message);
        }
    }

    /** GIFs must come from GIPHY's CDN, so nobody can inject arbitrary image URLs into the room. */
    private static final Pattern GIPHY_URL = Pattern.compile("^https://(media\\d*|i)\\.giphy\\.com/[\\w./?=&%-]+$");
    private static final int BURST = 5;
    private static final long WINDOW_MS = 5_000;

    private final MessageRepository messages;
    private final SimpMessagingTemplate broker;
    private final int maxLength;
    private final Map<UUID, Deque<Long>> recentSends = new ConcurrentHashMap<>();

    public ChatService(MessageRepository messages, SimpMessagingTemplate broker, ChatProperties props) {
        this.messages = messages;
        this.broker = broker;
        this.maxLength = props.chat().maxMessageLength();
    }

    public ChatMessage send(ChatPrincipal me, SendMessage msg) {
        if (msg == null || msg.kind() == null || msg.content() == null) {
            throw new ChatException("Empty message");
        }
        rateLimit(me.id());
        String content = msg.content().strip();
        Integer w = null;
        Integer h = null;
        switch (msg.kind()) {
            case TEXT -> {
                if (content.isEmpty()) {
                    throw new ChatException("Empty message");
                }
                if (content.length() > maxLength) {
                    throw new ChatException("Message too long (max " + maxLength + " characters)");
                }
            }
            case GIF -> {
                if (!GIPHY_URL.matcher(content).matches() || content.length() > 1000) {
                    throw new ChatException("That GIF isn't allowed");
                }
                w = clampDim(msg.gifWidth());
                h = clampDim(msg.gifHeight());
            }
        }
        ChatMessage saved = messages.insert(me.id(), me.username(), me.collegeId(), msg.kind().name(), content, w, h);
        broker.convertAndSend(me.roomTopic(), saved);
        return saved;
    }

    private void rateLimit(UUID userId) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> times = recentSends.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= BURST) {
                throw new ChatException("Slow down! You're sending messages too fast.");
            }
            times.addLast(now);
        }
    }

    private static Integer clampDim(Integer v) {
        return v == null ? null : Math.max(1, Math.min(v, 2000));
    }
}
