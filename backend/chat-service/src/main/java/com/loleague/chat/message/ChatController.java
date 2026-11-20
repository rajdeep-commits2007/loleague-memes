package com.loleague.chat.message;

import com.loleague.chat.config.ChatPrincipal;
import com.loleague.chat.config.ChatProperties;
import com.loleague.chat.config.WsTicketService;
import com.loleague.chat.message.MessageRepository.ChatMessage;
import com.loleague.chat.presence.PresenceTracker;
import com.loleague.common.security.CurrentUser;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ChatController {

    public record Ticket(String ticket, String wsUrl, String room) {
    }

    public record History(List<ChatMessage> items, String nextBefore) {
    }

    private final ChatService chat;
    private final MessageRepository messages;
    private final WsTicketService tickets;
    private final PresenceTracker presence;
    private final ChatProperties props;

    public ChatController(ChatService chat, MessageRepository messages, WsTicketService tickets,
                          PresenceTracker presence, ChatProperties props) {
        this.chat = chat;
        this.messages = messages;
        this.tickets = tickets;
        this.presence = presence;
        this.props = props;
    }

    // ------------------------------------------------------------- REST (via Next.js proxy, cookie auth)

    @GetMapping("/api/chat/ws-ticket")
    public Ticket ticket(CurrentUser me) {
        return new Ticket(tickets.issue(me), props.chat().wsUrl(), "/topic/college." + me.collegeId());
    }

    @GetMapping("/api/chat/messages")
    public History history(CurrentUser me, @RequestParam(required = false) UUID before,
                           @RequestParam(defaultValue = "40") int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        OffsetDateTime beforeAt = before == null ? null : messages.createdAt(before, me.collegeId());
        List<ChatMessage> page = messages.history(me.collegeId(), beforeAt, beforeAt == null ? null : before, size);
        String next = page.size() == size ? page.getLast().id().toString() : null;
        return new History(page, next);
    }

    @GetMapping("/api/chat/online")
    public Map<String, Integer> online(CurrentUser me) {
        return Map.of("online", presence.count(me.collegeId()));
    }

    // ------------------------------------------------------------- STOMP

    /** Client SENDs to /app/chat.send; the saved message is broadcast to /topic/college.{id}. */
    @MessageMapping("chat.send")
    public void send(@Payload ChatService.SendMessage message, Principal principal) {
        chat.send((ChatPrincipal) principal, message);
    }

    @MessageExceptionHandler(ChatService.ChatException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> onChatError(ChatService.ChatException e) {
        return Map.of("error", e.getMessage());
    }
}
