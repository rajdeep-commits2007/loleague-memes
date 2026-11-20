package com.loleague.chat.presence;

import com.loleague.chat.config.ChatPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** "42 online" badge per college room. Counts unique students, not tabs. */
@Component
public class PresenceTracker {

    private record Session(String collegeId, UUID userId) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Integer>> byCollege = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate broker;

    public PresenceTracker(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (!(event.getUser() instanceof ChatPrincipal me) || accessor.getSessionId() == null) {
            return;
        }
        sessions.put(accessor.getSessionId(), new Session(me.collegeId(), me.id()));
        byCollege.computeIfAbsent(me.collegeId(), k -> new ConcurrentHashMap<>()).merge(me.id(), 1, Integer::sum);
        publish(me.collegeId());
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Session s = sessions.remove(event.getSessionId());
        if (s == null) {
            return;
        }
        Map<UUID, Integer> users = byCollege.get(s.collegeId());
        if (users != null) {
            users.computeIfPresent(s.userId(), (k, v) -> v <= 1 ? null : v - 1);
        }
        publish(s.collegeId());
    }

    public int count(String collegeId) {
        Map<UUID, Integer> users = byCollege.get(collegeId);
        return users == null ? 0 : users.size();
    }

    public Set<String> colleges() {
        return byCollege.keySet();
    }

    private void publish(String collegeId) {
        broker.convertAndSend("/topic/college." + collegeId + ".presence", (Object) Map.of("online", count(collegeId)));
    }
}
